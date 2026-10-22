# 轻桌面后续更新方案（Roadmap）

> 基线：v1.2.1（versionCode 4，223 个 JVM 单测全部通过）
> 目的：在「功能搭建 + 视觉打磨」之后，给出 v1.2.2 起若干可独立交付的更新方向，供选择后按版本实施。
> 约束：仍遵守 [`docs/CONTRACTS.md`](CONTRACTS.md)——模块边界、纯函数可测、无硬编码、新增三方依赖需明确说明。

---

## 一、现状判断

### 做得好的

- **模块化清晰**：`:core:*` 能力下沉、`:feature:*` 零耦合、`:app` 统一组装，新增功能成本低；
- **数据可靠**：`JsonFileStore` 原子写 + 损坏备份回退 + `schemaVersion` 迁移机制，本地数据链路完整；
- **视觉体系成熟**：6 套配色注册表、课程/事项 pastel 色板、5 档热力图色阶，均已纯函数化并单测覆盖；
- **纯逻辑可测**：解析、日历、缩放、筛选、倒计时等核心逻辑都有 JVM 单测，回归成本低。

### 主要短板（按影响排序）

1. **待办提醒没有闭环**：`NoteItem.deadline` 只在页面内展示倒计时（`HomeViewModel` 的 ticker），应用不在前台时到期没有任何通知。`AppSettings.countdownWarningMinutes` 只影响展示文案，不会触发提醒。
2. **数据没有出口**：五份 JSON 只存在于应用私有目录，卸载应用或换机即全部丢失；没有导出、备份、恢复能力。
3. **桌面存在感弱**：应用叫「轻桌面」，但没有 AppWidget，也没有 launcher 快捷方式/快速记录入口。
4. **课表只能导入不能改**：`CourseRepository` 只有 `replace()` / `clear()`，改一间教室或一门课也要重新导入 ICS；周次/单双周没有编辑入口。
5. **天气信息单薄**：只有当前天气 + 7 天预报、单城市，没有小时级、空气质量、多城市。
6. **工程保障不足**：仓库无 CI；发布验证依赖本机脚本；多份更新报告注明「未在真机/模拟器走查」。
7. **有「存了但没接线」的设置**：`usageStatsEnabled` 无读取方（`StatsTracker` 无条件记录）、
   `weekStartMonday` 全仓无读取方、`countdownWarningMinutes` 只影响设置页文案而首页紧急判定是硬编码常量；
   另外 `StatsScreen.kt:584` 写着「请按需自行备份」，但全仓没有导出入口。

### 结论

v1.0.0 → v1.2.1 已经完成「功能搭建 + 视觉打磨」。下一阶段建议**优先把已有功能闭环**（提醒、数据、桌面入口），而不是继续增加新页面；新增页面受底部导航 5 项上限约束，收益也低于补齐已有场景。

---

## 二、前置：技术债与死配置清理（v1.2.2 先做）

> 这一版不新增功能，只把「说了但没做」的地方补齐、把将来要用的迁移框架搭好。工作量小、回归面窄，
> 建议作为 v1.2.2 的主内容，与 Quick wins 同版发布。

### 2.1 三处「存了但没接线」的配置

| 配置 | 现状（证据） | 处理 |
| --- | --- | --- |
| `AppSettings.usageStatsEnabled` | `AppContainer` 构造 `StatsTracker(repository, scope, timeProvider)`，`record()` 无条件写库；用户关掉后仍在记录 | `StatsTracker` 注入 `SettingsRepository`，`record()` 内先 `settingsRepository.current()`（`JsonSettingsRepository` 已用 `MutableStateFlow` 缓存，`current()` 不读盘），关闭则直接 return |
| `AppSettings.countdownWarningMinutes` | 只在 `StatsScreen.kt:258/486` 回显；首页紧急判定硬编码 `HomePresentation.WARNING_MILLIS = 60 * 60 * 1000`（`HomePresentation.kt:46`） | `HomePresentation.toTodoUi/todayTodosUi` 增加 `warningMillis` 参数（默认 60 分钟保持现行为）；`HomeViewModel` 从 `observeSettings()` 取 `countdownWarningMinutes * 60_000L` 传入；`todoTagLabel` 的「1 小时内」改为动态文案 |
| `AppSettings.weekStartMonday` | 全仓无读取方，设置页也无入口 | 二选一：**实现**（`CalendarUtils`/`TimetableCalendar`/热力图按周起始日计算 + 设置页开关）或**移除**（模型删字段，`ignoreUnknownKeys = true` 保证旧 JSON 兼容，`CONTRACTS.md` 注明）。建议先移除，等有真实需求再做 |

