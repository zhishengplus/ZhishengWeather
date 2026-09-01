package com.zhisheng.weather.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DayPhaseTest {
    private val offset = 8 * 3_600
    private val dayStart = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
    private val data = WeatherData(
        utcOffsetSeconds = offset,
        daily = listOf(
            DailyWeather(
                dateMillis = dayStart,
                sunrise = "06:24",
                sunset = "19:18",
            ),
        ),
    )

    @Test
    fun clearWeatherUsesMoonAfterLocalSunset() {
        val night = Instant.parse("2026-08-30T13:30:00Z").toEpochMilli()
        assertEquals(
            WeatherCondition.CLEAR_NIGHT,
            phaseAwareCondition(WeatherCondition.CLEAR, data, night),
        )
    }

    @Test
    fun staleNightVariantReturnsToSunAfterSunrise() {
        val noon = Instant.parse("2026-08-30T04:00:00Z").toEpochMilli()
        assertEquals(
            WeatherCondition.CLEAR,
            phaseAwareCondition(WeatherCondition.CLEAR_NIGHT, data, noon),
        )
    }

    @Test
    fun partlyCloudyAndRainKeepCorrectSemantics() {
        val night = Instant.parse("2026-08-30T13:30:00Z").toEpochMilli()
        assertEquals(
            WeatherCondition.PARTLY_CLOUDY_NIGHT,
            phaseAwareCondition(WeatherCondition.PARTLY_CLOUDY, data, night),
        )
        assertEquals(
            WeatherCondition.RAIN,
            phaseAwareCondition(WeatherCondition.RAIN, data, night),
        )
    }

    @Test
    fun missingAstronomyFallsBackToSixAndNineteen() {
        assertEquals(true, isNightBySun(null, null, 23 * 60))
        assertEquals(false, isNightBySun(null, null, 12 * 60))
    }
}
