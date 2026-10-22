package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Beta108PlacesTest {
    @Test fun onlineAdministrativeAliasesStillMatchTheChosenProvinceAndCity() {
        val city = City("海口", "海南·海口", 19.84, 110.42, "om:haikou")
        assertTrue(cityMatchesSearchArea(city, listOf("海南省", "海口市")))
        assertFalse(cityMatchesSearchArea(city.copy(affiliation = "台湾·苗栗县"), listOf("海南省")))
        assertTrue(cityMatchesSearchArea(city.copy(name = "上海", affiliation = "上海"), listOf("上海市")))
        assertTrue(cityMatchesSearchArea(city.copy(name = "南宁", affiliation = "广西·南宁"), listOf("广西壮族自治区", "南宁市")))
    }
    @Test fun duplicateShanghaiAdministrativeLevelsBecomeOneResult() {
        val index = LocalPlaceIndex.parse(File("src/main/assets/geo/local_places.json").readBytes())
        val result = mergePlaceSearchResults(index.search("上海"), emptyList())
        assertEquals(1, result.count { it.name == "上海市" && it.affiliation.isBlank() })
        val onlineCenter = City("上海", "上海市", 31.23, 121.47, "om:shanghai", countryCode = "CN")
        assertEquals(1, mergePlaceSearchResults(result, listOf(onlineCenter)).count { it.name.removeSuffix("市") == "上海" })
        assertTrue("海南省" in index.areas())
        assertTrue("海口市" in index.areas("海南省"))
        assertEquals(34, index.areas().size)
        assertTrue(index.areas().containsAll(listOf("香港", "澳门", "台湾")))
        assertFalse("Sai Kung District" in index.areas())
        assertTrue(index.areas("香港").isNotEmpty())
        assertTrue(index.areas("澳门").isNotEmpty())
        assertTrue(index.areas("台湾").containsAll(listOf("台北市", "高雄市")))
        assertTrue(index.search("海南省 海口市").any { it.name == "海口市" })
        assertFalse(index.search("海南省 海口").any { "苗栗" in it.affiliation })
    }
    @Test fun nearbyPoiCoordinatesRemainDistinct() {
        val first = City("海口", "台湾·苗栗县", 24.0, 120.0, "geo:one", coordinateKind = CoordinateKind.PLACE)
        val second = first.copy(latitude = 24.00001, locationKey = "geo:two")
        assertEquals(2, mergePlaceSearchResults(listOf(first, second), emptyList()).size)
        val admin = first.copy(coordinateKind = CoordinateKind.ADMINISTRATIVE)
        assertEquals(1, mergePlaceSearchResults(listOf(admin, second.copy(coordinateKind = CoordinateKind.ADMINISTRATIVE)), emptyList()).size)
    }
    @Test fun manualCoordinatesValidateRangeAndDoNotInventGpsAccuracy() {
        for (bad in listOf("NaN", "Infinity", "91", "-91", "")) assertNull(manualWeatherPlace("家", bad, "116", "WGS84"))
        assertNull(manualWeatherPlace("家", "39", "181", "WGS84"))
        assertNull(manualWeatherPlace("", "39", "116", "WGS84"))
        val place = manualWeatherPlace("清华大学", "40.002", "116.326", "WGS84")!!
        assertEquals(40.002, place.latitude, 0.0)
        assertNull(place.locationAccuracyMeters)
        assertFalse(place.hasMeasuredPrecision)
        assertEquals(CoordinateKind.PLACE, place.coordinateKind)
        assertTrue(place.locationKey.startsWith("geo:manual:"))
        val converted = manualWeatherPlace("高德地点", "40.002", "116.326", "GCJ02")!!
        assertTrue(converted.longitude != place.longitude)
    }
}
