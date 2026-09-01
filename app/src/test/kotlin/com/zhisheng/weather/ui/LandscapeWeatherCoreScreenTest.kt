package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeWeatherCoreScreenTest {
    @Test
    fun sunTrackUsesRealSunriseAndSunsetWindow() {
        assertEquals(0f, sunTrackProgress("06:00", "18:00", 5 * 60), 0.001f)
        assertEquals(0.5f, sunTrackProgress("06:00", "18:00", 12 * 60), 0.001f)
        assertEquals(1f, sunTrackProgress("06:00", "18:00", 20 * 60), 0.001f)
    }

    @Test
    fun invalidSunTimesHaveStableNeutralFallback() {
        assertEquals(0.5f, sunTrackProgress(null, "18:00", 12 * 60), 0.001f)
        assertEquals(0.5f, sunTrackProgress("18:00", "06:00", 12 * 60), 0.001f)
        assertEquals(0.5f, sunTrackProgress("25:00", "18:00", 12 * 60), 0.001f)
    }
}
