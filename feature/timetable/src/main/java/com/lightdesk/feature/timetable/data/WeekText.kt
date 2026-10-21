package com.lightdesk.feature.timetable.data

/** 周次列表的展示文案（纯逻辑，便于单元测试）。 */
object WeekText {

    /** 把周次列表压缩成可读文案：[1,2,3,5] -> "第1-3、5周"；空列表表示每周。 */
    fun format(weeks: List<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return "每周"

        val ranges = mutableListOf<String>()
        var rangeStart = sorted.first()
        var previous = rangeStart
        for (index in 1 until sorted.size) {
            val current = sorted[index]
            if (current == previous + 1) {
                previous = current
                continue
            }
            ranges += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
            rangeStart = current
            previous = current
        }
        ranges += if (rangeStart == previous) "$rangeStart" else "$rangeStart-$previous"
        return "第" + ranges.joinToString("、") + "周"
    }
}
