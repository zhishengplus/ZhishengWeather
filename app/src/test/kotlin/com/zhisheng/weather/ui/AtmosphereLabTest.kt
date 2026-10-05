package com.zhisheng.weather.ui

import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereLabTest {
    @Test fun partlyCloudyNightIsNotListedTwiceUnderTwoCodes() {
        assertEquals(1, atmosphereScenarios.count { it.night && it.condition in setOf(
            WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT) })
    }
    @Test fun previewTimeMatchesTheSelectedDayOrNightEvenWhenThePhoneClockDoesNot() {
        atmosphereScenarios.forEach { scenario ->
            val weather = simulatedWeather(scenario, WeatherIntensity.MODERATE)
            val hour = java.time.Instant.ofEpochMilli(weather.updateTime!!)
                .atOffset(java.time.ZoneOffset.ofHours(8)).hour
            assertEquals(scenario.minuteOfDay / 60, hour)
            if (scenario.condition.isPrecipitation) {
                assertTrue(precipitationPresentation(weather, weather.updateTime!!).points.isNotEmpty())
            }
        }
    }
    @Test
    fun everyKnownWeatherConditionCanBePreviewed() {
        val previewed = atmosphereScenarios.map { it.condition }.toSet()
        val expected = WeatherCondition.entries.toSet()
        assertEquals(expected, previewed)
    }

    @Test
    fun simulationBuildsSelfContainedWeatherWithoutRealProviderIdentity() {
        atmosphereScenarios.forEach { scenario ->
            val data = simulatedWeather(scenario, WeatherIntensity.HEAVY)
            assertEquals("SIMULATION", data.dataSource)
            assertEquals(scenario.condition, data.current?.condition)
            assertNotNull(data.current?.profile)
            assertTrue(data.hourly.isNotEmpty())
            assertTrue(data.daily.isNotEmpty())
        }
    }
}
