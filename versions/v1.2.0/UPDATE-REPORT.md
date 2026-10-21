# 轻桌面 LightDesk v1.2.0 · 更新报告

> 版本：1.2.0（versionCode 3） · 日期：2026-10-21 · 基线：v1.1.0（versionCode 2）
> 主题：**更轻快的视觉、可换的配色、可缩放的课表**。数据模型向后兼容，可覆盖升级。

---

## 一、版本概要

v1.2.0 是一次以「视觉与交互」为中心的迭代：给课程与事项加上柔和的 pastel 色块，让每节课、每张卡片不再
是统一的灰白；把主题从「一套蓝」扩展为 **6 套浅色系配色**，并提供应用内换色入口；课表新增 **捏合/按钮缩放**
与「全览」按钮，一屏看完整周安排。功能与数据结构不变，升级后原有数据全部保留。

| 项目 | 内容 |
| --- | --- |
| 版本号 | 1.2.0（versionCode 3） |
| 安装包 | `release/LightDesk-v1.2.0.apk` |
| 源码包 | `release/LightDesk-source-v1.2.0.zip` |
| 版本归档 | `versions/v1.2.0/`（安装包 + 源码包 + 校验和 + 本报告 + README/CHANGELOG 快照） |
| 最低系统 | Android 8.0（API 26），targetSdk 35 |
| 权限 | 仍只有 `INTERNET` / `ACCESS_NETWORK_STATE` |
| 数据兼容 | `settings.json` 新增 `themePalette` 自动取默认值；其余文件结构不变 |

---

## 二、需求对照

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 课程栏位每节课背景色块颜色随机，整体五颜六色但保持简约 | 新增 `CourseColors`（12 色柔和色板），按课程 `colorSeed` 稳定取色：同一门课颜色固定、相邻课程不同色；周视图课程块为 pastel 底色 + 3dp 强调条 + 深色文字，月视图课程条为色块 + 圆点，当天列表加强调竖条；圆角 6dp、无渐变无阴影 |
| 2 | 优化 UI 设计，添加色块，提升交互体验 | 首页：欢迎卡渐变底、课程/待办改色块卡、快捷入口按压 0.98 + 错峰淡入；备忘：卡片 pastel 色块 + 左侧强调条、概览色条、筛选 chip 主题色、完成态填充/对勾 spring + 底色渐变 + 内容淡化、空状态色块面板；底部导航：分隔线 + 选中项色块胶囊（`accentSoft`）+ 图标缩放；课表选中日期/今天用主题浅色块 |
| 3 | 课表页面提供缩放，方便一眼看全所有课程 | 新增 `TimetableZoom` 纯逻辑（0.6x–2.0x，步进 1.25）+ 顶栏「－ / 档位 / ＋ / 全览」控件 + 自定义 `Modifier.pinchToZoom` 双指捏合；缩放带动画（220ms），时间轴行高随档位联动（≥24dp），「全览」一键缩到 0.6x 并回到顶部 |
| 4 | 页面背景以浅色系为主，增加更多色彩选择并提供换色设置 | 新增 `ThemePalette`（海盐蓝 / 薄荷绿 / 蜜桃橙 / 薰衣草 / 暖沙金 / 樱花粉）与 `LightDeskPalettes`；`LightDeskTheme(palette = ...)` 由配色注册表生成整套 Material3 颜色；「设置与统计」新增配色选择卡（6 个三色预览色卡，选中描边 + 对勾），立即生效并持久化 |
| 5 | 迭代版本号、保留旧版文件、为新版写 README | `versionCode 3 / versionName 1.2.0`；`versions/v1.0.0`、`versions/v1.1.0` 原样保留，新增 `versions/v1.2.0`；本版本 README 重写（见 `README.md` 与 `versions/v1.2.0/README.md`） |

---

## 三、改动明细

### 3.1 核心层（Lead）

