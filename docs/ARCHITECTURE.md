# 轻桌面 v1.2.0 · 架构说明

> Android / Kotlin / Jetpack Compose 的桌面小工具集，功能模块化拆分，数据全部本地持久化。

## 1. 模块划分

```
:app                        应用壳：MainActivity、导航、主题宿主、依赖组装
├─ :core:common             通用能力：AppResult/错误码、日志、时间与日历工具、调度器、ViewModel 工厂
├─ :core:model              纯数据模型与纯逻辑（kotlinx.serialization；课表日历计算）
├─ :core:data               本地持久化、仓储接口/实现、数据迁移、统计入口 StatsTracker
├─ :core:designsystem       设计系统：主题、6 套配色注册表、课程色板、排版、圆角、通用组件
├─ :feature:home            首页：今日课程 + 今日待办倒计时 + 快捷入口
├─ :feature:notes           备忘：备忘录与待办合并（CRUD、完成、置顶、筛选、倒计时）
├─ :feature:timetable       课表：ICS 导入解析、周视图 / 月视图
├─ :feature:weather         天气：Open-Meteo 联网 + 缓存降级
├─ :feature:stats           设置与统计：偏好设置 + 使用统计 + 日历热力图
└─ (v1.1.0 移除 :feature:memo / :feature:todo / :feature:floatingball)
```

依赖方向：`feature:* → core:data / core:designsystem → core:model / core:common`；
`:app` 依赖全部模块并负责组装。**功能模块之间零依赖**，跨模块行为通过 `:app` 传入的回调
（如首页的 `onOpenNotes`）或 `core:data` 中的接口完成。

## 2. 分层与数据流

```
Compose UI (feature)
    ↓ 只依赖 core 接口 + 组件
ViewModel / UiState（feature 内部）
    ↓
Repository 接口（core:data）
    ↓
JsonFileStore（core:data，原子写 + 异常兜底 + 损坏备份）
    ↓
filesDir/lightdesk/*.json
```

- **读**：仓储持有 `StateFlow`，首次收集时懒加载；文件损坏会备份为 `.corrupt` 并回退默认值。
- **写**：先写 `.tmp` 再原子替换，`Mutex` 串行化并发写；失败返回 `AppResult.Failure`，由 UI 提示。
- **异常**：所有仓储/网络/解析调用都转为 `AppResult`；UI 统一用 `ErrorBanner` 呈现，不会崩溃。
- **时间**：统一通过 `TimeProvider.now()` 获取（便于测试），禁止在业务代码里直接 `System.currentTimeMillis()`。

## 3. 本地数据

| 文件 | 内容 |
| --- | --- |
| `settings.json` | 主题模式、主题配色（`themePalette`）、天气城市/经纬度、倒计时提醒阈值、统计开关 |
| `notes.json` | 统一事项列表（备忘 + 待办，`NoteItem`） |
| `courses.json` | 课表（课程列表 + 学期起始周 + 来源信息） |
| `usage_stats.json` | 功能访问次数、每日活跃、备忘记录/完成数、天气类型次数 |
| `weather_cache.json` | 最近一次天气快照（离线降级展示） |

均带 `schemaVersion`（当前为 2）。v1.0.0 升级时：

- `memos.json` + `todos.json` → `notes.json`（`LegacyNotesMigration`，待办映射为带 `deadline` 的事项）；
- 旧 `usage_stats.json` 的 `items`（含已删除的 `FLOATING_BALL`）→ 新维度并丢弃悬浮球统计；
- 迁移失败保留原文件、降级为空数据，不阻塞启动。

## 4. 关键设计决策

1. **备忘与待办合并为统一模型 `NoteItem`**：`deadline != null` 即为待办。
   一套 CRUD、一套列表、一套倒计时逻辑，避免两个模块重复实现；筛选维度由 `NotesFilter` 表达。
2. **首页是只读聚合页**：`HomeViewModel` 组合 `CourseRepository` + `NotesRepository` + `TimetableCalendar`
   生成 `HomeUiState`，不写任何数据；倒计时 ticker 仅在存在未完成待办时运行，页面离开即停。
