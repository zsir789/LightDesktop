package com.lightdesk.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 简约风配色：低饱和背景 + 单主色强调 + 克制的状态色。
 * 所有颜色集中定义，功能模块不得私自硬编码色值。
 */
object LightDeskColors {
    // 主色
    val Primary = Color(0xFF4F6EF7)
    val PrimaryDark = Color(0xFF6D87FF)
    val OnPrimary = Color(0xFFFFFFFF)

    // 辅助色
    val Secondary = Color(0xFF22C3A6)
    val SecondaryDark = Color(0xFF3ED8BC)
    val OnSecondary = Color(0xFFFFFFFF)

    // 浅色模式
    val LightBackground = Color(0xFFF4F5F9)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF0F6)
    val LightOnBackground = Color(0xFF191C24)
    val LightOnSurfaceVariant = Color(0xFF6B7280)
    val LightOutline = Color(0xFFDDE1EA)

    // 深色模式
    val DarkBackground = Color(0xFF111318)
    val DarkSurface = Color(0xFF1A1D24)
    val DarkSurfaceVariant = Color(0xFF242833)
    val DarkOnBackground = Color(0xFFECEFF5)
    val DarkOnSurfaceVariant = Color(0xFF9AA1AF)
    val DarkOutline = Color(0xFF2E3340)

    // 状态色
    val Success = Color(0xFF22A06B)
    val SuccessDark = Color(0xFF4CD39A)
    val Warning = Color(0xFFE8A23A)
    val WarningDark = Color(0xFFF2BE6B)
    val Danger = Color(0xFFE5484D)
    val DangerDark = Color(0xFFFF6B70)
    val Info = Color(0xFF4F6EF7)
}
