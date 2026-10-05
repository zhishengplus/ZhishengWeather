package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class CoarsePrecipitationRegressionTest {
    private val start = 1_800_000_000_000L
    private val now = start + 8 * 60_000L
    private fun weather(wetNow: Boolean = true) = WeatherData(
        current = CurrentWeather(temperature = 20.0, condition = WeatherCondition.CLEAR),
        rainMinutes = listOf(MinutePrecip(start, if (wetNow) 1f else 0f),
            MinutePrecip(start + 15 * 60_000L, 1f), MinutePrecip(start + 30 * 60_000L, 0f)),
        rainMeta = RainMeta("OPEN-METEO", 15),
    )

    @Test fun ongoingFifteenMinuteRainIsNotReportedAsStartingLater() {
        assertEquals("22 分钟后雨会停", Nowcast.briefing(weather(), "c", now)?.text)
        assertEquals(WeatherCondition.RAIN, WeatherConsistency.align(weather(), now).current?.condition)
    }

    @Test fun presentationRetainsTheCurrentlyActiveCoarseBucket() {
        val result = precipitationPresentation(weather(), now)
        assertEquals(start, result.points.first().timeMillis)
        assertEquals("目前有降水", result.summary)
        assertEquals(15, result.intervalMinutes)
        assertFalse(result.dry)
    }

    @Test fun dryCurrentBucketDoesNotBecomeRainBecauseNextBucketIsWet() {
        assertEquals("7 分钟后开始下雨", Nowcast.briefing(weather(wetNow = false), "c", now)?.text)
        assertEquals(WeatherCondition.CLEAR, WeatherConsistency.align(weather(wetNow = false), now).current?.condition)
    }

    @Test fun aSingleActiveDryBucketStillCountsAsAvailableForecastData() {
        val data = weather(false).copy(rainMinutes = listOf(MinutePrecip(start, 0f)))
        assertTrue(Nowcast.shouldShowPrecipModule(data, now))
        assertEquals(listOf(MinutePrecip(start, 0f)), precipitationPresentation(data, now).points)
    }
}
