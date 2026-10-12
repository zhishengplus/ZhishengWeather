package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherReminderDeliveryTest {
    private val at = Instant.parse("2026-10-09T23:10:00Z").toEpochMilli()
    private val city = "geo:39.9:116.4"
    private val weather = WeatherData(current = CurrentWeather(temperature = 15.0), fetchedAt = at,
        updateTime = at, utcOffsetSeconds = 28800,
        daily = listOf(DailyWeather(at, 21.0, 9.0, WeatherCondition.CLEAR)),
        rainMeta = RainMeta("OPEN-METEO", 15, at),
        rainMinutes = listOf(MinutePrecip(at, .8f)))

    @Test fun closingAllRemindersWhileCacheReadIsPausedPreventsSendAndReceipt() = runBlocking {
        val gate = ReminderDeliveryGate()
        var settings = AutomationSettings(morningEnabled = true, rainEnabled = true)
        val reading = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val receipts = mutableMapOf<String, Long>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.deliver({ settings }, { reading.complete(Unit); resume.await(); weather },
                { data, current -> planWeatherReminders(city, data, current, receipts, at) },
                { _, reminder -> sent += reminder.key }, { receipts[it.key] = at })
        }
        reading.await()
        gate.updateSettings { settings = AutomationSettings() }
        resume.complete(Unit)
        job.join()
        assertTrue("A completed close must prevent every later send", sent.isEmpty())
        assertTrue("Skipped reminders must not acquire a delivery receipt", receipts.isEmpty())
    }

    @Test fun closingRainDuringCacheReadKeepsOnlyTheStillEnabledMorningReminder() = runBlocking {
        val gate = ReminderDeliveryGate()
        var settings = AutomationSettings(morningEnabled = true, rainEnabled = true)
        val reading = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val receipts = mutableMapOf<String, Long>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.deliver({ settings }, { reading.complete(Unit); resume.await(); weather },
                { data, current -> planWeatherReminders(city, data, current, receipts, at) },
                { _, reminder -> sent += reminder.key }, { receipts[it.key] = at })
        }
        reading.await()
        gate.updateSettings { settings = settings.copy(rainEnabled = false) }
        resume.complete(Unit)
        job.join()
        assertEquals(listOf("geo:39.9:116.4:morning:2026-10-10"), sent)
        assertEquals(setOf("geo:39.9:116.4:morning:2026-10-10"), receipts.keys)
    }

    @Test fun settingsUpdateAndCancellationWaitForTheSynchronousSendAndReceipt() {
        val gate = ReminderDeliveryGate()
        val settings = AtomicReference(AutomationSettings(rainEnabled = true))
        val sendEntered = CountDownLatch(1)
        val releaseSend = CountDownLatch(1)
        val updateStarted = CountDownLatch(1)
        val updateDone = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val sender = executor.submit {
                runBlocking {
                    gate.deliver(settings::get, { weather },
                        { data, current -> planWeatherReminders(city, data, current, emptyMap(), at) },
                        { _, _ -> sendEntered.countDown(); check(releaseSend.await(5, TimeUnit.SECONDS)); events += "sent" },
                        { events += "receipt" })
                }
            }
            assertTrue("The real synchronous send must have started", sendEntered.await(5, TimeUnit.SECONDS))
            val updater = executor.submit {
                updateStarted.countDown()
                gate.updateSettings { settings.set(AutomationSettings()); events += "cancelled" }
                updateDone.countDown()
            }
            assertTrue(updateStarted.await(5, TimeUnit.SECONDS))
            assertFalse("Settings and cancellation must not run inside an in-flight send", updateDone.await(150, TimeUnit.MILLISECONDS))
            releaseSend.countDown()
            sender.get(5, TimeUnit.SECONDS)
            updater.get(5, TimeUnit.SECONDS)
            assertEquals(listOf("sent", "receipt", "cancelled"), events.toList())
        } finally {
            releaseSend.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun permissionFailureDoesNotRecordReceiptOrContinueTheBatch() = runBlocking {
        val gate = ReminderDeliveryGate()
        val receipts = mutableMapOf<String, Long>()
        var attempted = 0
        gate.deliver({ AutomationSettings(morningEnabled = true, rainEnabled = true) }, { weather },
            { data, settings -> planWeatherReminders(city, data, settings, receipts, at) },
            { _, _ -> attempted++; throw SecurityException("notification denied") }, { receipts[it.key] = at })
        assertEquals(1, attempted)
        assertTrue(receipts.isEmpty())
    }

    @Test fun singleCategoryCancellationRecognizesExistingTagsWithColonContainingCityKeys() {
        val morningOnly = AutomationSettings(morningEnabled = true)
        assertTrue(shouldCancelWeatherReminder("weather-reminder:geo:39.9:116.4:rain", morningOnly))
        assertTrue(shouldCancelWeatherReminder("weather-reminder:geo:39.9:116.4:evening:2026-10-10", morningOnly))
        assertFalse(shouldCancelWeatherReminder("weather-reminder:geo:39.9:116.4:morning:2026-10-10", morningOnly))
        assertFalse(shouldCancelWeatherReminder("ongoing-weather", AutomationSettings()))
        assertTrue(shouldCancelWeatherReminder("weather-reminder:legacy", AutomationSettings()))
        assertFalse(shouldCancelWeatherReminder(null, AutomationSettings()))
    }
}
