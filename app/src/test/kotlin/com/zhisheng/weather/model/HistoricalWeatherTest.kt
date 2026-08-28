package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoricalWeatherTest {
    @Test
    fun `all-null archive row is not usable weather`() {
        assertFalse(HistoricalDay(date = "2025-08-28").hasUsableWeather)
        assertTrue(HistoricalDay(date = "2025-08-28", high = 24.8).hasUsableWeather)
    }

    @Test
    fun targetDatesUseComparableCalendarDays() {
        assertEquals(
            listOf("2025-08-28", "2024-08-28", "2023-08-28"),
            historicalTargetDates(LocalDate.of(2026, 8, 28), 3).map(LocalDate::toString),
        )
    }

    @Test
    fun leapDayDoesNotInventFebruary28() {
        assertEquals(
            listOf("2024-02-29", "2020-02-29", "2016-02-29"),
            historicalTargetDates(LocalDate.of(2028, 2, 29), 3).map(LocalDate::toString),
        )
    }

    @Test
    fun summaryKeepsMissingValuesOutOfAverages() {
        val days = listOf(
            HistoricalDay("2025-08-28", high = 30.0, low = 18.0, precipitationMm = 0.0),
            HistoricalDay("2024-08-28", high = 34.0, low = 20.0, precipitationMm = 12.5),
            HistoricalDay("2023-08-28", high = null, low = null, precipitationMm = null),
        )
        val result = summarizeHistoricalDays(days)
        assertEquals(32.0, result.averageHigh!!, 0.001)
        assertEquals(19.0, result.averageLow!!, 0.001)
        assertEquals("2024-08-28", result.warmest?.date)
        assertEquals("2025-08-28", result.coldest?.date)
        assertEquals("2024-08-28", result.wettest?.date)
    }

    @Test
    fun emptySummaryIsHonest() {
        val result = summarizeHistoricalDays(emptyList())
        assertNull(result.averageHigh)
        assertNull(result.warmest)
        assertNull(result.wettest)
    }
}
