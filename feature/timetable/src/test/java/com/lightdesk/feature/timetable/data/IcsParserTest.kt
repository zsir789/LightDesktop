package com.lightdesk.feature.timetable.data

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    private val utcParser = IcsParser(ZoneOffset.UTC)
    private val shanghaiParser = IcsParser(ZoneId.of("Asia/Shanghai"))

    // ------------------------------------------------------------------
    // 基础解析：时间格式
    // ------------------------------------------------------------------

    @Test
    fun `parses event with tzid and maps to display zone`() {
        val content = calendar(
            listOf(
                "SUMMARY:高等数学",
                "LOCATION:教101",
                "DESCRIPTION:教师：张三",
                "DTSTART;TZID=Asia/Shanghai:20240902T080000",
                "DTEND;TZID=Asia/Shanghai:20240902T094000",
            ),
        )

        val result = success(shanghaiParser.parse(content))

        assertEquals(1, result.courses.size)
        val course = result.courses.first()
        assertEquals("高等数学", course.title)
        assertEquals("张三", course.teacher)
        assertEquals("教101", course.location)
        assertEquals(1, course.weekday)
        assertEquals(8 * 60, course.startMinutes)
        assertEquals(9 * 60 + 40, course.endMinutes)
        assertEquals(listOf(1), course.weeks)
    }

    @Test
    fun `converts utc ending z to display zone`() {
        val content = calendar(
            listOf(
                "SUMMARY:大学英语",
                "DTSTART:20240902T000000Z",
                "DTEND:20240902T014000Z",
            ),
        )

        val course = success(shanghaiParser.parse(content)).courses.single()

        assertEquals(1, course.weekday)
        assertEquals(8 * 60, course.startMinutes)
        assertEquals(9 * 60 + 40, course.endMinutes)
    }

    @Test
    fun `utc occurrence crossing midnight lands on next day`() {
        val content = calendar(
            listOf(
                "SUMMARY:凌晨课",
                "DTSTART:20240901T170000Z",
                "DTEND:20240901T184000Z",
            ),
        )

        val course = success(shanghaiParser.parse(content)).courses.single()

        assertEquals(1, course.weekday)
        assertEquals(60, course.startMinutes)
        assertEquals(160, course.endMinutes)
    }

    @Test
    fun `keeps floating time as written`() {
        val content = calendar(
            listOf(
                "SUMMARY:浮动时间课程",
                "DTSTART:20240902T080000",
                "DTEND:20240902T090000",
            ),
        )

        val course = success(shanghaiParser.parse(content)).courses.single()

        assertEquals(8 * 60, course.startMinutes)
        assertEquals(9 * 60, course.endMinutes)
        assertEquals(1, course.weekday)
    }

    @Test
    fun `maps value date to full day course`() {
        val content = calendar(
            listOf(
                "SUMMARY:国庆节",
                "DTSTART;VALUE=DATE:20240902",
                "DTEND;VALUE=DATE:20240903",
            ),
        )

        val course = success(shanghaiParser.parse(content)).courses.single()

        assertEquals(0, course.startMinutes)
        assertEquals(24 * 60, course.endMinutes)
        assertEquals(1, course.weekday)
    }

    @Test
    fun `uses default duration when dtend missing`() {
        val content = calendar(
            listOf(
                "SUMMARY:没有结束时间",
                "DTSTART:20240902T080000Z",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals(8 * 60, course.startMinutes)
        assertEquals(8 * 60 + IcsParser.DEFAULT_EVENT_DURATION_MINUTES, course.endMinutes)
    }

    // ------------------------------------------------------------------
    // 文本处理：BOM / CRLF / 折叠行
    // ------------------------------------------------------------------

    @Test
    fun `unfolds folded summary line with crlf and bom`() {
        val lines = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "BEGIN:VEVENT",
            "SUMMARY:高等数",
            " 学",
            "DTSTART:20240902T080000Z",
            "DTEND:20240902T094000Z",
            "END:VEVENT",
            "END:VCALENDAR",
        )
        val content = "\uFEFF" + lines.joinToString("\r\n")

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals("高等数学", course.title)
    }

    // ------------------------------------------------------------------
    // RRULE / EXDATE
    // ------------------------------------------------------------------

    @Test
    fun `expands weekly rule with interval byday and until`() {
        val content = calendar(
            listOf(
                "SUMMARY:数据结构",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20240930T080000Z",
            ),
        )

        val result = success(utcParser.parse(content))
        assertEquals(2, result.courses.size)

        val mondayCourse = result.courses.single { it.weekday == 1 }
        val wednesdayCourse = result.courses.single { it.weekday == 3 }
        assertEquals(listOf(1, 3, 5), mondayCourse.weeks)
        assertEquals(listOf(1, 3), wednesdayCourse.weeks)
        assertEquals(8 * 60, mondayCourse.startMinutes)
    }

    @Test
    fun `weekly rule respects count`() {
        val content = calendar(
            listOf(
                "SUMMARY:程序设计",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=3",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals(listOf(1, 2, 3), course.weeks)
    }

    @Test
    fun `exdate removes matching week`() {
        val content = calendar(
            listOf(
                "SUMMARY:线性代数",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20240930T080000Z",
                "EXDATE:20240909T080000Z",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals(listOf(1, 3, 4, 5), course.weeks)
    }

    @Test
    fun `merges same course exported as several events`() {
        val content = calendar(
            listOf(
                "SUMMARY:高等数学",
                "LOCATION:教101",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
            ),
            listOf(
                "SUMMARY:高等数学",
                "LOCATION:教101",
                "DTSTART:20240909T080000Z",
                "DTEND:20240909T094000Z",
            ),
        )

        val result = success(utcParser.parse(content))

        assertEquals(1, result.courses.size)
        assertEquals(listOf(1, 2), result.courses.single().weeks)
        assertEquals(2, result.eventCount)
    }

    // ------------------------------------------------------------------
    // 容错
    // ------------------------------------------------------------------

    @Test
    fun `ignores nested alarm properties`() {
        val content = calendar(
            listOf(
                "SUMMARY:有提醒的课",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "SUMMARY:闹钟提醒",
                "TRIGGER:-PT15M",
                "END:VALARM",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals("有提醒的课", course.title)
    }

    @Test
    fun `skips event without dtstart but keeps valid ones`() {
        val content = calendar(
            listOf(
                "SUMMARY:坏事件",
                "DTEND:20240902T094000Z",
            ),
            listOf(
                "SUMMARY:好事件",
                "DTSTART:20240903T080000Z",
                "DTEND:20240903T094000Z",
            ),
        )

        val result = success(utcParser.parse(content))

        assertEquals(1, result.courses.size)
        assertEquals("好事件", result.courses.single().title)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `unsupported frequency keeps first occurrence and warns`() {
        val content = calendar(
            listOf(
                "SUMMARY:每日课",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "RRULE:FREQ=DAILY;COUNT=10",
            ),
        )

        val result = success(utcParser.parse(content))

        assertEquals(1, result.courses.size)
        assertEquals(listOf(1), result.courses.single().weeks)
        assertTrue(result.warnings.any { it.contains("DAILY") })
    }

    @Test
    fun `returns failure for empty content`() {
        assertTrue(utcParser.parse("") is IcsParseResult.Failure)
        assertTrue(utcParser.parse("   ") is IcsParseResult.Failure)
    }

    @Test
    fun `returns failure for garbage content`() {
        val result = utcParser.parse("hello world, this is not a calendar")
        assertTrue(result is IcsParseResult.Failure)
    }

    @Test
    fun `returns failure for calendar without events`() {
        val result = utcParser.parse(
            listOf("BEGIN:VCALENDAR", "VERSION:2.0", "END:VCALENDAR").joinToString("\n"),
        )
        assertTrue(result is IcsParseResult.Failure)
    }

    @Test
    fun `returns failure when no event has valid start time`() {
        val content = calendar(
            listOf(
                "SUMMARY:没有时间的课",
                "DTEND:20240902T094000Z",
            ),
        )

        assertTrue(utcParser.parse(content) is IcsParseResult.Failure)
    }

    // ------------------------------------------------------------------
    // 学期起始 / 日历名称 / 描述提取
    // ------------------------------------------------------------------

    @Test
    fun `derives semester start as monday of earliest dtstart week`() {
        val content = calendar(
            listOf(
                "SUMMARY:高等数学",
                "DTSTART:20240904T080000Z",
                "DTEND:20240904T094000Z",
            ),
        )

        val result = success(utcParser.parse(content))

        // 2024-09-04 是周三，所在周的周一是 2024-09-02
        assertEquals("2024-09-02", result.semesterStart)
    }

    @Test
    fun `semester start uses earliest event across whole file`() {
        val content = calendar(
            listOf(
                "SUMMARY:晚开始课程",
                "DTSTART:20240909T080000Z",
                "DTEND:20240909T094000Z",
            ),
            listOf(
                "SUMMARY:早开始课程",
                "DTSTART:20240903T100000Z",
                "DTEND:20240903T114000Z",
            ),
        )

        val result = success(utcParser.parse(content))

        assertEquals("2024-09-02", result.semesterStart)
        assertEquals(2, result.courses.size)
        assertEquals(listOf(2), result.courses.single { it.title == "晚开始课程" }.weeks)
        assertEquals(listOf(1), result.courses.single { it.title == "早开始课程" }.weeks)
    }

    @Test
    fun `keeps calendar name from x wr calname`() {
        val content = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "X-WR-CALNAME:2024 秋季学期",
            "BEGIN:VEVENT",
            "SUMMARY:高等数学",
            "DTSTART:20240902T080000Z",
            "DTEND:20240902T094000Z",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\n")

        assertEquals("2024 秋季学期", success(utcParser.parse(content)).calendarName)

        val plain = calendar(
            listOf(
                "SUMMARY:高等数学",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
            ),
        )
        assertEquals(null, success(utcParser.parse(plain)).calendarName)
    }

    @Test
    fun `extracts teacher and location from multiline description`() {
        val content = calendar(
            listOf(
                "SUMMARY:高等数学",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "DESCRIPTION:教师: 张三\\n地点: 教 101\\n课程: 高等数学",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals("张三", course.teacher)
        assertEquals("教 101", course.location)
        assertTrue(course.note.contains("课程: 高等数学"))
        assertTrue(!course.note.contains("教师"))
        assertTrue(!course.note.contains("地点"))
    }

    // ------------------------------------------------------------------
    // 更多 RRULE / EXDATE 写法
    // ------------------------------------------------------------------

    @Test
    fun `supports exdate with tzid and multiple values`() {
        val content = calendar(
            listOf(
                "SUMMARY:线性代数",
                "DTSTART;TZID=Asia/Shanghai:20240902T080000",
                "DTEND;TZID=Asia/Shanghai:20240902T094000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=4",
                "EXDATE;TZID=Asia/Shanghai:20240909T080000,20240923T080000",
            ),
        )

        val course = success(shanghaiParser.parse(content)).courses.single()

        // 第 2、4 周被排除，剩第 1、3 周
        assertEquals(listOf(1, 3), course.weeks)
    }

    @Test
    fun `tolerates wkst token in weekly rule`() {
        val content = calendar(
            listOf(
                "SUMMARY:程序设计",
                "DTSTART:20240902T080000Z",
                "DTEND:20240902T094000Z",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;WKST=SU;COUNT=2",
            ),
        )

        val course = success(utcParser.parse(content)).courses.single()

        assertEquals(listOf(1, 2), course.weeks)
    }

    @Test
    fun `default duration constant is 45 minutes`() {
        assertEquals(45, IcsParser.DEFAULT_EVENT_DURATION_MINUTES)
    }

    // ------------------------------------------------------------------
    // TimetableImporter 适配
    // ------------------------------------------------------------------

    @Test
    fun `import content maps success to imported table`() {
        val content = calendar(
            listOf(
                "SUMMARY:高等数学",
                "DTSTART;TZID=Asia/Shanghai:20240902T080000",
                "DTEND;TZID=Asia/Shanghai:20240902T084500",
            ),
        )

        val imported = shanghaiParser.importContent(content)

        assertTrue(imported is AppResult.Success)
        val table = (imported as AppResult.Success).value
        assertEquals("ICS", table.sourceType)
        assertEquals("2024-09-02", table.semesterStart)
        assertEquals(1, table.courses.size)
        assertEquals(1, table.eventCount)
    }

    @Test
    fun `import content maps failure to parse error`() {
        val imported = utcParser.importContent("hello world, this is not a calendar")

        assertTrue(imported is AppResult.Failure)
        assertEquals(ErrorCode.PARSE, (imported as AppResult.Failure).error.code)
        assertTrue(imported.error.message.isNotBlank())
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun calendar(vararg events: List<String>, eol: String = "\n"): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//WakeUp//CN")
        events.forEach { eventLines ->
            lines += "BEGIN:VEVENT"
            lines += eventLines
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString(eol)
    }

    private fun success(result: IcsParseResult): IcsParseResult.Success {
        assertTrue("期望解析成功，实际为 $result", result is IcsParseResult.Success)
        return result as IcsParseResult.Success
    }
}
