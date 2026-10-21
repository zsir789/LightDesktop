package com.lightdesk.feature.timetable

import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 课表周/月视图纯逻辑单测：不依赖 Android 运行时。
 */
class TimetableQueryTest {

    private val semesterStartKey = "2024-09-02"

    private fun table(courses: List<Course>): CourseTable = CourseTable(
        name = "测试课表",
        sourceType = "ICS",
        semesterStart = semesterStartKey,
        courses = courses,
    )

    private fun course(
        id: String,
        weekday: Int,
        startMinutes: Int = 480,
        endMinutes: Int = 570,
        weeks: List<Int> = emptyList(),
    ): Course = Course(
        id = id,
        title = "课程$id",
        weekday = weekday,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        weeks = weeks,
    )

    @Test
    fun `week start returns monday of containing week`() {
        assertEquals(LocalDate.of(2024, 9, 2), TimetableQuery.weekStart(LocalDate.of(2024, 9, 5), 0))
        assertEquals(LocalDate.of(2024, 9, 9), TimetableQuery.weekStart(LocalDate.of(2024, 9, 5), 1))
        assertEquals(LocalDate.of(2024, 8, 26), TimetableQuery.weekStart(LocalDate.of(2024, 9, 5), -1))
        // 周日仍属于同一周
        assertEquals(LocalDate.of(2024, 9, 2), TimetableQuery.weekStart(LocalDate.of(2024, 9, 8), 0))
    }

