package com.lightdesk.feature.notes

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 内存版 [NotesRepository]，用于 JVM 单测：
 * - 默认行为与真实仓库一致（observeItems 持续发射最新列表）；
 * - 可注入读取/写入/删除失败，验证 ViewModel 的错误兜底；
 * - 记录写入与删除历史，便于断言埋点之外的持久化副作用。
 */
class FakeNotesRepository(
    initial: List<NoteItem> = emptyList(),
) : NotesRepository {

    private val state = MutableStateFlow(initial)

    /** 当前内存中的条目（与真实仓库同样的排序无关，仅按写入顺序）。 */
    val items: List<NoteItem> get() = state.value

    /** 每次 upsert 的入参，按调用顺序记录。 */
    val written = mutableListOf<NoteItem>()

    /** 每次 delete 的 id，按调用顺序记录。 */
    val deletedIds = mutableListOf<String>()

    var failObserve: Boolean = false
    var failUpsert: Boolean = false
    var failDelete: Boolean = false

    override fun observeItems(): Flow<List<NoteItem>> =
        if (failObserve) {
            flow { throw IllegalStateException("读取失败") }
        } else {
            state
        }

    override suspend fun upsert(item: NoteItem): AppResult<Unit> {
        if (failUpsert) {
            return AppResult.Failure(AppError(ErrorCode.STORAGE, "写入失败"))
        }
        written += item
        state.value = if (state.value.any { it.id == item.id }) {
            state.value.map { if (it.id == item.id) item else it }
        } else {
            state.value + item
        }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        if (failDelete) {
            return AppResult.Failure(AppError(ErrorCode.STORAGE, "删除失败"))
        }
        deletedIds += id
        state.value = state.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun get(id: String): NoteItem? = state.value.firstOrNull { it.id == id }

    /** 模拟仓库外部数据变化（例如其它页面写入后 Flow 回流）。 */
    fun emit(items: List<NoteItem>) {
        state.value = items
    }
}
