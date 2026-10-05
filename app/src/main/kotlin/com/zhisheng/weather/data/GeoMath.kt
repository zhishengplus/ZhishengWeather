package com.zhisheng.weather.data

/** 两点球面距离，统一返回千米。 */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
        Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return 2 * earthRadiusKm * Math.asin(Math.sqrt(a))
}

internal data class GeoPoint(val latitude: Double, val longitude: Double)

/** 地图 POI 返回的是国内加密坐标；保存为精确地址前统一近似还原为 WGS84。 */
internal fun gcj02ToWgs84(latitude: Double, longitude: Double): GeoPoint {
    if (outsideChina(latitude, longitude)) return GeoPoint(latitude, longitude)
    val dLat = transformLat(longitude - 105.0, latitude - 35.0)
    val dLon = transformLon(longitude - 105.0, latitude - 35.0)
    val radLat = latitude / 180.0 * Math.PI
    var magic = Math.sin(radLat)
    magic = 1 - 0.00669342162296594323 * magic * magic
    val sqrtMagic = Math.sqrt(magic)
    val latOffset = dLat * 180.0 / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI)
    val lonOffset = dLon * 180.0 / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI)
    return GeoPoint(latitude * 2 - (latitude + latOffset), longitude * 2 - (longitude + lonOffset))
}

internal fun bd09ToWgs84(latitude: Double, longitude: Double): GeoPoint {
    val x = longitude - 0.0065
    val y = latitude - 0.006
    val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * Math.PI * 3000.0 / 180.0)
    val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * Math.PI * 3000.0 / 180.0)
    return gcj02ToWgs84(z * Math.sin(theta), z * Math.cos(theta))
}

private fun outsideChina(lat: Double, lon: Double): Boolean =
    lon !in 72.004..137.8347 || lat !in 0.8293..55.8271

private fun transformLat(x: Double, y: Double): Double =
    -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x)) +
        (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0 +
        (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0 +
        (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0

private fun transformLon(x: Double, y: Double): Double =
    300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x)) +
        (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0 +
        (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0 +
        (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
