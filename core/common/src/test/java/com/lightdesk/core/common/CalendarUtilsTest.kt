package com.lightdesk.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarUtilsTest {

    @Test
    fun `monthGrid 每行 7 格且月外补空`() {
        // 2026 年 9 月 1 日是周二，周一列应补 null
        val grid = CalendarUtils.monthGrid(YearMonth.of(2026, 9))
        assertTrue(grid.isNotEmpty())
        grid.forEach { assertEquals(7, it.size) }
        assertNull(grid.first().first())
        assertEquals(LocalDate.of(2026, 9, 1), grid.first()[1])
        val flat = grid.flatten().filterNotNull()
        assertEquals(30, flat.size)
        assertEquals(LocalDate.of(2026, 9, 30), flat.last())
    }

    @Test
    fun `monthGrid 月初为周一时不留空`() {
        // 2026 年 6 月 1 日是周一
        val grid = CalendarUtils.monthGrid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first().first())
    }

    @Test
    fun `weekOf 返回周一到周日`() {
        val date = LocalDate.of(2026, 10, 21) // 周三
        val week = CalendarUtils.weekOf(date)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 19), week.first())
        assertEquals(LocalDate.of(2026, 10, 25), week.last())
        assertTrue(week.contains(date))
    }

    @Test
    fun `recentWeeks 升序且最后一行包含本周`() {
        val today = LocalDate.of(2026, 10, 21)
        val weeks = CalendarUtils.recentWeeks(today, 4)
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 10, 25), weeks.last().last())
        assertEquals(7, weeks.last().size)
    }

    @Test
    fun `recentWeeks 参数为 0 或 1 时边界安全`() {
        val today = LocalDate.of(2026, 10, 21)
        assertTrue(CalendarUtils.recentWeeks(today, 0).isEmpty())
        assertEquals(1, CalendarUtils.recentWeeks(today, 1).size)
        assertEquals(today, CalendarUtils.recentWeeks(today, 1).first()[2])
    }

    @Test
    fun `dateKey 使用 ISO 日期`() {
        assertEquals("2026-10-21", CalendarUtils.dateKey(LocalDate.of(2026, 10, 21)))
        val timestamp = LocalDate.of(2026, 10, 21)
            .atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals("2026-10-21", CalendarUtils.dateKey(timestamp))
    }
}
