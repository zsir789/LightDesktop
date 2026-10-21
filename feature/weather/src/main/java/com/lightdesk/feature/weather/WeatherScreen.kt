package com.lightdesk.feature.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdesk.core.common.TimeFormat
import com.lightdesk.core.designsystem.component.CenteredHint
import com.lightdesk.core.designsystem.component.EmptyState
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.InfoBanner
import com.lightdesk.core.designsystem.component.LoadingView
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.ScreenHeader
import com.lightdesk.core.designsystem.component.SectionCard
import com.lightdesk.core.designsystem.component.SectionTitle
import com.lightdesk.core.designsystem.theme.LocalStatusColors
import com.lightdesk.core.model.DailyForecast
import com.lightdesk.core.model.WeatherSnapshot
import com.lightdesk.feature.weather.data.WeatherCodeMapper
import com.lightdesk.feature.weather.data.WeatherIcon
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 天气主界面：当前天气 + 未来 7 天 + 刷新；网络异常时按状态展示离线缓存或错误提示。
 * 颜色全部来自主题，图标随天气码映射。
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "天气",
            subtitle = state.snapshot?.city ?: state.city,
            actions = {
                if (state.refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新天气",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageHorizontalPadding),
        ) {
            val snapshot = state.snapshot
            when {
                state.isLoading && snapshot == null -> {
                    LoadingView(text = "正在获取天气…")
                }

                snapshot == null -> {
                    if (state.errorMessage != null) {
                        ErrorBanner(
                            message = state.errorMessage,
                            actionLabel = "重试",
                            onAction = onRefresh,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        EmptyState(
                            title = "暂时获取不到天气",
                            description = "请检查网络连接后重试，之前的数据会保留在本地缓存中。",
                            icon = Icons.Outlined.WbSunny,
                            actionLabel = "重新获取",
                            onAction = onRefresh,
                        )
                    } else {
                        EmptyState(
                            title = "暂无天气数据",
                            description = "点击下方按钮获取当前天气。",
                            icon = Icons.Outlined.WbSunny,
                            actionLabel = "获取天气",
                            onAction = onRefresh,
                        )
                    }
                }

                else -> {
                    if (state.isOffline) {
                        InfoBanner(
                            message = "离线缓存 · 更新于 ${TimeFormat.timestampToDateTimeText(snapshot.updatedAt)}",
                            actionLabel = "重试",
                            onAction = onRefresh,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    CurrentWeatherCard(snapshot = snapshot)
                    Spacer(modifier = Modifier.height(14.dp))
                    DailyForecastCard(snapshot = snapshot)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (state.isOffline) {
                            "当前展示的是本地缓存数据，联网后会更新。"
                        } else {
                            "数据来源：Open-Meteo · 更新于 ${TimeFormat.timestampToDateTimeText(snapshot.updatedAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CurrentWeatherCard(snapshot: WeatherSnapshot) {
    val condition = WeatherCodeMapper.map(snapshot.weatherCode)
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.city,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${snapshot.temperature.roundToInt()}°",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = condition.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = iconOf(condition.icon),
                contentDescription = condition.description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(76.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                label = "体感",
                value = "${snapshot.apparentTemperature.roundToInt()}°",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "湿度",
                value = "${snapshot.humidity}%",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "风速",
                value = "${snapshot.windSpeed.roundToInt()} km/h",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "更新于 ${TimeFormat.timestampToDateTimeText(snapshot.updatedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DailyForecastCard(snapshot: WeatherSnapshot) {
    SectionCard {
        SectionTitle(text = "未来 ${snapshot.daily.size} 天")
        Spacer(modifier = Modifier.height(4.dp))
        if (snapshot.daily.isEmpty()) {
            CenteredHint(text = "暂无未来天气预报")
        } else {
            snapshot.daily.forEachIndexed { index, day ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DailyRow(day = day, index = index)
            }
        }
    }
}

@Composable
private fun DailyRow(day: DailyForecast, index: Int) {
    val statusColors = LocalStatusColors.current
    val condition = WeatherCodeMapper.map(day.weatherCode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabel(day.date, index),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = iconOf(condition.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = condition.description,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (day.precipitationProbability > 0) {
            Text(
                text = "${day.precipitationProbability}%",
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = statusColors.info,
            )
        }
        Text(
            text = "${day.tempMax.roundToInt()}° / ${day.tempMin.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/** 把日期文案转成“今天/明天/周几”，解析失败时回退原始日期。 */
private fun dayLabel(dateText: String, index: Int): String {
    if (index == 0) return "今天"
    if (index == 1) return "明天"
    return runCatching {
        val date = LocalDate.parse(dateText)
        TimeFormat.weekdayLabel(date.dayOfWeek.value)
    }.getOrDefault(dateText)
}

private fun iconOf(icon: WeatherIcon): ImageVector = when (icon) {
    WeatherIcon.CLEAR -> Icons.Outlined.WbSunny
    WeatherIcon.PARTLY_CLOUDY -> Icons.Outlined.WbCloudy
    WeatherIcon.CLOUDY -> Icons.Outlined.Cloud
    WeatherIcon.FOG -> Icons.Outlined.BlurOn
    WeatherIcon.DRIZZLE -> Icons.Outlined.Grain
    WeatherIcon.RAIN -> Icons.Outlined.Opacity
    WeatherIcon.SHOWERS -> Icons.Outlined.Umbrella
    WeatherIcon.SNOW -> Icons.Outlined.AcUnit
    WeatherIcon.THUNDERSTORM -> Icons.Outlined.FlashOn
    WeatherIcon.UNKNOWN -> Icons.Outlined.HelpOutline
}
