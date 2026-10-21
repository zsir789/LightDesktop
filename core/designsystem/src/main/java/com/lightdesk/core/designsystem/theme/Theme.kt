package com.lightdesk.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lightdesk.core.model.ThemePalette

/** 语义化状态色：供“已完成/临近截止/异常”等场景使用，自动适配深浅色。 */
data class StatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val muted: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        success = LightDeskColors.Success,
        warning = LightDeskColors.Warning,
        danger = LightDeskColors.Danger,
        info = LightDeskColors.Info,
        muted = LightDeskColors.LightOnSurfaceVariant,
    )
}

/** 当前生效的完整配色（含背景微色调、色块底色 [LightDeskPalette.accentSoft]）。 */
val LocalLightDeskPalette = staticCompositionLocalOf { LightDeskPalettes.lightOf(ThemePalette.OCEAN) }

/** 由配色注册表生成 Material3 浅色方案，新增配色无需改这里。 */
private fun lightSchemeOf(palette: LightDeskPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,
    secondary = palette.secondary,
    onSecondary = palette.onSecondary,
    secondaryContainer = palette.secondaryContainer,
    onSecondaryContainer = palette.onSecondaryContainer,
    tertiary = palette.secondary,
    onTertiary = palette.onSecondary,
    background = palette.background,
    onBackground = palette.onBackground,
    surface = palette.surface,
    onSurface = palette.onBackground,
    surfaceVariant = palette.surfaceVariant,
    onSurfaceVariant = palette.onSurfaceVariant,
    outline = palette.outline,
    outlineVariant = palette.outlineVariant,
    error = LightDeskColors.Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E8),
    onErrorContainer = Color(0xFF7A1316),
)

/** 由配色注册表生成 Material3 深色方案。 */
private fun darkSchemeOf(palette: LightDeskPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryContainer,
    onPrimaryContainer = palette.onPrimaryContainer,
    secondary = palette.secondary,
    onSecondary = palette.onSecondary,
    secondaryContainer = palette.secondaryContainer,
    onSecondaryContainer = palette.onSecondaryContainer,
    tertiary = palette.secondary,
    onTertiary = palette.onSecondary,
    background = palette.background,
    onBackground = palette.onBackground,
    surface = palette.surface,
    onSurface = palette.onBackground,
    surfaceVariant = palette.surfaceVariant,
    onSurfaceVariant = palette.onSurfaceVariant,
    outline = palette.outline,
    outlineVariant = palette.outlineVariant,
    error = LightDeskColors.DangerDark,
    onError = Color(0xFF2A0507),
    errorContainer = Color(0xFF4A1418),
    onErrorContainer = Color(0xFFFFDADB),
)

/**
 * 应用唯一主题入口。功能模块统一使用 [LightDeskTheme]，
 * 不直接创建 MaterialTheme，保证换配色/换肤只改这一处。
 *
 * @param palette 用户选择的配色（来自 [com.lightdesk.core.model.AppSettings.themePalette]）。
 */
@Composable
fun LightDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.OCEAN,
    content: @Composable () -> Unit,
) {
    val resolved = LightDeskPalettes.of(palette, darkTheme)
    val colorScheme = if (darkTheme) darkSchemeOf(resolved) else lightSchemeOf(resolved)
    val statusColors = if (darkTheme) {
        StatusColors(
            success = LightDeskColors.SuccessDark,
            warning = LightDeskColors.WarningDark,
            danger = LightDeskColors.DangerDark,
            info = resolved.primary,
            muted = resolved.onSurfaceVariant,
        )
    } else {
        StatusColors(
            success = LightDeskColors.Success,
            warning = LightDeskColors.Warning,
            danger = LightDeskColors.Danger,
            info = resolved.primary,
            muted = resolved.onSurfaceVariant,
        )
    }

    CompositionLocalProvider(
        LocalStatusColors provides statusColors,
        LocalLightDeskPalette provides resolved,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LightDeskTypography,
            shapes = LightDeskShapes,
            content = content,
        )
    }
}
