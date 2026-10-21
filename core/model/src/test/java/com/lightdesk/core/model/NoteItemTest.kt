package com.lightdesk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteItemTest {

    @Test
    fun `有截止时间即为待办`() {
        assertTrue(NoteItem(id = "1", title = "交作业", deadline = 1_000L).isTodo)
        assertFalse(NoteItem(id = "2", title = "随手记").isTodo)
    }

    @Test
    fun `isOverdue 仅在未完成且超过截止时间时为真`() {
        val now = 10_000L
        val item = NoteItem(id = "1", title = "交作业", deadline = 5_000L)
        assertTrue(item.isOverdue(now))
        assertFalse(item.isOverdue(5_000L))
        assertFalse(item.copy(done = true).isOverdue(now))
        assertFalse(NoteItem(id = "2", title = "备忘").isOverdue(now))
    }

    @Test
    fun `preview 返回正文首个非空行`() {
        assertEquals("", NoteItem(id = "1", title = "标题").preview)
        assertEquals(
            "第一行",
            NoteItem(id = "1", title = "标题", content = "\n\n第一行\n第二行").preview,
        )
    }

    @Test
    fun `完成状态与完成时间解耦存储`() {
        val item = NoteItem(id = "1", title = "待办", deadline = 1L, done = true, completedAt = 2L)
        assertTrue(item.done)
        assertEquals(2L, item.completedAt)
    }
}
