package com.zhisheng.weather.ui.components

import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.luminance
import com.zhisheng.weather.ui.theme.PhosphorVistaLightPalette
import com.zhisheng.weather.ui.theme.PhosphorVistaDarkPalette

class NaturalLightStateTest {
    private val noon = java.time.LocalDate.of(2026, 9, 15).atTime(12, 0).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
    private fun weather(condition: WeatherCondition, wind: Double? = null, cloud: Double? = null) =
        WeatherData(current = CurrentWeather(condition = condition, windSpeed = wind, cloudCover = cloud), utcOffsetSeconds = 28800)

    @Test fun everyKnownConditionHasAnIntentionalFamily() {
        WeatherCondition.entries.filterNot { it == WeatherCondition.UNKNOWN }.forEach {
            assertNotEquals(it.name, NaturalWeather.NEUTRAL, naturalWeatherOf(it))
        }
        assertEquals(NaturalWeather.NEUTRAL, naturalWeatherOf(null))
        assertEquals(NaturalWeather.NEUTRAL, naturalWeatherOf(WeatherCondition.UNKNOWN))
    }
    @Test fun dawnAndDuskAreContinuousAndIndependentOfTheme() {
        assertEquals(0.5f, naturalDaylight("06:30", "19:00", 390, false).first, 0.001f)
        assertEquals(0.5f, naturalDaylight("06:30", "19:00", 1140, true).first, 0.001f)
        assertEquals(1f, naturalDaylight("06:30", "19:00", 720, false).first, 0.001f)
        assertEquals(0f, naturalDaylight("06:30", "19:00", 1320, true).first, 0.001f)
        val values = (365..415).map { naturalDaylight("06:30", "19:00", it, false).first }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a && b - a < 0.04f })
    }
    @Test fun invalidAstronomyUsesNightFallbackWithoutInventingEvents() {
        assertEquals(0f to 0f, naturalDaylight("bad", null, 600, true))
        assertEquals(1f to 0f, naturalDaylight("00:00", "00:00", 600, false))
    }
    @Test fun badWeatherFieldsStayFiniteAndDoNotOverrideOvercast() {
        val state = naturalLightState(weather(WeatherCondition.OVERCAST, Double.NaN, -10.0), false, 720, noon)
        assertEquals(0f, state.wind, 0.001f)
        assertTrue(state.cloud >= 0.6f)
        assertTrue(state.intensity.isFinite())
    }
    @Test fun missingRainRateDoesNotTurnOrdinaryRainIntoDrizzle() {
        val rain = naturalLightState(weather(WeatherCondition.RAIN), false, 720, noon)
        val drizzle = naturalLightState(weather(WeatherCondition.DRIZZLE), false, 720, noon)
        assertTrue(rain.intensity > drizzle.intensity)
    }
    @Test fun explicitHeatAndColdProfilesDoNotCollapseIntoOrdinaryClearSky() {
        fun state(thermal: ThermalModifier, condition: WeatherCondition = WeatherCondition.CLEAR): NaturalLightState {
            val data = weather(condition)
            return naturalLightState(data.copy(current = data.current!!.copy(
                profile = WeatherProfile(condition = condition, thermal = thermal))), false, 720, noon)
        }
        val normal = state(ThermalModifier.NONE)
        val hot = state(ThermalModifier.HOT)
        val cold = state(ThermalModifier.COLD)
        assertNotEquals(normal, hot)
        assertNotEquals(normal, cold)
        assertNotEquals(naturalLightPalette(hot, true), naturalLightPalette(cold, true))
        // Providers can report a thermal event without a known sky condition.
        assertNotEquals(state(ThermalModifier.NONE, WeatherCondition.UNKNOWN), state(ThermalModifier.HOT, WeatherCondition.UNKNOWN))
        assertEquals(NaturalWeather.NEUTRAL, state(ThermalModifier.COLD, WeatherCondition.UNKNOWN).weather)
    }
    @Test fun freezingRainRetainsAnIcyVisualDistinctionFromLiquidRain() {
        val rain = naturalLightState(weather(WeatherCondition.RAIN), false, 720, noon)
        val frozen = naturalLightState(weather(WeatherCondition.FREEZING_RAIN), false, 720, noon)
        assertTrue(frozen.freezing)
        for (light in listOf(true, false)) assertNotEquals(naturalLightPalette(rain, light).particle,
            naturalLightPalette(frozen, light).particle)
    }
    @Test fun equalIntensityDrizzleAndRainDoNotBecomeTheSameDropletField() {
        fun state(condition: WeatherCondition) = naturalLightState(WeatherData(current = CurrentWeather(
            condition = condition, profile = WeatherProfile(condition, intensity = WeatherIntensity.MODERATE))),
            false, 720, noon)
        assertNotEquals(state(WeatherCondition.RAIN), state(WeatherCondition.DRIZZLE))
        assertNotEquals(state(WeatherCondition.FREEZING_RAIN), state(WeatherCondition.FREEZING_DRIZZLE))
    }
    @Test fun allBudgetsAreBoundedAndOffHasNoParticles() {
        WeatherCondition.entries.forEach { condition ->
            val state = naturalLightState(weather(condition), true, 1320, noon)
            AmbienceLevel.entries.forEach { assertTrue(naturalParticleBudget(state, it) in 0..84) }
            assertEquals(0, naturalParticleBudget(state, AmbienceLevel.OFF))
        }
    }
    @Test fun readingAreaAndDeepScrollSuppressParticles() {
        assertEquals(0f, naturalParticleMask(0.8f, 0.85f, 0f), 0.001f)
        assertEquals(0f, naturalParticleMask(0.8f, 0.2f, 500f), 0.001f)
        assertTrue(naturalParticleMask(0.1f, 0.2f, 0f) < naturalParticleMask(0.85f, 0.2f, 0f))
    }
    @Test fun thunderGlowHasAQuietLongIntervalAndNoHardFlashEdge() {
        assertEquals(0f, naturalLightning(10.0), 0.001f)
        assertEquals(0f, naturalLightning(14.0), 0.001f)
        assertTrue((0..3600).all { naturalLightning(it / 100.0) in 0f..0.081f })
        assertTrue(kotlin.math.abs(naturalLightning(12.01) - naturalLightning(12.0)) < 0.001f)
    }
    @Test fun allBaseSkiesKeepSmallInformationTextReadableInBothThemes() {
        WeatherCondition.entries.forEach { condition ->
            for (light in listOf(true, false)) for (day in listOf(0f, 0.5f, 1f)) {
                val state = naturalLightState(weather(condition), day < 0.5f, 720, noon).copy(daylight = day, twilight = if (day == 0.5f) 1f else 0f)
                val sky = naturalLightPalette(state, light)
                val text = if (light) PhosphorVistaLightPalette else PhosphorVistaDarkPalette
                for (bg in listOf(sky.top, sky.middle, sky.base)) for (fg in listOf(text.text, text.textSecondary, text.textTertiary, text.mint, text.cyan)) {
                    val a = bg.luminance(); val b = fg.luminance()
                    val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                    assertTrue("$condition light=$light daylight=$day ratio=$ratio", ratio >= 4.5f)
                }
            }
        }
    }
}
