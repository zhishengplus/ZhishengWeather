package com.zhisheng.weather.ui

import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.*
import org.junit.Test

class WeatherSourceComparisonTest {
    private fun row(source: SourcePref, vararg times: Long) = WeatherRepository.SourceComparison(source,
        WeatherData(hourly = times.map { HourlyWeather(it, temperature = 20.0) }), 1000)

    @Test fun onlyExactSharedFutureHourIsComparable() {
        assertEquals(3000L, commonComparisonHour(listOf(row(SourcePref.XIAOMI, 500, 2000, 3000), row(SourcePref.NMC, 500, 2100, 3000)), 1000))
        assertNull(commonComparisonHour(listOf(row(SourcePref.XIAOMI, 2000), row(SourcePref.NMC, 2100)), 1000))
    }

    @Test fun duplicateHoursWithinOneSourceCannotBecomeAComparisonPair() {
        assertNull(commonComparisonHour(listOf(row(SourcePref.XIAOMI, 2000, 2000)), 1000))
        assertNull(commonComparisonHour(listOf(row(SourcePref.XIAOMI, 2000),
            WeatherRepository.SourceComparison(SourcePref.NMC, WeatherData(hourly = listOf(HourlyWeather(2000))), 1000)), 1000))
    }

    @Test fun aNonFiniteValueCannotMakeAnHourComparable() {
        val missing = WeatherRepository.SourceComparison(SourcePref.NMC,
            WeatherData(hourly = listOf(HourlyWeather(2000, Double.NaN))), 1000)
        assertNull(commonComparisonHour(listOf(row(SourcePref.XIAOMI, 2000), missing), 1000))
    }
}
