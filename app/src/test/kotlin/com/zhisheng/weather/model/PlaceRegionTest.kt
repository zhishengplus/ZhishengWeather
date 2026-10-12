package com.zhisheng.weather.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaceRegionTest {
    private fun city(code: String?) = City("同名地点", "", 38.0, 102.0, "test:${code}", countryCode = code)
    @Test fun domesticIncludesMainlandHongKongMacaoAndTaiwan() {
        for (code in listOf("CN", "HK", "MO", "TW", "cn")) assertEquals(PlaceRegion.DOMESTIC, city(code).placeRegion())
        assertEquals(PlaceRegion.OVERSEAS, city("US").placeRegion())
    }
    @Test fun overseasCannotLeakIntoDomesticResultsAndViceVersa() {
        val items = listOf(city("CN"), city("US"), city("HK"))
        assertEquals(listOf("CN", "HK"), filterPlaceRegion(items, PlaceRegion.DOMESTIC).map { it.countryCode })
        assertEquals(listOf("US"), filterPlaceRegion(items, PlaceRegion.OVERSEAS).map { it.countryCode })
    }
    @Test fun unclassifiedLegacyFavoritesAreLabelledWhileOnlineUnknownsAreExcluded() {
        val unknown = city(null)
        assertEquals("地区待确认", unknown.regionLabel())
        assertEquals(listOf(unknown), filterPlaceRegion(listOf(unknown), PlaceRegion.DOMESTIC))
        assertTrue(filterPlaceRegion(listOf(unknown), PlaceRegion.DOMESTIC, includeUnclassified = false).isEmpty())
    }
    @Test fun localIndexCountryAndPersonalDomesticAddressRemainClassifiedWithoutBoundingBoxes() {
        for (code in listOf("CN", "HK", "MO", "TW")) assertEquals(PlaceRegion.DOMESTIC,
            city(null).copy(locationKey = "geo:local:geonames:$code:123").placeRegion())
        assertEquals(PlaceRegion.DOMESTIC, city(null).copy(addressSource = "amap").placeRegion())
        assertNull(city(null).placeRegion())
    }
    @Test fun regionMetadataNeverChangesCoordinateOrLocationIdentity() {
        val before = city(null)
        val after = before.copy(countryCode = "CN")
        assertEquals(before.latitude, after.latitude, 0.0)
        assertEquals(before.longitude, after.longitude, 0.0)
        assertEquals(before.locationKey, after.locationKey)
    }
    @Test fun oldCityJsonStillLoadsAndNewCountryMetadataRoundTrips() {
        val old = Json.decodeFromString<City>("""{"name":"金川区","affiliation":"甘肃","latitude":38.5,"longitude":102.2,"locationKey":"101"}""")
        assertNull(old.countryCode)
        val overseas = old.copy(countryCode = "GB", countryName = "英国")
        assertEquals(overseas, Json.decodeFromString<City>(Json.encodeToString(City.serializer(), overseas)))
        assertEquals("英国", overseas.regionLabel())
    }
    @Test fun groupingKeepsCategoriesTogetherAndUnknownFavoritesLast() {
        val place = city("CN").copy(coordinateKind = CoordinateKind.PLACE)
        val items = filterPlaceRegion(listOf(city(null), place, city("HK"), city("CN")), PlaceRegion.DOMESTIC)
        assertEquals(listOf("HK", "CN", "CN", null), items.map { it.countryCode })
        assertEquals(place, items[2])
    }
}
