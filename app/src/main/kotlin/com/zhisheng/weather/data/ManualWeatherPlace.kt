package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import java.util.Locale

internal fun manualWeatherPlace(name: String, latitude: String, longitude: String, system: String): City? {
    val lat = latitude.trim().toDoubleOrNull() ?: return null
    val lon = longitude.trim().toDoubleOrNull() ?: return null
    if (!validSearchCoordinates(lat, lon) || name.isBlank()) return null
    val point = when (system) {
        "WGS84" -> GeoPoint(lat, lon)
        "GCJ02" -> gcj02ToWgs84(lat, lon)
        "BD09" -> bd09ToWgs84(lat, lon)
        else -> return null
    }
    if (!validSearchCoordinates(point.latitude, point.longitude)) return null
    return City(name.trim().take(40), "自定义地点", point.latitude, point.longitude,
        "geo:manual:" + String.format(Locale.ROOT, "%.6f,%.6f", point.latitude, point.longitude),
        coordinateKind = CoordinateKind.PLACE, addressSource = "manual")
}
