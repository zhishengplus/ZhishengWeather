package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import kotlinx.serialization.json.Json

class WeatherRepositoryTest {

    @Test
    fun parallelSupplementsKeepPrimaryValuesAndEachCompletedBlockProvenance() {
        val base = WeatherData(
            current = CurrentWeather(temperature = 21.0),
            hourly = listOf(HourlyWeather(timeMillis = 1L, temperature = 21.0)),
            blockSources = mapOf("current" to "XIAOMI", "hourly" to "XIAOMI"),
            dataSource = "ZHISHENG",
        )
        val hourly = base.copy(
            hourly = listOf(HourlyWeather(timeMillis = 2L, temperature = 22.0)),
            blockSources = base.blockSources + ("hourly" to "OPEN-METEO"),
        )
        val current = base.copy(
            current = base.current!!.copy(dewPoint = 10.0),
            blockSources = base.blockSources + ("current-supplement" to "OPEN-METEO"),
        )
        val result = WeatherRepository.mergeCompletedSupplements(base, null, hourly, current)
        assertEquals(21.0, result.current!!.temperature!!, 0.0)
        assertEquals(10.0, result.current!!.dewPoint!!, 0.0)
        assertEquals(2L, result.hourly.single().timeMillis)
        assertEquals("OPEN-METEO", result.blockSources["hourly"])
        assertEquals("XIAOMI", result.blockSources["current"])
        assertEquals("OPEN-METEO", result.blockSources["current-supplement"])
        assertEquals("ZHISHENG", result.dataSource)
    }

    @Test
    fun missingSupplementsKeepUsablePrimaryWeatherIntact() {
        val base = WeatherData(current = CurrentWeather(temperature = 21.0), dataSource = "XIAOMI")
        assertEquals(base, WeatherRepository.mergeCompletedSupplements(base, null, null, null))
    }

    @Test
    fun xiaomiHourlyGenericRainIsMappedWithoutDroppingTemperatureOrTime() {
        val result = XiaomiForecastResult(forecastHourly = XiaomiForecastHourly(
            temperature = XiaomiIntList(pubTime = "2026-09-18T03:00:00+08:00", value = listOf(16, 16, 15, 14)),
            weather = XiaomiIntList(value = listOf(301, 301, 302, 999999)),
        ))
        val hours = WeatherRepository.mapXiaomiToWeatherData(result, "weathercn:101160106").hourly
        assertEquals(listOf(WeatherCondition.RAIN, WeatherCondition.RAIN, WeatherCondition.SNOW, WeatherCondition.UNKNOWN), hours.map { it.condition })
        assertEquals(listOf(16.0, 16.0, 15.0, 14.0), hours.map { it.temperature })
        assertEquals(java.time.Instant.parse("2026-09-17T19:00:00Z").toEpochMilli(), hours.first().timeMillis)
        assertEquals(3_600_000L, hours[1].timeMillis - hours[0].timeMillis)
        assertEquals("301", hours.first().profile?.rawCode)
    }

    @Test
    fun successfulFetchKeepsProviderTimeAndRecordsCompletionTimeSeparately() {
        val providerTime = 1_725_000_000_000L
        val fetchedAt = providerTime + 14 * 60_000L
        val data = WeatherData(
            current = CurrentWeather(temperature = 20.0),
            updateTime = providerTime,
        )

        val marked = WeatherRepository.markSuccessfulFetch(data, fetchedAt)

        assertEquals(providerTime, marked.updateTime)
        assertEquals(fetchedAt, marked.fetchedAt)
    }

    @Test
    fun failedFetchDoesNotPretendToHaveANewRefreshTime() {
        val failed = WeatherData(error = "请求失败", updateTime = 1L)

        val marked = WeatherRepository.markSuccessfulFetch(failed, 2L)

        assertNull(marked.fetchedAt)
    }

    @Test
    fun windDirectionHandlesCardinalAndBoundaryValues() {
        assertEquals("北", WeatherRepository.windDirection(0.0))
        assertEquals("东北", WeatherRepository.windDirection(22.5))
        assertEquals("东", WeatherRepository.windDirection(90.0))
        assertEquals("北", WeatherRepository.windDirection(360.0))
    }

    @Test
    fun windDirectionNormalizesOutOfRangeProviderValues() {
        assertEquals("西", WeatherRepository.windDirection(-90.0))
        assertEquals("东", WeatherRepository.windDirection(450.0))
        assertEquals("北", WeatherRepository.windDirection(720.0))
    }

