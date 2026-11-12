package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import org.junit.Assert.*
import org.junit.Test

class CaiyunTimeAlignmentTest {
    private val city = City("香港", "香港", 22.3, 114.17, "test")
    private val first = "2026-11-02T12:00:00+08:00"
    private val second = "2026-11-02T13:00:00+08:00"

    @Test fun partialUnsortedArraysJoinByInstantWithoutShiftingRainOrWind() {
        val data = CaiyunSource.map(CaiyunResult(hourly = CaiyunHourly(
            temperature = listOf(CaiyunTimed(second, 25.0), CaiyunTimed(first, 24.0)),
            precipitation = listOf(CaiyunTimed(second, 3.0, 0.8)),
            wind = listOf(CaiyunTimedWind("2026-11-02T04:00:00Z", 12.0, 90.0)),
            humidity = listOf(CaiyunTimed("invalid", 0.8)),
        )), city, 28800)
        assertEquals(listOf(24.0, 25.0), data.hourly.map { it.temperature })
        assertNull(data.hourly[0].precipMm)
        assertEquals(3.0, data.hourly[1].precipMm!!, 0.0)
        assertEquals(12.0, data.hourly[0].windSpeed!!, 0.0)
        assertNull(data.hourly[1].windSpeed)
        assertTrue(data.hourly.all { it.humidity == null })
    }

    @Test fun conflictingDuplicatesAreMissingInsteadOfArbitrarilyWinning() {
        val rows = CaiyunSource.map(CaiyunResult(hourly = CaiyunHourly(
            temperature = listOf(CaiyunTimed(first, 24.0)),
            precipitation = listOf(CaiyunTimed(first, 0.0), CaiyunTimed(first, 9.0)),
        )), city).hourly
        assertEquals(1, rows.size)
        assertNull(rows.single().precipMm)
    }

    @Test fun dailyFieldsJoinByLocalDateIncludingDateOnlyAndMissingTemperature() {
        val data = CaiyunSource.map(CaiyunResult(daily = CaiyunDaily(
            temperature = listOf(CaiyunDailyTemp("2026-11-02T00:00:00+08:00", 30.0, 24.0)),
            precipitation = listOf(CaiyunDailyPrecip("2026-11-03", probability = 0.8)),
            skyconDay = listOf(CaiyunTimedStr(date = "2026-11-03T00:00:00+08:00", value = "LIGHT_RAIN")),
            humidity = listOf(CaiyunDailyMetric("2026-11-01T16:00:00Z", avg = 0.6)),
            lifeIndex = CaiyunLifeIndex(ultraviolet = listOf(CaiyunLifeIndexItem("2026-11-03", "6"))),
        )), city, 28800)
        assertEquals(2, data.daily.size)
        assertEquals(30.0, data.daily[0].high!!, 0.0)
        assertEquals(60.0, data.daily[0].humidity!!, 0.0)
        assertNull(data.daily[0].precipProbability)
        assertNull(data.daily[0].uvIndex)
        assertNull(data.daily[1].high)
        assertEquals(com.zhisheng.weather.model.WeatherCondition.DRIZZLE, data.daily[1].condition)
        assertEquals(80, data.daily[1].precipProbability)
        assertEquals(6, data.daily[1].uvIndex)
    }

    @Test fun failedBlocksCannotPublishStaleWeatherOrAFalseAllClear() {
        val data = CaiyunSource.map(CaiyunResult(
            realtime = CaiyunRealtime(temperature = 25.0, status = "failed"),
            hourly = CaiyunHourly(status = "failed", temperature = listOf(CaiyunTimed(first, 25.0))),
            daily = CaiyunDaily(status = "failed", temperature = listOf(CaiyunDailyTemp("2026-11-02", 30.0, 24.0))),
            alert = CaiyunAlertBlock(status = "failed", content = emptyList()),
        ), city)
        assertNull(data.current)
        assertNull(data.aqi)
        assertTrue(data.hourly.isEmpty())
        assertTrue(data.daily.isEmpty())
        assertFalse(data.blockSources.containsKey("alerts"))
    }
}
