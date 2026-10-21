package com.lightdesk.feature.weather.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Open-Meteo 请求地址构建。集中在一处，便于替换天气服务时定位修改点，
 * 也可被单元测试直接验证参数完整性。
 */
object OpenMeteoUrl {

    const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    const val FORECAST_DAYS = 7

    val CURRENT_FIELDS = listOf(
        "temperature_2m",
        "relative_humidity_2m",
        "apparent_temperature",
        "weather_code",
        "wind_speed_10m",
    )

    val DAILY_FIELDS = listOf(
        "weather_code",
        "temperature_2m_max",
        "temperature_2m_min",
        "precipitation_probability_max",
    )

    fun build(latitude: Double, longitude: Double): String = buildString {
        append(BASE_URL)
        append("?latitude=").append(formatCoordinate(latitude))
        append("&longitude=").append(formatCoordinate(longitude))
        append("&current=").append(CURRENT_FIELDS.joinToString(","))
        append("&daily=").append(DAILY_FIELDS.joinToString(","))
        append("&timezone=auto")
        append("&forecast_days=").append(FORECAST_DAYS)
    }

    private fun formatCoordinate(value: Double): String =
        BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}
