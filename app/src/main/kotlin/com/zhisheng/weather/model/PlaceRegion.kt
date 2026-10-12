package com.zhisheng.weather.model

enum class PlaceRegion(val label: String) { DOMESTIC("国内"), OVERSEAS("海外") }

private val domesticCodes = setOf("CN", "HK", "MO", "TW")

/** Metadata only: a bounding rectangle cannot identify countries or administrative containment. */
fun City.resolvedRegionCode(): String? = countryCode?.trim()?.uppercase(java.util.Locale.ROOT)
    ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
    ?: locationKey.takeIf { it.startsWith("geo:local:geonames:") }
        ?.substringAfter("geonames:")?.substringBefore(':')?.takeIf { it in domesticCodes }
    ?: "CN".takeIf { addressSource == "amap" || addressSource == "baidu" }

fun City.placeRegion(): PlaceRegion? = resolvedRegionCode()?.let {
    if (it in domesticCodes) PlaceRegion.DOMESTIC else PlaceRegion.OVERSEAS
}

fun filterPlaceRegion(cities: List<City>, region: PlaceRegion, includeUnclassified: Boolean = true): List<City> =
    cities.filter { it.placeRegion() == region || (includeUnclassified && it.placeRegion() == null) }
        .sortedBy { if (it.placeRegion() == null) 2 else if (it.coordinateKind == CoordinateKind.PLACE || it.street != null) 1 else 0 }

fun City.regionLabel(): String = when (resolvedRegionCode()) {
    "CN" -> "中国"
    "HK" -> "中国香港"
    "MO" -> "中国澳门"
    "TW" -> "中国台湾"
    null -> "地区待确认"
    else -> countryName?.takeIf(String::isNotBlank) ?: resolvedRegionCode().orEmpty()
}
