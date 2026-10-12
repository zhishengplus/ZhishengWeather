package com.zhisheng.weather.ui

import com.zhisheng.weather.data.*
import org.junit.Assert.*
import org.junit.Test

class WeatherSourceNavigationTest {
    private val all = SourcePref.entries.toSet()

    @Test fun backFromModuleSelectionReturnsToModulesBeforeClosing() {
        val modules = WeatherSourceNavigation(modules = true)
        assertEquals(modules, modules.edit(WeatherModule.CURRENT).back())
        assertNull(modules.back())
        assertNull(WeatherSourceNavigation().back())
    }

    @Test fun followPrimaryIsAvailableEvenWhenNoProviderIsConfigured() {
        val choices = weatherSourceChoices(WeatherModule.CURRENT, all, emptySet(), SourcePref.CAIYUN)
        assertEquals(WeatherSourceChoice(null, true), choices.first())
        assertTrue(choices.drop(1).none { it.enabled })
    }

    @Test fun moduleChoicesRespectActualCapabilities() {
        val minutely = weatherSourceChoices(WeatherModule.MINUTELY, all, all, null).map { it.source }
        assertFalse(minutely.contains(SourcePref.AMAP))
        assertFalse(minutely.contains(SourcePref.NMC))
        val alerts = weatherSourceChoices(WeatherModule.ALERTS, all, all, null).map { it.source }
        assertFalse(alerts.contains(SourcePref.OPEN_METEO))
        val forecast = weatherSourceChoices(WeatherModule.FORECAST, all, all, null)
        assertTrue(forecast.contains(WeatherSourceChoice(SourcePref.AMAP, true)))
    }

    @Test fun unavailableSavedChoiceRemainsVisibleAndDisabled() {
        val choices = weatherSourceChoices(WeatherModule.CURRENT, SourcePref.visible(false).toSet(),
            setOf(SourcePref.AUTO), SourcePref.QWEATHER)
        assertTrue(choices.contains(WeatherSourceChoice(SourcePref.QWEATHER, false)))
        assertFalse(choices.any { it.source == SourcePref.BAIDU })
    }

    @Test fun unsupportedSavedChoiceCannotBeSelectedAgain() {
        val choices = weatherSourceChoices(WeatherModule.MINUTELY, all, all, SourcePref.AMAP)
        assertTrue(choices.contains(WeatherSourceChoice(SourcePref.AMAP, false)))
        assertEquals(1, choices.count { it.source == SourcePref.AMAP })
    }

    @Test fun primaryChoicesNeverContainModuleFollowOption() {
        val choices = weatherSourceChoices(null, all, all, SourcePref.CAIYUN)
        assertFalse(choices.any { it.source == null })
        assertTrue(choices.contains(WeatherSourceChoice(SourcePref.CAIYUN, true)))
    }
}
