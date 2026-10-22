package com.lightdesk.feature.home

import com.lightdesk.core.common.TimeFormat
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.NoteItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页展示逻辑单测：日期/问候、今日待办筛选与排序、倒计时文案、今日课程与高亮。
 */
class HomePresentationTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2026-10-19 是周一。 */
    private val today: LocalDate = LocalDate.of(2026, 10, 19)

    /** 测试基准时刻：2026-10-19 09:00。 */
    private val now: Long = at(today, 9, 0)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun todo(
        id: String,
        deadline: Long?,
        done: Boolean = false,
        createdAt: Long = 0L,
    ): NoteItem = NoteItem(
        id = id,
        title = "待办-$id",
        deadline = deadline,
        done = done,
        createdAt = createdAt,
    )

    private fun course(
        id: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        weekday: Int = 1,
    ): Course = Course(
        id = id,
        title = "课程-$id",
        weekday = weekday,
        startMinutes = startHour * 60 + startMinute,
        endMinutes = endHour * 60 + endMinute,
    )

    // ---- 问候与日期 ----

    @Test
    fun `greeting follows hour of day`() {
        assertEquals("早上好", HomePresentation.greeting(6))
        assertEquals("早上好", HomePresentation.greeting(8))
        assertEquals("上午好", HomePresentation.greeting(9))
        assertEquals("上午好", HomePresentation.greeting(11))
        assertEquals("中午好", HomePresentation.greeting(12))
        assertEquals("中午好", HomePresentation.greeting(13))
        assertEquals("下午好", HomePresentation.greeting(14))
        assertEquals("下午好", HomePresentation.greeting(17))
        assertEquals("晚上好", HomePresentation.greeting(18))
        assertEquals("晚上好", HomePresentation.greeting(23))
        assertEquals("晚上好", HomePresentation.greeting(2))
    }

    @Test
    fun `date label shows month day and weekday`() {
        assertEquals("10月19日 周一", HomePresentation.dateLabel(LocalDate.of(2026, 10, 19)))
        assertEquals("1月1日 周四", HomePresentation.dateLabel(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `timestamp helpers use the given zone`() {
        assertEquals(9, HomePresentation.hourOfDay(now, zone))
        assertEquals(9 * 60, HomePresentation.minutesOfDay(now, zone))
        assertEquals(today, HomePresentation.localDate(now, zone))
    }

    // ---- 今日待办 ----

    @Test
    fun `today todos keep today and overdue items only`() {
        val items = listOf(
            todo("today", at(today, 18, 0)),
            todo("overdue", at(today.minusDays(1), 18, 0)),
            todo("tomorrow", at(today.plusDays(1), 9, 0)),
            todo("done", at(today, 12, 0), done = true),
            NoteItem(id = "memo", title = "没有截止时间的备忘"),
        )

        val ids = HomePresentation.todayTodos(items, now, zone).map { it.id }

        assertEquals(listOf("overdue", "today"), ids)
    }

    @Test
    fun `today todos are sorted by deadline then creation time`() {
        val items = listOf(
            todo("evening", at(today, 18, 0), createdAt = 1L),
            todo("noon", at(today, 12, 0), createdAt = 5L),
            todo("early", at(today.minusDays(2), 8, 0), createdAt = 3L),
            todo("noon-later-created", at(today, 12, 0), createdAt = 9L),
        )

        val ids = HomePresentation.todayTodos(items, now, zone).map { it.id }

        assertEquals(listOf("early", "noon", "noon-later-created", "evening"), ids)
    }

    @Test
    fun `todo ui shows countdown flags`() {
        val in90Minutes = HomePresentation.toTodoUi(todo("a", now + 90L * 60_000L), now)
        assertEquals("01:30:00", in90Minutes.countdownText)
        assertFalse(in90Minutes.overdue)
        assertFalse(in90Minutes.urgent)

        val in30Minutes = HomePresentation.toTodoUi(todo("b", now + 30L * 60_000L), now)
        assertTrue(in30Minutes.urgent)
        assertFalse(in30Minutes.overdue)

        val overdue = HomePresentation.toTodoUi(todo("c", now - 60_000L), now)
        assertTrue(overdue.overdue)
        assertFalse(overdue.urgent)
        assertEquals("已超时 00:01:00", overdue.countdownText)
    }

    @Test
    fun `pending todo count ignores finished items and memos`() {
        val items = listOf(
            todo("today", now + 60_000L),
            todo("future", at(today.plusDays(2), 10, 0)),
            todo("done", now, done = true),
            NoteItem(id = "memo", title = "备忘"),
        )

        assertEquals(2, HomePresentation.pendingTodoCount(items))
        assertEquals(0, HomePresentation.pendingTodoCount(emptyList()))
    }

    // ---- 今日课程 ----

    @Test
    fun `today courses contains only today sorted by start time`() {
        val table = CourseTable(
            courses = listOf(
                course("late", 14, 0, 15, 40),
                course("early", 8, 0, 9, 40),
                course("tuesday", 10, 0, 11, 40, weekday = 2),
            ),
        )

        val result = HomePresentation.todayCourses(table, now, zone)

        assertEquals(listOf("early", "late"), result.map { it.course.id })
        assertEquals(CourseStatus.ONGOING, result[0].status)
        assertEquals(CourseStatus.UPCOMING, result[1].status)
    }

    @Test
    fun `course status switches at class boundaries`() {
        val lesson = course("a", 9, 0, 10, 0)
        assertEquals(CourseStatus.UPCOMING, HomePresentation.courseStatus(lesson, 8 * 60 + 59))
        assertEquals(CourseStatus.ONGOING, HomePresentation.courseStatus(lesson, 9 * 60))
        assertEquals(CourseStatus.ONGOING, HomePresentation.courseStatus(lesson, 9 * 60 + 59))
        assertEquals(CourseStatus.FINISHED, HomePresentation.courseStatus(lesson, 10 * 60))
    }

    @Test
    fun `highlighted course is the ongoing one then the next one`() {
        val table = CourseTable(
            courses = listOf(
                course("morning", 8, 0, 9, 40),
                course("noon", 10, 0, 11, 40),
            ),
        )

        val duringFirst = HomePresentation.todayCourses(table, at(today, 9, 0), zone)
        assertEquals("morning", HomePresentation.highlightedCourseId(duringFirst))

        val betweenClasses = HomePresentation.todayCourses(table, at(today, 9, 40), zone)
        assertEquals("noon", HomePresentation.highlightedCourseId(betweenClasses))
        assertEquals(CourseStatus.UPCOMING, HomePresentation.highlighted(betweenClasses)?.status)

        val afterAll = HomePresentation.todayCourses(table, at(today, 12, 0), zone)
        assertNull(HomePresentation.highlightedCourseId(afterAll))
        assertNull(HomePresentation.highlighted(afterAll))
    }

    @Test
    fun `no timetable yields empty courses`() {
        assertTrue(HomePresentation.todayCourses(null, now, zone).isEmpty())
        assertTrue(HomePresentation.todayCourses(CourseTable(courses = emptyList()), now, zone).isEmpty())
        assertFalse(HomePresentation.hasTimetable(null))
        assertFalse(HomePresentation.hasTimetable(CourseTable(courses = emptyList())))
        assertTrue(HomePresentation.hasTimetable(CourseTable(courses = listOf(course("a", 8, 0, 9, 0)))))
    }

    // ---- 状态聚合 ----

    @Test
    fun `buildHomeUiState aggregates today data`() {
        val table = CourseTable(
            semesterStart = today.minusWeeks(1).toString(),
            courses = listOf(course("morning", 8, 0, 9, 40)),
        )
        val items = listOf(
            todo("today", at(today, 18, 0)),
            todo("done", at(today, 12, 0), done = true),
        )

        val state = buildHomeUiState(table = table, items = items, now = now, zone = zone)

        assertEquals(now, state.now)
        assertEquals("上午好", state.greeting)
        assertEquals("10月19日 周一", state.dateLabel)
        assertEquals(2, state.weekNumber)
        assertTrue(state.hasTimetable)
        assertEquals(listOf("morning"), state.courses.map { it.course.id })
        assertEquals(listOf("today"), state.todos.map { it.item.id })
        assertEquals(1, state.pendingTodoCount)
        assertEquals("morning", state.highlightedCourseId)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `buildHomeUiState without timetable has no week number`() {
        val state = buildHomeUiState(table = null, items = emptyList(), now = now, zone = zone)

        assertNull(state.weekNumber)
        assertFalse(state.hasTimetable)
        assertTrue(state.courses.isEmpty())
        assertTrue(state.todos.isEmpty())
        assertEquals(0, state.pendingTodoCount)
        assertEquals("上午好", state.greeting)
    }

    @Test
    fun `buildHomeUiState keeps error message`() {
        val state = buildHomeUiState(
            table = null,
            items = emptyList(),
            now = now,
            zone = zone,
            error = "读取课表失败：磁盘错误",
        )

        assertEquals("读取课表失败：磁盘错误", state.error)
    }

    @Test
    fun `countdown text formats days and hours`() {
        assertEquals("00:00:30", TimeFormat.countdownText(30_000L))
        assertEquals("1天 02:00:00", TimeFormat.countdownText(26L * 60L * 60L * 1000L))
    }

    // ---- 可配置提醒窗口 ----

    @Test
    fun `toTodoUi respects custom warning window`() {
        val item = todo("a", now + 90L * 60_000L)

        assertFalse(HomePresentation.toTodoUi(item, now, warningMillis = 60L * 60_000L).urgent)
        assertTrue(HomePresentation.toTodoUi(item, now, warningMillis = 120L * 60_000L).urgent)
        assertFalse(
            "关闭提醒窗口（<=0）后不再标记紧急",
            HomePresentation.toTodoUi(item, now, warningMillis = 0L).urgent,
        )
        assertTrue(
            HomePresentation.toTodoUi(todo("late", now - 1L), now, warningMillis = 0L).overdue,
        )
    }

    @Test
    fun `todo tag label follows warning minutes`() {
        assertEquals("已逾期", HomePresentation.todoTagLabel(overdue = true, urgent = false, warningMinutes = 30))
        assertEquals("30 分钟内", HomePresentation.todoTagLabel(false, true, 30))
        assertEquals("2 小时内", HomePresentation.todoTagLabel(false, true, 120))
        assertEquals("1 天内", HomePresentation.todoTagLabel(false, true, 24 * 60))
        assertEquals("3 天内", HomePresentation.todoTagLabel(false, true, 3 * 24 * 60))
        assertNull(HomePresentation.todoTagLabel(false, true, 0))
        assertNull("普通待办不显示标签", HomePresentation.todoTagLabel(false, false, 60))
    }

    @Test
    fun `warning window label handles boundaries`() {
        assertNull(HomePresentation.warningWindowLabel(0))
        assertNull(HomePresentation.warningWindowLabel(-5))
        assertEquals("1 分钟内", HomePresentation.warningWindowLabel(1))
        assertEquals("59 分钟内", HomePresentation.warningWindowLabel(59))
        assertEquals("1 小时内", HomePresentation.warningWindowLabel(60))
        assertEquals("1 天内", HomePresentation.warningWindowLabel(24 * 60))
    }

    @Test
    fun `buildHomeUiState applies warning minutes`() {
        val items = listOf(todo("soon", now + 30L * 60_000L))

        val state = buildHomeUiState(
            table = null,
            items = items,
            now = now,
            zone = zone,
            warningMinutes = 15,
        )

        assertEquals(15, state.warningMinutes)
        assertFalse("剩余 30 分钟，15 分钟窗口内不应紧急", state.todos.single().urgent)
    }
}
