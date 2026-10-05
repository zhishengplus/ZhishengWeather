package com.zhisheng.weather.ui

import com.zhisheng.weather.data.*
import com.zhisheng.weather.ui.theme.vistaGlowDrift
import org.junit.Assert.*
import org.junit.Test

class ReleasePresentationTest {
    @Test fun upgradeUsesLightAndThenRespectsAnExplicitChoice() {
        assertEquals(ThemeMode.LIGHT, ReleaseFeatures.themeMode("dark", false))
        assertEquals(ThemeMode.LIGHT, ReleaseFeatures.themeMode(null, false))
        assertEquals(ThemeMode.DARK, ReleaseFeatures.themeMode("dark", true))
        assertEquals(ThemeMode.SYSTEM, ReleaseFeatures.themeMode("system", true))
        assertEquals(ThemeMode.LIGHT, ReleaseFeatures.themeMode(null, true))
        assertEquals(InterfaceStyle.CLASSIC_TERMINAL, ReleaseFeatures.interfaceStyle(InterfaceStyle.CLASSIC_TERMINAL))
        assertFalse(ReleaseFeatures.themeChoiceOnStartup)
    }

    @Test fun glowClosesItsLoopWithoutPositionOrVelocityJump() {
        val epsilon = 0.0001
        repeat(3) { layer ->
            assertEquals(vistaGlowDrift(0.0, layer).x, vistaGlowDrift(1.0, layer).x, 0.000001f)
            assertEquals(vistaGlowDrift(0.0, layer).y, vistaGlowDrift(1.0, layer).y, 0.000001f)
            val before = (vistaGlowDrift(1.0, layer) - vistaGlowDrift(1.0 - epsilon, layer)) / epsilon.toFloat()
            val after = (vistaGlowDrift(epsilon, layer) - vistaGlowDrift(0.0, layer)) / epsilon.toFloat()
            assertEquals(before.x, after.x, 0.002f)
            assertEquals(before.y, after.y, 0.002f)
            assertNotEquals(vistaGlowDrift(0.0, layer), vistaGlowDrift(0.25, layer))
        }
    }
}