3. **统计模型面向展示**：`UsageStats` 拆为「功能访问 `visits`」「每日 `daily`」「备忘汇总」
   「天气类型 `weatherConditions`」，比为每个功能建一张表更容易扩展热力图与新维度。
   旧数据通过 `legacyItems` 字段做一次性迁移，之后清空该字段。
4. **统计埋点集中**：页面访问在 `:app` 的 `NavHost` 用 `LaunchedEffect(currentRoute)` 统一记录，
   功能模块不需要各自埋点；业务事件（新建备忘、完成待办、天气获取成功）用 `StatsTracker` 显式记录。
5. **课表导入可插拔**：`TimetableImporter` 接口 + `IcsParser` 实现，解析结果 `ImportedTable`
   与 UI 解耦；学期起始周由全表最早 `DTSTART` 推导（兼容 WakeUp 导出）。
6. **移除悬浮球**：去掉前台服务与悬浮窗权限后，应用只保留网络权限，安装包更小、审核更友好。
7. **UI 与逻辑分离**：每个 feature 都把纯逻辑抽到无 Android 依赖的 `XxxPresentation` / `XxxLogic`，
   用 JVM 单测覆盖（当前共 216 个用例）。
8. **不硬编码**：城市、阈值、主题等配置来自 `AppSettings`；颜色/圆角/排版来自设计系统；
   网络地址集中在数据源，解析规则独立成类，均可单点替换。
9. **配色是一等配置（v1.2.0）**：`ThemePalette` 枚举存在 `core:model`，由 `core:designsystem` 的
   `LightDeskPalettes` 注册成 6 套浅/深 `LightDeskPalette`，`LightDeskTheme` 据此生成 Material3
   `ColorScheme` 并通过 `LocalLightDeskPalette` 下发；页面只用语义色（`primary` / `primaryContainer` /
   `accentSoft`），新增配色不需要改动任何 feature。
10. **色块取色是纯函数**：`CourseColors.indexForSeed(seed) = floorMod(seed, 12)`，课程用 `Course.colorSeed`、
   事项用 `id.hashCode()` 作为种子，保证「同一对象颜色稳定、彼此尽量不同」，可单测且与 UI 解耦。
11. **缩放是独立状态机**：课表缩放抽为 `TimetableZoom` 纯函数（档位、步进、边界、行高换算），
   UI 只负责手势与动画，便于后续做持久化或更多视图。

## 5. 扩展点（预留迭代接口）

| 扩展方向 | 接入方式 |
| --- | --- |
| 新增功能模块 | 新建 `:feature:xxx`，在 `:app` 的 `TopLevelDestination`/`NavHost` 注册；`FeatureKey` 追加枚举值即可出现在统计页 |
| 更换存储引擎 | 实现 `core:data` 对应 Repository 接口（如 Room），在 `AppContainer` 替换实现，功能模块零改动 |
| 更换天气服务 | 替换 `feature:weather` 的数据源实现（`OpenMeteoClient` 注入点） |
| 课表来源扩展 | 新增 `TimetableImporter` 实现（CSV、教务系统等），在 UI 选择导入方式 |
| 渲染扩展 | 复用 `TimetableCalendar`（周/月课程计算）与 `CalendarUtils`（日历网格）做新的课表视图 |
| 系统通知提醒 | `NoteItem.deadline` 已就绪，接入 WorkManager / AlarmManager 即可实现到期提醒 |
| 统计新维度 | `StatsRepository` 已按「访问 / 备忘 / 天气 / 每日」拆分记录方法，`DailyActivity` 可继续加字段 |
| 新增主题配色 | `ThemePalette` 加枚举值 + `LightDeskPalettes` 注册一套浅/深色，设置页色卡自动增加 |
| 自定义课程色板 | 修改 `CourseColors.kt` 的 12 色数组，课表/备忘/首页色块同步生效 |
| 换肤 / 品牌字体 | `LightDeskColors` / `LightDeskTypography` 集中在 `core:designsystem`，可整体替换 |
| 云端同步 | 在 Repository 之上叠加同步层，或新增远程数据源实现 |
