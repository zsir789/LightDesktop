package com.lightdesk.core.data.migration

import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.Logger
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.LegacyMemoStore
import com.lightdesk.core.data.store.LegacyTodoStore
import com.lightdesk.core.model.NoteItem

/**
 * v1.0.0 → v1.1.0 数据迁移：把 `memos.json` 与 `todos.json` 合并为统一的 `notes.json`。
 *
 * 迁移只读取旧文件、不删除旧文件（保留回滚可能）；合并结果为空时视为“无旧数据”，
 * 由仓储决定是否落盘。所有异常都会被捕获并降级为空列表，保证升级后应用可正常启动。
 */
class LegacyNotesMigration(
    private val memoStore: JsonFileStore<LegacyMemoStore>,
    private val todoStore: JsonFileStore<LegacyTodoStore>,
    private val logger: Logger = AppLogger,
) {
    suspend fun load(): List<NoteItem> = runCatching {
        val memos = readLegacy("memos") { memoStore.read().items.map { it.toNoteItem() } }
        val todos = readLegacy("todos") { todoStore.read().items.map { it.toNoteItem() } }
        (todos + memos).distinctBy { it.id }
    }.getOrElse { throwable ->
        logger.e(TAG, "旧版本数据迁移失败，按空数据启动", throwable)
        emptyList()
    }

    private suspend fun readLegacy(name: String, block: suspend () -> List<NoteItem>): List<NoteItem> =
        runCatching { block() }.getOrElse { throwable ->
            logger.w(TAG, "读取旧数据 $name 失败", throwable)
            emptyList()
        }

    private fun com.lightdesk.core.data.store.LegacyMemo.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title,
        content = content,
        deadline = null,
        done = done,
        completedAt = null,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun com.lightdesk.core.data.store.LegacyTodo.toNoteItem(): NoteItem = NoteItem(
        id = id,
        title = title,
        content = note,
        deadline = deadline,
        done = done,
        completedAt = completedAt,
        pinned = false,
        createdAt = createdAt,
        updatedAt = if (completedAt != null) completedAt else createdAt,
    )

    private companion object {
        const val TAG = "LegacyNotesMigration"
    }
}
