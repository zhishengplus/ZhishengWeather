package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import org.junit.Assert.*
import org.junit.Test

class PlaceResolutionCacheTest {
    private val city = City("金川区", "甘肃省", 38.5, 102.2, "unused", street = "公园路")

    @Test fun cacheSeparatesCredentialAndPermissionAndNeverRenewsOnRead() {
        var now = 0L
        val cache = PlaceResolutionCache(clock = { now }, ttlMillis = 100)
        cache.put("system:fine", 38.5, 102.2, city)
        assertEquals(city, cache.get("system:fine", 38.5, 102.2))
        assertNull(cache.get("system:coarse", 38.5, 102.2))
        assertNull(cache.get("amap:new-key", 38.5, 102.2))
        now = 99
        assertEquals(city, cache.get("system:fine", 38.5, 102.2))
        now = 101
        assertNull(cache.get("system:fine", 38.5, 102.2))
    }

    @Test fun cacheCannotTransferStreetToNearbyRoadOrDistrict() {
        val cache = PlaceResolutionCache()
        cache.put("system", 38.5, 102.2, city)
        assertNull(cache.get("system", 38.501, 102.2))
        assertEquals(city, cache.get("system", 38.500001, 102.2))
    }

    @Test fun labelUpdatePreservesFavoriteCoordinatesAndProviderIdentity() {
        val measured = measuredLocationCity(LocationFix(38.5, 102.2, 500, 1, 20f), true)
            .copy(isFavorite = true, weatherLocationKey = "provider-id")
        val label = city.copy(latitude = 0.0, longitude = 0.0, addressSource = "system")
        val updated = updateMeasuredLabel(measured, measured.copy(name = label.name, street = label.street, addressSource = label.addressSource))!!
        assertTrue(updated.isFavorite)
        assertEquals(38.5, updated.latitude, 0.0)
        assertEquals("provider-id", updated.weatherLocationKey)
        assertNull(updateMeasuredLabel(measured, label))
        assertNull(updateMeasuredLabel(measured, measured.copy(latitude = 38.5001)))
    }

    @Test fun failedCredentialDoesNotDisableOtherKeyOrCapability() {
        val key = "test-key-unique"
        PlaceServiceGate.record(key, "reverse", PlaceFailure.AUTH)
        try { PlaceServiceGate.check(key, "reverse"); fail("must cool down") }
        catch (expected: PlaceServiceException) { assertEquals(PlaceFailure.AUTH, expected.failure) }
        PlaceServiceGate.check("different-test-key", "reverse")
        PlaceServiceGate.check(key, "search")
        PlaceServiceGate.clear(key, "reverse")
        PlaceServiceGate.check(key, "reverse")
    }
}
