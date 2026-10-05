package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class WeatherAuditRegressionTest {
    private val city = City("北京", "北京", 39.9, 116.4, "101010100")
    private fun epoch(value: String) = Instant.parse(value).toEpochMilli()
    private fun obj(value: String) = Json.parseToJsonElement(value) as JsonObject

    @Test fun explicitPercentSignsAreNotInterpretedAsRatios() {
        assertEquals(1, WeatherRepository.normalizeProviderProbability("1%"))
        assertEquals(1, WeatherRepository.normalizeProviderProbability("0.5%"))
        assertEquals(100, WeatherRepository.normalizeProviderProbability("100%"))
        assertEquals(40, WeatherRepository.normalizeProviderProbability("0.4"))
        assertEquals(40, WeatherRepository.normalizeProviderProbability("40"))
        assertEquals(40, WeatherRepository.normalizeProviderProbability("40%"))
    }

    @Test fun derivedHourlyUvUsesMinutesSinceLocalMidnight() {
        val midnight = epoch("2026-09-29T16:00:00Z")
        val data = WeatherData(
            hourly = listOf(0, 6, 9, 12, 15, 18, 21).map {
                HourlyWeather(midnight + it * 3_600_000L, temperature = 20.0)
            },
            daily = listOf(DailyWeather(midnight, sunrise = "06:00", sunset = "18:00", uvIndex = 8)),
            utcOffsetSeconds = 28800,
        )
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "backfillHourlyCompleteness", WeatherData::class.java, City::class.java,
            OmAirQuality::class.java, OmSupplements::class.java,
        ).apply { isAccessible = true }
        val result = method.invoke(WeatherRepository, data, city, null, null) as WeatherData
        assertEquals(listOf(0, 0, 6, 8, 6, 0, 0), result.hourly.map { it.uvIndex })
    }

    @Test fun supplementaryDailyForecastUsesWmoCodes() {
        val supplements = OpenMeteoApi.parseSupplements(obj("""
            {"utc_offset_seconds":28800,"daily":{
              "time":["2026-09-30","2026-10-01","2026-10-02","2026-10-03"],
              "weather_code":[3,61,71,95]}}
        """.trimIndent()))
        val result = requireNotNull(omSupplementsToWeatherData(supplements, epoch("2026-09-30T00:00:00Z")))
        assertEquals(listOf(WeatherCondition.OVERCAST, WeatherCondition.RAIN,
            WeatherCondition.SNOW, WeatherCondition.THUNDERSTORM), result.daily.map { it.condition })
    }

    @Test fun derivedUvRetainsLocalHourBucketsInHalfHourTimezones() {
        val midnight = epoch("2026-09-29T18:30:00Z")
        val data = WeatherData(
            hourly = listOf(HourlyWeather(midnight + 12 * 3_600_000L, temperature = 20.0)),
            daily = listOf(DailyWeather(midnight, sunrise = "06:00", sunset = "18:00", uvIndex = 8)),
            utcOffsetSeconds = 19800,
        )
        assertEquals(8, backfill(data, null).hourly.single().uvIndex)
    }

    @Test fun backfilledAqiUsesActualForecastTimestampsInHalfHourTimezones() {
        val air = OpenMeteoApi.parseAirQuality(obj("""
            {"utc_offset_seconds":19800,"hourly":{
            "time":["2026-09-30T11:00","2026-09-30T12:00"],"pm2_5":[35,75]}}
        """))
        val data = WeatherData(hourly = listOf(HourlyWeather(epoch("2026-09-30T06:30:00Z"))),
            utcOffsetSeconds = 19800)
        assertEquals(100, backfill(data, air).hourly.single().aqi)
    }

    private fun backfill(data: WeatherData, air: OmAirQuality?): WeatherData {
        val method = WeatherRepository::class.java.getDeclaredMethod(
            "backfillHourlyCompleteness", WeatherData::class.java, City::class.java,
            OmAirQuality::class.java, OmSupplements::class.java,
        ).apply { isAccessible = true }
        return method.invoke(WeatherRepository, data, city, air, null) as WeatherData
    }

    @Test fun hourlyAqiPrefersTheExactHourInsteadOfTheFirstNearbyRow() {
        val air = airWithHourlyPm25()
        assertEquals(100, FusionEngine.gbAqiAt(air, epoch("2026-09-30T03:00:00Z"))?.first)
        assertEquals(50, FusionEngine.gbAqiAt(air, epoch("2026-09-30T02:00:00Z"))?.first)
    }

    @Test fun hourlyAqiUsesNearestActualTimestampAndRejectsDistantRows() {
        val bucket = epoch("2026-09-30T03:00:00Z")
        val air = airWithHourlyPm25().copy(hourlyTimeMillis = listOf(bucket - 3_600_000L, bucket + 20 * 60_000L))
        assertEquals(100, FusionEngine.gbAqiAt(air, bucket)?.first)
        assertNull(FusionEngine.gbAqiAt(air.copy(hourlyTimeMillis = listOf(bucket - 2 * 3_600_000L)), bucket))
    }

    @Test fun carbonMonoxideIsConvertedToMilligramsBeforeAqiAndDisplay() {
        val air = OpenMeteoApi.parseAirQuality(obj("""
            {"utc_offset_seconds":28800,
             "current":{"pm2_5":35,"carbon_monoxide":352},
             "hourly":{"time":["2026-09-30T11:00"],"pm2_5":[35],"carbon_monoxide":[352]}}
        """.trimIndent()))
        assertEquals(0.352, requireNotNull(air.currentCo), 0.000001)
        assertEquals(0.352, requireNotNull(air.hourlyCo.single()), 0.000001)
        assertEquals(50, FusionEngine.gbAqiNow(air)?.first)
        assertEquals(50, FusionEngine.gbAqiAt(air, epoch("2026-09-30T03:00:00Z"))?.first)
        val result = FusionEngine.fuse(FusionEngine.Input(city, listOf(
            FusionEngine.FusionSource("XIAOMI", "小米", 1.0,
                WeatherData(current = CurrentWeather(temperature = 20.0))),
        ), epoch("2026-09-30T03:00:00Z"), omAir = air))
        assertEquals("0.352", result.aqi?.co)
        assertEquals("mg/m³", result.aqi?.pollutantUnits?.get("co"))
    }

    @Test fun windChillUsesKilometresPerHourInTheCanadianFormula() {
        assertEquals(-20.3027, requireNotNull(FusionEngine.localFeelsLike(-10.0, 50.0, 36.0)), 0.0001)
        assertEquals(-10.0, requireNotNull(FusionEngine.localFeelsLike(-10.0, 50.0, 0.0)), 0.0)
    }

    @Test fun fifteenMinutePrecipitationUsesIntervalStartsAndSkipsEndedIntervals() {
        val supplements = OpenMeteoApi.parseSupplements(obj("""
            {"utc_offset_seconds":28800,"minutely_15":{
              "time":["2026-09-30T10:00","2026-09-30T10:15","2026-09-30T10:30","2026-09-30T13:00"],
              "precipitation":[0.5,0.25,0.0,1.0]}}
        """.trimIndent()))
        val minutes = FusionEngine.omMinutelyToMinutes(supplements, epoch("2026-09-30T02:05:00Z"))
        assertEquals(listOf(epoch("2026-09-30T02:00:00Z"), epoch("2026-09-30T02:15:00Z")),
            minutes.map { it.timeMillis })
        assertEquals(1.0f, minutes[0].precip, 0.0f)
        assertEquals(0.0f, minutes[1].precip, 0.0f)
    }

    private fun airWithHourlyPm25() = OpenMeteoApi.parseAirQuality(obj("""
        {"utc_offset_seconds":28800,"hourly":{
          "time":["2026-09-30T10:00","2026-09-30T11:00"],"pm2_5":[35,75]}}
    """.trimIndent()))
}
