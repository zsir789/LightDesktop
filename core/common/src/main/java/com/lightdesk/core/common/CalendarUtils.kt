package com.lightdesk.core.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日历网格工具（纯函数）：生成“周一为一周起点、整月补空”的二维网格。
 * 供统计页热力图、课表月视图复用，避免各模块重复实现。
 */
object CalendarUtils {

    /** 整月网格：每行 7 格（周一..周日），月外的日期以 null 占位。 */
    fun monthGrid(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val rows = mutableListOf<List<LocalDate?>>()
        var row = MutableList<LocalDate?>(7) { null }
        var column = leading
        var date = first
        while (!date.isAfter(month.atEndOfMonth())) {
            row[column] = date
            column += 1
            if (column == 7) {
                rows += row
                row = MutableList<LocalDate?>(7) { null }
                column = 0
            }
            date = date.plusDays(1)
        }
        if (row.any { it != null }) rows += row
        return rows
    }

    /** 某天所在周的周一到周日。 */
    fun weekOf(date: LocalDate): List<LocalDate> {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /** 最近 N 周（以周一为起点，升序），最后一行是本周。 */
    fun recentWeeks(today: LocalDate, weeks: Int): List<List<LocalDate>> {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (weeks - 1 downTo 0).map { offset ->
            weekOf(thisMonday.minusWeeks(offset.toLong()))
        }
    }

    fun dateKey(date: LocalDate): String = date.toString()

    fun dateKey(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString()
}
