package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Public city responses captured 2026-10-22; proves mapping, not forecast skill. */
class WeatherPublicFixtureTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val now = Instant.parse("2026-10-22T04:30:00Z").toEpochMilli()
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/weather-quality-20261022/$name")).readText()

    @Test fun xiaomiActualResponsesKeepTemperatureTimestampAndFifteenDays() {
        listOf("jinchang" to "101160601", "guangzhou" to "101280101").forEach { (city, id) ->
            val raw = json.decodeFromString<XiaomiForecastResult>(fixture("xiaomi-$city.json"))
            val data = WeatherQuality.prepare(WeatherRepository.mapXiaomiToWeatherData(raw, "weathercn:$id"), now)
            assertEquals(if (city == "jinchang") 14.0 else 27.0, data.current!!.temperature!!, 0.0)
            assertEquals(15, data.daily.size)
            assertEquals(23, data.hourly.size)
            assertNotNull(data.updateTime)
            assertEquals(if (city == "jinchang") 850.0 else 1010.0, data.current!!.pressure!!, 0.0)
        }
    }

    @Test fun nmcActualResponsesDecodeSentinelsAndRetainSevenDays() {
        listOf("jinchang" to 13.2, "guangzhou" to 27.9).forEach { (city, expected) ->
            val raw = json.decodeFromString<NmcApi.NmcFull>(fixture("nmc-$city.json"))
            val current = NmcSource.parseNmcReal(requireNotNull(raw.real))!!
            assertEquals(expected, current.temperature!!, 0.0)
            assertNull(current.pressure) // Both public responses report 9999, not real pressure.
            val daily = NmcSource.parseNmcPredict(raw.predict, raw.real?.sunriseSunset, emptyList())
            assertEquals(7, daily.size)
            assertTrue(daily.all { it.windSpeed == null })
        }
    }

    @Test fun tencentActualResponsesExposeFortyEightDatedHoursWithoutInventedWindSpeed() {
        listOf("jinchang", "guangzhou").forEach { city ->
            val raw = json.parseToJsonElement(fixture("tencent-$city.json")) as JsonObject
            val hours = TencentWeatherApi.parseHourly(raw)
            assertEquals(48, hours.size)
            assertTrue(hours.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis == 3600_000L })
            assertTrue(hours.all { it.windSpeed == null && it.precipMm == null })
            val bundle = TencentWeatherApi.TencentBundle(TencentWeatherApi.parseObserve(raw), TencentWeatherApi.parseDaily(raw),
                emptyList(), emptyList(), hours)
            val data = WeatherQuality.prepare(tencentToWeatherData(bundle, now)!!, now)
            assertEquals(if (city == "jinchang") 14.0 else 28.0, data.current!!.temperature!!, 0.0)
            assertNotNull(data.updateTime)
            assertTrue(data.hourly.size >= 46)
            assertTrue(data.daily.size >= 6)
        }
    }

    @Test fun weatherCnActualPagesProvideSevenDaysAndThreeHourSteps() {
        listOf("jinchang" to "101160601", "guangzhou" to "101280101").forEach { (city, id) ->
            val data = parseWeatherCnForecast(fixture("weathercn-$city.html"), id, now)
            assertEquals(7, data.daily.size)
            assertTrue(data.hourly.size >= 40)
            assertTrue(data.hourly.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis == 3 * 3600_000L })
            assertNotNull(data.publishedAt)
        }
    }

    @Test fun xiaomiMissingPublicationTimesCannotBecomeTodaysForecast() {
        val raw = json.parseToJsonElement(fixture("xiaomi-jinchang.json")) as JsonObject
        val daily = JsonObject(raw.getValue("forecastDaily").jsonObject - "pubTime")
        val hourly = raw.getValue("forecastHourly").jsonObject
        val noHourDates = JsonObject(hourly - setOf("pubTime", "wind") +
            ("temperature" to JsonObject(hourly.getValue("temperature").jsonObject - "pubTime")))
        val data = WeatherRepository.mapXiaomiToWeatherData(json.decodeFromJsonElement<XiaomiForecastResult>(
            JsonObject(raw + ("forecastDaily" to daily) + ("forecastHourly" to noHourDates))), "weathercn:101160601")
        assertTrue(data.daily.isEmpty())
        assertTrue(data.hourly.isEmpty())
    }

    @Test fun malformedTencentHourDoesNotMakeTheWholeProviderFail() {
        val raw = json.parseToJsonElement("""{"data":{"forecast_1h":{
            "0":{"degree":"9999","update_time":"20261022140000"},
            "1":{"degree":"20","update_time":"invalid"},
            "2":{"degree":"0","update_time":"20261022150000","weather_code":"00"}
        }}}""") as JsonObject
        val hours = TencentWeatherApi.parseHourly(raw)
        assertEquals(1, hours.size)
        assertEquals(0.0, hours.single().temperature!!, 0.0)
    }

    @Test fun xiaomiCurrentNeverBorrowsForecastOrAirTimeAndOldAirExpiresIndependently() {
        val raw = json.decodeFromString<XiaomiForecastResult>(fixture("xiaomi-jinchang.json"))
        val missingTime = raw.copy(current = raw.current!!.copy(pubTime = null), updateTime = "2026-10-22T12:00:00+08:00")
        assertNull(WeatherRepository.xiaomiUpdateMillis(missingTime, now))
        val mapped = WeatherRepository.mapXiaomiToWeatherData(missingTime, "weathercn:101160601")
        assertTrue(WeatherQuality.prepare(mapped, now).sourceDiagnostics.getValue("current-time").contains("未提供"))
        val oldAir = raw.copy(aqi = raw.aqi!!.copy(pubTime = "2026-10-22T08:00:00+08:00"))
        val checked = WeatherQuality.prepare(WeatherRepository.mapXiaomiToWeatherData(oldAir, "weathercn:101160601"), now)
        assertNotNull(checked.current)
        assertNull(checked.aqi)
    }

    @Test fun xiaomiFutureObservationRemainsAvailableToQualityRejection() {
        val raw = json.decodeFromString<XiaomiForecastResult>(fixture("xiaomi-jinchang.json"))
        val future = raw.copy(current = raw.current!!.copy(pubTime = "2026-10-22T14:00:00+08:00"))
        val mapped = WeatherRepository.mapXiaomiToWeatherData(future, "weathercn:101160601")
        assertNull(WeatherQuality.prepare(mapped, now).current)
        val tencent = json.parseToJsonElement(fixture("tencent-jinchang.json")) as JsonObject
        val futureObserve = TencentWeatherApi.parseObserve(tencent)!!.copy(updateTime = "202610221400")
        val data = tencentToWeatherData(TencentWeatherApi.TencentBundle(futureObserve,
            TencentWeatherApi.parseDaily(tencent), emptyList(), emptyList()), now)!!
        assertNull(WeatherQuality.prepare(data, now).current)
    }
}
