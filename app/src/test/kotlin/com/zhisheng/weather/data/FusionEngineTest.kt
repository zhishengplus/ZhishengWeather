package com.zhisheng.weather.data

import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** 融合引擎纯函数测试（无网络）。时间基准固定为 2026-09-14 12:00 北京（epoch 1789358400000）。 */
class FusionEngineTest {

    private val now = 1789358400000L
    private val beijing = ZoneOffset.ofHours(8)
    private val city = City("朝阳区", "北京市", 39.9, 116.4, "k")

    private fun src(id: String, weight: Double = 1.0, data: WeatherData): FusionEngine.FusionSource =
        FusionEngine.FusionSource(id, if (id.startsWith("OPEN-METEO")) "OPEN-METEO" else id, weight, data)

    private fun data(
        current: CurrentWeather? = null,
        hourly: List<HourlyWeather> = emptyList(),
        daily: List<DailyWeather> = emptyList(),
        aqi: AqiInfo? = null,
    ) = WeatherData(current = current, hourly = hourly, daily = daily, aqi = aqi, utcOffsetSeconds = 28800)

    private fun hourAt(
        offsetHours: Long,
        temp: Double? = null,
        prob: Int? = null,
        cond: WeatherCondition? = null,
        wind: Double? = null,
        dir: Double? = null,
        humidity: Double? = null,
    ) = HourlyWeather(
        timeMillis = FusionEngine.hourBucket(now) + offsetHours * 3_600_000L,
        temperature = temp, precipProb = prob, condition = cond,
        windSpeed = wind, windDirectionDeg = dir, humidity = humidity,
    )

    private fun dayOf(date: LocalDate, high: Double? = null, low: Double? = null, cond: WeatherCondition? = null) =
        DailyWeather(
            dateMillis = date.atStartOfDay(beijing).toInstant().toEpochMilli(),
            high = high, low = low, condition = cond,
        )

    private fun fuseHourly(vararg sources: FusionEngine.FusionSource): List<HourlyWeather> =
        FusionEngine.fuseHourly(
            FusionEngine.Input(city, sources.toList(), now),
            startHour = FusionEngine.hourBucket(now) - 3_600_000L,
            endHour = FusionEngine.hourBucket(now) + FusionEngine.FUSED_HOURLY_HOURS * 3_600_000L,
        )

    private fun hour(fused: List<HourlyWeather>, offsetHours: Long): HourlyWeather =
        fused.first { it.timeMillis == FusionEngine.hourBucket(now) + offsetHours * 3_600_000L }

    // —— 标量：加权中位数 ——

    @Test
    fun weightedMedianResistsLowWeightOutlier() {
        val votes = listOf(20.0 to 1.0, 21.0 to 1.0, 22.0 to 1.0, 99.0 to 0.2)
        assertEquals(21.0, FusionEngine.weightedMedian(votes)!!, 0.001)
        assertNull(FusionEngine.weightedMedian(emptyList()))
    }

    // —— 风：u/v 向量平均 ——

    @Test
    fun vectorMeanWindAveragesDirectionAndCalmsOpposites() {
        val same = FusionEngine.vectorMeanWind(
            listOf(
                FusionEngine.WindSample(10.0, 90.0, 1.0),
                FusionEngine.WindSample(10.0, 90.0, 1.0),
            )
        )!!
        assertEquals(10.0, same.speedKmh, 0.01)
        assertEquals(90.0, same.dirDeg!!, 0.01)

        // 对吹抵消 → 静风分支：方向回退最大权重源、速度回退加权中位数（不让对吹互相放大）
        val calm = FusionEngine.vectorMeanWind(
            listOf(
                FusionEngine.WindSample(20.0, 0.0, 1.0),
                FusionEngine.WindSample(20.0, 180.0, 1.0),
            )
        )!!
        assertEquals(20.0, calm.speedKmh, 0.01)
        assertEquals(0.0, calm.dirDeg!!, 0.01)
    }

