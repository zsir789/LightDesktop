package com.lightdesk.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.lightdesk.core.model.ThemePalette

/**
 * 一套主题的所有色值（浅色/深色由 [LightDeskPalettes.of] 分别给出）。
 *
 * 设计原则：浅色系背景 + 低饱和强调色 + 极浅的色块底色，
 * 换色只改变强调色与背景微色调，不影响信息层级，保持“简约但不单调”。
 */
data class LightDeskPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val accentSoft: Color,
)

/** 每套配色的中文名，供设置页展示。 */
fun ThemePalette.label(): String = when (this) {
    ThemePalette.OCEAN -> "海盐蓝"
    ThemePalette.MINT -> "薄荷绿"
    ThemePalette.PEACH -> "蜜桃橙"
    ThemePalette.LAVENDER -> "薰衣草"
    ThemePalette.SAND -> "暖沙金"
    ThemePalette.ROSE -> "樱花粉"
}

/** 全部可选项，设置页按此顺序展示。 */
val ThemePaletteOptions: List<ThemePalette> = ThemePalette.entries

/**
 * 配色注册表：新配色只需在 [ThemePalette] 与这里各加一项。
 * 未注册的枚举值回退到 [ThemePalette.OCEAN]，保证旧数据/新枚举混用时不会崩溃。
 */
object LightDeskPalettes {

    fun of(palette: ThemePalette, dark: Boolean): LightDeskPalette =
        if (dark) darkOf(palette) else lightOf(palette)

    fun lightOf(palette: ThemePalette): LightDeskPalette = when (palette) {
        ThemePalette.OCEAN -> light(
            primary = Color(0xFF4F6EF7), primaryContainer = Color(0xFFE3E8FF),
            onPrimaryContainer = Color(0xFF1B2A78), secondary = Color(0xFF22C3A6),
            secondaryContainer = Color(0xFFD9F5EE), onSecondaryContainer = Color(0xFF0B4D41),
            background = Color(0xFFF5F6FC), surfaceVariant = Color(0xFFEFF1FA),
            outline = Color(0xFFDFE3F0), accentSoft = Color(0xFFEDF0FE),
        )
        ThemePalette.MINT -> light(
            primary = Color(0xFF2E9E78), primaryContainer = Color(0xFFDCF3E9),
            onPrimaryContainer = Color(0xFF10473A), secondary = Color(0xFF4F9BD9),
            secondaryContainer = Color(0xFFE0F0FC), onSecondaryContainer = Color(0xFF14415D),
            background = Color(0xFFF5FAF7), surfaceVariant = Color(0xFFEDF6F1),
            outline = Color(0xFFDCEAE3), accentSoft = Color(0xFFE7F7F0),
        )
        ThemePalette.PEACH -> light(
            primary = Color(0xFFD97A4A), primaryContainer = Color(0xFFFCE7DA),
            onPrimaryContainer = Color(0xFF6B3418), secondary = Color(0xFFE0A23E),
            secondaryContainer = Color(0xFFFBF0DA), onSecondaryContainer = Color(0xFF5F4310),
            background = Color(0xFFFDF8F4), surfaceVariant = Color(0xFFF8EEE6),
            outline = Color(0xFFF0E0D4), accentSoft = Color(0xFFFDEFE6),
        )
        ThemePalette.LAVENDER -> light(
            primary = Color(0xFF7C6AE8), primaryContainer = Color(0xFFEAE5FD),
            onPrimaryContainer = Color(0xFF2F2470), secondary = Color(0xFF5FA8E8),
            secondaryContainer = Color(0xFFE3F0FC), onSecondaryContainer = Color(0xFF16405E),
            background = Color(0xFFF8F6FD), surfaceVariant = Color(0xFFF0ECFB),
            outline = Color(0xFFE3DEF3), accentSoft = Color(0xFFF1EDFE),
        )
        ThemePalette.SAND -> light(
            primary = Color(0xFFB9873E), primaryContainer = Color(0xFFF7EDD9),
            onPrimaryContainer = Color(0xFF5A3E12), secondary = Color(0xFF7FA88F),
            secondaryContainer = Color(0xFFE4F1E9), onSecondaryContainer = Color(0xFF1F4231),
            background = Color(0xFFFBFAF6), surfaceVariant = Color(0xFFF5F1E7),
            outline = Color(0xFFECE5D6), accentSoft = Color(0xFFF9F2E4),
        )
        ThemePalette.ROSE -> light(
            primary = Color(0xFFD4698B), primaryContainer = Color(0xFFFCE4EC),
            onPrimaryContainer = Color(0xFF6B1F36), secondary = Color(0xFFCF9C6C),
            secondaryContainer = Color(0xFFF8EBDD), onSecondaryContainer = Color(0xFF54341A),
            background = Color(0xFFFDF7F9), surfaceVariant = Color(0xFFF8EDF1),
            outline = Color(0xFFF1E0E6), accentSoft = Color(0xFFFCEAF0),
        )
    }

