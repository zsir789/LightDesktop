# 开发契约（v1.2.1 基线）

本文件记录工程约定与核心接口清单。**修改接口签名需同步更新本文件与调用方**。

## 0. 环境与构建

- JDK 21：`D:\dsh_develop\_tools\jdk21`；Android SDK：`D:\dsh_develop\_tools\android-sdk`（`local.properties` 已配置）。
- Gradle Wrapper 位于项目根目录。**串行构建脚本**（推荐，避免 Kotlin 增量缓存争用）：

```powershell
cd D:\dsh_develop\dsh_desktop_master
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 :feature:notes:testDebugUnitTest :feature:notes:compileDebugKotlin
```

- 直接 gradle 示例（同一次只跑一个构建）：

```powershell
$env:JAVA_HOME='D:\dsh_develop\_tools\jdk21'
$env:ANDROID_HOME='D:\dsh_develop\_tools\android-sdk'
.\gradlew.bat :app:assembleDebug --console=plain
```

- 依赖源（阿里云镜像）已在 `settings.gradle.kts` 的 `pluginManagement` / `dependencyResolutionManagement` 配好，**不要**修改仓库配置。
- Windows PowerShell 5.1 读取无 BOM 的 `.ps1` 会按 ANSI 解析，`scripts/*.ps1` **必须保持纯 ASCII**。
- 本机无 `pwsh` 命令，脚本统一用 `powershell -NoProfile -ExecutionPolicy Bypass -File` 调用。

## 1. 文件所有权

| 范围 | 可写目录 |
| --- | --- |
| Lead | `:app`、`core/**`、Gradle 配置、`docs/**`、`scripts/**`、`versions/**`、`release/**`、`README.md`、签名与打包 |
| 功能模块负责人 | 各自 `feature/<module>/**`（含其 `build.gradle.kts`、`AndroidManifest.xml`、`src/test`） |

- 不要修改模块之外的任何文件；需要新增依赖或权限时由 Lead 统一修改模块构建文件。
- 不要运行 `clean`，不要结束别人的 Gradle 进程。

## 2. 通用编码规范

- Kotlin + Jetpack Compose（Material3）+ 协程；单 Activity，页面以 `@Composable fun XxxRoute(...)` 暴露。
- UI 文案中文；颜色/圆角/排版必须来自 `core:designsystem`（禁止硬编码色值与字体族）。
- 状态：`ViewModel` + `StateFlow`，UI 用 `collectAsStateWithLifecycle()`；一次性动作使用 `rememberCoroutineScope`。
- 依赖注入：通过构造函数接收 `core:data` 的 Repository 接口与 `core:common` 工具，**不要在模块内 new 仓储**。
- 异常处理：所有可能失败的调用（仓储写、网络、解析）必须捕获并转为可见 UI 提示，不允许未捕获异常崩溃。
- 页面访问统计：**不要在各页面重复埋点**，`:app` 的 `NavHost` 已统一 `trackVisit`；业务事件用 `StatsTracker`。
- 时间：用 `TimeProvider.now()`；格式化用 `TimeFormat` / 各模块 `XxxFormat` 纯函数。
- 不新增权限、不新增三方依赖（除非任务明确说明）。

## 3. 核心接口（只读，按此调用）

### 3.1 结果类型（`com.lightdesk.core.common`）

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
enum class ErrorCode { NETWORK, TIMEOUT, PARSE, STORAGE, PERMISSION, INVALID_INPUT, NOT_FOUND, UNKNOWN }
data class AppError(val code: ErrorCode, val message: String, val cause: Throwable? = null)

