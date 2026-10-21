package com.lightdesk.feature.weather.data

/** 与具体图标库解耦的天气图形语义，UI 层再映射到 Material 图标。 */
enum class WeatherIcon {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SHOWERS,
    SNOW,
    THUNDERSTORM,
    UNKNOWN,
}

/** 天气现象的展示信息：中文描述 + 图形语义。 */
data class WeatherCondition(
    val description: String,
    val icon: WeatherIcon,
)

/**
 * WMO 天气码（Open-Meteo `weather_code`）到中文描述/图标的映射。
 * 纯 Kotlin，无 Android 依赖，可直接单元测试。
 */
object WeatherCodeMapper {

    fun map(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition("晴", WeatherIcon.CLEAR)
        1 -> WeatherCondition("晴间多云", WeatherIcon.PARTLY_CLOUDY)
        2 -> WeatherCondition("多云", WeatherIcon.PARTLY_CLOUDY)
        3 -> WeatherCondition("阴", WeatherIcon.CLOUDY)
        45, 48 -> WeatherCondition("雾", WeatherIcon.FOG)
        51, 53, 55 -> WeatherCondition("毛毛雨", WeatherIcon.DRIZZLE)
        56, 57 -> WeatherCondition("冻毛毛雨", WeatherIcon.DRIZZLE)
        61 -> WeatherCondition("小雨", WeatherIcon.RAIN)
        63 -> WeatherCondition("中雨", WeatherIcon.RAIN)
        65 -> WeatherCondition("大雨", WeatherIcon.RAIN)
        66, 67 -> WeatherCondition("冻雨", WeatherIcon.RAIN)
        71 -> WeatherCondition("小雪", WeatherIcon.SNOW)
        73 -> WeatherCondition("中雪", WeatherIcon.SNOW)
        75 -> WeatherCondition("大雪", WeatherIcon.SNOW)
        77 -> WeatherCondition("雪粒", WeatherIcon.SNOW)
        80 -> WeatherCondition("小阵雨", WeatherIcon.SHOWERS)
        81 -> WeatherCondition("阵雨", WeatherIcon.SHOWERS)
        82 -> WeatherCondition("强阵雨", WeatherIcon.SHOWERS)
        85, 86 -> WeatherCondition("阵雪", WeatherIcon.SNOW)
        95 -> WeatherCondition("雷阵雨", WeatherIcon.THUNDERSTORM)
        96, 99 -> WeatherCondition("雷暴伴冰雹", WeatherIcon.THUNDERSTORM)
        else -> WeatherCondition("未知天气", WeatherIcon.UNKNOWN)
    }
}
