package com.zhisheng.weather.ui

import com.zhisheng.weather.model.HourlyWeather
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ForecastHourlyDateTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    @Test fun localMidnightFiltersRealHoursWithoutInventingMissingDays() {
        val before = HourlyWeather(ms("2026-10-08T15:00:00Z"), 18.0)
        val midnight = HourlyWeather(ms("2026-10-08T16:00:00Z"), 16.0)
        val late = HourlyWeather(ms("2026-10-09T15:00:00Z"), 12.0)
        val next = HourlyWeather(ms("2026-10-09T16:00:00Z"), 10.0)
        assertEquals(listOf(midnight, late), forecastHoursForDate(listOf(next, late, before, midnight), midnight.timeMillis, 28800))
        assertTrue(forecastHoursForDate(listOf(midnight), ms("2026-10-15T00:00:00Z"), 28800).isEmpty())
    }
    @Test fun missingTemperatureStaysMissingAndDuplicateTimesDoNotDuplicateCurve() {
        val good = HourlyWeather(ms("2026-10-09T01:00:00Z"), 12.0)
        val gap = HourlyWeather(ms("2026-10-09T02:00:00Z"), null)
        assertEquals(listOf(good, gap), forecastHoursForDate(listOf(good, gap, good), good.timeMillis, 0))
        assertNull(forecastHoursForDate(listOf(gap), gap.timeMillis, 0).single().temperature)
    }
    @Test fun negativeOffsetUsesPreviousLocalDay() {
        val hour = HourlyWeather(ms("2026-10-09T02:00:00Z"), 10.0)
        assertEquals(listOf(hour), forecastHoursForDate(listOf(hour), ms("2026-10-08T12:00:00Z"), -18000))
    }
}
