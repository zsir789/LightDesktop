package com.lightdesk.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lightdesk.app.di.AppContainer
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.feature.home.HomeRoute
import com.lightdesk.feature.notes.NotesRoute
import com.lightdesk.feature.stats.StatsRoute
import com.lightdesk.feature.timetable.TimetableRoute
import com.lightdesk.feature.weather.WeatherRoute

/**
 * 一级页面（底部导航项）。新增模块时在此追加即可：
 * 只需实现同名 Route 并在 [LightDeskApp] 的 NavHost 中登记。
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val featureKey: FeatureKey?,
) {
    HOME("home", "首页", Icons.Outlined.Home, FeatureKey.HOME),
    NOTES("notes", "备忘", Icons.Outlined.EditNote, FeatureKey.NOTES),
    TIMETABLE("timetable", "课表", Icons.Outlined.CalendarMonth, FeatureKey.TIMETABLE),
    WEATHER("weather", "天气", Icons.Outlined.WbSunny, FeatureKey.WEATHER),
    STATS("stats", "设置与统计", Icons.Outlined.Tune, null),
}

private const val TRANSITION_MS = 260

/**
 * 应用主界面：Scaffold + 底部导航 + NavHost。
 * 各功能页只接收仓储接口与工具，不感知彼此，便于独立开发与替换。
 */
@Composable
fun LightDeskApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 统一埋点：任一一级页面进入时累加使用次数，功能页无需关心统计实现
    LaunchedEffect(currentRoute) {
        TopLevelDestination.entries
            .firstOrNull { it.route == currentRoute }
            ?.featureKey
            ?.let(container.statsTracker::trackVisit)
    }

    fun navigateTo(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val enterTransition: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 12 }
    }
    val exitTransition: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        fadeOut(tween(TRANSITION_MS / 2))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                currentRoute = currentRoute,
                onSelect = ::navigateTo,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = enterTransition,
            popExitTransition = exitTransition,
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeRoute(
                    courseRepository = container.courseRepository,
                    notesRepository = container.notesRepository,
                    settingsRepository = container.settingsRepository,
                    statsTracker = container.statsTracker,
                    timeProvider = container.timeProvider,
                    onOpenNotes = { navigateTo(TopLevelDestination.NOTES) },
                    onOpenTimetable = { navigateTo(TopLevelDestination.TIMETABLE) },
                )
            }
            composable(TopLevelDestination.NOTES.route) {
                NotesRoute(
                    notesRepository = container.notesRepository,
                    statsTracker = container.statsTracker,
                    timeProvider = container.timeProvider,
                )
            }
            composable(TopLevelDestination.TIMETABLE.route) {
                TimetableRoute(
                    repository = container.courseRepository,
                    statsTracker = container.statsTracker,
                    timeProvider = container.timeProvider,
                )
            }
            composable(TopLevelDestination.WEATHER.route) {
                WeatherRoute(
                    cacheRepository = container.weatherRepository,
                    settingsRepository = container.settingsRepository,
                    statsTracker = container.statsTracker,
                )
            }
            composable(TopLevelDestination.STATS.route) {
                StatsRoute(
                    settingsRepository = container.settingsRepository,
                    statsRepository = container.statsRepository,
                    appVersion = container.appVersion,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column {
            // 顶部细分隔线：让底部栏与浅色页面背景形成柔和层次，而不是硬阴影
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    BottomBarItem(
                        destination = destination,
                        selected = currentRoute == destination.route,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (selected) activeColor else inactiveColor
    val palette = LocalLightDeskPalette.current
    // 选中项使用当前配色的柔和底色，换色时底部栏跟着变色，交互反馈更明显
    val pillColor by animateColorAsState(
        targetValue = if (selected) palette.accentSoft else Color.Transparent,
        animationSpec = tween(220),
        label = "bottomBarPill",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(220),
        label = "bottomBarIconScale",
    )

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pillColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(scale),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
        // 选中指示条：以高度动画呈现，避免生硬切换
        Spacer(modifier = Modifier.height(3.dp))
        Surface(
            color = if (selected) activeColor else androidx.compose.ui.graphics.Color.Transparent,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .size(width = 14.dp, height = 2.dp),
        ) {}
    }
}
