package com.lightdesk.feature.weather.data

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.model.DailyForecast
import com.lightdesk.core.model.WeatherSnapshot
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Open-Meteo 响应解析：JSON -> [WeatherSnapshot]。
 *
 * 使用 kotlinx.serialization 的 JsonElement 手动取值，避免为响应结构额外生成序列化器；
 * 任何结构缺失/类型错误都会返回带中文提示的 [AppResult.Failure]，不会抛异常。
 */
object OpenMeteoResponseMapper {

    const val SOURCE = "open-meteo"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(
        body: String,
        city: String,
        latitude: Double,
        longitude: Double,
        updatedAt: Long,
    ): AppResult<WeatherSnapshot> {
        val root = try {
            json.parseToJsonElement(body)
        } catch (throwable: Throwable) {
            return failure("天气数据解析失败：返回内容不是合法 JSON")
        }
        val rootObject = root as? JsonObject
            ?: return failure("天气数据解析失败：返回结构异常")

        val current = rootObject["current"] as? JsonObject
            ?: return failure("天气数据解析失败：缺少当前天气（current）")
        val temperature = current.doubleOrNull("temperature_2m")
            ?: return failure("天气数据解析失败：缺少温度字段")

        val weatherCode = current.intOrNull("weather_code") ?: UNKNOWN_WEATHER_CODE
        val condition = WeatherCodeMapper.map(weatherCode)
        val apparentTemperature = current.doubleOrNull("apparent_temperature") ?: temperature
        val humidity = current.intOrNull("relative_humidity_2m")?.coerceIn(0, 100) ?: 0
        val windSpeed = current.doubleOrNull("wind_speed_10m") ?: 0.0
        val daily = mapDaily(rootObject["daily"] as? JsonObject)

        return AppResult.Success(
            WeatherSnapshot(
                city = city,
                latitude = latitude,
                longitude = longitude,
                temperature = temperature,
                apparentTemperature = apparentTemperature,
                humidity = humidity,
                windSpeed = windSpeed,
                weatherCode = weatherCode,
                condition = condition.description,
                updatedAt = updatedAt,
                daily = daily,
                source = SOURCE,
            ),
        )
    }

    private fun mapDaily(daily: JsonObject?): List<DailyForecast> {
        if (daily == null) return emptyList()

        val dates = daily.arrayOf("time").map { it.asStringOrNull() }
        val codes = daily.arrayOf("weather_code").map { it.asIntOrNull() }
        val maximums = daily.arrayOf("temperature_2m_max").map { it.asDoubleOrNull() }
        val minimums = daily.arrayOf("temperature_2m_min").map { it.asDoubleOrNull() }
        val precipitations = daily.arrayOf("precipitation_probability_max").map { it.asIntOrNull() }

        val result = mutableListOf<DailyForecast>()
        for (index in dates.indices) {
            val date = dates[index] ?: continue
            val code = codes.getOrNull(index) ?: continue
            val tempMax = maximums.getOrNull(index) ?: continue
            val tempMin = minimums.getOrNull(index) ?: continue
            val precipitation = (precipitations.getOrNull(index) ?: 0).coerceIn(0, 100)
            result += DailyForecast(
                date = date,
                weatherCode = code,
                condition = WeatherCodeMapper.map(code).description,
                tempMax = tempMax,
                tempMin = tempMin,
                precipitationProbability = precipitation,
            )
        }
        return result
    }

    private fun failure(message: String): AppResult.Failure =
        AppResult.Failure(AppError(code = ErrorCode.PARSE, message = message))

    private fun JsonObject.doubleOrNull(key: String): Double? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun JsonObject.intOrNull(key: String): Int? =
        doubleOrNull(key)?.roundToInt()

    private fun JsonObject.arrayOf(key: String): List<JsonElement> =
        (this[key] as? JsonArray)?.toList().orEmpty()

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asDoubleOrNull(): Double? =
        (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun JsonElement?.asIntOrNull(): Int? =
        asDoubleOrNull()?.roundToInt()

    private const val UNKNOWN_WEATHER_CODE = -1
}
