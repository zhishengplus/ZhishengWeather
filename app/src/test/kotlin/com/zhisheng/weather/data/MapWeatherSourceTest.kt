package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MapWeatherSourceTest {
    private val city = City("颍上县", "安徽省·阜阳市", 32.637, 116.264, "original")
    private fun root(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    @Test fun amapKeepsRealReportTimeDistrictAndDoesNotSynthesizeHoursOrWindSpeed() {
        val live = root("""{"status":"1","lives":[{"adcode":"341226","weather":"小雨","temperature":"23","humidity":"65","windpower":"≤3","reporttime":"2026-10-09 12:30:00"}]}""")
        val fc = root("""{"status":"1","forecasts":[{"adcode":"341226","reporttime":"2026-10-09 11:00:00","casts":[{"date":"2026-10-10","daytemp":"26","nighttemp":"16","dayweather":"晴","nightweather":"雷阵雨"}]}]}""")
        val data = parseAmapWeather(live, fc, city, "341226", 999L)
        assertNull(data.error)
        assertEquals(23.0, data.current!!.temperature!!, 0.0)
        assertNull(data.current.windSpeed)
        assertEquals(WeatherCondition.DRIZZLE, data.current.condition)
        assertTrue(data.hourly.isEmpty())
        assertTrue(data.rainMinutes.isEmpty())
        assertNull(data.aqi)
        assertEquals(Instant.parse("2026-10-09T04:30:00Z").toEpochMilli(), data.updateTime)
        assertEquals(Instant.parse("2026-10-09T03:00:00Z").toEpochMilli(), data.moduleEvidence.getValue("forecast").observationTime)
        assertEquals(WeatherCondition.THUNDERSTORM, data.daily.single().condition)
        assertTrue(data.moduleEvidence.getValue("current").locationScope.contains("341226"))
        assertNull(data.locationMatch)
    }

    @Test fun amapRejectsOtherDistrictAndMissingSentinelFields() {
        assertNotNull(parseAmapWeather(root("""{"status":"1","lives":[{"adcode":"110101","temperature":"20"}]}"""), null, city, "341226", 1).error)
        assertNotNull(parseAmapWeather(root("""{"status":"1","lives":[{"adcode":"341226","temperature":"999999"}]}"""), null, city, "341226", 1).error)
        assertNotNull(parseAmapWeather(root("""{"status":"0","infocode":"10001"}"""), null, city, "341226", 1).error)
    }

    @Test fun baiduRetainsAuthorizedFieldsOnlyWithBeijingTimestampAndUnits() {
        val response = root("""{"status":0,"result":{"location":{"id":"341226"},"now":{"temp":20,"text":"多云","rh":50,"wind_class":"3级","vis":10000,"aqi":36,"pm25":25,"co":0.6,"uptime":"20261009120000"},"alerts":[],"indexes":[{"name":"穿衣指数","brief":"舒适","detail":"薄外套"}],"forecasts":[{"date":"2026-10-10","high":25,"low":15}],"forecast_hours":[{"data_time":"2026-10-09 13:00:00","temp_fc":21,"prec_1h":0.5,"pop":999999}]}}""")
        val data = parseBaiduWeather(response, city, "341226", 500L)
        assertNull(data.error)
        assertEquals(10.0, data.current!!.visibility!!, 0.0)
        assertNull(data.current.windSpeed)
        assertEquals(Instant.parse("2026-10-09T04:00:00Z").toEpochMilli(), data.updateTime)
        assertEquals(Instant.parse("2026-10-09T05:00:00Z").toEpochMilli(), data.hourly.single().timeMillis)
        assertNull(data.hourly.single().precipProb)
        assertEquals("μg/m³", data.aqi!!.pollutantUnits["pm2p5"])
        assertEquals("mg/m³", data.aqi.pollutantUnits["co"])
        assertTrue(data.moduleEvidence.containsKey("alerts"))
        assertEquals("舒适 · 薄外套", data.extraIndices.single().category)
        assertTrue(data.rainMinutes.isEmpty())
    }

    @Test fun baiduBasicResponseDoesNotInventMissingAuthorizedData() {
        val basic = root("""{"status":0,"result":{"location":{"id":"341226"},"now":{"temp":19,"rh":999999,"uptime":"20261009120000"}}}""")
        val data = parseBaiduWeather(basic, city, "341226", 1)
        assertNull(data.current!!.humidity)
        assertNull(data.aqi)
        assertTrue(data.hourly.isEmpty())
        assertFalse(data.moduleEvidence.containsKey("alerts"))
        assertFalse(data.moduleEvidence.containsKey("indices"))
    }

    @Test fun baiduDeniedPermissionOrMismatchedDistrictFailsWithoutCreatingData() {
        assertNotNull(parseBaiduWeather(root("""{"status":401,"message":"denied"}"""), city, "341226", 1).error)
        assertNotNull(parseBaiduWeather(root("""{"status":0,"result":{"location":{"id":"110101"},"now":{"temp":20}}}"""), city, "341226", 1).error)
    }

    @Test fun parsedAmapAndBasicBaiduStayWithoutHourlyThroughWarehouseAlignment() {
        val now = Instant.parse("2026-10-09T04:00:00Z").toEpochMilli()
        val amap = parseAmapWeather(
            root("""{"status":"1","lives":[{"adcode":"341226","temperature":"20","weather":"晴","reporttime":"2026-10-09 12:00:00"}]}"""),
            root("""{"status":"1","forecasts":[{"adcode":"341226","casts":[{"date":"2026-10-10","daytemp":"25","nighttemp":"15"}]}]}"""),
            city, "341226", now)
        val baidu = parseBaiduWeather(root("""{"status":0,"result":{"location":{"id":"341226"},"now":{"temp":20,"uptime":"20261009120000"},"forecasts":[{"date":"2026-10-10","high":25,"low":15}]}}"""), city, "341226", now)
        listOf(amap, baidu).forEach { parsed ->
            assertNull(parsed.error)
            assertNotNull(parsed.current)
            assertTrue(parsed.daily.isNotEmpty())
            assertTrue("${parsed.dataSource} alignment must not invent hourly forecasts", WeatherConsistency.align(parsed, now).hourly.isEmpty())
        }
    }

    @Test fun dailyOnlyModuleDonorRemainsWithoutHourlyAfterOfflineRealignment() {
        val now = Instant.parse("2026-10-09T04:00:00Z").toEpochMilli()
        val donor = parseAmapWeather(
            root("""{"status":"1","lives":[{"adcode":"341226","temperature":"20"}]}"""),
            root("""{"status":"1","forecasts":[{"adcode":"341226","casts":[{"date":"2026-10-10","daytemp":"25","nighttemp":"15"}]}]}"""),
            city, "341226", now)
        val primary = stampModules(WeatherData(current = CurrentWeather(temperature = 22.0),
            hourly = listOf(HourlyWeather(now + 3_600_000, temperature = 23.0)), dataSource = "XIAOMI"), city)
        val result = replaceModule(primary, stampModules(WeatherConsistency.align(donor, now), city), WeatherModule.FORECAST)
        assertTrue(result.hourly.isEmpty())
        assertTrue(WeatherConsistency.align(result, now).hourly.isEmpty())
        assertEquals("AMAP", result.moduleEvidence.getValue("forecast").source)
        assertEquals(primary.current, result.current)
    }
}
