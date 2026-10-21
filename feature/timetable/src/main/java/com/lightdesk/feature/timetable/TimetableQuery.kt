package com.lightdesk.feature.timetable

import com.lightdesk.core.common.CalendarUtils
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.TimetableCalendar
import java.time.LocalDate
import java.time.YearMonth

/**
 * 课表视图的纯计算逻辑：周/月分组、教学周次、选中日期等。
 *
 * 不依赖 Android 与 Compose，可直接在 JVM 单元测试中验证；
 * 具体的日历/周次规则复用 `core:model` 的 [TimetableCalendar]，避免各模块重复实现。
 */
object TimetableQuery {

    /** 展示周所在周的周一；[weekOffset] 以“本周”为 0，负数向前、正数向后。 */
    fun weekStart(today: LocalDate, weekOffset: Int): LocalDate =
        TimetableCalendar.startOfWeek(today).plusWeeks(weekOffset.toLong())

    /** 周一..周日 7 天。 */
    fun weekDates(weekStart: LocalDate): List<LocalDate> =
        (0..6).map { weekStart.plusDays(it.toLong()) }

    /**
     * 某一周的课程，按星期（1..7）分组，只包含有课的星期。
     * 教学周过滤依据 `CourseTable.semesterStart`，没有学期起始时按“每周都有课”处理。
     */
    fun weekCourses(table: CourseTable?, weekStart: LocalDate): Map<Int, List<Course>> {
        val semesterStart = semesterStartOf(table)
        val courses = table?.courses.orEmpty()
        val result = LinkedHashMap<Int, List<Course>>()
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            val dayCourses = courses
                .filter { TimetableCalendar.occursOn(it, date, semesterStart) }
                .sortedBy { it.startMinutes }
            if (dayCourses.isNotEmpty()) {
                result[offset + 1] = dayCourses
            }
        }
        return result
    }

    /** 展示月；[monthOffset] 以“本月”为 0。 */
    fun monthOf(today: LocalDate, monthOffset: Int): YearMonth =
        YearMonth.from(today).plusMonths(monthOffset.toLong())

    /** 整月每天的课程（只包含有课的日期），供月视图统计与选中日列表复用。 */
    fun monthCourses(table: CourseTable?, month: YearMonth): Map<LocalDate, List<Course>> =
        TimetableCalendar.coursesInMonth(table, month)

    /**
     * 月视图网格中的日期：在 [CalendarUtils.monthGrid] 基础上把月外的 null 补成真实日期，
     * 这样“非本月日期”可以弱化显示而不是留白。
     */
    fun monthGridDates(month: YearMonth): List<List<LocalDate>> {
        val grid = CalendarUtils.monthGrid(month)
        return grid.map { week ->
            val firstIndex = week.indexOfFirst { it != null }
            if (firstIndex < 0) {
                emptyList()
            } else {
                val firstDate = week[firstIndex]!!
                week.indices.map { index -> firstDate.plusDays((index - firstIndex).toLong()) }
            }
        }
    }

    /** 月视图网格内每个日期的课程（含月外补位日期），用于格子里的课程条。 */
    fun monthGridCourses(table: CourseTable?, month: YearMonth): Map<LocalDate, List<Course>> {
        if (table == null) return emptyMap()
        val result = LinkedHashMap<LocalDate, List<Course>>()
        monthGridDates(month).forEach { week ->
            week.forEach { date ->
                val courses = TimetableCalendar.coursesOn(table, date)
                if (courses.isNotEmpty()) result[date] = courses
            }
        }
        return result
    }

    /** 某天的课程（考虑星期与教学周）。 */
    fun coursesOn(table: CourseTable?, date: LocalDate): List<Course> =
        TimetableCalendar.coursesOn(table, date)

    /** 某天是第几教学周；没有学期起始、或早于学期开始时返回 null。 */
    fun weekNumber(table: CourseTable?, date: LocalDate): Int? =
        TimetableCalendar.weekNumber(date, semesterStartOf(table))

    /** 今天属于第几教学周。 */
    fun currentWeek(table: CourseTable?, today: LocalDate): Int? = weekNumber(table, today)

    /**
     * 月视图选中的日期：用户点选且属于当前展示月时用它；
     * 否则当前展示月包含今天就用今天，其余月份回落到 1 号。
     */
    fun selectedDate(today: LocalDate, month: YearMonth, selectedDateKey: String?): LocalDate {
        val selected = TimetableCalendar.parseIsoDate(selectedDateKey)
        if (selected != null && YearMonth.from(selected) == month) return selected
        return if (YearMonth.from(today) == month) today else month.atDay(1)
    }

    /** 学期第 1 周周一。 */
    fun semesterStartOf(table: CourseTable?): LocalDate? =
        TimetableCalendar.parseIsoDate(table?.semesterStart)
}
