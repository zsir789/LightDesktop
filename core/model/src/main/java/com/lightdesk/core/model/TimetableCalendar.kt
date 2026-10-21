package com.lightdesk.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 课表日历计算（纯函数，无 Android 依赖，便于单元测试）。
 *
 * v1.1.0 新增能力：
 * - 依据 [CourseTable.semesterStart] 计算教学周次，支持单双周/指定周课程；
 * - 查询“某天有哪些课”，供首页与月视图使用；
 * - 生成整月每天的课程映射，供“每月每天课程查看”。
 *
 * 降级策略：没有学期起始时间时，`weeks` 过滤被忽略，按“每周都有课”展示，避免空白。
 */
object TimetableCalendar {

    fun parseIsoDate(text: String?): LocalDate? = runCatching {
        text?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    }.getOrNull()

    fun toLocalDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    /** 第几教学周（1 起）；无学期起始或早于起始日期时返回 null。 */
    fun weekNumber(date: LocalDate, semesterStart: LocalDate?): Int? {
        val start = semesterStart ?: return null
        if (date.isBefore(start)) return null
        return (ChronoUnit.DAYS.between(start, date) / 7 + 1).toInt()
    }

    /** 某门课是否在某天上课（考虑星期与教学周）。 */
    fun occursOn(course: Course, date: LocalDate, semesterStart: LocalDate?): Boolean {
        if (course.weekday != date.dayOfWeek.value) return false
        // 没有学期起始：无法判断教学周，降级为“每周有课”。
        val start = semesterStart ?: return true
        // 学期开始之前的日期不上课（避免月视图把开学前的日期也算成有课）。
        if (date.isBefore(start)) return false
        if (course.weeks.isEmpty()) return true
        val week = weekNumber(date, start) ?: return true
        return course.weeks.contains(week)
    }

    /** 某天的课程，按开始时间排序。 */
    fun coursesOn(table: CourseTable?, date: LocalDate): List<Course> {
        val courses = table?.takeIf { it.courses.isNotEmpty() }?.courses ?: return emptyList()
        val start = parseIsoDate(table.semesterStart)
        return courses
            .filter { occursOn(it, date, start) }
            .sortedBy { it.startMinutes }
    }

    fun coursesOn(table: CourseTable?, timestamp: Long): List<Course> =
        coursesOn(table, toLocalDate(timestamp))

    /** 整月每天的课程；仅包含有课的日期。 */
    fun coursesInMonth(table: CourseTable?, month: YearMonth): Map<LocalDate, List<Course>> {
        if (table == null) return emptyMap()
        val result = LinkedHashMap<LocalDate, List<Course>>()
        var date = month.atDay(1)
        val end = month.atEndOfMonth()
        while (!date.isAfter(end)) {
            val courses = coursesOn(table, date)
            if (courses.isNotEmpty()) result[date] = courses
            date = date.plusDays(1)
        }
        return result
    }

    /** 某天第一个尚未结束的课程（进行中或下一节）。 */
    fun ongoingOrNext(table: CourseTable?, timestamp: Long): Course? {
        val localTime = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val nowMinutes = localTime.hour * 60 + localTime.minute
        val today = coursesOn(table, timestamp)
        return today.firstOrNull { it.endMinutes > nowMinutes }
    }

    /** 周一为一周起点。 */
    fun startOfWeek(date: LocalDate): LocalDate =
        date.with(DayOfWeek.MONDAY)

    /** 当前教学周（无学期起始时为 null）。 */
    fun currentWeek(table: CourseTable?, timestamp: Long): Int? =
        weekNumber(toLocalDate(timestamp), parseIsoDate(table?.semesterStart))
}
