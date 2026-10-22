package com.lightdesk.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TimetableZoom] 纯逻辑单测：范围夹紧、按钮档位、全览判定与高度换算。
 */
class TimetableZoomTest {

    @Test
    fun `clamp keeps scale inside range`() {
        assertEquals(TimetableZoom.MIN_SCALE, TimetableZoom.clamp(0.1f), 0.0001f)
        assertEquals(TimetableZoom.MAX_SCALE, TimetableZoom.clamp(3.5f), 0.0001f)
        assertEquals(1.4f, TimetableZoom.clamp(1.4f), 0.0001f)
    }

    @Test
    fun `clamp falls back to default for NaN`() {
        assertEquals(TimetableZoom.DEFAULT_SCALE, TimetableZoom.clamp(Float.NaN), 0.0001f)
    }

    @Test
    fun `zoomBy multiplies factor`() {
        assertEquals(1.5f, TimetableZoom.zoomBy(1.0f, 1.5f), 0.0001f)
        assertEquals(0.75f, TimetableZoom.zoomBy(1.5f, 0.5f), 0.0001f)
    }

    @Test
    fun `zoomBy ignores invalid factor`() {
        assertEquals(0.9f, TimetableZoom.zoomBy(0.9f, 0f), 0.0001f)
        assertEquals(0.9f, TimetableZoom.zoomBy(0.9f, -2f), 0.0001f)
        assertEquals(0.9f, TimetableZoom.zoomBy(0.9f, Float.NaN), 0.0001f)
    }

    @Test
    fun `zoomIn stops at max scale`() {
        var scale = TimetableZoom.DEFAULT_SCALE
        repeat(20) { scale = TimetableZoom.zoomIn(scale) }
        assertEquals(TimetableZoom.MAX_SCALE, scale, 0.0001f)
        assertFalse(TimetableZoom.canZoomIn(scale))
    }

    @Test
    fun `zoomOut stops at min scale`() {
        var scale = TimetableZoom.DEFAULT_SCALE
        repeat(20) { scale = TimetableZoom.zoomOut(scale) }
        assertEquals(TimetableZoom.MIN_SCALE, scale, 0.0001f)
        assertFalse(TimetableZoom.canZoomOut(scale))
    }

    @Test
    fun `zoomIn and zoomOut one step are inverse`() {
        assertEquals(1.0f, TimetableZoom.zoomOut(TimetableZoom.zoomIn(1.0f)), 0.0001f)
    }

    @Test
    fun `pinch factor accumulates from current scale`() {
        assertEquals(1.2f, TimetableZoom.zoomBy(TimetableZoom.zoomBy(1.0f, 1.5f), 0.8f), 0.0001f)
    }

    @Test
    fun `overview is min scale and scroll-to-top state`() {
        assertEquals(TimetableZoom.MIN_SCALE, TimetableZoom.overviewScale(), 0.0001f)
        assertTrue(TimetableZoom.isOverview(TimetableZoom.overviewScale()))
        assertFalse(TimetableZoom.isOverview(TimetableZoom.DEFAULT_SCALE))
    }

    @Test
    fun `default state detection`() {
        assertTrue(TimetableZoom.isDefault(TimetableZoom.DEFAULT_SCALE))
        assertFalse(TimetableZoom.isDefault(1.1f))
        assertEquals("标准", TimetableZoom.scaleLabel(TimetableZoom.DEFAULT_SCALE))
    }

    @Test
    fun `scale label reflects state and percent`() {
        assertEquals("全览", TimetableZoom.scaleLabel(TimetableZoom.MIN_SCALE))
        assertEquals("150%", TimetableZoom.scaleLabel(1.5f))
        assertEquals("70%", TimetableZoom.scaleLabel(0.7f))
    }

    @Test
    fun `compact mode only below threshold`() {
        assertTrue(TimetableZoom.isCompact(TimetableZoom.MIN_SCALE))
        assertFalse(TimetableZoom.isCompact(TimetableZoom.DEFAULT_SCALE))
        assertFalse(TimetableZoom.isCompact(TimetableZoom.COMPACT_SCALE_THRESHOLD))
    }

    @Test
    fun `hour height scales with zoom`() {
        assertEquals(64f, TimetableZoom.hourHeightDp(1.0f), 0.0001f)
        assertEquals(96f, TimetableZoom.hourHeightDp(1.5f), 0.0001f)
        assertEquals(38.4f, TimetableZoom.hourHeightDp(TimetableZoom.MIN_SCALE), 0.0001f)
    }

    @Test
    fun `hour height never drops below minimum`() {
        assertEquals(TimetableZoom.MIN_HOUR_HEIGHT_DP, TimetableZoom.hourHeightDp(1.0f, base = 10f), 0.0001f)
    }

    @Test
    fun `grid height uses total minutes and scale`() {
        // 8:00 - 20:00 共 12 小时，标准缩放 64dp/小时
        assertEquals(768f, TimetableZoom.gridHeightDp(720, 1.0f), 0.001f)
        // 全览 0.6x
        assertEquals(460.8f, TimetableZoom.gridHeightDp(720, TimetableZoom.MIN_SCALE), 0.001f)
    }

    @Test
    fun `day column width scales horizontally`() {
        assertEquals(TimetableZoom.BASE_DAY_COLUMN_WIDTH_DP, TimetableZoom.dayColumnWidthDp(1.0f), 0.001f)
        assertEquals(192f, TimetableZoom.dayColumnWidthDp(2.0f), 0.001f)
        assertEquals(57.6f, TimetableZoom.dayColumnWidthDp(TimetableZoom.MIN_SCALE), 0.001f)
    }

    @Test
    fun `time axis width scales but keeps a minimum`() {
        assertEquals(TimetableZoom.BASE_TIME_AXIS_WIDTH_DP, TimetableZoom.timeAxisWidthDp(1.0f), 0.001f)
        assertEquals(96f, TimetableZoom.timeAxisWidthDp(2.0f), 0.001f)
        // 0.6x 时 48dp * 0.6 = 28.8dp，低于下限，应夹紧到最小值
        assertEquals(TimetableZoom.MIN_TIME_AXIS_WIDTH_DP, TimetableZoom.timeAxisWidthDp(TimetableZoom.MIN_SCALE), 0.001f)
    }

    @Test
    fun `text scale follows zoom but is clamped`() {
        assertEquals(1.0f, TimetableZoom.textScale(1.0f), 0.001f)
        assertEquals(TimetableZoom.MIN_TEXT_SCALE, TimetableZoom.textScale(TimetableZoom.MIN_SCALE), 0.001f)
        assertEquals(TimetableZoom.MAX_TEXT_SCALE, TimetableZoom.textScale(TimetableZoom.MAX_SCALE), 0.001f)
    }

    @Test
    fun `horizontal zoom is monotonic with scale`() {
        var previousWidth = 0f
        listOf(0.6f, 0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { scale ->
            val width = TimetableZoom.dayColumnWidthDp(scale)
            assertTrue("列宽应随缩放增大：scale=$scale width=$width", width > previousWidth)
            previousWidth = width
        }
    }
}
