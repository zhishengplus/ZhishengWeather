package com.zhisheng.weather.widget

import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.City
import org.junit.Assert.*
import org.junit.Test

class WidgetLocationContextTest {
    private val city = City("当前位置", "", 38.5, 102.2, "geo:38.500,102.200")
    @Test fun addressOnlyChangeRendersWithoutReschedulingWeatherOrReminders() {
        val label = city.copy(name = "金川区", affiliation = "甘肃省", street = "公园路", addressSource = "system")
        assertEquals(widgetWeatherContext(city, listOf(city), city.locationKey, SourcePref.AUTO, "c"),
            widgetWeatherContext(label, listOf(label), label.locationKey, SourcePref.AUTO, "c"))
    }
    @Test fun actualCoordinatesAndProviderMappingInvalidateWeatherContext() {
        val base = widgetWeatherContext(city, listOf(city), city.locationKey, SourcePref.AUTO, "c")
        val moved = city.copy(latitude = 38.6)
        assertNotEquals(base, widgetWeatherContext(moved, listOf(moved), moved.locationKey, SourcePref.AUTO, "c"))
        val mapped = city.copy(weatherLocationKey = "weathercn:101")
        assertNotEquals(base, widgetWeatherContext(mapped, listOf(mapped), mapped.locationKey, SourcePref.AUTO, "c"))
    }
}
