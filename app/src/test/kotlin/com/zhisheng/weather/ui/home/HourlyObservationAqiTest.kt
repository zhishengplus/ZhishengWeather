package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun forecastProbabilityAndProfileCannotFillMissingObservationFields() {
        val now = 1_800_000_000_000L
        val anchor = HourlyWeather(now - 1_800_000L, precipProb = 80,
            profile = WeatherProfile(WeatherCondition.SNOW), aqi = 40, aqiEstimated = true)
        val result = hourlyDisplayItems(CurrentWeather(temperature = 23.0), listOf(anchor), now, 120)
        val observed = result.single { it.isNow }.weather
        assertNull(observed.precipProb)
        assertNull(observed.profile)
        assertFalse(observed.aqiEstimated)
        assertEquals(anchor, result.first().weather)
    }

    @Test fun estimationFlagsSurviveObservationAndForecastPresentationIndependently() {
        val now = 1_800_000_000_000L
        val provider = HourlyWeather(now - 1_800_000L, aqi = 40)
        val estimated = HourlyWeather(now + 1_800_000L, aqi = 50, aqiEstimated = true)
        val result = hourlyDisplayItems(CurrentWeather(temperature = 23.0), listOf(provider, estimated), now,
            currentAqi = 120, currentAqiEstimated = true)
        assertTrue(result.single { it.isNow }.weather.aqiEstimated)
        assertFalse(result.first().weather.aqiEstimated)
        assertTrue(result.last().weather.aqiEstimated)
        assertEquals(listOf(40, 120, 50), result.map { it.weather.aqi })
        assertTrue(hourlyDisplayItems(CurrentWeather(), emptyList(), now, 120, true).single().weather.aqiEstimated)
    }
}
