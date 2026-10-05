package com.zhisheng.weather.widget

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.*
import org.junit.Test

class WidgetPolicyTest {
    private val a = City("杭州", "浙江", 30.0, 120.0, "a")
    private val b = City("金昌", "甘肃", 38.0, 102.0, "b")
    @Test fun fixedCitySurvivesRemovalAndDoesNotFollowSelection() {
        val cfg = WidgetConfig(locationMode = "fixed", cityKey = "a", fixedCity = a)
        assertEquals(a, resolveWidgetCity(cfg, b, listOf(b), "b"))
        assertEquals(a.copy(name = "新名称"), resolveWidgetCity(cfg, b, listOf(a.copy(name = "新名称"), b), "b"))
    }
    @Test fun locationFollowsLocatedSlotAndNeverSubstitutesSelectedCity() {
        val cfg = WidgetConfig(locationMode = "located")
        assertEquals(b, resolveWidgetCity(cfg, a, listOf(a, b), "b"))
        assertNull(resolveWidgetCity(cfg, a, listOf(a, b), null))
        assertNull(resolveWidgetCity(cfg, a, listOf(a), "b"))
    }
    @Test fun currentCityModeTracksSelection() {
        assertEquals(b, resolveWidgetCity(WidgetConfig(), b, listOf(a, b), "a"))
    }
    @Test fun staleWeatherIsNotPresentedAsFreshAfterClockOrSourceChange() {
        val now = 100_000_000L
        assertTrue(widgetStale(null, now, 30))
        assertTrue(widgetStale(WeatherData(fetchedAt = now + 600_000), now, 30))
        assertTrue(widgetStale(WeatherData(fetchedAt = now, updateTime = now - 4 * 3_600_000), now, 30))
        assertFalse(widgetStale(WeatherData(fetchedAt = now - 1_800_000, updateTime = now), now, 30))
    }
    @Test fun invalidSettingsCannotCreateUnboundedLayoutsOrDuplicateMetrics() {
        val cfg = WidgetConfig(opacity = -1, textScale = 100f, intervalMinutes = 1, iconSet = "unknown", metrics = listOf("wind", "wind", "bogus", "air", "feels", "uv")).normalized()
        assertEquals(0, cfg.opacity); assertEquals(1.25f, cfg.textScale); assertEquals(30, cfg.intervalMinutes)
        assertEquals(WidgetIconSet.WUJIE.key, cfg.iconSet)
        assertEquals(listOf("wind", "air", "feels", "uv"), cfg.metrics)
    }
    @Test fun beta9DefaultsToWujieIconSet() = assertEquals(WidgetIconSet.WUJIE.key, WidgetConfig.defaults(WidgetStyle.VISTA).iconSet)
    @Test fun newWidgetsOpenWeatherByDefaultAndPreserveExplicitEditChoice() {
        for (style in WidgetStyle.entries) assertEquals("app", WidgetConfig.defaults(style).clickWeather)
        assertEquals("settings", WidgetConfig(clickWeather = "settings").normalized().clickWeather)
        assertEquals("settings", migrateWeatherClick(WidgetConfig(clickWeather = "settings"), false).clickWeather)
        assertEquals("settings", migrateWeatherClick(WidgetConfig(clickWeather = "settings"), true).clickWeather)
        assertEquals("none", migrateWeatherClick(WidgetConfig(clickWeather = "none"), false).clickWeather)
        assertEquals("settings", WidgetConfig(clickTime = "settings", clickDate = "settings").normalized().clickTime)
        assertEquals("settings", WidgetConfig(clickTime = "settings", clickDate = "settings").normalized().clickDate)
        assertEquals("app", WidgetConfig(clickWeather = "invalid").normalized().clickWeather)
    }
    @Test fun switchingWidgetKindsNeverMovesTheEditorControls() {
        assertEquals(280, previewStageHeight(false))
        assertEquals(340, previewStageHeight(true))
    }
}
