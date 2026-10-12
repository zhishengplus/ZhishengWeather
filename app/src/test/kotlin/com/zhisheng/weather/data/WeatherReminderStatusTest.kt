package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WeatherReminderStatusTest {
    private val at = Instant.parse("2026-10-10T01:35:00Z").toEpochMilli()
    private val settings = AutomationSettings(morningEnabled = true, morningMinute = 575, rainEnabled = true)
    private val weather = WeatherData(fetchedAt = at, utcOffsetSeconds = 28800,
        daily = listOf(DailyWeather(at, 20.0, 6.0, WeatherCondition.CLEAR)))
    private fun status(data: WeatherData? = weather, now: Long = at, receipts: Map<String, Long> = emptyMap()) =
        evaluateWeatherReminders("city", data, settings, receipts, now)

    @Test fun pendingMorningAndMissingRainAreSeparateReasons() {
        val result = status(now = at - 60_000)
        assertEquals(ReminderCheckState.WAITING, result.checks.first { it.kind == "morning" }.state)
        assertEquals(at, result.checks.first { it.kind == "morning" }.nextAt)
        assertEquals(ReminderCheckState.NO_FORECAST, result.checks.first { it.kind == "rain" }.state)
    }

    @Test fun dryForecastIsDifferentFromMissingAndExpiredForecast() {
        val rain = weather.copy(rainMeta = RainMeta("OPEN_METEO", 15, at),
            rainMinutes = listOf(MinutePrecip(at, 0f), MinutePrecip(at + 15 * 60_000, 0f)))
        assertEquals(ReminderCheckState.NO_RAIN, status(rain).checks.last().state)
        assertEquals(ReminderCheckState.STALE_DATA,
            status(rain.copy(rainMeta = rain.rainMeta!!.copy(updateTime = at - 31 * 60_000))).checks.last().state)
        assertEquals(ReminderCheckState.NO_FORECAST, status().checks.last().state)
    }

    @Test fun sentMorningAndRainCooldownRemainIndependent() {
        val result = status(receipts = mapOf("city:morning:2026-10-10" to at, "city:rain" to at))
        assertEquals(ReminderCheckState.SENT, result.checks.first().state)
        assertEquals(ReminderCheckState.RAIN_COOLDOWN, result.checks.last().state)
        assertEquals(at + 4 * 3_600_000L, result.checks.last().nextAt)
    }

    @Test fun missingLocationAndDataExplainWhyNoNotificationWasPlanned() {
        assertTrue(evaluateWeatherReminders(null, weather, settings, emptyMap(), at).checks
            .all { it.state == ReminderCheckState.NO_LOCATION && it.reminder == null })
        assertTrue(status(null).checks.all { it.state == ReminderCheckState.NO_DATA && it.reminder == null })
        assertEquals(ReminderCheckState.LATE, status(now = at + 90 * 60_000).checks.first().state)
    }

    @Test fun unsuccessfulPostIsFailedAndNeverClaimedAsSent() {
        val planned = status()
        val failed = finishReminderDelivery(planned, emptySet(), true)
        assertEquals(ReminderCheckState.FAILED, failed.checks.first().state)
        assertNull(failed.checks.first().reminder)
        assertEquals(ReminderCheckState.NO_FORECAST, failed.checks.last().state)
        val blocked = finishReminderDelivery(planned, emptySet(), false)
        assertEquals(ReminderCheckState.NOTIFICATIONS_BLOCKED, blocked.checks.first().state)
    }

    @Test fun successfulReceiptWinsIfPermissionChangesImmediatelyAfterPosting() {
        val planned = status()
        val key = planned.checks.first().reminder!!.key
        val completed = finishReminderDelivery(planned, setOf(key), false)
        assertEquals(ReminderCheckState.SENT, completed.checks.first().state)
        assertNull(completed.checks.first().reminder)
        assertEquals(ReminderCheckState.NO_FORECAST, completed.checks.last().state)
    }

    @Test fun clockRollbackDoesNotResendTheSameDailyBrief() {
        val key = "city:morning:2026-10-10"
        val sentAt = at + 30 * 60_000L
        val retained = validReminderReceipts(mapOf(key to sentAt, "city:rain" to sentAt,
            "old:morning:2026-10-01" to at - 8 * 24 * 3_600_000L), at)
        assertEquals(setOf(key), retained.keys)
        assertEquals(ReminderCheckState.SENT, status(receipts = retained).checks.first().state)
        assertNull(status(receipts = retained).checks.first().reminder)
    }

    @Test fun sentDailyBriefKeepsTomorrowAfterMovingTimeIntoFuture() {
        val next = settings.copy(morningMinute = 600, eveningEnabled = true, eveningMinute = 600, rainEnabled = false)
        val receipts = mapOf("city:morning:2026-10-10" to at, "city:evening:2026-10-10" to at)
        val checks = evaluateWeatherReminders("city", weather, next, receipts, at).checks
        val scheduled = nextDailyReminderChecks("city", 28800, next, receipts, at)
        val expected = Instant.parse("2026-10-11T02:00:00Z").toEpochMilli()
        for (kind in listOf("morning", "evening")) {
            val check = checks.first { it.kind == kind }
            assertEquals(ReminderCheckState.SENT, check.state)
            assertNull(check.reminder)
            assertEquals(expected, check.nextAt)
            assertEquals(check.nextAt, scheduled.first { it.kind == kind }.dueAt)
        }
    }

    @Test fun todayReceiptAlsoPreventsChoosingYesterdayWindowAfterMidnightEdit() {
        val now = Instant.parse("2026-10-10T16:05:00Z").toEpochMilli()
        val next = settings.copy(morningMinute = 1425, eveningEnabled = true, eveningMinute = 1425, rainEnabled = false)
        val receipts = mapOf("city:morning:2026-10-11" to now - 300_000, "city:evening:2026-10-11" to now - 300_000)
        val checks = evaluateWeatherReminders("city", weather.copy(fetchedAt = now), next, receipts, now).checks
        val scheduled = nextDailyReminderChecks("city", 28800, next, receipts, now)
        val expected = Instant.parse("2026-10-12T15:45:00Z").toEpochMilli()
        for (kind in listOf("morning", "evening")) {
            val check = checks.first { it.kind == kind }
            assertEquals(ReminderCheckState.SENT, check.state)
            assertNull(check.reminder)
            assertEquals(expected, check.nextAt)
            assertEquals(check.nextAt, scheduled.first { it.kind == kind }.dueAt)
        }
    }
}
