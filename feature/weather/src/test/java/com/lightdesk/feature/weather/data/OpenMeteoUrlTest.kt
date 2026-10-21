package com.lightdesk.feature.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoUrlTest {

    @Test
    fun `builds url with all required parameters`() {
        val url = OpenMeteoUrl.build(latitude = 39.9042, longitude = 116.4074)

        assertTrue(url.startsWith(OpenMeteoUrl.BASE_URL))
        assertTrue(url.contains("latitude=39.9042"))
        assertTrue(url.contains("longitude=116.4074"))
        assertTrue(url.contains("current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"))
        assertTrue(url.contains("daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"))
        assertTrue(url.contains("timezone=auto"))
        assertTrue(url.contains("forecast_days=7"))
    }

    @Test
    fun `formats coordinates without exponent or trailing zeros`() {
        val url = OpenMeteoUrl.build(latitude = 0.0, longitude = -0.5)

        assertTrue(url.contains("latitude=0&"))
        assertTrue(url.contains("longitude=-0.5&"))
    }

    @Test
    fun `keeps four decimal precision`() {
        val url = OpenMeteoUrl.build(latitude = 31.230456, longitude = 121.4737)

        assertEquals(
            1,
            Regex("latitude=31\\.2305(&|$)").findAll(url).count(),
        )
        assertTrue(url.contains("longitude=121.4737"))
    }
}