补充细节：

- 统计开关关闭后，已记录的历史数据保留，只是不再新增；设置页文案要说清楚这一点；
- 阈值改动要即时反映到首页（`observeSettings()` 已是 Flow，无需额外刷新机制）；
- 单测：`StatsTrackerTest` 补「关闭 → 不写入」；`HomePresentationTest` 补自定义阈值与 0/负数边界；
  `HomeViewModelTest` 补「设置变化 → urgent 变化」；
- `StatsScreen.kt:584` 目前写「请按需自行备份」却没有任何备份入口：v1.2.2 先把文案改为
  「当前版本暂不支持导出，计划后续提供」，避免文案承诺大于能力；真正的导出/导入由方案 B 实现。

### 2.2 schemaVersion 只有编号，没有迁移分支

- 现状：`StoreModels.SCHEMA_VERSION = 2` 仅作为序列化默认值；`JsonFileStore.readFromDisk()` 解码失败即
  改名 `.corrupt` + 回退默认值。`ignoreUnknownKeys = true` 只能兜住「新增字段」，兜不住字段重命名、
  删除、语义变化——将来结构一变，用户会**静默丢数据**。
- 方案：给 `JsonFileStore` 增加可选迁移钩子：
  1. 先轻量解析根对象上的 `schemaVersion`（不必反序列化整个模型）；
  2. `from < current` 时按版本链迁移（2→3、3→4…），再反序列化；
  3. `from > current`（降级安装）时**不覆盖原文件**，备份后回退默认值并记录明确日志；
  4. 迁移本身失败时同样保留原文件，不写回。
- 测试：v2 JSON → v3 字段迁移；未知高版本不动原文件；坏 JSON 仍走 `.corrupt`；空文件仍回退默认。
- 为什么现在做：方案 B 会引入备份文件的 `formatVersion`，两套版本号并存最容易混乱；先把 store 迁移
  框架落地，B 才能复用同一套校验。

### 2.3 明确不在 v1.2.2 做的事

- 不为了「还债」拆分 `TimetableScreen.kt`（1225 行）/`StatsScreen.kt`（1136 行）：纯结构调整、回归面大，
  等方案 D 动课表时顺手拆；
- 不引入 Room/DataStore/Hilt，不补 androidTest（当前 223 个 JVM 单测 + 手动清单足够，真机验收见方案 F）。

---

## 三、方案总览

| 方案 | 解决的核心问题 | 用户价值 | 实现成本 | 风险 | 建议版本 |
| --- | --- | --- | --- | --- | --- |
| **0 技术债清理** | 死配置不生效、schema 无迁移、备份文案空承诺 | 中（A/B 的前置） | 低 | 低 | v1.2.2 |
| **A 提醒闭环** | 待办到期没人提醒 | 高 | 中 | 中（国产 ROM 后台限制） | v1.3.0 |
| **B 数据安心** | 数据无法备份/迁移 | 高 | 低 | 低 | v1.3.x / v1.4.0 |
| **C 桌面存在感** | 没有桌面小组件与快捷入口 | 高 | 高 | 中高（Widget 生命周期） | v1.5.0 |
| **D 课表 2.0** | 课表只能导入不能改 | 中高 | 中高 | 中（涉及数据迁移） | v1.6.0 |
| **E 天气 2.0** | 天气信息偏少 | 中 | 低 | 低 | 随版本搭车 |
| **F 质量工程** | 无 CI、无真机基线 | 中（长期） | 低 | 低 | 持续 |

推荐排序：**0 → A → B → F（并行）→ C → D/E**。理由见第十节。

---

## 四、方案 A：提醒闭环（v1.3.0，建议首先做）

### 用户视角

