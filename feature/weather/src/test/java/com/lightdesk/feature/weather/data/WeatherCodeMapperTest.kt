package com.lightdesk.feature.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodeMapperTest {

    @Test
    fun `maps clear sky`() {
        assertEquals("晴", WeatherCodeMapper.map(0).description)
        assertEquals(WeatherIcon.CLEAR, WeatherCodeMapper.map(0).icon)
    }

    @Test
    fun `maps cloud codes`() {
        assertEquals("晴间多云", WeatherCodeMapper.map(1).description)
        assertEquals(WeatherIcon.PARTLY_CLOUDY, WeatherCodeMapper.map(2).icon)
        assertEquals("阴", WeatherCodeMapper.map(3).description)
        assertEquals(WeatherIcon.CLOUDY, WeatherCodeMapper.map(3).icon)
    }

    @Test
    fun `maps fog and drizzle`() {
        assertEquals(WeatherIcon.FOG, WeatherCodeMapper.map(45).icon)
        assertEquals(WeatherIcon.FOG, WeatherCodeMapper.map(48).icon)
        assertEquals(WeatherIcon.DRIZZLE, WeatherCodeMapper.map(53).icon)
    }

    @Test
    fun `maps rain snow showers thunderstorm`() {
        assertEquals("小雨", WeatherCodeMapper.map(61).description)
        assertEquals(WeatherIcon.RAIN, WeatherCodeMapper.map(65).icon)
        assertEquals(WeatherIcon.SNOW, WeatherCodeMapper.map(71).icon)
        assertEquals(WeatherIcon.SHOWERS, WeatherCodeMapper.map(82).icon)
        assertEquals(WeatherIcon.THUNDERSTORM, WeatherCodeMapper.map(95).icon)
        assertEquals("雷暴伴冰雹", WeatherCodeMapper.map(99).description)
    }

    @Test
    fun `unknown code falls back gracefully`() {
        val condition = WeatherCodeMapper.map(999)
        assertEquals("未知天气", condition.description)
        assertEquals(WeatherIcon.UNKNOWN, condition.icon)
    }
}
