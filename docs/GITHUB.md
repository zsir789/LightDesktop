# GitHub 发布与版本维护

本仓库为「轻桌面」（Android 原生，Kotlin + Jetpack Compose）的源码、文档与历史版本归档仓库。

## 仓库内容约定

| 路径 | 内容 | 是否入库 |
| --- | --- | --- |
| `app/`、`core/`、`feature/` | 全部源码 | 是 |
| `docs/` | 架构、契约、变更日志、逐版本更新报告 | 是 |
| `versions/vX.Y.Z/` | 该版本 APK、源码包、README、CHANGELOG、更新报告、SHA256 | 是 |
| `release/` | 本地构建输出（不入库，见 `.gitignore`） | 否 |
| `keystore/`、`keystore.properties`、`local.properties` | 签名密钥与本地 SDK 路径（**严禁入库**） | 否 |

## 克隆

```bash
git clone git@github.com:zsir789/LightDesktop.git
cd LightDesktop
```

构建见 `README.md` 的「构建与测试」。需要 JDK 17+、Android SDK（compileSdk 35），并自备签名密钥
（`keystore.properties`，参考 `keystore.properties.example`）。

## 每次更新（日常提交）

```bash
git add -A
git commit -m "feat(notes): 支持批量归档"   # Conventional Commits 前缀：feat/fix/docs/refactor/test/chore
git push
```

约定：

- 一个提交只做一件事，提交信息说明「做了什么、为什么」；
- 提交前至少跑一遍 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 testDebugUnitTest`；
- 不要提交 `build/`、`release/`、`local.properties`、`keystore.properties`、`*.jks`。

## 发新版（归档 + 打标签 + 推送）

1. 修改 `app/build.gradle.kts` 的 `versionCode`（+1）与 `versionName`（如 `1.3.0`）；
2. 更新 `README.md` 与 `docs/CHANGELOG.md`，补充 `docs/releases/vX.Y.Z/UPDATE-REPORT.md`；
3. 执行打包归档脚本（自动测试 → 签名 APK → 源码包 → 复制到 `versions/vX.Y.Z/` → 校验和）：

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Version 1.3.0
   ```

4. 提交并打标签（标签与版本号一致）：

   ```bash
   git add -A
   git commit -m "release: v1.3.0"
   git tag -a v1.3.0 -m "轻桌面 v1.3.0"
   git push
   git push --tags
   ```

5. 在 GitHub 的 Releases 页面基于标签 `v1.3.0` 发布简要说明，并把 `versions/v1.3.0/LightDesk-v1.3.0.apk`
   作为附件上传（仓库内已有同一文件，附件便于手机直接下载）。

## 校验下载的 APK

```bash
# Windows PowerShell
Get-FileHash .\LightDesk-v1.3.0.apk -Algorithm SHA256
# macOS / Linux
shasum -a 256 LightDesk-v1.3.0.apk
```

与 `versions/vX.Y.Z/SHA256SUMS.txt` 中的值比对。

## 签名密钥说明

- 仓库不含私钥。`keystore.properties` 只在本地存在，被 `.gitignore` 排除。
- 若更换签名密钥，已安装的旧版本无法直接覆盖升级（Android 会拒绝签名不一致的安装包），请在
  更新报告中注明。
- 密钥丢失即无法发布可覆盖升级的新版本，请自行离线备份 `keystore/lightdesk-release.jks` 与口令。
