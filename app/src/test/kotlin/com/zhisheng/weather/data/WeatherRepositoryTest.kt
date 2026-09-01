package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData

class WeatherRepositoryTest {

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
        assertEquals(true, SourcePref.XIAOMI.matches("XIAOMI"))
        assertEquals(false, SourcePref.OPEN_METEO.matches("XIAOMI"))
        assertEquals(false, SourcePref.QWEATHER.matches("OPEN-METEO"))
        assertEquals(true, SourcePref.CAIYUN.matches("CAIYUN"))
        assertEquals(false, SourcePref.CAIYUN.matches("XIAOMI"))
        assertEquals(true, SourcePref.OPEN_METEO.matches("OPEN-METEO"))
    }

    @Test
    fun qweatherStaysHiddenUntilDeveloperMode() {
        assertEquals(
            listOf(SourcePref.AUTO, SourcePref.XIAOMI, SourcePref.OPEN_METEO),
            SourcePref.visible(developerMode = false),
        )
        assertEquals(
            listOf(
                SourcePref.AUTO,
                SourcePref.XIAOMI,
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
        assertEquals(fetchedAt, WeatherRepository.xiaomiUpdateMillis(XiaomiForecastResult(), fetchedAt))
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
}
