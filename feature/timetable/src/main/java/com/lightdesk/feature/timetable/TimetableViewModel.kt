package com.lightdesk.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.feature.timetable.data.IcsParser
import com.lightdesk.feature.timetable.data.ImportedTable
import com.lightdesk.feature.timetable.data.TimetableImporter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 课表展示视图：周视图 / 月视图。 */
enum class TimetableViewMode {
    WEEK,
    MONTH,
}

/**
 * 课表页 UI 状态。
 *
 * 时间相关的“今天”由 [TimeProvider] 注入并保存在状态里，避免 Composable 里直接读系统时钟，
 * 也方便测试与跨天刷新（[TimetableViewModel.refreshToday]）。
 */
data class TimetableUiState(
    val isLoading: Boolean = true,
    val importing: Boolean = false,
    val table: CourseTable? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val clearConfirmVisible: Boolean = false,
    val selectedCourse: Course? = null,
    val today: LocalDate = LocalDate.now(),
    val viewMode: TimetableViewMode = TimetableViewMode.WEEK,
    /** 相对“本周”的周偏移，0 = 本周。 */
    val weekOffset: Int = 0,
    /** 相对“本月”的月偏移，0 = 本月。 */
    val monthOffset: Int = 0,
    /** 月视图选中的日期（ISO 字符串），null 表示默认今天。 */
    val selectedDateKey: String? = null,
)

class TimetableViewModel(
    private val repository: CourseRepository,
    private val timeProvider: TimeProvider,
    private val importer: TimetableImporter = IcsParser(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        refreshToday()
        viewModelScope.launch {
            repository.observeTable()
                .catch { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "读取本地课表失败：${throwable.message ?: "未知错误"}",
                        )
                    }
                }
                .collect { table ->
                    _uiState.update { state -> state.copy(isLoading = false, table = table) }
                }
        }
    }

    // ------------------------------------------------------------------
    // 导入 / 清空
    // ------------------------------------------------------------------

    fun importIcs(fileName: String, content: String) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, errorMessage = null, infoMessage = null) }
            val imported = withContext(Dispatchers.Default) { importer.importContent(content) }
            when (imported) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(importing = false, errorMessage = imported.error.message)
                }
                is AppResult.Success -> persist(fileName, imported.value)
            }
        }
    }

    private suspend fun persist(fileName: String, imported: ImportedTable) {
        val fallbackName = fileName.substringBeforeLast('.', fileName).trim().ifBlank { DEFAULT_TABLE_NAME }
        val displayName = imported.calendarName?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName
        val table = CourseTable(
            name = displayName,
            sourceFile = fileName,
            sourceType = imported.sourceType,
            importedAt = timeProvider.now(),
            semesterStart = imported.semesterStart,
            courses = imported.courses,
        )
        when (val result = repository.replace(table)) {
            is AppResult.Failure -> _uiState.update {
                it.copy(importing = false, errorMessage = "保存课表失败：${result.error.message}")
            }
            is AppResult.Success -> {
                val semesterHint = imported.semesterStart
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "，第 1 周 $it 起" }
                    .orEmpty()
                val warningHint = if (imported.warnings.isEmpty()) {
                    ""
                } else {
                    "；${imported.warnings.size} 条解析提示"
                }
                _uiState.update {
                    it.copy(
                        importing = false,
                        infoMessage = "已导入 ${imported.courses.size} 门课程$semesterHint$warningHint",
                        weekOffset = 0,
                        monthOffset = 0,
                        selectedDateKey = null,
                    )
                }
            }
        }
    }

    fun onFileReadError(message: String) {
        _uiState.update { it.copy(importing = false, errorMessage = message) }
    }

    fun requestClear() {
        _uiState.update { it.copy(clearConfirmVisible = true) }
    }

    fun dismissClear() {
        _uiState.update { it.copy(clearConfirmVisible = false) }
    }

    fun clearTable() {
        _uiState.update { it.copy(clearConfirmVisible = false) }
        viewModelScope.launch {
            when (val result = repository.clear()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(errorMessage = "清空课表失败：${result.error.message}")
                }
                is AppResult.Success -> _uiState.update {
                    it.copy(infoMessage = "课表已清空", weekOffset = 0, monthOffset = 0, selectedDateKey = null)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 详情 / 提示条
    // ------------------------------------------------------------------

    fun selectCourse(course: Course) {
        _uiState.update { it.copy(selectedCourse = course) }
    }

    fun dismissCourseDetail() {
        _uiState.update { it.copy(selectedCourse = null) }
    }

    fun dismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ------------------------------------------------------------------
    // 周 / 月导航
    // ------------------------------------------------------------------

    fun setViewMode(mode: TimetableViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun previousWeek() {
        _uiState.update { it.copy(weekOffset = (it.weekOffset - 1).coerceAtLeast(MIN_WEEK_OFFSET)) }
    }

    fun nextWeek() {
        _uiState.update { it.copy(weekOffset = (it.weekOffset + 1).coerceAtMost(MAX_WEEK_OFFSET)) }
    }

    fun currentWeek() {
        _uiState.update { it.copy(weekOffset = 0) }
    }

    fun previousMonth() {
        _uiState.update { it.copy(monthOffset = (it.monthOffset - 1).coerceAtLeast(MIN_MONTH_OFFSET)) }
    }

    fun nextMonth() {
        _uiState.update { it.copy(monthOffset = (it.monthOffset + 1).coerceAtMost(MAX_MONTH_OFFSET)) }
    }

    fun currentMonth() {
        _uiState.update { it.copy(monthOffset = 0, selectedDateKey = null) }
    }

    /** 选中某天；点击月视图里补位的相邻月份日期时，同时把展示月份切到那个月。 */
    fun selectDate(date: LocalDate) {
        _uiState.update { state ->
            val offset = ChronoUnit.MONTHS.between(YearMonth.from(state.today), YearMonth.from(date))
                .toInt()
                .coerceIn(MIN_MONTH_OFFSET, MAX_MONTH_OFFSET)
            state.copy(monthOffset = offset, selectedDateKey = date.toString())
        }
    }

    // ------------------------------------------------------------------
    // 时间
    // ------------------------------------------------------------------

    /** 按 [TimeProvider] 重新计算“今天”，供页面进入 / 回到前台 / 跨天时调用。 */
    fun refreshToday() {
        val today = Instant.ofEpochMilli(timeProvider.now())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        _uiState.update { state -> if (state.today == today) state else state.copy(today = today) }
    }

    companion object {
        private const val DEFAULT_TABLE_NAME = "我的课表"
        private const val MIN_WEEK_OFFSET = -52
        private const val MAX_WEEK_OFFSET = 52
        private const val MIN_MONTH_OFFSET = -36
        private const val MAX_MONTH_OFFSET = 36
    }
}
