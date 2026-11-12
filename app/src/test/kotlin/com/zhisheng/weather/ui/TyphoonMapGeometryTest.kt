package com.zhisheng.weather.ui

import com.zhisheng.weather.data.TyphoonTrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TyphoonMapGeometryTest {
    @Test
    fun dateLineCrossingDoesNotDrawAcrossAsia() {
        val points = listOf(point(170.0), point(179.0), point(-179.0), point(-170.0))
        val segments = splitTyphoonMapLine(points)
        assertEquals(2, segments.size)
        assertEquals(points.take(2), segments[0])
        assertEquals(points.drop(2), segments[1])
    }

    @Test
    fun invalidCoordinateBreaksTrackRatherThanJoiningAcrossMissingFix() {
        val start = point(120.0)
        val end = point(123.0)
        assertEquals(listOf(listOf(start), listOf(end)), splitTyphoonMapLine(listOf(start, point(Double.NaN), end)))
        assertFalse(validTyphoonMapPoint(point(Double.POSITIVE_INFINITY)))
        assertFalse(validTyphoonMapPoint(TyphoonTrackPoint("", 90.0, 120.0)))
        assertTrue(validTyphoonMapPoint(TyphoonTrackPoint("", 0.0, 0.0)))
    }

    @Test
    fun stationaryStormAndOrdinaryTrackKeepAllFixes() {
        val points = listOf(point(120.0), point(120.0), point(121.0))
        assertEquals(listOf(points), splitTyphoonMapLine(points))
        assertTrue(splitTyphoonMapLine(emptyList()).isEmpty())
    }

    private fun point(longitude: Double) = TyphoonTrackPoint("2026-10-26 08:00", 20.0, longitude)

    @Test
    fun dateLineCameraFitsThePacificInsteadOfNearlyTheWholeWorld() {
        assertEquals(listOf(170.0, 179.0, 181.0, 190.0), typhoonMapCameraLongitudes(listOf(170.0, 179.0, -179.0, -170.0)))
        assertEquals(listOf(121.0, 119.0), typhoonMapCameraLongitudes(listOf(121.0, 119.0)))
        assertTrue(typhoonMapCameraLongitudes(emptyList()).isEmpty())
    }
}
