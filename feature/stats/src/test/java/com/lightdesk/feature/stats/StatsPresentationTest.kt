package com.lightdesk.feature.stats

import com.lightdesk.core.common.CalendarUtils
import com.lightdesk.core.model.DailyActivity
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.FeatureVisit
import com.lightdesk.core.model.ThemePalette
import com.lightdesk.core.model.UsageStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置与统计页展示逻辑的单测（纯 JVM，无 Android 依赖）。
 *
 * 覆盖：热力图分级边界、天气排序与占比、备忘完成率、recentWeeks 维度、
 * 概览/今日/最近 7 天数据换算、时间文案、主题配色选项与提示文案。
 */
class StatsPresentationTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun date(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)

    private fun dateKey(date: LocalDate): String = CalendarUtils.dateKey(date)

    private fun epochMillis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------------
    // 热力图分级
    // ------------------------------------------------------------------

    @Test
    fun `heatLevel maps boundaries to five levels`() {
        assertEquals(0, HeatmapConfig.heatLevel(-5))
        assertEquals(0, HeatmapConfig.heatLevel(0))
        assertEquals(1, HeatmapConfig.heatLevel(1))
        assertEquals(1, HeatmapConfig.heatLevel(2))
        assertEquals(2, HeatmapConfig.heatLevel(3))
        assertEquals(2, HeatmapConfig.heatLevel(5))
        assertEquals(3, HeatmapConfig.heatLevel(6))
        assertEquals(3, HeatmapConfig.heatLevel(9))
        assertEquals(4, HeatmapConfig.heatLevel(10))
        assertEquals(4, HeatmapConfig.heatLevel(99))
    }

    @Test
    fun `heatLevel never exceeds level count`() {
        val maxLevel = HeatmapConfig.LEVEL_COUNT - 1
        assertEquals(maxLevel, HeatmapConfig.heatLevel(Int.MAX_VALUE))
        assertEquals(HeatmapConfig.LEVEL_COUNT, HeatmapConfig.levelAlphas.size)
        assertEquals(maxLevel, HeatmapConfig.levelAlphas.lastIndex)
    }

    // ------------------------------------------------------------------
    // 日历维度
    // ------------------------------------------------------------------

    @Test
    fun `recentWeeks returns 12 weeks of monday to sunday ending this week`() {
        // 2026-10-20 是周二
        val today = date(2026, 10, 20)
        val weeks = CalendarUtils.recentWeeks(today, HeatmapConfig.WEEKS)

        assertEquals(HeatmapConfig.WEEKS, weeks.size)
        weeks.forEach { week ->
            assertEquals(7, week.size)
            assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        }
        // 升序且连续
        weeks.flatten().zipWithNext { previous, next ->
            assertEquals(previous.plusDays(1), next)
        }
        // 最后一行是本周，包含今天
        assertTrue(weeks.last().contains(today))
        // 第一行是 11 周前的周一（本周周一 = 今天 - 1 天）
        assertEquals(today.minusDays(1).minusDays(11L * 7L), weeks.first().first())
    }

    @Test
    fun `buildHeatmap produces 12 rows and 7 columns with levels from daily totals`() {
        val today = date(2026, 10, 20)
        val yesterday = today.minusDays(1)
        val stats = UsageStats(
            daily = mapOf(
                dateKey(today) to DailyActivity(visits = 7),
                dateKey(yesterday) to DailyActivity(notesCreated = 3, weatherFetches = 1),
            ),
        )

        val heatmap = StatsPresentation.buildHeatmap(stats, today)
        assertEquals(HeatmapConfig.WEEKS, heatmap.size)
        heatmap.forEach { assertEquals(7, it.days.size) }

        val cells = heatmap.flatMap { it.days }
        val todayCell = cells.single { it.date == today }
        assertEquals(7, todayCell.total)
        assertEquals(3, todayCell.level)
        assertFalse(todayCell.isFuture)

        val yesterdayCell = cells.single { it.date == yesterday }
        assertEquals(4, yesterdayCell.total)
        assertEquals(2, yesterdayCell.level)

        val emptyCell = cells.first { it.date.isBefore(yesterday) }
        assertEquals(0, emptyCell.total)
        assertEquals(0, emptyCell.level)

        // 本周日（含未来日期）被标记为 future
        val currentWeek = heatmap.last()
        assertTrue(currentWeek.days.any { it.isFuture })
    }

    @Test
    fun `recentDayTotals returns 7 ascending days ending today`() {
        val today = date(2026, 10, 20)
        val stats = UsageStats(
            daily = mapOf(
                dateKey(today) to DailyActivity(visits = 2, notesCreated = 1),
                dateKey(today.minusDays(6)) to DailyActivity(weatherFetches = 3),
            ),
        )

        val days = StatsPresentation.recentDayTotals(stats, today)
        assertEquals(HeatmapConfig.RECENT_DAYS, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(today, days.last().date)
        assertEquals(3, days.first().total)
        assertEquals(3, days.last().total)
        days.zipWithNext { previous, next ->
            assertEquals(previous.date.plusDays(1), next.date)
        }
    }

    // ------------------------------------------------------------------
    // 天气统计
    // ------------------------------------------------------------------

    @Test
    fun `weather conditions sorted by count desc with percentage share`() {
        val stats = UsageStats(
            weatherConditions = mapOf(
                "晴" to 5L,
                "多云" to 3L,
                "雨" to 8L,
                "" to 2L,
            ),
        )

        val rows = StatsPresentation.buildWeatherRows(stats)

        assertEquals(
            listOf("雨", "晴", "多云", StatsPresentation.UNKNOWN_CONDITION_NAME),
            rows.map { it.displayName },
        )
        assertEquals(listOf(8L, 5L, 3L, 2L), rows.map { it.count })
        assertEquals(listOf(44, 27, 16, 11), rows.map { it.percent })
        assertEquals(1f, rows.first().progress, 0.0001f)
        assertEquals(0.25f, rows.last().progress, 0.0001f)
    }

    @Test
    fun `weather conditions ignore non positive counts and empty map`() {
        assertTrue(StatsPresentation.buildWeatherRows(UsageStats()).isEmpty())
        val rows = StatsPresentation.buildWeatherRows(
            UsageStats(weatherConditions = mapOf("晴" to 0L, "雨" to -2L, "雪" to 1L)),
        )
        assertEquals(1, rows.size)
        assertEquals("雪", rows.single().displayName)
        assertEquals(100, rows.single().percent)
    }

    // ------------------------------------------------------------------
    // 备忘统计
    // ------------------------------------------------------------------

    @Test
    fun `noteCompletionRate handles zero and clamps to range`() {
        assertEquals(0, UsageStats().noteCompletionRate())
        assertEquals(40, UsageStats(notesCreated = 10L, notesCompleted = 4L).noteCompletionRate())
        assertEquals(100, UsageStats(notesCreated = 2L, notesCompleted = 5L).noteCompletionRate())
    }

    @Test
    fun `noteSummary derives rate and progress`() {
        val summary = StatsPresentation.noteSummary(
            UsageStats(notesCreated = 10L, notesCompleted = 4L),
        )
        assertEquals(10L, summary.total)
        assertEquals(4L, summary.completed)
        assertEquals(40, summary.rate)
        assertEquals(0.4f, summary.progress, 0.0001f)

        val empty = StatsPresentation.noteSummary(UsageStats())
        assertEquals(0L, empty.total)
        assertEquals(0, empty.rate)
        assertEquals(0f, empty.progress, 0.0001f)
    }

    // ------------------------------------------------------------------
    // 使用概览 / 今日 / 空态判断
    // ------------------------------------------------------------------

    @Test
    fun `feature rows keep order and show zero when no records`() {
        val today = date(2026, 10, 20)
        val rows = StatsPresentation.buildFeatureRows(UsageStats(), today, zone)

        assertEquals(StatsPresentation.featureOrder, rows.map { it.feature })
        assertEquals(4, rows.size)
        rows.forEach { row ->
            assertEquals(0L, row.count)
            assertEquals(StatsPresentation.NO_RECORD_TEXT, row.lastUsedText)
            assertEquals(0f, row.relativeProgress, 0.0001f)
            assertEquals(0, row.percentOfTotal)
        }
    }

    @Test
    fun `feature rows use real counts and last used text`() {
        val today = date(2026, 10, 20)
        val stats = UsageStats(
            visits = listOf(
                FeatureVisit(FeatureKey.HOME, count = 10L, lastUsedAt = epochMillis(today, 12, 30)),
                FeatureVisit(FeatureKey.NOTES, count = 5L, lastUsedAt = epochMillis(date(2026, 10, 19), 12, 30)),
                FeatureVisit(FeatureKey.TIMETABLE, count = 0L),
                FeatureVisit(FeatureKey.WEATHER, count = 0L),
            ),
        )

        val rows = StatsPresentation.buildFeatureRows(stats, today, zone)
        assertEquals(15L, StatsPresentation.totalVisits(stats))
        assertEquals(listOf(10L, 5L, 0L, 0L), rows.map { it.count })
        assertEquals("今天 12:30", rows[0].lastUsedText)
        assertEquals("10-19 12:30", rows[1].lastUsedText)
        assertEquals(StatsPresentation.NO_RECORD_TEXT, rows[2].lastUsedText)
        assertEquals(1f, rows[0].relativeProgress, 0.0001f)
        assertEquals(0.5f, rows[1].relativeProgress, 0.0001f)
        assertEquals(66, rows[0].percentOfTotal)
        assertEquals(33, rows[1].percentOfTotal)
    }

    @Test
    fun `lastUsedText handles zero older dates and future dates`() {
        val today = date(2026, 10, 20)
        assertEquals(
            StatsPresentation.NO_RECORD_TEXT,
            StatsPresentation.lastUsedText(0L, today, zone),
        )
        assertEquals(
            StatsPresentation.NO_RECORD_TEXT,
            StatsPresentation.lastUsedText(-1L, today, zone),
        )
        assertEquals(
            "今天 08:05",
            StatsPresentation.lastUsedText(epochMillis(today, 8, 5), today, zone),
        )
        // 未来时间按「MM-dd HH:mm」展示，不视为今天
        assertEquals(
            "10-21 09:00",
            StatsPresentation.lastUsedText(epochMillis(date(2026, 10, 21), 9, 0), today, zone),
        )
    }

    @Test
    fun `todaySummary reads daily activity for today only`() {
        val today = date(2026, 10, 20)
        val stats = UsageStats(
            daily = mapOf(
                dateKey(today) to DailyActivity(
                    notesCreated = 3,
                    notesCompleted = 2,
                    visits = 5,
                    weatherFetches = 1,
                ),
                dateKey(today.minusDays(1)) to DailyActivity(visits = 100),
            ),
        )

        val summary = StatsPresentation.todaySummary(stats, today)
        assertEquals(5, summary.visits)
        assertEquals(3, summary.notesCreated)
        assertEquals(2, summary.notesCompleted)
        assertEquals(1, summary.weatherFetches)
        assertEquals(11, summary.total)
    }

    @Test
    fun `hasAnyStats is false only for empty stats`() {
        assertFalse(StatsPresentation.hasAnyStats(UsageStats()))
        assertTrue(
            StatsPresentation.hasAnyStats(
                UsageStats(visits = listOf(FeatureVisit(FeatureKey.HOME, count = 1L))),
            ),
        )
        assertTrue(
            StatsPresentation.hasAnyStats(
                UsageStats(daily = mapOf("2026-10-20" to DailyActivity(visits = 1))),
            ),
        )
        assertTrue(StatsPresentation.hasAnyStats(UsageStats(weatherConditions = mapOf("晴" to 1L))))
        assertTrue(StatsPresentation.hasAnyStats(UsageStats(notesCompleted = 1L)))
    }

    // ------------------------------------------------------------------
    // 主题配色
    // ------------------------------------------------------------------

    @Test
    fun `themePaletteOptions covers all palettes in declaration order`() {
        val options = StatsPresentation.themePaletteOptions()

        assertEquals(ThemePalette.entries.toList(), options.map { it.palette })
        assertEquals(
            listOf("海盐蓝", "薄荷绿", "蜜桃橙", "薰衣草", "暖沙金", "樱花粉"),
            options.map { it.label },
        )
    }

    @Test
    fun `themePaletteOptions labels are unique and non blank`() {
        val labels = StatsPresentation.themePaletteOptions().map { it.label }

        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
    }

    @Test
    fun `themePaletteSummary names current palette`() {
        val expected = mapOf(
            ThemePalette.OCEAN to "海盐蓝",
            ThemePalette.MINT to "薄荷绿",
            ThemePalette.PEACH to "蜜桃橙",
            ThemePalette.LAVENDER to "薰衣草",
            ThemePalette.SAND to "暖沙金",
            ThemePalette.ROSE to "樱花粉",
        )
        expected.forEach { (palette, label) ->
            assertEquals("当前配色：$label", StatsPresentation.themePaletteSummary(palette))
        }
    }
}
