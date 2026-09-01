package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTest {
    @Test
    fun frameOrderIsOldestToNewestAndDeduplicated() {
        val ordered = orderRadarFrames(listOf(30L, 10L, 20L, 20L)) { it }
        assertEquals(listOf(10L, 20L, 30L), ordered)
    }

    @Test
    fun viewportRadiusMatchesRegionalRadarScale() {
        val jinchang = radarViewportRadiusKm(38.52)
        val shanghai = radarViewportRadiusKm(31.23)
        assertTrue(jinchang in 115..130)
        assertTrue(shanghai in 125..140)
    }

    @Test
    fun radarTileTemplateUsesStandardXyzCoordinates() {
        val frame = RadarFrame(1_000L, "/v2/radar/example")
        val timeline = RadarTimeline("https://tilecache.rainviewer.com/", listOf(frame), false)

        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/example/256/{z}/{x}/{y}/2/1_1.png",
            timeline.tileTemplate(frame),
        )
    }

    @Test
    fun coverageMaskSeparatesTransparentCoverageFromOpaqueBlackOutsideArea() {
        assertEquals(RadarCoverageState.AVAILABLE, classifyRadarCoveragePixels(IntArray(25) { 0x00000000 }))
        assertEquals(RadarCoverageState.OUTSIDE, classifyRadarCoveragePixels(IntArray(25) { 0xff000000.toInt() }))
        assertEquals(
            RadarCoverageState.UNKNOWN,
            classifyRadarCoveragePixels(intArrayOf(0x00000000, 0xffffffff.toInt(), 0xffff0000.toInt())),
        )
    }

    @Test
    fun coverageLookupConvertsCityCoordinatesToTheCorrectXyzPixel() {
        val equator = radarTileSample(latitude = 0.0, longitude = 0.0, zoom = 1)
        assertEquals(RadarTileSample(1, 1, 0, 0), equator)

        val beijing = radarTileSample(latitude = 39.9042, longitude = 116.4074, zoom = 7)
        assertEquals(105, beijing.tileX)
        assertEquals(48, beijing.tileY)
        assertTrue(beijing.pixelX in 0..255)
        assertTrue(beijing.pixelY in 0..255)
    }

    @Test
    fun coverageLookupWrapsLongitudeAndClampsMercatorLatitude() {
        assertEquals(
            radarTileSample(90.0, 181.0, 7),
            radarTileSample(85.05112878, -179.0, 7),
        )
    }

    @Test
    fun playbackFramesMergePastAndFutureInTimeOrder() {
        val past = listOf(RadarFrame(10_000L, "/p1"), RadarFrame(30_000L, "/p3"), RadarFrame(20_000L, "/p2"))
        val future = listOf(RadarFrame(60_000L, "/f2"), RadarFrame(40_000L, "/f1"))
        val timeline = RadarTimeline("https://h/", past, false, futureFrames = future)

        assertEquals(
            listOf("/p1", "/p2", "/p3", "/f1", "/f2"),
            timeline.playbackFrames.map { it.path },
        )
        assertEquals(2, timeline.nowBoundaryIndex)
    }

    @Test
    fun nowBoundaryFallsAtLastPastFrameWhenFutureIsLocked() {
        val past = listOf(RadarFrame(10_000L, "/p1"), RadarFrame(20_000L, "/p2"))
        val timeline = RadarTimeline("https://h/", past, false)

        assertEquals(1, timeline.nowBoundaryIndex)
        assertEquals(past, timeline.playbackFrames)
    }

    @Test
    fun feedCarriesSourceFramesAndBoundary() {
        val past = listOf(RadarFrame(10_000L, "/p1"), RadarFrame(20_000L, "/p2"))
        val future = listOf(RadarFrame(40_000L, "/f1"))
        val feed = RadarFeed(past = past, future = future, host = "https://tile.h/")

        assertEquals(listOf("/p1", "/p2", "/f1"), feed.playbackFrames.map(RadarFrame::path))
        assertEquals(1, feed.nowBoundaryIndex)
        assertEquals("https://tile.h/", feed.host)
    }

    @Test
    fun imageFramesCarryBoundsAndStableFrameKey() {
        val frame = RadarFrame(
            timeMillis = 1_640_787_600_000L,
            imageUrl = "https://cdn.caiyunapp.com/a.png?auth_key=x",
            southLat = 3.9,
            westLng = 71.9,
            northLat = 57.9,
            eastLng = 150.6,
        )
        assertTrue(frame.isImageFrame)
        assertTrue(frame.frameKey.contains("a.png"))
        val tile = RadarFrame(1_000L, "/v2/radar/example")
        assertTrue(!tile.isImageFrame)
    }

    @Test
    fun radarSourceRoundTripsThroughPrefKey() {
        assertEquals(RadarSource.RAINVIEWER, RadarSource.fromKey(null))
        assertEquals(RadarSource.RAINVIEWER, RadarSource.fromKey("rainviewer"))
        assertEquals(RadarSource.CAIYUN, RadarSource.fromKey("caiyun"))
        assertEquals(RadarSource.RAINVIEWER, RadarSource.fromKey("unknown"))
    }
}
