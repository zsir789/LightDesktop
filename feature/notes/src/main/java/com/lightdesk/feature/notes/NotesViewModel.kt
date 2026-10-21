package com.lightdesk.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.model.NoteItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 备忘页的单一状态源。
 *
 * [items] 是已经按 [filter] 过滤并 [NotesLogic.sort] 排序后的展示列表；
 * [counts] 始终基于全量数据统计，保证 chip 计数不随筛选变化。
 */
data class NotesUiState(
    val items: List<NoteItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val now: Long = 0L,
    val filter: NotesFilter = NotesFilter.ALL,
    val counts: Map<NotesFilter, Int> = emptyMap(),
    val editor: NoteEditorState? = null,
    val pendingDelete: NoteItem? = null,
) {
    /** 页面活跃时是否值得每秒刷新倒计时：只有“未完成且带截止时间”的待办才需要。 */
    val hasActiveDeadline: Boolean
        get() = items.any { !it.done && it.deadline != null }
}

/**
 * 新增/编辑弹窗（底部卡片）的表单状态。
 */
data class NoteEditorState(
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val todoEnabled: Boolean = false,
    val deadline: Long? = null,
    val pinned: Boolean = false,
    val titleError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isEditing: Boolean get() = id != null
}

/**
 * 备忘页 ViewModel：承担仓库订阅、筛选/排序、写操作与错误兜底。
 *
 * 约定：
 * - UI 只读 [uiState] 并调用本类公开方法，不直接接触 [NotesRepository]；
 * - 所有挂起调用都经 [safeCall] 转成 [AppResult]，仓库异常不会向上抛导致崩溃；
 * - 时间统一来自 [TimeProvider]，“现在”由 UI 每秒调用 [refreshNow] 驱动。
 */
