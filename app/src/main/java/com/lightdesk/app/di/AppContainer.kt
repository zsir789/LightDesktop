package com.lightdesk.app.di

import android.content.Context
import com.lightdesk.app.BuildConfig
import com.lightdesk.core.common.DefaultDispatcherProvider
import com.lightdesk.core.common.DispatcherProvider
import com.lightdesk.core.common.SystemTimeProvider
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.StatsTracker
import com.lightdesk.core.data.migration.LegacyNotesMigration
import com.lightdesk.core.data.repository.CourseRepository
import com.lightdesk.core.data.repository.JsonCourseRepository
import com.lightdesk.core.data.repository.JsonNotesRepository
import com.lightdesk.core.data.repository.JsonSettingsRepository
import com.lightdesk.core.data.repository.JsonStatsRepository
import com.lightdesk.core.data.repository.JsonWeatherRepository
import com.lightdesk.core.data.repository.NotesRepository
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.data.repository.WeatherRepository
import com.lightdesk.core.data.store.CourseTableStore
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.JsonSchemaMigrator
import com.lightdesk.core.data.store.LegacyMemoStore
import com.lightdesk.core.data.store.LegacyTodoStore
import com.lightdesk.core.data.store.NoteStore
import com.lightdesk.core.data.store.SettingsStore
import com.lightdesk.core.data.store.StoreSchemaMigrator
import com.lightdesk.core.data.store.UsageStatsStore
import com.lightdesk.core.data.store.WeatherCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * 手动依赖注入容器（组装层）。
 *
 * 特点：
 * 1. 所有仓储/服务在此单点创建，功能模块只依赖接口；
 * 2. 新增功能时在此登记依赖，不影响既有模块；
 * 3. 后续如需接入 Hilt/Koin，只需替换本容器，功能模块零改动。
 */
class AppContainer(context: Context) {

    private val baseDir = File(context.filesDir, "lightdesk")

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val timeProvider: TimeProvider = SystemTimeProvider()
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    /** 应用版本号（构建期注入，避免硬编码）。 */
    val appVersion: String = BuildConfig.VERSION_NAME

    private fun <T> store(
        fileName: String,
        serializer: KSerializer<T>,
        defaultValue: () -> T,
        migrator: JsonSchemaMigrator? = StoreSchemaMigrator,
    ): JsonFileStore<T> = JsonFileStore(
        file = File(baseDir, fileName),
        serializer = serializer,
        defaultValue = defaultValue,
        ioDispatcher = dispatchers.io,
        migrator = migrator,
    )

    /** v1.0.0 旧文件只读，不做 schema 迁移（其结构由 LegacyNotesMigration 独立转换）。 */
    private fun <T> legacyStore(
        fileName: String,
        serializer: KSerializer<T>,
        defaultValue: () -> T,
    ): JsonFileStore<T> = store(fileName, serializer, defaultValue, migrator = null)

    val settingsRepository: SettingsRepository = JsonSettingsRepository(
        store("settings.json", SettingsStore.serializer(), defaultValue = { SettingsStore() }),
    )

    /**
     * 统一事项仓储：优先读取 notes.json；不存在时自动迁移 v1.0.0 的 memos.json/todos.json。
     * 旧文件只读不删，迁移失败自动降级为空数据，不影响启动。
     */
    val notesRepository: NotesRepository = JsonNotesRepository(
        store = store("notes.json", NoteStore.serializer(), defaultValue = { NoteStore() }),
        legacyMigration = LegacyNotesMigration(
            memoStore = legacyStore("memos.json", LegacyMemoStore.serializer()) { LegacyMemoStore() },
            todoStore = legacyStore("todos.json", LegacyTodoStore.serializer()) { LegacyTodoStore() },
        ),
    )

    val courseRepository: CourseRepository = JsonCourseRepository(
        store("courses.json", CourseTableStore.serializer(), defaultValue = { CourseTableStore() }),
    )

    val statsRepository: StatsRepository = JsonStatsRepository(
        store("usage_stats.json", UsageStatsStore.serializer(), defaultValue = { UsageStatsStore() }),
    )

    val weatherRepository: WeatherRepository = JsonWeatherRepository(
        store("weather_cache.json", WeatherCacheStore.serializer(), defaultValue = { WeatherCacheStore() }),
    )

    val statsTracker: StatsTracker = StatsTracker(
        repository = statsRepository,
        settingsRepository = settingsRepository,
        scope = appScope,
        timeProvider = timeProvider,
    )
}
