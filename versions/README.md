# 版本归档目录

每个发布版本一个子目录，格式 `v<版本号>`，内容固定为：

| 文件 | 说明 |
| --- | --- |
| `LightDesk-v<版本号>.apk` | 该版本签名安装包 |
| `LightDesk-source-v<版本号>.zip` | 该版本源码包（排除构建产物、签名私钥、`local.properties`；zip 内统一使用 `/` 路径分隔符） |
| `SHA256SUMS.txt` | 本版本两个产物的 SHA-256 校验和 |
| `UPDATE-REPORT.md` | 该版本更新报告 |
| `README.md` | 该版本对应的使用文档快照 |
| `CHANGELOG.md` | 该版本对应的变更日志快照 |

## 已归档版本

| 版本 | 时间 | 说明 |
| --- | --- | --- |
| v1.0.0 | 2026-10-19 | 首个版本：备忘录、课表、天气、交互统计、悬浮球、待办倒计时 |
| v1.1.0 | 2026-10-19 | 首页聚合、备忘与待办合并、设置与统计合页、课表月视图、移除悬浮球 |
| v1.2.0 | 2026-10-21 | 6 套浅色主题与换色设置、课程与卡片彩色色块、课表缩放与全览、交互动效打磨 |
| v1.2.1 | 2026-10-22 | 课表横向缩放修复（横纵同步缩放）、热力图 5 档色阶与图例优化 |

`versions/SHA256SUMS.txt` 汇总记录所有版本的校验和，可对照校验下载的安装包。

## 新增版本

在项目根目录执行（本机没有 `pwsh`，必须用 `powershell`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-release.ps1 -Version 1.2.1
```

脚本依次执行：全量 JVM 单测 → 签名 Release APK → 打包源码 → 复制产物与更新报告到 `versions/v1.2.1/`
→ 生成校验和。更新报告需提前放在 `docs/releases/v1.2.1/UPDATE-REPORT.md`，否则归档目录中不会有报告文件。
