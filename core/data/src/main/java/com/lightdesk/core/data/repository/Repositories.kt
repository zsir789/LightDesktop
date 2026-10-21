package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.model.AppSettings
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.NoteItem
import com.lightdesk.core.model.UsageStats
import com.lightdesk.core.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * 仓储接口层：功能模块只依赖这些接口，不感知具体存储实现（当前为 JSON 文件）。
 *
 * 扩展方式：新增一种存储（Room / 服务端同步）时，实现同样的接口并在组装层替换即可，
 * 功能模块代码无需改动。
 */
interface NotesRepository {
    fun observeItems(): Flow<List<NoteItem>>
    suspend fun upsert(item: NoteItem): AppResult<Unit>
    suspend fun delete(id: String): AppResult<Unit>
    suspend fun get(id: String): NoteItem?
}

interface StatsRepository {
    fun observeStats(): Flow<UsageStats>

    /** 记录一次功能使用（首页/备忘/课表/天气）。 */
    suspend fun recordVisit(feature: FeatureKey, at: Long): AppResult<Unit>

    /** 记录一次“新建备忘/待办”。 */
    suspend fun recordNoteCreated(at: Long): AppResult<Unit>

    /** 记录一次“完成事项”。 */
    suspend fun recordNoteCompleted(at: Long): AppResult<Unit>

    /** 记录一次天气获取成功（condition 为天气现象文案，如 晴/多云/雨）。 */
    suspend fun recordWeatherCondition(condition: String, at: Long): AppResult<Unit>

    suspend fun reset(): AppResult<Unit>
}

interface CourseRepository {
    fun observeTable(): Flow<CourseTable?>
    suspend fun replace(table: CourseTable): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit>
}

interface WeatherRepository {
    fun observeCache(): Flow<WeatherSnapshot?>
    suspend fun cached(): WeatherSnapshot?
    suspend fun save(snapshot: WeatherSnapshot): AppResult<Unit>
    suspend fun clear(): AppResult<Unit>
}
