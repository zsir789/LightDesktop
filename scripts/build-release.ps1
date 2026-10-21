# Build and archive one release version.
#
# Usage (from project root):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Version 1.1.0
#
# Outputs:
#   release/LightDesk-v<Version>.apk        signed release APK of this build
#   release/LightDesk-source-v<Version>.zip source archive
#   versions/v<Version>/                    archived copy (APK, source, update report, checksums)
#   versions/SHA256SUMS.txt                 aggregate checksums (appended)
#
# Requires release/keystore.properties (signing config). JDK/SDK paths are set below.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files
# without a BOM as ANSI, so non-ASCII text breaks the parser.

param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:JAVA_HOME = 'D:\dsh_develop\_tools\jdk21'
$env:ANDROID_HOME = 'D:\dsh_develop\_tools\android-sdk'

$versionsDir = Join-Path $root "versions\v$Version"
$releaseDir = Join-Path $root 'release'
$docsReleaseDir = Join-Path $root "docs\releases\v$Version"
New-Item -ItemType Directory -Force -Path $versionsDir, $releaseDir | Out-Null

Write-Host "== 1/5 run unit tests =="
& .\gradlew.bat testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { throw 'unit tests failed, abort archiving' }

Write-Host "== 2/5 build release APK =="
& .\gradlew.bat :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'assembleRelease failed, abort archiving' }

$apk = Join-Path $releaseDir "LightDesk-v$Version.apk"
$builtApk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $builtApk)) { throw "missing build output $builtApk" }
Copy-Item $builtApk $apk -Force
Write-Host "APK -> $apk"

Write-Host "== 3/5 pack source archive =="
$sourceZip = Join-Path $releaseDir "LightDesk-source-v$Version.zip"
if (Test-Path $sourceZip) { Remove-Item $sourceZip -Force }

# Stage a clean copy outside the project so build outputs (locked by the Gradle
# daemon) and binaries never enter the archive. robocopy /XD matches any
# directory with that name at any depth.
$stage = Join-Path $env:TEMP "lightdesk-source-v$Version"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
$null = New-Item -ItemType Directory -Force -Path $stage
$excludeDirs = @('build', '.gradle', '.kotlin', '.idea', 'release', 'versions', 'keystore', '.git')
$robocopyArgs = @($root, $stage, '/MIR', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:1', '/W:1')
$robocopyArgs += '/XD'
$robocopyArgs += $excludeDirs
$robocopyArgs += '/XF'
$robocopyArgs += @('local.properties', 'keystore.properties', '*.apk', '*.zip')
& robocopy @robocopyArgs | Out-Null
if ($LASTEXITCODE -gt 7) { throw "robocopy failed with exit code $LASTEXITCODE" }

# Write the archive with '/'-separated entry names so it extracts correctly on
# Windows, macOS and Linux. Compress-Archive on Windows PowerShell 5.1 stores
# '\' separators, which break extraction on non-Windows systems.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipStream = [System.IO.Compression.ZipFile]::Open($sourceZip, 'Create')
try {
    $stageLength = (Resolve-Path $stage).Path.Length + 1
    foreach ($file in (Get-ChildItem -Path $stage -Recurse -File -Force)) {
        $entryName = $file.FullName.Substring($stageLength).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zipStream, $file.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $zipStream.Dispose()
}
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "source -> $sourceZip"
Write-Host "== 4/5 archive into versions/v$Version =="
Copy-Item $apk (Join-Path $versionsDir "LightDesk-v$Version.apk") -Force
Copy-Item $sourceZip (Join-Path $versionsDir "LightDesk-source-v$Version.zip") -Force
if (Test-Path (Join-Path $docsReleaseDir 'UPDATE-REPORT.md')) {
    Copy-Item (Join-Path $docsReleaseDir 'UPDATE-REPORT.md') (Join-Path $versionsDir 'UPDATE-REPORT.md') -Force
}
Copy-Item (Join-Path $root 'README.md') (Join-Path $versionsDir 'README.md') -Force
Copy-Item (Join-Path $root 'docs\CHANGELOG.md') (Join-Path $versionsDir 'CHANGELOG.md') -Force

Write-Host "== 5/5 generate checksums =="
$lines = @()
foreach ($f in @("LightDesk-v$Version.apk", "LightDesk-source-v$Version.zip")) {
    $hash = (Get-FileHash (Join-Path $versionsDir $f) -Algorithm SHA256).Hash.ToLowerInvariant()
    $lines += "$hash  $f"
    Write-Host "$hash  $f"
}
$lines | Set-Content -Encoding ASCII (Join-Path $versionsDir 'SHA256SUMS.txt')

# Aggregate file: keep one header + checksum pair per version, newest first.
$aggregate = Join-Path $root 'versions\SHA256SUMS.txt'
$kept = @()
if (Test-Path $aggregate) {
    # Keep only checksum lines of other versions (ASCII-safe, no comments).
    $kept = Get-Content $aggregate | Where-Object { $_ -match '^[0-9a-f]{64}\s\s' -and $_ -notmatch "v$([regex]::Escape($Version))\s*$" }
}
$block = @("# $Version") + @($lines | ForEach-Object { "$_  v$Version" })
$all = $block
if ($kept.Count -gt 0) { $all = $block + $kept }
$all | Set-Content -Encoding ASCII $aggregate
Write-Host "archived: $versionsDir"
