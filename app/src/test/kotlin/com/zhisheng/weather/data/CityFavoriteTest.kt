package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityFavoriteTest {
    private fun city(key: String, favorite: Boolean = false) = City(
        name = key,
        affiliation = "测试",
        latitude = 39.9,
        longitude = 116.4,
        locationKey = key,
        isFavorite = favorite,
    )

    @Test
    fun favoriteCitiesMoveAheadWithoutChangingEitherGroupsOrder() {
        val cities = listOf(
            city("普通甲"),
            city("收藏甲", favorite = true),
            city("普通乙"),
            city("收藏乙", favorite = true),
        )

        assertEquals(
            listOf("收藏甲", "收藏乙", "普通甲", "普通乙"),
            favoriteCitiesFirst(cities).map(City::locationKey),
        )
    }

    @Test
    fun olderCityJsonSafelyDefaultsToNotFavorite() {
        val oldJson = """{
            "name":"北京","affiliation":"北京","latitude":39.9,
            "longitude":116.4,"locationKey":"101010100"
        }""".trimIndent()

        val decoded = Json.decodeFromString<City>(oldJson)

        assertFalse(decoded.isFavorite)
        assertEquals(null, decoded.weatherLocationKey)
    }

    @Test
    fun locationRefreshKeepsFavoriteWhileUpdatingStreet() {
        val existing = city("101010100", favorite = true)
        val located = existing.copy(street = "新华路街道", isFavorite = false)

        val merged = mergeLocatedCity(existing, located)

        assertTrue(merged.isFavorite)
        assertEquals("新华路街道", merged.street)
    }

    @Test
    fun seventhFavoriteIsRejectedWithoutChangingSavedCities() {
        val cities = (1..7).map { city("地址$it", favorite = it <= 6) }

        val toggled = toggleFavoriteIn(cities, "地址7")

        assertEquals(FavoriteToggleResult.LIMIT_REACHED, toggled.result)
        assertEquals(6, toggled.cities.count(City::isFavorite))
        assertFalse(toggled.cities.last().isFavorite)
    }

    @Test
    fun removingAFavoriteAlwaysRemainsAvailableAtTheLimit() {
        val cities = (1..6).map { city("地址$it", favorite = true) }

        val toggled = toggleFavoriteIn(cities, "地址3")

        assertEquals(FavoriteToggleResult.UNFAVORITED, toggled.result)
        assertEquals(5, toggled.cities.count(City::isFavorite))
    }

    @Test
    fun preciseLocationsWithin250MetersMergeEvenWhenAnotherCityIsSelected() {
        val oldHome = City("金川区", "甘肃·金昌", 38.5200, 102.1900, "geo:38.520,102.190")
        val unrelatedSelected = city("北京")
        val driftedHome = oldHome.copy(
            latitude = 38.5208,
            longitude = 102.1908,
            locationKey = "geo:38.521,102.191",
        )

        assertEquals(0, nearestLocatedCityIndex(listOf(oldHome, unrelatedSelected), driftedHome))
        assertTrue(shouldRefreshSameLocatedAddress(oldHome, driftedHome))
    }

    @Test
    fun preciseLocationsFartherThan250MetersRemainSeparateFavorites() {
        val home = City("金川区", "甘肃·金昌", 38.5200, 102.1900, "geo:38.520,102.190")
        val office = home.copy(latitude = 38.5240, locationKey = "geo:38.524,102.190")

        assertEquals(-1, nearestLocatedCityIndex(listOf(home), office))
        assertFalse(shouldRefreshSameLocatedAddress(home, office))
    }
}
