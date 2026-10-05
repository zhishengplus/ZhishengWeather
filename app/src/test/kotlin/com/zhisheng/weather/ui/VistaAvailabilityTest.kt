package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.AtlasPage
import com.zhisheng.weather.ui.home.vistaScenePages
import org.junit.Assert.*
import org.junit.Test

class VistaAvailabilityTest {
    private val sky = SkyPhotographyForecast(
        glow = GlowForecast(GlowKind.DUSK, "值得留意", 1000, 2000, emptyList()),
        stars = null, updatedAtMillis = 500,
    )
    @Test fun missingDataDoesNotCreatePlaceholderModules() {
        assertTrue(vistaScenePages(null, DisplayPrefs()).isEmpty())
        assertTrue(vistaScenePages(SceneWeatherData(skyError = "network"), DisplayPrefs()).isEmpty())
    }
    @Test fun partialDataKeepsOnlyUsefulModules() {
        assertEquals(setOf(AtlasPage.GLOW), vistaScenePages(SceneWeatherData(sky = sky), DisplayPrefs()))
    }
    @Test fun staleDataStaysAvailableWithoutInventingOtherContent() {
        val scene = SceneWeatherData(sky = sky.copy(stale = true), stale = true, coastError = "network")
        assertEquals(setOf(AtlasPage.GLOW), vistaScenePages(scene, DisplayPrefs()))
        assertFalse(AtlasPage.DARK in vistaScenePages(scene, DisplayPrefs()))
    }
    @Test fun userDisabledModulesStayHiddenEvenWithData() {
        assertTrue(vistaScenePages(SceneWeatherData(sky = sky), DisplayPrefs(showSkyPhotography = false)).isEmpty())
    }
    @Test fun cachedStarsStayDormantWhileGlowRemainsAvailable() {
        val cachedSky = sky.copy(stars = StarForecast(75, "较好", 1000, 2000, emptyList(), "参考"))
        assertEquals(setOf(AtlasPage.GLOW), vistaScenePages(SceneWeatherData(sky = cachedSky), DisplayPrefs(showSkyPhotography = true)))
        assertNotNull(cachedSky.stars)
    }
}
