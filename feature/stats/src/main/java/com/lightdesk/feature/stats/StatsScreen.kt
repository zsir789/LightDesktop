package com.lightdesk.feature.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdesk.core.designsystem.component.CenteredHint
import com.lightdesk.core.designsystem.component.ConfirmDialog
import com.lightdesk.core.designsystem.component.EmptyState
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.InfoBanner
import com.lightdesk.core.designsystem.component.LoadingView
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.ScreenHeader
import com.lightdesk.core.designsystem.component.SectionCard
import com.lightdesk.core.designsystem.component.SectionTitle
import com.lightdesk.core.designsystem.component.SettingSwitchRow
import com.lightdesk.core.designsystem.component.SettingValueRow
import com.lightdesk.core.designsystem.component.TagPill
import com.lightdesk.core.designsystem.component.TextAction
import com.lightdesk.core.designsystem.theme.LightDeskPalettes
import com.lightdesk.core.designsystem.theme.LocalStatusColors
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.ThemeMode
import com.lightdesk.core.model.ThemePalette
import com.lightdesk.core.model.UsageStats
import java.time.LocalDate
import kotlinx.coroutines.delay

/** 顶部一次性提示（Info/Error Banner）自动消失时间。 */
private const val MESSAGE_AUTO_DISMISS_MS = 4_000L

/** 热力图左侧月份标签列宽。 */
private val HEATMAP_MONTH_COLUMN_WIDTH = 34.dp

/** 热力图格子尺寸圆角。 */
private val HEAT_CELL_CORNER = 4.dp

/** 最近 7 天小计的星期简称（周一..周日），与 CalendarUtils.recentWeeks 的列顺序一致。 */
private val WEEKDAY_SHORT_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 主题配色色卡：预览块高度。 */
private val PALETTE_PREVIEW_HEIGHT = 34.dp

/** 主题配色色卡：预览块圆角。 */
private val PALETTE_PREVIEW_CORNER = 8.dp

/** 主题配色色卡之间的间距。 */
private val PALETTE_SWATCH_SPACING = 6.dp

/** 主题配色色卡：选中描边宽度。 */
private val PALETTE_SELECTED_BORDER = 2.dp

/** 主题配色色卡：未选中描边宽度（仅用来勾勒浅色背景块，避免和卡片背景糊在一起）。 */
private val PALETTE_IDLE_BORDER = 1.dp

/**
 * 设置与统计页（v1.1.0 合页重写）。
 *
 * 结构：Settings 区（主题模式/主题配色/城市/提醒阈值/统计开关/关于/清空数据）+ 统计区
 * （使用概览、备忘统计、天气类型、近 12 周热力图、今日数据）。
 * 统计区无任何记录时整体显示 [EmptyState] 引导，设置区始终可见。
 */
