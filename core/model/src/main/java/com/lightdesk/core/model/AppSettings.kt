package com.lightdesk.core.model

import kotlinx.serialization.Serializable

/** 主题模式。 */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 主题配色（浅色系为主）：只换“强调色 + 背景微色调”，保持简约。
 * 新增配色时在此追加枚举，并在 `core:designsystem` 的 `LightDeskPalettes` 补一组色值即可全 App 生效。
 */
@Serializable
enum class ThemePalette {
    OCEAN,
    MINT,
    PEACH,
    LAVENDER,
    SAND,
    ROSE,
}

/**
 * 全局设置：所有可调参数集中于此，避免散落成硬编码。
 * v1.1.0 起移除悬浮球相关设置（功能下线），旧版本 JSON 中多余字段会被忽略。
 * v1.2.0 新增 [themePalette]；旧数据缺该字段时取默认值 [ThemePalette.OCEAN]，无需迁移。
 */
@Serializable
data class AppSettings(
    /** 天气城市名称。 */
    val weatherCity: String = "北京",
    val weatherLatitude: Double = 39.9042,
    val weatherLongitude: Double = 116.4074,
    /** 待办“临近截止”的预警阈值（分钟），默认 24 小时。 */
    val countdownWarningMinutes: Int = 24 * 60,
    /** 课表是否以周一为一周起点（预留）。 */
    val weekStartMonday: Boolean = true,
    /** 主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 主题配色。 */
    val themePalette: ThemePalette = ThemePalette.OCEAN,
    /** 是否记录使用统计（预留开关）。 */
    val usageStatsEnabled: Boolean = true,
)
