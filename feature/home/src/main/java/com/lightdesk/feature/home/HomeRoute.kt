package com.lightdesk.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.common.simpleViewModelFactory
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.data.repository.SettingsRepository

/**
 * 首页入口。
 *
 * [statsTracker] 由 app 模块在导航时统一记录（NavHost 的 `LaunchedEffect(currentRoute)`
 * 会对当前一级页面调用 `trackVisit`，其中已包含 [com.lightdesk.core.model.FeatureKey.HOME]），
 * 因此这里不再重复埋点，仅保留参数以维持既定调用签名。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun HomeRoute(
    courseRepository: CourseRepository,
    notesRepository: NotesRepository,
    settingsRepository: SettingsRepository,
    statsTracker: StatsTracker,
    timeProvider: TimeProvider,
    onOpenNotes: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = simpleViewModelFactory {
            HomeViewModel(
                courseRepository = courseRepository,
                notesRepository = notesRepository,
                settingsRepository = settingsRepository,
                timeProvider = timeProvider,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenNotes = onOpenNotes,
        onOpenTimetable = onOpenTimetable,
        modifier = modifier,
    )
}