fun <T> AppResult<T>.getOrNull(): T?
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T>
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T>   // 回调参数是 AppError，原因在 it.cause
inline fun <T> attempt(code: ErrorCode = ErrorCode.UNKNOWN, block: () -> T): AppResult<T>
```

### 3.2 工具（`com.lightdesk.core.common`）

```kotlin
interface TimeProvider { fun now(): Long }
object TimeFormat {
    fun weekdayLabel(weekday: Int): String            // 1..7 -> 周一..周日
    fun minutesToClock(minutesOfDay: Int): String     // 485 -> "08:05"
    fun clockToMinutes(clock: String): Int?
    fun timestampToDateTimeText(timestamp: Long): String
    fun timestampToFullText(timestamp: Long): String
    fun countdownText(remainingMillis: Long): String  // "2天 03:12:09" / "已超时 00:12:03"
    fun deadlineHint(deadline: Long, now: Long): String
}
object CalendarUtils {
    fun monthGrid(month: YearMonth): List<List<LocalDate?>>       // 周一起始，月外 null
    fun weekOf(date: LocalDate): List<LocalDate>
    fun recentWeeks(today: LocalDate, weeks: Int): List<List<LocalDate>>  // 升序，末行本周
    fun dateKey(date: LocalDate): String
    fun dateKey(timestamp: Long, zone: ZoneId = systemDefault()): String
}
inline fun <reified VM : ViewModel> simpleViewModelFactory(crossinline create: () -> VM): ViewModelProvider.Factory
```

### 3.3 数据模型（`com.lightdesk.core.model`）

```kotlin
data class NoteItem(id, title, content = "", deadline: Long? = null, done = false, completedAt: Long? = null,
                    pinned = false, createdAt = 0L, updatedAt = 0L) {
    val isTodo: Boolean                       // deadline != null
    fun isOverdue(now: Long): Boolean
    val preview: String
}
data class Course(id, title, teacher, location, weekday /*1..7*/, startMinutes, endMinutes, weeks, note, colorSeed)
data class CourseTable(name, sourceFile, sourceType, importedAt, courses, semesterStart: String? = null) // ISO 日期，第 1 周周一
data class WeatherSnapshot(city, latitude, longitude, temperature, apparentTemperature, humidity, windSpeed,
                           weatherCode, condition: String /*中文*/, updatedAt, daily: List<DailyForecast>, source)
data class DailyForecast(date, weatherCode, condition, tempMax, tempMin, precipitationProbability)
enum class FeatureKey { HOME, NOTES, TIMETABLE, WEATHER }
data class FeatureVisit(feature, count = 0, lastUsedAt = 0)
data class DailyActivity(notesCreated = 0, notesCompleted = 0, visits = 0, weatherFetches = 0) { val total: Int }
data class UsageStats(visits, daily: Map<String, DailyActivity>, notesCreated, notesCompleted,
                      weatherConditions: Map<String, Long>, legacyItems) {
    fun visitCount(key): Long; fun lastUsedAt(key): Long; fun totalVisits(): Long
    fun dailyActivity(dateKey: String): DailyActivity; fun noteCompletionRate(): Int
}
data class AppSettings(weatherCity = "北京", weatherLatitude, weatherLongitude, countdownWarningMinutes = 1440,
                       weekStartMonday = true, themeMode = ThemeMode.SYSTEM, usageStatsEnabled = true,
                       themePalette: ThemePalette = ThemePalette.OCEAN)   // v1.2.0 新增，缺字段取默认
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ThemePalette { OCEAN, MINT, PEACH, LAVENDER, SAND, ROSE }   // v1.2.0 新增
```

课表日历计算（`com.lightdesk.core.model.TimetableCalendar`）：

```kotlin
fun parseIsoDate(text: String?): LocalDate?
fun toLocalDate(timestamp: Long, zone: ZoneId = systemDefault()): LocalDate
fun weekNumber(date: LocalDate, semesterStart: String?): Int?          // 学期第几周，无起始为 null
fun occursOn(course: Course, date: LocalDate, semesterStart: String?): Boolean
fun coursesOn(table: CourseTable?, date: LocalDate): List<Course>
fun coursesOn(table: CourseTable?, timestamp: Long): List<Course>
fun coursesInMonth(table: CourseTable?, month: YearMonth): Map<LocalDate, List<Course>>
fun ongoingOrNext(table: CourseTable?, timestamp: Long): Course?
fun startOfWeek(date: LocalDate): LocalDate
fun currentWeek(table: CourseTable?, timestamp: Long): Int?
```

### 3.4 仓储与统计（`com.lightdesk.core.data`）

```kotlin
interface NotesRepository {
    fun observeItems(): Flow<List<NoteItem>>
    suspend fun upsert(item: NoteItem): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>
    suspend fun get(id: String): NoteItem?
}
interface CourseRepository {
    fun observeTable(): Flow<CourseTable?>
    suspend fun replace(table: CourseTable): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
interface StatsRepository {
    fun observeStats(): Flow<UsageStats>
    suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit>
    suspend fun recordNoteCreated(at: Long): AppResult<Unit>
    suspend fun recordNoteCompleted(at: Long): AppResult<Unit>
    suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit>
    suspend fun reset(): AppResult<Unit>
}
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit>
}
interface WeatherRepository {
    fun observeCache(): Flow<WeatherSnapshot?>
    suspend fun cached(): WeatherSnapshot?
    suspend fun save(snapshot: WeatherSnapshot): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}