class NotesViewModel(
    private val notesRepository: NotesRepository,
    private val statsTracker: StatsTracker,
    private val timeProvider: TimeProvider,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState(now = timeProvider.now()))
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    /** 全量数据缓存：筛选/排序计数都基于它，保证切换筛选不丢条目。 */
    private var allItems: List<NoteItem> = emptyList()

    private var loadJob: Job? = null

    init {
        loadItems()
    }

    /**
     * 订阅仓库数据；失败时把错误写入 [NotesUiState.error]，由 UI 展示 ErrorBanner 并提供重试。
     * 重试时先取消上一次订阅，避免重复收集。
     */
    fun loadItems() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            notesRepository.observeItems()
                .onEach { items ->
                    allItems = items
                    publish(loading = false, error = null)
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = "加载失败：${throwable.message ?: "未知错误"}",
                        )
                    }
                }
                .collect()
        }
    }

    /** 切换筛选维度，展示列表随之重算（不重新请求仓库）。 */
    fun setFilter(filter: NotesFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        publish()
    }

    /** 刷新“现在”，供倒计时文案使用。 */
    fun refreshNow() {
        val now = timeProvider.now()
        if (now == _uiState.value.now) return
        _uiState.update { it.copy(now = now) }
    }

    /** 勾选/取消勾选完成；所有条目（含备忘）都可以标记完成。 */
    fun toggleDone(item: NoteItem) {
        val current = allItems.firstOrNull { it.id == item.id } ?: item
        val now = timeProvider.now()
        val target = if (current.done) {
            current.copy(done = false, completedAt = null, updatedAt = now)
        } else {
            current.copy(done = true, completedAt = now, updatedAt = now)
        }
        viewModelScope.launch {
            when (val result = safeCall { notesRepository.upsert(target) }) {
                is AppResult.Success -> {
                    replaceLocal(target)
                    publish()
                    if (target.done) statsTracker.trackNoteCompleted()
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(error = "更新失败：${result.error.message}") }
                }
            }
        }
    }

    /** 置顶/取消置顶。 */
    fun togglePinned(item: NoteItem) {
        val current = allItems.firstOrNull { it.id == item.id } ?: item
        val now = timeProvider.now()
        val target = current.copy(pinned = !current.pinned, updatedAt = now)
        viewModelScope.launch {
            when (val result = safeCall { notesRepository.upsert(target) }) {
                is AppResult.Success -> {
                    replaceLocal(target)
                    publish()
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(error = "操作失败：${result.error.message}") }
                }
            }
        }
    }

    fun requestDelete(item: NoteItem) {
        _uiState.update { it.copy(pendingDelete = item) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /** 确认删除（UI 已通过 ConfirmDialog 二次确认）。 */
    fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            when (val result = safeCall { notesRepository.delete(target.id) }) {
                is AppResult.Success -> {
                    allItems = allItems.filterNot { it.id == target.id }
                    publish()
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(error = "删除失败：${result.error.message}") }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---- 编辑弹窗 ----

    fun openCreate() {
        _uiState.update { it.copy(editor = NoteEditorState()) }
    }

    fun openEdit(item: NoteItem) {
        _uiState.update {
            it.copy(
                editor = NoteEditorState(
                    id = item.id,
                    title = item.title,
                    content = item.content,
                    todoEnabled = item.isTodo,
                    deadline = item.deadline,
                    pinned = item.pinned,
                ),
            )
        }
    }

    fun closeEditor() {
        if (_uiState.value.editor?.saving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    fun onEditorTitleChange(title: String) {
        updateEditor { it.copy(title = title, titleError = null) }
    }

    fun onEditorContentChange(content: String) {
        updateEditor { it.copy(content = content) }
    }

    /**
     * 打开“设为待办”时补一个默认截止时间（今天 23:59）；
     * 关闭时只把类型切回备忘，草稿里的截止时间保留，重新打开可直接沿用。
     * 真正落库时以 [saveEditor] 为准：非待办一律写 deadline = null。
     */
    fun onEditorTodoEnabledChange(enabled: Boolean) {
        updateEditor { editor ->
            if (enabled) {
                editor.copy(
                    todoEnabled = true,
                    deadline = editor.deadline ?: NotesFormat.defaultDeadline(timeProvider.now()),
                )
            } else {
                editor.copy(todoEnabled = false)
            }
        }
    }

    fun onEditorDeadlineChange(deadline: Long) {
        updateEditor { it.copy(deadline = deadline) }
    }

    fun onEditorPinnedChange(pinned: Boolean) {
        updateEditor { it.copy(pinned = pinned) }
    }

    fun dismissEditorError() {
        updateEditor { it.copy(error = null) }
    }

    /**
     * 保存表单：校验标题 -> upsert -> 关闭弹窗。
     * 新建时记录 [StatsTracker.trackNoteCreated]；编辑失败时保留弹窗内容并展示错误。
     */
    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.saving) return

        val titleError = NotesFormat.validateTitle(editor.title)
        if (titleError != null) {
            updateEditor { it.copy(titleError = titleError) }
            return
        }

        val now = timeProvider.now()
        val existing = editor.id?.let { id -> allItems.firstOrNull { it.id == id } }
        val item = NoteItem(
            id = existing?.id ?: idFactory(),
            title = editor.title.trim(),
            content = editor.content.trim(),
            deadline = if (editor.todoEnabled) {
                editor.deadline ?: NotesFormat.defaultDeadline(now)
            } else {
                null
            },
            done = existing?.done ?: false,
            completedAt = existing?.completedAt,
            pinned = editor.pinned,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        updateEditor { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = safeCall { notesRepository.upsert(item) }) {
                is AppResult.Success -> {
                    replaceLocal(item)
                    publish()
                    _uiState.update { it.copy(editor = null) }
                    if (existing == null) statsTracker.trackNoteCreated()
                }
                is AppResult.Failure -> {
                    updateEditor { it.copy(saving = false, error = "保存失败：${result.error.message}") }
                }
            }
        }
    }

    // ---- 内部实现 ----

    private fun replaceLocal(item: NoteItem) {
        allItems = if (allItems.any { it.id == item.id }) {
            allItems.map { if (it.id == item.id) item else it }
        } else {
            allItems + item
        }
    }

    /** 按当前筛选/排序重算展示列表与计数。 */
    private fun publish(
        loading: Boolean = _uiState.value.loading,
        error: String? = _uiState.value.error,
        now: Long = timeProvider.now(),
    ) {
        _uiState.update { state ->
            state.copy(
                items = NotesLogic.visible(allItems, state.filter),
                counts = NotesLogic.counts(allItems),
                loading = loading,
                error = error,
                now = now,
            )
        }
    }

    private fun updateEditor(transform: (NoteEditorState) -> NoteEditorState) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }

    /**
     * 包裹仓库挂起调用：任何非取消除异常都转换成 [AppResult.Failure]，
     * 保证 UI 层拿到的一定是可展示的错误而非崩溃。
     */
    private suspend fun safeCall(block: suspend () -> AppResult<Unit>): AppResult<Unit> =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppError(
                    code = ErrorCode.UNKNOWN,
                    message = throwable.message ?: "未知错误",
                    cause = throwable,
                ),
            )
        }
}
