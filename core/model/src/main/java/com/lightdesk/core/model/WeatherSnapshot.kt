package com.lightdesk.core.model

import kotlinx.serialization.Serializable

/** 单日预报。 */
@Serializable
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val condition: String,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationProbability: Int = 0,
)

/**
 * 一次天气快照。既用于在线结果展示，也用于离线降级时的缓存展示。
 * source 记录数据来源，方便后续接入其它天气服务（预留扩展点）。
 */
@Serializable
data class WeatherSnapshot(
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val condition: String,
    val updatedAt: Long,
    val daily: List<DailyForecast> = emptyList(),
    val source: String = "open-meteo",
)
