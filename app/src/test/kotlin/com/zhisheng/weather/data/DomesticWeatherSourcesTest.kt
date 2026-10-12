package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class DomesticWeatherSourcesTest {
    @Test fun domesticSourcesHaveIndependentPersistentIdentities() {
        listOf("tencent", "weathercn", "seniverse", "moji").forEach { key ->
            assertEquals(key, SourcePref.from(key).key)
            assertTrue(SourcePref.from(key).matches(SourcePref.from(key).en))
        }
    }
    @Test fun onlyPublicSourcesAreVisibleWithoutPersonalCredentialsMode() {
        val keys = SourcePref.visible(false).map { it.key }
        assertTrue("tencent" in keys)
        assertTrue("weathercn" in keys)
        assertFalse("moji" in keys)
        assertFalse("seniverse" in keys)
        assertEquals(SourcePref.AUTO, SourcePref.from("moji").effective(false))
    }
    @Test fun dailyOnlySourcesCannotOfferMadeUpHourlyOrMinutelyData() {
        listOf("tencent", "weathercn", "seniverse", "moji").forEach { key ->
            val source = SourcePref.from(key)
            assertFalse(WeatherSourceCapabilities.map.getValue(source).hourly)
            assertFalse(WeatherSourceCapabilities.supports(source, WeatherModule.MINUTELY))
        }
    }
}
