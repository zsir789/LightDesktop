package com.lightdesk.feature.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.getOrNull
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.WeatherRepository
import com.lightdesk.core.model.WeatherSnapshot
import com.lightdesk.feature.weather.data.OpenMeteoClient
import com.lightdesk.feature.weather.data.WeatherFallbackResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 天气页面状态。 */
data class WeatherUiState(
    val city: String = DEFAULT_CITY,
    val isLoading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: WeatherSnapshot? = null,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        const val DEFAULT_CITY = "北京"
    }
}

/**
 * 天气 ViewModel：城市/经纬度来自 [SettingsRepository]（默认北京），
 * 联网成功保存快照到 [WeatherRepository]；失败时按 [WeatherFallbackResolver] 降级缓存。
 */
class WeatherViewModel(
    private val cacheRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val statsTracker: StatsTracker,
    private val client: OpenMeteoClient = OpenMeteoClient(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var currentLocation: WeatherLocation? = null

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .map { settings ->
                    WeatherLocation(
                        city = settings.weatherCity.ifBlank { WeatherUiState.DEFAULT_CITY },
                        latitude = settings.weatherLatitude,
                        longitude = settings.weatherLongitude,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { location ->
                    currentLocation = location
                    loadWeather(location)
                }
        }
    }

    /** 手动刷新：保持当前内容可见，仅展示刷新中状态。 */
    fun refresh() {
        if (_uiState.value.refreshing) return
        val location = currentLocation ?: return
        viewModelScope.launch { loadWeather(location) }
    }

    private suspend fun loadWeather(location: WeatherLocation) {
        _uiState.update { state ->
            state.copy(
                city = location.city,
                isLoading = state.snapshot == null,
                refreshing = true,
                errorMessage = null,
            )
        }

        val cached = cacheRepository.cached()
        val online = client.fetch(
            city = location.city,
            latitude = location.latitude,
            longitude = location.longitude,
        )
        val onlineSnapshot = online.getOrNull()
        if (onlineSnapshot != null) {
            // 缓存写入失败不影响本次展示，页面仍展示在线数据。
            cacheRepository.save(onlineSnapshot)
            // 统计各天气类型出现次数（仅在线成功时记录，离线降级不重复计数）。
            statsTracker.trackWeatherCondition(onlineSnapshot.condition)
        }

        val outcome = WeatherFallbackResolver.resolve(online = online, cached = cached)
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                refreshing = false,
                snapshot = outcome.snapshot,
                isOffline = outcome.isOffline,
                errorMessage = outcome.errorMessage,
            )
        }
    }

    private data class WeatherLocation(
        val city: String,
        val latitude: Double,
        val longitude: Double,
    )
}
