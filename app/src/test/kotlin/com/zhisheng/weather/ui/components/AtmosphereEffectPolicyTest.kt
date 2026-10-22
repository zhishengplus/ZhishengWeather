package com.zhisheng.weather.ui.components

import com.zhisheng.weather.model.ThermalModifier
import org.junit.Assert.*
import org.junit.Test

class AtmosphereEffectPolicyTest {
    @Test fun borrowedShaderTimeContinuesThroughTheSharedPhaseSeam() {
        val clock = AtmosphereEffectClock()
        val before = clock.seconds(.99, true)
        assertEquals(before + 1.44, clock.seconds(.01, true), .000001)
        assertEquals(before + 2.16, clock.seconds(.02, true), .000001)
    }

    @Test fun staticAndHiddenTimeIsNotReplayedWhenTheEffectResumes() {
        val clock = AtmosphereEffectClock()
        val first = clock.seconds(.2, true)
        val visible = clock.seconds(.3, true)
        assertEquals(first + 7.2, visible, .000001)
        assertEquals(visible, clock.seconds(.6, false), .000001)
        assertEquals(visible, clock.seconds(.9, true), .000001)
        assertEquals(visible + .72, clock.seconds(.91, true), .000001)
    }

    @Test fun ordinaryDryAndMissingWeatherNeverGainRainOrSnow() {
        for (weather in listOf(NaturalWeather.CLEAR, NaturalWeather.CLOUDS, NaturalWeather.OVERCAST,
            NaturalWeather.FOG, NaturalWeather.HAZE, NaturalWeather.DUST, NaturalWeather.WIND, NaturalWeather.ICE, NaturalWeather.NEUTRAL))
            assertEquals(WeatherGlassKind.NONE, weatherGlassKind(state(weather)))
        assertEquals(WeatherGlassKind.RAIN, weatherGlassKind(state(NaturalWeather.RAIN)))
        assertEquals(WeatherGlassKind.RAIN, weatherGlassKind(state(NaturalWeather.STORM)))
        assertEquals(WeatherGlassKind.SNOW, weatherGlassKind(state(NaturalWeather.SNOW)))
        assertEquals(WeatherGlassKind.SNOW, weatherGlassKind(state(NaturalWeather.MIXED)))
        assertEquals(WeatherGlassKind.FROST, weatherGlassKind(state(NaturalWeather.RAIN).copy(freezing = true)))
        assertEquals(WeatherGlassKind.NONE, weatherGlassKind(state(NaturalWeather.CLEAR).copy(thermal = ThermalModifier.COLD)))
        assertEquals(WeatherGlassKind.NONE, weatherGlassKind(state(NaturalWeather.NEUTRAL).copy(thermal = ThermalModifier.COLD)))
    }

    @Test fun rainfallStrengthKeepsTheObservedRangeAndRejectsNonfiniteInputs() {
        assertEquals(2.5f, rainVeilIntensity(.28f), .0001f)
        assertEquals(4.5f, rainVeilIntensity(.6f), .0001f)
        assertEquals(5f, rainVeilIntensity(1f), .0001f)
        for (value in listOf(-2f, 2f, Float.NaN, Float.POSITIVE_INFINITY))
            assertTrue(rainVeilIntensity(value) in 2.5f..5f)
    }

    @Test fun effectSnapshotsHaveBoundedTickRatesAndStaticTimeIsStable() {
        assertEquals(30L, atmosphereTimeTick(101.0, SkyRenderQuality.HIGH) - atmosphereTimeTick(100.0, SkyRenderQuality.HIGH))
        assertEquals(20L, atmosphereTimeTick(101.0, SkyRenderQuality.SIMPLIFIED) - atmosphereTimeTick(100.0, SkyRenderQuality.SIMPLIFIED))
        val clock = AtmosphereEffectClock()
        val first = atmosphereTimeTick(clock.seconds(.2, false), SkyRenderQuality.HIGH)
        assertEquals(first, atmosphereTimeTick(clock.seconds(.8, false), SkyRenderQuality.HIGH))
    }

    private fun state(weather: NaturalWeather) = NaturalLightState(weather, 1f, 0f, .5f, .5f, .2f, false)
}
