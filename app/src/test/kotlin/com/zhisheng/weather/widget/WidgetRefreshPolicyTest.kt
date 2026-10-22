package com.zhisheng.weather.widget

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.data.ReminderDeliveryGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WidgetRefreshPolicyTest {
    private val fixedCity = City("杭州", "浙江", 30.0, 120.0, "fixed")
    private val selected = City("金昌", "甘肃", 38.0, 102.0, "selected")
    private val config = WidgetConfig(locationMode = "fixed", cityKey = fixedCity.locationKey, fixedCity = fixedCity)
    private val weather = WeatherData(current = CurrentWeather(temperature = 15.0), fetchedAt = 123_000L)

    @Test fun successfulUnlistedFixedCityResultIsSavedAndPublished() = runBlocking {
        val targets = widgetRefreshTargets(listOf(config), selected, listOf(selected), null, null)
        val operations = mutableListOf<String>()
        assertTrue(publishWidgetRefresh(weather,
            isCurrent = { isCurrentWidgetRefreshCity(fixedCity, targets) },
            save = { operations += "saved" }, publish = { operations += "published" }))
        assertEquals(listOf("saved", "published"), operations)
        assertEquals(listOf(fixedCity to 30), targets)
    }

    @Test fun fixedCityStillPublishesAfterRemovalWhileCurrentCityBindingsFollowSelection() {
        val targets = widgetRefreshTargets(listOf(config, WidgetConfig()), selected, listOf(selected), null, 15)
        assertTrue(isCurrentWidgetRefreshCity(fixedCity, targets))
        assertEquals(2, targets.count { it.first == selected })
        assertFalse(isCurrentWidgetRefreshCity(fixedCity, widgetRefreshTargets(
            listOf(WidgetConfig()), selected, listOf(selected), null, null)))
    }

    @Test fun sameKeyWithChangedCoordinatesOrWeatherRoutingRejectsTheOldResponse() {
        val moved = fixedCity.copy(latitude = 31.0)
        val rerouted = fixedCity.copy(weatherLocationKey = "different-provider-city")
        for (changed in listOf(moved, rerouted)) {
            val targets = widgetRefreshTargets(listOf(config), selected, listOf(changed, selected), null, null)
            assertFalse(isCurrentWidgetRefreshCity(fixedCity, targets))
        }
    }

    @Test fun sourceChangeOrRemovedBindingBeforePublishingCannotWriteOrPublish() = runBlocking {
        val operations = mutableListOf<String>()
        assertFalse(publishWidgetRefresh(weather, isCurrent = { false },
            save = { operations += "saved" }, publish = { operations += "published" }))
        assertTrue(operations.isEmpty())
    }

    @Test fun bindingChangeDuringSuspendingCacheWritePreventsWidgetPublication() = runBlocking {
        var targets = widgetRefreshTargets(listOf(config), selected, listOf(selected), null, null)
        val saving = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        var published = false
        var accepted = true
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            accepted = publishWidgetRefresh(weather,
                isCurrent = { isCurrentWidgetRefreshCity(fixedCity, targets) },
                save = { saving.complete(Unit); finishSave.await() }, publish = { published = true })
        }
        saving.await()
        targets = widgetRefreshTargets(listOf(config.copy(cityKey = selected.locationKey, fixedCity = selected)),
            selected, listOf(selected), null, null)
        finishSave.complete(Unit)
        job.join()
        assertFalse(accepted)
        assertFalse(published)
    }

    @Test fun cancellationAndUnsuccessfulDataNeverPublish() = runBlocking {
        var published = false
        try {
            publishWidgetRefresh(weather, { true }, { throw CancellationException("replaced worker") }, { published = true })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(published)
        assertFalse(publishWidgetRefresh(weather.copy(error = "failed"), { true },
            { fail("Failed weather must not be saved") }, { published = true }))
        assertFalse(published)
    }

    @Test fun optionalWeatherCacheWriteFailureDoesNotSuppressTheValidWidgetSnapshot() = runBlocking {
        var published: WeatherData? = null
        assertTrue(publishWidgetRefresh(weather, { true }, { throw java.io.IOException("cache unavailable") },
            { published = weather }))
        assertEquals(weather, published)
    }

    @Test fun pendingContextWriteDefersAForcedRefreshInsteadOfClaimingNoBindings() = runBlocking {
        val gate = ReminderDeliveryGate()
        val writing = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.changeContext { writing.complete(Unit); finishWrite.await() }
        }
        writing.await()
        var reads = 0
        val pending = readStableWidgetRefreshTargets(gate::contextVersion) {
            reads++
            widgetRefreshTargets(listOf(config), selected, listOf(selected), null, null)
        }
        assertNull("An in-flight persisted context must defer rather than appear empty", pending)
        assertEquals(0, reads)
        assertTrue(shouldRetryWidgetRefresh(pending == null, 0))
        assertTrue(shouldRetryWidgetRefresh(pending == null, 1))
        assertFalse("A pending write must not create unlimited work retries", shouldRetryWidgetRefresh(pending == null, 2))
        finishWrite.complete(Unit)
        writer.join()
        val settled = readStableWidgetRefreshTargets(gate::contextVersion) {
            widgetRefreshTargets(listOf(config), selected, listOf(selected), null, null)
        }
        assertNotNull(settled)
        assertTrue(isCurrentWidgetRefreshCity(fixedCity, settled!!))
        val noBindings = readStableWidgetRefreshTargets(gate::contextVersion) { emptyList() }
        assertNotNull(noBindings)
        assertTrue(noBindings!!.isEmpty())
        assertFalse(shouldRetryWidgetRefresh(noBindings == null, 0))
    }

    @Test fun continuallyChangingContextCannotPublishAnOldRefreshSnapshot() = runBlocking {
        val gate = ReminderDeliveryGate()
        var published = false
        val accepted = publishWidgetRefresh(weather, isCurrent = {
            val snapshot = readStableWidgetRefreshTargets(gate::contextVersion) {
                gate.changeContext { }
                widgetRefreshTargets(listOf(config), selected, listOf(selected), null, null)
            }
            snapshot?.let { isCurrentWidgetRefreshCity(fixedCity, it) } == true
        }, save = { fail("An unstable snapshot must not save old weather") }, publish = { published = true })
        assertFalse(accepted)
        assertFalse(published)
    }
}