class StatsTracker(repository, scope, timeProvider) {
    fun trackVisit(feature: FeatureKey)
    fun trackNoteCreated()
    fun trackNoteCompleted()
    fun trackWeatherCondition(condition: String)   // 仅在线成功获取时调用，离线降级不计数
}
```

存储：`JsonFileStore<T>(file, serializer, defaultValue, logger, ioDispatcher)` + `StoreModels.kt`
（`NoteStore`/`CourseTableStore`/`UsageStatsStore`/`SettingsStore`/`WeatherCacheStore`，`schemaVersion = 2`）。

### 3.5 课表导入（`com.lightdesk.feature.timetable.data`）

```kotlin
interface TimetableImporter {
    val sourceType: String
    fun importContent(content: String): AppResult<ImportedTable>
}
data class ImportedTable(courses, semesterStart: String?, calendarName: String?,
                         eventCount: Int, warnings: List<String>, sourceType: String)
class IcsParser(displayZone: ZoneId = systemDefault()) : TimetableImporter
sealed interface IcsParseResult {
    data class Success(courses, eventCount, warnings, semesterStart, calendarName) : IcsParseResult
    data class Failure(message: String) : IcsParseResult
}
```

### 3.6 功能模块入口（`:app` 调用签名）

```kotlin
HomeRoute(courseRepository, notesRepository, statsTracker, timeProvider, onOpenNotes, onOpenTimetable, modifier)
NotesRoute(notesRepository, statsTracker, timeProvider, modifier)
TimetableRoute(repository: CourseRepository, statsTracker, timeProvider, modifier)
WeatherRoute(cacheRepository: WeatherRepository, settingsRepository, statsTracker, modifier)
StatsRoute(settingsRepository, statsRepository, appVersion: String, modifier)
```

### 3.7 课表缩放（`com.lightdesk.feature.timetable.TimetableZoom`，v1.2.0 起；v1.2.1 增加横向缩放）

```kotlin
object TimetableZoom {
    const val MIN_SCALE = 0.6f; const val MAX_SCALE = 2.0f; const val DEFAULT_SCALE = 1.0f
    const val STEP = 1.25f
    const val BASE_HOUR_HEIGHT_DP = 64f; const val MIN_HOUR_HEIGHT_DP = 24f
    const val COMPACT_SCALE_THRESHOLD = 0.85f
    // v1.2.1 新增：横向尺寸与字号也跟随同一缩放比例
    const val BASE_DAY_COLUMN_WIDTH_DP = 96f
    const val BASE_TIME_AXIS_WIDTH_DP = 48f
    const val MIN_TIME_AXIS_WIDTH_DP = 36f
    const val MIN_TEXT_SCALE = 0.82f
    const val MAX_TEXT_SCALE = 1.5f
    fun clamp(scale: Float): Float
    fun zoomBy(current: Float, factor: Float): Float
    fun zoomIn(current: Float): Float; fun zoomOut(current: Float): Float
    fun canZoomIn(scale: Float): Boolean; fun canZoomOut(scale: Float): Boolean
    fun overviewScale(): Float
    fun isOverview(scale: Float): Boolean; fun isDefault(scale: Float): Boolean
    fun isCompact(scale: Float): Boolean
    fun scaleLabel(scale: Float): String          // "全览" / "标准" / "150%"
    fun hourHeightDp(scale: Float, base: Float = BASE_HOUR_HEIGHT_DP): Float
    fun gridHeightDp(totalMinutes: Int, scale: Float, base: Float = BASE_HOUR_HEIGHT_DP): Float
    fun dayColumnWidthDp(scale: Float, base: Float = BASE_DAY_COLUMN_WIDTH_DP): Float
    fun timeAxisWidthDp(scale: Float, base: Float = BASE_TIME_AXIS_WIDTH_DP, min: Float = MIN_TIME_AXIS_WIDTH_DP): Float
    fun textScale(scale: Float): Float            // 0.82f..1.5f
}
```

> 约定：缩放只改变尺寸与字号，不改变课程在时间轴上的位置（`startMinutes`/`endMinutes` 不变）；
> 横向与纵向必须使用同一个 `scale`，避免只在单一方向缩放。`TimetableScreen` 通过私有
> `TextStyle.scaled(factor)` 复刻字号/行高，其余字体属性仍由主题控制。

## 4. 设计系统（`com.lightdesk.core.designsystem`）

主题：`LightDeskTheme(darkTheme, palette = ThemePalette.OCEAN) { ... }`（`:app` 已包裹，功能模块直接用 Material3 组件；
页面只使用语义色，**不要硬编码色值**）。v1.2.0 新增：

```kotlin
// Palette.kt
data class LightDeskPalette(primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary,
                            secondaryContainer, onSecondaryContainer, background, onBackground, surface,
                            surfaceVariant, onSurfaceVariant, outline, outlineVariant, accentSoft: Color)
