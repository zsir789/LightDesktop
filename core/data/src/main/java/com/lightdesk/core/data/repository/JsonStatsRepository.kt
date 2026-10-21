package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.CalendarUtils
import com.lightdesk.core.common.Logger
import com.lightdesk.core.common.map
import com.lightdesk.core.common.onFailure
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.UsageStatsStore
import com.lightdesk.core.model.DailyActivity
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.FeatureVisit
import com.lightdesk.core.model.LegacyUsageStat
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 使用统计仓储。
 *
 * 数据结构覆盖三类统计：
 * 1. 功能使用次数（首页/备忘/课表/天气）；
 * 2. 按天活动量（热力图）；
 * 3. 备忘记录数/完成次数与各天气类型次数。
 *
 * 首次读取时会把 v1.0.0 的 `items` 旧格式自动映射为新维度（MEMO/TODO → NOTES），
 * 转换后立即回写，保证后续读取不再触发迁移。
 */
class JsonStatsRepository(
    private val store: JsonFileStore<UsageStatsStore>,
    private val logger: Logger = AppLogger,
) : StatsRepository {

    private val state = MutableStateFlow(UsageStats())
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false

    override fun observeStats(): Flow<UsageStats> = state.onStart { ensureLoaded() }

    override suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit> = update { stats ->
        val dayKey = CalendarUtils.dateKey(at)
        stats.copy(
            visits = stats.visits.increase(feature, at),
            daily = stats.daily.bump(dayKey) { it.copy(visits = it.visits + 1) },
        )
    }

    override suspend fun recordNoteCreated(at: Long): AppResult<Unit> = update { stats ->
        val dayKey = CalendarUtils.dateKey(at)
        stats.copy(
            notesCreated = stats.notesCreated + 1,
            daily = stats.daily.bump(dayKey) { it.copy(notesCreated = it.notesCreated + 1) },
        )
    }

    override suspend fun recordNoteCompleted(at: Long): AppResult<Unit> = update { stats ->
        val dayKey = CalendarUtils.dateKey(at)
        stats.copy(
            notesCompleted = stats.notesCompleted + 1,
            daily = stats.daily.bump(dayKey) { it.copy(notesCompleted = it.notesCompleted + 1) },
        )
    }

    override suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit> =
        update { stats ->
            val dayKey = CalendarUtils.dateKey(at)
            val conditions = stats.weatherConditions.toMutableMap()
            conditions[condition] = (conditions[condition] ?: 0L) + 1L
            stats.copy(
                weatherConditions = conditions,
                daily = stats.daily.bump(dayKey) { it.copy(weatherFetches = it.weatherFetches + 1) },
            )
        }

    override suspend fun reset(): AppResult<Unit> = writeMutex.withLock {
        ensureLoaded()
        store.write(UsageStatsStore(stats = UsageStats())).map { state.value = UsageStats() }
    }

    private suspend fun update(transform: (UsageStats) -> UsageStats): AppResult<Unit> =
        writeMutex.withLock {
            ensureLoaded()
            val updated = transform(state.value)
            store.write(UsageStatsStore(stats = updated)).map { state.value = updated }
        }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            val stored = store.read().stats
            val migrated = migrateLegacy(stored)
            if (migrated != stored) {
                store.write(UsageStatsStore(stats = migrated))
                    .onFailure { logger.w(TAG, "旧统计迁移落盘失败", it.cause) }
                logger.d(TAG, "已迁移 v1.0.0 统计维度")
            }
            state.value = migrated
            loaded = true
        }
    }

    /** v1.0.0 `items` → v1.1.0 维度；FLOATING_BALL 等已下线维度直接丢弃。 */
    private fun migrateLegacy(stats: UsageStats): UsageStats {
        if (stats.legacyItems.isEmpty()) return stats
        val visits = stats.visits.toMutableList()
        stats.legacyItems.forEach { legacy ->
            val feature = legacy.toFeatureKey() ?: return@forEach
            visits.add(FeatureVisit(feature = feature, count = legacy.count, lastUsedAt = legacy.lastUsedAt))
        }
        val merged = visits
            .groupBy { it.feature }
            .map { (feature, list) ->
                FeatureVisit(
                    feature = feature,
                    count = list.sumOf { it.count },
                    lastUsedAt = list.maxOfOrNull { it.lastUsedAt } ?: 0L,
                )
            }
        return stats.copy(visits = merged, legacyItems = emptyList())
    }

    private fun LegacyUsageStat.toFeatureKey(): FeatureKey? = when (feature.uppercase()) {
        "MEMO", "TODO", "NOTES" -> FeatureKey.NOTES
        "TIMETABLE" -> FeatureKey.TIMETABLE
        "WEATHER" -> FeatureKey.WEATHER
        "HOME" -> FeatureKey.HOME
        else -> null
    }

    private fun List<FeatureVisit>.increase(feature: FeatureKey, at: Long): List<FeatureVisit> {
        val existing = firstOrNull { it.feature == feature }
        return if (existing == null) {
            this + FeatureVisit(feature = feature, count = 1L, lastUsedAt = at)
        } else {
            map {
                if (it.feature == feature) it.copy(count = it.count + 1L, lastUsedAt = at) else it
            }
        }
    }

    private fun Map<String, DailyActivity>.bump(
        dateKey: String,
        transform: (DailyActivity) -> DailyActivity,
    ): Map<String, DailyActivity> {
        val map = toMutableMap()
        map[dateKey] = transform(map[dateKey] ?: DailyActivity())
        return map
    }

    private companion object {
        const val TAG = "JsonStatsRepository"
    }
}
