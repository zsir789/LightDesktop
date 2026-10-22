package com.lightdesk.core.data

import com.lightdesk.core.common.NoOpLogger
import com.lightdesk.core.common.isSuccess
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.JsonSchemaMigrator
import com.lightdesk.core.data.store.NoteStore
import com.lightdesk.core.data.store.StoreSchemaMigrator
import com.lightdesk.core.model.NoteItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class JsonFileStoreTest {

    private lateinit var dir: File
    private lateinit var file: File
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("lightdesk-store-test").toFile()
        file = File(dir, "notes.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun newStore() = JsonFileStore(
        file = file,
        serializer = NoteStore.serializer(),
        defaultValue = { NoteStore() },
        ioDispatcher = dispatcher,
        logger = NoOpLogger,
    )

    @Test
    fun `文件不存在时返回默认值`() = runTest {
        val store = newStore()
        assertFalse(store.exists())
        assertEquals(emptyList<NoteItem>(), store.read().items)
    }

    @Test
    fun `写入后可以从磁盘重新读取`() = runTest {
        val store = newStore()
        val note = NoteItem(
            id = "1",
            title = "买牛奶",
            content = "两盒",
            deadline = 1_700_000_000_000L,
            createdAt = 1L,
            updatedAt = 2L,
        )
        assertTrue(store.write(NoteStore(items = listOf(note))).isSuccess)
        assertTrue(store.exists())

        // 新实例模拟进程重启：必须从磁盘读取而不是内存缓存
        val reloaded = newStore().read()
        assertEquals(2, reloaded.schemaVersion)
        assertEquals(1, reloaded.items.size)
        assertEquals("买牛奶", reloaded.items.first().title)
        assertEquals("两盒", reloaded.items.first().content)
        assertEquals(1_700_000_000_000L, reloaded.items.first().deadline)
    }

    @Test
    fun `损坏文件会备份并回退默认值`() = runTest {
        file.writeText("{ 这不是合法 JSON ", Charsets.UTF_8)
        val store = newStore()

        val result = store.read()

        assertEquals(emptyList<NoteItem>(), result.items)
        val backup = File(dir, "notes.json.corrupt")
        assertTrue("损坏文件应被备份", backup.exists())
        assertTrue("读取后应回到默认值", store.read().items.isEmpty())
    }

    @Test
    fun `写入使用临时文件原子替换`() = runTest {
        val store = newStore()
        assertTrue(store.write(NoteStore(items = listOf(NoteItem(id = "a", title = "标题")))).isSuccess)
        assertFalse("写入完成后临时文件应被清理", File(dir, "notes.json.tmp").exists())
        assertTrue(file.exists())
    }

    // ---- schemaVersion 迁移 ----

    private fun newStoreWithMigrator(migrator: JsonSchemaMigrator?) = JsonFileStore(
        file = file,
        serializer = NoteStore.serializer(),
        defaultValue = { NoteStore() },
        ioDispatcher = dispatcher,
        logger = NoOpLogger,
        migrator = migrator,
    )

    private val v1File = """
        {"schemaVersion":1,"items":[{"id":"a","title":"旧标题"}]}
    """.trimIndent()

    @Test
    fun `低版本文件先迁移再解码`() = runTest {
        file.writeText(v1File, Charsets.UTF_8)
        var migratedFrom: Int? = null
        val migrator = object : JsonSchemaMigrator {
            override val currentVersion: Int = StoreSchemaMigrator.currentVersion
            override fun migrate(raw: String, from: Int): String {
                migratedFrom = from
                return raw.replace("旧标题", "迁移后标题")
            }
        }

        val reloaded = newStoreWithMigrator(migrator).read()

        assertEquals(1, migratedFrom)
        assertEquals("迁移后标题", reloaded.items.single().title)
    }

    @Test
    fun `迁移失败时保留原文件并回退默认值`() = runTest {
        file.writeText(v1File, Charsets.UTF_8)
        val failing = object : JsonSchemaMigrator {
            override val currentVersion: Int = 2
            override fun migrate(raw: String, from: Int): String = error("迁移实现有 bug")
        }

        val reloaded = newStoreWithMigrator(failing).read()

        assertTrue(reloaded.items.isEmpty())
        assertTrue("迁移失败不能删除原文件", file.exists())
        assertFalse("迁移失败不应把文件当损坏备份", File(dir, "notes.json.corrupt").exists())
        assertEquals(v1File, file.readText(Charsets.UTF_8))
    }

    @Test
    fun `版本高于当前时保留原文件并写 incompatible 备份`() = runTest {
        val future = """{"schemaVersion":99,"items":"不是列表"}"""
        file.writeText(future, Charsets.UTF_8)

        val reloaded = newStoreWithMigrator(StoreSchemaMigrator).read()

        assertTrue(reloaded.items.isEmpty())
        assertTrue("高版本原文件应保留", file.exists())
        assertTrue(
            "高版本不兼容时应留副本",
            File(dir, "notes.json.incompatible").exists(),
        )
    }

    @Test
    fun `版本高于当前但结构兼容时正常读取`() = runTest {
        file.writeText("""{"schemaVersion":99,"items":[{"id":"a","title":"未来数据"}]}""")

        val reloaded = newStoreWithMigrator(StoreSchemaMigrator).read()

        assertEquals("未来数据", reloaded.items.single().title)
        assertFalse(File(dir, "notes.json.incompatible").exists())
    }

    @Test
    fun `无迁移器时低版本文件直接按当前结构读取`() = runTest {
        file.writeText(v1File, Charsets.UTF_8)

        val reloaded = newStoreWithMigrator(migrator = null).read()

        assertEquals("旧标题", reloaded.items.single().title)
    }

    @Test
    fun `默认迁移器可处理当前版本文件`() = runTest {
        file.writeText("""{"schemaVersion":2,"items":[{"id":"a","title":"当前版本"}]}""")

        val reloaded = newStoreWithMigrator(StoreSchemaMigrator).read()

        assertEquals("当前版本", reloaded.items.single().title)
    }
}