- 待办到期前 N 分钟收到系统通知，点通知直达该事项；
- 完成、删除、改期后，旧提醒自动取消；
- 设置页可开关提醒、调整默认提前量、查看通知权限状态、发送测试提醒；
- 不增加常驻通知、不申请精确闹钟权限、不申请前台服务，保持「轻」的定位。

### 技术方案

1. **纯逻辑先行（可 JVM 测）**：在 `core:model`（无 Android 依赖）新增 `ReminderPlan`：

   ```kotlin
   data class ReminderRequest(
       val noteId: String,
       val triggerAt: Long,     // 通知触发时间
       val deadline: Long,      // 事项截止时间
       val title: String,
   )

   object ReminderPlan {
       /** 过滤已完成/无 deadline/已过期事项，按触发时间升序生成排期。 */
       fun build(items: List<NoteItem>, now: Long, defaultLeadMinutes: Int): List<ReminderRequest>
       fun notificationTitle(note: NoteItem): String
       fun notificationBody(note: NoteItem, remainingMillis: Long): String
   }
   ```

2. **调度接口（core:data，只依赖模型）**：

   ```kotlin
   interface ReminderScheduler {
       /** 全量重排：取消旧任务，按当前事项重建。 */
       suspend fun sync(items: List<NoteItem>, settings: AppSettings): AppResult<Unit>
       suspend fun cancelAll(): AppResult<Unit>
   }
   ```

3. **实现（:app）**：`WorkManagerReminderScheduler` 使用 `OneTimeWorkRequest`
   + `setInitialDelay(triggerAt - now)` + `ExistingWorkPolicy.REPLACE`，唯一任务名 `"reminder-<noteId>"`；
   `ReminderWorker : CoroutineWorker` 负责建通知渠道、发通知、点击打开应用。WorkManager 自带重启恢复，
   **不需要** `RECEIVE_BOOT_COMPLETED`，也不需要精确闹钟权限。
4. **同步时机**：`AppContainer.appScope` 合并 `notesRepository.observeItems()` +
   `settingsRepository.observeSettings()`，去抖后调用 `sync()`；启动、增删改、导入/恢复后自动生效。
5. **通知点击路由**：`MainActivity` 已是 `singleTop`，需同时处理冷启动 `intent` 与 `onNewIntent`，
   通过 extra（如 `open_note_id`）导航到备忘页并高亮目标项。
6. **权限**：`POST_NOTIFICATIONS`（API 33+ 运行时申请）；首次开启提醒时请求，拒绝后开关回退并展示
   「去系统设置开启」引导。**不**申请 `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`。
7. **数据字段**（均有默认值，旧 JSON 无需迁移）：
   - `AppSettings.reminderEnabled: Boolean = false`（默认关闭，避免升级后突然弹通知）；
   - `AppSettings.reminderLeadMinutes: Int = 10`；
   - 可选：`NoteItem.remindBeforeMinutes: Int? = null`（单条事项覆盖默认提前量）。

### 涉及文件

| 层 | 文件 |
| --- | --- |
| `core:model` | `AppSettings.kt`、`NoteItem.kt`、新增 `ReminderPlan.kt`（+ 单测） |
| `core:data` | 新增 `ReminderScheduler.kt`（接口） |
| `:app` | `AppContainer.kt`、`MainActivity.kt`、`AndroidManifest.xml`、新增 `reminder/ReminderWorker.kt`、`reminder/WorkManagerReminderScheduler.kt` |
| `:feature:stats` | `StatsScreen.kt` / `StatsViewModel.kt` 设置区新增「到期提醒」卡片 |
| 构建 | `gradle/libs.versions.toml`、`app/build.gradle.kts` 新增 `androidx.work:work-runtime-ktx` |

### 风险与对策

| 风险 | 对策 |
| --- | --- |
| 国产 ROM 后台限制导致延迟 | 首版定位为「非精确提醒」，文档写明可能存在分钟级延迟；设置页提供自启动/电池优化引导；后续可加精确闹钟「增强提醒」开关 |
| 用户拒绝通知权限 | 开关保持关闭、页面展示可操作提示，不崩溃、不反复弹窗 |
| 时区/跨天/夏令时 | 统一用 `TimeProvider.now()` 计算延迟，Worker 执行时再次校验 deadline，过期则跳过 |
| 通知点击路由 | 冷启动与 `onNewIntent` 两条路径都要处理，写入手动验收清单 |
| 无法自动化验证通知 | `ReminderPlan` 纯逻辑单测 + 真机手动清单（当前无 Robolectric，暂不引入仪器测试） |

