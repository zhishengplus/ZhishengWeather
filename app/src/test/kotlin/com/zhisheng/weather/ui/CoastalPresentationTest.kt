package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.*
import org.junit.Assert.*
import org.junit.Test

class CoastalPresentationTest {
    private val empty = CoastalForecast(TideTrend.UNKNOWN, emptyList(), null, null,
        null, null, null, null, null, null, null, null, 0.0, updatedAtMillis = 0)

    @Test fun pointsAreChronologicalAndDeduplicatedWithoutFillingGaps() {
        val points = coastalDisplayPoints(listOf(MarinePoint(3, 0.2), MarinePoint(1, null),
            MarinePoint(2, Double.NaN), MarinePoint(1, -0.1), MarinePoint(4, Double.POSITIVE_INFINITY)))
        assertEquals(listOf(1L, 2L, 3L, 4L), points.map { it.timeMillis })
        assertEquals(-0.1, points.first().seaLevelM!!, 0.0)
        assertNull(points[1].seaLevelM)
        assertNull(points[3].seaLevelM)
    }

    @Test fun zeroSeaStateAndNegativeSeaTemperatureAreValid() {
        val facts = coastalDisplayFacts(empty.copy(waveHeightM = 0.0, currentVelocityKmh = 0.0, seaTemperatureC = -1.0), "c").toMap()
        assertEquals("0.0m", facts["有效浪高"])
        assertEquals("0.0m/s", facts["海流速度"])
        assertEquals("-1.0°C", facts["海温"])
        assertTrue(coastalDisplayFacts(empty, "c").isEmpty())
        assertTrue(coastalDisplayFacts(empty.copy(waveHeightM = -1.0, wavePeriodSeconds = Double.NaN), "c").isEmpty())
    }

    @Test fun dormantCoastRetainsDataButCannotAppearFromAnOldEnabledPreference() {
        val coast = empty.copy(swellHeightM = 0.8, swellPeriodSeconds = 9.0)
        val pages = vistaScenePages(SceneWeatherData(coast = coast), DisplayPrefs(showCoastWeather = true))
        assertFalse(AtlasPage.COAST in pages)
        assertEquals(listOf("涌浪高度", "涌浪周期"), coastalDisplayFacts(coast, "c").map { it.first })
        assertTrue(coastalDisplayPoints(coast.points).isEmpty())
    }
}