    @Test
    fun vectorMeanWindSingleCandidatePassesThrough() {
        val single = FusionEngine.vectorMeanWind(listOf(FusionEngine.WindSample(5.0, 270.0, 1.0)))!!
        assertEquals(5.0, single.speedKmh, 0.01)
        assertEquals(270.0, single.dirDeg!!, 0.01)
        // 只有速度的候选不参与方向
        val noDir = FusionEngine.vectorMeanWind(listOf(FusionEngine.WindSample(8.0, null, 1.0)))!!
        assertNull(noDir.dirDeg)
        assertEquals(8.0, noDir.speedKmh, 0.01)
    }

    // —— 现象投票 ——

    @Test
    fun conditionVoteTiePrefersSevereAndOfficialBoostDecides() {
        val tie = FusionEngine.voteCondition(
            listOf(
                FusionEngine.ConditionVote(WeatherCondition.CLEAR, 1.0, false),
                FusionEngine.ConditionVote(WeatherCondition.RAIN, 1.0, false),
            )
        )
        assertEquals(WeatherCondition.RAIN, tie)
        // 央台 ×1.5：阴（央台）压过等权的小雨
        val boosted = FusionEngine.voteCondition(
            listOf(
                FusionEngine.ConditionVote(WeatherCondition.RAIN, 1.0, false),
                FusionEngine.ConditionVote(WeatherCondition.OVERCAST, 1.0, true),
            )
        )
        assertEquals(WeatherCondition.OVERCAST, boosted)
    }

    // —— 官方锚（>3°C 分歧才拉） ——

    @Test
    fun officialAnchorPullsBeyondThreeDegreesOnly() {
        val a = src("XIAOMI", 1.0, data(hourly = listOf(hourAt(2, temp = 20.0))))
        val b = src("TENCENT", 1.0, data(hourly = listOf(hourAt(2, temp = 20.0))))
        val nmcFar = src("NMC", 0.9, data(hourly = listOf(hourAt(2, temp = 10.0))))
        val fused = fuseHourly(a, b, nmcFar)
        assertEquals(15.0, hour(fused, 2).temperature!!, 0.01) // 20 → 向 10 拉 0.5

        val nmcNear = src("NMC", 0.9, data(hourly = listOf(hourAt(2, temp = 18.0))))
        val fused2 = fuseHourly(a, b, nmcNear)
        assertEquals(20.0, hour(fused2, 2).temperature!!, 0.01) // |20-18|<3 不拉
    }

    // —— 实况偏差订正 ——

    @Test
    fun biasCorrectionDecaysAndStopsAtSixHours() {
        val hourly = listOf(hourAt(0, temp = 20.0), hourAt(2, temp = 22.0), hourAt(10, temp = 24.0))
        val corrected = FusionEngine.applyBiasCorrection(hourly, CurrentWeather(temperature = 16.0), now)
        assertEquals(20.0, corrected[0].temperature!!, 0.001) // 现在整点不订正
        assertEquals(22.0 - 4.0 * kotlin.math.exp(-2.0 / 3.0), corrected[1].temperature!!, 0.001)
        assertEquals(24.0, corrected[2].temperature!!, 0.001) // 超出 6h 不订正
        // 异常大的偏差（>15°C）整个跳过
        val skipped = FusionEngine.applyBiasCorrection(hourly, CurrentWeather(temperature = 100.0), now)
        assertEquals(22.0, skipped[1].temperature!!, 0.001)
    }

    // —— NMC 只在自身整点投票（缺的地方不插值、不派生） ——

    @Test
    fun nmcVotesOnlyWhereItHasData() {
        val a = src("XIAOMI", 1.0, data(hourly = listOf(hourAt(1, temp = 20.0), hourAt(2, temp = 20.0))))
        val nmc = src("NMC", 0.9, data(hourly = listOf(hourAt(2, temp = 10.0))))
        val fused = fuseHourly(a, nmc)
        assertEquals(20.0, hour(fused, 1).temperature!!, 0.01) // 央台不在场，无锚
        assertEquals(15.0, hour(fused, 2).temperature!!, 0.01) // 央台在场且分歧大 → 拉
    }

