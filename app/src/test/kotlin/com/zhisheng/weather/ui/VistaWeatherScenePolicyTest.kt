package com.zhisheng.weather.ui

import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class VistaWeatherScenePolicyTest {
    @Test fun offAndNoneAllocateNothing() {
        assertEquals(0, sceneParticleCount(AmbienceKind.RAIN, AmbienceLevel.OFF))
        assertEquals(0, sceneParticleCount(AmbienceKind.NONE, AmbienceLevel.INTENSE))
    }

    @Test fun cloudAndAtmosphericKindsCarryNoParticlesTheirShapeIsTheScene() {
        assertEquals(0, sceneParticleCount(AmbienceKind.CLEAR_DAY, AmbienceLevel.INTENSE))
        assertEquals(0, sceneParticleCount(AmbienceKind.PARTLY_CLOUDY, AmbienceLevel.INTENSE))
        assertEquals(0, sceneParticleCount(AmbienceKind.OVERCAST, AmbienceLevel.INTENSE))
        assertEquals(0, sceneParticleCount(AmbienceKind.FOG, AmbienceLevel.VIVID))
        assertEquals(0, sceneParticleCount(AmbienceKind.HAZE, AmbienceLevel.SUBTLE))
    }

    @Test fun windNowHasItsOwnStreaks() {
        assertTrue(sceneParticleCount(AmbienceKind.WIND, AmbienceLevel.VIVID) > 0)
    }

    @Test fun userLevelIsTheMainDial() {
        val counts = listOf(AmbienceLevel.SUBTLE, AmbienceLevel.VIVID, AmbienceLevel.INTENSE)
            .map { sceneParticleCount(AmbienceKind.RAIN, it, WeatherIntensity.MODERATE) }
        assertTrue(counts[0] in 1 until counts[1])
        assertTrue(counts[1] < counts[2])
    }

    @Test fun weatherIntensityFineTunesDensity() {
        val counts = WeatherIntensity.entries.map {
            sceneParticleCount(AmbienceKind.RAIN, AmbienceLevel.INTENSE, it)
        }
        assertTrue(counts.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test fun budgetStaysCappedForEveryKindAndLevel() {
        AmbienceKind.entries.forEach { kind ->
            AmbienceLevel.entries.forEach { level ->
                WeatherIntensity.entries.forEach { intensity ->
                    assertTrue(sceneParticleCount(kind, level, intensity) in 0..SCENE_PARTICLE_CAP)
                }
            }
        }
    }

    @Test fun rainTiltsWithWindAndStaysBounded() {
        assertEquals(0f, rainTiltDp(0f, 270f))
        assertTrue(rainTiltDp(40f, 270f) > 0f)   // 西风 → 雨丝向东倒
        assertTrue(rainTiltDp(40f, 90f) < 0f)    // 东风 → 雨丝向西倒
        assertTrue(kotlin.math.abs(rainTiltDp(120f, 270f)) <= 26f)
        assertEquals(0f, rainTiltDp(40f, 0f), 0.0001f) // 正北风无东西分量
    }

    @Test fun maskProtectsStatusBarAndBottomEdgeOnly() {
        listOf(640f, 800f, 915f).forEach { height ->
            assertEquals(0f, sceneMaskY(0f, height))
            assertEquals(1f, sceneMaskY(height * 0.5f, height))
            assertTrue(sceneMaskY(height - 4f, height) < 0.2f)
        }
        assertEquals(0f, sceneMaskY(100f, 0f))
    }

    @Test fun stormFlashIsDeterministicAndPeriodic() {
        val phase = 0.25
        val cycle = 5.4 + phase * 3.4
        assertTrue(stormFlashAlpha(2.05, phase) > 0f)
        assertEquals(0f, stormFlashAlpha(3.5, phase))
        assertEquals(stormFlashAlpha(2.05, phase), stormFlashAlpha(2.05 + cycle, phase), 0.0001f)
        assertFalse(stormBoltVisible(3.0, phase))
    }

    @Test fun reducedMotionAndPowerSaveStopTheClock() {
        assertFalse(sceneMotionAllowed(AmbienceKind.RAIN, animationsEnabled = false, powerSave = false))
        assertFalse(sceneMotionAllowed(AmbienceKind.RAIN, animationsEnabled = true, powerSave = true))
        assertTrue(sceneMotionAllowed(AmbienceKind.RAIN, animationsEnabled = true, powerSave = false))
        assertFalse(sceneMotionAllowed(AmbienceKind.NONE, animationsEnabled = true, powerSave = false))
    }
}
