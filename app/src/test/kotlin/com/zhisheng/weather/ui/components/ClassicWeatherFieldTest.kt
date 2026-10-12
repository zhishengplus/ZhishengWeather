package com.zhisheng.weather.ui.components

import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.ThermalModifier
import org.junit.Assert.*
import org.junit.Test

class ClassicWeatherFieldTest {
    private fun state(kind: NaturalWeather) = NaturalLightState(kind, 1f, 0f, .5f, .5f, 0f, false)
    @Test fun enabledKnownWeatherHasBoundedFeedbackAndOffDrawsNothing() {
        NaturalWeather.entries.filter { it != NaturalWeather.NEUTRAL }.forEach { weather ->
            assertTrue(classicWeatherMarks(state(weather), AmbienceLevel.SUBTLE) in 1..40)
            assertEquals(0, classicWeatherMarks(state(weather), AmbienceLevel.OFF))
        }
        assertEquals(0, classicWeatherMarks(state(NaturalWeather.NEUTRAL), AmbienceLevel.INTENSE))
    }
    @Test fun vividHasMoreMarksThanSubtleWithoutUnboundedParticles() {
        val rain = state(NaturalWeather.RAIN)
        assertTrue(classicWeatherMarks(rain, AmbienceLevel.VIVID) > classicWeatherMarks(rain, AmbienceLevel.SUBTLE))
        assertTrue(classicWeatherMarks(rain, AmbienceLevel.INTENSE) <= 40)
    }
    @Test fun unknownSkyKeepsReportedHotAndColdFeedbackWithoutInventingWeather() {
        listOf(ThermalModifier.HOT, ThermalModifier.COLD).forEach { thermal ->
            val reported = state(NaturalWeather.NEUTRAL).copy(thermal = thermal)
            assertTrue(classicWeatherMarks(reported, AmbienceLevel.SUBTLE) in 1..40)
            assertEquals(0, classicWeatherMarks(reported, AmbienceLevel.OFF))
            assertEquals(NaturalWeather.NEUTRAL, reported.weather)
        }
    }
}
