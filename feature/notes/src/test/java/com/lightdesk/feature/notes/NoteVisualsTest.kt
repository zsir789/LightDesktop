package com.lightdesk.feature.notes

import com.lightdesk.core.designsystem.theme.CourseColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NotesVisuals] 的纯逻辑单测：种子/色板下标稳定、按压缩放、完成态弱化，
 * 以及和 `CourseColors` 取色规则的一致性（不依赖 Compose 运行时）。
 */
class NoteVisualsTest {

    @Test
    fun `seed is the id hash code`() {
        listOf("", "a", "note-1", "备忘", "polygenelubricants").forEach { id ->
            assertEquals(id.hashCode(), NotesVisuals.seedFor(id))
        }
    }

    @Test
    fun `id hash seed maps to a stable course color`() {
        listOf("note-a", "note-b", "备忘").forEach { id ->
            val seed = NotesVisuals.seedFor(id)
            // seed 与 seed + 色板大小 命中同一格 pastel，说明取色只依赖 id 的稳定 hash。
            assertEquals(
                CourseColors.containerFor(seed, false),
                CourseColors.containerFor(seed + CourseColors.size, false),
            )
        }
    }

    @Test
    fun `color index is deterministic for same id`() {
        val first = NotesVisuals.colorIndexFor("note-stable-id")
        val second = NotesVisuals.colorIndexFor("note-stable-id")
        assertEquals(first, second)
    }

    @Test
    fun `color index stays inside palette range`() {
        val ids = listOf(
            "",
            "a",
            "note-1",
            "备忘-第一条",
            "todo-2026-10-21",
            "polygenelubricants", // hashCode() == Int.MIN_VALUE，验证负数边界
        )
        ids.forEach { id ->
            val index = NotesVisuals.colorIndexFor(id)
            assertTrue("index=$index for id=$id", index in 0 until CourseColors.size)
        }
    }

    @Test
    fun `color index can be used as course color seed directly`() {
        listOf("a", "note-1", "备忘", "polygenelubricants").forEach { id ->
            val index = NotesVisuals.colorIndexFor(id)
            // index 本身就是同一格色板的下标，因此再用 CourseColors 映射仍得自身。
            assertEquals(index, CourseColors.indexForSeed(index))
        }
    }

    @Test
    fun `color index handles non positive palette size`() {
        assertEquals(0, NotesVisuals.colorIndexFor("note-1", paletteSize = 0))
        assertEquals(0, NotesVisuals.colorIndexFor("note-1", paletteSize = -5))
    }

    @Test
    fun `color index differs for some different ids`() {
        // 不同 id 可以碰撞，但这两个常用 id 不应落在同一格，用于避免“整列同色”的回归。
        assertNotEquals(
            NotesVisuals.colorIndexFor("note-a"),
            NotesVisuals.colorIndexFor("note-b"),
        )
    }

    @Test
    fun `palette size matches course colors`() {
        assertEquals(CourseColors.size, NotesVisuals.paletteSize())
        assertEquals(12, NotesVisuals.paletteSize())
    }

    @Test
    fun `press scale shrinks only while pressed`() {
        assertEquals(0.98f, NotesVisuals.pressScale(true), 0.0001f)
        assertEquals(1f, NotesVisuals.pressScale(false), 0.0001f)
        assertEquals(NotesVisuals.PRESS_SCALE, NotesVisuals.pressScale(true), 0.0001f)
    }

    @Test
    fun `completed content is dimmed but still visible`() {
        assertEquals(1f, NotesVisuals.contentAlpha(false), 0.0001f)
        val doneAlpha = NotesVisuals.contentAlpha(true)
        assertTrue("doneAlpha=$doneAlpha", doneAlpha < 1f)
        assertTrue("doneAlpha=$doneAlpha", doneAlpha > 0.3f)
        assertEquals(NotesVisuals.COMPLETED_CONTENT_ALPHA, doneAlpha, 0.0001f)
    }

    @Test
    fun `course colors cycle by palette size`() {
        val seed = 5
        val size = CourseColors.size
        assertEquals(
            CourseColors.containerFor(seed, false),
            CourseColors.containerFor(seed + size, false),
        )
        assertEquals(
            CourseColors.accentFor(seed, true),
            CourseColors.accentFor(seed + size, true),
        )
    }

    @Test
    fun `course colors distinguish light dark and content roles`() {
        val seed = 3
        assertNotEquals(
            CourseColors.containerFor(seed, false),
            CourseColors.containerFor(seed, true),
        )
        assertNotEquals(
            CourseColors.containerFor(seed, false),
            CourseColors.onContainerFor(seed, false),
        )
        assertNotEquals(
            CourseColors.accentFor(seed, false),
            CourseColors.containerFor(seed, false),
        )
        assertNotEquals(
            CourseColors.onContainerFor(seed, false),
            CourseColors.onContainerFor(seed, true),
        )
    }
}
