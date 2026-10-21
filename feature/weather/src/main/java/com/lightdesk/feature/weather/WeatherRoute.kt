package com.lightdesk.feature.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightdesk.core.common.simpleViewModelFactory
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.WeatherRepository
import com.lightdesk.core.model.FeatureKey

/**
 * 天气功能入口：Open-Meteo 联网获取 + 本地缓存降级。
 * 城市/经纬度来自设置（由本模块观察 SettingsRepository），成功获取时记录天气类型统计。
 */
@Composable
fun WeatherRoute(
    cacheRepository: WeatherRepository,
    settingsRepository: SettingsRepository,
    statsTracker: StatsTracker,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        statsTracker.trackVisit(FeatureKey.WEATHER)
    }

    val viewModel: WeatherViewModel = viewModel(
        factory = simpleViewModelFactory {
            WeatherViewModel(cacheRepository, settingsRepository, statsTracker)
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WeatherScreen(
        state = state,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    )
}
