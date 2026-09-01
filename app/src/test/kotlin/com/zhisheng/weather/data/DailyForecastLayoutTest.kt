package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyForecastLayoutTest {
    @Test
    fun missingOrUnknownValueDefaultsToCollapsible() {
        assertEquals(DailyForecastLayout.COLLAPSIBLE, DailyForecastLayout.from(null))
        assertEquals(DailyForecastLayout.COLLAPSIBLE, DailyForecastLayout.from("unknown"))
    }

    @Test
    fun storedValuesRestoreBothLayouts() {
        assertEquals(DailyForecastLayout.COLLAPSIBLE, DailyForecastLayout.from("collapsible"))
        assertEquals(DailyForecastLayout.FULL, DailyForecastLayout.from("full"))
        assertEquals(DailyForecastLayout.CLASSIC, DailyForecastLayout.from("classic"))
    }
}