### 验收标准

- [ ] 退出应用后，待办到期前 N 分钟收到通知；点击直达该事项；
- [ ] 完成 / 删除 / 改期后，旧任务与旧通知被取消；
- [ ] 拒绝通知权限时应用不崩溃，并给出可操作提示；
- [ ] 重启手机后提醒仍有效；
- [ ] 全量单测通过，`ReminderPlan` 新增用例 ≥ 10 项（过去时间、已完成、无 deadline、提前量边界、排序）。

---

## 五、方案 B：数据安心（v1.3.x / v1.4.0）

### 用户视角

- 一键把备忘、待办、课表、设置、统计导出为一个文件，存到网盘/电脑；
- 换机或误清数据后，选文件导入即可恢复；
- 导出/导入都无存储权限（走系统文件选择器）；
- 恢复前有预览与二次确认，误操作可回退。

### 技术方案

1. **纯逻辑（core:data，可 JVM 测）**：新增 `BackupCodec` 与 `BackupPayload`：

   ```kotlin
   @Serializable
   data class BackupPayload(
       val formatVersion: Int = 1,
       val appVersion: String,
       val exportedAt: Long,
       val notes: List<NoteItem> = emptyList(),
       val courseTable: CourseTable? = null,
       val settings: AppSettings = AppSettings(),
       val stats: UsageStats? = null,
       val weatherCache: WeatherSnapshot? = null,
   )

   object BackupCodec {
       fun encode(payload: BackupPayload): String
       fun decode(json: String): AppResult<BackupPayload>   // 校验 formatVersion / JSON 结构
       fun summary(payload: BackupPayload): BackupSummary   // 预览：条目数、课表数、导出时间
   }
   ```

2. **导出**：`ActivityResultContracts.CreateDocument("application/json")` +
   `ContentResolver.openOutputStream`，默认文件名 `LightDesk-backup-yyyyMMdd-HHmm.json`；
   可选同时支持 zip（五份原始 JSON + 备份元数据，便于人工排查）。
3. **导入**：`OpenDocument` → 读取 → `decode` → 预览 → 二次确认（覆盖 / 取消）→ 通过各 Repository
   写入（沿用 `JsonFileStore` 原子写）。写入前先完整校验，任一环节失败都不改动现有数据。
4. **自动本地备份（可选）**：在清空数据、恢复备份、批量导入等破坏性操作前，把当前五份 JSON
   复制到 `filesDir/lightdesk/backups/`，保留最近 3 份；设置页显示最近备份时间。
5. **入口**：「设置与统计 → 数据」卡片：导出、导入、自动备份开关、上次导出时间。

### 涉及文件

`core:data`（`backup/BackupCodec.kt`、`backup/BackupRepository.kt`）、`feature:stats`（设置卡片与流程）、
`:app`（ActivityResult 注册、容器组装）、`docs/CONTRACTS.md`（新增接口清单）。

### 兼容性

- 不提升 `StoreModels.SCHEMA_VERSION`（除非同时做数据结构变更）；备份文件用独立的 `formatVersion` 演进；
- 旧备份导入新版本：按现有 `schemaVersion` 迁移逻辑处理；
- 新备份导入旧版本：拒绝并提示「备份来自更高版本的应用」。

### 风险与验收

- 风险：误覆盖、写一半失败、损坏文件。对策：预览 + 二次确认 + 恢复前自动备份 + 全量校验后再写。
- 验收：导出 → 清空数据 → 导入 → 全部恢复；损坏/非本应用 JSON 被拒绝且现有数据不变；
  编解码、版本校验、空数据有单测；导出/导入全程无存储权限申请。

---

## 六、方案 C：桌面存在感（建议 v1.5.0）

### 用户视角

- **4×2 小组件**：今天课程 + 今日待办（未完成最近 N 项），点击对应区域直达页面；
- **2×2 小组件**：下一节课倒计时 + 待办数量；
- **长按图标快捷方式**：「新建备忘」「新建待办」，直达编辑弹窗；
- 小组件跟随 6 套配色与深色模式。

