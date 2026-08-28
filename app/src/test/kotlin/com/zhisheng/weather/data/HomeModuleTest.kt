package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModuleTest {
    @Test
    fun customOrderIsPreservedAndMissingModulesAreAppended() {
        val order = HomeModule.orderFrom("aqi,hourly,aqi,unknown")
        assertEquals(HomeModule.AQI, order[0])
        assertEquals(HomeModule.HOURLY, order[1])
        assertEquals(HomeModule.entries.size, order.size)
        assertEquals(HomeModule.entries.toSet(), order.toSet())
    }

    @Test
    fun emptyPreferenceUsesStableDefaultOrder() {
        assertEquals(HomeModule.defaultOrder, HomeModule.orderFrom(null))
        assertTrue(HomeModule.defaultOrder.isNotEmpty())
    }

    @Test
    fun upgradeInsertsSpacetimeAfterPrecipitationWithoutLosingCustomOrder() {
        val order = HomeModule.orderFrom("aqi,precip,hourly,daily")
        val precip = order.indexOf(HomeModule.PRECIP)
        assertEquals(HomeModule.SPACETIME, order[precip + 1])
        assertEquals(HomeModule.entries.toSet(), order.toSet())
    }

    @Test
    fun earlyPreviewHistoryAndRadarKeysMigrateIntoOneSpacetimeModule() {
        val order = HomeModule.orderFrom("aqi,history,radar,hourly")
        assertEquals(1, order.count { it == HomeModule.SPACETIME })
        assertEquals(HomeModule.SPACETIME, order[1])
    }
}
