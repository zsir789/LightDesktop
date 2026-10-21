package com.lightdesk.feature.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.common.simpleViewModelFactory
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.NotesRepository
import kotlinx.coroutines.delay

/**
 * 备忘/待办统一页面入口。
 *
 * 备忘录与待办合并为一个模块，通过 [com.lightdesk.core.model.NoteItem.deadline] 区分类型：
 * 有截止时间 = 待办，无截止时间 = 备忘。
 *
 * - ViewModel 由 [simpleViewModelFactory] 创建，UI 与业务解耦；
 * - 页面活跃且存在未完成待办时，每秒刷新一次“现在”以驱动倒计时文案。
 */
@Composable
fun NotesRoute(
    notesRepository: NotesRepository,
    statsTracker: StatsTracker,
    timeProvider: TimeProvider,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotesViewModel = viewModel(
        factory = simpleViewModelFactory {
            NotesViewModel(
                notesRepository = notesRepository,
                statsTracker = statsTracker,
                timeProvider = timeProvider,
            )
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 只有存在“未完成 + 有截止时间”的待办时才需要每秒刷新，避免空转耗电。
    LaunchedEffect(uiState.hasActiveDeadline) {
        if (!uiState.hasActiveDeadline) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            viewModel.refreshNow()
        }
    }

    NotesScreen(
        uiState = uiState,
        viewModel = viewModel,
        modifier = modifier,
    )
}
