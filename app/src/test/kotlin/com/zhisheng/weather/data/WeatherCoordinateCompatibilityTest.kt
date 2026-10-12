package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class WeatherCoordinateCompatibilityTest {
    private val city = measuredLocationCity(LocationFix(38.5, 102.2, 500, 1, 20f), true)
    @Test fun sameRoundedKeyDoesNotPermitWeatherFromAnOlderDifferentFix() {
        val data = WeatherData(locationMatch = WeatherLocationMatch(38.5001, 102.2001, 38.5, 102.2))
        assertFalse(weatherCoordinatesMatch(city, data))
        assertTrue(weatherCoordinatesMatch(city.copy(latitude = 38.5001, longitude = 102.2001), data))
    }
    @Test fun missingMeasurementEvidenceCannotValidateANewDeviceLocation() {
        assertFalse(weatherCoordinatesMatch(city, WeatherData()))
        assertTrue(weatherCoordinatesMatch(city.copy(coordinateKind = CoordinateKind.UNKNOWN), WeatherData()))
    }
}
