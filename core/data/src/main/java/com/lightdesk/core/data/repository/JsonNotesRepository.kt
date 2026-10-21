package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.Logger
import com.lightdesk.core.common.map
import com.lightdesk.core.common.onFailure
import com.lightdesk.core.data.migration.LegacyNotesMigration
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.NoteStore
import com.lightdesk.core.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一事项仓储（备忘录 + 待办）。
 *
 * 首次启动时若 `notes.json` 不存在，会尝试从 v1.0.0 的 `memos.json`/`todos.json`
 * 迁移数据并立即落盘；迁移失败不会阻塞启动。
 */
class JsonNotesRepository(
    private val store: JsonFileStore<NoteStore>,
    private val legacyMigration: LegacyNotesMigration? = null,
    private val logger: Logger = AppLogger,
) : NotesRepository {

    private val state = MutableStateFlow<List<NoteItem>>(emptyList())
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false

    override fun observeItems(): Flow<List<NoteItem>> = state.onStart { ensureLoaded() }

    override suspend fun upsert(item: NoteItem): AppResult<Unit> = writeMutex.withLock {
        ensureLoaded()
        val items = state.value.toMutableList()
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item else items.add(item)
        persist(items.toList())
    }

    override suspend fun delete(id: String): AppResult<Unit> = writeMutex.withLock {
        ensureLoaded()
        persist(state.value.filterNot { it.id == id })
    }

    override suspend fun get(id: String): NoteItem? {
        ensureLoaded()
        return state.value.firstOrNull { it.id == id }
    }

    private suspend fun persist(items: List<NoteItem>): AppResult<Unit> =
        store.write(NoteStore(items = items)).map { state.value = items }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            val items = if (store.exists()) {
                store.read().items
            } else {
                val migrated = legacyMigration?.load().orEmpty()
                if (migrated.isNotEmpty()) {
                    store.write(NoteStore(items = migrated))
                        .onFailure { logger.w(TAG, "迁移旧数据落盘失败", it.cause) }
                    logger.d(TAG, "已从 v1.0.0 迁移 ${migrated.size} 条事项")
                }
                migrated
            }
            state.value = items
            loaded = true
        }
    }

    private companion object {
        const val TAG = "JsonNotesRepository"
    }
}
