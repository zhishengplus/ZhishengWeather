package com.zhisheng.weather.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherReminderScheduleTest {
    private val city = "geo:39.9:116.4"
    private val daily = AutomationSettings(morningEnabled = true, morningMinute = 9 * 60 + 35,
        eveningEnabled = true, eveningMinute = 19 * 60)

    @Test fun appointmentsUseTheSelectedCityOffsetRatherThanTheDeviceDate() {
        assertEquals(listOf(
            DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"), "$city:morning:2026-10-10"),
            DailyReminderCheck("evening", at("2026-10-10T11:00:00Z"), "$city:evening:2026-10-10"),
        ), nextDailyReminderChecks(city, 28_800, daily, emptyMap(), at("2026-10-10T01:00:00Z")))
    }

    @Test fun negativeOffsetsScheduleTheNextLocalDayAfterItsMorningWindowHasPassed() {
        val settings = AutomationSettings(morningEnabled = true, morningMinute = 7 * 60)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T12:00:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, -18_000, settings, emptyMap(), at("2026-10-10T01:00:00Z")))
    }

    @Test fun enteringTheWindowChecksTheOriginalAppointmentImmediately() {
        val settings = daily.copy(eveningEnabled = false)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-10T01:44:00Z")))
    }

    @Test fun theLastSecondInsideTheNinetyMinuteWindowStillChecksToday() {
        val settings = daily.copy(eveningEnabled = false)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-10T03:04:59Z")))
    }

    @Test fun theNinetyMinuteBoundarySchedulesTomorrowRatherThanAnExpiredCheck() {
        val settings = daily.copy(eveningEnabled = false)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-11T01:35:00Z"),
            "$city:morning:2026-10-11")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-10T03:05:00Z")))
    }

    @Test fun anExistingReceiptSchedulesTheFollowingDayAndKeepsTheOtherCategory() {
        val now = at("2026-10-10T01:44:00Z")
        assertEquals(listOf(
            DailyReminderCheck("evening", at("2026-10-10T11:00:00Z"), "$city:evening:2026-10-10"),
            DailyReminderCheck("morning", at("2026-10-11T01:35:00Z"), "$city:morning:2026-10-11"),
        ), nextDailyReminderChecks(city, 28_800, daily,
            mapOf("$city:morning:2026-10-10" to now), now))
    }

    @Test fun receiptsForOtherLocationsDoNotSuppressThisLocationsAppointment() {
        val now = at("2026-10-10T01:44:00Z")
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, daily.copy(eveningEnabled = false),
                mapOf("other-city:morning:2026-10-10" to now), now))
    }

    @Test fun anEveningWindowCrossingMidnightRetainsItsPreviousLocalDate() {
        val settings = AutomationSettings(eveningEnabled = true, eveningMinute = 23 * 60 + 45)
        assertEquals(listOf(DailyReminderCheck("evening", at("2026-10-09T15:45:00Z"),
            "$city:evening:2026-10-09")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-09T16:05:00Z")))
    }

    @Test fun aCompletedCheckWithoutAReceiptDoesNotImmediatelyScheduleItselfAgain() {
        val completed = DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-11T01:35:00Z"),
            "$city:morning:2026-10-11")),
            nextDailyReminderChecks(city, 28_800, daily.copy(eveningEnabled = false),
                emptyMap(), at("2026-10-10T01:44:00Z"), completed))
    }

    @Test fun completingThePreviousEveningAfterMidnightSchedulesTonightsAppointment() {
        val settings = AutomationSettings(eveningEnabled = true, eveningMinute = 23 * 60 + 45)
        val completed = DailyReminderCheck("evening", at("2026-10-09T15:45:00Z"),
            "$city:evening:2026-10-09")
        assertEquals(listOf(DailyReminderCheck("evening", at("2026-10-10T15:45:00Z"),
            "$city:evening:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-09T16:05:00Z"), completed))
    }

    @Test fun aCompletedOtherCategoryDoesNotSuppressTheMorningCheck() {
        val completed = DailyReminderCheck("evening", at("2026-10-09T11:00:00Z"),
            "$city:evening:2026-10-09")
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, daily.copy(eveningEnabled = false),
                emptyMap(), at("2026-10-10T01:44:00Z"), completed))
    }

    @Test fun changingTodaysTimeWhileTheOldCheckFinishesKeepsTheNewAppointment() {
        val completed = DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
            "$city:morning:2026-10-10")
        val settings = daily.copy(eveningEnabled = false, morningMinute = 9 * 60 + 50)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:50:00Z"),
            "$city:morning:2026-10-10")),
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-10T01:44:00Z"), completed))
    }

    @Test fun unknownOrInvalidOffsetsNeverFallBackToTheDeviceTimezone() {
        for (offset in listOf<Int?>(null, -64_801, 64_801, 86_400)) {
            assertTrue("Offset $offset must not schedule a location reminder",
                nextDailyReminderChecks(city, offset, daily, emptyMap(), at("2026-10-10T01:00:00Z")).isEmpty())
        }
    }

    @Test fun disablingBothDailyRemindersLeavesNoAppointmentsEvenWhenRainIsEnabled() {
        assertTrue(nextDailyReminderChecks(city, 28_800, AutomationSettings(rainEnabled = true),
            emptyMap(), at("2026-10-10T01:00:00Z")).isEmpty())
        assertTrue(nextDailyReminderChecks(city, 28_800, AutomationSettings(),
            emptyMap(), at("2026-10-10T01:00:00Z")).isEmpty())
    }

    @Test fun appointmentsAreChronologicalEvenWhenTheCategoryTimesAreReversed() {
        val settings = AutomationSettings(morningEnabled = true, morningMinute = 19 * 60,
            eveningEnabled = true, eveningMinute = 7 * 60)
        assertEquals(listOf(
            DailyReminderCheck("evening", at("2026-10-09T23:00:00Z"), "$city:evening:2026-10-10"),
            DailyReminderCheck("morning", at("2026-10-10T11:00:00Z"), "$city:morning:2026-10-10"),
        ), nextDailyReminderChecks(city, 28_800, settings, emptyMap(), at("2026-10-09T22:00:00Z")))
    }

    @Test fun completedMorningAndEveningAtTheSameTimeBothAdvanceToTomorrow() {
        val settings = daily.copy(eveningMinute = daily.morningMinute)
        val now = at("2026-10-10T01:44:00Z")
        val morning = morningAppointment()
        val evening = DailyReminderCheck("evening", morning.dueAt, "$city:evening:2026-10-10")
        val afterMorning = finishedDailyReminderChecks(emptySet(), morning, now)
        val afterBoth = finishedDailyReminderChecks(afterMorning, evening, now)
        val expected = listOf(
            DailyReminderCheck("morning", at("2026-10-11T01:35:00Z"), "$city:morning:2026-10-11"),
            DailyReminderCheck("evening", at("2026-10-11T01:35:00Z"), "$city:evening:2026-10-11"),
        )
        for (completed in listOf(null, morning, evening)) assertEquals(expected,
            nextDailyReminderChecks(city, 28_800, settings, emptyMap(), now, completed, afterBoth))
    }

    @Test fun completedAppointmentsWithOverlappingWindowsCannotRestartEachOtherToday() {
        val settings = daily.copy(eveningMinute = 10 * 60 + 30)
        val now = at("2026-10-10T02:31:00Z")
        val morning = morningAppointment()
        val evening = DailyReminderCheck("evening", at("2026-10-10T02:30:00Z"), "$city:evening:2026-10-10")
        val finished = finishedDailyReminderChecks(finishedDailyReminderChecks(emptySet(), evening, now), morning, now)
        assertEquals(listOf(
            DailyReminderCheck("morning", at("2026-10-11T01:35:00Z"), "$city:morning:2026-10-11"),
            DailyReminderCheck("evening", at("2026-10-11T02:30:00Z"), "$city:evening:2026-10-11"),
        ), nextDailyReminderChecks(city, 28_800, settings, emptyMap(), now, morning, finished))
    }

    @Test fun aRetryDoesNotRecordCompletionAndItsAppointmentRemainsAvailableToday() {
        val appointment = morningAppointment()
        val now = appointment.dueAt + 60_000
        val failed = listOf(ReminderCheck("morning", ReminderCheckState.NO_DATA, "test"))
        assertTrue(shouldRetryReminderCheck(appointment, daily, failed, now, 1))
        val finished = finishedDailyReminderChecks(emptySet(), null, now)
        assertTrue(finished.isEmpty())
        assertEquals(listOf(appointment), nextDailyReminderChecks(city, 28_800,
            daily.copy(eveningEnabled = false), emptyMap(), now, finishedChecks = finished))
    }

    @Test fun changingTodaysTimeIsNotBlockedByCompletionOfThePreviousTime() {
        val now = at("2026-10-10T01:44:00Z")
        val finished = finishedDailyReminderChecks(emptySet(), morningAppointment(), now)
        assertEquals(listOf(DailyReminderCheck("morning", at("2026-10-10T01:50:00Z"),
            "$city:morning:2026-10-10")), nextDailyReminderChecks(city, 28_800,
            daily.copy(eveningEnabled = false, morningMinute = 9 * 60 + 50), emptyMap(), now, finishedChecks = finished))
    }

    @Test fun schedulerCompletionDoesNotActAsADeliveryReceiptOrBlockImmediateDataRecovery() {
        val appointment = morningAppointment()
        val now = appointment.dueAt + 60_000
        val finished = finishedDailyReminderChecks(emptySet(), appointment, now)
        assertFalse(finished.isEmpty())
        assertTrue("Immediate notification checks use delivery receipts, not scheduler completion",
            shouldRetryReminderCheck(null, daily, listOf(ReminderCheck("morning", ReminderCheckState.NO_DATA, "test")), now, 0))
    }

    @Test fun completedIdentitiesOlderThanSevenDaysAndInvalidRecordsArePruned() {
        val now = at("2026-10-10T01:44:00Z")
        val old = DailyReminderCheck("morning", now - 7 * 24 * 3_600_000L - 1, "$city:morning:2026-10-03")
        val boundary = old.copy(dueAt = now - 7 * 24 * 3_600_000L)
        val today = morningAppointment()
        assertEquals(setOf(dailyReminderCheckIdentity(boundary), dailyReminderCheckIdentity(today)),
            finishedDailyReminderChecks(setOf(dailyReminderCheckIdentity(old), dailyReminderCheckIdentity(boundary), "invalid"), today, now))
    }

    @Test fun anEarlyExecutionCannotMarkItsFutureAppointmentComplete() {
        val appointment = morningAppointment()
        assertTrue(finishedDailyReminderChecks(emptySet(), appointment, appointment.dueAt - 1).isEmpty())
    }

    @Test fun dataProblemsRetryOnlyTheFirstTwoOfThreeDailyExecutions() {
        val appointment = morningAppointment()
        val now = appointment.dueAt + 60_000
        for (state in listOf(ReminderCheckState.NO_DATA, ReminderCheckState.CONFIG_CHANGED,
            ReminderCheckState.STALE_DATA, ReminderCheckState.NO_FORECAST,
            ReminderCheckState.NO_TIMEZONE, ReminderCheckState.FAILED)) {
            val checks = listOf(ReminderCheck("morning", state, "test"))
            assertTrue("First execution may retry $state", shouldRetryReminderCheck(appointment, daily, checks, now, 0))
            assertTrue("Second execution may retry $state", shouldRetryReminderCheck(appointment, daily, checks, now, 1))
            assertFalse("Third execution must stop $state", shouldRetryReminderCheck(appointment, daily, checks, now, 2))
            assertFalse(shouldRetryReminderCheck(appointment, daily, checks, now, 3))
        }
    }

    @Test fun dailyRetriesStopAtTheNinetyMinuteBoundaryAndNeverStartBeforeDue() {
        val appointment = morningAppointment()
        val failed = listOf(ReminderCheck("morning", ReminderCheckState.FAILED, "test"))
        assertTrue(shouldRetryReminderCheck(appointment, daily, failed, appointment.dueAt + 90 * 60_000 - 1, 0))
        assertFalse(shouldRetryReminderCheck(appointment, daily, failed, appointment.dueAt + 90 * 60_000, 0))
        assertFalse(shouldRetryReminderCheck(appointment, daily, failed, appointment.dueAt - 1, 0))
    }

    @Test fun aClosedDailyCategoryDoesNotRetryEvenWhenAnotherReminderRemainsEnabled() {
        val appointment = morningAppointment()
        val failed = listOf(ReminderCheck("morning", ReminderCheckState.NO_DATA, "test"))
        assertFalse(shouldRetryReminderCheck(appointment, daily.copy(morningEnabled = false), failed, appointment.dueAt, 0))
        assertFalse(shouldRetryReminderCheck(appointment, AutomationSettings(), failed, appointment.dueAt, 0))
    }

    @Test fun aDailyReceiptPreventsRetriesEvenWhenTheWeatherCheckReturnsNoData() {
        val appointment = morningAppointment()
        val failed = listOf(ReminderCheck("morning", ReminderCheckState.NO_DATA, "test"))
        assertFalse(shouldRetryReminderCheck(appointment, daily, failed, appointment.dueAt, 0,
            mapOf(appointment.key to appointment.dueAt)))
        assertTrue(shouldRetryReminderCheck(appointment, daily, failed, appointment.dueAt, 0,
            mapOf("other-city:morning:2026-10-10" to appointment.dueAt)))
    }

    @Test fun sentOrNonDataStatesNeverRetryADailyAppointment() {
        val appointment = morningAppointment()
        for (state in listOf(ReminderCheckState.SENT, ReminderCheckState.WAITING, ReminderCheckState.LATE,
            ReminderCheckState.NO_LOCATION, ReminderCheckState.NO_RAIN, ReminderCheckState.RAIN_COOLDOWN,
            ReminderCheckState.NOTIFICATIONS_BLOCKED, ReminderCheckState.READY)) {
            assertFalse("$state must not restart a daily check", shouldRetryReminderCheck(appointment, daily,
                listOf(ReminderCheck("morning", state, "test")), appointment.dueAt, 0))
        }
    }

    @Test fun anotherCategoryFailureCannotRetryAnAlreadySentDailyAppointment() {
        val appointment = morningAppointment()
        assertFalse(shouldRetryReminderCheck(appointment, daily, listOf(
            ReminderCheck("morning", ReminderCheckState.SENT, "test"),
            ReminderCheck("evening", ReminderCheckState.NO_FORECAST, "test"),
        ), appointment.dueAt, 0))
        assertFalse(shouldRetryReminderCheck(appointment, daily,
            listOf(ReminderCheck("evening", ReminderCheckState.FAILED, "test")), appointment.dueAt, 0))
    }

    @Test fun timezonePreparationAndImmediateFailuresHaveTheSameThreeExecutionLimit() {
        for (state in listOf(ReminderCheckState.NO_TIMEZONE, ReminderCheckState.NO_DATA, ReminderCheckState.FAILED)) {
            val checks = listOf(ReminderCheck("morning", state, "test"))
            assertTrue(shouldRetryReminderCheck(null, daily, checks, at("2026-10-10T00:00:00Z"), 0))
            assertTrue(shouldRetryReminderCheck(null, daily, checks, at("2026-10-10T00:01:00Z"), 1))
            assertFalse(shouldRetryReminderCheck(null, daily, checks, at("2026-10-10T00:03:00Z"), 2))
        }
    }

    @Test fun immediateRetriesUseLatestSwitchesAndKeepOtherUnsentFailures() {
        val now = at("2026-10-10T01:44:00Z")
        val morningFailure = ReminderCheck("morning", ReminderCheckState.NO_DATA, "test")
        assertFalse(shouldRetryReminderCheck(null, daily.copy(morningEnabled = false), listOf(morningFailure), now, 0))
        assertFalse(shouldRetryReminderCheck(null, AutomationSettings(), listOf(morningFailure), now, 0))
        assertTrue(shouldRetryReminderCheck(null, daily.copy(rainEnabled = true), listOf(
            morningFailure, ReminderCheck("rain", ReminderCheckState.SENT, "test")), now, 0))
    }

    @Test fun aSentMorningDoesNotBlockRainDataRetriesInAnImmediateCheck() {
        assertTrue(shouldRetryReminderCheck(null, daily.copy(rainEnabled = true), listOf(
            ReminderCheck("morning", ReminderCheckState.SENT, "test"),
            ReminderCheck("rain", ReminderCheckState.NO_FORECAST, "test"),
        ), at("2026-10-10T01:44:00Z"), 0))
    }

    @Test fun aSentMorningAppointmentDoesNotRetryForAnotherCategoryFailure() {
        val appointment = morningAppointment()
        assertFalse(shouldRetryReminderCheck(appointment, daily.copy(rainEnabled = true), listOf(
            ReminderCheck("morning", ReminderCheckState.SENT, "test"),
            ReminderCheck("rain", ReminderCheckState.NO_FORECAST, "test"),
        ), appointment.dueAt + 60_000, 0))
    }

    @Test fun unavailableRainDataCanRetryAnImmediateCheckButNoRainAndCooldownCannot() {
        val settings = AutomationSettings(rainEnabled = true)
        val now = at("2026-10-10T01:44:00Z")
        assertTrue(shouldRetryReminderCheck(null, settings,
            listOf(ReminderCheck("rain", ReminderCheckState.NO_FORECAST, "test")), now, 0))
        for (state in listOf(ReminderCheckState.NO_RAIN, ReminderCheckState.RAIN_COOLDOWN,
            ReminderCheckState.NOTIFICATIONS_BLOCKED)) {
            assertFalse(shouldRetryReminderCheck(null, settings, listOf(ReminderCheck("rain", state, "test")), now, 0))
        }
    }

    private fun morningAppointment() = DailyReminderCheck("morning", at("2026-10-10T01:35:00Z"),
        "$city:morning:2026-10-10")

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
}
