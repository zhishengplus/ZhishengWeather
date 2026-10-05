package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class WeatherProviderAuditRegressionTest {
    @Test fun modelOnlyFusionRetainsProviderTimezoneWithoutSupplements() {
        val root = Json.parseToJsonElement("""{
            "latitude":35.7,"longitude":139.7,"utc_offset_seconds":32400,
            "hourly":{"time":["2026-09-30T12:00"],"temperature_2m_ecmwf_ifs025":[25.0]}
        }""") as JsonObject
        val model = OpenMeteoApi.parseMultiModel(root, JsonObject(emptyMap())).models.single()
        val now = Instant.parse("2026-09-30T03:15:00Z").toEpochMilli()
        val adapted = omModelToWeatherData(model, now)

        assertEquals(32400, adapted.utcOffsetSeconds)
        assertEquals(Instant.parse("2026-09-30T03:00:00Z").toEpochMilli(), adapted.hourly.single().timeMillis)
        val fused = FusionEngine.fuse(FusionEngine.Input(
            city = City("东京", "日本", 35.7, 139.7, "tokyo"),
            sources = listOf(FusionEngine.FusionSource("OPEN-METEO:ecmwf_ifs025", "OPEN-METEO", 1.0, adapted)),
            nowMillis = now,
            omSupplements = null,
        ))

        assertEquals(32400, fused.utcOffsetSeconds)
        val displayed = Instant.ofEpochMilli(fused.hourly.single().timeMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(requireNotNull(fused.utcOffsetSeconds)))
        assertEquals(12, displayed.hour)
        assertEquals(0, displayed.minute)
        assertEquals(25.0, fused.hourly.single().temperature!!, 0.001)
    }

    @Test fun tencentDailyRetainsDominantNightPhenomenon() {
        val now = Instant.parse("2026-09-30T04:00:00Z").toEpochMilli()
        phenomena.forEach { sample ->
            val converted = tencentToWeatherData(TencentWeatherApi.TencentBundle(
                observe = null,
                daily = listOf(TencentWeatherApi.TencentDaily(
                    date = "2026-09-30", high = 30.0, low = 22.0,
                    dayText = sample.dayText, dayCode = sample.dayCode?.padStart(2, '0'),
                    nightText = sample.nightText, nightCode = sample.nightCode?.padStart(2, '0'), aqi = null,
                )),
                indices = emptyList(), alarms = emptyList(),
            ), now)!!
            val day = converted.daily.single()

            assertEquals(sample.expectedCondition, day.condition)
            assertEquals(sample.expectedText, day.weatherText)
            day.profile?.let { assertEquals(sample.expectedCondition, it.condition) }
        }
    }

    @Test fun weatherComCnDailyRetainsDominantNightPhenomenon() {
        val now = Instant.parse("2026-09-30T04:00:00Z").toEpochMilli()
        phenomena.forEach { sample ->
            val converted = wcnToWeatherData(WeatherComCnApi.WcnBundle(
                areaId = "101010100", current = null,
                today = WeatherComCnApi.WcnToday(
                    high = 30.0, low = 22.0, dayText = sample.dayText, nightText = sample.nightText,
                    dayCode = sample.dayCode, nightCode = sample.nightCode,
                    windText = null, windPower = null,
                ),
                indices = emptyList(), alarms = emptyList(),
            ), now)!!
            val day = converted.daily.single()

            assertEquals(sample.expectedCondition, day.condition)
            assertEquals(sample.expectedText, day.weatherText)
            day.profile?.let { assertEquals(sample.expectedCondition, it.condition) }
        }
    }

    private data class Phenomena(
        val dayCode: String?, val dayText: String?, val nightCode: String?, val nightText: String?,
        val expectedCondition: WeatherCondition, val expectedText: String,
    )

    private val phenomena = listOf(
        Phenomena("0", "晴", "4", "雷阵雨", WeatherCondition.THUNDERSTORM, "晴转雷阵雨"),
        Phenomena("4", "雷阵雨", "0", "晴", WeatherCondition.THUNDERSTORM, "雷阵雨转晴"),
        Phenomena(null, null, "15", "中雪", WeatherCondition.SNOW, "中雪"),
        Phenomena("0", "晴", null, null, WeatherCondition.CLEAR, "晴"),
    )
}
