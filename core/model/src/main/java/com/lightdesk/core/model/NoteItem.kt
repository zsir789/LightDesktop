package com.lightdesk.core.model

import kotlinx.serialization.Serializable

/**
 * 统一事项模型：备忘录与待办合并后的唯一数据载体。
 *
 * - 没有 [deadline] 的事项即“备忘”，有截止时间即“待办”；
 * - done/completedAt 记录完成状态与完成时间；
 * - pinned 用于置顶；
 * - 新增字段时提供默认值，保证旧版本 JSON 可平滑解析（迁移见 core:data）。
 */
@Serializable
data class NoteItem(
    val id: String,
    val title: String,
    val content: String = "",
    val deadline: Long? = null,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 是否属于“待办”（有截止时间）。 */
    val isTodo: Boolean get() = deadline != null

    fun isOverdue(now: Long): Boolean = !done && deadline != null && deadline < now

    /** 列表预览：正文第一行非空内容。 */
    val preview: String
        get() = content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
}
