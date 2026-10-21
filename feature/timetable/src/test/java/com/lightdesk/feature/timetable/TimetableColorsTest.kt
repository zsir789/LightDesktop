package com.lightdesk.feature.timetable

import androidx.compose.ui.graphics.Color
import com.lightdesk.core.designsystem.theme.CourseColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程色块映射单测：由 `Course.colorSeed` 决定、跨会话稳定、不同种子颜色分散。
 *
 * 色板本身在 `core:designsystem` 的 [CourseColors]（只读使用，不修改）。
 */
class TimetableColorsTest {

    @Test
    fun `palette exposes twelve colors`() {
        assertEquals(12, CourseColors.size)
    }

    @Test
    fun `same seed always maps to same color`() {
        val seed = "高等数学".hashCode()
        assertEquals(CourseColors.containerFor(seed, dark = false), CourseColors.containerFor(seed, dark = false))
        assertEquals(CourseColors.onContainerFor(seed, dark = false), CourseColors.onContainerFor(seed, dark = false))
        assertEquals(CourseColors.accentFor(seed, dark = false), CourseColors.accentFor(seed, dark = false))
        assertEquals(CourseColors.containerFor(seed, dark = true), CourseColors.containerFor(seed, dark = true))
    }

    @Test
    fun `palette index is safe for negative and min value seeds`() {
        listOf(Int.MIN_VALUE, -1, -13, -12, 0, 11, 12, 25).forEach { seed ->
            val index = CourseColors.indexForSeed(seed)
            assertTrue("index $index for seed $seed", index in 0 until CourseColors.size)
        }
    }

    @Test
    fun `consecutive palette slots produce distinct light colors`() {
        val colors = (0 until CourseColors.size).map { seed -> CourseColors.containerFor(seed, dark = false) }
        assertEquals(CourseColors.size, colors.distinct().size)
    }

    @Test
    fun `dark theme container differs from light theme`() {
        val seed = "数据结构".hashCode()
        assertNotEquals(CourseColors.containerFor(seed, dark = false), CourseColors.containerFor(seed, dark = true))
    }

    @Test
    fun `text color contrasts with container color`() {
        (0 until CourseColors.size).forEach { seed ->
            assertNotEquals(
                "container/onContainer should differ for seed $seed",
                CourseColors.containerFor(seed, dark = false),
                CourseColors.onContainerFor(seed, dark = false),
            )
            assertNotEquals(
                CourseColors.containerFor(seed, dark = true),
                CourseColors.onContainerFor(seed, dark = true),
            )
        }
    }

    @Test
    fun `accent differs from container`() {
        (0 until CourseColors.size).forEach { seed ->
            assertNotEquals(
                CourseColors.containerFor(seed, dark = false),
                CourseColors.accentFor(seed, dark = false),
            )
        }
    }

    @Test
    fun `adjacent seeds map to different palette slots`() {
        // 种子 +1 时色板下标整体平移，避免相邻课程同色（色板长度为 12）
        (0 until CourseColors.size - 1).forEach { seed ->
            assertNotEquals(CourseColors.indexForSeed(seed), CourseColors.indexForSeed(seed + 1))
        }
    }

    @Test
    fun `color values are opaque`() {
        // 色块底色必须不透明，叠在网格线上不会透出底色
        (0 until CourseColors.size).forEach { seed ->
            val light = CourseColors.containerFor(seed, dark = false)
            val dark = CourseColors.containerFor(seed, dark = true)
            assertEquals(1f, light.alpha, 0.0001f)
            assertEquals(1f, dark.alpha, 0.0001f)
        }
    }

    @Test
    fun `light and dark text colors are valid`() {
        val seed = 42
        assertTrue(CourseColors.onContainerFor(seed, dark = false) != Color.Unspecified)
        assertTrue(CourseColors.onContainerFor(seed, dark = true) != Color.Unspecified)
    }
}