    @Test
    fun windDirectionRejectsMissingAndNonFiniteValues() {
        assertNull(WeatherRepository.windDirection(null))
        assertNull(WeatherRepository.windDirection(Double.NaN))
        assertNull(WeatherRepository.windDirection(Double.POSITIVE_INFINITY))
        assertNull(WeatherRepository.windDirection(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun lockedSourceDoesNotAcceptAnotherProvidersCache() {
        assertEquals(true, SourcePref.AUTO.matches("XIAOMI"))
        assertEquals(true, SourcePref.AUTO.matches("OPEN-METEO"))
        assertEquals(false, SourcePref.AUTO.matches("CAIYUN"))
        assertEquals(false, SourcePref.AUTO.matches("QWEATHER"))
        // AUTO 不读融合源缓存（切换时重拉一次，语义严格一致）
        assertEquals(false, SourcePref.AUTO.matches("ZHISHENG"))
        assertEquals(true, SourcePref.XIAOMI.matches("XIAOMI"))
        assertEquals(false, SourcePref.OPEN_METEO.matches("XIAOMI"))
        assertEquals(false, SourcePref.QWEATHER.matches("OPEN-METEO"))
        assertEquals(true, SourcePref.CAIYUN.matches("CAIYUN"))
        assertEquals(false, SourcePref.CAIYUN.matches("XIAOMI"))
        assertEquals(true, SourcePref.OPEN_METEO.matches("OPEN-METEO"))
        // 枳生天气源身份只由 dataSource 确定
        assertEquals(true, SourcePref.ZHISHENG.matches("ZHISHENG"))
        assertEquals(false, SourcePref.ZHISHENG.matches("XIAOMI"))
        assertEquals(false, SourcePref.XIAOMI.matches("ZHISHENG"))
    }

    @Test
    fun qweatherStaysHiddenUntilDeveloperMode() {
        assertEquals(
            listOf(SourcePref.AUTO, SourcePref.ZHISHENG, SourcePref.XIAOMI, SourcePref.NMC, SourcePref.OPEN_METEO),
            SourcePref.visible(developerMode = false),
        )
        assertEquals(
            listOf(
                SourcePref.AUTO,
                SourcePref.ZHISHENG,
                SourcePref.XIAOMI,
                SourcePref.NMC,
                SourcePref.OPEN_METEO,
                SourcePref.CAIYUN,
                SourcePref.QWEATHER,
            ),
            SourcePref.visible(developerMode = true),
        )
    }

    @Test
    fun qweatherLockFallsBackToAutoWithoutDeveloperMode() {
        assertEquals(SourcePref.AUTO, SourcePref.QWEATHER.effective(developerMode = false))
        assertEquals(SourcePref.QWEATHER, SourcePref.QWEATHER.effective(developerMode = true))
        assertEquals(SourcePref.AUTO, SourcePref.CAIYUN.effective(developerMode = false))
        assertEquals(SourcePref.CAIYUN, SourcePref.CAIYUN.effective(developerMode = true))
        assertEquals(SourcePref.AUTO, SourcePref.AUTO.effective(developerMode = false))
        assertEquals(SourcePref.XIAOMI, SourcePref.XIAOMI.effective(developerMode = false))
        // 旧版本持久化的 "amap"/"baidu" 偏好自动回落 AUTO
        assertEquals(SourcePref.AUTO, SourcePref.from("amap"))
        assertEquals(SourcePref.AUTO, SourcePref.from("baidu"))
    }

    @Test
    fun xiaomiNowcastFillsDistanceTemplateInsteadOfLeakingPrintfToken() {
        assertEquals(
            "降水在 38 公里外",
            WeatherRepository.fillXiaomiDistancePlaceholder("降水在 %d 公里外", "38"),
        )
        assertEquals(
            "降水在 12.5 公里外",
            WeatherRepository.fillXiaomiDistancePlaceholder("降水在 %s 公里外", "12.5"),
        )
        assertEquals(
            "降水在附近",
            WeatherRepository.fillXiaomiDistancePlaceholder("降水在 %d 公里外", null),
        )
        assertEquals(
            "未来两小时无降水",
            WeatherRepository.fillXiaomiDistancePlaceholder("未来两小时无降水", "38"),
        )
    }

    @Test
    fun openMeteoSupplementRestoresMissingXiaomiTelemetryWithoutOverwritingProviderValues() {
        val source = WeatherData(
            current = CurrentWeather(
                temperature = 18.0,
                condition = WeatherCondition.OVERCAST,
                precipMm = 0.0,
                visibility = 18.0,
                dewPoint = null,
                cloudCover = null,
                windGust = null,
            ),
            dataSource = "XIAOMI",
        )
        val merged = WeatherRepository.mergeCurrentSupplement(
            source,
            OpenMeteoResult(
                current = OpenMeteoCurrent(
                    visibility = 9_000.0,
                    dew_point_2m = 7.5,
                    cloud_cover = 62.0,
                    wind_gusts_10m = 28.0,
                ),
            ),
        )

        assertEquals(18.0, merged.current?.visibility)
        assertEquals(7.5, merged.current?.dewPoint)
        assertEquals(62.0, merged.current?.cloudCover)
        assertEquals(28.0, merged.current?.windGust)
        assertEquals(18.0, merged.current?.temperature)
        assertEquals(WeatherCondition.OVERCAST, merged.current?.condition)
        assertEquals(0.0, merged.current?.precipMm)
        assertEquals("OPEN-METEO", merged.blockSources["current-supplement"])
    }

    @Test
    fun autoHourlyPrecipSupplementFillsXiaomiGapsWithoutReplacingProviderHours() {
        val noon = java.time.Instant.parse("2026-09-07T04:00:00Z").toEpochMilli()
        val xiaomi = listOf(
            HourlyWeather(timeMillis = noon, temperature = 28.0, precipProb = null),
            HourlyWeather(timeMillis = noon + 3_600_000L, temperature = 29.0, precipProb = 40),
        )
        val openMeteo = listOf(
            HourlyWeather(timeMillis = noon, temperature = 17.0, precipProb = 15),
            HourlyWeather(timeMillis = noon + 3_600_000L, temperature = 18.0, precipProb = 80),
        )

        val merged = WeatherRepository.mergeHourlyPrecipSupplement(xiaomi, openMeteo)

        assertEquals(28.0, merged[0].temperature)
        assertEquals(15, merged[0].precipProb)
        assertEquals(40, merged[1].precipProb)
        val alreadyFilled = xiaomi.map { it.copy(precipProb = 5) }
        assertEquals(alreadyFilled, WeatherRepository.mergeHourlyPrecipSupplement(alreadyFilled, openMeteo))
    }

    @Test
    fun nearlyExpiredHourlyForecastNeedsFallbackEvenWithTwoEntries() {
        val now = java.time.Instant.parse("2026-09-27T02:30:00Z").toEpochMilli()
        val hour = 3_600_000L
        val short = listOf(
            HourlyWeather(timeMillis = now - hour),
            HourlyWeather(timeMillis = now + hour),
        )
        assertEquals(false, WeatherRepository.hasUsefulHourlyForecast(short, now))
        val sufficient = (1..12).map { HourlyWeather(timeMillis = now + it * hour) }
        assertEquals(true, WeatherRepository.hasUsefulHourlyForecast(sufficient, now))
    }

    @Test
    fun qweatherPaidIndicesKeepEveryReturnedTypeWithoutDuplicatingDedicatedCards() {
        val mapped = WeatherRepository.qweatherLifeIndices(
            QwIndices(
                daily = listOf(
                    QwIndexItem(type = "1", name = "运动指数", category = "适宜"),
                    QwIndexItem(type = "2", name = "洗车指数", category = "不宜"),
                    QwIndexItem(type = "3", name = "穿衣指数", category = "舒适"),
                    QwIndexItem(type = "7", name = "过敏指数", category = "较易发"),
                    QwIndexItem(type = "16", name = "防晒指数", category = "强"),
                ),
            ),
        )

        assertEquals(listOf("穿衣", "过敏", "防晒"), mapped.map { it.name })
        assertEquals(listOf("舒适", "较易发", "强"), mapped.map { it.category })
    }

    @Test
    fun qweatherLifeIndicesOmitEntriesWithoutDisplayValue() {
        val mapped = WeatherRepository.qweatherLifeIndices(
            QwIndices(
                daily = listOf(
                    QwIndexItem(type = "3", name = "穿衣指数", category = ""),
                    QwIndexItem(type = "10", name = "空气污染扩散条件指数", category = "良"),
                ),
            ),
        )

        assertEquals(listOf("空气扩散"), mapped.map { it.name })
        assertEquals(listOf("AIR"), mapped.map { it.en })
    }

    @Test
    fun onlyAutoMaySupplementFromAnotherSource() {
        assertEquals(true, WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.AUTO))
        assertEquals(false, WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.QWEATHER))
        assertEquals(false, WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.CAIYUN))
        assertEquals(false, WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.XIAOMI))
        assertEquals(false, WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.OPEN_METEO))
        assertEquals(false, WeatherRepository.shouldFillMissingHourlyPrecip(SourcePref.QWEATHER))
        assertEquals(false, WeatherRepository.shouldFillMissingHourlyPrecip(SourcePref.XIAOMI))
        assertEquals(false, WeatherRepository.shouldFillMissingHourlyPrecip(SourcePref.OPEN_METEO))
    }

    @Test
    fun xiaomiDailyDatesSnapToLocalMidnight() {
        val pub = java.time.Instant.parse("2026-08-26T14:00:00Z").toEpochMilli()
        val zone = java.time.ZoneOffset.ofHours(8)
        val day0 = WeatherRepository.xiaomiDailyDateMillis(pub, 0, 8 * 3_600)
        val day1 = WeatherRepository.xiaomiDailyDateMillis(pub, 1, 8 * 3_600)
        assertEquals(
            java.time.LocalDate.of(2026, 8, 26),
            java.time.Instant.ofEpochMilli(day0).atZone(zone).toLocalDate(),
        )
        assertEquals(0, java.time.Instant.ofEpochMilli(day0).atZone(zone).hour)
        assertEquals(
            java.time.LocalDate.of(2026, 8, 27),
            java.time.Instant.ofEpochMilli(day1).atZone(zone).toLocalDate(),
        )
    }

    @Test
    fun preferredAirIndexPicksChinaScaleOverUs() {
        val us = QwAirIndex(code = "us-epa", aqi = 120.0)
        val cn = QwAirIndex(code = "cn-mee", aqi = 80.0)
        assertEquals(80.0, WeatherRepository.preferredAirIndex(listOf(us, cn))?.aqi!!, 0.0001)
    }

    @Test
    fun usAqiDoesNotReuseChinaModerateBand() {
        assertEquals("对敏感人群不健康", WeatherRepository.usAqiLevel(120))
        assertEquals("轻度污染", WeatherRepository.aqiLevel(120))
    }

    @Test
    fun xiaomiHourlySnapsPublishTimeToLocalHour() {
        val pub = java.time.Instant.parse("2026-08-21T02:20:00Z").toEpochMilli()
        val zone = java.time.ZoneOffset.ofHours(8)
        val hour0 = WeatherRepository.xiaomiHourlyMillis(pub, 0, 8 * 3_600)
        val hour1 = WeatherRepository.xiaomiHourlyMillis(pub, 1, 8 * 3_600)
        assertEquals(10, java.time.Instant.ofEpochMilli(hour0).atZone(zone).hour)
        assertEquals(0, java.time.Instant.ofEpochMilli(hour0).atZone(zone).minute)
        assertEquals(11, java.time.Instant.ofEpochMilli(hour1).atZone(zone).hour)
    }

    @Test
    fun precipAmountConvertsProviderUnitsToMillimetres() {
        assertEquals(12.0, WeatherRepository.precipToMm(QwVal(1.2, "cm"))!!, 0.0001)
        assertEquals(5.0, WeatherRepository.precipToMm(QwVal(5.0, "mm"))!!, 0.0001)
        assertNull(WeatherRepository.precipToMm(QwVal(-1.0, "mm")))
        assertNull(WeatherRepository.precipToMm(QwVal(5.0, "unknown")))
    }

    @Test
    fun providerProbabilityAcceptsRatioPercentAndPercentSign() {
        assertEquals(40, WeatherRepository.normalizeProviderProbability("0.4"))
        assertEquals(40, WeatherRepository.normalizeProviderProbability("40"))
        assertEquals(40, WeatherRepository.normalizeProviderProbability("40%"))
        assertNull(WeatherRepository.normalizeProviderProbability("140"))
        assertNull(WeatherRepository.normalizeProviderProbability("unknown"))
    }

    @Test
    fun xiaomiCurrentUnitsAreNormalizedToTheInternalContract() {
        assertEquals(36.0, WeatherRepository.xiaomiWindKmh(XiaomiUnitValue("m/s", "10")) ?: -1.0, 0.0001)
        assertEquals(18.0, WeatherRepository.xiaomiWindKmh(XiaomiUnitValue("km/h", "18")) ?: -1.0, 0.0001)
        assertEquals(1013.25, WeatherRepository.xiaomiPressureHpa(XiaomiUnitValue("Pa", "101325")) ?: -1.0, 0.0001)
        assertEquals(8.5, WeatherRepository.xiaomiDistanceKm(XiaomiUnitValue("m", "8500")) ?: -1.0, 0.0001)
        assertNull(WeatherRepository.xiaomiPressureHpa(XiaomiUnitValue("unknown", "1013")))
    }

    @Test
    fun xiaomiUpdateUsesProviderObservationTimeBeforeDownloadTime() {
        val fetchedAt = java.time.Instant.parse("2026-08-31T02:00:00Z").toEpochMilli()
        val result = XiaomiForecastResult(
            current = XiaomiCurrent(pubTime = "2026-08-31T09:45:00+08:00"),
            updateTime = "2026-08-31T09:40:00+08:00",
        )

        assertEquals(
            java.time.Instant.parse("2026-08-31T01:45:00Z").toEpochMilli(),
            WeatherRepository.xiaomiUpdateMillis(result, fetchedAt),
        )
        assertNull(WeatherRepository.xiaomiUpdateMillis(XiaomiForecastResult(), fetchedAt))
    }

    @Test
    fun xiaomiMapperUsesProviderTimesDirectionsAndDailyAqiWithoutCrossSourceData() {
        val result = XiaomiForecastResult(
            current = XiaomiCurrent(
                pubTime = "2026-09-02T21:30:00+08:00",
                temperature = XiaomiUnitValue("°C", "28"),
                weather = "1",
            ),
            forecastHourly = XiaomiForecastHourly(
                pubTime = "2026-09-02T20:15:00+08:00",
                temperature = XiaomiIntList(value = listOf(28)),
                weather = XiaomiIntList(value = listOf(1)),
                wind = XiaomiHourlyWind(
                    listOf(XiaomiHourlyWindValue("2026-09-02T22:00:00+08:00", "315.95", "15.43")),
                ),
                aqi = XiaomiIntList(value = listOf(21)),
            ),
            forecastDaily = XiaomiForecastDaily(
                pubTime = "2026-09-02T20:00:00+08:00",
                temperature = XiaomiDailyTemperature(value = listOf(XiaomiFromTo("31", "23"))),
                weather = XiaomiDailyWeather(value = listOf(XiaomiFromTo("1", "2"))),
                aqi = XiaomiIntList(value = listOf(34)),
                wind = XiaomiDailyWind(
                    direction = XiaomiFromToList(listOf(XiaomiFromTo("350", "10"))),
                    speed = XiaomiFromToList(listOf(XiaomiFromTo("12", "16"))),
                ),
            ),
        )

        val mapped = WeatherRepository.mapXiaomiToWeatherData(result, "weathercn:101280701")

        assertEquals("XIAOMI", mapped.dataSource)
        assertEquals(java.time.Instant.parse("2026-09-02T14:00:00Z").toEpochMilli(), mapped.hourly.single().timeMillis)
        assertEquals(315.95, mapped.hourly.single().windDirectionDeg ?: -1.0, 0.001)
        assertEquals(21, mapped.hourly.single().aqi)
        assertNull(mapped.hourly.single().precipProb)
        assertEquals(34, mapped.daily.single().aqi)
        assertTrue((mapped.daily.single().windDirectionDeg ?: 180.0) < 1.0)
    }

    @Test
    fun xiaomiIndicesOnlyFillMissingCurrentFields() {
        val indices = XiaomiIndices(
            listOf(
                XiaomiIndexItem("feelsLike", "31"),
                XiaomiIndexItem("humidity", "73"),
                XiaomiIndexItem("pressure", "994"),
                XiaomiIndexItem("uvIndex", "2"),
            ),
        )
        val filled = WeatherRepository.mapXiaomiToWeatherData(
            XiaomiForecastResult(current = XiaomiCurrent(temperature = XiaomiUnitValue(value = "28"), weather = "1"), indices = indices),
        ).current
        val preserved = WeatherRepository.mapXiaomiToWeatherData(
            XiaomiForecastResult(
                current = XiaomiCurrent(
                    temperature = XiaomiUnitValue(value = "28"),
                    feelsLike = XiaomiUnitValue(value = "29"),
                    humidity = XiaomiUnitValue(value = "65"),
                    pressure = XiaomiUnitValue("hPa", "1001"),
                    uvIndex = "5",
                    weather = "1",
                ),
                indices = indices,
            ),
        ).current

        assertEquals(31.0, filled?.feelsLike ?: -1.0, 0.001)
        assertEquals(73.0, filled?.humidity ?: -1.0, 0.001)
        assertEquals(994.0, filled?.pressure ?: -1.0, 0.001)
        assertEquals(2, filled?.uvIndex)
        assertEquals(29.0, preserved?.feelsLike ?: -1.0, 0.001)
        assertEquals(65.0, preserved?.humidity ?: -1.0, 0.001)
        assertEquals(1001.0, preserved?.pressure ?: -1.0, 0.001)
        assertEquals(5, preserved?.uvIndex)
    }

    @Test
    fun xiaomiMapperKeepsYesterdayAlertAndTyphoonProviderDetails() {
        val mapped = WeatherRepository.mapXiaomiToWeatherData(
            XiaomiForecastResult(
                alerts = listOf(XiaomiAlert(title = "台风白色预警", type = "台风", level = "白色")),
                yesterday = XiaomiYesterday(
                    date = "2026-09-01T12:00:00+08:00",
                    tempMax = "31",
                    tempMin = "23",
                    aqi = "20",
                    weatherStart = "8",
                    weatherEnd = "4",
                    sunRise = "2026-09-01T06:08:00+08:00",
                    sunSet = "2026-09-01T18:44:00+08:00",
                    windDircStart = "348",
                    windDircEnd = "348",
                    windSpeedStart = "16",
                    windSpeedEnd = "16",
                ),
                typhoon = listOf(
                    XiaomiTyphoon("科罗旺", "KROVANH", "2624", "TS", 23.7, 131.5, 18.0),
                ),
            ),
        )

        assertEquals("台风", mapped.alerts.single().type)
        assertEquals("06:08", mapped.yesterday?.sunrise)
        assertEquals("18:44", mapped.yesterday?.sunset)
        assertEquals(348.0, mapped.yesterday?.windDirectionStartDeg ?: -1.0, 0.001)
        assertEquals("2624", mapped.typhoons.single().id)
        assertEquals("XIAOMI", mapped.typhoons.single().source)
        assertEquals(23.7, mapped.typhoons.single().latitude ?: -1.0, 0.001)
        assertEquals(131.5, mapped.typhoons.single().longitude ?: -1.0, 0.001)
    }

    @Test
    fun dailyDirectionUsesCircularMeanAcrossNorth() {
        assertEquals(0.0, WeatherRepository.meanDirectionDeg("350", "10") ?: -1.0, 0.001)
        assertEquals(90.0, WeatherRepository.meanDirectionDeg("90", null) ?: -1.0, 0.001)
        assertNull(WeatherRepository.meanDirectionDeg(null, "invalid"))
    }

    @Test
    fun userFacingFetchErrorDoesNotExposeExceptionText() {
        val message = WeatherRepository.userFacingFetchError("小米天气")
        assertEquals("小米天气暂时无法获取，请检查网络后重试", message)
        assertEquals(false, message.contains("Exception"))
        assertEquals(false, message.contains("timeout"))
        assertEquals(false, message.contains("Unable to resolve host"))
    }

    @Test
    fun unknownPaidLifeIndexKeepsProviderShortName() {
        val mapped = WeatherRepository.qweatherLifeIndices(
            QwIndices(
                daily = listOf(
                    QwIndexItem(type = "21", name = "路况指数", category = "较好"),
                ),
            ),
        )
        assertEquals(listOf("路况"), mapped.map { it.name })
        assertEquals(listOf("INDEX 21"), mapped.map { it.en })
    }

    @Test
    fun xiaomiMinutelyMatchesTheRealArrayAndStringResponseShape() {
        val parsed = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }.decodeFromString<XiaomiForecastResult>(
            """{
                "current":{"temperature":{"value":"20"},"weather":"1"},
                "minutely":{"precipitation":{
                    "probability":[0,25,0,0],
                    "weather":"1",
                    "kmNum":0,
                    "value":[0,0.2,0]
                }}
            }""".trimIndent(),
        )

        assertEquals(listOf(0.0, 25.0, 0.0, 0.0), parsed.minutely?.precipitation?.probability)
        assertEquals("1", parsed.minutely?.precipitation?.weather)
        assertEquals("0", parsed.minutely?.precipitation?.kmNum)
    }
}
