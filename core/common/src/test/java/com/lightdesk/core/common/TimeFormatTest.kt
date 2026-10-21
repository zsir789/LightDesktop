package com.lightdesk.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `分钟数转时钟文本`() {
        assertEquals("00:00", TimeFormat.minutesToClock(0))
        assertEquals("08:05", TimeFormat.minutesToClock(485))
        assertEquals("23:59", TimeFormat.minutesToClock(1439))
        assertEquals("00:00", TimeFormat.minutesToClock(1440))
    }

    @Test
    fun `时钟文本转分钟数`() {
        assertEquals(485, TimeFormat.clockToMinutes("08:05"))
        assertEquals(0, TimeFormat.clockToMinutes("00:00"))
        assertNull(TimeFormat.clockToMinutes("25:00"))
        assertNull(TimeFormat.clockToMinutes("8:5:1"))
        assertNull(TimeFormat.clockToMinutes("abc"))
    }

    @Test
    fun `倒计时文本包含天与时分秒`() {
        val twoDays = 2L * 86_400_000 + 3L * 3_600_000 + 12L * 60_000 + 9L * 1000
        assertEquals("2天 03:12:09", TimeFormat.countdownText(twoDays))
        assertEquals("00:00:10", TimeFormat.countdownText(10_000))
        assertEquals("已超时 00:00:05", TimeFormat.countdownText(-5_000))
    }

    @Test
    fun `截止时间提示`() {
        val now = 1_000_000L
        assertEquals("已超时", TimeFormat.deadlineHint(now - 1, now))
        assertEquals("即将截止", TimeFormat.deadlineHint(now + 30_000, now))
        assertEquals("5 分钟后截止", TimeFormat.deadlineHint(now + 5 * 60_000, now))
        assertEquals("2 小时后截止", TimeFormat.deadlineHint(now + 2 * 3_600_000, now))
        assertEquals("3 天后截止", TimeFormat.deadlineHint(now + 3 * 86_400_000, now))
    }

    @Test
    fun `星期标签`() {
        assertEquals("周一", TimeFormat.weekdayLabel(1))
        assertEquals("周日", TimeFormat.weekdayLabel(7))
        assertEquals("未知", TimeFormat.weekdayLabel(0))
    }
}
