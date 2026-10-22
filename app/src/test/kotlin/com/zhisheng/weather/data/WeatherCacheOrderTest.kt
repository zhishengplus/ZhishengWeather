package com.zhisheng.weather.data

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.*
import org.junit.Test

class WeatherCacheOrderTest {
    private val now = 2_000_000_000_000L
    private fun weather(at: Long?, temperature: Double = 20.0) =
        WeatherData(current = CurrentWeather(temperature = temperature), fetchedAt = at)

    @Test fun olderConcurrentResponseCannotReplaceANewerSuccess() {
        assertFalse(shouldReplaceWeatherCache(weather(now - 1_000), weather(now - 2_000), now))
        assertTrue(shouldReplaceWeatherCache(weather(now - 2_000), weather(now - 1_000), now))
        assertFalse(shouldReplaceWeatherCache(weather(now - 1_000), weather(null), now))
    }

    @Test fun clockRollbackLetsNewSuccessReplaceTheFutureOldSnapshot() {
        for (rollback in listOf(1L, 60_000L, 3_600_000L)) {
            assertTrue(shouldReplaceWeatherCache(weather(now + rollback), weather(now), now))
            assertFalse(shouldReplaceWeatherCache(weather(now), weather(now + rollback), now))
        }
    }

    @Test fun lateResponseFromBeforeClockCorrectionCannotPoisonANewClockCache() {
        assertFalse(shouldReplaceWeatherCache(null, weather(now + 3_600_000L), now))
        assertFalse(shouldReplaceWeatherCache(weather(now + 3_600_000L), weather(now + 3_590_000L), now))
    }

    @Test fun snapshotSelectionPrefersTheCorrectedClockAndStillKeepsNormalNewestOrder() {
        val oldClock = weather(now + 3_600_000L, 30.0)
        val fresh = weather(now, 10.0)
        val older = weather(now - 60_000L, 9.0)
        assertEquals(fresh, latestWeatherCache(listOf(oldClock, fresh, older), now))
        assertEquals(fresh, latestWeatherCache(listOf(older, fresh), now))
        assertEquals(oldClock, latestWeatherCache(listOf(oldClock), now))
        assertNull(latestWeatherCache(emptyList(), now))
    }
}
