package com.lightdesk.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/**
 * 课程色块配色。
 *
 * 需求：“每节课背景色块颜色随机，呈现五颜六色但仍简约”。
 * 实现要点：
 * 1. 颜色由 `colorSeed` 决定（解析 ICS 时按课程名生成稳定 hash），**随机但跨会话稳定**，
 *    同一门课每次打开颜色一致，不同课程尽量分散；
 * 2. 使用低饱和 pastel 底色 + 深色文字，浅色主题下保持柔和；
 * 3. 深色主题自动把底色压暗、描边提亮，不刺眼。
 *
 * 新增/调整色板只改这里的 [PastelColors]，学习成本为零。
 */
object CourseColors {

    /** 12 个低饱和 pastel 底色，顺序打散色相避免相邻课程撞色。 */
    private val PastelColors: List<Color> = listOf(
        Color(0xFFFBDCDC), // 珊瑚
        Color(0xFFDDE6FC), // 雾蓝
        Color(0xFFD5F2E2), // 薄荷
        Color(0xFFFFE8D2), // 蜜桃
        Color(0xFFE9E1FB), // 紫藤
        Color(0xFFFDF3C9), // 奶油黄
        Color(0xFFD2EEF4), // 晴空
        Color(0xFFFCDDE8), // 樱粉
        Color(0xFFE7F5D0), // 青柠
        Color(0xFFEDE7DC), // 浅沙
        Color(0xFFF8E0F4), // 藕荷
        Color(0xFFDFF0E2), // 鼠尾草
    )

    /** 按种子取色下标；对负数和 Int.MIN_VALUE 安全。 */
    fun indexForSeed(seed: Int): Int = abs(seed % PastelColors.size).let {
        // abs(Int.MIN_VALUE) 仍是负数，这里再兜一次底
        if (it in PastelColors.indices) it else 0
    }

    /** 浅色主题下的色块底色。 */
    fun lightContainer(seed: Int): Color = PastelColors[indexForSeed(seed)]

    /** 深色主题下的色块底色：压在深底上的一层薄雾。 */
    fun darkContainer(seed: Int): Color = lerp(Color(0xFF1E222C), PastelColors[indexForSeed(seed)], 0.20f)

    private fun isLightTheme(background: Color): Boolean = background.luminance() > 0.5f

    /** 色块底色（随主题自动适配）。 */
    @Composable
    @ReadOnlyComposable
    fun container(seed: Int): Color =
        if (isLightTheme(MaterialTheme.colorScheme.background)) lightContainer(seed) else darkContainer(seed)

    /** 色块内文字/图标颜色（与底色同色相，压深/提亮保证对比度）。 */
    @Composable
    @ReadOnlyComposable
    fun onContainer(seed: Int): Color {
        val base = PastelColors[indexForSeed(seed)]
        return if (isLightTheme(MaterialTheme.colorScheme.background)) {
            lerp(base, Color(0xFF1B2030), 0.72f)
        } else {
            lerp(base, Color.White, 0.55f)
        }
    }

    /** 描边/左侧强调条颜色，比底色重一档，用于色块之间的分隔。 */
    @Composable
    @ReadOnlyComposable
    fun accent(seed: Int): Color {
        val base = PastelColors[indexForSeed(seed)]
        return if (isLightTheme(MaterialTheme.colorScheme.background)) {
            lerp(base, Color(0xFF1B2030), 0.35f).copy(alpha = 0.55f)
        } else {
            lerp(base, Color.White, 0.30f).copy(alpha = 0.45f)
        }
    }

    /** 非 Composable 版本，供 ViewModel/纯函数计算（如课程列表导出、测试）。 */
    fun accentFor(seed: Int, dark: Boolean): Color {
        val base = PastelColors[indexForSeed(seed)]
        return if (dark) lerp(base, Color.White, 0.30f).copy(alpha = 0.45f)
        else lerp(base, Color(0xFF1B2030), 0.35f).copy(alpha = 0.55f)
    }

    /** 非 Composable 版本，供需要纯数据场景使用。 */
    fun containerFor(seed: Int, dark: Boolean): Color =
        if (dark) darkContainer(seed) else lightContainer(seed)

    /** 非 Composable 版本。 */
    fun onContainerFor(seed: Int, dark: Boolean): Color {
        val base = PastelColors[indexForSeed(seed)]
        return if (dark) lerp(base, Color.White, 0.55f) else lerp(base, Color(0xFF1B2030), 0.72f)
    }

    /** 色板大小，供测试断言或 UI 展示全部颜色。 */
    val size: Int get() = PastelColors.size
}