### 技术选型

| 方式 | 优点 | 缺点 |
| --- | --- | --- |
| **Glance**（`androidx.glance:glance-appwidget:1.1.1`） | Compose 风格，排版/主题代码可复用，长期维护性好 | 新增依赖（约 1MB）；Glance 版本与 Compose BOM 需对齐；仍受 RemoteViews 能力限制 |
| **RemoteViews + XML** | 无新依赖，行为稳定 | 布局与配色需要两套实现，6 套主题适配重复，复杂列表实现成本高 |

建议：若确定长期做小组件，直接上 Glance，并在 PR 中按 CONTRACTS 说明新增依赖理由；若只想快速验证需求，
可先用 RemoteViews 做「今日课程」静态卡片。

### 更新策略

- 数据变更：`AppContainer.appScope` 收集 notes/course Flow，应用进程存活时主动刷新小组件；
- 定时兜底：`AppWidgetProviderInfo.updatePeriodMillis`（最小 30 分钟）或 WorkManager 周期任务刷新「今天」；
- 主题：把当前 `ThemePalette` 与深浅色状态写入独立偏好（或直接读 `settings.json`），小组件渲染时取用；
  API 31+ 可叠加动态取色作为增强。

### 结构与风险

- 建议新增 `:feature:widget`（Provider + GlanceAppWidget + 数据读取），`:app` 依赖并注册 receiver；
  数据读取复用 `JsonFileStore` / `StoreModels`，避免第二套序列化逻辑。
- 风险：杀后台后更新不及时、各 ROM 小组件行为差异、尺寸/预览图适配。以「打开 App 时刷新 + 定时兜底」为承诺，
  真机验证必须覆盖小米/华为/三星/原生（至少两台）。
- 验收：添加/移除/缩放正常；跨天自动换数据；点击路由正确；深色与 6 套配色可读。

---

## 七、方案 D：课表 2.0（v1.6.0）

1. **手动编辑（优先）**：扩展 `CourseRepository` 为 `upsert(course)` / `delete(courseId)`（内部仍用
   `replace()` 原子写整表），新增课程编辑弹窗；导入后可直接修改教室、时间、周次，无需重导 ICS。
2. **冲突检测**：纯函数 `CourseConflict.check(course, existing): List<Conflict>`，同一 weekday 时间重叠时提示。
3. **单双周与周次跳转**：`Course.weeks` 已是 `List<Int>`，补「单周 / 双周 / 指定周」编辑控件；
   `TimetableCalendar.currentWeek()` 已具备周次计算，可直接做「回到本周」。
4. **多学期（可选，单独版本）**：`CourseTableStore` 从单表扩为 `tables + currentTableId`，
   `SCHEMA_VERSION` 2 → 3 并写迁移与测试；建议不与其他大改同版发布。
5. **导出 `.ics`（可选）**：新增 `TimetableExporter` 接口，与 `TimetableImporter` 对称，方便分享到系统日历。
6. **缩放档位持久化（小改动，可提前）**：`AppSettings.timetableZoom: Float = 1f`，进入课表页恢复、
   离开或停止手势时写回，解决 README 已知限制。

---

## 八、方案 E：天气 2.0（可搭车）

- **小时级预报**：Open-Meteo 增加 `hourly=temperature_2m,precipitation_probability,weather_code`，
  `WeatherSnapshot` 新增 `hourly: List<HourlyForecast> = emptyList()`（默认空，旧缓存兼容）；
- **空气质量**：`https://air-quality-api.open-meteo.com/v1/air-quality?latitude=..&longitude=..&current=pm2_5,european_aqi`
  （无需 Key），失败不影响主天气展示；
- **多城市**：`AppSettings.favoriteCities: List<City>`（可选字段）+ Open-Meteo Geocoding
  （`geocoding-api.open-meteo.com`）搜索城市；先做「小时级」，多城市视需求再做。
- 风险：请求与字段增加，需同步调整缓存策略与超时降级；均可用纯函数映射单测覆盖。

---

## 九、方案 F：质量与工程（持续，建议随每个版本做）

