package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class CoordinateWeatherKeyTest {
    @Test fun providerKeyCanBeResolvedBeforeAnyAddressLabelExists() {
        val hits = listOf(XiaomiLocationResult(name = "金川区", latitude = "38.5", longitude = "102.2",
            locationKey = "weathercn:101", status = 0))
        assertEquals("weathercn:101", coordinateXiaomiKey(hits, 38.5001, 102.2001))
    }
    @Test fun stationNameMappingPreservesActualCoordinatesAndSavedIdentity() {
        val measured = measuredLocationCity(LocationFix(38.5001, 102.2001, 500L, 1L, 30f), true)
        val named = namedWeatherCity(measured, XiaomiLocationResult(name = "金川区", affiliation = "金昌市,甘肃省,中国",
            latitude = "38.0", longitude = "102.0"))
        assertEquals("甘肃省·金昌市", named.affiliation)
        assertEquals(measured.locationKey, named.locationKey)
        assertEquals(measured.latitude, named.latitude, 0.0)
        assertEquals(measured.longitude, named.longitude, 0.0)
    }
    @Test fun invalidOrDistantHitCannotAttachWeatherFromAnotherCity() {
        val hits = listOf(
            XiaomiLocationResult(latitude = null, longitude = null, locationKey = "weathercn:101", status = 0),
            XiaomiLocationResult(latitude = "39.9", longitude = "116.4", locationKey = "weathercn:102", status = 0),
            XiaomiLocationResult(latitude = "38.5", longitude = "102.2", locationKey = "geo:local:1", status = 0))
        assertNull(coordinateXiaomiKey(hits, 38.5, 102.2))
    }
}
