package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class PrecipitationPresentationTest {
    private val now = 1_800_000_000_000L
    private fun dryWindow(count: Int = 120, interval: Int = 1) = precipitationPresentation(
        WeatherData(rainMinutes = (0 until count).map { MinutePrecip(now + it * interval * 60_000L, 0f) },
            rainMeta = RainMeta("XIAOMI", interval)), now)
    @Test fun gameNeedsAnUnbrokenCurrentTwoHourDryWindow() {
        assertTrue(dryWindow().hasDrySkyWindow())
        assertTrue(dryWindow(8, 15).hasDrySkyWindow())
        assertFalse(dryWindow(119).hasDrySkyWindow())
        assertFalse(dryWindow(2).hasDrySkyWindow())
        val full = dryWindow()
        assertFalse(full.copy(points = full.points.filterIndexed { i, _ -> i != 30 }).hasDrySkyWindow())
        assertFalse(full.copy(hourlyFallback = true).hasDrySkyWindow())
        assertFalse(full.copy(intervalMinutes = null).hasDrySkyWindow())
        assertFalse(full.copy(dry = false).hasDrySkyWindow())
        assertTrue(full.copy(nowMillis = now + 10 * 60_000).hasDrySkyWindow())
        assertFalse(full.copy(nowMillis = now + 16 * 60_000).hasDrySkyWindow())
    }
    @Test fun completeResponseSurvivesDeliveryDelayButNotStaleDataOrHoles() {
        val zero = (0 until 120).map { MinutePrecip(now + it * 60_000, 0f) }
        val data = WeatherData(rainMinutes = zero, rainMeta = RainMeta("XIAOMI", 1))
        for (age in listOf(1L, 34_000L, 180_000L, 900_000L))
            assertTrue(precipitationPresentation(data, now + age).hasDrySkyWindow())
        assertFalse(precipitationPresentation(data, now + 900_001L).hasDrySkyWindow())
        assertFalse(precipitationPresentation(data.copy(rainMinutes = zero.drop(1)), now + 34_000).hasDrySkyWindow())
        assertFalse(precipitationPresentation(data.copy(rainMinutes = zero.filterIndexed { i, _ -> i != 1 }),
            now + 180_000).hasDrySkyWindow())
        assertFalse(precipitationPresentation(data.copy(rainMinutes = zero.dropLast(1)), now + 34_000).hasDrySkyWindow())
    }
    @Test fun rainMissingExpiredAndFutureOnlyNeverOpenGame() {
        val zero = (0..120).map { MinutePrecip(now + it * 60_000, 0f) }
        for (data in listOf(
            WeatherData(), WeatherData(rainNowcast = "暂无降水"),
            WeatherData(rainMinutes = zero.map { it.copy(timeMillis = it.timeMillis - 8_000_000) }),
            WeatherData(rainMinutes = zero.map { it.copy(timeMillis = it.timeMillis + 900_000) }),
            WeatherData(rainMinutes = zero, current = CurrentWeather(condition = WeatherCondition.RAIN)),
            WeatherData(rainMinutes = zero.mapIndexed { i, it -> if (i == 50) it.copy(precip = .5f) else it }),
        )) assertFalse(precipitationPresentation(data, now).hasDrySkyWindow())
    }
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
    @Test fun anActualMissingNativeBucketNeverBecomesADryCardOrHidesTheGap() {
        for (interval in listOf(1, 5, 15)) {
            val zero = (0 until 120 / interval).map { MinutePrecip(now + it * interval * 60_000L, 0f) }
            val data = WeatherData(rainMinutes = zero, rainMeta = RainMeta("TEST", interval, now))
            assertTrue(precipitationPresentation(data, now).dry)
            assertTrue(precipitationPresentation(data, now).hasDrySkyWindow())
            val missing = precipitationPresentation(data.copy(rainMinutes = zero.filterIndexed { index, _ -> index != 3 }), now)
            assertFalse(missing.dry)
            assertFalse(missing.hasDrySkyWindow())
            assertEquals(interval, missing.intervalMinutes)
            assertEquals("预报数据有缺口，已返回时刻暂无降水", missing.summary)
            assertEquals(zero.size - 1, missing.points.size)
        }
    }
    @Test fun missingEveryOtherBucketCannotRedefineTheDeclaredNativeInterval() {
        val zero = (0 until 120).map { MinutePrecip(now + it * 60_000L, 0f) }
        val partial = precipitationPresentation(WeatherData(rainMinutes = zero.filterIndexed { index, _ -> index % 2 == 0 },
            rainMeta = RainMeta("XIAOMI", 1, now)), now)
        assertEquals(1, partial.intervalMinutes)
        assertFalse(partial.dry)
        assertFalse(partial.hasDrySkyWindow())
        assertTrue(partial.summary.contains("缺口"))
    }
    @Test fun futureOnlyDryBucketsDoNotClaimTheMissingCurrentIntervalIsDry() {
        val data = WeatherData(rainMinutes = (1..8).map { MinutePrecip(now + it * 15 * 60_000L, 0f) },
            rainMeta = RainMeta("OPEN-METEO", 15, now))
        val partial = precipitationPresentation(data, now)
        assertFalse(partial.dry)
        assertFalse(partial.hasDrySkyWindow())
        assertTrue(partial.summary.contains("缺口"))
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

    @Test fun wetModelDoesNotClaimObservedRain() {
        val input = WeatherData(current = CurrentWeather(condition = WeatherCondition.CLEAR),
            rainMinutes = listOf(MinutePrecip(now, .5f), MinutePrecip(now + 60_000, .5f)))
        assertEquals("短时预报提示当前有降水", precipitationPresentation(input, now).summary)
        assertEquals(WeatherCondition.CLEAR, input.current!!.condition)
    }
}
