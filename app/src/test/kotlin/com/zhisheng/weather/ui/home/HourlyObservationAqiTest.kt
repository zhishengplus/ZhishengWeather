package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HourlyObservationAqiTest {
    @Test fun liveObservationDoesNotInheritForecastAirQuality() {
        val now = 1_800_000_000_000L
        val result = hourlyDisplayItems(
            current = CurrentWeather(temperature = 23.0),
            hourly = listOf(
                HourlyWeather(now - 1_800_000L, temperature = 21.0, aqi = 40),
                HourlyWeather(now + 1_800_000L, temperature = 24.0, aqi = 50),
            ),
            nowMillis = now,
        )

        assertEquals(listOf(false, true, false), result.map { it.isNow })
        assertNull(result.single { it.isNow }.weather.aqi)
        assertEquals(40, result.first().weather.aqi)
        assertEquals(50, result.last().weather.aqi)
    }

    @Test fun actualAirQualityWinsOverTheForecastAnchorWithoutChangingForecasts() {
        val now = 1_800_000_000_000L
        val result = hourlyDisplayItems(
            CurrentWeather(temperature = 23.0),
            listOf(HourlyWeather(now - 1_800_000L, aqi = 40), HourlyWeather(now + 1_800_000L, aqi = 50)),
            now,
            currentAqi = 120,
        )

        assertEquals(120, result.single { it.isNow }.weather.aqi)
        assertEquals(40, result.first().weather.aqi)
        assertEquals(50, result.last().weather.aqi)
    }
}
