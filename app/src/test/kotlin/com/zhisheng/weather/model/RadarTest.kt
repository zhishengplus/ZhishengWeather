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
}
