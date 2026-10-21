package com.lightdesk.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 统计维度：首页、备忘、课表、天气。
 * 新增功能时追加枚举值，统计页会自动适配；v1.0.0 的旧枚举值在迁移时映射（见 core:data）。
 */
@Serializable
enum class FeatureKey {
    HOME,
    NOTES,
    TIMETABLE,
    WEATHER,
}

/** 单个功能的使用次数与最近使用时间。 */
@Serializable
data class FeatureVisit(
    val feature: FeatureKey,
    val count: Long = 0L,
    val lastUsedAt: Long = 0L,
)

/** 某一天的活动量，用于热力图与趋势展示。 */
@Serializable
data class DailyActivity(
    val notesCreated: Int = 0,
    val notesCompleted: Int = 0,
    val visits: Int = 0,
    val weatherFetches: Int = 0,
) {
    /** 当日活动总量，热力图强度按它分级。 */
    val total: Int get() = notesCreated + notesCompleted + visits + weatherFetches
}

/**
 * 使用统计汇总。
 *
 * - [visits]：各功能使用次数；
 * - [daily]：按天聚合的活动量（key 为 yyyy-MM-dd），供热力图使用；
 * - [notesCreated]/[notesCompleted]：备忘记录数、完成次数；
 * - [weatherConditions]：各天气现象出现次数；
 * - [legacyItems]：v1.0.0 旧格式（`items`）迁移入口，读取后自动转换，不再写入。
 */
@Serializable
data class UsageStats(
    val visits: List<FeatureVisit> = emptyList(),
    val daily: Map<String, DailyActivity> = emptyMap(),
    val notesCreated: Long = 0L,
    val notesCompleted: Long = 0L,
    val weatherConditions: Map<String, Long> = emptyMap(),
    @SerialName("items")
    val legacyItems: List<LegacyUsageStat> = emptyList(),
) {
    fun visitCount(feature: FeatureKey): Long =
        visits.firstOrNull { it.feature == feature }?.count ?: 0L

    fun lastUsedAt(feature: FeatureKey): Long =
        visits.firstOrNull { it.feature == feature }?.lastUsedAt ?: 0L

    fun totalVisits(): Long = visits.sumOf { it.count }

    fun dailyActivity(dateKey: String): DailyActivity = daily[dateKey] ?: DailyActivity()

    /** 备忘完成率（0..100）；没有记录时为 0。 */
    fun noteCompletionRate(): Int {
        if (notesCreated <= 0L) return 0
        return ((notesCompleted.toDouble() * 100.0) / notesCreated.toDouble())
            .coerceIn(0.0, 100.0)
            .toInt()
    }
}

/** v1.0.0 的统计条目（feature 为字符串，兼容已删除的 MEMO/TODO/FLOATING_BALL）。 */
@Serializable
data class LegacyUsageStat(
    val feature: String = "",
    val count: Long = 0L,
    val lastUsedAt: Long = 0L,
)
