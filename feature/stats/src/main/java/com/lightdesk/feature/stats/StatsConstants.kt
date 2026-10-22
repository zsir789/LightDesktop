package com.lightdesk.feature.stats

/**
 * 统计/设置页的集中配置：城市、提醒阈值、热力图分级。
 *
 * 所有“魔法值”都收拢到这里，避免散落在 UI 与 ViewModel 中；
 * 后续新增城市、调整阈值档位或热力图档位时只改本文件即可。
 */

/** 预置天气城市：城市名与经纬度一起持久化，天气模块直接使用。 */
data class WeatherCityOption(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * 预置城市列表。
 *
 * 扩展点：新增城市在 [cities] 中追加即可（列表首项为默认城市）；
 * 若未来改为城市搜索，只需替换数据源，UI 无需改动。
 */
object WeatherCityPresets {

    val cities: List<WeatherCityOption> = listOf(
        WeatherCityOption(name = "北京", latitude = 39.9042, longitude = 116.4074),
        WeatherCityOption(name = "上海", latitude = 31.2304, longitude = 121.4737),
        WeatherCityOption(name = "广州", latitude = 23.1291, longitude = 113.2644),
        WeatherCityOption(name = "深圳", latitude = 22.5431, longitude = 114.0579),
        WeatherCityOption(name = "杭州", latitude = 30.2741, longitude = 120.1551),
        WeatherCityOption(name = "成都", latitude = 30.5728, longitude = 104.0668),
        WeatherCityOption(name = "武汉", latitude = 30.5928, longitude = 114.3055),
        WeatherCityOption(name = "西安", latitude = 34.3416, longitude = 108.9398),
    )

    /** 默认城市（列表首项，当前为北京）。 */
    val default: WeatherCityOption
        get() = cities.first()

    fun byName(name: String): WeatherCityOption? = cities.firstOrNull { it.name == name }
}

/** 待办“临近截止”提醒阈值选项。 */
data class CountdownWarningOption(
    val minutes: Int,
    val label: String,
)

/**
 * 提醒阈值档位。
 *
 * 扩展点：追加新档位只需在 [options] 中增加一项；
 * 模型里存的是分钟数，展示文案由 [labelFor] 统一生成。
 */
object CountdownWarningOptions {

    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    val options: List<CountdownWarningOption> = listOf(
        CountdownWarningOption(minutes = 30, label = "30 分钟"),
        CountdownWarningOption(minutes = MINUTES_PER_HOUR, label = "1 小时"),
        CountdownWarningOption(minutes = 3 * MINUTES_PER_HOUR, label = "3 小时"),
        CountdownWarningOption(minutes = MINUTES_PER_DAY, label = "1 天"),
        CountdownWarningOption(minutes = 3 * MINUTES_PER_DAY, label = "3 天"),
        CountdownWarningOption(minutes = 7 * MINUTES_PER_DAY, label = "7 天"),
    )

    /**
     * 任意分钟数 → 可读文案。
     * 非预置档位（例如旧数据或将来手输）也能安全展示，不会出现空白。
     */
    fun labelFor(minutes: Int): String {
        options.firstOrNull { it.minutes == minutes }?.let { return it.label }
        return when {
            minutes <= 0 -> "不提醒"
            minutes % MINUTES_PER_DAY == 0 -> "${minutes / MINUTES_PER_DAY} 天"
            minutes % MINUTES_PER_HOUR == 0 -> "${minutes / MINUTES_PER_HOUR} 小时"
            else -> "$minutes 分钟"
        }
    }
}

/**
 * 热力图配置：近 [WEEKS] 周、每天格子的活动量分级、图例透明度。
 *
 * 分级（见 [levelMinTotals]）：0 / 1-2 / 3-5 / 6-9 / 10+，共 [LEVEL_COUNT] 档。
 * 调整分级时保持 levelMinTotals 升序即可，heatLevel 会自动适配。
 */
object HeatmapConfig {

    /** 热力图展示的周数（含本周）。 */
    const val WEEKS = 12

    /** 下方“最近 N 天小计”的天数。 */
    const val RECENT_DAYS = 7

    /** 等级数量：0..4 共 5 档。 */
    const val LEVEL_COUNT = 5

    /** 各等级（1..4）的活动量下界，0 级为 total <= 0。 */
    val levelMinTotals: List<Int> = listOf(1, 3, 6, 10)

    /**
     * 图例上每一档的活动量区间文案，与 [levelMinTotals] 一一对应。
     *
     * 图例同时展示“色块 + 区间”，用户不必只靠颜色深浅猜测层级。
     */
    val levelLegendLabels: List<String> = listOf("0", "1-2", "3-5", "6-9", "10+")

    /** 活动总量 → 热力等级（0..4）；负数/异常值按 0 处理。 */
    fun heatLevel(total: Int): Int {
        if (total <= 0) return 0
        return levelMinTotals.count { total >= it }.coerceAtMost(LEVEL_COUNT - 1)
    }
}
