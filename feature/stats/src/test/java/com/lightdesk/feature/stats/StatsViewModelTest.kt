package com.lightdesk.feature.stats

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.ThemeMode
import com.lightdesk.core.model.ThemePalette
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 设置与统计页 ViewModel 单测：验证设置写入（主题模式/主题配色/城市/阈值/统计开关）与清空统计。
 *
 * 使用 Fake Repository：成功路径直接改内存状态；失败分支通过注入 AppResult.Failure 模拟，
 * 不抛异常，避免触发 android.util.Log 依赖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme mode update persists selected mode`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
    }

    @Test
    fun `theme palette update persists selected palette`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.setThemePalette(ThemePalette.ROSE)
        advanceUntilIdle()

        assertEquals(ThemePalette.ROSE, settings.state.value.themePalette)
    }

    @Test
    fun `theme palette update keeps other settings untouched`() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(themeMode = ThemeMode.DARK, weatherCity = "成都"),
        )
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.setThemePalette(ThemePalette.SAND)
        advanceUntilIdle()

        assertEquals(ThemePalette.SAND, settings.state.value.themePalette)
        assertEquals(ThemeMode.DARK, settings.state.value.themeMode)
        assertEquals("成都", settings.state.value.weatherCity)
    }

    @Test
    fun `theme palette failure posts error message`() = runTest {
        val settings = FakeSettingsRepository(
            updateFailure = AppError(ErrorCode.STORAGE, "本地数据读写失败"),
        )
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()

        viewModel.setThemePalette(ThemePalette.MINT)
        advanceUntilIdle()

        assertEquals(ThemePalette.OCEAN, settings.state.value.themePalette)
        assertEquals(
            StatsMessage(text = "保存主题配色失败：本地数据读写失败", isError = true),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `weather city update writes name latitude and longitude`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.setWeatherCity(WeatherCityPresets.byName("上海")!!)
        advanceUntilIdle()

        assertEquals("上海", settings.state.value.weatherCity)
        assertEquals(31.2304, settings.state.value.weatherLatitude, 0.0001)
        assertEquals(121.4737, settings.state.value.weatherLongitude, 0.0001)
    }

    @Test
    fun `reset weather city restores default preset`() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(
                weatherCity = "成都",
                weatherLatitude = 30.5728,
                weatherLongitude = 104.0668,
            ),
        )
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.resetWeatherCity()
        advanceUntilIdle()

        assertEquals(WeatherCityPresets.default.name, settings.state.value.weatherCity)
        assertEquals(WeatherCityPresets.default.latitude, settings.state.value.weatherLatitude, 0.0001)
        assertEquals(WeatherCityPresets.default.longitude, settings.state.value.weatherLongitude, 0.0001)
    }

    @Test
    fun `countdown warning minutes update persists value`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        viewModel.setCountdownWarningMinutes(60)
        advanceUntilIdle()

        assertEquals(60, settings.state.value.countdownWarningMinutes)
    }

    @Test
    fun `usage stats switch update persists value`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = StatsViewModel(settings, FakeStatsRepository())

        assertTrue(settings.state.value.usageStatsEnabled)

        viewModel.setUsageStatsEnabled(false)
        advanceUntilIdle()

        assertFalse(settings.state.value.usageStatsEnabled)
    }

    @Test
    fun `reset stats clears repository data and shows success message`() = runTest {
        val stats = FakeStatsRepository(UsageStats(notesCreated = 3L, notesCompleted = 1L))
        val viewModel = StatsViewModel(FakeSettingsRepository(), stats)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()

        viewModel.resetStats()
        advanceUntilIdle()

        assertEquals(1, stats.resetCalls)
        assertEquals(0L, stats.state.value.notesCreated)
        assertEquals(
            StatsMessage(text = "统计数据已清空", isError = false),
            viewModel.uiState.value.message,
        )
        assertFalse(viewModel.uiState.value.resetting)
    }
}

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

private class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
    private val updateFailure: AppError? = null,
) : SettingsRepository {

    val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit> {
        updateFailure?.let { error -> return AppResult.Failure(error) }
        state.value = transform(state.value)
        return AppResult.Success(Unit)
    }
}

private class FakeStatsRepository(
    initial: UsageStats = UsageStats(),
) : StatsRepository {

    val state = MutableStateFlow(initial)
    var resetCalls: Int = 0
        private set

    override fun observeStats(): Flow<UsageStats> = state

    override suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun recordNoteCreated(at: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun recordNoteCompleted(at: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun reset(): AppResult<Unit> {
        resetCalls += 1
        state.value = UsageStats()
        return AppResult.Success(Unit)
    }
}
