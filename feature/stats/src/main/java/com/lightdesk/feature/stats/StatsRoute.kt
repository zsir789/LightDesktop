package com.lightdesk.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightdesk.core.common.simpleViewModelFactory
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.StatsRepository
import java.time.LocalDate

/**
 * 设置与统计页入口。
 *
 * 参数由 app 层（LightDeskApp）传入，签名保持稳定。
 * 注意：`LocalDate.now()` 仅用于“今天 / 最近 12 周”等展示基准，
 * 不参与任何业务判断，因此这里不注入 TimeProvider。
 */
@Composable
fun StatsRoute(
    settingsRepository: SettingsRepository,
    statsRepository: StatsRepository,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: StatsViewModel = viewModel(
        factory = simpleViewModelFactory { StatsViewModel(settingsRepository, statsRepository) },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    StatsScreen(
        uiState = uiState,
        appVersion = appVersion,
        today = today,
        onThemeModeChange = viewModel::setThemeMode,
        onThemePaletteChange = viewModel::setThemePalette,
        onCitySelected = viewModel::setWeatherCity,
        onResetCity = viewModel::resetWeatherCity,
        onWarningMinutesSelected = viewModel::setCountdownWarningMinutes,
        onUsageStatsEnabledChange = viewModel::setUsageStatsEnabled,
        onResetStats = viewModel::resetStats,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}