object LightDeskPalettes { fun of(palette: ThemePalette, dark: Boolean): LightDeskPalette
                           fun lightOf(palette: ThemePalette): LightDeskPalette
                           fun darkOf(palette: ThemePalette): LightDeskPalette }
fun ThemePalette.label(): String                 // 海盐蓝 / 薄荷绿 / 蜜桃橙 / 薰衣草 / 暖沙金 / 樱花粉
val ThemePaletteOptions: List<ThemePalette>
val LocalLightDeskPalette: ProvidableCompositionLocal<LightDeskPalette>   // 取 accentSoft 等扩展色
val LocalLightDeskDarkTheme: ProvidableCompositionLocal<Boolean>          // v1.2.1：需要按深浅色取色的纯函数用

// HeatmapColors.kt（v1.2.1，热力图 5 档顺序色阶；纯函数，便于单测）
object HeatmapColors {
    const val LEVEL_COUNT = 5
    fun levelColor(level: Int, palette: LightDeskPalette, dark: Boolean): Color
}

// CourseColors.kt（课程/事项 pastel 色块，12 色）
object CourseColors {
    val size: Int
    fun indexForSeed(seed: Int): Int
    fun lightContainer(seed: Int): Color; fun darkContainer(seed: Int): Color
    @Composable @ReadOnlyComposable fun container(seed: Int): Color
    @Composable @ReadOnlyComposable fun onContainer(seed: Int): Color
    @Composable @ReadOnlyComposable fun accent(seed: Int): Color
    fun containerFor(seed: Int, dark: Boolean): Color
    fun onContainerFor(seed: Int, dark: Boolean): Color
    fun accentFor(seed: Int, dark: Boolean): Color
}
```

```kotlin
ScreenHeader(title, modifier, subtitle, actions)
SectionCard(modifier, contentPadding) { ... }
SectionTitle(text, modifier, trailing)
EmptyState(title, modifier, description, icon, actionLabel, onAction)
LoadingView(modifier, text)
ErrorBanner(message, modifier, actionLabel, onAction)
InfoBanner(message, modifier, actionLabel, onAction)
ConfirmDialog(title, message, onConfirm, onDismiss, confirmText, dismissText, destructive)
PrimaryButton(text, onClick, modifier, enabled, loading, icon)
SecondaryButton(text, onClick, modifier, enabled, icon)
SettingSwitchRow(title, checked, onCheckedChange, modifier, description, enabled)
SettingValueRow(label, value, modifier, onClick)
TagPill(text, modifier, color)
TextAction(text, onClick, modifier, color, enabled)
val PageHorizontalPadding: Dp
val LocalStatusColors.current: StatusColors   // success / warning / danger / info / muted
```

排版：`LightDeskTypography` 不指定字体族（交给系统默认中文字体），如需品牌字体在此集中替换。
图标：`compose-material-icons-extended`，直接 `Icons.Outlined.Xxx`。

## 5. 模块内推荐结构

```
feature/xxx/
├─ XxxRoute.kt          # 对外唯一入口：@Composable fun XxxRoute(...)
├─ XxxViewModel.kt      # UiState + 动作
├─ XxxScreen.kt         # 纯 UI
├─ XxxLogic.kt / XxxPresentation.kt   # 无 Android 依赖的纯逻辑（JVM 可测）
├─ data/                # 仅该功能使用的解析器/网络客户端（不得依赖其它 feature）
└─ src/test/            # JVM 单元测试（纯逻辑，不依赖 Android）
```

## 6. 验收标准

1. `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gw.ps1 :feature:<name>:testDebugUnitTest :feature:<name>:compileDebugKotlin` 通过。
2. 页面可用：加载态、空态、错误态齐全；操作有反馈；深色模式可读。
3. 数据持久化：重启后数据仍在（必须真正调用 Repository，不得只存内存）。
4. 统计：业务事件用 `StatsTracker` 记录，页面访问不要重复埋点。
5. 无硬编码地址/密钥/魔法值；无未处理异常；无 TODO 占位。
6. 纯逻辑（解析、排序、倒计时计算等）有 JVM 单测，整体由 `scripts/build-release.ps1` 在打包前统一跑全量测试。
