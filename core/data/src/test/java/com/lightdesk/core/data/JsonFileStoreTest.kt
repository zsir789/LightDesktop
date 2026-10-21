package com.lightdesk.core.data

import com.lightdesk.core.common.NoOpLogger
import com.lightdesk.core.common.isSuccess
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.NoteStore
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
}
