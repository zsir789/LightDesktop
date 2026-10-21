# Serialized Gradle build runner for shared workspace (multiple agents).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 :feature:notes:testDebugUnitTest
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 :app:assembleRelease
#
# Why: concurrent Gradle builds in the same project directory corrupt the
# Kotlin incremental cache (e.g. "Failed to create MD5 hash for file
# '.../shrunk-classpath-snapshot.bin' as it does not exist"). This script holds
# a named mutex so only one build runs at a time, and injects JAVA_HOME /
# ANDROID_HOME for every caller.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files
# without a BOM as ANSI, so non-ASCII text breaks the parser.

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\dsh_develop\_tools\jdk21'
$env:ANDROID_HOME = 'D:\dsh_develop\_tools\android-sdk'

if ($GradleArgs.Count -eq 0) {
    throw 'No Gradle task given. Example: scripts\gw.ps1 :app:assembleRelease'
}

$mutex = New-Object System.Threading.Mutex($false, 'Local\LightDeskGradleBuild')
$acquired = $false
try {
    $acquired = $mutex.WaitOne([TimeSpan]::FromMinutes(30))
    if (-not $acquired) { throw 'Timed out waiting for the Gradle build lock (30 min).' }
    Push-Location $root
    try {
        & .\gradlew.bat @GradleArgs --console=plain
        exit $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
