package com.zhisheng.weather.ui

import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.data.TyphoonTrackPoint
import com.zhisheng.weather.data.TyphoonWindRadii
import org.junit.Assert.assertEquals
import org.junit.Test

class TyphoonTrackDrawTest {
    @Test
    fun polylineKeepsFixesWithoutSpline() {
        val points = listOf(
            TyphoonTrackPoint("t0", 18.0, 130.0, windLevel = 8),
            TyphoonTrackPoint("t1", 19.4, 128.2, windLevel = 10),
            TyphoonTrackPoint("t2", 21.1, 126.0, windLevel = 12),
            TyphoonTrackPoint("t3", 23.0, 123.5, windLevel = 14),
        )
        val line = typhoonPolyline(points)
        assertEquals(points.size, line.size)
        assertEquals(points.first().longitude, line.first().longitude(), 0.0001)
        assertEquals(points.last().latitude, line.last().latitude(), 0.0001)
    }

    @Test
    fun windCircleUsesConstantRadiusPerQuadrant() {
        val center = TyphoonTrackPoint("t0", 18.0, 130.0)
        val radii = TyphoonWindRadii(northEastKm = 200.0, southEastKm = 80.0, southWestKm = 80.0, northWestKm = 200.0)
        val ring = windCircleRing(center, radii)
        val northEast = destinationPoint(center.latitude, center.longitude, 45.0, 200.0)
        val southEast = destinationPoint(center.latitude, center.longitude, 135.0, 80.0)
        val nearestNe = ring.minBy { dist2(it, northEast) }
        val nearestSe = ring.minBy { dist2(it, southEast) }
        assertEquals(northEast.longitude(), nearestNe.longitude(), 0.002)
        assertEquals(northEast.latitude(), nearestNe.latitude(), 0.002)
        assertEquals(southEast.longitude(), nearestSe.longitude(), 0.002)
        assertEquals(southEast.latitude(), nearestSe.latitude(), 0.002)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 0.0001)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 0.0001)
    }

    @Test
    fun intensityColorsFollowNationalSixLevels() {
        assertEquals(Color(0xFF00FEDF), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "热带低压")))
        assertEquals(Color(0xFFFEF300), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "热带风暴")))
        assertEquals(Color(0xFFFE902C), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "强热带风暴")))
        assertEquals(Color(0xFFFE0404), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "台风")))
        assertEquals(Color(0xFFFE3AA3), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "强台风")))
        assertEquals(Color(0xFFAE00D9), typhoonIntensityColor(TyphoonTrackPoint("t", 0.0, 0.0, intensity = "超强台风")))
    }

    @Test
    fun forecastAgenciesUseCookbookColors() {
        assertEquals(Color(0xFFFF4050), forecastAgencyColor("中国"))
        assertEquals(Color(0xFFFF66FF), forecastAgencyColor("中国香港"))
        assertEquals(Color(0xFFFFA040), forecastAgencyColor("中国台湾"))
        assertEquals(Color(0xFF43FF4B), forecastAgencyColor("日本"))
        assertEquals(Color(0xFF40DDFF), forecastAgencyColor("美国"))
    }

    private fun dist2(a: org.maplibre.geojson.Point, b: org.maplibre.geojson.Point): Double {
        val dx = a.longitude() - b.longitude()
        val dy = a.latitude() - b.latitude()
        return dx * dx + dy * dy
    }
}
