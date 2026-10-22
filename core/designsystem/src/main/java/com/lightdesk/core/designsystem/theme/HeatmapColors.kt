package com.lightdesk.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 热力图色阶。
 *
 * v1.2.0 使用“主题色 + 透明度叠加”，在浅色背景上 0.20/0.40/0.65/0.95 四档差别很小，
 * 小格子里几乎看不出层级。这里改为固定混合比例的 5 档顺序色阶：
 * - 0 级：背景色（无活动）；
 * - 1..4 级：主题色与表面色按递增比例混合，最后一档为主题色实色。
 *
 * 深色模式需要更高的混合比例才能在深色表面上体现层级，故单独给出 [DARK_STOPS]。
 * 色阶纯函数化，便于单测与后续换肤。
 */
object HeatmapColors {

    /** 档位数量：0..4。 */
    const val LEVEL_COUNT = 5

    /** 浅色模式：0 级之外，各档与主题色混合的比例。 */
    private val LIGHT_STOPS = floatArrayOf(0f, 0.24f, 0.46f, 0.70f, 1.0f)

    /** 深色模式：深色表面吸收力强，比例整体上移。 */
    private val DARK_STOPS = floatArrayOf(0f, 0.32f, 0.54f, 0.76f, 1.0f)

    /**
     * 取某一档的热力格颜色。
     *
     * @param level 0..[LEVEL_COUNT]-1，越界自动夹紧
     * @param palette 当前主题配色
     * @param dark 是否深色模式
     */
    fun levelColor(level: Int, palette: LightDeskPalette, dark: Boolean): Color {
        val safeLevel = level.coerceIn(0, LEVEL_COUNT - 1)
        if (safeLevel == 0) return palette.surfaceVariant
        val stops = if (dark) DARK_STOPS else LIGHT_STOPS
        return lerp(palette.surface, palette.primary, stops[safeLevel])
    }
}
