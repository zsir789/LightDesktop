package com.lightdesk.core.data.store

import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.NoteItem
import com.lightdesk.core.model.UsageStats
import com.lightdesk.core.model.WeatherSnapshot
import kotlinx.serialization.Serializable

/**
 * 持久化包装类型。
 *
 * 包一层对象而不是直接存数组/可空对象，便于未来平滑加入 schemaVersion、
 * 迁移逻辑等字段，而不破坏已安装用户的本地数据。
 */

@Serializable
data class NoteStore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val items: List<NoteItem> = emptyList(),
)

@Serializable
data class CourseTableStore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val table: CourseTable? = null,
)

@Serializable
data class UsageStatsStore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val stats: UsageStats = UsageStats(),
)

@Serializable
data class SettingsStore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: AppSettings = AppSettings(),
)

@Serializable
data class WeatherCacheStore(
    val schemaVersion: Int = SCHEMA_VERSION,
    val snapshot: WeatherSnapshot? = null,
)

// ---------------------------------------------------------------------------
// v1.0.0 旧格式（只读，用于一次性迁移）
// ---------------------------------------------------------------------------

/** v1.0.0 备忘录条目。 */
@Serializable
data class LegacyMemo(
    val id: String,
    val title: String,
    val content: String = "",
    val done: Boolean = false,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class LegacyMemoStore(
    val schemaVersion: Int = LEGACY_SCHEMA_VERSION,
    val items: List<LegacyMemo> = emptyList(),
)

/** v1.0.0 待办条目。 */
@Serializable
data class LegacyTodo(
    val id: String,
    val title: String,
    val note: String = "",
    val deadline: Long? = null,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val remindBeforeMinutes: Int? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class LegacyTodoStore(
    val schemaVersion: Int = LEGACY_SCHEMA_VERSION,
    val items: List<LegacyTodo> = emptyList(),
)

const val SCHEMA_VERSION: Int = 2
const val LEGACY_SCHEMA_VERSION: Int = 1
