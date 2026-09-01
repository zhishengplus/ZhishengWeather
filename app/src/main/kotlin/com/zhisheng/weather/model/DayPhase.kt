package com.zhisheng.weather.model

import java.time.Instant

/**
 * 只修正有明确昼夜变体的图标。数据源的天气现象仍保留为观测事实，
 * 昼夜在展示层按城市当地时间与当日日出日落决定。
 */
fun phaseAwareCondition(
    condition: WeatherCondition?,
    data: WeatherData,
    timeMillis: Long,
): WeatherCondition? {
    if (condition !in setOf(
            WeatherCondition.CLEAR,
            WeatherCondition.CLEAR_NIGHT,
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.PARTLY_CLOUDY_NIGHT,
        )
    ) return condition

    val local = Instant.ofEpochMilli(timeMillis).atZone(cityZone(data.utcOffsetSeconds))
    val day = data.daily.firstOrNull {
        cityDate(it.dateMillis, data.utcOffsetSeconds) == local.toLocalDate()
    }
    val night = isNightBySun(
        sunrise = day?.sunrise,
        sunset = day?.sunset,
        nowMinutes = local.hour * 60 + local.minute,
    )
    return when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.CLEAR_NIGHT -> if (night) WeatherCondition.CLEAR_NIGHT else WeatherCondition.CLEAR
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.PARTLY_CLOUDY_NIGHT ->
            if (night) WeatherCondition.PARTLY_CLOUDY_NIGHT else WeatherCondition.PARTLY_CLOUDY
        else -> condition
    }
}

internal fun isNightBySun(sunrise: String?, sunset: String?, nowMinutes: Int): Boolean {
    val rise = weatherClockMinutes(sunrise)
    val set = weatherClockMinutes(sunset)
    if (rise == null || set == null || rise >= set) {
        return nowMinutes < 6 * 60 || nowMinutes >= 19 * 60
    }
    return nowMinutes < rise || nowMinutes >= set
}

private fun weatherClockMinutes(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val match = Regex("(?:T|^)(\\d{1,2}):(\\d{2})").find(text)
        ?: Regex("(\\d{1,2}):(\\d{2})").find(text)
        ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
