package com.lightdesk.feature.notes

import com.lightdesk.core.common.NoOpLogger
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.model.NoteItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [NotesViewModel] 的行为单测。
 *
 * 统计埋点用“真 StatsTracker + FakeStatsRepository”验证：
 * StatsTracker 是 final class 不可 fake，但其写入是 fire-and-forget，
 * 因此给它一个 UnconfinedTestDispatcher 作用域即可同步断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeNotesRepository
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var statsTracker: StatsTracker

    private val now: Long get() = timeProvider.current

    @Before
    fun setUp() {
        repository = FakeNotesRepository()
        statsRepository = FakeStatsRepository()
        timeProvider = FakeTimeProvider()
        statsTracker = StatsTracker(
            repository = statsRepository,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            timeProvider = timeProvider,
            logger = NoOpLogger,
        )
    }

    private fun createViewModel(): NotesViewModel = NotesViewModel(
        notesRepository = repository,
        statsTracker = statsTracker,
        timeProvider = timeProvider,
        idFactory = { GENERATED_ID },
    )

    private fun note(
        id: String,
        deadline: Long? = null,
        done: Boolean = false,
        pinned: Boolean = false,
        createdAt: Long = 0L,
    ) = NoteItem(
        id = id,
        title = "title-$id",
        deadline = deadline,
        done = done,
        pinned = pinned,
        createdAt = createdAt,
    )

    // ---- 加载与筛选 ----

    @Test
    fun `loads items and keeps display order sorted`() = runTest {
        repository.emit(
            listOf(
                note("memo", createdAt = 1L),
                note("todoLate", deadline = 5_000L, createdAt = 2L),
                note("todoEarly", deadline = 1_000L, createdAt = 3L),
                note("done", deadline = 500L, done = true, createdAt = 4L),
            ),
        )

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(listOf("todoEarly", "todoLate", "memo", "done"), state.items.map { it.id })
        assertEquals(4, state.counts[NotesFilter.ALL])
    }

    @Test
    fun `filter changes visible list but not counts`() = runTest {
        repository.emit(
            listOf(
                note("memo"),
                note("todo", deadline = 1_000L),
                note("done", deadline = 2_000L, done = true),
            ),
        )
        val viewModel = createViewModel()

        viewModel.setFilter(NotesFilter.MEMO)
        assertEquals(listOf("memo"), viewModel.uiState.value.items.map { it.id })

        // “done” 带截止时间，因此同时属于待办与已完成两个维度
        viewModel.setFilter(NotesFilter.TODO)
        assertEquals(listOf("todo", "done"), viewModel.uiState.value.items.map { it.id })

        viewModel.setFilter(NotesFilter.COMPLETED)
        assertEquals(listOf("done"), viewModel.uiState.value.items.map { it.id })
        assertEquals(3, viewModel.uiState.value.counts[NotesFilter.ALL])

        viewModel.setFilter(NotesFilter.ALL)
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    // ---- 完成 / 取消完成 ----

    @Test
    fun `toggle done sets completedAt and tracks completion`() = runTest {
        repository.emit(listOf(note("todo", deadline = now + 60_000L)))
        val viewModel = createViewModel()

        viewModel.toggleDone(repository.items.first())

        val stored = repository.items.single()
        assertTrue(stored.done)
        assertEquals(now, stored.completedAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(1, statsRepository.noteCompletedCount)
        assertEquals(0, statsRepository.noteCreatedCount)
        assertTrue(viewModel.uiState.value.items.single().done)
    }

    @Test
    fun `toggle done twice clears completedAt without extra tracking`() = runTest {
        repository.emit(
            listOf(
                note("todo", deadline = now + 60_000L, done = true, createdAt = 1L),
            ),
        )
        val viewModel = createViewModel()

        viewModel.toggleDone(repository.items.first())

        val stored = repository.items.single()
        assertFalse(stored.done)
        assertNull(stored.completedAt)
        assertEquals(0, statsRepository.noteCompletedCount)
    }

    @Test
    fun `toggle pinned moves item to the top`() = runTest {
        repository.emit(listOf(note("a", createdAt = 2L), note("b", createdAt = 1L)))
        val viewModel = createViewModel()

        viewModel.togglePinned(repository.items.first { it.id == "b" })

        assertTrue(repository.items.first { it.id == "b" }.pinned)
        assertEquals("b", viewModel.uiState.value.items.first().id)
    }

    // ---- 新建 ----

    @Test
    fun `creating a memo trims fields and tracks creation`() = runTest {
        val viewModel = createViewModel()

        viewModel.openCreate()
        viewModel.onEditorTitleChange("  买牛奶  ")
        viewModel.onEditorContentChange("  两盒  ")
        viewModel.onEditorPinnedChange(true)
        viewModel.saveEditor()

        val created = repository.items.single()
        assertEquals(GENERATED_ID, created.id)
        assertEquals("买牛奶", created.title)
        assertEquals("两盒", created.content)
        assertTrue(created.pinned)
        assertNull(created.deadline)
        assertEquals(now, created.createdAt)
        assertEquals(1, statsRepository.noteCreatedCount)
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `creating a todo uses default deadline`() = runTest {
        val viewModel = createViewModel()
        val expectedDeadline = NotesFormat.defaultDeadline(now)

        viewModel.openCreate()
        viewModel.onEditorTitleChange("交作业")
        viewModel.onEditorTodoEnabledChange(true)

        assertEquals(expectedDeadline, viewModel.uiState.value.editor?.deadline)

        viewModel.saveEditor()

        val created = repository.items.single()
        assertTrue(created.isTodo)
        assertEquals(expectedDeadline, created.deadline)
    }

    @Test
    fun `blank title is rejected and nothing is written`() = runTest {
        val viewModel = createViewModel()

        viewModel.openCreate()
        viewModel.onEditorTitleChange("   ")
        viewModel.saveEditor()

        assertTrue(repository.items.isEmpty())
        assertTrue(repository.written.isEmpty())
        assertEquals("请输入标题", viewModel.uiState.value.editor?.titleError)
        assertNotNull(viewModel.uiState.value.editor)
        assertEquals(0, statsRepository.noteCreatedCount)
    }

    // ---- 编辑 ----

    @Test
    fun `editing keeps identity and does not count as creation`() = runTest {
        repository.emit(
            listOf(
                NoteItem(
                    id = "existing",
                    title = "旧标题",
                    content = "",
                    deadline = null,
                    pinned = false,
                    createdAt = 1_600_000_000_000L,
                    updatedAt = 1_600_000_000_000L,
                ),
            ),
        )
        val viewModel = createViewModel()

        viewModel.openEdit(repository.items.first())
        assertEquals("旧标题", viewModel.uiState.value.editor?.title)
        viewModel.onEditorTitleChange("新标题")
        viewModel.saveEditor()

        val stored = repository.items.single()
        assertEquals("existing", stored.id)
        assertEquals("新标题", stored.title)
        assertEquals(1_600_000_000_000L, stored.createdAt)
        assertEquals(now, stored.updatedAt)
        assertEquals(0, statsRepository.noteCreatedCount)
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `turning todo switch off saves memo without deadline but keeps draft`() = runTest {
        val originalDeadline = NotesFormat.defaultDeadline(now)
        repository.emit(
            listOf(
                NoteItem(id = "todo", title = "待办", deadline = originalDeadline, createdAt = 1L),
            ),
        )
        val viewModel = createViewModel()

        viewModel.openEdit(repository.items.first())
        viewModel.onEditorTodoEnabledChange(false)
        // 草稿里保留原截止时间，重新打开开关可直接沿用
        assertEquals(originalDeadline, viewModel.uiState.value.editor?.deadline)

        viewModel.saveEditor()

        val stored = repository.items.single()
        assertFalse(stored.isTodo)
        assertNull(stored.deadline)
    }

    // ---- 删除 ----

    @Test
    fun `delete needs confirmation and cancelling keeps item`() = runTest {
        repository.emit(listOf(note("a")))
        val viewModel = createViewModel()

        viewModel.requestDelete(repository.items.first())
        assertNotNull(viewModel.uiState.value.pendingDelete)
        assertTrue(repository.deletedIds.isEmpty())

        viewModel.cancelDelete()
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(1, repository.items.size)

        viewModel.requestDelete(repository.items.first())
        viewModel.confirmDelete()

        assertTrue(repository.items.isEmpty())
        assertEquals(listOf("a"), repository.deletedIds)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `delete failure shows error and keeps item`() = runTest {
        repository.emit(listOf(note("a")))
        repository.failDelete = true
        val viewModel = createViewModel()

        viewModel.requestDelete(repository.items.first())
        viewModel.confirmDelete()

        assertEquals(1, repository.items.size)
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.contains("删除失败"))
    }

    // ---- 错误兜底 ----

    @Test
    fun `repository read failure surfaces error and retry recovers`() = runTest {
        repository.emit(listOf(note("a")))
        repository.failObserve = true
        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.loading)
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.startsWith("加载失败"))

        repository.failObserve = false
        viewModel.loadItems()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("a"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun `save failure keeps editor open with error`() = runTest {
        repository.failUpsert = true
        val viewModel = createViewModel()

        viewModel.openCreate()
        viewModel.onEditorTitleChange("标题")
        viewModel.saveEditor()

        val editor = viewModel.uiState.value.editor
        assertNotNull(editor)
        assertFalse(editor!!.saving)
        assertNotNull(editor.error)
        assertTrue(editor.error!!.contains("保存失败"))
        assertTrue(repository.items.isEmpty())
        assertEquals(0, statsRepository.noteCreatedCount)
    }

    // ---- 时钟 ----

    @Test
    fun `refreshNow updates clock and active deadline flag follows completion`() = runTest {
        repository.emit(listOf(note("todo", deadline = now + 3_600_000L, createdAt = 1L)))
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.hasActiveDeadline)

        timeProvider.advanceBy(5_000L)
        viewModel.refreshNow()
        assertEquals(now, viewModel.uiState.value.now)

        viewModel.toggleDone(repository.items.first())
        assertFalse(viewModel.uiState.value.hasActiveDeadline)
    }

    private companion object {
        const val GENERATED_ID = "generated-id"
    }
}