1. **GitHub Actions CI**：`.github/workflows/android.yml`，JDK 21 + `./gradlew testDebugUnitTest :app:assembleDebug`。
   注意 `scripts/gw.ps1` 硬编码了本机 JDK/SDK 路径，CI 直接调用 `gradlew`（或把路径改为可覆盖的环境变量）。
2. **真机验收清单**：新增 `docs/QA-CHECKLIST.md`——Android 8 / 12 / 13 / 14 / 15；
   小米 / 华为 / 三星 / 原生；浅色 / 深色 / 6 套配色；通知权限拒绝；重启；覆盖安装升级；弱网；字体 1.3x / 2.0x。
3. **无障碍与大字**：补齐 `contentDescription`、48dp 触控区、`fontScale = 2.0` 下的布局检查。
4. **截图回归（可选）**：Paparazzi / Roborazzi 对主题、热力图、课表做 JVM 截图测试。
5. **依赖与构建升级**：Compose BOM / AGP 升级单独提交，不与功能改动混在一起。

---

## 十、推荐路线图

| 版本 | 主题 | 范围 | 风险 | 验收重点 |
| --- | --- | --- | --- | --- |
| v1.2.2 | 先修「说了没做」 | 方案 0（2.1 + 2.2）+ Quick wins + 真机走查修复 + CI | 低 | 三处配置全部生效、迁移用例通过、223+ 单测通过 |
| v1.3.0 | 提醒闭环 | 方案 A | 中 | 真机通知验证 |
| v1.3.1 / v1.4.0 | 数据安心 | 方案 B | 低 | 导出→清空→恢复往返测试 |
| v1.5.0 | 桌面存在感 | 方案 C + launcher 快捷方式 | 中高 | 多 ROM 小组件验证 |
| v1.6.0 | 课表可编辑 | 方案 D + 方案 E 小时预报 | 中 | 数据迁移与冲突提示 |

> 如果更看重「视觉冲击」，可以把方案 C 换成「主题 2.0」（动态取色、更多配色、字体与动效 + 首页信息重排），
> 成本更低、风险更小，但对日常使用的价值不如提醒与备份。建议作为 C 的替代项，而不是前置项。

---

## 十一、Quick wins（可放进 v1.2.2）

- 课表缩放档位持久化（`AppSettings.timetableZoom`）；
- 首页空态引导：无课表 → 「导入课表」，无待办 → 「新建待办」；
- 课表「回到今天 / 本周」按钮与当前时间指示线；
- 关于页补充开源依赖清单、构建时间、GitHub 链接；
- 天气下拉刷新，缓存更新时间更显眼；
- 备忘新建后自动聚焦标题，编辑器支持 Ctrl+Enter 保存；
- 统计页「清空统计」二次确认与空态文案优化。

> 以上逐条先核对现状再动手，避免与既有交互重复。

---

## 十二、暂不建议

| 方向 | 原因 |
| --- | --- |
| 云同步 / 账号 | 违背「纯本地」定位，需要服务端、隐私合规与运营成本；如确需，先用「导出到网盘 + 手动恢复」折中 |
| Room / Hilt | 当前数据量与手写 DI 足够，改造收益低、回归面大；等数据量或协作规模上来再评估 |
| 全量 i18n | 文案目前硬编码在 Kotlin（CONTRACTS 约定中文），抽 `strings.xml` 是跨模块大改，目标用户以中文为主 |
| 应用商店上架 | 签名、隐私政策、审核成本；GitHub Release 已满足分发 |
| 更多一级页面 | 底部导航 5 项已接近上限；新增能力优先做成首页卡片、小组件、快捷方式 |

---

## 十三、需要拍板的四件事

1. **下一版做 A（提醒）还是 B（备份）？** 建议 v1.2.2 先做方案 0（约 1~2 天），随后 A 优先补齐最高频的「待办」闭环；B 紧随其后，成本低。
2. **是否接受新增依赖**：WorkManager（A）、Glance 或 RemoteViews（C）？CONTRACTS 要求新增依赖需明确说明。
3. **能否安排真机验证**（至少一台国产 ROM + 一台原生/模拟器）？A 与 C 的验收依赖真机通知/小组件行为。
4. **`weekStartMonday` 实现还是删除？** 建议删除（当前无 UI 入口、无读取方），等出现「周日起始」的真实需求再加；若保留，需同时补设置入口与测试。
