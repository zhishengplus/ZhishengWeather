package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import com.zhisheng.weather.model.WeatherData

/** A rounded legacy key cannot validate the coordinates of a different measurement. */
internal fun weatherCoordinatesMatch(city: City, data: WeatherData): Boolean {
    if (city.coordinateKind != CoordinateKind.DEVICE) return true
    val match = data.locationMatch
    val evidence = data.moduleEvidence[WeatherModule.CURRENT.key]
    val latitude = match?.requestedLatitude ?: evidence?.requestedLatitude ?: return false
    val longitude = match?.requestedLongitude ?: evidence?.requestedLongitude ?: return false
    return latitude == city.latitude && longitude == city.longitude
}
