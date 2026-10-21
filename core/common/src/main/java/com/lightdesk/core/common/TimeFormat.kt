package com.lightdesk.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间格式化工具。全部使用 java.time（minSdk 26 起原生支持），
 * 时区统一取设备默认时区，避免各模块自行实现导致展示不一致。
 */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM月dd日 HH:mm", Locale.getDefault())
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm", Locale.getDefault())

    private val weekdayLabels = mapOf(
        1 to "周一",
        2 to "周二",
        3 to "周三",
        4 to "周四",
        5 to "周五",
        6 to "周六",
        7 to "周日",
    )

    fun weekdayLabel(weekday: Int): String = weekdayLabels[weekday] ?: "未知"

    /** 一天的分钟数（0..1439）转换为 "08:05" 形式。 */
    fun minutesToClock(minutesOfDay: Int): String {
        val normalized = ((minutesOfDay % 1440) + 1440) % 1440
        return clockFormatter.format(
            java.time.LocalTime.of(normalized / 60, normalized % 60)
        )
    }

    fun clockToMinutes(text: String): Int? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun timestampToDateTimeText(timestamp: Long): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

    fun timestampToFullText(timestamp: Long): String =
        fullDateFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

    /** 剩余毫秒数的倒计时文案，例如 "2天 03:12:09" 或 "已超时 00:12:03"。 */
    fun countdownText(remainingMillis: Long): String {
        val overdue = remainingMillis < 0
        val totalSeconds = kotlin.math.abs(remainingMillis) / 1000
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        val clock = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        val prefix = if (overdue) "已超时 " else ""
        return if (days > 0) {
            "$prefix${days}天 $clock"
        } else {
            "$prefix$clock"
        }
    }

    /** 距离截止时间的粗略描述，用于列表副标题。 */
    fun deadlineHint(deadline: Long, now: Long): String {
        val diff = deadline - now
        return when {
            diff < 0 -> "已超时"
            diff < 60_000 -> "即将截止"
            diff < 3_600_000 -> "${diff / 60_000} 分钟后截止"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时后截止"
            else -> "${diff / 86_400_000} 天后截止"
        }
    }
}
