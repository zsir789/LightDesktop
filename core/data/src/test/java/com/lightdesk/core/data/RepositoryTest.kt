package com.lightdesk.core.data

import com.lightdesk.core.common.NoOpLogger
import com.lightdesk.core.common.isSuccess
import com.lightdesk.core.data.migration.LegacyNotesMigration
import com.lightdesk.core.data.repository.JsonNotesRepository
import com.lightdesk.core.data.repository.JsonSettingsRepository
import com.lightdesk.core.data.repository.JsonStatsRepository
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.LegacyMemo
import com.lightdesk.core.data.store.LegacyMemoStore
import com.lightdesk.core.data.store.LegacyTodo
import com.lightdesk.core.data.store.LegacyTodoStore
import com.lightdesk.core.data.store.NoteStore
import com.lightdesk.core.data.store.SettingsStore
import com.lightdesk.core.data.store.UsageStatsStore
import com.lightdesk.core.model.FeatureKey
import com.lightdesk.core.model.LegacyUsageStat
import com.lightdesk.core.model.NoteItem
import com.lightdesk.core.model.ThemeMode
import com.lightdesk.core.model.UsageStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    private lateinit var dir: File
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("lightdesk-repo-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun <T> store(
        fileName: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: () -> T,
    ): JsonFileStore<T> = JsonFileStore(File(dir, fileName), serializer, default, NoOpLogger, dispatcher)

    @Test
    fun `统一事项仓储写入后可跨实例读取且支持删除`() = runTest {
        fun repo() = JsonNotesRepository(store("notes.json", NoteStore.serializer()) { NoteStore() })

        val first = repo()
        val note = NoteItem(
            id = "n1",
            title = "周报",
            content = "周五前",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertTrue(first.upsert(note).isSuccess)
        assertTrue(first.upsert(note.copy(title = "周报（已更新）", updatedAt = 2L)).isSuccess)

        val second = repo()
        val loaded = second.observeItems().first()
        assertEquals(1, loaded.size)
        assertEquals("周报（已更新）", loaded.first().title)
        assertEquals("n1", second.get("n1")?.id)

        assertTrue(second.delete("n1").isSuccess)
        assertTrue(repo().observeItems().first().isEmpty())
    }

    @Test
    fun `v1_0_0 备忘录与待办会迁移为统一事项`() = runTest {
        val memoStore = store("memos.json", LegacyMemoStore.serializer()) { LegacyMemoStore() }
        memoStore.write(
            LegacyMemoStore(
                items = listOf(
                    LegacyMemo(id = "m1", title = "购物清单", content = "牛奶", createdAt = 10L, updatedAt = 10L),
                ),
            ),
        )
        val todoStore = store("todos.json", LegacyTodoStore.serializer()) { LegacyTodoStore() }
        todoStore.write(
            LegacyTodoStore(
                items = listOf(
                    LegacyTodo(
                        id = "t1",
                        title = "交作业",
                        note = "高数",
                        deadline = 900_000L,
                        done = true,
                        completedAt = 800_000L,
                        createdAt = 100L,
                    ),
                ),
            ),
        )

        val repo = JsonNotesRepository(
            store("notes.json", NoteStore.serializer()) { NoteStore() },
            legacyMigration = LegacyNotesMigration(memoStore, todoStore, NoOpLogger),
        )
        val items = repo.observeItems().first()
        assertEquals(2, items.size)

        val migratedTodo = items.first { it.id == "t1" }
        assertEquals("交作业", migratedTodo.title)
        assertEquals(900_000L, migratedTodo.deadline)
        assertTrue(migratedTodo.isTodo)
        assertTrue(migratedTodo.done)
        assertEquals(800_000L, migratedTodo.completedAt)

        val migratedMemo = items.first { it.id == "m1" }
        assertFalse(migratedMemo.isTodo)
        assertEquals("牛奶", migratedMemo.content)

        // 迁移结果应已落盘：新实例不再依赖旧文件。
        File(dir, "memos.json").delete()
        File(dir, "todos.json").delete()
        val reread = JsonNotesRepository(store("notes.json", NoteStore.serializer()) { NoteStore() })
        assertEquals(2, reread.observeItems().first().size)
    }

    @Test
    fun `使用统计累加访问次数并按天聚合`() = runTest {
        fun repo() = JsonStatsRepository(store("usage_stats.json", UsageStatsStore.serializer()) { UsageStatsStore() })

        val repository = repo()
        assertTrue(repository.recordVisit(FeatureKey.NOTES, 1000L).isSuccess)
        assertTrue(repository.recordVisit(FeatureKey.NOTES, 2000L).isSuccess)
        assertTrue(repository.recordVisit(FeatureKey.WEATHER, 3000L).isSuccess)
        assertTrue(repository.recordNoteCreated(4000L).isSuccess)
        assertTrue(repository.recordNoteCompleted(5000L).isSuccess)
        assertTrue(repository.recordWeatherCondition("晴", 6000L).isSuccess)
        assertTrue(repository.recordWeatherCondition("晴", 7000L).isSuccess)

        val stats = repo().observeStats().first()
        assertEquals(2L, stats.visitCount(FeatureKey.NOTES))
        assertEquals(1L, stats.visitCount(FeatureKey.WEATHER))
        assertEquals(2000L, stats.lastUsedAt(FeatureKey.NOTES))
        assertEquals(3L, stats.totalVisits())
        assertEquals(1L, stats.notesCreated)
        assertEquals(1L, stats.notesCompleted)
        assertEquals(100, stats.noteCompletionRate())
        assertEquals(2L, stats.weatherConditions["晴"])
        // 1000~7000ms 同属一天，按天聚合后应有访问/新建/完成/天气计数。
        val today = stats.daily.values.first()
        assertEquals(3, today.visits)
        assertEquals(1, today.notesCreated)
        assertEquals(1, today.notesCompleted)
        assertEquals(2, today.weatherFetches)

        assertTrue(repository.reset().isSuccess)
        assertEquals(0L, repository.observeStats().first().totalVisits())
    }

    @Test
    fun `v1_0_0 统计 items 迁移映射到新维度`() = runTest {
        val file = File(dir, "usage_stats.json")
        val legacy = UsageStats(
            legacyItems = listOf(
                LegacyUsageStat(feature = "MEMO", count = 4, lastUsedAt = 100L),
                LegacyUsageStat(feature = "TODO", count = 3, lastUsedAt = 200L),
                LegacyUsageStat(feature = "FLOATING_BALL", count = 9, lastUsedAt = 300L),
            ),
        )
        JsonFileStore(file, UsageStatsStore.serializer(), { UsageStatsStore() }, NoOpLogger, dispatcher)
            .write(UsageStatsStore(stats = legacy))

        val repository = JsonStatsRepository(
            JsonFileStore(file, UsageStatsStore.serializer(), { UsageStatsStore() }, NoOpLogger, dispatcher),
        )
        val stats = repository.observeStats().first()
        // MEMO + TODO 合并为 NOTES，FLOATING_BALL 丢弃
        assertEquals(7L, stats.visitCount(FeatureKey.NOTES))
        assertEquals(200L, stats.lastUsedAt(FeatureKey.NOTES))
        assertEquals(0L, stats.visitCount(FeatureKey.WEATHER))
        assertTrue(stats.legacyItems.isEmpty())
    }

    @Test
    fun `设置变更持久化到磁盘`() = runTest {
        fun repo() = JsonSettingsRepository(store("settings.json", SettingsStore.serializer()) { SettingsStore() })

        val repository = repo()
        assertTrue(
            repository.update {
                it.copy(
                    themeMode = ThemeMode.DARK,
                    countdownWarningMinutes = 60,
                    weatherCity = "上海",
                    usageStatsEnabled = false,
                )
            }.isSuccess,
        )

        val reloaded = repo().current()
        assertEquals(ThemeMode.DARK, reloaded.themeMode)
        assertEquals(60, reloaded.countdownWarningMinutes)
        assertEquals("上海", reloaded.weatherCity)
        assertFalse(reloaded.usageStatsEnabled)
    }
}
