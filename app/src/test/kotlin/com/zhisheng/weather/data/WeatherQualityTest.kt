package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherQualityTest {
    private val now = Instant.parse("2026-10-22T04:30:00Z").toEpochMilli()
    private val city = City("北京", "北京", 39.9, 116.4, "weathercn:101010100")
    private fun sample(source: String = "XIAOMI", time: Long? = now) = WeatherData(
        current = CurrentWeather(temperature = 16.0), updateTime = time, dataSource = source,
        utcOffsetSeconds = 28800, blockSources = mapOf("current" to source),
        hourly = listOf(HourlyWeather(now + 60 * 60_000L, temperature = 18.0)),
        daily = listOf(DailyWeather(now, high = 20.0, low = 10.0)),
    )

    @Test fun oldObservationCannotBeMadeFreshByDownloadingAgain() {
        val data = sample(time = now - WeatherQuality.CURRENT_MAX_AGE_MS - 1).copy(fetchedAt = now)
        val result = WeatherQuality.prepare(data, now)
        assertNull(result.current)
        assertNull(result.updateTime)
        assertFalse(result.blockSources.containsKey("current"))
        assertEquals(data.hourly, result.hourly)
        assertEquals(data.daily, result.daily)
        assertFalse(WeatherQuality.usableCurrent(result))
    }

    @Test fun observationEvidenceOverridesNewerTopLevelTimestamp() {
        val data = sample().copy(moduleEvidence = mapOf("current" to WeatherModuleEvidence("XIAOMI", now - 4 * 3600_000L)))
        assertNull(WeatherQuality.prepare(data, now).current)
    }

    @Test fun impossibleFutureObservationIsRejected() {
        assertNull(WeatherQuality.prepare(sample(time = now + 6 * 60_000), now).current)
    }

    @Test fun unknownObservationTimeIsExplicitAndNeverInvented() {
        val result = WeatherQuality.prepare(sample(time = null), now)
        assertNull(result.updateTime)
        assertNotNull(result.current)
        assertTrue(result.sourceDiagnostics.getValue("current-time").contains("未提供"))
    }

    @Test fun emptyOrSentinelCurrentIsNotUsable() {
        assertNull(WeatherQuality.prepare(sample().copy(current = CurrentWeather(humidity = 30.0)), now).current)
        assertFalse(WeatherQuality.usableCurrent(WeatherQuality.prepare(sample().copy(current = CurrentWeather(humidity = 30.0)), now)))
        assertFalse(WeatherQuality.usableCurrent(WeatherQuality.prepare(sample().copy(current = CurrentWeather(temperature = 9999.0)), now)))
        assertNotNull(WeatherQuality.prepare(WeatherData(), now).error)
    }

    @Test fun expiredShortRangeDataCannotClaimCurrentCoverage() {
        val result = WeatherQuality.prepare(sample().copy(
            aqi = AqiInfo(value = 35), moduleEvidence = mapOf("air" to WeatherModuleEvidence("NMC", now - 4 * 3600_000L)),
            daily = listOf(DailyWeather(now - 24 * 3600_000L, high = 18.0), DailyWeather(now, high = 20.0)),
            alerts = listOf(AlertInfo("已结束", expiresAt = now - 1), AlertInfo("仍有效", expiresAt = now + 1)),
            rainMinutes = listOf(MinutePrecip(now + 60_000, 1f)), rainNowcast = "有雨",
            rainMeta = RainMeta("XIAOMI", 1, now - 2 * 3600_000L), blockSources = mapOf("minutely" to "XIAOMI")), now)
        assertEquals(1, result.daily.size)
        assertEquals("仍有效", result.alerts.single().title)
        assertTrue(result.rainMinutes.isEmpty())
        assertNull(result.rainNowcast)
        assertNull(result.rainMeta)
        assertNull(result.aqi)
        assertFalse(result.moduleEvidence.containsKey("air"))
        assertFalse(result.blockSources.containsKey("minutely"))
    }

    @Test fun automaticSelectionKeepsOneCurrentAndFillsAlignedForecastWithProvenance() {
        val main = sample()
        val donor = sample("NMC").copy(current = CurrentWeather(temperature = 32.0, humidity = 90.0),
            hourly = listOf(main.hourly.single().copy(temperature = 35.0, windSpeed = 12.0)), aqi = AqiInfo(value = 40))
        val result = WeatherQuality.combine(listOf(main, donor), city, true, now)
        assertEquals(main.current, result.current)
        assertEquals(18.0, result.hourly.single().temperature!!, 0.0)
        assertEquals(12.0, result.hourly.single().windSpeed!!, 0.0)
        assertEquals("ZHISHENG", result.dataSource)
        assertEquals("XIAOMI", result.moduleEvidence.getValue("current").source)
        assertEquals("NMC", result.moduleEvidence.getValue("air").source)
        assertTrue(result.moduleEvidence.getValue("forecast").source.contains("NMC"))
    }

    @Test fun stalePreferredSourceFallsBackWithoutReusingItsCurrent() {
        val bad = sample(time = now - 4 * 3600_000L)
        val donor = sample("NMC").copy(current = CurrentWeather(temperature = 15.5))
        val result = WeatherQuality.combine(listOf(bad, donor), city, false, now)
        assertEquals(donor.current, result.current)
        assertEquals("NMC", result.dataSource)
    }

    @Test fun datedSourceWinsOverUnknownTimeAndFailedSourcesCannotFill() {
        val unknown = sample(time = null)
        val dated = sample("NMC")
        val failed = sample("TENCENT").copy(error = "failure", aqi = AqiInfo(value = 2))
        val result = WeatherQuality.combine(listOf(unknown, dated, failed), city, false, now)
        assertEquals("NMC", result.dataSource)
        assertNull(result.aqi)
        assertNotNull(WeatherQuality.combine(listOf(failed), city, true, now).error)
    }

    @Test fun currentFieldsRequireKnownAlignedTimesAndDoNotBorrowPressure() {
        val base = sample()
        val donor = sample("OPEN-METEO").copy(current = CurrentWeather(temperature = 30.0, humidity = 70.0, pressure = 1020.0))
        assertNull(fillMissingWeather(base.copy(updateTime = null), donor).current!!.humidity)
        assertNull(fillMissingWeather(base, donor.copy(updateTime = now - 61 * 60_000)).current!!.humidity)
        val filled = fillMissingWeather(base, donor)
        assertEquals(70.0, filled.current!!.humidity!!, 0.0)
        assertNull(filled.current!!.pressure)
    }

    @Test fun modelCurrentRetainsItsNatureAndTimestamp() {
        val result = WeatherQuality.prepare(sample("OPEN-METEO"), now)
        assertEquals(now, result.updateTime)
        assertTrue(result.sourceDiagnostics.getValue("current-time").contains("模型"))
    }

    @Test fun retrievalTimestampDoesNotReplaceModuleObservationOrEarlierDonorFetch() {
        val data = sample().copy(moduleEvidence = mapOf(
            "current" to WeatherModuleEvidence("XIAOMI", now - 60_000),
            "air" to WeatherModuleEvidence("NMC", now - 120_000, now - 30_000)))
        val result = WeatherRepository.markSuccessfulFetch(data, now)
        assertEquals(now, result.moduleEvidence.getValue("current").fetchedAt)
        assertEquals(now - 60_000, result.moduleEvidence.getValue("current").observationTime)
        assertEquals(now - 30_000, result.moduleEvidence.getValue("air").fetchedAt)
    }

    @Test fun moduleOverrideUsesActualCurrentProviderForModelAndPressureNotes() {
        val base = stampModules(WeatherQuality.prepare(sample("OPEN-METEO"), now), city)
        val donor = stampModules(sample("QWEATHER").copy(current = CurrentWeather(temperature = 18.0, pressure = 1020.0)), city)
        val result = WeatherQuality.prepare(replaceModule(base, donor, WeatherModule.CURRENT), now)
        assertFalse(result.sourceDiagnostics.containsKey("current-time"))
        assertTrue(result.sourceDiagnostics.getValue("pressure-reference").contains("海平面"))
        assertEquals("QWEATHER", result.moduleEvidence.getValue("current").source)
    }
}
