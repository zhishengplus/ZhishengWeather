package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class WeatherPriorityTest {
    @Test fun missingWindDirectionStillRequestsSupplementWhenAllModulesArePresent() {
        val current = CurrentWeather(temperature = 20.0, feelsLike = 19.0, condition = WeatherCondition.CLEAR,
            weatherText = "晴", humidity = 50.0, windSpeed = 10.0, windDirectionDeg = 90.0,
            pressure = 1000.0, uvIndex = 2, visibility = 10.0, dewPoint = 9.0,
            cloudCover = 0.0, windGust = 12.0, precipMm = 0.0)
        val complete = stamped(WeatherData(current = current, dataSource = "XIAOMI",
            hourly = listOf(completeHour(1)), daily = listOf(completeDay(1)),
            rainNowcast = "无雨", blockSources = mapOf("alerts" to "XIAOMI"),
            carWashOk = true, aqi = AqiInfo(value = 42)))
        assertFalse(weatherNeedsSupplement(complete))
        assertTrue(weatherNeedsSupplement(complete.copy(hourly = listOf(complete.hourly.single().copy(windSpeed = null)))))
        assertTrue(weatherNeedsSupplement(complete.copy(daily = listOf(complete.daily.single().copy(humidity = null)))))
        val missing = complete.copy(current = current.copy(windDirectionDeg = null))
        assertTrue(weatherNeedsSupplement(missing))
        val donor = stamped(WeatherData(current = CurrentWeather(windDirectionDeg = 270.0), dataSource = "OPEN-METEO"))
        assertEquals(current.copy(windDirectionDeg = 270.0), fillMissingWeather(missing, donor).current)
    }
    @Test fun aMissingPrimaryObservationKeepsTheActualDonorLabel() {
        val donor = stamped(WeatherData(current = CurrentWeather(temperature = 18.0), dataSource = "XIAOMI"))
        val result = fillMissingWeather(WeatherData(error = "主源失败", dataSource = "MOJI"), donor)
        assertNull(result.error)
        assertEquals("XIAOMI", result.dataSource)
        assertEquals("XIAOMI", result.moduleEvidence.getValue("current").source)
    }
    private val city = City("清华大学", "北京", 40.002, 116.326, "geo:test")
    private fun stamped(data: WeatherData) = stampModules(data, city)
    @Test fun preservesPrimaryValuesAndZeroRainWhenFillingMissingFields() {
        val base = stamped(WeatherData(current = CurrentWeather(temperature = 20.0, precipMm = 0.0), dataSource = "XIAOMI"))
        val donor = stamped(WeatherData(current = CurrentWeather(temperature = 99.0, humidity = 60.0, precipMm = 9.0), dataSource = "OPEN-METEO"))
        val result = fillMissingWeather(base, donor)
        assertEquals(20.0, result.current!!.temperature!!, 0.0)
        assertEquals(0.0, result.current!!.precipMm!!, 0.0)
        assertEquals(60.0, result.current!!.humidity!!, 0.0)
        assertTrue(result.moduleEvidence.getValue("current").source.contains("OPEN-METEO"))
        assertEquals(result, fillMissingWeather(result, donor.copy(current = donor.current!!.copy(humidity = 70.0))))
    }
    @Test fun forecastComplementsMissingHalfWithoutErasingExistingDailyForecast() {
        val base = stamped(WeatherData(daily = listOf(DailyWeather(1, high = 20.0)), dataSource = "AMAP"))
        val donor = stamped(WeatherData(hourly = listOf(HourlyWeather(2, temperature = 18.0)), daily = listOf(DailyWeather(1, high = 99.0)), dataSource = "XIAOMI"))
        val result = fillMissingWeather(base, donor)
        assertEquals(base.daily, result.daily)
        assertEquals(donor.hourly, result.hourly)
        assertEquals("XIAOMI", result.blockSources["hourly"])
        assertTrue(result.moduleEvidence.getValue("forecast").source.contains("AMAP"))
    }
    @Test fun failedProviderCannotSupplyWeatherOrClearConfirmedZeroAlerts() {
        val base = stamped(WeatherData(dataSource = "BAIDU", blockSources = mapOf("alerts" to "BAIDU")))
        val donor = stamped(WeatherData(dataSource = "XIAOMI", alerts = listOf(AlertInfo("高温预警"))))
        assertTrue(fillMissingWeather(base, donor).alerts.isEmpty())
        assertEquals(base, fillMissingWeather(base, donor.copy(error = "失败")))
    }

    @Test fun partialHourlyForecastKeepsPrimaryValuesAndUsesFirstAvailableDonorPerField() {
        val at = time("2026-10-22T03:00:00Z")
        val base = stamped(WeatherData(dataSource = "AMAP", hourly = listOf(HourlyWeather(at,
            temperature = 20.0, precipMm = 0.0, precipProb = 0, aqi = 0))))
        val first = stamped(WeatherData(dataSource = "XIAOMI", hourly = listOf(HourlyWeather(at,
            temperature = 99.0, windSpeed = 12.0, precipMm = 3.0, precipProb = 90, aqi = 99, aqiEstimated = true))))
        val second = stamped(WeatherData(dataSource = "OPEN-METEO", hourly = listOf(HourlyWeather(at,
            windSpeed = 88.0, humidity = 60.0))))
        val result = fillMissingWeather(fillMissingWeather(base, first), second)
        val hour = result.hourly.single()
        assertEquals(20.0, hour.temperature!!, 0.0)
        assertEquals(12.0, hour.windSpeed!!, 0.0)
        assertEquals(60.0, hour.humidity!!, 0.0)
        assertEquals(0.0, hour.precipMm!!, 0.0)
        assertEquals(0, hour.precipProb)
        assertEquals(0, hour.aqi)
        assertFalse(hour.aqiEstimated)
        assertEquals(at, hour.timeMillis)
        assertEquals("XIAOMI + OPEN-METEO", result.blockSources["hourly-supplement"])
        assertEquals("AMAP + XIAOMI + OPEN-METEO", result.moduleEvidence.getValue("forecast").source)
        assertEquals(result, fillMissingWeather(result, second))
    }

    @Test fun hourlySupplementMatchesCityHourIncludingHalfHourZonesAndNeverAdjacentHours() {
        val at = time("2026-10-22T03:00:00Z")
        val base = WeatherData(utcOffsetSeconds = 19_800, hourly = listOf(HourlyWeather(at, temperature = 20.0)))
        // 08:30 and 08:15 are the same local forecast hour; 09:10 is not.
        val matching = WeatherData(hourly = listOf(HourlyWeather(at - 15 * 60_000, windSpeed = 12.0)))
        assertEquals(12.0, fillMissingWeather(base, matching).hourly.single().windSpeed!!, 0.0)
        val nextHour = matching.copy(hourly = listOf(HourlyWeather(at + 40 * 60_000, windSpeed = 99.0)))
        assertEquals(base, fillMissingWeather(base, nextHour))
    }

    @Test fun ambiguousHourlySamplesAreSkippedUnlessOneTimestampMatchesExactly() {
        val at = time("2026-10-22T03:00:00Z")
        val base = WeatherData(hourly = listOf(HourlyWeather(at, temperature = 20.0)), utcOffsetSeconds = 0)
        val donor = WeatherData(hourly = listOf(HourlyWeather(at + 60_000, windSpeed = 10.0),
            HourlyWeather(at + 120_000, windSpeed = 99.0)))
        assertEquals(base, fillMissingWeather(base, donor))
        val exact = donor.copy(hourly = donor.hourly + HourlyWeather(at, windSpeed = 3.0))
        assertEquals(3.0, fillMissingWeather(base, exact).hourly.single().windSpeed!!, 0.0)
    }

    @Test fun dailySupplementMatchesEachProvidersLocalDateAndKeepsThePrimaryHorizon() {
        val at = time("2026-10-21T16:00:00Z")
        val base = stamped(WeatherData(dataSource = "AMAP", utcOffsetSeconds = 28_800,
            daily = listOf(DailyWeather(at, high = 20.0, low = 10.0, condition = WeatherCondition.RAIN,
                weatherText = "小雨", precipMm = 0.0))))
        val donor = stamped(WeatherData(dataSource = "OPEN-METEO", utcOffsetSeconds = 0,
            daily = listOf(DailyWeather(time("2026-10-22T00:00:00Z"), high = 99.0, low = 88.0,
                condition = WeatherCondition.CLEAR, weatherText = "晴", humidity = 55.0,
                precipMm = 5.0, sunrise = "06:20"), DailyWeather(time("2026-10-23T00:00:00Z"), high = 30.0))))
        val result = fillMissingWeather(base, donor)
        val day = result.daily.single()
        assertEquals(at, day.dateMillis)
        assertEquals(20.0, day.high!!, 0.0)
        assertEquals(10.0, day.low!!, 0.0)
        assertEquals(55.0, day.humidity!!, 0.0)
        assertEquals(0.0, day.precipMm!!, 0.0)
        assertEquals(WeatherCondition.RAIN, day.condition)
        assertEquals("小雨", day.weatherText)
        assertEquals("06:20", day.sunrise)
        assertEquals("OPEN-METEO", result.blockSources["daily-supplement"])
        assertEquals("AMAP + OPEN-METEO", result.moduleEvidence.getValue("forecast").source)
        assertEquals(base, fillMissingWeather(base, donor.copy(daily = donor.daily.drop(1))))
    }

    @Test fun complementaryDailyTemperaturesCannotInvertThePrimaryHighAndLow() {
        val donor = WeatherData(daily = listOf(DailyWeather(1, high = 15.0, low = 25.0)))
        val high = WeatherData(daily = listOf(DailyWeather(1, high = 20.0)))
        val low = WeatherData(daily = listOf(DailyWeather(1, low = 20.0)))
        assertNull(fillMissingWeather(high, donor).daily.single().low)
        assertNull(fillMissingWeather(low, donor).daily.single().high)
    }

    @Test fun borrowedHourlyAqiRetainsItsEstimationFlagAndUnknownConditionUsesMatchingProfile() {
        val profile = WeatherCondition.chinaProfile("07")
        val base = WeatherData(hourly = listOf(HourlyWeather(1, condition = WeatherCondition.UNKNOWN)))
        val donor = WeatherData(hourly = listOf(HourlyWeather(1, aqi = 42, aqiEstimated = true,
            condition = profile.condition, profile = profile)))
        val hour = fillMissingWeather(base, donor).hourly.single()
        assertEquals(42, hour.aqi)
        assertTrue(hour.aqiEstimated)
        assertEquals(profile.condition, hour.condition)
        assertEquals(profile, hour.profile)
        assertEquals(base, fillMissingWeather(base, donor.copy(error = "请求失败")))
    }

    private fun time(iso: String) = java.time.Instant.parse(iso).toEpochMilli()

    private fun completeHour(at: Long) = HourlyWeather(at, temperature = 20.0, feelsLike = 19.0,
        condition = WeatherCondition.CLEAR, windSpeed = 10.0, windDirectionDeg = 90.0,
        windGust = 12.0, precipProb = 0, precipMm = 0.0, aqi = 42, humidity = 50.0,
        pressure = 1000.0, visibility = 10.0, dewPoint = 9.0, cloudCover = 0.0, uvIndex = 2)

    private fun completeDay(at: Long) = DailyWeather(at, high = 20.0, low = 10.0, average = 15.0,
        condition = WeatherCondition.CLEAR, weatherText = "晴", windSpeed = 10.0, windDirectionDeg = 90.0,
        windGust = 12.0, precipProbability = 0, precipMm = 0.0, aqi = 42, humidity = 50.0,
        cloudCover = 0.0, uvIndex = 2, sunrise = "06:20", sunset = "18:00",
        moonrise = "17:00", moonset = "05:00", moonPhase = "满月")
}