    @Test
    fun `week dates span monday to sunday`() {
        val dates = TimetableQuery.weekDates(LocalDate.of(2024, 9, 2))

        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2024, 9, 2), dates.first())
        assertEquals(LocalDate.of(2024, 9, 8), dates.last())
        assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, dates.last().dayOfWeek)
    }

    @Test
    fun `week courses group by weekday and filter teaching weeks`() {
        val table = table(
            listOf(
                course("a", weekday = 1, weeks = listOf(1, 3)),
                course("b", weekday = 2, weeks = emptyList()),
            ),
        )

        val week1 = TimetableQuery.weekCourses(table, LocalDate.of(2024, 9, 2))
        assertEquals(setOf(1, 2), week1.keys)
        assertEquals(listOf("a"), week1[1].orEmpty().map { it.id })
        assertEquals(listOf("b"), week1[2].orEmpty().map { it.id })

        // 第 2 周只有 b 有课
        val week2 = TimetableQuery.weekCourses(table, LocalDate.of(2024, 9, 9))
        assertEquals(setOf(2), week2.keys)
    }

    @Test
    fun `courses on a date respect weekday and weeks`() {
        val table = table(
            listOf(
                course("a", weekday = 1, startMinutes = 480, endMinutes = 570, weeks = listOf(2)),
                course("b", weekday = 1, startMinutes = 600, endMinutes = 690, weeks = emptyList()),
            ),
        )

        assertEquals(listOf("b"), TimetableQuery.coursesOn(table, LocalDate.of(2024, 9, 2)).map { it.id })
        assertEquals(
            listOf("a", "b"),
            TimetableQuery.coursesOn(table, LocalDate.of(2024, 9, 9)).map { it.id },
        )
        assertEquals(
            emptyList<Course>(),
            TimetableQuery.coursesOn(table, LocalDate.of(2024, 9, 3)),
        )
    }

    @Test
    fun `month aggregation maps only dates with courses`() {
        val table = table(
            listOf(
                course("a", weekday = 1, weeks = listOf(1, 3)),
                course("b", weekday = 2, weeks = emptyList()),
            ),
        )

        val september = TimetableQuery.monthCourses(table, YearMonth.of(2024, 9))

        // a：9/2、9/16 两周；b：9 月的 4 个周二
        assertEquals(6, september.size)
        assertEquals(listOf("a"), september[LocalDate.of(2024, 9, 2)].orEmpty().map { it.id })
        assertEquals(listOf("a"), september[LocalDate.of(2024, 9, 16)].orEmpty().map { it.id })
        assertEquals(listOf("b"), september[LocalDate.of(2024, 9, 3)].orEmpty().map { it.id })
        assertNull(september[LocalDate.of(2024, 9, 9)])
    }

    @Test
    fun `week number is relative to semester start`() {
        val table = table(emptyList())

        assertEquals(1, TimetableQuery.weekNumber(table, LocalDate.of(2024, 9, 2)))
        assertEquals(1, TimetableQuery.weekNumber(table, LocalDate.of(2024, 9, 8)))
        assertEquals(2, TimetableQuery.weekNumber(table, LocalDate.of(2024, 9, 9)))
        assertEquals(4, TimetableQuery.weekNumber(table, LocalDate.of(2024, 9, 23)))
        assertNull(TimetableQuery.weekNumber(table, LocalDate.of(2024, 8, 26)))
        assertNull(TimetableQuery.weekNumber(table.copy(semesterStart = null), LocalDate.of(2024, 9, 2)))
    }

    @Test
    fun `current week uses today`() {
        assertEquals(4, TimetableQuery.currentWeek(table(emptyList()), LocalDate.of(2024, 9, 23)))
        assertNull(TimetableQuery.currentWeek(null, LocalDate.of(2024, 9, 23)))
    }

    @Test
    fun `selected date defaults to today or first day of month`() {
        val today = LocalDate.of(2024, 9, 18)
        val september = YearMonth.of(2024, 9)

        assertEquals(today, TimetableQuery.selectedDate(today, september, null))
        assertEquals(LocalDate.of(2024, 9, 5), TimetableQuery.selectedDate(today, september, "2024-09-05"))
        // 用户选中的日期不属于当前展示月时忽略
        assertEquals(LocalDate.of(2024, 10, 1), TimetableQuery.selectedDate(today, YearMonth.of(2024, 10), "2024-09-05"))
        assertEquals(LocalDate.of(2024, 8, 1), TimetableQuery.selectedDate(today, YearMonth.of(2024, 8), null))
    }

    @Test
    fun `month offset navigation`() {
        val today = LocalDate.of(2024, 9, 30)

        assertEquals(YearMonth.of(2024, 10), TimetableQuery.monthOf(today, 1))
        assertEquals(YearMonth.of(2024, 8), TimetableQuery.monthOf(today, -1))
        assertEquals(YearMonth.of(2024, 12), TimetableQuery.monthOf(today, 3))
        assertEquals(YearMonth.of(2025, 1), TimetableQuery.monthOf(today, 4))
    }

    @Test
    fun `month grid dates cover whole weeks including adjacent months`() {
        val grid = TimetableQuery.monthGridDates(YearMonth.of(2024, 9))

        assertEquals(6, grid.size)
        assertEquals(42, grid.sumOf { it.size })
        grid.forEach { week -> assertEquals(7, week.size) }
        // 2024-09-01 是周日，网格从 2024-08-26（周一）开始
        assertEquals(LocalDate.of(2024, 8, 26), grid.first().first())
        assertEquals(LocalDate.of(2024, 9, 1), grid.first().last())
        assertEquals(LocalDate.of(2024, 10, 6), grid.last().last())
    }

    @Test
    fun `month grid courses include adjacent month dates`() {
        val september = YearMonth.of(2024, 9)
        val table = table(
            listOf(course("a", weekday = 1, weeks = emptyList())),
        )

        val gridCourses = TimetableQuery.monthGridCourses(table, september)

        // 2024-08-26 早于学期起始（2024-09-02），学期前不上课
        assertNull(gridCourses[LocalDate.of(2024, 8, 26)])
        assertEquals(listOf("a"), gridCourses[LocalDate.of(2024, 9, 30)].orEmpty().map { it.id })

        // 学期起始提前到 8 月后，网格中 8 月的补位日期也会显示课程
        val earlyTable = table.copy(semesterStart = "2024-08-26")
        val earlyGrid = TimetableQuery.monthGridCourses(earlyTable, september)
        assertEquals(listOf("a"), earlyGrid[LocalDate.of(2024, 8, 26)].orEmpty().map { it.id })

        assertNull(TimetableQuery.monthGridCourses(null, september)[LocalDate.of(2024, 9, 2)])
    }
}
