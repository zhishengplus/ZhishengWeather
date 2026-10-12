package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FreeLocationPolicyTest {
    private val fix = LocationFix(38.520123, 102.188765, 1_800_000_000_000L, 100_000_000L, 30f)

    @Test fun fiveHundredMeterFixCannotClaimStreetPrecision() {
        assertFalse(LocationFixPolicy.canResolveStreet(fix.copy(accuracyMeters = 500f)))
        assertFalse(LocationFixPolicy.canResolveStreet(fix.copy(accuracyMeters = 0f)))
        assertTrue(LocationFixPolicy.canResolveStreet(fix))
    }

    @Test fun permissionWithoutMeasurementAccuracyDoesNotMeanPrecise() {
        val city = measuredLocationCity(fix.copy(accuracyMeters = null), preciseGranted = true)
        assertFalse(city.hasMeasuredPrecision)
        assertEquals(CoordinateKind.DEVICE, city.coordinateKind)
    }

    @Test fun parsedAdministrativeCenterCannotReplaceDeviceCoordinates() {
        val measured = measuredLocationCity(fix, preciseGranted = true)
        val center = City("金川区", "甘肃省·金昌市", 38.0, 102.0, "weathercn:101")
        val enriched = applyResolvedLabel(measured, center, "公园路", "system")
        assertEquals(38.520123, enriched.latitude, 0.0)
        assertEquals(102.188765, enriched.longitude, 0.0)
        assertEquals(measured.locationKey, enriched.locationKey)
        assertNull(enriched.weatherLocationKey)
        assertTrue(enriched.hasMeasuredPrecision)
    }

    @Test fun approximateGrantKeepsRealCoordinatesWithoutFalseQualityClaim() {
        val city = measuredLocationCity(fix, preciseGranted = false)
        assertFalse(city.hasMeasuredPrecision)
        assertEquals(38.520123, city.latitude, 0.0)
        assertTrue(city.isPreciseLocation) // legacy coordinate routing, not a precision claim
    }

    @Test fun oldSavedJsonPreservesIdentityWithUnknownMeasurementQuality() {
        val city = Json.decodeFromString<City>("""{"name":"原地点","affiliation":"","latitude":38.5,"longitude":102.2,"locationKey":"geo:38.500,102.200","isFavorite":true}""")
        assertTrue(city.isFavorite)
        assertTrue(city.isPreciseLocation)
        assertFalse(city.hasMeasuredPrecision)
        assertEquals(CoordinateKind.UNKNOWN, city.coordinateKind)
    }

    @Test fun localCityIsAnAdministrativePointWithNoGpsAccuracy() {
        val city = LocalPlaceIndex.parse("""{"schema":1,"places":[{"id":"CN:1","name":"金川区","affiliation":"甘肃省","lat":38.5,"lon":102.2,"aliases":[],"level":"ADM3"}]}""".toByteArray()).search("金川区").single()
        assertEquals(CoordinateKind.ADMINISTRATIVE, city.coordinateKind)
        assertFalse(city.hasMeasuredPrecision)
        assertNull(city.locationAccuracyMeters)
    }
}
