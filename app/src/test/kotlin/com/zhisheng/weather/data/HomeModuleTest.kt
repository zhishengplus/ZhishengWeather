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
        assertTrue(HomeModule.defaultOrder.indexOf(HomeModule.DAILY) < HomeModule.defaultOrder.indexOf(HomeModule.SPACETIME))
    }

    @Test
    fun persistedLegacyDefaultMigratesWithoutOverwritingRealCustomOrders() {
        val legacyDefault = HomeModule.entries.joinToString(",") { it.key }
        assertEquals(HomeModule.defaultOrder, HomeModule.orderFrom(legacyDefault))

        val custom = HomeModule.orderFrom("daily,hourly,precip,spacetime")
        assertEquals(HomeModule.DAILY, custom.first())
        assertEquals(HomeModule.HOURLY, custom[1])
    }

    @Test
    fun upgradeInsertsSpacetimeAfterDailyWithoutLosingCustomOrder() {
        val order = HomeModule.orderFrom("aqi,precip,hourly,daily")
        val daily = order.indexOf(HomeModule.DAILY)
        assertEquals(HomeModule.SPACETIME, order[daily + 1])
        assertEquals(HomeModule.entries.toSet(), order.toSet())
    }

    @Test
    fun earlyPreviewHistoryAndRadarKeysMigrateIntoOneSpacetimeModule() {
        val order = HomeModule.orderFrom("aqi,history,radar,hourly")
        assertEquals(1, order.count { it == HomeModule.SPACETIME })
        assertEquals(HomeModule.SPACETIME, order[1])
    }
}