    // —— 每变量独立覆盖率 ——

    @Test
    fun precipProbabilityFusesOnlySourcesWithCoverage() {
        val a = src("XIAOMI", 1.0, data(hourly = listOf(hourAt(1, temp = 20.0, prob = 60))))
        val b = src("NMC", 0.9, data(hourly = listOf(hourAt(1, temp = 21.0))))
        val fused = fuseHourly(a, b)
        assertEquals(60, hour(fused, 1).precipProb) // 无票源不参与（不是 0 票）
    }

    // —— 逐日：cityDate 分桶 + 并集长度 + 月相本地补齐 ——

    @Test
    fun dailyBucketsByCityDateAndEnrichesMoon() {
        val d1 = LocalDate.of(2026, 9, 14)
        val d2 = LocalDate.of(2026, 9, 15)
        val a = src(
            "XIAOMI", 1.0, data(
                daily = listOf(
                    dayOf(d1, 30.0, 20.0, WeatherCondition.CLEAR),
                    dayOf(d2, 31.0, 21.0, WeatherCondition.CLEAR),
                )
            )
        )
        val b = src("NMC", 0.9, data(daily = listOf(dayOf(d1, 28.0, 18.0, WeatherCondition.PARTLY_CLOUDY))))
        val daily = FusionEngine.fuseDaily(listOf(a, b), city, 28800)
        assertEquals(2, daily.size) // 并集长度 = 最长参与者
        val first = daily[0]
        assertEquals(30.0, first.high!!, 0.01)
        assertEquals(20.0, first.low!!, 0.01)
        assertEquals(d1.atStartOfDay(beijing).toInstant().toEpochMilli(), first.dateMillis)
        // 月相/月升月落：MoonCalc 本地计算（完全体恒有）
        assertNotNull(first.moonPhase)
        assertNotNull(first.moonrise)
        assertNotNull(first.moonset)
    }

    // —— 身份与分块来源 ——

    @Test
    fun fusedOutputKeepsZhishengIdentity() {
        val a = src("XIAOMI", 1.0, data(current = CurrentWeather(temperature = 20.0)))
        val b = src("OPEN-METEO:ecmwf_ifs025", 0.8, data(current = CurrentWeather(temperature = 21.0)))
        val fused = FusionEngine.fuse(FusionEngine.Input(city, listOf(a, b), now))
        assertEquals("ZHISHENG", fused.dataSource)
        assertEquals(listOf("XIAOMI", "OPEN-METEO"), fused.fusionSources)
        assertEquals("XIAOMI", fused.blockSources["current"])
    }

    // —— 完全体本地兜底 ——

    @Test
    fun completenessFillsDewPointAndFeelsLike() {
        val a = src("XIAOMI", 1.0, data(current = CurrentWeather(temperature = 20.0, humidity = 50.0)))
        val b = src(
            "NMC", 0.9,
            data(current = CurrentWeather(temperature = 21.0, humidity = 55.0, condition = WeatherCondition.CLEAR)),
        )
        val c = FusionEngine.fuse(FusionEngine.Input(city, listOf(a, b), now)).current!!
        // Magnus 露点（20°C/50% ≈ 9.3°C）
        assertEquals(9.26, c.dewPoint!!, 0.2)
        assertNotNull(c.feelsLike)
        assertEquals("晴", c.weatherText) // 现象在场时文本取 label 兜底
    }

    @Test
    fun forecastSummaryDerivedFromHourly() {
        val hourly = (1..6).map {
            hourAt(it.toLong(), temp = 20.0 + it * 2.0, prob = if (it >= 4) 70 else 0)
        }
        val s = FusionEngine.deriveForecastSummary(hourly, CurrentWeather(temperature = 19.0), now)!!
        assertTrue(s.contains("回升"))
        assertTrue(s.contains("较大"))
    }

