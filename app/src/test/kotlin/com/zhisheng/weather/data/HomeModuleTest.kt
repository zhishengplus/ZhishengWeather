package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModuleTest {
    @Test
    fun unifiedAtlasPreservesOtherCustomModulePositions() {
        val saved = "aqi,hourly,daily,spacetime,precip,telemetry,indices,yesterday,typhoon"
        val order = HomeModule.orderFrom(saved)
        assertEquals(saved, order.joinToString(",") { it.key })
        assertEquals(1, order.count { it == HomeModule.SPACETIME })
    }

    @Test
    fun skyCoastAndToolsMergeAtFirstSavedPosition() {
        val order = HomeModule.orderFrom("aqi,sky,hourly,coast,spacetime,daily")
        assertEquals(listOf(HomeModule.AQI, HomeModule.SPACETIME, HomeModule.HOURLY, HomeModule.DAILY), order.take(4))
        assertEquals(1, order.count { it == HomeModule.SPACETIME })
    }

    @Test
    fun customOrderIsPreservedAndMissingModulesAreAppended() {
        val order = HomeModule.orderFrom("aqi,hourly,aqi,unknown")
        assertEquals(HomeModule.AQI, order[0])
        assertTrue(order.indexOf(HomeModule.AQI) < order.indexOf(HomeModule.HOURLY))
        assertEquals(HomeModule.entries.size, order.size)
        assertEquals(HomeModule.entries.toSet(), order.toSet())
    }

    @Test
    fun emptyPreferenceUsesStableDefaultOrder() {
        assertEquals(HomeModule.defaultOrder, HomeModule.orderFrom(null))
        assertTrue(HomeModule.defaultOrder.isNotEmpty())
        assertTrue(HomeModule.defaultOrder.indexOf(HomeModule.DAILY) < HomeModule.defaultOrder.indexOf(HomeModule.SPACETIME))
        assertEquals(HomeModule.SPACETIME, HomeModule.defaultOrder[HomeModule.defaultOrder.indexOf(HomeModule.AQI) + 1])
    }

    @Test
    fun persistedLegacyDefaultMigratesWithoutOverwritingRealCustomOrders() {
        val legacyDefault = HomeModule.entries.joinToString(",") { it.key }
        assertEquals(HomeModule.defaultOrder, HomeModule.orderFrom(legacyDefault))
        assertEquals(HomeModule.defaultOrder, HomeModule.orderFrom("hourly,precip,daily,spacetime,telemetry,aqi,indices,yesterday,typhoon"))

        val custom = HomeModule.orderFrom("daily,hourly,precip,spacetime")
        assertEquals(HomeModule.DAILY, custom.first())
        assertEquals(HomeModule.HOURLY, custom[1])
    }

    @Test
    fun upgradeInsertsSpacetimeAfterAqiWithoutLosingCustomOrder() {
        val order = HomeModule.orderFrom("aqi,precip,hourly,daily")
        assertEquals(HomeModule.SPACETIME, order[order.indexOf(HomeModule.AQI) + 1])
        assertEquals(listOf(HomeModule.AQI, HomeModule.PRECIP, HomeModule.HOURLY, HomeModule.DAILY),
            order.filter { it in setOf(HomeModule.AQI, HomeModule.PRECIP, HomeModule.HOURLY, HomeModule.DAILY) })
        assertEquals(HomeModule.entries.toSet(), order.toSet())
    }

    @Test
    fun earlyPreviewHistoryAndRadarKeysMigrateIntoOneSpacetimeModule() {
        val order = HomeModule.orderFrom("aqi,history,radar,hourly")
        assertEquals(1, order.count { it == HomeModule.SPACETIME })
        assertEquals(HomeModule.SPACETIME, order[1])
    }
}
