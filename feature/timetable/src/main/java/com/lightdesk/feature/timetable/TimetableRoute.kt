package com.lightdesk.feature.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.common.getOrNull
import com.lightdesk.core.common.simpleViewModelFactory
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.feature.timetable.data.IcsFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统文件选择器用的 MIME 类型：不同 ROM 对 WakeUp 导出的 .ics 识别结果不同，
 * 因此同时声明 text/calendar、octet-stream、text/plain 与通配符，保证一定能选中文件。
 */
private val ICS_MIME_TYPES = arrayOf(
    "text/calendar",
    "application/octet-stream",
    "text/plain",
    "*/*",
)

/**
 * 课表功能入口：系统文件选择器导入 WakeUp 导出的 .ics 文件，支持周视图 / 月视图展示。
 * 只依赖 core 中的仓储接口与工具，具体逻辑在 [TimetableViewModel] 与 [TimetableScreen] 中。
 */
@Composable
fun TimetableRoute(
    repository: CourseRepository,
    statsTracker: StatsTracker,
    timeProvider: TimeProvider,
    modifier: Modifier = Modifier,
) {
    val viewModel: TimetableViewModel = viewModel(
        factory = simpleViewModelFactory { TimetableViewModel(repository, timeProvider) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        statsTracker.trackVisit(FeatureKey.TIMETABLE)
        viewModel.refreshToday()
    }
    // 从后台回到前台时刷新“今天”，避免跨天后课表仍停在昨天
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val readResult = withContext(Dispatchers.IO) { IcsFileReader.read(context, uri) }
            val fileContent = readResult.getOrNull()
            if (fileContent != null) {
                viewModel.importIcs(
                    fileName = fileContent.fileName,
                    content = fileContent.content,
                )
            } else {
                val message = (readResult as? AppResult.Failure)?.error?.message
                    ?: "读取文件失败，请重新选择文件"
                viewModel.onFileReadError(message)
            }
        }
    }

    TimetableScreen(
        state = state,
        onImportClick = {
            runCatching { filePicker.launch(ICS_MIME_TYPES) }
                .onFailure { throwable ->
                    viewModel.onFileReadError("无法打开文件选择器：${throwable.message ?: "未知错误"}")
                }
        },
        onClearClick = viewModel::requestClear,
        onConfirmClear = viewModel::clearTable,
        onDismissClear = viewModel::dismissClear,
        onDismissInfo = viewModel::dismissInfoMessage,
        onDismissError = viewModel::dismissErrorMessage,
        onViewModeChange = viewModel::setViewMode,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onCurrentWeek = viewModel::currentWeek,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onCurrentMonth = viewModel::currentMonth,
        onSelectDate = viewModel::selectDate,
        onCourseClick = viewModel::selectCourse,
        onDismissCourse = viewModel::dismissCourseDetail,
        modifier = modifier,
    )
}
