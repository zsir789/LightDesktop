package com.lightdesk.core.data

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.NoOpLogger
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计开关单测：关闭「使用统计」后所有埋点都应被跳过，重新开启后恢复。
 *
 * 用 UnconfinedTestDispatcher 让 fire-and-forget 的写入在调用点同步完成，便于断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsTrackerTest {

    private val statsRepository = FakeStatsRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val timeProvider = FakeTimeProvider()
    private val tracker = StatsTracker(
        repository = statsRepository,
        settingsRepository = settingsRepository,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        timeProvider = timeProvider,
        logger = NoOpLogger,
    )

    @Test
    fun `开关开启时正常记录访问`() {
        tracker.trackVisit(FeatureKey.HOME)

        assertEquals(listOf(FeatureKey.HOME), statsRepository.visitedFeatures)
    }

    @Test
    fun `关闭统计后访问与业务事件都不再写入`() {
        settingsRepository.state.value = AppSettings(usageStatsEnabled = false)

        tracker.trackVisit(FeatureKey.HOME)
        tracker.trackNoteCreated()
        tracker.trackNoteCompleted()
        tracker.trackWeatherCondition("晴")

        assertTrue(statsRepository.visitedFeatures.isEmpty())
        assertEquals(0, statsRepository.noteCreatedCount)
        assertEquals(0, statsRepository.noteCompletedCount)
        assertTrue(statsRepository.weatherConditions.isEmpty())
    }

    @Test
    fun `重新开启统计后恢复记录`() {
        settingsRepository.state.value = AppSettings(usageStatsEnabled = false)
        tracker.trackVisit(FeatureKey.HOME)

        settingsRepository.state.value = AppSettings(usageStatsEnabled = true)
        tracker.trackVisit(FeatureKey.NOTES)

        assertEquals(listOf(FeatureKey.NOTES), statsRepository.visitedFeatures)
    }

    private class FakeTimeProvider : TimeProvider {
        override fun now(): Long = 1_700_000_000_000L
    }

    private class FakeSettingsRepository(
        initial: AppSettings = AppSettings(),
    ) : SettingsRepository {

        val state = MutableStateFlow(initial)

        override fun observeSettings(): Flow<AppSettings> = state

        override suspend fun current(): AppSettings = state.value

        override suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit> {
            state.value = transform(state.value)
            return AppResult.Success(Unit)
        }
    }

    private class FakeStatsRepository : StatsRepository {

        val visitedFeatures = mutableListOf<FeatureKey>()
        var noteCreatedCount: Int = 0
            private set
        var noteCompletedCount: Int = 0
            private set
        val weatherConditions = mutableMapOf<String, Int>()

        override fun observeStats(): Flow<UsageStats> = MutableStateFlow(UsageStats())

        override suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit> {
            visitedFeatures += feature
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

        override suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit> {
            weatherConditions[condition] = (weatherConditions[condition] ?: 0) + 1
            return AppResult.Success(Unit)
        }

        override suspend fun reset(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
