package com.zhisheng.weather.ui.components

import org.junit.Assert.*
import org.junit.Test

class SkyRenderPolicyTest {
    private val capable = SkyRenderCapabilities(apiLevel = 34)
    private fun quality(capabilities: SkyRenderCapabilities = capable, active: Boolean = true, enabled: Boolean = true) =
        skyRenderQuality(capabilities, active, enabled)

    @Test fun capableForegroundKeepsTheOriginalCloudQuality() {
        assertEquals(SkyRenderQuality.HIGH, quality())
        assertEquals(.5f, quality().resolutionScale, 0f)
        assertEquals(1f, quality().particleScale, 0f)
    }

    @Test fun lowMemoryOlderApisAndShaderFailureHaveCompatibleBudgets() {
        for (capabilities in listOf(capable.copy(apiLevel = 26), capable.copy(apiLevel = 32),
            capable.copy(lowMemory = true), capable.copy(shaderAvailable = false))) {
            assertEquals(SkyRenderQuality.SIMPLIFIED, quality(capabilities))
            assertTrue(quality(capabilities).resolutionScale < SkyRenderQuality.HIGH.resolutionScale)
            assertTrue(quality(capabilities).particleScale < SkyRenderQuality.HIGH.particleScale)
        }
    }

    @Test fun powerSaveDisabledAnimationsAndCoveredPagesAreStatic() {
        assertEquals(SkyRenderQuality.STATIC, quality(capable.copy(powerSave = true)))
        assertEquals(SkyRenderQuality.STATIC, quality(capable.copy(animationsEnabled = false)))
        assertEquals(SkyRenderQuality.STATIC, quality(active = false))
        assertEquals(SkyRenderQuality.STATIC, quality(enabled = false))
        assertEquals(SkyRenderQuality.STATIC, quality(capable.copy(lowMemory = true, shaderAvailable = false, powerSave = true)))
    }

    @Test fun pagingEasesDensityWithoutSwitchingTheRenderer() {
        assertEquals(1f, skyPagingParticleScale(0f, true), .0001f)
        assertEquals(1f, skyPagingParticleScale(1f, true), .0001f)
        assertEquals(.65f, skyPagingParticleScale(.5f, true), .0001f)
        for (step in 0..100) {
            val f = step / 100f
            assertEquals(skyPagingParticleScale(f, true), skyPagingParticleScale(1f - f, true), .0001f)
            assertEquals(1f, skyPagingParticleScale(f, false), 0f)
            assertTrue(skyPagingParticleScale(f, true) in .65f..1f)
        }
    }

    @Test fun badProgressCannotPoisonDrawing() {
        for (f in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -4f, 8f))
            assertTrue(skyPagingParticleScale(f, true).isFinite())
    }

    @Test fun cloudCacheBudgetsSlowDownOnlyExpensiveCloudRecordings() {
        assertEquals(0L, skyCloudFrameIntervalNanos(SkyRenderQuality.HIGH, false))
        assertTrue(skyCloudFrameIntervalNanos(SkyRenderQuality.HIGH, true) >= 33_000_000L)
        assertEquals(50_000_000L, skyCloudFrameIntervalNanos(SkyRenderQuality.SIMPLIFIED, true))
        assertEquals(Long.MAX_VALUE, skyCloudFrameIntervalNanos(SkyRenderQuality.STATIC, false))
    }

    private fun frame(kind: NaturalWeather, cloud: Float, daylight: Float): NaturalSkyFrame {
        val state = NaturalLightState(kind, daylight, 0f, .5f, cloud, .2f, false)
        return NaturalSkyFrame(state, naturalLightPalette(state, false))
    }

    @Test fun allContinuousSkyFieldsAndWeatherWeightsRemainBounded() {
        val clear = frame(NaturalWeather.CLEAR, 0f, 1f)
        val storm = frame(NaturalWeather.STORM, 1f, 0f)
        for (step in 0..100) {
            val f = step / 100f
            val mixed = clear.mix(storm, f)
            assertEquals(f, mixed.state.cloud, .0001f)
            assertEquals(1f - f, mixed.state.daylight, .0001f)
            assertEquals(1f, mixed.weatherWeights.sum(), .0001f)
            assertTrue(mixed.weatherWeights.all { it in 0f..1f })
            assertEquals(f, mixed.weatherWeights[NaturalWeather.STORM.ordinal], .0001f)
        }
    }

    @Test fun interruptedDragAndReverseStartAtTheExactDisplayedSky() {
        val clear = frame(NaturalWeather.CLEAR, .1f, 1f)
        val storm = frame(NaturalWeather.STORM, .9f, .2f)
        val snow = frame(NaturalWeather.SNOW, .7f, .6f)
        val displayed = clear.mix(storm, .63f)
        assertEquals(displayed, displayed.mix(clear, 0f))
        assertEquals(displayed, displayed.mix(snow, 0f))
        val reversed = displayed.mix(clear, .25f)
        assertEquals(.63f * .75f, reversed.weatherWeights[NaturalWeather.STORM.ordinal], .0001f)
        assertEquals(1f, reversed.weatherWeights.sum(), .0001f)
    }

    @Test fun adjacentSegmentsShareAnIdenticalEndpointAndInvalidFractionsHold() {
        val clear = frame(NaturalWeather.CLEAR, .1f, 1f)
        val storm = frame(NaturalWeather.STORM, .9f, .2f)
        val snow = frame(NaturalWeather.SNOW, .7f, .6f)
        assertEquals(clear.mix(storm, 1f), storm.mix(snow, 0f))
        assertEquals(clear, clear.mix(storm, Float.NaN))
        assertEquals(clear, clear.mix(storm, -1f))
        assertEquals(storm, clear.mix(storm, 2f))
    }

    @Test fun pauseKeepsTheLastVisiblePhaseAndResumeDoesNotCatchUpHiddenTime() {
        val clock = SkyPhaseClock(.2)
        assertEquals(.3, clock.sample(.3, true), .000001)
        clock.pause()
        assertEquals(.3, clock.sample(.8, false), .000001)
        assertEquals(.3, clock.sample(.95, true), .000001)
        assertEquals(.34, clock.sample(.99, true), .000001)
        assertEquals(.36, clock.sample(.01, true), .000001)
    }

    @Test fun staticDefaultDoesNotRemoveWeatherEffectsOrChangeTheirDensity() {
        assertEquals(SkyRenderQuality.HIGH.particleScale, SkyRenderQuality.STATIC.particleScale, 0f)
        assertEquals(SkyRenderQuality.HIGH.resolutionScale, SkyRenderQuality.STATIC.resolutionScale, 0f)
    }
}
