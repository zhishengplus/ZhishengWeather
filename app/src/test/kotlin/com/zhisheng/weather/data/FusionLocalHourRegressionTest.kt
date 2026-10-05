package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherData
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FusionLocalHourRegressionTest {
    @Test fun fusionKeepsLocalHourTimestampsInFractionalTimezoneCities() {
        listOf(19800, 20700, -12600, 28800).forEach { offsetSeconds ->
            val zone = ZoneOffset.ofTotalSeconds(offsetSeconds)
            val first = LocalDate.of(2026, 9, 30).atTime(12, 0).toInstant(zone).toEpochMilli()
            val hours = listOf(HourlyWeather(first, temperature = 20.0),
                HourlyWeather(first + 3_600_000L, temperature = 25.0))
            val data = WeatherData(hourly = hours, utcOffsetSeconds = offsetSeconds)
            val result = FusionEngine.fuse(FusionEngine.Input(
                city = City("测试城市", "", 20.0, 80.0, "local-grid"),
                sources = listOf(FusionEngine.FusionSource("OPEN-METEO", "OPEN-METEO", 1.0, data)),
                nowMillis = first + 45 * 60_000L,
            ))
            assertEquals("The hourly grid must use the city's local hours ($offsetSeconds)",
                hours.map { it.timeMillis }, result.hourly.map { it.timeMillis })
            assertEquals(hours.map { it.temperature }, result.hourly.map { it.temperature })
        }
    }

    @Test fun modelCurrentUsesTheContainingLocalHourInsteadOfAFutureHour() {
        listOf(19800, 20700, -12600, 28800).forEach { offsetSeconds ->
            val zone = ZoneOffset.ofTotalSeconds(offsetSeconds)
            val first = LocalDate.of(2026, 9, 30).atTime(12, 0).toInstant(zone).toEpochMilli()
            // Parse the real provider timezone; the adapter must retain it too.
            val root = kotlinx.serialization.json.Json.parseToJsonElement("""{
                "utc_offset_seconds":$offsetSeconds,
                "hourly":{"time":["2026-09-30T12:00","2026-09-30T13:00"],
                "temperature_2m_ecmwf_ifs025":[20.0,25.0]}}
            """) as kotlinx.serialization.json.JsonObject
            val series = OpenMeteoApi.parseMultiModel(root, kotlinx.serialization.json.JsonObject(emptyMap())).models.single()
            assertEquals(20.0, omModelToWeatherData(series, first + 45 * 60_000L).current?.temperature)
        }
    }
}
