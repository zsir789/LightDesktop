package com.lightdesk.feature.timetable

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 周视图缩放的纯逻辑。
 *
 * 缩放只影响“时间轴高度 / 课程块高度”，横向 7 列宽度不变，因此缩放后依然可以双向滚动；
 * 全部为纯函数，便于 JVM 单测（见 `TimetableZoomTest`）。
 *
 * 取值范围 0.6x–2.0x：
 * - 0.6x 即“全览”，整周压缩到最紧凑并回到顶部；
 * - 1.0x 是默认的“标准”字号；
 * - 2.0x 为最大放大，便于查看长课程名与地点。
 */
object TimetableZoom {

    /** 最小缩放（全览）。 */
    const val MIN_SCALE = 0.6f

    /** 最大缩放。 */
    const val MAX_SCALE = 2.0f

    /** 默认（标准）缩放。 */
    const val DEFAULT_SCALE = 1.0f

    /** 点击「＋/－」的每档缩放步长。 */
    const val STEP = 1.25f

    /** 标准缩放下每小时的高度（dp）。 */
    const val BASE_HOUR_HEIGHT_DP = 64f

    /** 每小时高度的下限，避免缩到不可读。 */
    const val MIN_HOUR_HEIGHT_DP = 24f

    /** 小于该比例时课程块进入紧凑模式（单行标题、隐藏地点）。 */
    const val COMPACT_SCALE_THRESHOLD = 0.85f

    private const val EPSILON = 0.001f

    /** 把任意缩放值收拢到 [MIN_SCALE]..[MAX_SCALE]；NaN 回落默认值。 */
    fun clamp(scale: Float): Float =
        if (scale.isNaN()) DEFAULT_SCALE else scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** 在当前缩放上乘以手势比例，并夹紧到合法范围。[factor] 非法时保持当前值。 */
    fun zoomBy(current: Float, factor: Float): Float =
        if (factor.isNaN() || factor <= 0f) clamp(current) else clamp(current * factor)

    /** 「＋」按钮：放大一档。 */
    fun zoomIn(current: Float): Float = zoomBy(current, STEP)

    /** 「－」按钮：缩小一档。 */
    fun zoomOut(current: Float): Float = zoomBy(current, 1f / STEP)

    /** 还能继续放大吗（用于按钮禁用态）。 */
    fun canZoomIn(scale: Float): Boolean = clamp(scale) < MAX_SCALE - EPSILON

    /** 还能继续缩小吗（用于按钮禁用态）。 */
    fun canZoomOut(scale: Float): Boolean = clamp(scale) > MIN_SCALE + EPSILON

    /** 全览缩放值：缩到最小，调用方再把滚动位置回到顶部。 */
    fun overviewScale(): Float = MIN_SCALE

    /** 是否处于全览状态。 */
    fun isOverview(scale: Float): Boolean = abs(clamp(scale) - MIN_SCALE) <= EPSILON

    /** 是否处于默认（标准）状态。 */
    fun isDefault(scale: Float): Boolean = abs(clamp(scale) - DEFAULT_SCALE) <= EPSILON

    /** 是否进入紧凑模式（文字可省略，但字号仍不小于主题最小字号）。 */
    fun isCompact(scale: Float): Boolean = clamp(scale) < COMPACT_SCALE_THRESHOLD

    /** 状态提示文案：全览 / 标准 / 150% 之类。 */
    fun scaleLabel(scale: Float): String = when {
        isOverview(scale) -> "全览"
        isDefault(scale) -> "标准"
        else -> "${(clamp(scale) * 100).roundToInt()}%"
    }

    /** 某一缩放下的每小时高度（dp），不会低于 [MIN_HOUR_HEIGHT_DP]。 */
    fun hourHeightDp(scale: Float, base: Float = BASE_HOUR_HEIGHT_DP): Float =
        (base * clamp(scale)).coerceAtLeast(MIN_HOUR_HEIGHT_DP)

    /** 给定网格总分钟数（如 8:00–20:00 = 720）对应的整列高度（dp）。 */
    fun gridHeightDp(
        totalMinutes: Int,
        scale: Float,
        base: Float = BASE_HOUR_HEIGHT_DP,
    ): Float = (totalMinutes / 60f) * hourHeightDp(scale, base)
}
