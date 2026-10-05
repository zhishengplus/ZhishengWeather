package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsMagnetTest {
    @Test fun slowReleaseSelectsNearestStop() {
        assertEquals(1, magneticTabTarget(1.4f, 0f, 3))
        assertEquals(2, magneticTabTarget(1.6f, 0f, 3))
    }
    @Test fun releaseDirectionSupportsReversalWithoutSkippingAcrossIsland() {
        assertEquals(2, magneticTabTarget(1.2f, 15f, 3))
        assertEquals(1, magneticTabTarget(1.8f, -15f, 3))
        assertEquals(3, magneticTabTarget(3.5f, 8f, 3))
        assertEquals(0, magneticTabTarget(-0.3f, -8f, 3))
    }
    @Test fun resistanceIsContinuousAndBoundedAtBothEdges() {
        assertEquals(1.2f, resistedTabPosition(1.2f, 3), 0f)
        assertEquals(0f, resistedTabPosition(0f, 3), 0f)
        assertTrue(resistedTabPosition(-100f, 3) in -0.07f..0f)
        assertTrue(resistedTabPosition(100f, 3) in 3f..3.07f)
    }
    @Test fun invalidInputNeverProducesAnInvalidSection() {
        assertEquals(0, magneticTabTarget(Float.NaN, 0f, 3))
        assertEquals(2, magneticTabTarget(2f, Float.NaN, 3))
        assertEquals(0f, resistedTabPosition(Float.POSITIVE_INFINITY, 3), 0f)
    }
}
