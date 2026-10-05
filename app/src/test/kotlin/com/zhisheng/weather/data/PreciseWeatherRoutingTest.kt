package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherLocationMatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseWeatherRoutingTest {
    private val gpsCity = City(
        name = "测试街道",
        affiliation = "测试市",
        latitude = 22.761234,
        longitude = 113.612345,
        locationKey = "geo:22.76123,113.61235",
        weatherLocationKey = "weathercn:101280701",
    )

    @Test
    fun preciseIdentityKeepsGpsSeparateFromProviderCityKey() {
        assertTrue(gpsCity.isPreciseLocation)
        assertEquals("weathercn:101280701", gpsCity.weatherLocationKey)
        assertEquals(22.761234, gpsCity.latitude, 0.0)
        assertEquals(113.612345, gpsCity.longitude, 0.0)
    }

    @Test
    fun openMeteoUsesNearestGridOnlyForGpsLocations() {
        assertEquals("&cell_selection=nearest", openMeteoCellSelection(gpsCity.isPreciseLocation))
        assertEquals("", openMeteoCellSelection(false))
    }

    @Test
    fun providerMatchCanProveOriginalAndActualRequestCoordinates() {
        val match = WeatherLocationMatch(
            requestedLatitude = gpsCity.latitude,
            requestedLongitude = gpsCity.longitude,
            providerLatitude = 22.76,
            providerLongitude = 113.61,
            preciseGps = true,
            matchedLatitude = 22.75,
            matchedLongitude = 113.625,
        )
        val restored = Json.decodeFromString<WeatherLocationMatch>(Json.encodeToString(match))

        assertTrue(restored.preciseGps)
        assertEquals(22.761234, restored.requestedLatitude, 0.0)
        assertEquals(22.76, restored.providerLatitude, 0.0)
        assertFalse(restored.requestedLatitude == restored.providerLatitude)
        assertEquals(113.625, restored.matchedLongitude ?: 0.0, 0.0)
    }
}
