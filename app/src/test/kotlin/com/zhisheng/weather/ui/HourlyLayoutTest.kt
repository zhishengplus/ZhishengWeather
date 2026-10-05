package com.zhisheng.weather.ui

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.home.hourlyDisplayItems
import com.zhisheng.weather.ui.home.hourlyWindLabel
import org.junit.Assert.*
import org.junit.Test

/** Timeline behaviour; visual spacing is exercised by VistaTakeoverSnapshotTest. */
class HourlyLayoutTest {
    private val now = 1_800_000_000_000L
    @Test fun anchorAndObservationRemainSeparateAndCapacityIsBounded() {
        val forecast = (-1..30).map { HourlyWeather(now + it * 3_600_000L + 60_000L, 20.0) }
        val result = hourlyDisplayItems(CurrentWeather(temperature = 24.0), forecast, now)
        assertEquals(24, result.size)
        assertFalse(result.first().isNow)
        assertTrue(result[1].isNow)
        assertEquals(1, result.count { it.isNow })
        assertTrue(result.zipWithNext().all { (a, b) -> a.weather.timeMillis <= b.weather.timeMillis })
    }
    @Test fun missingIconDoesNotDeleteTheTemperaturePoint() {
        val result = hourlyDisplayItems(null, listOf(HourlyWeather(now + 60_000L, 19.0)), now)
        assertEquals(1, result.size)
        assertEquals(19.0, result.single().weather.temperature!!, 0.0)
        assertNull(result.single().weather.condition)
    }
    @Test fun windLabelDoesNotInventMissingWind() {
        assertNull(hourlyWindLabel(HourlyWeather(now)))
        assertTrue(hourlyWindLabel(HourlyWeather(now, windSpeed = 12.0, windDirectionDeg = 90.0))!!.contains("东"))
    }
}
