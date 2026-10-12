package com.zhisheng.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.zhisheng.weather.model.*
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class WeatherReminderContextTest {
    private val at = Instant.parse("2026-10-09T23:10:00Z").toEpochMilli()
    private val weather = WeatherData(current = CurrentWeather(temperature = 15.0), fetchedAt = at,
        updateTime = at, rainMeta = RainMeta("OPEN-METEO", 15, at), rainMinutes = listOf(MinutePrecip(at, .8f)))

    @Test fun persistedCitySelectionDuringPreparationPreventsOldCitySendAndReceipt() = runBlocking {
        withStore(CityRepository) {
            assertPreparationInvalidated(reminderDeliveryGate) { CityRepository.selectCity("new-city") }
        }
    }

    @Test fun persistedSourceModuleDeveloperAndLocationChangesInvalidatePreparation() = runBlocking {
        withStore(SettingsRepository) {
            for (change in listOf<suspend () -> Unit>(
                { SettingsRepository.setSourcePref(SourcePref.CAIYUN) },
                { SettingsRepository.setModuleSource(WeatherModule.MINUTELY, SourcePref.OPEN_METEO) },
                { SettingsRepository.setDeveloperMode(true) },
                { SettingsRepository.setLocationEnabled(true) },
                { SettingsRepository.setPreciseLocationEnabled(true) },
            )) assertPreparationInvalidated(reminderDeliveryGate, change)
        }
    }

    @Test fun credentialSaveAndClearInvalidatePreparationThroughTheRealStoreEntryPoints() = runBlocking {
        withStore(SecretStore) {
            // Test-only data lives in a test DataStore; no device or workspace secrets are read.
            val runtime = SecretStore.javaClass.getDeclaredField("caiyunRuntime").apply { isAccessible = true }
            val previous = runtime.get(SecretStore)
            try {
                assertPreparationInvalidated(reminderDeliveryGate) { SecretStore.saveCaiyun("test-only-token") }
                assertPreparationInvalidated(reminderDeliveryGate) { SecretStore.clearCaiyun() }
            } finally { reminderDeliveryGate.changeContextNow { runtime.set(SecretStore, previous) } }
        }
    }

    @Test fun synchronousRuntimeCredentialChangeInvalidatesPreparedBatch() = runBlocking {
        val gate = ReminderDeliveryGate()
        var credentialRevision = 1
        assertPreparationInvalidated(gate) { gate.changeContextNow { credentialRevision = 2 } }
        assertEquals(2, credentialRevision)
    }

    @Test fun pendingContextWriteBlocksDeliveryAfterPreparationCompletes() = runBlocking {
        val gate = ReminderDeliveryGate()
        val prepared = CompletableDeferred<Unit>()
        val finishPreparation = CompletableDeferred<Unit>()
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val receipts = mutableListOf<String>()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            deliver(gate, { prepared.complete(Unit); finishPreparation.await(); weather }, sent, receipts)
        }
        prepared.await()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { writeStarted.complete(Unit); finishWrite.await() }
        }
        writeStarted.await()
        finishPreparation.complete(Unit)
        sender.join()
        finishWrite.complete(Unit)
        writer.join()
        assertTrue("An in-progress DataStore edit makes prepared context unusable", sent.isEmpty())
        assertTrue(receipts.isEmpty())
    }

    @Test fun preparationDoesNotStartWhileAnyContextWriteIsPending() = runBlocking {
        val gate = ReminderDeliveryGate()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { firstStarted.complete(Unit); finishFirst.await() }
        }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { secondStarted.complete(Unit); finishSecond.await() }
        }
        firstStarted.await(); secondStarted.await()
        finishFirst.complete(Unit); first.join()
        var prepared = 0
        val sent = mutableListOf<String>()
        val receipts = mutableListOf<String>()
        deliver(gate, { prepared++; weather }, sent, receipts)
        finishSecond.complete(Unit); second.join()
        assertEquals("Completing one overlapping edit must not clear the other edit's pending state", 0, prepared)
        assertTrue(sent.isEmpty())
        assertTrue(receipts.isEmpty())
    }

    @Test fun failedAndCancelledContextWritesReleasePendingStateForLaterFreshPreparation() = runBlocking {
        val gate = ReminderDeliveryGate()
        try { gate.changeContext { error("test write failed") } } catch (_: IllegalStateException) { }
        val started = CompletableDeferred<Unit>()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { started.complete(Unit); awaitCancellation() }
        }
        started.await(); writer.cancelAndJoin()
        val sent = mutableListOf<String>()
        val receipts = mutableListOf<String>()
        deliver(gate, { weather }, sent, receipts)
        assertEquals(listOf("old-city:rain"), sent)
        assertEquals(listOf("old-city:rain"), receipts)
    }

    @Test fun cancelledOwnerCannotSendAfterNonCooperativePreparationReturns() = runBlocking {
        val gate = ReminderDeliveryGate()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val sent = mutableListOf<String>()
        val receipts = mutableListOf<String>()
        val sender = launch(Dispatchers.Default) {
            deliver(gate, { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)); weather }, sent, receipts)
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            sender.cancel()
            resume.countDown()
            sender.join()
            assertTrue(sent.isEmpty())
            assertTrue(receipts.isEmpty())
        } finally { resume.countDown(); sender.cancelAndJoin() }
    }

    @Test fun contextChangeBeginsOnlyAfterTheSynchronousSendAndReceiptFinish() {
        val gate = ReminderDeliveryGate()
        val sendEntered = CountDownLatch(1)
        val finishSend = CountDownLatch(1)
        val changeRequested = CountDownLatch(1)
        val contextApplied = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(2)
        try {
            val sender = executor.submit {
                runBlocking {
                    gate.deliver({ AutomationSettings(rainEnabled = true) }, { weather },
                        { data, settings -> planWeatherReminders("old-city", data, settings, emptyMap(), at) },
                        { _, _ -> sendEntered.countDown(); check(finishSend.await(5, TimeUnit.SECONDS)); events += "sent" },
                        { events += "receipt" })
                }
            }
            assertTrue(sendEntered.await(5, TimeUnit.SECONDS))
            val writer = executor.submit {
                runBlocking {
                    changeRequested.countDown()
                    gate.changeContext { events += "context"; contextApplied.countDown() }
                }
            }
            assertTrue(changeRequested.await(5, TimeUnit.SECONDS))
            assertFalse("Context persistence must not complete inside a previous synchronous send", contextApplied.await(150, TimeUnit.MILLISECONDS))
            finishSend.countDown()
            sender.get(5, TimeUnit.SECONDS); writer.get(5, TimeUnit.SECONDS)
            assertEquals(listOf("sent", "receipt", "context"), events.toList())
        } finally { finishSend.countDown(); executor.shutdownNow() }
    }

    @Test fun awaitingContextPersistenceDoesNotHoldTheSynchronousSettingsBoundary() {
        val gate = ReminderDeliveryGate()
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val settingsChanged = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer = executor.submit {
                runBlocking { gate.changeContext { writeStarted.countDown(); check(finishWrite.await(5, TimeUnit.SECONDS)) } }
            }
            assertTrue(writeStarted.await(5, TimeUnit.SECONDS))
            val updater = executor.submit { gate.updateSettings { settingsChanged.countDown() } }
            assertTrue("Waiting for DataStore must leave synchronous settings updates available", settingsChanged.await(2, TimeUnit.SECONDS))
            finishWrite.countDown()
            writer.get(5, TimeUnit.SECONDS); updater.get(5, TimeUnit.SECONDS)
        } finally { finishWrite.countDown(); executor.shutdownNow() }
    }

    @Test fun closingRemindersDuringSchedulePreparationStillAppliesLatestDisabledSettings() = runBlocking {
        val gate = ReminderDeliveryGate()
        var settings = AutomationSettings(morningEnabled = true)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var applied: AutomationSettings? = null
        val scheduler = async(start = CoroutineStart.UNDISPATCHED) {
            gate.applySnapshot({ settings }, { entered.complete(Unit); resume.await(); "old-city" },
                { _, latest -> applied = latest })
        }
        entered.await()
        gate.updateSettings { settings = AutomationSettings() }
        resume.complete(Unit)
        assertTrue("An all-off snapshot must reach task cancellation", scheduler.await())
        assertEquals(AutomationSettings(), applied)
    }

    @Test fun changingDailyTimeDuringPreparationAppliesTheLatestAppointment() = runBlocking {
        val gate = ReminderDeliveryGate()
        var settings = AutomationSettings(morningEnabled = true, morningMinute = 7 * 60)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var checks = emptyList<DailyReminderCheck>()
        val scheduler = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.applySnapshot({ settings }, { entered.complete(Unit); resume.await(); "old-city" },
                { city, latest -> checks = nextDailyReminderChecks(city, 28_800, latest, emptyMap(), at) })
        }
        entered.await()
        gate.updateSettings { settings = settings.copy(morningMinute = 9 * 60 + 35) }
        resume.complete(Unit); scheduler.join()
        assertEquals(Instant.parse("2026-10-10T01:35:00Z").toEpochMilli(), checks.single().dueAt)
    }

    @Test fun changedCityDuringSchedulePreparationIsReReadBeforeAnyTaskIsApplied() = runBlocking {
        val gate = ReminderDeliveryGate()
        var city = "old-city"
        var preparations = 0
        val applied = mutableListOf<String>()
        assertTrue(applyLatestReminderSchedule(gate, { AutomationSettings(morningEnabled = true) }, {
            val captured = city
            if (preparations++ == 0) gate.changeContext { city = "new-city" }
            captured
        }, { captured, _ -> applied += captured }))
        assertEquals("The invalid old snapshot must be replaced", 2, preparations)
        assertEquals(listOf("new-city"), applied)
    }

    @Test fun repeatedContextChangesCannotCauseAnUnboundedSchedulePreparationLoop() = runBlocking {
        val gate = ReminderDeliveryGate()
        var preparations = 0
        var applied = 0
        assertFalse(applyLatestReminderSchedule(gate, { AutomationSettings(morningEnabled = true) }, {
            preparations++
            gate.changeContextNow { }
            "invalid-city"
        }, { _, _ -> applied++ }))
        assertEquals(3, preparations)
        assertEquals(0, applied)
    }

    @Test fun pendingCityWriteCannotCancelOrEnqueueTasksFromAnUnstableSnapshot() = runBlocking {
        val gate = ReminderDeliveryGate()
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { started.complete(Unit); resume.await() }
        }
        started.await()
        var preparations = 0
        var applied = 0
        assertFalse(applyLatestReminderSchedule(gate, { AutomationSettings() }, { preparations++; "city" },
            { _, _ -> applied++ }))
        assertEquals(0, preparations)
        assertEquals(0, applied)
        resume.complete(Unit); writer.join()
        assertTrue(applyLatestReminderSchedule(gate, { AutomationSettings() }, { "new-city" },
            { _, _ -> applied++ }))
        assertEquals(1, applied)
    }

    @Test fun cancelledScheduleOwnerCannotApplyEvenWhenPreparationDoesNotCooperate() = runBlocking {
        val gate = ReminderDeliveryGate()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        var applied = false
        val scheduler = launch(Dispatchers.Default) {
            gate.applySnapshot({ AutomationSettings() }, {
                entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)); "city"
            }, { _, _ -> applied = true })
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            scheduler.cancel(); resume.countDown(); scheduler.join()
            assertFalse(applied)
        } finally { resume.countDown(); scheduler.cancelAndJoin() }
    }

    private suspend fun assertPreparationInvalidated(gate: ReminderDeliveryGate, change: suspend () -> Unit) = coroutineScope {
        val prepared = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val receipts = mutableListOf<String>()
        val sender = launch(start = CoroutineStart.UNDISPATCHED) {
            deliver(gate, { prepared.complete(Unit); resume.await(); weather }, sent, receipts)
        }
        prepared.await()
        try { change() } finally { resume.complete(Unit) }
        sender.join()
        assertTrue("A context change completed after capture must invalidate every prepared notification", sent.isEmpty())
        assertTrue("Invalidated preparation must not record any delivery receipt", receipts.isEmpty())
    }

    private suspend fun deliver(gate: ReminderDeliveryGate, prepare: suspend () -> WeatherData?,
        sent: MutableList<String>, receipts: MutableList<String>) {
        gate.deliver({ AutomationSettings(rainEnabled = true) }, prepare,
            { data, settings -> planWeatherReminders("old-city", data, settings, emptyMap(), at) },
            { _, reminder -> sent += reminder.key }, { receipts += it.key })
    }

    private suspend fun withStore(repository: Any, action: suspend () -> Unit) {
        val field = repository.javaClass.getDeclaredField("store").apply { isAccessible = true }
        val previous = field.get(repository)
        val testStore = object : DataStore<Preferences> {
            var preferences: Preferences = emptyPreferences()
            override val data: Flow<Preferences> get() = flowOf(preferences)
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(preferences).also { preferences = it }
        }
        try { field.set(repository, testStore); action() }
        finally { field.set(repository, previous) }
    }
}
