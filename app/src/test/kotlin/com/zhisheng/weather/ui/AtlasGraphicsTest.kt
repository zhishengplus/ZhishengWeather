package com.zhisheng.weather.ui

import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.home.*
import org.junit.Assert.*
import org.junit.Test

class AtlasGraphicsTest {
    private val city = City("青岛", "山东", 36.07, 120.38, "test")

    @Test fun sunChartIsFiniteAndKeepsExactWindowEndpoints() {
        val start = 1_788_516_000_000L
        val end = start + 4 * 3_600_000
        val points = atlasSunPoints(city, start, end)
        assertEquals(49, points.size)
        assertEquals(start, points.first().time)
        assertEquals(end, points.last().time)
        assertTrue(points.all { it.altitude.isFinite() && it.altitude in -90.0..90.0 })
        assertTrue(points.zipWithNext().all { (a, b) -> b.time > a.time })
    }

    @Test fun invalidCoordinatesOrWindowDoNotInventPlot() {
        assertTrue(atlasSunPoints(city, 10, 10).isEmpty())
        assertTrue(atlasSunPoints(city, 10, 9).isEmpty())
        assertTrue(atlasSunPoints(city.copy(latitude = Double.NaN), 10, 20).isEmpty())
        assertTrue(atlasSunPoints(city.copy(latitude = 91.0), 10, 20).isEmpty())
        assertTrue(atlasSunPoints(city.copy(longitude = -181.0), 10, 20).isEmpty())
    }

    @Test fun timeCursorClampsAtEdgesAndHandlesEmptyWindow() {
        assertEquals(0f, atlasTimeFraction(0, 10, 20))
        assertEquals(.5f, atlasTimeFraction(15, 10, 20))
        assertEquals(1f, atlasTimeFraction(30, 10, 20))
        assertEquals(0f, atlasTimeFraction(30, 20, 20))
    }

    @Test fun missingNumbersStayMissingAndUnitsAreConsistent() {
        assertEquals("—", atlasNumber(null, "m"))
        assertEquals("—", atlasNumber(Double.NaN))
        assertEquals("—", atlasNumber(Double.POSITIVE_INFINITY))
        assertEquals("32.0°F", atlasTemperature(0.0, "f"))
        assertEquals("0.0°C", atlasTemperature(0.0, "c"))
        assertEquals("00:00", atlasClock(0, "bad timezone"))
    }

    @Test fun radarThumbnailOnlyUsesTrustedHttpsOrigin() {
        assertTrue(isAtlasRadarTileUrl("https://tilecache.rainviewer.com/v2/radar/1/256/6/1/2/2/1_1.png"))
        assertFalse(isAtlasRadarTileUrl("http://tilecache.rainviewer.com/x"))
        assertFalse(isAtlasRadarTileUrl("https://rainviewer.com.evil.example/x"))
        assertFalse(isAtlasRadarTileUrl("https://tilecache.rainviewer.com:8443/x"))
        assertFalse(isAtlasRadarTileUrl("https://user:pass@tilecache.rainviewer.com/x"))
        assertFalse(isAtlasRadarTileUrl("invalid"))
    }

    @Test fun nightAxisUsesLocalEveningToNoonAcrossDaylightSaving() {
        val night = java.time.Instant.parse("2026-03-08T06:00:00Z").toEpochMilli()
        val (start, end) = atlasNightBounds(night, "America/New_York")
        assertEquals("18:00", atlasClock(start, "America/New_York"))
        assertEquals("12:00", atlasClock(end, "America/New_York"))
        assertEquals(17 * 3_600_000L, end - start)
    }

    @Test fun tideAxisExplicitlyMarksTheFollowingLocalDay() {
        assertEquals("次日 08:00", atlasTideEndLabel(0, 86_400_000, "Asia/Shanghai"))
        assertEquals("09:00", atlasTideEndLabel(0, 3_600_000, "Asia/Shanghai"))
    }
}
