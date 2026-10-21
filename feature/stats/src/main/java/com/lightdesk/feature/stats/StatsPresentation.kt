package com.lightdesk.feature.stats

import com.lightdesk.core.common.CalendarUtils
import com.lightdesk.core.designsystem.theme.ThemePaletteOptions
import com.lightdesk.core.designsystem.theme.label
import com.lightdesk.core.model.DailyActivity
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.ThemePalette
import com.lightdesk.core.model.UsageStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设置与统计页的纯展示逻辑（无 Android 依赖，方便 JVM 单测）。
 *
 * 所有“数据 → 文案/比例”的转换都集中在这里，Composable 只负责摆放。
 */
object StatsPresentation {

    /** 没有任何记录时的统一文案。 */
    const val NO_RECORD_TEXT = "暂无记录"

    /** 概览中功能的展示顺序（与主导航顺序一致）。 */
    val featureOrder: List<FeatureKey> = listOf(
        FeatureKey.HOME,
        FeatureKey.NOTES,
        FeatureKey.TIMETABLE,
        FeatureKey.WEATHER,
    )

    /** 功能中文名。 */
    fun featureName(feature: FeatureKey): String = when (feature) {
        FeatureKey.HOME -> "首页"
        FeatureKey.NOTES -> "备忘"
        FeatureKey.TIMETABLE -> "课表"
        FeatureKey.WEATHER -> "天气"
    }

    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 最近使用时间文案：
     * - 无记录（<= 0）→ [NO_RECORD_TEXT]
     * - 与 [today] 同一天 → 「今天 12:30」
     * - 其它（含未来时间）→ 「10-19 12:30」
     *
     * [today] 由调用方传入而不是内部取系统时间，保证可测试。
     */
    fun lastUsedText(lastUsedAt: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        if (lastUsedAt <= 0L) return NO_RECORD_TEXT
        return try {
            val dateTime = Instant.ofEpochMilli(lastUsedAt).atZone(zone).toLocalDateTime()
            if (dateTime.toLocalDate() == today) {
                "今天 ${dateTime.format(CLOCK_FORMATTER)}"
            } else {
                dateTime.format(DATE_TIME_FORMATTER)
            }
        } catch (_: Exception) {
            NO_RECORD_TEXT
        }
    }

    /** 0..1 的相对进度（用于概览小进度条），max<=0 时返回 0。 */
    fun relativeProgress(count: Long, max: Long): Float {
        if (max <= 0L) return 0f
        return ((count.toDouble() / max.toDouble()).coerceIn(0.0, 1.0)).toFloat()
    }

