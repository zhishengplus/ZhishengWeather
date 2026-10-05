package com.zhisheng.weather.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 腾讯天气（wis.qq.com）解析测试。fixture 为 2026-09-14 实测结构（含三亚真实预警样本）。 */
class TencentWeatherApiTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun observeParsesRealFields() {
        val root = obj(
            """{"status":200,"data":{"observe":{"degree":"25","humidity":"28","precipitation":"0",
               "pressure":"1014","update_time":"202609141730","weather":"晴","weather_code":"00",
               "wind_direction_name":"南风","wind_power":"4-5"}}}"""
        )
        val o = TencentWeatherApi.parseObserve(root)!!
        assertEquals(25.0, o.temperature!!, 0.001)
        assertEquals(28.0, o.humidity!!, 0.001)
        assertEquals(1014.0, o.pressure!!, 0.001)
        assertEquals(0.0, o.precipMm!!, 0.001)
        assertEquals("晴", o.weatherText)
        assertEquals("00", o.weatherCode)
        assertEquals("南风", o.windDirectionName)
        assertEquals("4-5", o.windPowerLevel)
        assertEquals("202609141730", o.updateTime)
    }

    @Test
    fun dailyIsYesterdayPlusForecast() {
        // 实测语义：forecast_24h 实为"昨日 + 未来 6 天"的逐日（含昼夜现象与高低温），不是逐小时
        val root = obj(
            """{"status":200,"data":{"forecast_24h":{
              "0":{"time":"2026-09-13","min_degree":"15","max_degree":"30","day_weather":"晴",
                   "day_weather_code":"00","night_weather":"晴","night_weather_code":"00","aqi":51},
              "1":{"time":"2026-09-14","min_degree":"15","max_degree":"26","day_weather":"晴",
                   "day_weather_code":"00","night_weather":"晴","night_weather_code":"00","aqi":49},
              "2":{"time":"2026-09-15","min_degree":"18","max_degree":"28","day_weather":"多云",
                   "day_weather_code":"01","night_weather":"晴","night_weather_code":"00"}}}}"""
        )
        val daily = TencentWeatherApi.parseDaily(root)
        assertEquals(3, daily.size)
        assertEquals("2026-09-13", daily[0].date) // 昨日在列 → yesterday 兜底
        assertEquals(30.0, daily[0].high!!, 0.001)
        assertEquals(15.0, daily[0].low!!, 0.001)
        assertEquals("01", daily[2].dayCode)
        assertEquals(49, daily[1].aqi)
    }

    @Test
    fun indexParsesNameAndLevel() {
        val root = obj(
            """{"status":200,"data":{"index":{"airconditioner":{"detail":"x","info":"较少开启","name":"空调开启"},
               "sports":{"detail":"y","info":"适宜","name":"运动"},"time":"2026091417"}}}"""
        )
        val idx = TencentWeatherApi.parseIndex(root)
        assertEquals(2, idx.size) // "time" 不是指数
        assertEquals("空调开启", idx.first { it.en == "airconditioner" }.name)
        assertEquals("适宜", idx.first { it.en == "sports" }.category)
    }

    @Test
    fun alarmParsesRealSanyaSample() {
        val root = obj(
            """{"status":200,"data":{"alarm":[{"city":"三亚市","county":"","detail":"三亚市气象台2026年09月14日13时40分继续发布暴雨橙色预警信号：……",
               "info":"202609141340M10682暴雨橙色","level_code":"03","level_name":"橙色","province":"海南省",
               "type_code":"02","type_name":"暴雨","update_time":"2026-09-14 13:40","url":"x"}]}}"""
        )
        val alarms = TencentWeatherApi.parseAlarm(root)
        assertEquals(1, alarms.size)
        assertEquals("暴雨", alarms[0].typeName)
        assertEquals("橙色", alarms[0].levelName)
        assertEquals("海南省", alarms[0].province)
        assertEquals("三亚市", alarms[0].city)
        assertEquals("2026-09-14 13:40", alarms[0].pubTime)
    }

    @Test
    fun emptySectionsTolerateNull() {
        assertNull(TencentWeatherApi.parseObserve(obj("""{"status":-1,"data":null}""")))
        assertTrue(TencentWeatherApi.parseDaily(obj("""{"data":{}}""")).isEmpty())
        assertTrue(TencentWeatherApi.parseAlarm(obj("""{"data":{"alarm":null}}""")).isEmpty())
        assertTrue(TencentWeatherApi.parseIndex(obj("""{"data":null}""")).isEmpty())
    }
}
