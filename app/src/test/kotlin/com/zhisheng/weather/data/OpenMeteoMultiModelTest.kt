package com.zhisheng.weather.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Open-Meteo 多模式 / 补充包 / 空气质量的 JsonObject 解析测试。
 * fixture 为 2026-09-14 实测结构的截取（含 cma/jma 无降水概率、吸附坐标等真实行为）。
 */
class OpenMeteoMultiModelTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun multiModelParsesSuffixedKeysAndToleratesMissingPrecipProb() {
        // cma 的 precipProb 列存在但全 null（真实行为）→ 该列无票，但温度列照常。
        val tier1 = obj(
            """
            {"latitude":40.0,"longitude":116.25,"utc_offset_seconds":28800,
             "hourly":{"time":["2026-09-14T12:00","2026-09-14T13:00"],
              "temperature_2m_ecmwf_ifs025":[25.7,26.1],
              "relative_humidity_2m_ecmwf_ifs025":[30.0,31.0],
              "precipitation_probability_ecmwf_ifs025":[0,5],
              "precipitation_ecmwf_ifs025":[0.0,0.0],
              "wind_speed_10m_ecmwf_ifs025":[4.1,4.3],
              "wind_direction_10m_ecmwf_ifs025":[187.0,190.0],
              "wind_gusts_10m_ecmwf_ifs025":[10.0,11.0],
              "visibility_ecmwf_ifs025":[24000.0,24000.0],
              "cloud_cover_ecmwf_ifs025":[0.0,2.0],
              "surface_pressure_ecmwf_ifs025":[1002.0,1001.0],
              "temperature_2m_cma_grapes_global":[21.9,22.5],
              "relative_humidity_2m_cma_grapes_global":[40.0,41.0],
              "precipitation_probability_cma_grapes_global":[null,null],
              "precipitation_cma_grapes_global":[0.0,0.0],
              "wind_speed_10m_cma_grapes_global":[9.3,9.0],
              "wind_direction_10m_cma_grapes_global":[240.0,245.0],
              "wind_gusts_10m_cma_grapes_global":[15.0,16.0],
              "visibility_cma_grapes_global":[20000.0,20000.0],
              "cloud_cover_cma_grapes_global":[10.0,12.0],
              "surface_pressure_cma_grapes_global":[1003.0,1002.0]
             }}
            """.trimIndent()
        )
        val parsed = OpenMeteoApi.parseMultiModel(tier1, obj("{}"))
        assertEquals(2, parsed.models.size)
        assertEquals(40.0, parsed.latitude!!, 0.001) // 吸附网格坐标（39.9042 → 40.0）
        val ecmwf = parsed.models.first { it.model == "ecmwf_ifs025" }
        assertEquals(2, ecmwf.timeMillis.size)
        assertEquals(25.7, ecmwf.temperature[0]!!, 0.001)
        assertEquals(0.0, ecmwf.precipProb[0]!!, 0.001)
        assertEquals(24000.0, ecmwf.visibilityMeters[0]!!, 0.001)
        val cma = parsed.models.first { it.model == "cma_grapes_global" }
        assertNull(cma.precipProb[0]) // null → 无票（不造数）
        assertEquals(21.9, cma.temperature[0]!!, 0.001)
    }

    @Test
    fun modelWithoutColumnIsAbsent() {
        // 模型列缺失（超载/未覆盖）→ 不占席位
        val tier1 = obj(
            """
            {"latitude":39.9,"longitude":116.4,"utc_offset_seconds":28800,
             "hourly":{"time":["2026-09-14T12:00"],
              "wind_speed_10m_ecmwf_ifs025":[4.1]}}
            """.trimIndent()
        )
        assertEquals(0, OpenMeteoApi.parseMultiModel(tier1, obj("{}")).models.size)
    }

    @Test
    fun timeAxisIsSharedAndConvertedToRealEpoch() {
        // time 键无后缀；本地壁钟字符串按 utc_offset_seconds 还原为真实 epoch
        val tier1 = obj(
            """
            {"latitude":40.0,"longitude":116.25,"utc_offset_seconds":28800,
             "hourly":{"time":["2026-09-14T12:00"],
              "temperature_2m_ecmwf_ifs025":[25.7]}}
            """.trimIndent()
        )
        val parsed = OpenMeteoApi.parseMultiModel(tier1, obj("{}"))
        val expected = java.time.LocalDateTime.of(2026, 9, 14, 12, 0)
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli() - 28800_000L
        assertEquals(expected, parsed.models[0].timeMillis[0])
    }

    @Test
    fun tier2LeanColumnsParseWindAndTemperature() {
        val tier2 = obj(
            """
            {"latitude":40.0,"longitude":116.25,"utc_offset_seconds":28800,
             "hourly":{"time":["2026-09-14T12:00"],
              "temperature_2m_ukmo_seamless":[23.0],
              "wind_speed_10m_ukmo_seamless":[12.0],
              "wind_direction_10m_ukmo_seamless":[90.0],
              "precipitation_probability_ukmo_seamless":[10]}}
            """.trimIndent()
        )
        val parsed = OpenMeteoApi.parseMultiModel(obj("{}"), tier2)
        assertEquals(1, parsed.models.size)
        assertEquals("ukmo_seamless", parsed.models[0].model)
        assertTrue(parsed.models[0].humidity.isEmpty()) // 精简组无湿度列
        assertEquals(12.0, parsed.models[0].windSpeedKmh[0]!!, 0.001)
        assertEquals(90.0, parsed.models[0].windDirectionDeg[0]!!, 0.001)
    }

    @Test
    fun supplementsBundleParsesUvDailyYesterdayAndMinutely() {
        val root = obj(
            """
            {"latitude":40.0,"longitude":116.25,"utc_offset_seconds":28800,
             "hourly":{"time":["2026-09-14T12:00"],"uv_index":[5.6]},
             "daily":{"time":["2026-09-13","2026-09-14"],
               "temperature_2m_max":[30.1,26.9],"temperature_2m_min":[15.0,16.2],
               "weather_code":[1,0],"wind_speed_10m_max":[10.0,9.0],
               "wind_gusts_10m_max":[20.0,19.0],"wind_direction_10m_dominant":[180.0,190.0],
               "precipitation_probability_max":[0,5],"precipitation_sum":[0.0,0.0],
               "sunrise":["2026-09-13T05:52","2026-09-14T05:53"],
               "sunset":["2026-09-13T18:24","2026-09-14T18:23"],
               "uv_index_max":[6.0,5.5],"relative_humidity_2m_mean":[40.0,38.0],
               "cloud_cover_mean":[20.0,15.0]},
             "minutely_15":{"time":["2026-09-14T12:00","2026-09-14T12:15"],"precipitation":[0.0,0.2]}}
            """.trimIndent()
        )
        val s = OpenMeteoApi.parseSupplements(root)
        assertEquals(2, s.dailyTime.size)
        assertEquals("2026-09-13", s.dailyTime[0]) // 昨日在列（past_days=1）
        assertEquals(5.6, s.uvIndex[0]!!, 0.001)
        assertEquals(5.5, s.dailyUvMax[1]!!, 0.001)
        assertEquals("05:53", s.dailySunrise[1]!!.substringAfter('T'))
        assertEquals(2, s.minutely15PrecipMm.size)
        assertEquals(0.2, s.minutely15PrecipMm[1]!!, 0.001)
        assertEquals(1, s.dailyWeatherCode[0]) // Int 列
    }

    @Test
    fun airQualityParsesPollutantsAsPhysicalValues() {
        // 只取浓度（物理量）；european_aqi/us_aqi 不解析（中国适用性原则）
        val root = obj(
            """
            {"latitude":40.0,"longitude":116.25,"utc_offset_seconds":28800,
             "current":{"time":"2026-09-14T17:00","pm10":19.9,"pm2_5":18.0,"carbon_monoxide":352.0,
                        "nitrogen_dioxide":40.8,"sulphur_dioxide":3.6,"ozone":54.0},
             "hourly":{"time":["2026-09-14T12:00"],"pm2_5":[18.0],"pm10":[19.9],
                       "carbon_monoxide":[352.0],"nitrogen_dioxide":[40.8],
                       "sulphur_dioxide":[3.6],"ozone":[54.0]}}
            """.trimIndent()
        )
        val aq = OpenMeteoApi.parseAirQuality(root)
        assertEquals(18.0, aq.currentPm25!!, 0.001)
        assertEquals(54.0, aq.currentO3!!, 0.001)
        assertEquals(1, aq.hourlyTimeMillis.size)
        // CO is normalized to mg/m³ for the GB AQI calculation and pollutant display.
        assertEquals(0.352, aq.hourlyCo[0]!!, 0.001)
    }

    @Test
    fun missingSectionsTolerateNull() {
        val s = OpenMeteoApi.parseSupplements(obj("{}"))
        assertTrue(s.dailyTime.isEmpty())
        assertNull(s.latitude)
        val aq = OpenMeteoApi.parseAirQuality(obj("{}"))
        assertNull(aq.currentPm25)
        assertTrue(aq.hourlyTimeMillis.isEmpty())
    }
}
