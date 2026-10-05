package com.zhisheng.weather.ui.components

import org.junit.Assert.*
import org.junit.Test

class NaturalRainTest {
    @Test fun drizzleFallsMoreSlowlyAndPreservesTheSharedClockSeam() {
        repeat(84) { i ->
            val ordinary = rainFlight(.21, i, .52f)
            val drizzle = rainFlight(.21, i, .52f, drizzle = true)
            assertTrue(drizzle.seconds > ordinary.seconds)
            assertEquals(rainFlight(0.0, i, drizzle = true), rainFlight(1.0, i, drizzle = true))
        }
    }
    @Test fun newFlightChangesItsLaneAndDepthControlsItsSpeed() {
        repeat(100) { i ->
            val start = rainFlight(.21, i)
            val next = rainFlight(.21 + start.seconds / 72.0, i)
            assertNotEquals(start.x, next.x)
            assertEquals(start.depth, next.depth, 0f)
            assertEquals(start.progress, next.progress, .00001f)
            assertEquals(rainFlight(0.0, i), rainFlight(1.0, i))
            // 默认雨强 0.52 档：毛毛雨更慢、暴雨更快后，默认档落在 0.85–2.4 秒穿过
            assertTrue(start.seconds in .5f..2.6f)
        }
    }
    @Test fun rainHasNoClockSeamJump() {
        repeat(84) { i ->
            assertEquals(rainTravel(0.0, i), rainTravel(1.0, i), .00001f)
            val a = rainTravel(1.0 - .000001, i)
            val b = rainTravel(.000001, i)
            val delta = (b - a + 1f) % 1f
            assertTrue(delta < .0003f)
        }
    }
    @Test fun depthsAndPositionsAreStableAndBounded() {
        repeat(84) { i ->
            assertTrue(rainSeed(i, 11) in 0f..1f)
            assertEquals(rainSeed(i, 3), rainSeed(i, 3), 0f)
            assertTrue(rainTravel(.37, i) in 0f..1f)
        }
        assertTrue((0 until 84).map { rainSeed(it, 11) }.distinct().size > 80)
    }
}
