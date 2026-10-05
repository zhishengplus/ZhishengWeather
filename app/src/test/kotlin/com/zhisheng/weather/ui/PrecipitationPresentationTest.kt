package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class PrecipitationPresentationTest {
    private val now = 1_800_000_000_000L
    @Test fun caiyunWithoutMinutePermissionShowsNativeHourlySamples() {
        val data = WeatherData(dataSource = "CAIYUN", current = CurrentWeather(condition = WeatherCondition.RAIN),
            hourly = listOf(HourlyWeather(now + 3_600_000, precipMm = .7),
                HourlyWeather(now + 7_200_000, precipMm = .3)))
        val result = precipitationPresentation(data, now)
        assertTrue(result.hourlyFallback)
        assertEquals(60, result.intervalMinutes)
        assertEquals(2, result.points.size)
        assertEquals("当前有降水", result.summary)
        assertTrue(precipitationSourceLabel(data, result).contains("小时级"))
    }
    @Test fun minuteForecastTakesPriorityAndMissingHourlyRainIsNotZeroFilled() {
        val data = WeatherData(dataSource = "CAIYUN", hourly = listOf(HourlyWeather(now + 3_600_000)),
            rainMinutes = listOf(MinutePrecip(now, .5f)))
        assertFalse(precipitationPresentation(data, now).hourlyFallback)
        assertTrue(precipitationPresentation(data.copy(rainMinutes = emptyList()), now).points.isEmpty())
    }
    @Test fun isolatedZeroPointDoesNotPromiseADryWindow() {
        val p = precipitationPresentation(WeatherData(rainMinutes = listOf(MinutePrecip(now, 0f))), now)
        assertFalse(p.dry)
        assertTrue(p.summary.contains("该时刻"))
    }
    @Test fun currentRainTakesPriorityOverZeroForecast() {
        val p = precipitationPresentation(WeatherData(current = CurrentWeather(condition = WeatherCondition.RAIN),
            rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 60_000, 0f))), now)
        assertFalse(p.dry)
        assertTrue(p.summary.contains("当前"))
    }
    @Test fun densifiedSeriesDoesNotClaimNativeMinutePrecision() {
        val p = precipitationPresentation(WeatherData(rainMeta = RainMeta("OPEN-METEO", 15),
            rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 60_000, 0f))), now)
        assertEquals(15, p.intervalMinutes)
    }
    @Test fun absentSeriesCannotPromiseTwoDryHours() {
        val result = precipitationPresentation(WeatherData(rainDistanceKm = 12.0), now)
        assertFalse(result.dry)
        assertTrue(result.summary.contains("12"))
        assertNull(result.coverageStart)
    }
    @Test fun expiredZeroSeriesIsNotADryForecast() {
        val result = precipitationPresentation(WeatherData(rainMinutes = listOf(MinutePrecip(now - 600_000, 0f))), now)
        assertFalse(result.dry)
        assertTrue(result.summary.contains("过期"))
        assertTrue(result.points.isEmpty())
    }
    @Test fun validZeroSeriesRetainsItsActualCoverage() {
        val result = precipitationPresentation(WeatherData(rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 900_000, 0f))), now)
        assertTrue(result.dry)
        assertEquals(now, result.coverageStart)
        assertEquals(now + 900_000, result.coverageEnd)
        assertFalse(result.summary.contains("2 小时"))
    }
    @Test fun irregularSamplesKeepTimeAndNeverInventRegularIntervals() {
        val result = precipitationPresentation(WeatherData(rainMinutes = listOf(MinutePrecip(now + 900_000, 1f), MinutePrecip(now, 0f), MinutePrecip(now + 300_000, 0.2f))), now)
        assertEquals(listOf(now, now + 300_000, now + 900_000), result.points.map { it.timeMillis })
        assertNull(result.intervalMinutes)
        assertFalse(result.dry)
    }
    @Test fun textOnlyAndCurrentRainRemainQualifiedWithoutFakeSeries() {
        val text = precipitationPresentation(WeatherData(rainNowcast = "雨带正在靠近"), now)
        assertTrue(text.summary.contains("雨带正在靠近"))
        assertFalse(text.dry)
        val raining = precipitationPresentation(WeatherData(current = CurrentWeather(condition = WeatherCondition.RAIN)), now)
        assertTrue(raining.summary.contains("当前"))
        assertTrue(raining.points.isEmpty())
    }
    @Test fun invalidRainValuesAreExcludedAndDuplicateTimesDoNotDoubleCount() {
        val result = precipitationPresentation(WeatherData(rainMinutes = listOf(MinutePrecip(now, Float.NaN), MinutePrecip(now, 1f), MinutePrecip(now, 1f), MinutePrecip(now + 60_000, -1f))), now)
        assertEquals(1, result.points.size)
        assertEquals(1f, result.points.single().precip)
    }
}