    fun darkOf(palette: ThemePalette): LightDeskPalette = when (palette) {
        ThemePalette.OCEAN -> dark(
            primary = Color(0xFF8FA2FF), primaryContainer = Color(0xFF2A3768),
            onPrimaryContainer = Color(0xFFDCE2FF), secondary = Color(0xFF3ED8BC),
            secondaryContainer = Color(0xFF0E4F45), onSecondaryContainer = Color(0xFFC9F7EE),
            surface = Color(0xFF1A1D27), surfaceVariant = Color(0xFF242838),
            outline = Color(0xFF2F3446),
        )
        ThemePalette.MINT -> dark(
            primary = Color(0xFF6FD3AE), primaryContainer = Color(0xFF1D4A3B),
            onPrimaryContainer = Color(0xFFCDF3E4), secondary = Color(0xFF7FC0EC),
            secondaryContainer = Color(0xFF163E56), onSecondaryContainer = Color(0xFFD2EBFA),
            surface = Color(0xFF18201E), surfaceVariant = Color(0xFF22302C),
            outline = Color(0xFF2C3B36),
        )
        ThemePalette.PEACH -> dark(
            primary = Color(0xFFF0A277), primaryContainer = Color(0xFF54301D),
            onPrimaryContainer = Color(0xFFFCE0CF), secondary = Color(0xFFF0C177),
            secondaryContainer = Color(0xFF4A3A18), onSecondaryContainer = Color(0xFFF8E8C9),
            surface = Color(0xFF211B17), surfaceVariant = Color(0xFF2E251F),
            outline = Color(0xFF3C2F27),
        )
        ThemePalette.LAVENDER -> dark(
            primary = Color(0xFFA79BFF), primaryContainer = Color(0xFF352B6E),
            onPrimaryContainer = Color(0xFFE6E1FF), secondary = Color(0xFF86BDF0),
            secondaryContainer = Color(0xFF1A3A55), onSecondaryContainer = Color(0xFFD6EBFA),
            surface = Color(0xFF1C1A26), surfaceVariant = Color(0xFF272437),
            outline = Color(0xFF333044),
        )
        ThemePalette.SAND -> dark(
            primary = Color(0xFFDDB478), primaryContainer = Color(0xFF4A3A20),
            onPrimaryContainer = Color(0xFFF6E4C6), secondary = Color(0xFF9EC7AE),
            secondaryContainer = Color(0xFF25402F), onSecondaryContainer = Color(0xFFDCF1E4),
            surface = Color(0xFF1F1D18), surfaceVariant = Color(0xFF2B2821),
            outline = Color(0xFF38332A),
        )
        ThemePalette.ROSE -> dark(
            primary = Color(0xFFF095B0), primaryContainer = Color(0xFF54222F),
            onPrimaryContainer = Color(0xFFFCDDE6), secondary = Color(0xFFE0B183),
            secondaryContainer = Color(0xFF48321E), onSecondaryContainer = Color(0xFFF6E4CD),
            surface = Color(0xFF211A1D), surfaceVariant = Color(0xFF2E2429),
            outline = Color(0xFF3C2E34),
        )
    }

    private fun light(
        primary: Color,
        primaryContainer: Color,
        onPrimaryContainer: Color,
        secondary: Color,
        secondaryContainer: Color,
        onSecondaryContainer: Color,
        background: Color,
        surfaceVariant: Color,
        outline: Color,
        accentSoft: Color,
    ): LightDeskPalette = LightDeskPalette(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = Color(0xFF191C24),
        surface = Color.White,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF6B7280),
        outline = outline,
        outlineVariant = Color(0xFFE8EBF2),
        accentSoft = accentSoft,
    )

    private fun dark(
        primary: Color,
        primaryContainer: Color,
        onPrimaryContainer: Color,
        secondary: Color,
        secondaryContainer: Color,
        onSecondaryContainer: Color,
        surface: Color,
        surfaceVariant: Color,
        outline: Color,
    ): LightDeskPalette = LightDeskPalette(
        primary = primary,
        onPrimary = Color(0xFF10131A),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color(0xFF0B120E),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = Color(0xFF121419),
        onBackground = Color(0xFFECEFF5),
        surface = surface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF9AA1AF),
        outline = outline,
        outlineVariant = Color(0xFF262B36),
        accentSoft = surfaceVariant,
    )
}
