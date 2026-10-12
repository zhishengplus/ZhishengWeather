package com.zhisheng.weather.ui

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRangeTest {
    @Test fun meanOnlyPointsStayInsideTheScaleInBothTemperatureUnits() {
        for (unit in listOf("c", "f")) {
            val readings = listOf(-30.0, 5.0, 19.0, 42.0)
            val scale = historyPlotScale(readings, unit)
            readings.forEach { celsius ->
                val shown = historyShownTemperature(celsius, unit)!!
                assertTrue("Every real point must fit the plot: $unit/$celsius", shown in scale.first..scale.second)
            }
            assertEquals(historyShownTemperature(-30.0, unit)!!, scale.first, 0.001)
            assertEquals(historyShownTemperature(42.0, unit)!!, scale.second, 0.001)
        }
    }

    @Test fun aFlatMeanOnlySeriesUsesANonzeroCenteredScale() {
        for (unit in listOf("c", "f")) {
            val scale = historyPlotScale(List(7) { 12.0 }, unit)
            val mean = historyShownTemperature(12.0, unit)!!
            assertTrue(scale.first < mean && scale.second > mean)
            assertEquals(mean, (scale.first + scale.second) / 2, 0.001)
        }
    }

    @Test fun missingAndInvalidReadingsNeverBecomePlottedValues() {
        assertEquals(0.0 to 1.0, historyPlotScale(listOf(null, Double.NaN, Double.POSITIVE_INFINITY), "c"))
        assertEquals(5.0 to 20.0, historyPlotScale(listOf(null, 5.0, 20.0, Double.NaN), "c"))
    }

    @Test fun missingDayDoesNotInventATemperatureSpan() {
        assertNull(historyBarSpan(null, null, 0.0, 20.0, "c"))
    }

    @Test fun coldestAndWarmestSitOnTheSharedScaleEnds() {
        val cold = historyBarSpan(0.0, 10.0, 0.0, 20.0, "c")!!
        val warm = historyBarSpan(10.0, 20.0, 0.0, 20.0, "c")!!
        assertEquals(0f, cold.first, 0.001f)
        assertEquals(0.5f, cold.second, 0.001f)
        assertEquals(1f, warm.first + warm.second, 0.001f)
    }

    @Test fun october2026ThursdaySitsUnderItsWeekdayColumn() {
        val thursday = LocalDate.of(2026, 10, 1)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)
        val labels = historyWeekdayLabels()
        val grid = historyMonthGrid(thursday)
        assertEquals(7, labels.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(thursday, grid[0][labels.indexOf("四")])
        val sunday = LocalDate.of(2026, 10, 11)
        val flat = grid.flatten()
        assertEquals(labels.indexOf("日"), flat.indexOf(sunday) % 7)
    }

    @Test fun ribbonAppearsOnlyWhenNeighboringDaysBothHaveTemperatures() {
        assertTrue(historyRibbonHasBand(listOf(true, true, false)))
        assertEquals(false, historyRibbonHasBand(listOf(false, true, false, true)))
    }

    @Test fun aSingleTemperatureStaysAShortMark() {
        val mark = historyBarSpan(null, 20.0, 0.0, 20.0, "c")!!
        assertTrue(mark.second < 0.2f)
        assertTrue(mark.first > 0.8f)
    }
}
