package com.lightdesk.feature.weather.data

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherFallbackResolverTest {

    @Test
    fun `online success prefers fresh data`() {
        val online = snapshot(temperature = 20.0, city = "北京")

        val outcome = WeatherFallbackResolver.resolve(
            online = AppResult.Success(online),
            cached = snapshot(temperature = 10.0, city = "上海"),
        )

        assertEquals(online, outcome.snapshot)
        assertFalse(outcome.isOffline)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `network failure falls back to cache when available`() {
        val cached = snapshot(temperature = 12.0, city = "北京")

        val outcome = WeatherFallbackResolver.resolve(
            online = AppResult.Failure(AppError(ErrorCode.NETWORK, "网络连接失败")),
            cached = cached,
        )

        assertEquals(cached, outcome.snapshot)
        assertTrue(outcome.isOffline)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `network failure without cache surfaces error message`() {
        val outcome = WeatherFallbackResolver.resolve(
            online = AppResult.Failure(AppError(ErrorCode.TIMEOUT, "请求超时")),
            cached = null,
        )

        assertNull(outcome.snapshot)
        assertFalse(outcome.isOffline)
        assertEquals("请求超时", outcome.errorMessage)
    }

    @Test
    fun `parse failure also falls back to cache`() {
        val cached = snapshot(temperature = 12.0, city = "北京")

        val outcome = WeatherFallbackResolver.resolve(
            online = AppResult.Failure(AppError(ErrorCode.PARSE, "解析失败")),
            cached = cached,
        )

        assertEquals(cached, outcome.snapshot)
        assertTrue(outcome.isOffline)
    }

    @Test
    fun `no attempt yet shows cache but no error`() {
        val cached = snapshot(temperature = 12.0, city = "北京")

        val outcome = WeatherFallbackResolver.resolve(online = null, cached = cached)

        assertEquals(cached, outcome.snapshot)
        assertTrue(outcome.isOffline)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `no data at all keeps page empty`() {
        val outcome = WeatherFallbackResolver.resolve(online = null, cached = null)

        assertNull(outcome.snapshot)
        assertFalse(outcome.isOffline)
        assertNull(outcome.errorMessage)
    }

    private fun snapshot(temperature: Double, city: String) = WeatherSnapshot(
        city = city,
        latitude = 39.9042,
        longitude = 116.4074,
        temperature = temperature,
        apparentTemperature = temperature,
        humidity = 50,
        windSpeed = 3.0,
        weatherCode = 0,
        condition = "晴",
        updatedAt = 1_700_000_000_000L,
        daily = emptyList(),
    )
}