    /** 占总量百分比（四舍五入，0..100）。 */
    fun percentOfTotal(count: Long, total: Long): Int {
        if (total <= 0L || count <= 0L) return 0
        return ((count.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    // ---- 使用概览 ----

    /** 单个功能的使用概览行。 */
    data class FeatureUsageRow(
        val feature: FeatureKey,
        val count: Long,
        val lastUsedText: String,
        val relativeProgress: Float,
        val percentOfTotal: Int,
    )

    /** 总访问次数（各功能计数之和）。 */
    fun totalVisits(stats: UsageStats): Long = stats.totalVisits()

    /** 四个功能的概览行；无记录时 count = 0、文案为暂无记录。 */
    fun buildFeatureRows(
        stats: UsageStats,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<FeatureUsageRow> {
        val total = totalVisits(stats)
        val max = featureOrder.maxOf { stats.visitCount(it) }
        return featureOrder.map { feature ->
            val count = stats.visitCount(feature)
            FeatureUsageRow(
                feature = feature,
                count = count,
                lastUsedText = lastUsedText(stats.lastUsedAt(feature), today, zone),
                relativeProgress = relativeProgress(count, max),
                percentOfTotal = percentOfTotal(count, total),
            )
        }
    }

    // ---- 备忘统计 ----

    /** 备忘统计摘要。 */
    data class NoteSummary(
        val total: Long,
        val completed: Long,
        val rate: Int,
        val progress: Float,
    )

    fun noteSummary(stats: UsageStats): NoteSummary = NoteSummary(
        total = stats.notesCreated.coerceAtLeast(0L),
        completed = stats.notesCompleted.coerceAtLeast(0L),
        rate = stats.noteCompletionRate(),
        progress = (stats.noteCompletionRate() / 100f).coerceIn(0f, 1f),
    )

    // ---- 天气类型统计 ----

    /** 单个天气条件的使用次数与占比。 */
    data class WeatherConditionRow(
        val condition: String,
        val displayName: String,
        val count: Long,
        val percent: Int,
        val progress: Float,
    )

    /** 未知/空条件时的展示名。 */
    const val UNKNOWN_CONDITION_NAME = "未知天气"

    /**
     * 天气条件统计：按次数降序（次数相同按名称升序），并计算占比。
     *
     * 占比以“所有被统计天气条件的次数之和”为分母；次数 <= 0 的条目忽略。
     */
    fun buildWeatherRows(stats: UsageStats): List<WeatherConditionRow> {
        val entries = stats.weatherConditions
            .filter { (_, count) -> count > 0L }
            .toList()
        if (entries.isEmpty()) return emptyList()
        val total = entries.sumOf { it.second }
        val max = entries.maxOf { it.second }
        return entries
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first.ifBlank { UNKNOWN_CONDITION_NAME } })
            .map { (condition, count) ->
                WeatherConditionRow(
                    condition = condition,
                    displayName = condition.ifBlank { UNKNOWN_CONDITION_NAME },
                    count = count,
                    percent = percentOfTotal(count, total),
                    progress = relativeProgress(count, max),
                )
            }
    }

    // ---- 今日数据 ----

    /** 今日活动摘要（负数容错为 0）。 */
    data class TodaySummary(
        val visits: Int,
        val notesCreated: Int,
        val notesCompleted: Int,
        val weatherFetches: Int,
    ) {
        val total: Int
            get() = visits + notesCreated + notesCompleted + weatherFetches
    }

    fun todaySummary(stats: UsageStats, today: LocalDate): TodaySummary {
        val activity: DailyActivity = stats.dailyActivity(CalendarUtils.dateKey(today))
        return TodaySummary(
            visits = activity.visits.coerceAtLeast(0),
            notesCreated = activity.notesCreated.coerceAtLeast(0),
            notesCompleted = activity.notesCompleted.coerceAtLeast(0),
            weatherFetches = activity.weatherFetches.coerceAtLeast(0),
        )
    }

    // ---- 是否“完全没有任何统计” ----

    fun hasAnyStats(stats: UsageStats): Boolean {
        if (stats.legacyItems.isNotEmpty()) return true
        if (stats.notesCreated > 0L || stats.notesCompleted > 0L) return true
        if (stats.weatherConditions.values.any { it > 0L }) return true
        if (stats.visits.any { it.count > 0L }) return true
        return stats.daily.values.any { it.total > 0 }
    }

    // ---- 热力图 ----

    /** 一天的热力格子数据。 */
    data class HeatDay(
        val date: LocalDate,
        val total: Int,
        val level: Int,
        val isFuture: Boolean,
    )

    /** 一周（周一..周日）。 */
    data class HeatWeek(
        val monthLabel: String?,
        val days: List<HeatDay>,
    )

    /**
     * 近 [weeks] 周热力图：行 = 周（升序，最后一行本周），列 = 周一..周日。
     *
     * 每行最左侧可显示月份标签：当该周周一所在月份与上一行不同时显示「M 月」。
     */
    fun buildHeatmap(
        stats: UsageStats,
        today: LocalDate,
        weeks: Int = HeatmapConfig.WEEKS,
    ): List<HeatWeek> {
        if (weeks <= 0) return emptyList()
        var lastMonth = -1
        return CalendarUtils.recentWeeks(today, weeks).map { week ->
            val monday = week.first()
            val monthLabel = if (monday.monthValue != lastMonth) {
                lastMonth = monday.monthValue
                "${monday.monthValue} 月"
            } else {
                null
            }
            HeatWeek(
                monthLabel = monthLabel,
                days = week.map { date ->
                    val total = activityTotal(stats, date)
                    HeatDay(
                        date = date,
                        total = total,
                        level = HeatmapConfig.heatLevel(total),
                        isFuture = date.isAfter(today),
                    )
                },
            )
        }
    }

    /** 最近 N 天（含今天）的每日活动量小计，升序。 */
    data class RecentDayTotal(
        val date: LocalDate,
        val total: Int,
    )

    fun recentDayTotals(
        stats: UsageStats,
        today: LocalDate,
        days: Int = HeatmapConfig.RECENT_DAYS,
    ): List<RecentDayTotal> {
        if (days <= 0) return emptyList()
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            RecentDayTotal(date = date, total = activityTotal(stats, date))
        }
    }

    /** 配色选项：色值由 core:designsystem 提供，这里只负责“枚举 → 中文名”的映射。 */
    data class ThemePaletteOption(
        val palette: ThemePalette,
        val label: String,
    )

    /** 配色卡片的展示顺序与 [ThemePalette] 枚举声明顺序一致。 */
    fun themePaletteOptions(): List<ThemePaletteOption> =
        ThemePaletteOptions.map { palette -> ThemePaletteOption(palette, palette.label()) }

    /** 配色卡片底部的当前选中提示。 */
    fun themePaletteSummary(palette: ThemePalette): String = "当前配色：${palette.label()}"

    private fun activityTotal(stats: UsageStats, date: LocalDate): Int =
        stats.dailyActivity(CalendarUtils.dateKey(date)).total.coerceAtLeast(0)
}
