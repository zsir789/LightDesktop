package com.lightdesk.feature.home

import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.NoteItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 首页 ViewModel 单测：数据聚合、刷新重订阅、错误兜底、倒计时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val today: LocalDate = LocalDate.of(2026, 10, 19)
    private val now: Long = at(2026, 10, 19, 9, 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun todo(id: String, deadline: Long?, done: Boolean = false): NoteItem = NoteItem(
        id = id,
        title = "待办-$id",
        deadline = deadline,
        done = done,
    )

    private fun course(id: String, startHour: Int, endHour: Int): Course = Course(
        id = id,
        title = "课程-$id",
        weekday = today.dayOfWeek.value,
        startMinutes = startHour * 60,
        endMinutes = endHour * 60,
    )

    @Test
    fun `ui state aggregates timetable and today todos`() = runTest(dispatcher) {
        val semesterStart = today.minusWeeks(1).toString()
        val table = CourseTable(
            semesterStart = semesterStart,
            courses = listOf(course("morning", 8, 10), course("noon", 14, 16)),
        )
        val notes = listOf(
            todo("today", at(2026, 10, 19, 18, 0)),
            todo("overdue", at(2026, 10, 18, 18, 0)),
            todo("tomorrow", at(2026, 10, 20, 9, 0)),
            todo("done", at(2026, 10, 19, 12, 0), done = true),
        )
        val viewModel = HomeViewModel(
            courseRepository = FakeCourseRepository(table),
            notesRepository = FakeNotesRepository(notes),
            timeProvider = FakeTimeProvider(now),
            zone = zone,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val state = viewModel.uiState.value

        assertEquals(now, state.now)
        assertEquals("上午好", state.greeting)
        assertEquals("10月19日 周一", state.dateLabel)
        assertEquals(2, state.weekNumber)
        assertTrue(state.hasTimetable)
        assertEquals(listOf("morning", "noon"), state.courses.map { it.course.id })
        assertEquals(CourseStatus.ONGOING, state.courses.first().status)
        // 今日待办：已逾期 + 今天，按 deadline 升序
        assertEquals(listOf("overdue", "today"), state.todos.map { it.item.id })
        // 全部未完成待办 = today + overdue + tomorrow（不含已完成）
        assertEquals(3, state.pendingTodoCount)
        assertEquals("morning", state.highlightedCourseId)
        assertFalse(state.loading)
        assertNull(state.error)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `refresh resubscribes repositories`() = runTest(dispatcher) {
        val courses = FakeCourseRepository(CourseTable(courses = emptyList()))
        val notes = FakeNotesRepository(emptyList())
        val viewModel = HomeViewModel(
            courseRepository = courses,
            notesRepository = notes,
            timeProvider = FakeTimeProvider(now),
            zone = zone,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(1, courses.observeCount)
        assertEquals(1, notes.observeCount)

        viewModel.refresh()
        runCurrent()

        assertEquals(2, courses.observeCount)
        assertEquals(2, notes.observeCount)
        assertTrue(viewModel.uiState.value.courses.isEmpty())
        assertNull(viewModel.uiState.value.error)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `repository failure becomes error message`() = runTest(dispatcher) {
        val notes = FakeNotesRepository(emptyList()).apply {
            failure = IllegalStateException("磁盘不可用")
        }
        val viewModel = HomeViewModel(
            courseRepository = FakeCourseRepository(null),
            notesRepository = notes,
            timeProvider = FakeTimeProvider(now),
            zone = zone,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val error = state.error.orEmpty()

        assertNotNull(state.error)
        assertTrue(error.contains("读取备忘失败"))
        assertTrue(error.contains("磁盘不可用"))
        assertFalse(state.loading)
        assertTrue(state.todos.isEmpty())

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `ticker refreshes now while pending todos exist`() = runTest(dispatcher) {
        val clock = FakeTimeProvider(now)
        val notes = FakeNotesRepository(listOf(todo("today", now + 5L * 60_000L)))
        val viewModel = HomeViewModel(
            courseRepository = FakeCourseRepository(null),
            notesRepository = notes,
            timeProvider = clock,
            zone = zone,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(now, viewModel.uiState.value.now)

        clock.current = now + 2_000L
        advanceTimeBy(HomeViewModel.TICK_INTERVAL_MILLIS + 1L)
        runCurrent()

        assertEquals(now + 2_000L, viewModel.uiState.value.now)
        assertEquals(1, viewModel.uiState.value.todos.size)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `no pending todos keeps single now snapshot`() = runTest(dispatcher) {
        val clock = FakeTimeProvider(now)
        val notes = listOf(
            todo("done", now - 1_000L, done = true),
            NoteItem(id = "memo", title = "备忘"),
        )
        val viewModel = HomeViewModel(
            courseRepository = FakeCourseRepository(null),
            notesRepository = FakeNotesRepository(notes),
            timeProvider = clock,
            zone = zone,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.pendingTodoCount)
        assertEquals(now, viewModel.uiState.value.now)

        clock.current = now + 10_000L
        advanceUntilIdle()

        // 没有未完成待办时不启用 ticker，now 保持首次快照
        assertEquals(now, viewModel.uiState.value.now)

        viewModel.viewModelScope.cancel()
    }

    private class FakeTimeProvider(var current: Long) : TimeProvider {
        override fun now(): Long = current
    }

    private class FakeCourseRepository(initial: CourseTable?) : CourseRepository {
        private val state = MutableStateFlow(initial)
        var observeCount: Int = 0
            private set

        override fun observeTable(): Flow<CourseTable?> {
            observeCount++
            return state
        }

        override suspend fun replace(table: CourseTable): AppResult<Unit> {
            state.value = table
            return AppResult.Success(Unit)
        }

        override suspend fun clear(): AppResult<Unit> {
            state.value = null
            return AppResult.Success(Unit)
        }
    }

    private class FakeNotesRepository(initial: List<NoteItem>) : NotesRepository {
        private val state = MutableStateFlow(initial)
        var observeCount: Int = 0
            private set
        var failure: Throwable? = null

        override fun observeItems(): Flow<List<NoteItem>> {
            observeCount++
            val error = failure
            return if (error != null) {
                flow { throw error }
            } else {
                state
            }
        }

        override suspend fun upsert(item: NoteItem): AppResult<Unit> {
            state.value = state.value.filterNot { it.id == item.id } + item
            return AppResult.Success(Unit)
        }

        override suspend fun delete(id: String): AppResult<Unit> {
            state.value = state.value.filterNot { it.id == id }
            return AppResult.Success(Unit)
        }

        override suspend fun get(id: String): NoteItem? = state.value.firstOrNull { it.id == id }
    }
}
