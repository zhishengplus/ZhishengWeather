package com.zhisheng.weather.model

import org.junit.Assert.*
import org.junit.Test

class ModuleEvidenceAlignmentTest {
    private val now = 1_791_518_400_000L
    private val clear = CurrentWeather(temperature = 20.0, condition = WeatherCondition.CLEAR, weatherText = "晴")
    private val forecast = HourlyWeather(now, temperature = 25.0, condition = WeatherCondition.CLOUDY, windSpeed = 10.0)
    private val wet = listOf(MinutePrecip(now, 1f), MinutePrecip(now + 60_000, 1f))
    private fun evidence(current: String, minutely: String = "CAIYUN", forecast: String = "OPEN-METEO") = mapOf(
        "current" to WeatherModuleEvidence(current), "minutely" to WeatherModuleEvidence(minutely), "forecast" to WeatherModuleEvidence(forecast))

    @Test fun differentMinuteSourceCannotRewriteCurrentOrItsEvidence() {
        val data = WeatherData(current = clear, rainMinutes = wet,
            rainMeta = RainMeta("CAIYUN", 1), moduleEvidence = evidence("XIAOMI"))
        val result = WeatherConsistency.align(data, now)
        assertEquals(clear, result.current)
        assertEquals(data.rainMinutes, result.rainMinutes)
        assertEquals(data.moduleEvidence, result.moduleEvidence)
    }

    @Test fun differentForecastSourceKeepsExistingHourMeasurements() {
        val data = WeatherData(current = clear, hourly = listOf(forecast), moduleEvidence = evidence("XIAOMI"))
        val result = WeatherConsistency.align(data, now)
        assertEquals(data.hourly, result.hourly)
        assertEquals(clear, result.current)
        assertEquals("OPEN-METEO", result.moduleEvidence.getValue("forecast").source)
    }

    @Test fun differentForecastSourceCannotReceiveCurrentObservationAsExtraHour() {
        val data = WeatherData(current = clear, hourly = listOf(forecast.copy(timeMillis = now + 3_600_000)),
            moduleEvidence = evidence("XIAOMI"))
        assertEquals(data.hourly, WeatherConsistency.align(data, now).hourly)
    }

    @Test fun differentMinuteSourceKeepsItsDryTextWhenCurrentSourceReportsRain() {
        val data = WeatherData(current = clear.copy(condition = WeatherCondition.RAIN, weatherText = "雨", precipMm = 1.0),
            rainNowcast = "未来两小时无降水", rainMinutes = listOf(MinutePrecip(now, 0f)),
            moduleEvidence = evidence("XIAOMI"))
        assertEquals(data.rainNowcast, WeatherConsistency.align(data, now).rainNowcast)
    }

    @Test fun sameSourceStillSynchronizesRainCurrentAndExistingForecast() {
        val data = WeatherData(current = clear, hourly = listOf(forecast), rainMinutes = wet,
            moduleEvidence = evidence("XIAOMI", "XIAOMI", "XIAOMI"))
        val result = WeatherConsistency.align(data, now)
        assertEquals(WeatherCondition.RAIN, result.current!!.condition)
        assertEquals(WeatherCondition.RAIN, result.hourly.single().condition)
        assertEquals(20.0, result.hourly.single().temperature!!, 0.0)
    }

    @Test fun sameSourceStillAddsCurrentToAnExistingFutureSeriesAndReconcilesDryText() {
        val data = WeatherData(current = clear, hourly = listOf(forecast.copy(timeMillis = now + 3_600_000)),
            rainMinutes = wet, rainNowcast = "未来两小时无降水",
            moduleEvidence = evidence("XIAOMI", "XIAOMI", "XIAOMI"))
        val result = WeatherConsistency.align(data, now)
        assertEquals(2, result.hourly.size)
        assertEquals(now, result.hourly.first().timeMillis)
        assertNull(result.rainNowcast)
    }

    @Test fun absentOrUnknownEvidencePreservesHistoricalAlignment() {
        val legacy = WeatherData(current = clear, hourly = listOf(forecast.copy(timeMillis = now + 3_600_000)),
            rainMinutes = wet, rainNowcast = "未来两小时无降水")
        listOf(emptyMap(), evidence("未知"), mapOf("current" to WeatherModuleEvidence("XIAOMI"))).forEach { metadata ->
            val result = WeatherConsistency.align(legacy.copy(moduleEvidence = metadata), now)
            assertEquals(WeatherCondition.RAIN, result.current!!.condition)
            assertEquals(2, result.hourly.size)
            assertEquals(20.0, result.hourly.first().temperature!!, 0.0)
            assertNull(result.rainNowcast)
        }
    }

    @Test fun reorderedLabelsOfSameFusionSourcesStillAlign() {
        val data = WeatherData(current = clear, hourly = listOf(forecast), rainMinutes = wet,
            moduleEvidence = evidence("XIAOMI + OPEN-METEO", "OPEN-METEO + XIAOMI", "OPEN-METEO + XIAOMI"))
        val result = WeatherConsistency.align(data, now)
        assertEquals(WeatherCondition.RAIN, result.current!!.condition)
        assertEquals(20.0, result.hourly.single().temperature!!, 0.0)
    }
}
