package com.zhisheng.weather.ui

import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.cityDate

internal fun forecastHoursForDate(hours: List<HourlyWeather>, dateMillis: Long, utcOffsetSeconds: Int?): List<HourlyWeather> {
    val date = cityDate(dateMillis, utcOffsetSeconds)
    return hours.filter { cityDate(it.timeMillis, utcOffsetSeconds) == date }
        .distinctBy(HourlyWeather::timeMillis).sortedBy(HourlyWeather::timeMillis)
}
