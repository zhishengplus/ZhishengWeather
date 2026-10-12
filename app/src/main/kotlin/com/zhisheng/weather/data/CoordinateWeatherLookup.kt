package com.zhisheng.weather.data

import com.zhisheng.weather.model.City

/** Weather-station mapping only. Never used to infer a user's administrative address. */
internal fun coordinateXiaomiHit(hits: List<XiaomiLocationResult>, lat: Double, lon: Double): XiaomiLocationResult? =
    hits.filter { hit ->
        val hl = hit.latitude?.toDoubleOrNull()
        val ho = hit.longitude?.toDoubleOrNull()
        hit.status == 0 && (hit.locationKey?.startsWith("weathercn:") == true || hit.locationKey?.startsWith("accu:") == true) &&
            hl != null && ho != null && validSearchCoordinates(hl, ho) && distanceKm(lat, lon, hl, ho) <= 150.0
    }.minByOrNull { distanceKm(lat, lon, it.latitude!!.toDouble(), it.longitude!!.toDouble()) }

internal fun coordinateXiaomiKey(hits: List<XiaomiLocationResult>, lat: Double, lon: Double): String? =
    coordinateXiaomiHit(hits, lat, lon)?.locationKey

internal fun namedWeatherCity(measured: City, hit: XiaomiLocationResult): City = measured.copy(
    name = hit.name?.takeIf(String::isNotBlank) ?: measured.name,
    affiliation = hit.affiliation.orEmpty().split(',').map(String::trim)
        .filter { it.isNotBlank() && it != "中国" }.reversed().joinToString("·"),
)
