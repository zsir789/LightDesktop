package com.lightdesk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TimetableCalendarTest {

    private val semesterStart = LocalDate.of(2026, 9, 7) // 周一，第 1 周

    private fun course(
        id: String = "c1",
        weekday: Int = 1,
        start: Int = 8 * 60,
        end: Int = 9 * 60 + 40,
        weeks: List<Int> = emptyList(),
    ) = Course(
        id = id,
        title = "高等数学",
        weekday = weekday,
        startMinutes = start,
        endMinutes = end,
        weeks = weeks,
    )

    private fun table(
        start: String? = semesterStart.toString(),
        vararg courses: Course,
    ) = CourseTable(semesterStart = start, courses = courses.toList())

    @Test
    fun `weekNumber 从第 1 周周一起算`() {
        assertEquals(1, TimetableCalendar.weekNumber(semesterStart, semesterStart))
        assertEquals(1, TimetableCalendar.weekNumber(semesterStart.plusDays(6), semesterStart))
        assertEquals(2, TimetableCalendar.weekNumber(semesterStart.plusDays(7), semesterStart))
        assertEquals(10, TimetableCalendar.weekNumber(semesterStart.plusWeeks(9), semesterStart))
        assertNull(TimetableCalendar.weekNumber(semesterStart.minusDays(1), semesterStart))
        assertNull(TimetableCalendar.weekNumber(semesterStart, null))
    }

    @Test
    fun `parseIsoDate 解析合法日期并容错`() {
        assertEquals(LocalDate.of(2026, 9, 7), TimetableCalendar.parseIsoDate("2026-09-07"))
        assertNull(TimetableCalendar.parseIsoDate(null))
        assertNull(TimetableCalendar.parseIsoDate("  "))
        assertNull(TimetableCalendar.parseIsoDate("2026/09/07"))
    }

    @Test
    fun `occursOn 同时校验星期与教学周`() {
        val mon = semesterStart // 第 1 周周一
        // 周一有课
        assertTrue(TimetableCalendar.occursOn(course(weekday = 1), mon, semesterStart))
        // 周二同一门课不上
        assertFalse(TimetableCalendar.occursOn(course(weekday = 1), mon.plusDays(1), semesterStart))
        // 仅第 2 周的单双周课程：第 1 周不上，第 2 周上
        val oddWeek = course(weekday = 1, weeks = listOf(1, 3, 5))
        assertTrue(TimetableCalendar.occursOn(oddWeek, mon, semesterStart))
        assertFalse(TimetableCalendar.occursOn(oddWeek, mon.plusWeeks(1), semesterStart))
        assertTrue(TimetableCalendar.occursOn(oddWeek, mon.plusWeeks(2), semesterStart))
    }

    @Test
    fun `没有学期起始时忽略 weeks 过滤按每周有课降级`() {
        val weekly = table(start = null, course(weekday = 1, weeks = listOf(3, 5)))
        assertEquals(1, TimetableCalendar.coursesOn(weekly, semesterStart).size)
        assertEquals(1, TimetableCalendar.coursesOn(weekly, semesterStart.plusWeeks(7)).size)
    }

    @Test
    fun `coursesOn 按开始时间排序且忽略空课表`() {
        val morning = course(id = "morning", start = 8 * 60, end = 9 * 60)
        val evening = course(id = "evening", start = 19 * 60, end = 20 * 60)
        val table = table(start = semesterStart.toString(), evening, morning)
        val courses = TimetableCalendar.coursesOn(table, semesterStart)
        assertEquals(listOf("morning", "evening"), courses.map { it.id })
        assertTrue(TimetableCalendar.coursesOn(null, semesterStart).isEmpty())
    }

    @Test
    fun `coursesInMonth 返回整月有课日期映射`() {
        val table = table(
            start = semesterStart.toString(),
            course(weekday = 1, start = 8 * 60, end = 9 * 60),
            course(id = "c2", weekday = 3, start = 10 * 60, end = 11 * 60),
        )
        val month = TimetableCalendar.coursesInMonth(table, YearMonth.of(2026, 9))
        // 9/7 起每周一、三有课：9/7 周一、9/9 周三、9/14、9/16、9/21、9/23、9/28、9/30
        assertEquals(8, month.size)
        assertTrue(month.containsKey(LocalDate.of(2026, 9, 7)))
        assertTrue(month.containsKey(LocalDate.of(2026, 9, 9)))
        assertTrue(month.containsKey(LocalDate.of(2026, 9, 30)))
        assertFalse(month.containsKey(LocalDate.of(2026, 9, 8)))
        assertEquals(1, month.getValue(LocalDate.of(2026, 9, 7)).size)
    }

    @Test
    fun `ongoingOrNext 返回进行中或下一节课`() {
        val table = table(
            start = semesterStart.toString(),
            course(id = "a", start = 8 * 60, end = 9 * 60),
            course(id = "b", start = 10 * 60, end = 11 * 60),
        )
        // 第 1 周周一 08:30 -> 正在上 a
        val morning = semesterStart.atTime(8, 30).toEpochMilliSecond()
        assertEquals("a", TimetableCalendar.ongoingOrNext(table, morning)?.id)
        // 09:30 -> a 已结束，下一节 b
        val between = semesterStart.atTime(9, 30).toEpochMilliSecond()
        assertEquals("b", TimetableCalendar.ongoingOrNext(table, between)?.id)
        // 11:30 -> 今天的课都结束了
        val evening = semesterStart.atTime(11, 30).toEpochMilliSecond()
        assertNull(TimetableCalendar.ongoingOrNext(table, evening))
    }

    @Test
    fun `currentWeek 无表或跨学期前返回空`() {
        val table = table(start = semesterStart.toString(), course())
        val ts = semesterStart.plusWeeks(2).atTime(9, 0).toEpochMilliSecond()
        assertEquals(3, TimetableCalendar.currentWeek(table, ts))
        assertNull(TimetableCalendar.currentWeek(null, ts))
        assertNull(TimetableCalendar.currentWeek(table(start = null), ts))
    }

    private fun LocalDate.atTime(hour: Int, minute: Int): java.time.LocalDateTime =
        atTime(hour, minute, 0)

    private fun java.time.LocalDateTime.toEpochMilliSecond(): Long =
        atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