@Composable
internal fun StatsScreen(
    uiState: StatsUiState,
    appVersion: String,
    today: LocalDate,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePaletteChange: (ThemePalette) -> Unit,
    onCitySelected: (WeatherCityOption) -> Unit,
    onResetCity: () -> Unit,
    onWarningMinutesSelected: (Int) -> Unit,
    onUsageStatsEnabledChange: (Boolean) -> Unit,
    onResetStats: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCityDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(MESSAGE_AUTO_DISMISS_MS)
            onDismissMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "设置与统计",
            subtitle = "轻桌面 v$appVersion",
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = PageHorizontalPadding,
                end = PageHorizontalPadding,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.message?.let { message ->
                item(key = "message") {
                    MessageBanner(message = message, onDismiss = onDismissMessage)
                }
            }

            // ---------------- 设置 ----------------
            item(key = "settings_section") { SectionTitle("设置") }
            item(key = "theme_mode_card") {
                ThemeModeCard(
                    current = uiState.settings.themeMode,
                    onSelect = onThemeModeChange,
                )
            }
            item(key = "palette_card") {
                ThemePaletteCard(
                    current = uiState.settings.themePalette,
                    onSelect = onThemePaletteChange,
                )
            }
            item(key = "settings_card") {
                SettingsCard(
                    settings = uiState.settings,
                    onEditCity = { showCityDialog = true },
                    onEditWarning = { showWarningDialog = true },
                    onUsageStatsEnabledChange = onUsageStatsEnabledChange,
                )
            }

            item(key = "about_section") { SectionTitle("关于") }
            item(key = "about_card") { AboutCard(appVersion = appVersion) }

            item(key = "data_section") { SectionTitle("数据") }
            item(key = "clear_stats") {
                ClearStatsCard(
                    resetting = uiState.resetting,
                    onClick = { showResetConfirm = true },
                )
            }

            // ---------------- 统计 ----------------
            item(key = "stats_section") { SectionTitle("统计") }

            if (uiState.loading) {
                item(key = "loading") { LoadingView(text = "正在读取统计数据…") }
            } else if (!StatsPresentation.hasAnyStats(uiState.stats)) {
                item(key = "stats_empty") {
                    EmptyState(
                        title = "还没有统计数据",
                        description = "使用首页、备忘、课表或天气功能后，这里会自动汇总你的使用情况。",
                        icon = Icons.Outlined.BarChart,
                    )
                }
            } else {
                item(key = "overview_title") {
                    SectionTitle(
                        text = "使用概览",
                        trailing = { TagPill("累计 ${StatsPresentation.totalVisits(uiState.stats)} 次") },
                    )
                }
                item(key = "overview_card") { OverviewCard(stats = uiState.stats, today = today) }

                item(key = "notes_title") { SectionTitle("备忘统计") }
                item(key = "notes_card") { NotesStatsCard(stats = uiState.stats) }

                item(key = "weather_title") { SectionTitle("天气类型") }
                item(key = "weather_card") { WeatherStatsCard(stats = uiState.stats) }

                item(key = "heatmap_title") { SectionTitle("近 ${HeatmapConfig.WEEKS} 周活动") }
                item(key = "heatmap_card") { HeatmapCard(stats = uiState.stats, today = today) }

                item(key = "today_title") { SectionTitle("今日数据") }
                item(key = "today_card") { TodayCard(stats = uiState.stats, today = today) }
            }
        }
    }

    if (showCityDialog) {
        WeatherCityDialog(
            currentCity = uiState.settings.weatherCity,
            onSelect = { city ->
                onCitySelected(city)
                showCityDialog = false
            },
            onResetDefault = {
                onResetCity()
                showCityDialog = false
            },
            onDismiss = { showCityDialog = false },
        )
    }

    if (showWarningDialog) {
        CountdownWarningDialog(
            currentMinutes = uiState.settings.countdownWarningMinutes,
            onSelect = { minutes ->
                onWarningMinutesSelected(minutes)
                showWarningDialog = false
            },
            onDismiss = { showWarningDialog = false },
        )
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "清空统计数据？",
            message = "将删除所有使用次数、活动热力图与天气统计，此操作不可撤销；设置项不受影响。",
            confirmText = "清空",
            onConfirm = {
                showResetConfirm = false
                onResetStats()
            },
            onDismiss = { showResetConfirm = false },
            destructive = true,
        )
    }
}

// ---------------------------------------------------------------------------
// 提示条
// ---------------------------------------------------------------------------

@Composable
private fun MessageBanner(message: StatsMessage, onDismiss: () -> Unit) {
    if (message.isError) {
        ErrorBanner(message = message.text, actionLabel = "知道了", onAction = onDismiss)
    } else {
        InfoBanner(message = message.text, actionLabel = "知道了", onAction = onDismiss)
    }
}

// ---------------------------------------------------------------------------
// 设置卡片
// ---------------------------------------------------------------------------

