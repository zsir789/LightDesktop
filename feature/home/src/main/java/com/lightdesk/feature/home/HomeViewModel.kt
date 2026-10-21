package com.lightdesk.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.NoteItem
import com.lightdesk.core.model.TimetableCalendar
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 首页聚合状态。
 *
 * 注意：[loading] 只在首次加载时为 true；后续刷新失败只更新 [error]，不遮挡已有内容。
 */
data class HomeUiState(
    val now: Long = 0L,
    val greeting: String = "",
    val dateLabel: String = "",
    val weekNumber: Int? = null,
    val hasTimetable: Boolean = false,
    val courses: List<TodayCourse> = emptyList(),
    val todos: List<TodayTodo> = emptyList(),
    val pendingTodoCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 需要高亮的课程：正在上的课，否则下一节；全部结束为 null。 */
    val highlightedCourse: TodayCourse?
        get() = HomePresentation.highlighted(courses)

    val highlightedCourseId: String?
        get() = HomePresentation.highlightedCourseId(courses)
}

/**
 * 首页 ViewModel：合并课表、备忘两个数据源，并驱动“每秒刷新”的时钟。
 *
 * 时钟仅在存在未完成待办时开启（倒计时需要实时刷新），没有待办时只取一次当前时间，
 * 避免无意义的重组；页面不可见时 StateFlow 按 [SharingStarted.WhileSubscribed] 停止收集，
 * 从而自动停止 ticker。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val courseRepository: CourseRepository,
    private val notesRepository: NotesRepository,
    private val timeProvider: TimeProvider,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** 手动刷新信号：自增即可触发重新订阅数据源。 */
    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = refreshTrigger
        .flatMapLatest { loadSources() }
        .flatMapLatest { sources ->
            nowFlow(sources).map { now ->
                buildHomeUiState(
                    table = sources.table,
                    items = sources.items,
                    now = now,
                    zone = zone,
                    error = sources.error,
                )
            }
        }
        .catch { throwable ->
            emit(
                buildHomeUiState(
                    table = null,
                    items = emptyList(),
                    now = timeProvider.now(),
                    zone = zone,
                    error = "首页数据加载失败：${throwable.message ?: "未知错误"}",
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = buildHomeUiState(
                table = null,
                items = emptyList(),
                now = timeProvider.now(),
                zone = zone,
            ).copy(loading = true),
        )

    /** 重新订阅数据源（下拉刷新/重试）。 */
    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    /** 合并两个仓储的数据；任一侧异常转成可展示的错误文案，不中断另一侧。 */
    private fun loadSources(): Flow<HomeSources> {
        val tableFlow: Flow<Result<CourseTable?>> = courseRepository.observeTable()
            .map<CourseTable?, Result<CourseTable?>> { table -> Result.success(table) }
            .catch { throwable -> emit(Result.failure(throwable)) }

        val notesFlow: Flow<Result<List<NoteItem>>> = notesRepository.observeItems()
            .map<List<NoteItem>, Result<List<NoteItem>>> { items -> Result.success(items) }
            .catch { throwable -> emit(Result.failure(throwable)) }

        return combine(tableFlow, notesFlow) { tableResult, notesResult ->
            val messages = buildList {
                tableResult.exceptionOrNull()?.let { add("读取课表失败：${it.message ?: "未知错误"}") }
                notesResult.exceptionOrNull()?.let { add("读取备忘失败：${it.message ?: "未知错误"}") }
            }
            HomeSources(
                table = tableResult.getOrNull(),
                items = notesResult.getOrNull().orEmpty(),
                error = messages.takeIf { it.isNotEmpty() }?.joinToString("；"),
            )
        }
    }

    /** 有待办时每秒推送一次当前时间，否则只取一次快照。 */
    private fun nowFlow(sources: HomeSources): Flow<Long> {
        val needsTicking = sources.items.any { it.isTodo && !it.done }
        if (!needsTicking) {
            return flowOf(timeProvider.now())
        }
        return flow {
            while (true) {
                emit(timeProvider.now())
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    private data class HomeSources(
        val table: CourseTable?,
        val items: List<NoteItem>,
        val error: String?,
    )

    internal companion object {
        const val TICK_INTERVAL_MILLIS: Long = 1_000L
        const val STOP_TIMEOUT_MILLIS: Long = 5_000L
    }
}

/**
 * 由原始数据构建首页状态（纯函数，供 ViewModel 与单元测试共用）。
 */
internal fun buildHomeUiState(
    table: CourseTable?,
    items: List<NoteItem>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    error: String? = null,
): HomeUiState {
    val today = HomePresentation.localDate(now, zone)
    return HomeUiState(
        now = now,
        greeting = HomePresentation.greeting(HomePresentation.hourOfDay(now, zone)),
        dateLabel = HomePresentation.dateLabel(today),
        weekNumber = TimetableCalendar.currentWeek(table, now),
        hasTimetable = HomePresentation.hasTimetable(table),
        courses = HomePresentation.todayCourses(table, now, zone),
        todos = HomePresentation.todayTodosUi(items, now, zone),
        pendingTodoCount = HomePresentation.pendingTodoCount(items),
        loading = false,
        error = error,
    )
}