| 文件 | 改动 |
| --- | --- |
| `core/model/.../AppSettings.kt` | 新增 `enum class ThemePalette { OCEAN, MINT, PEACH, LAVENDER, SAND, ROSE }` 与 `AppSettings.themePalette`（默认 `OCEAN`） |
| `core/designsystem/.../theme/Palette.kt` | 新增 `LightDeskPalette`（16 个语义色）、6 套浅色/深色配色注册表 `LightDeskPalettes.of/lightOf/darkOf`、`ThemePalette.label()`、`ThemePaletteOptions` |
| `core/designsystem/.../theme/Theme.kt` | 颜色方案改为由 `LightDeskPalette` 推导（`lightSchemeOf` / `darkSchemeOf`）；新增 `LocalLightDeskPalette`；`LightDeskTheme(darkTheme, palette, content)`；`StatusColors.info/muted` 改为跟随当前主色 |
| `core/designsystem/.../theme/CourseColors.kt` | 新增课程/事项 pastel 色板（12 色）与取色 API：`indexForSeed` / `container(seed)` / `onContainer(seed)` / `accent(seed)`，以及非 Compose 版本供单测使用；深色模式自动降亮 |
| `core/designsystem/build.gradle.kts` | 增加 `api(project(":core:model"))`（`ThemePalette` 定义在 model 层） |
| `app/MainActivity.kt` | `LightDeskTheme(darkTheme = darkTheme, palette = settings.themePalette)` |
| `app/ui/LightDeskApp.kt` | 底部导航改为「分隔线 + 选中色块胶囊」布局，颜色跟随当前配色 |
| `app/build.gradle.kts` | `versionCode = 3`、`versionName = "1.2.0"` |

### 3.2 功能模块（teammate 并行完成，各自单测通过）

| 模块 | 改动 |
| --- | --- |
| `feature/timetable` | 新增 `TimetableZoom.kt`（缩放档位、行高、全览判定等纯函数）与 `TimetableZoomTest`/`TimetableColorsTest`；`TimetableScreen` 课程色块 + 缩放控件 + 捏合手势 + 全览 |
| `feature/notes` | 新增 `NoteVisuals.kt`（色块种子、按压比例、完成态透明度）与 `NoteVisualsTest`；`NoteCard`/`NotesScreen`/`NoteEditorSheet` 色块化与交互打磨 |
| `feature/stats` | `StatsViewModel.setThemePalette()`；`StatsPresentation` 新增配色选项/摘要纯函数；`StatsScreen` 新增「主题配色」设置卡（含深色预览） |
| `feature/home` | 新增 `HomeVisualTest`；`HomeScreen` 欢迎卡、课程/待办色块、快捷入口按压与错峰淡入；`HomePresentation` 增加种子/状态标签/透明度纯函数 |

### 3.3 文档与工程

- `README.md` 重写为 v1.2.0（新增配色、课表缩放、色块说明，更新测试数与版本记录）。
- `docs/CHANGELOG.md` 新增 v1.2.0 条目；`docs/ARCHITECTURE.md`、`docs/CONTRACTS.md` 同步配色系统与缩放 API。
- `versions/v1.2.0/` 归档安装包、源码包、校验和、本报告及 README/CHANGELOG 快照；`versions/README.md` 更新归档表。
- `scripts/build-release.ps1` 无需改动即可复用（自动读取 `docs/releases/v<版本>/UPDATE-REPORT.md`）。

---

## 四、兼容性与数据

- **覆盖升级**：与 v1.0.0 / v1.1.0 使用同一签名（`keystore/lightdesk-release.jks`），可直接覆盖安装。
- **数据兼容**：`settings.json` 缺少 `themePalette` 时取默认「海盐蓝」，无需迁移；`notes.json` / `courses.json` /
  `usage_stats.json` / `weather_cache.json` 结构未变，旧数据原样可用。
- **主题模式**：仍支持跟随系统 / 浅色 / 深色；6 套配色在深色模式下各有对应深色方案。

---

## 五、测试与校验

| 模块 | 用例数 |
| --- | --- |
| `core:common` | 11 |
| `core:model` | 12 |
| `core:data` | 9 |
| `feature:home` | 25 |
| `feature:notes` | 44 |
| `feature:timetable` | 67 |
| `feature:weather` | 22 |
| `feature:stats` | 26 |
| **合计** | **216（0 失败 0 错误）** |

集成验证：`scripts/build-release.ps1 -Version 1.2.0` 内部执行全量 `testDebugUnitTest` + `:app:assembleRelease`，
构建通过；发布包经 `apksigner verify` 校验签名，`aapt2 dump badging` 校验 `versionCode 3 / versionName 1.2.0`。

下载后校验（与 `versions/v1.2.0/SHA256SUMS.txt` 对照）：

```powershell
Get-FileHash .\LightDesk-v1.2.0.apk -Algorithm SHA256
```

---

## 六、已知限制

- 课表缩放档位不跨页面持久化：离开课表页再进入会回到 1.0x（缩放是即时操作，未写入设置）。
- `CourseColors` 为 12 色滚动色板，课程超过 12 门时颜色会重复（仍与课程一一对应，不会随机跳色）。
- 月视图窄屏每格最多展示 2 条课程，其余折叠为「+N」。
- 本次交付通过编译与 JVM 单测验证，未在真机 / 模拟器进行完整 UI 走查；配色观感建议在真机确认后再微调色值。
- 天气来自 Open-Meteo（海外服务），弱网下较慢，已做超时与本地缓存降级。
