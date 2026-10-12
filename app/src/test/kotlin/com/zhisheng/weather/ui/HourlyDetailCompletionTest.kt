package com.zhisheng.weather.ui

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.home.hourlyDisplayItems
import org.junit.Assert.*
import org.junit.Test

class HourlyDetailCompletionTest {
    private val now = 1_800_000_000_000L

    @Test fun detailKeepsAll240ForecastHoursWhileHomeStaysShort() {
        val hours = (1..240).map { HourlyWeather(now + it * 3_600_000L, it.toDouble()) }
        assertEquals(24, hourlyDisplayItems(null, hours, now).size)
        assertEquals(240, hourlyDetailDisplayItems(null, hours, now, null).size)
    }

    @Test fun missingObservedValuesRemainMissingEvenWhenTheNextForecastHasThem() {
        val next = HourlyWeather(now + 3_600_000, temperature = 25.0, feelsLike = 26.0,
            windGust = 15.0, uvIndex = 8)
        val observation = hourlyDetailDisplayItems(CurrentWeather(temperature = 23.0), listOf(next), now, 45)
            .single { it.isNow }.weather
        assertNull(observation.feelsLike)
        assertNull(observation.windGust)
        assertNull(observation.uvIndex)
        assertEquals(45, observation.aqi)
    }

    @Test fun curvesBreakAtMissingValuesNonFiniteValuesAndMissingHours() {
        val times = listOf(0L, 3_600_000, 7_200_000, 10_800_000, 14_400_000, 25_200_000, 28_800_000)
        assertEquals(listOf(listOf(0, 1), listOf(3), listOf(5, 6)),
            hourlyCurveSegments(times, listOf(10.0, 12.0, null, 16.0, Double.NaN, 20.0, 21.0)))
        assertTrue(hourlyCurveSegments(times, List(times.size) { null }).isEmpty())
    }

    @Test fun detailDateChoicesUseTheLocationsOffsetAndPreserveEveryHour() {
        val utc = java.time.Instant.parse("2026-10-10T18:30:00Z").toEpochMilli()
        val items = hourlyDetailDisplayItems(null, (0..47).map { HourlyWeather(utc + it * 3_600_000) }, utc - 1, null)
        val local = hourlyDetailDays(items, 28_800)
        assertEquals("2026-10-11", local.keys.first())
        assertEquals(48, local.values.sumOf { it.size })
        assertEquals("2026-10-10", hourlyDetailDays(items, -18_000).keys.first())
    }
}
