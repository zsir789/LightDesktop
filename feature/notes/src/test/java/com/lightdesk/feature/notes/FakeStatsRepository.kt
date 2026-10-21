package com.lightdesk.feature.notes

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 内存版 [StatsRepository]。
 *
 * [StatsTracker] 是 final class 且统计写入是 fire-and-forget，
 * 因此单测用“真 StatsTracker + 假 StatsRepository”验证埋点调用次数。
 */
class FakeStatsRepository : StatsRepository {

    private val state = MutableStateFlow(UsageStats())

    var noteCreatedCount: Int = 0
        private set
    var noteCompletedCount: Int = 0
        private set
    val visits = mutableListOf<FeatureKey>()

    override fun observeStats(): Flow<UsageStats> = state

    override suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit> {
        visits += feature
        return AppResult.Success(Unit)
    }

    override suspend fun recordNoteCreated(at: Long): AppResult<Unit> {
        noteCreatedCount++
        return AppResult.Success(Unit)
    }

    override suspend fun recordNoteCompleted(at: Long): AppResult<Unit> {
        noteCompletedCount++
        return AppResult.Success(Unit)
    }

    override suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun reset(): AppResult<Unit> = AppResult.Success(Unit)
}
