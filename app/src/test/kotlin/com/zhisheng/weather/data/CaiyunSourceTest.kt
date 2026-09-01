package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherIntensity

class CaiyunSourceTest {

    @Test
    fun forecastKeypointIsA24HourSummaryNotCurrentWeather() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.CLEAR, weatherText = "晴"),
            rainNowcast = "未来两小时无降水",
            forecastSummary = "多云，今天晚上22点钟后转小雨，其后晴",
            dataSource = "CAIYUN",
        )

        assertEquals("晴", data.current?.weatherText)
        assertEquals("未来两小时无降水", data.rainNowcast)
        assertEquals("多云，今天晚上22点钟后转小雨，其后晴", data.forecastSummary)
    }

    @Test
    fun hourlyProbabilityAcceptsPaidPlanPercentScale() {
        assertEquals(37, CaiyunSource.normalizeProbability(CaiyunTimed(probability = 37.0).probability))
        assertEquals(37, CaiyunSource.normalizeProbability(CaiyunTimed(probability = 0.37).probability))
    }

    @Test
    fun hailAndIntensityRemainDistinct() {
        assertEquals(WeatherCondition.HAIL, CaiyunSource.skycon("LIGHT_HAIL"))
        assertEquals(WeatherIntensity.HEAVY, CaiyunSource.skyconProfile("HEAVY_RAIN")?.intensity)
        assertEquals(WeatherCondition.UNKNOWN, CaiyunSource.skycon("FUTURE_CODE"))
    }
    @Test
    fun mapsEveryLifeIndexReturnedByCurrentAccount() {
        val item = { desc: String -> listOf(CaiyunLifeIndexItem(desc = desc)) }
        val indices = CaiyunSource.mapLifeIndices(
            CaiyunLifeIndex(
                ultraviolet = item("最弱"),
                carWashing = item("适宜"),
                dressing = item("温暖"),
                comfort = item("舒适"),
                coldRisk = item("少发"),
            ),
        )

        assertEquals(listOf("紫外线", "洗车", "穿衣", "舒适", "感冒"), indices.map { it.name })
        assertEquals(listOf("最弱", "适宜", "温暖", "舒适", "少发"), indices.map { it.category })
    }

    @Test
    fun absentPaidBlockDoesNotCreateFakeIndices() {
        assertTrue(CaiyunSource.mapLifeIndices(null).isEmpty())
    }

    @Test
    fun probabilityAcceptsBothCaiyunValueConventionsWithoutDoubleScaling() {
        assertEquals(60, CaiyunSource.normalizeProbability(0.60))
        assertEquals(60, CaiyunSource.normalizeProbability(60.0))
        assertEquals(100, CaiyunSource.normalizeProbability(1.0))
        assertEquals(86, CaiyunSource.normalizeProbability(0.86))
        assertNull(CaiyunSource.normalizeProbability(6000.0))
        assertNull(CaiyunSource.normalizeProbability(Double.NaN))
    }

    @Test
    fun naiveDatetimeUsesFallbackOffsetInsteadOfPhoneZone() {
        val tokyo = 9 * 3_600
        val ms = CaiyunSource.parseTime("2026-08-26T09:00:00", tokyo)
        assertEquals(java.time.Instant.parse("2026-08-26T00:00:00Z").toEpochMilli(), ms)
    }

    @Test
    fun humidityAndCloudCoverAcceptBothRatioAndPercent() {
        assertEquals(65.0, CaiyunSource.ratioToPercent(0.65)!!, 0.0001)
        assertEquals(65.0, CaiyunSource.ratioToPercent(65.0)!!, 0.0001)
        assertNull(CaiyunSource.ratioToPercent(6500.0))
        assertNull(CaiyunSource.ratioToPercent(-0.1))
    }

    @Test
    fun dailyDayAndNightSkyconBecomeATurnPhrase() {
        val mapped = CaiyunSource.dailyDayNight("CLEAR_DAY", "CLOUDY", "PARTLY_CLOUDY_DAY")
        assertEquals(WeatherCondition.OVERCAST, mapped.first)
        assertEquals("晴转阴", mapped.second)
    }

    @Test
    fun paidFieldsUseProviderTimeZoneAndDocumentedUnits() {
        val providerTimeSeconds = 1_777_777_777L
        val mapped = CaiyunSource.map(
            r = CaiyunResult(
                realtime = CaiyunRealtime(
                    temperature = 29.0,
                    humidity = 0.65,
                    pressure = 100_800.0,
                    visibility = 7.2,
                    cloudrate = 0.4,
                    apparentTemperature = 32.0,
                    wind = CaiyunWind(speed = 18.0, direction = 135.0),
                    lifeIndex = CaiyunRealtimeLifeIndex(
                        ultraviolet = CaiyunRealtimeLifeItem(index = 6.0),
                    ),
                    precipitation = CaiyunRealtimePrecip(
                        local = CaiyunLocalPrecip(intensity = 0.7),
                        nearest = CaiyunNearestPrecip(distance = 2_500.0),
                    ),
                ),
                minutely = CaiyunMinutely(precipitation2h = listOf(0.0, 0.2)),
                hourly = CaiyunHourly(
                    temperature = listOf(CaiyunTimed("2026-08-30T12:00:00", 29.0)),
                    apparentTemperature = listOf(CaiyunTimed("2026-08-30T12:00:00", 32.0)),
                    humidity = listOf(CaiyunTimed("2026-08-30T12:00:00", 0.65)),
                    pressure = listOf(CaiyunTimed("2026-08-30T12:00:00", 100_800.0)),
                    visibility = listOf(CaiyunTimed("2026-08-30T12:00:00", 7.2)),
                    cloudrate = listOf(CaiyunTimed("2026-08-30T12:00:00", 0.4)),
                    precipitation = listOf(CaiyunTimed("2026-08-30T12:00:00", 0.7, 0.35)),
                    wind = listOf(CaiyunTimedWind("2026-08-30T12:00:00", 18.0, 135.0)),
                    airQuality = CaiyunHourlyAir(
                        aqi = listOf(CaiyunTimedAqi("2026-08-30T12:00:00", CaiyunAqiChn(chn = 52))),
                    ),
                ),
                daily = CaiyunDaily(
                    temperature = listOf(CaiyunDailyTemp("2026-08-30T00:00:00", 33.0, 24.0)),
                    skyconDay = listOf(CaiyunTimedStr(date = "2026-08-30T00:00:00", value = "CLEAR_DAY")),
                    skyconNight = listOf(CaiyunTimedStr(date = "2026-08-30T00:00:00", value = "CLOUDY")),
                    precipitation = listOf(CaiyunDailyPrecip("2026-08-30T00:00:00", max = 1.8, probability = 0.6)),
                    humidity = listOf(CaiyunDailyMetric("2026-08-30T00:00:00", avg = 0.7)),
                    cloudrate = listOf(CaiyunDailyMetric("2026-08-30T00:00:00", avg = 0.5)),
                    wind = listOf(
                        CaiyunDailyWind(
                            "2026-08-30T00:00:00",
                            max = CaiyunWind(speed = 25.0, direction = 180.0),
                            avg = CaiyunWind(speed = 12.0, direction = 160.0),
                        ),
                    ),
                ),
            ),
            city = City("测试城市", "测试", 39.9, 116.4, "test"),
            utcOffsetSeconds = 8 * 3_600,
            providerUpdateTime = providerTimeSeconds * 1_000L,
        )

        assertEquals(providerTimeSeconds * 1_000L, mapped.updateTime)
        assertEquals(8 * 3_600, mapped.utcOffsetSeconds)
        assertEquals(1008.0, mapped.current?.pressure ?: -1.0, 0.0001)
        assertEquals(18.0, mapped.current?.windSpeed ?: -1.0, 0.0001)
        assertEquals(2.5, mapped.rainDistanceKm ?: -1.0, 0.0001)
        assertEquals(65.0, mapped.hourly.single().humidity ?: -1.0, 0.0001)
        assertEquals(1008.0, mapped.hourly.single().pressure ?: -1.0, 0.0001)
        assertEquals(35, mapped.hourly.single().precipProb)
        assertEquals(52, mapped.hourly.single().aqi)
        assertEquals(70.0, mapped.daily.single().humidity ?: -1.0, 0.0001)
        assertNull(mapped.daily.single().precipMm)
        assertEquals(providerTimeSeconds * 1_000L / 60_000L * 60_000L, mapped.rainMeta?.updateTime)
    }
}
