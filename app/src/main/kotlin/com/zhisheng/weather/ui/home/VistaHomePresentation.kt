package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.Fmt
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun vistaLocationHeading(cityName: String): Pair<String, String> =
    cityName.substringBefore('·') to cityName.substringAfter('·', "")

// Only a complete, recognized release title may be shortened. Never strip a
// cancellation, an unknown advisory or trailing instructions from the source.
internal fun vistaVisibleAlerts(alerts: List<AlertInfo>): List<AlertInfo> =
    alerts.filter { it.title.isNotBlank() }

internal fun vistaAlertHeading(alert: AlertInfo): String =
    Regex("^.*?发布([^\\n。]+(?:蓝|黄|橙|红)色预警(?:信号)?)$")
        .matchEntire(alert.title)?.groupValues?.get(1) ?: alert.title

internal fun vistaHourlyPrecipLabel(prob: Int?): String = when (prob) {
    null -> ""
    in 0..100 -> "$prob%"
    else -> ""
}

internal fun vistaHourlyShowsPrecip(hourly: List<com.zhisheng.weather.model.HourlyWeather>): Boolean =
    hourly.any { it.precipProb != null && it.precipProb in 0..100 }

internal fun vistaUpdateTime(timestamp: Long, now: Long, utcOffsetSeconds: Int?): String {
    val zone = Fmt.zoneId(utcOffsetSeconds)
    val observed = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val pattern = when {
        observed.toLocalDate() == today -> "HH:mm"
        observed.year == today.year -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd HH:mm"
    }
    return observed.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
}

internal data class HomeWeatherUpdateStamp(val timeMillis: Long, val label: String, val code: String)

/** Show the last successful app refresh; source publication is a separate timestamp. */
internal fun homeWeatherUpdateStamp(data: WeatherData?): HomeWeatherUpdateStamp? = when {
    data?.fetchedAt != null -> HomeWeatherUpdateStamp(data.fetchedAt, "更新于", "UPD")
    data?.updateTime != null -> HomeWeatherUpdateStamp(data.updateTime, "数据发布于", "DATA")
    else -> null
}
