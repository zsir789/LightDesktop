package com.lightdesk.feature.weather.data

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.common.getOrNull
import com.lightdesk.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoResponseMapperTest {

    @Test
    fun `maps current weather and daily forecast`() {
        val result = OpenMeteoResponseMapper.parse(
            body = SAMPLE_BODY,
            city = "北京",
            latitude = 39.9042,
            longitude = 116.4074,
            updatedAt = 1_700_000_000_000L,
        )

        assertTrue(result.getOrNull() != null)
        val snapshot = success(result)
        assertEquals("北京", snapshot.city)
        assertEquals(39.9042, snapshot.latitude, 0.0001)
        assertEquals(116.4074, snapshot.longitude, 0.0001)
        assertEquals(26.4, snapshot.temperature, 0.001)
        assertEquals(27.1, snapshot.apparentTemperature, 0.001)
        assertEquals(58, snapshot.humidity)
        assertEquals(8.6, snapshot.windSpeed, 0.001)
        assertEquals(1, snapshot.weatherCode)
        assertEquals("晴间多云", snapshot.condition)
        assertEquals(1_700_000_000_000L, snapshot.updatedAt)
        assertEquals("open-meteo", snapshot.source)

        assertEquals(2, snapshot.daily.size)
        val first = snapshot.daily.first()
        assertEquals("2024-09-02", first.date)
        assertEquals(1, first.weatherCode)
        assertEquals("晴间多云", first.condition)
        assertEquals(29.1, first.tempMax, 0.001)
        assertEquals(20.3, first.tempMin, 0.001)
        assertEquals(10, first.precipitationProbability)

        val second = snapshot.daily[1]
        assertEquals("小雨", second.condition)
        assertEquals(80, second.precipitationProbability)
    }

    @Test
    fun `ignores unknown json keys`() {
        val body = SAMPLE_BODY.replace("\"latitude\": 39.875,", "\"latitude\": 39.875, \"unknown_key\": 123,")

        val result = OpenMeteoResponseMapper.parse(body, "北京", 39.9, 116.4, 1L)

        assertTrue(result.getOrNull() != null)
    }

    @Test
    fun `missing weather code falls back to unknown condition`() {
        val body = """
            {
              "current": {
                "temperature_2m": 20.0,
                "apparent_temperature": 19.0,
                "relative_humidity_2m": 50,
                "wind_speed_10m": 3.0
              }
            }
        """.trimIndent()

        val snapshot = success(OpenMeteoResponseMapper.parse(body, "北京", 39.9, 116.4, 1L))

        assertEquals(-1, snapshot.weatherCode)
        assertEquals("未知天气", snapshot.condition)
        assertTrue(snapshot.daily.isEmpty())
    }

    @Test
    fun `null precipitation probability defaults to zero`() {
        val body = """
            {
              "current": { "temperature_2m": 20.0, "weather_code": 0 },
              "daily": {
                "time": ["2024-09-02", "2024-09-03"],
                "weather_code": [0, 3],
                "temperature_2m_max": [30.0, 28.0],
                "temperature_2m_min": [20.0, 19.0],
                "precipitation_probability_max": [null, 80]
              }
            }
        """.trimIndent()

        val snapshot = success(OpenMeteoResponseMapper.parse(body, "北京", 39.9, 116.4, 1L))

        assertEquals(2, snapshot.daily.size)
        assertEquals(0, snapshot.daily[0].precipitationProbability)
        assertEquals(80, snapshot.daily[1].precipitationProbability)
    }

    @Test
    fun `incomplete daily entries are skipped`() {
        val body = """
            {
              "current": { "temperature_2m": 20.0, "weather_code": 0 },
              "daily": {
                "time": ["2024-09-02", "2024-09-03"],
                "weather_code": [0],
                "temperature_2m_max": [30.0]
              }
            }
        """.trimIndent()

        val snapshot = success(OpenMeteoResponseMapper.parse(body, "北京", 39.9, 116.4, 1L))

        assertTrue(snapshot.daily.isEmpty())
    }

    @Test
    fun `malformed body returns parse failure`() {
        val result = OpenMeteoResponseMapper.parse("{ not valid json", "北京", 39.9, 116.4, 1L)

        assertTrue(result is AppResult.Failure)
        assertEquals(ErrorCode.PARSE, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `missing current returns parse failure`() {
        val result = OpenMeteoResponseMapper.parse("""{"daily":{}}""", "北京", 39.9, 116.4, 1L)

        assertTrue(result is AppResult.Failure)
        assertEquals(ErrorCode.PARSE, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `missing temperature returns parse failure`() {
        val body = """{"current": {"weather_code": 0}}"""

        val result = OpenMeteoResponseMapper.parse(body, "北京", 39.9, 116.4, 1L)

        assertTrue(result is AppResult.Failure)
    }

    private fun success(result: AppResult<WeatherSnapshot>): WeatherSnapshot =
        result.getOrNull() ?: throw AssertionError("期望解析成功，实际为 $result")

    private companion object {
        val SAMPLE_BODY = """
            {
              "latitude": 39.875,
              "longitude": 116.375,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2024-09-02T10:00",
                "interval": 900,
                "temperature_2m": 26.4,
                "relative_humidity_2m": 58,
                "apparent_temperature": 27.1,
                "weather_code": 1,
                "wind_speed_10m": 8.6
              },
              "daily": {
                "time": ["2024-09-02", "2024-09-03"],
                "weather_code": [1, 61],
                "temperature_2m_max": [29.1, 27.5],
                "temperature_2m_min": [20.3, 19.8],
                "precipitation_probability_max": [10, 80]
              }
            }
        """.trimIndent()
    }
}
