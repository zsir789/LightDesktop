package com.lightdesk.core.model

import kotlinx.serialization.Serializable

/**
 * 单门课程（周课表中的一条记录）。
 *
 * weekday：1=周一 … 7=周日；startMinutes/endMinutes 为“从 00:00 起的分钟数”，
 * 由 ICS 中的 DTSTART/DTEND 解析而来，避免硬编码节次时间。
 * weeks 为空表示每周生效；非空时表示仅在这些教学周生效。
 */
@Serializable
data class Course(
    val id: String,
    val title: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val weeks: List<Int> = emptyList(),
    val note: String = "",
    val colorSeed: Int = 0,
) {
    val durationMinutes: Int
        get() = (endMinutes - startMinutes).coerceAtLeast(0)
}

/**
 * 导入后的课表。
 *
 * semesterStart 为第 1 周周一的 ISO 日期（yyyy-MM-dd），由 ICS 中最早事件推导，
 * 用于月视图与“今天上什么课”的日期映射；为空时按“每周都有”降级展示。
 */
@Serializable
data class CourseTable(
    val name: String = "我的课表",
    val sourceFile: String = "",
    val sourceType: String = "ICS",
    val importedAt: Long = 0L,
    val semesterStart: String? = null,
    val courses: List<Course> = emptyList(),
) {
    val courseCount: Int get() = courses.size
}
