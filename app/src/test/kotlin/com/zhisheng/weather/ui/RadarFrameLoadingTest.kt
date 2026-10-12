package com.zhisheng.weather.ui

import com.zhisheng.weather.model.RadarFrame
import org.junit.Assert.*
import org.junit.Test

class RadarFrameLoadingTest {
    private val image = RadarFrame(1000, imageUrl = "https://cdn.caiyunapp.com/one.png")
    @Test fun idleMapCannotMakeAMissingOrFailedImageReady() {
        for (state in listOf(null, RadarImageState.LOADING, RadarImageState.FAILED)) {
            assertFalse(radarFrameReady(image, state, true))
            assertEquals(0f, radarFrameOpacity(image, 1000, state), 0f)
        }
        assertTrue(radarFrameReady(image, RadarImageState.READY, false))
        assertEquals(.82f, radarFrameOpacity(image, 1000, RadarImageState.READY), 0f)
        assertEquals(0f, radarFrameOpacity(image, 2000, RadarImageState.READY), 0f)
    }
    @Test fun tileFramesContinueToUseTileReadinessAndPrefetch() {
        val tile = RadarFrame(1000, path = "/one")
        assertFalse(radarFrameReady(tile, RadarImageState.READY, false))
        assertTrue(radarFrameReady(tile, null, true))
        assertEquals(.001f, radarFrameOpacity(tile, 2000, null), 0f)
    }
}
