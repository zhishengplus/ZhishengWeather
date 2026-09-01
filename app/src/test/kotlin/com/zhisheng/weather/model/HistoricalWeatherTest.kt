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
        assertFalse(HistoricalDay(date = "2025-08-28", weatherCode = 1).hasComparableTemperature)
        assertTrue(HistoricalDay(date = "2025-08-28", mean = 24.8).hasComparableTemperature)
    }

    @Test
    fun targetDatesUseComparableCalendarDays() {
        assertEquals(
            listOf("2025-08-28", "2024-08-28", "2023-08-28"),
            historicalTargetDates(LocalDate.of(2026, 8, 28), 3).map(LocalDate::toString),
        )
        val review = HistoricalReview(
            referenceDate = LocalDate.of(2026, 8, 28),
            days = listOf(HistoricalDay("2025-08-28", high = 30.0), HistoricalDay("2023-08-28", high = 29.0)),
            requestedCount = 3,
        )
        assertEquals("2023—2025", review.requestedYearRange)
        assertEquals("2023 · 2025", review.includedYearsText)
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
        assertEquals(6.25, result.averagePrecipitationMm!!, 0.001)
        assertEquals(1, result.rainyYears)
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

    @Test
    fun forecastComparisonUsesBothHighAndLowWithoutInventingMissingValues() {
        val summary = HistoricalSummary(averageHigh = 30.0, averageLow = 20.0)
        val warmer = compareWithHistorical(34.0, 22.0, summary)!!
        assertEquals(3.0, warmer.representativeDelta, 0.001)
        assertEquals(HistoricalTemperatureBand.WARMER, warmer.band)

        assertEquals(HistoricalTemperatureBand.TYPICAL, compareWithHistorical(31.4, 21.4, summary)?.band)
        assertEquals(HistoricalTemperatureBand.MUCH_WARMER, compareWithHistorical(35.0, 23.0, summary)?.band)

        val highOnly = compareWithHistorical(30.4, null, summary)!!
        assertEquals(HistoricalTemperatureBand.TYPICAL, highOnly.band)
        assertNull(compareWithHistorical(null, null, summary))
    }

    @Test
    fun recentWeekUsesExactDateRangeAndCompleteDaySummary() {
        val week = RecentWeatherWeek(
            listOf(
                HistoricalDay("2026-08-23", high = 30.0, low = 18.0, precipitationMm = 1.5),
                HistoricalDay("2026-08-29", high = 34.0, low = 20.0, precipitationMm = 0.5),
            ),
        )
        assertEquals(LocalDate.of(2026, 8, 23), requireNotNull(week.startDate))
        assertEquals(LocalDate.of(2026, 8, 29), requireNotNull(week.endDate))
        assertEquals(2.0, requireNotNull(week.totalPrecipitationMm), 0.001)
        assertEquals(32.0, week.summary.averageHigh!!, 0.001)
    }

    @Test
    fun historicalRowsRejectWrongDatesAndImpossibleValues() {
        assertNull(HistoricalDay("bad-date", high = 30.0).normalized())
        assertNull(
            HistoricalDay("2025-08-28", high = 30.0)
                .normalized(LocalDate.of(2025, 8, 29)),
        )
        val clean = HistoricalDay(
            "2025-08-28",
            high = 18.0,
            low = 31.0,
            precipitationMm = -2.0,
            gustMaxKmh = Double.POSITIVE_INFINITY,
        ).normalized()!!
        assertEquals(31.0, clean.high!!, 0.001)
        assertEquals(18.0, clean.low!!, 0.001)
        assertNull(clean.precipitationMm)
        assertNull(clean.gustMaxKmh)
    }

    @Test
    fun recentWeekNeverIncludesTodayOrAnOlderExtraDay() {
        val end = LocalDate.of(2026, 8, 30)
        val rows = (0..8).map { offset ->
            val date = LocalDate.of(2026, 8, 22).plusDays(offset.toLong())
            HistoricalDay(date.toString(), high = 25.0 + offset)
        } + HistoricalDay("2026-08-31", high = 40.0)

        val normalized = normalizeRecentWeatherDays(rows, end)

        assertEquals(7, normalized.size)
        assertEquals("2026-08-24", normalized.first().date)
        assertEquals("2026-08-30", normalized.last().date)
    }
}