    // —— 国标 AQI 本地计算（GB HJ 633-2012） ——

    @Test
    fun gbAqiComputesFromPollutantConcentrations() {
        val om = omAir(pm25 = 18.0, o3 = 54.0)
        val (v, primary) = FusionEngine.gbAqiNow(om)!!
        assertEquals(25, v) // pm2.5=18 → IAQI≈25.7，O₃=54 → ≈16.9，取最大
        assertNull(primary) // ≤50 无首要污染物

        val heavy = omAir(pm25 = 75.0, o3 = null)
        val (v2, p2) = FusionEngine.gbAqiNow(heavy)!!
        assertEquals(100, v2)
        assertEquals("PM2.5", p2)
    }

    @Test
    fun aqiPrefersDomesticSourceAndFillsPollutantsFromOm() {
        // 国产源整条优先（不横比），污染物空字段用 OM 浓度补（物理量可跨源）
        val a = src("XIAOMI", 1.0, data(aqi = AqiInfo(value = 42, level = "优", standard = "中国")))
        val withSource = FusionEngine.fuse(FusionEngine.Input(city, listOf(a), now, omAir = omAir(pm25 = 18.0)))
            .aqi!!
        assertEquals(42, withSource.value)
        assertEquals("中国", withSource.standard)
        assertEquals("18", withSource.pm25)
        // 全缺 → 本地 GB 公式
        val noAqi = src("XIAOMI", 1.0, data(current = CurrentWeather(temperature = 20.0)))
        val local = FusionEngine.fuse(FusionEngine.Input(city, listOf(noAqi), now, omAir = omAir(pm25 = 18.0, o3 = 54.0)))
            .aqi!!
        assertEquals("中国", local.standard)
        assertEquals(25, local.value)
    }

    // —— OM 15 分钟降水兜底 ——

    @Test
    fun omMinutelyFallbackConvertsToIntensityWithinWindow() {
        val om = omSupplements(
            minutelyTimes = listOf(now + 900_000L, now + 30 * 60_000L, now + 3 * 3_600_000L),
            minutelyPrecip = listOf(0.2, 0.0, 1.0),
        )
        val minutes = FusionEngine.omMinutelyToMinutes(om, now)
        assertEquals(2, minutes.size) // 超过 2h 的桶剔除
        assertEquals(0.8f, minutes[0].precip, 0.0001f) // mm/15min → mm/h
    }

    // —— 测试夹具 ——

    private fun omAir(pm25: Double? = null, o3: Double? = null) = OmAirQuality(
        timeZoneOffsetSeconds = 28800,
        currentPm25 = pm25, currentPm10 = null, currentCo = null,
        currentNo2 = null, currentSo2 = null, currentO3 = o3,
        hourlyTimeMillis = emptyList(), hourlyPm25 = emptyList(), hourlyPm10 = emptyList(),
        hourlyCo = emptyList(), hourlyNo2 = emptyList(), hourlySo2 = emptyList(), hourlyO3 = emptyList(),
    )

    private fun omSupplements(
        minutelyTimes: List<Long> = emptyList(),
        minutelyPrecip: List<Double?> = emptyList(),
    ) = OmSupplements(
        timeZoneOffsetSeconds = 28800, latitude = null, longitude = null,
        hourlyTimeMillis = emptyList(), uvIndex = emptyList(),
        dailyTime = emptyList(), dailyHigh = emptyList(), dailyLow = emptyList(),
        dailyWeatherCode = emptyList(), dailyWindMaxKmh = emptyList(), dailyGustMaxKmh = emptyList(),
        dailyWindDirDeg = emptyList(), dailyPrecipProbMax = emptyList(), dailyPrecipSumMm = emptyList(),
        dailySunrise = emptyList(), dailySunset = emptyList(), dailyUvMax = emptyList(),
        dailyHumidityMean = emptyList(), dailyCloudMean = emptyList(),
        minutely15TimeMillis = minutelyTimes, minutely15PrecipMm = minutelyPrecip,
    )
}
