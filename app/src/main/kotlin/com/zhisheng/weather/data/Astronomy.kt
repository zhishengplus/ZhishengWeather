package com.zhisheng.weather.data

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** 足够用于判定民用/航海/天文暮光的 NOAA 低成本太阳位置算法。 */
object Astronomy {
    fun sunAltitudeDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val instant = Instant.ofEpochMilli(epochMillis)
        val utc = instant.atZone(ZoneOffset.UTC)
        val day = utc.dayOfYear
        val hour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        val gamma = 2.0 * PI / utc.toLocalDate().lengthOfYear() * (day - 1 + (hour - 12.0) / 24.0)
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) -
            0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val minutes = hour * 60 + equationOfTime + 4.0 * longitude
        val trueSolarMinutes = ((minutes % 1440.0) + 1440.0) % 1440.0
        val hourAngle = (trueSolarMinutes / 4.0 - 180.0) * PI / 180.0
        val latRad = latitude * PI / 180.0
        val altitude = asin(
            (sin(latRad) * sin(declination) + cos(latRad) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0),
        )
        return altitude * 180.0 / PI
    }
}