@Composable
private fun ThemeModeCard(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SectionCard {
        Text(
            text = "主题模式",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "跟随系统，或固定为浅色 / 深色外观",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ThemeModeSelector(current = current, onSelect = onSelect)
    }
}

/** 主题配色卡片：6 个色卡横向排布，点选即写入设置并即时换色。 */
@Composable
private fun ThemePaletteCard(
    current: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
) {
    // 选项固定不变，避免每次重组都重新映射文案
    val options = remember { StatsPresentation.themePaletteOptions() }
    // 深色外观下用深色档色值预览，保证色卡在深色背景上仍有对比度
    val darkPreview = MaterialTheme.colorScheme.background.luminance() < 0.5f

    SectionCard {
        Text(
            text = "主题配色",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "换一套强调色，背景会同步带上极浅的同色倾向",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(PALETTE_SWATCH_SPACING)) {
            options.forEach { option ->
                PaletteSwatch(
                    option = option,
                    selected = option.palette == current,
                    darkPreview = darkPreview,
                    onClick = { onSelect(option.palette) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = StatsPresentation.themePaletteSummary(current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 单个配色色卡：三色预览块（主色 / 辅色 / 背景）+ 中文名。
 *
 * 描边通过叠加层绘制、不参与布局，选中与否只改变线宽与颜色，色卡尺寸保持稳定。
 */
@Composable
private fun PaletteSwatch(
    option: StatsPresentation.ThemePaletteOption,
    selected: Boolean,
    darkPreview: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LightDeskPalettes.of(option.palette, darkPreview)
    val borderWidth by animateDpAsState(
        targetValue = if (selected) PALETTE_SELECTED_BORDER else PALETTE_IDLE_BORDER,
        label = "paletteBorderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "paletteBorderColor",
    )
    val shape = RoundedCornerShape(PALETTE_PREVIEW_CORNER)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PALETTE_PREVIEW_HEIGHT),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.primary),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.secondary),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.background),
                )
            }
            // 描边叠在色块之上，避免边框压住三色预览
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(width = borderWidth, color = borderColor, shape = shape),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已选中",
                        tint = palette.primary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCard(
    settings: AppSettings,
    onEditCity: () -> Unit,
    onEditWarning: () -> Unit,
    onUsageStatsEnabledChange: (Boolean) -> Unit,
) {
    SectionCard {
        SettingValueRow(
            label = "天气城市",
            value = settings.weatherCity,
            onClick = onEditCity,
        )
        SectionDivider()
        SettingValueRow(
            label = "待办提醒阈值",
            value = CountdownWarningOptions.labelFor(settings.countdownWarningMinutes),
            onClick = onEditWarning,
        )
        SectionDivider()
        SettingSwitchRow(
            title = "记录使用统计",
            checked = settings.usageStatsEnabled,
            onCheckedChange = onUsageStatsEnabledChange,
            description = "关闭后不再累计使用次数；已记录的统计数据会保留。",
        )
    }
}

@Composable
private fun ThemeModeSelector(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.values().forEach { mode ->
            ThemeModeOption(
                label = themeModeLabel(mode),
                selected = mode == current,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun AboutCard(appVersion: String) {
    SectionCard {
        SettingValueRow(label = "应用版本", value = "v$appVersion")
        SectionDivider()
        Text(
            text = "开发者说明",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "轻桌面是一款本地优先的轻量桌面小工具，包含备忘、课表与天气。" +
                "如果使用中遇到问题或有建议，欢迎通过应用页面反馈。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "数据存储",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "所有设置与统计仅保存在本机应用私有目录（JSON 文件），不会上传服务器；" +
                "卸载应用或清除应用数据会一并删除，请按需自行备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClearStatsCard(
    resetting: Boolean,
    onClick: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "清空统计数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "删除使用次数、活动热力图与天气统计，设置项不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextAction(
                text = if (resetting) "清空中…" else "清空",
                onClick = onClick,
                color = LocalStatusColors.current.danger,
                enabled = !resetting,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 使用概览
// ---------------------------------------------------------------------------

@Composable
private fun OverviewCard(stats: UsageStats, today: LocalDate) {
    val rows = remember(stats, today) { StatsPresentation.buildFeatureRows(stats, today) }
    SectionCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) SectionDivider()
            FeatureUsageItem(row = row)
        }
    }
}

@Composable
private fun FeatureUsageItem(row: StatsPresentation.FeatureUsageRow) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureIconBadge(feature = row.feature)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = StatsPresentation.featureName(row.feature),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = row.lastUsedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${row.count} 次",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "占比 ${row.percentOfTotal}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        ProgressBar(progress = row.relativeProgress)
    }
}

@Composable
private fun FeatureIconBadge(feature: FeatureKey) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            imageVector = featureIcon(feature),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(9.dp),
        )
    }
}

private fun featureIcon(feature: FeatureKey): ImageVector = when (feature) {
    FeatureKey.HOME -> Icons.Outlined.Home
    FeatureKey.NOTES -> Icons.Outlined.EditNote
    FeatureKey.TIMETABLE -> Icons.Outlined.CalendarMonth
    FeatureKey.WEATHER -> Icons.Outlined.WbSunny
}

// ---------------------------------------------------------------------------
// 备忘统计
// ---------------------------------------------------------------------------

@Composable
private fun NotesStatsCard(stats: UsageStats) {
    val summary = remember(stats) { StatsPresentation.noteSummary(stats) }
    SectionCard {
        Row {
            MetricColumn(
                modifier = Modifier.weight(1f),
                value = "${summary.total}",
                label = "记录总数",
            )
            MetricColumn(
                modifier = Modifier.weight(1f),
                value = "${summary.completed}",
                label = "完成次数",
            )
            MetricColumn(
                modifier = Modifier.weight(1f),
                value = "${summary.rate}%",
                label = "完成率",
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        ProgressBar(progress = summary.progress)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "完成率 = 完成次数 / 记录总数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 天气类型统计
// ---------------------------------------------------------------------------

@Composable
private fun WeatherStatsCard(stats: UsageStats) {
    val rows = remember(stats) { StatsPresentation.buildWeatherRows(stats) }
    SectionCard {
        if (rows.isEmpty()) {
            CenteredHint(text = "还没有天气记录，打开天气页获取一次天气后自动统计。")
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) SectionDivider()
                WeatherConditionItem(row = row)
            }
        }
    }
}

@Composable
private fun WeatherConditionItem(row: StatsPresentation.WeatherConditionRow) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${row.count} 次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${row.percent}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        ProgressBar(progress = row.progress)
    }
}

// ---------------------------------------------------------------------------
// 近 12 周热力图
// ---------------------------------------------------------------------------

@Composable
private fun HeatmapCard(stats: UsageStats, today: LocalDate) {
    val weeks = remember(stats, today) { StatsPresentation.buildHeatmap(stats, today) }
    val recentDays = remember(stats, today) { StatsPresentation.recentDayTotals(stats, today) }
    SectionCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
        WeekdayHeaderRow()
        Spacer(modifier = Modifier.height(4.dp))
        weeks.forEach { week ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = week.monthLabel.orEmpty(),
                    modifier = Modifier.width(HEATMAP_MONTH_COLUMN_WIDTH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                week.days.forEach { day ->
                    HeatCell(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HeatmapLegend()
        Spacer(modifier = Modifier.height(14.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "近 ${HeatmapConfig.RECENT_DAYS} 天每日活动",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row {
            recentDays.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WEEKDAY_SHORT_LABELS[(day.date.dayOfWeek.value - 1).coerceIn(0, 6)],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${day.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row {
        Spacer(modifier = Modifier.width(HEATMAP_MONTH_COLUMN_WIDTH))
        WEEKDAY_SHORT_LABELS.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeatCell(day: StatsPresentation.HeatDay, modifier: Modifier = Modifier) {
    val color: Color = when {
        day.isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        day.level <= 0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary.copy(
            alpha = HeatmapConfig.levelAlphas[day.level.coerceIn(0, HeatmapConfig.LEVEL_COUNT - 1)],
        )
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(HEAT_CELL_CORNER))
            .background(color),
    )
}

@Composable
private fun HeatmapLegend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (level in 0 until HeatmapConfig.LEVEL_COUNT) {
            val color = if (level == 0) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = HeatmapConfig.levelAlphas[level])
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 今日数据
// ---------------------------------------------------------------------------

@Composable
private fun TodayCard(stats: UsageStats, today: LocalDate) {
    val summary = remember(stats, today) { StatsPresentation.todaySummary(stats, today) }
    SectionCard {
        Row {
            MetricColumn(modifier = Modifier.weight(1f), value = "${summary.visits}", label = "访问")
            MetricColumn(modifier = Modifier.weight(1f), value = "${summary.notesCreated}", label = "新建")
            MetricColumn(modifier = Modifier.weight(1f), value = "${summary.notesCompleted}", label = "完成")
            MetricColumn(modifier = Modifier.weight(1f), value = "${summary.weatherFetches}", label = "天气")
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// 通用小部件
// ---------------------------------------------------------------------------

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

// ---------------------------------------------------------------------------
// 弹窗
// ---------------------------------------------------------------------------

@Composable
private fun WeatherCityDialog(
    currentCity: String,
    onSelect: (WeatherCityOption) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "选择天气城市", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                WeatherCityPresets.cities.forEach { city ->
                    val selected = city.name == currentCity
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(city) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = city.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "经纬度 ${formatCoordinate(city.latitude)}, ${formatCoordinate(city.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "当前城市",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextAction(
                    text = "恢复默认城市（${WeatherCityPresets.default.name}）",
                    onClick = onResetDefault,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextAction(
                text = "取消",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun CountdownWarningDialog(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "待办提醒阈值", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                Text(
                    text = "待办临近截止多少时间时提醒你？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CountdownWarningOptions.options.forEach { option ->
                    val selected = option.minutes == currentMinutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(option.minutes) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "当前阈值",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextAction(
                text = "取消",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

/** 经纬度展示保留两位小数，避免弹窗里出现一长串小数。 */
private fun formatCoordinate(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
