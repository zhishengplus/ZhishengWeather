package com.zhisheng.weather.ui

import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.data.TyphoonTrackPoint
import com.zhisheng.weather.data.TyphoonWindRadii
import org.maplibre.geojson.Point
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal const val TY_WIND_ARC_STEP_DEG = 3

internal fun typhoonLevelGroup(point: TyphoonTrackPoint): Int = when {
    point.windLevel == null -> 0
    point.windLevel <= 7 -> 1
    point.windLevel <= 9 -> 2
    point.windLevel <= 11 -> 3
    point.windLevel <= 13 -> 4
    point.windLevel <= 15 -> 5
    else -> 6
}

/** 国标六级：低压青、风暴黄、强风暴橙、台风红、强台风粉、超强紫。 */
internal fun typhoonIntensityGroup(point: TyphoonTrackPoint): Int {
    val name = point.intensity.orEmpty()
    return when {
        name.contains("超强") -> 6
        name.contains("强台风") -> 5
        name.contains("台风") -> 4
        name.contains("强热带风暴") -> 3
        name.contains("热带风暴") -> 2
        name.contains("热带低压") -> 1
        else -> typhoonLevelGroup(point)
    }
}

internal fun typhoonIntensityColor(point: TyphoonTrackPoint): Color =
    typhoonLevelColor(typhoonIntensityGroup(point))

internal fun typhoonLevelColor(group: Int): Color = when (group) {
    1 -> Color(0xFF00FEDF)
    2 -> Color(0xFFFEF300)
    3 -> Color(0xFFFE902C)
    4 -> Color(0xFFFE0404)
    5 -> Color(0xFFFE3AA3)
    6 -> Color(0xFFAE00D9)
    else -> Color(0xFF8A8A8A)
}

internal fun forecastAgencyColor(agency: String): Color = when (agency.trim()) {
    "中国" -> Color(0xFFFF4050)
    "中国香港" -> Color(0xFFFF66FF)
    "中国台湾", "台湾" -> Color(0xFFFFA040)
    "日本" -> Color(0xFF43FF4B)
    "美国" -> Color(0xFF40DDFF)
    else -> Color(0xFF40DDFF)
}

internal fun typhoonPolyline(points: List<TyphoonTrackPoint>): List<Point> =
    points.map { Point.fromLngLat(it.longitude, it.latitude) }

/**
 * 四象限等半径圆弧拼成风圈，和 turf.lineArc / 巴威追踪器同一套：
 * 东北 0–90、东南 90–180、西南 180–270、西北 270–360，象限内半径不变。
 */
internal fun windCircleRing(center: TyphoonTrackPoint, radii: TyphoonWindRadii): List<Point> {
    val quads = doubleArrayOf(
        radii.northEastKm ?: 0.0,
        radii.southEastKm ?: 0.0,
        radii.southWestKm ?: 0.0,
        radii.northWestKm ?: 0.0,
    )
    val ring = ArrayList<Point>(4 * (90 / TY_WIND_ARC_STEP_DEG + 1) + 1)
    for (quadrant in 0 until 4) {
        val start = quadrant * 90
        val end = (quadrant + 1) * 90
        var bearing = start
        while (bearing <= end) {
            ring += destinationPoint(center.latitude, center.longitude, (bearing % 360).toDouble(), quads[quadrant])
            bearing += TY_WIND_ARC_STEP_DEG
        }
    }
    ring += ring.first()
    return ring
}

internal fun destinationPoint(latitude: Double, longitude: Double, bearingDegrees: Double, distanceKm: Double): Point {
    val angular = distanceKm / 6371.0088
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
    return Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
}
