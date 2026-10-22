package com.lightdesk.feature.stats

import androidx.compose.ui.graphics.luminance
import com.lightdesk.core.designsystem.theme.HeatmapColors
import com.lightdesk.core.designsystem.theme.LightDeskPalettes
import com.lightdesk.core.model.ThemePalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热力图色阶单测：保证相邻档位“亮度差足够明显”，且深浅色下方向正确。
 *
 * 这正是 v1.2.1 修复的问题：旧实现用低透明度叠加，相邻档位差异过小，
 * 小格子里几乎分不清层级。
 */
class HeatmapColorsTest {

    /** 相邻两档之间需要的最小亮度差，低于该值即视为“区分不明显”。 */
    private val minLuminanceGap = 0.02f

    @Test
    fun `light theme has five clearly separated levels`() {
        ThemePalette.entries.forEach { paletteKey ->
            assertLevels(paletteKey = paletteKey, dark = false, ascending = false)
        }
    }

    @Test
    fun `dark theme has five clearly separated levels`() {
        ThemePalette.entries.forEach { paletteKey ->
            assertLevels(paletteKey = paletteKey, dark = true, ascending = true)
        }
    }

    @Test
    fun `out of range level is clamped`() {
        val palette = LightDeskPalettes.lightOf(ThemePalette.OCEAN)
        val lowest = HeatmapColors.levelColor(0, palette, dark = false)
        val highest = HeatmapColors.levelColor(HeatmapColors.LEVEL_COUNT - 1, palette, dark = false)
        assertTrue(HeatmapColors.levelColor(-3, palette, dark = false) == lowest)
        assertTrue(HeatmapColors.levelColor(99, palette, dark = false) == highest)
    }

    private fun assertLevels(paletteKey: ThemePalette, dark: Boolean, ascending: Boolean) {
        val palette = LightDeskPalettes.of(paletteKey, dark)
        val luminances = (0 until HeatmapColors.LEVEL_COUNT).map { level ->
            HeatmapColors.levelColor(level, palette, dark).luminance()
        }
        for (index in 1 until luminances.size) {
            val gap = luminances[index] - luminances[index - 1]
            if (ascending) {
                assertTrue(
                    "深色模式 ${paletteKey.name} 档位 $index 亮度未递增（gap=$gap）",
                    gap >= minLuminanceGap,
                )
            } else {
                assertTrue(
                    "浅色模式 ${paletteKey.name} 档位 $index 亮度未递减（gap=$gap）",
                    -gap >= minLuminanceGap,
                )
            }
        }
    }
}
