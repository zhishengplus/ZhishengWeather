package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeStandbyStyleTest {
    @Test
    fun newWeatherCoreIsDefaultWhileClassicRemainsSelectable() {
        assertEquals(LandscapeStandbyStyle.WEATHER_CORE, LandscapeStandbyStyle.from(null))
        assertEquals(LandscapeStandbyStyle.WEATHER_CORE, LandscapeStandbyStyle.from("unknown"))
        assertEquals(LandscapeStandbyStyle.CLASSIC, LandscapeStandbyStyle.from("classic"))
        assertEquals(LandscapeStandbyStyle.WEATHER_CORE, LandscapeStandbyStyle.from("weather_core"))
    }
}
