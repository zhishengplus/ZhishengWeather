package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherReminderPolicyTest {
    @Test fun dailyBriefDoesNotDependOnLiveObservationFreshness() {
        val at = Instant.parse("2026-10-10T01:35:00Z").toEpochMilli()
        val weather = WeatherData(fetchedAt = at - 3 * 3_600_000L, updateTime = at - 5 * 3_600_000L,
            utcOffsetSeconds = 28800, daily = listOf(DailyWeather(at, 20.0, 6.0, WeatherCondition.CLEAR)),
            moduleEvidence = mapOf("forecast" to WeatherModuleEvidence("CAIYUN", at - 6 * 3_600_000L)))
        val reminders = planWeatherReminders("a", weather,
            AutomationSettings(morningEnabled = true, morningMinute = 9 * 60 + 35), emptyMap(), at)
        assertEquals("A valid daily forecast must work even without a fresh live observation", 1, reminders.size)
    }

    @Test fun freshRainModuleDoesNotDependOnUnrelatedCurrentObservation() {
        val at = Instant.parse("2026-10-10T01:35:00Z").toEpochMilli()
        val weather = WeatherData(fetchedAt = at, updateTime = at - 5 * 3_600_000L,
            rainMeta = RainMeta("OPEN_METEO", 15, at), rainMinutes = listOf(MinutePrecip(at, .8f)))
        assertEquals(1, planWeatherReminders("a", weather, AutomationSettings(rainEnabled = true), emptyMap(), at).size)
    }

    @Test fun invalidCityOffsetNeverSchedulesAgainstDeviceTime() {
        val at = Instant.parse("2026-10-10T01:35:00Z").toEpochMilli()
        val weather = WeatherData(current = CurrentWeather(temperature = 8.0), fetchedAt = at,
            updateTime = at, utcOffsetSeconds = 19 * 3600,
            daily = listOf(DailyWeather(at, 20.0, 6.0, WeatherCondition.CLEAR)))
        val minute = Instant.ofEpochMilli(at).atZone(cityZone(null)).let { it.hour * 60 + it.minute }
        assertTrue(planWeatherReminders("a", weather,
            AutomationSettings(morningEnabled = true, morningMinute = minute), emptyMap(), at).isEmpty())
    }

    @Test fun currentRainBucketStillCoversTheUpcomingMinutes() {
        val at = Instant.parse("2026-10-10T04:07:00Z").toEpochMilli()
        val bucketStart = at - 7 * 60_000L
        val weather = WeatherData(current = CurrentWeather(temperature = 15.0), fetchedAt = at,
            updateTime = at, rainMeta = RainMeta("OPEN_METEO", 15, at),
            rainMinutes = listOf(MinutePrecip(bucketStart, .8f), MinutePrecip(bucketStart + 15 * 60_000L, 0f)))
        val result = planWeatherReminders("a", weather, AutomationSettings(rainEnabled = true), emptyMap(), at)
        assertEquals(1, result.size)
        assertTrue(result.single().text.startsWith("当前时段"))
        assertTrue(planWeatherReminders("a", weather.copy(rainMinutes = listOf(MinutePrecip(bucketStart - 15 * 60_000L, .8f))),
            AutomationSettings(rainEnabled = true), emptyMap(), at).isEmpty())
    }

    @Test fun freshCurrentWeatherDoesNotRenewAnOldDailyForecast() {
        val at = Instant.parse("2026-10-09T23:10:00Z").toEpochMilli()
        val weather = WeatherData(current = CurrentWeather(temperature = 12.0), fetchedAt = at,
            updateTime = at, utcOffsetSeconds = 28800,
            daily = listOf(DailyWeather(at, 21.0, 9.0, WeatherCondition.CLEAR)),
            moduleEvidence = mapOf("forecast" to WeatherModuleEvidence("AMAP", at - 48 * 3_600_000L, at)))
        assertTrue(planWeatherReminders("a", weather, AutomationSettings(morningEnabled = true), emptyMap(), at).isEmpty())
        assertEquals(1, planWeatherReminders("a", weather.copy(moduleEvidence = mapOf("forecast" to
            WeatherModuleEvidence("AMAP", at - 6 * 3_600_000L, at))), AutomationSettings(morningEnabled = true), emptyMap(), at).size)
    }
    private val now = Instant.parse("2026-10-09T23:10:00Z").toEpochMilli() // next day 07:10 UTC+8
    private val data = WeatherData(current = CurrentWeather(temperature = 12.0), fetchedAt = now,
        updateTime = now, utcOffsetSeconds = 28800,
        daily = listOf(DailyWeather(now, 21.0, 9.0, WeatherCondition.CLEAR)))
    @Test fun morningUsesCityDateAndDoesNotRepeat() {
        val settings = AutomationSettings(morningEnabled = true)
        val reminder = planWeatherReminders("a", data, settings, emptyMap(), now).single()
        assertTrue(reminder.key.contains("2026-10-10"))
        assertTrue(reminder.text.contains("9"))
        assertTrue(planWeatherReminders("a", data, settings, mapOf(reminder.key to now), now + 1000).isEmpty())
        assertEquals(1, planWeatherReminders("b", data, settings, mapOf(reminder.key to now), now).size)
    }
    @Test fun missingDailyStaleForecastAndLateWindowDoNotInventReports() {
        val settings = AutomationSettings(morningEnabled = true)
        assertTrue(planWeatherReminders("a", data.copy(daily = emptyList()), settings, emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", data.copy(moduleEvidence = mapOf("forecast" to
            WeatherModuleEvidence("CAIYUN", now - 25 * 3600_000L))), settings, emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", data.copy(fetchedAt = now - 25 * 3600_000L), settings, emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", data.copy(fetchedAt = now + 2 * 3600_000, updateTime = now + 2 * 3600_000), settings, emptyMap(), now + 2 * 3600_000).isEmpty())
    }
    @Test fun eveningRequiresRealTomorrowForecast() {
        val evening = now + 12 * 3600_000
        val settings = AutomationSettings(eveningEnabled = true)
        assertTrue(planWeatherReminders("a", data.copy(fetchedAt = evening, updateTime = evening), settings, emptyMap(), evening).isEmpty())
        val tomorrow = DailyWeather(evening + 24 * 3600_000, 20.0, 10.0, WeatherCondition.RAIN)
        assertEquals(1, planWeatherReminders("a", data.copy(fetchedAt = evening, updateTime = evening, daily = listOf(tomorrow)), settings, emptyMap(), evening).size)
    }
    @Test fun rainRequiresFreshTimedFutureSamplesAndCooldown() {
        val settings = AutomationSettings(rainEnabled = true)
        val rain = data.copy(rainMinutes = listOf(MinutePrecip(now + 600_000, 0.6f)),
            rainMeta = RainMeta("XIAOMI", 5, now))
        val reminder = planWeatherReminders("a", rain, settings, emptyMap(), now).single()
        assertTrue(reminder.text.contains("10"))
        assertTrue(planWeatherReminders("a", rain, settings, mapOf("a:rain" to now - 60_000), now).isEmpty())
        assertTrue(planWeatherReminders("a", rain.copy(rainMeta = rain.rainMeta!!.copy(updateTime = now - 3600_000)), settings, emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", rain.copy(rainMinutes = listOf(MinutePrecip(now - 600_000, 0.6f))), settings, emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", rain.copy(rainMinutes = listOf(MinutePrecip(now + 600_000, Float.NaN))), settings, emptyMap(), now).isEmpty())
    }
    @Test fun defaultsOptOutAndFailedWeatherNeverNotifies() {
        assertTrue(planWeatherReminders("a", data, AutomationSettings(), emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", data.copy(error = "offline"), AutomationSettings(morningEnabled = true), emptyMap(), now).isEmpty())
        assertTrue(planWeatherReminders("a", data.copy(utcOffsetSeconds = null), AutomationSettings(morningEnabled = true), emptyMap(), now).isEmpty())
    }
    @Test fun remindersStillHaveACheckInTheirWindowWithTwoHourRefreshPreference() {
        val settings = AutomationSettings(morningEnabled = true, backgroundMinutes = 120)
        // Simulate every periodic phase; an ordinary two-hour-only schedule can miss the whole window.
        for (phase in 0 until settings.checkMinutes) {
            val dayStart = Instant.parse("2026-10-09T16:00:00Z").toEpochMilli()
            val checks = generateSequence(phase) { it + settings.checkMinutes }.takeWhile { it < 1440 }
            assertTrue(checks.any { minute ->
                val at = dayStart + minute * 60_000L
                planWeatherReminders("a", data.copy(fetchedAt = at, updateTime = at), settings, emptyMap(), at).isNotEmpty()
            })
        }
        assertEquals(15, AutomationSettings(backgroundMinutes = 120, rainEnabled = true).checkMinutes)
    }
    @Test fun lateEveningWindowCrossesMidnightWithoutSendingDayAfterTomorrow() {
        val at = Instant.parse("2026-10-09T16:05:00Z").toEpochMilli() // Oct 10 00:05 local
        val settings = AutomationSettings(eveningEnabled = true, eveningMinute = 23 * 60 + 45)
        val weather = data.copy(fetchedAt = at, updateTime = at)
        val reminder = planWeatherReminders("a", weather, settings, emptyMap(), at).single()
        assertEquals("a:evening:2026-10-09", reminder.key)
        assertEquals("今日天气", reminder.title)
        assertTrue(planWeatherReminders("a", weather, settings, mapOf(reminder.key to at), at + 60_000).isEmpty())
        assertTrue(planWeatherReminders("a", weather.copy(fetchedAt = at + 100 * 60_000, updateTime = at + 100 * 60_000), settings, emptyMap(), at + 100 * 60_000).isEmpty())
    }
}
