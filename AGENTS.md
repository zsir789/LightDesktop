# AGENTS.md

本仓库的长期约定，供后续迭代（人类或 AI 协作）遵循。

## 每次改动都要提交并推送

用户已明确要求：**当前及以后的所有更新调整都要上传到本 GitHub 仓库**
（<https://github.com/zsir789/LightDesktop>）。因此：

1. 改完代码并验证通过后，立刻提交（Conventional Commits：`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:`）；
2. 提交后立即 `git push origin main`，不要攒大批改动；
3. 新增功能或修复缺陷时，同步更新 `docs/CHANGELOG.md`（顶部加当前版本条目）；
4. 发版时按 `docs/GITHUB.md` 的「发新版」流程：升 `versionCode`/`versionName` →
   `scripts/build-release.ps1 -Version X.Y.Z` → 提交 → `git tag -a vX.Y.Z` → `git push` + `git push --tags`。

## 提交前自检

- 单测：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 testDebugUnitTest`
- 集成：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 :app:assembleDebug testDebugUnitTest`
- 严禁提交：`local.properties`、`keystore.properties`、`*.jks`、`build/`、`release/`（`.gitignore` 已覆盖）。

## 工程约束

- 模块边界：`:core:*` 不放 UI 业务逻辑，`:feature:*` 之间不得互相依赖，只允许依赖 `:core:*`；
  `:app` 负责组装与导航。
- 可测试性优先：纯函数（如 `CourseColors`、`TimetableZoom`、`StatsPresentation`）承载逻辑，Compose 只做渲染。
- 不硬编码：城市、阈值、配色、色板、缩放档位等都走注册表/配置对象，新增一项只需注册。
- 数据兼容：新增设置字段用序列化默认值；结构性变更需提升 `StoreModels.kt` 的 `SCHEMA_VERSION` 并写迁移与测试。
- 文档同步：改公开 API 时更新 `docs/CONTRACTS.md`；改模块职责时更新 `docs/ARCHITECTURE.md`。
