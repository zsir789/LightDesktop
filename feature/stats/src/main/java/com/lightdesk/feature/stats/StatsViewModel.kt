package com.lightdesk.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.ThemeMode
import com.lightdesk.core.model.ThemePalette
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 设置与统计页的一次性提示（自动消失的 Info/Error Banner）。
 */
data class StatsMessage(
    val text: String,
    val isError: Boolean,
)

/**
 * 设置与统计页 UI 状态。
 *
 * [loading] 只在首帧为 true；设置/统计数据流各自独立，任一读取失败都会降级为空数据，
 * 因此页面始终可用（设置项仍可点击）。
 */
data class StatsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val stats: UsageStats = UsageStats(),
    val resetting: Boolean = false,
    val message: StatsMessage? = null,
)

/**
 * 设置与统计页 ViewModel。
 *
 * 只依赖 Repository 接口（由 app 的 AppContainer 注入），不直接读文件。
 */
class StatsViewModel(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private data class ActionState(
        val resetting: Boolean = false,
        val message: StatsMessage? = null,
    )

    private val actionState = MutableStateFlow(ActionState())

    private val settingsFlow = settingsRepository.observeSettings()
        .catch { error ->
            AppLogger.e(TAG, "读取设置失败，使用默认设置", error)
            emit(AppSettings())
        }

    private val statsFlow = statsRepository.observeStats()
        .catch { error ->
            AppLogger.e(TAG, "读取统计数据失败，使用空统计", error)
            emit(UsageStats())
        }

    val uiState: StateFlow<StatsUiState> = combine(
        settingsFlow,
        statsFlow,
        actionState,
    ) { settings, stats, action ->
        StatsUiState(
            loading = false,
            settings = settings,
            stats = stats,
            resetting = action.resetting,
            message = action.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StatsUiState(),
    )

    /** 主题模式：立即写入设置，MainActivity 观察后即时切换外观。 */
    fun setThemeMode(mode: ThemeMode) {
        updateSettings(failureText = "保存主题失败") { it.copy(themeMode = mode) }
    }

    /** 主题配色：与主题模式同源写入，LightDeskTheme 观察后整体换色。 */
    fun setThemePalette(palette: ThemePalette) {
        updateSettings(failureText = "保存主题配色失败") { it.copy(themePalette = palette) }
    }

    /** 天气城市：城市名与经纬度一起写入，天气模块据此刷新。 */
    fun setWeatherCity(city: WeatherCityOption) {
        updateSettings(failureText = "保存天气城市失败") {
            it.copy(
                weatherCity = city.name,
                weatherLatitude = city.latitude,
                weatherLongitude = city.longitude,
            )
        }
    }

    /** 恢复默认城市（预置列表首项）。 */
    fun resetWeatherCity() {
        setWeatherCity(WeatherCityPresets.default)
    }

    /** 待办提醒阈值（分钟）。 */
    fun setCountdownWarningMinutes(minutes: Int) {
        updateSettings(failureText = "保存提醒阈值失败") { it.copy(countdownWarningMinutes = minutes) }
    }

    /** 交互统计开关。 */
    fun setUsageStatsEnabled(enabled: Boolean) {
        updateSettings(failureText = "保存统计开关失败") { it.copy(usageStatsEnabled = enabled) }
    }

    /** 清空统计数据：成功/失败都通过 Banner 反馈。 */
    fun resetStats() {
        if (actionState.value.resetting) return
        actionState.value = actionState.value.copy(resetting = true)
        viewModelScope.launch {
            try {
                when (val result = statsRepository.reset()) {
                    is AppResult.Success ->
                        postMessage(StatsMessage("统计数据已清空", isError = false))

                    is AppResult.Failure ->
                        postMessage(StatsMessage("清空统计数据失败：${result.error.message}", isError = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "清空统计数据失败", e)
                postMessage(StatsMessage("清空统计数据失败，请稍后重试", isError = true))
            } finally {
                actionState.value = actionState.value.copy(resetting = false)
            }
        }
    }

    /** 关闭当前 Banner（手动点「知道了」或自动消失时调用）。 */
    fun dismissMessage() {
        actionState.value = actionState.value.copy(message = null)
    }

    private fun updateSettings(
        failureText: String,
        transform: (AppSettings) -> AppSettings,
    ) {
        viewModelScope.launch {
            try {
                when (val result = settingsRepository.update(transform)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure ->
                        postMessage(StatsMessage("$failureText：${result.error.message}", isError = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, failureText, e)
                postMessage(StatsMessage("$failureText，请稍后重试", isError = true))
            }
        }
    }

    private fun postMessage(message: StatsMessage) {
        actionState.value = actionState.value.copy(message = message)
    }

    private companion object {
        const val TAG = "StatsViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
