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
    }

    @Test
    fun locationRefreshKeepsFavoriteWhileUpdatingStreet() {
        val existing = city("101010100", favorite = true)
        val located = existing.copy(street = "新华路街道", isFavorite = false)

        val merged = mergeLocatedCity(existing, located)

        assertTrue(merged.isFavorite)
        assertEquals("新华路街道", merged.street)
    }
}
