package com.zhisheng.weather.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class LocationFixAcquisitionTest {
    private val now = 1_800_000_000_000L
    private val elapsed = 5_000_000_000_000L
    private fun fix(age: Long = 1_000, accuracy: Float = 1_500f) =
        LocationFix(31.23, 121.47, now - age, elapsed - age * 1_000_000, accuracy)

    private class Provider(val inline: LocationFix? = null, val completeInline: Boolean = false) {
        var cancelled = 0
        var started = 0
        var callback: ((LocationFix?) -> Unit)? = null
        val request = LocationFixRequest { result ->
            started++
            callback = result
            if (completeInline) result(inline)
            ({ cancelled++ })
        }
    }

    private suspend fun locate(
        providers: List<Provider>, cached: List<LocationFix> = emptyList(),
        approximate: Boolean = true, preferStreet: Boolean = false,
        timeout: Long = 500, cacheGrace: Long = 5, precisionGrace: Long = 5,
    ) = acquireLocationFix(providers.map { it.request }, cached, approximate, preferStreet,
        { now }, { elapsed }, timeout, cacheGrace, precisionGrace)

    @Test fun tenSecondCacheDoesNotStartOrWaitForProviders() = runBlocking {
        val provider = Provider()
        val cached = fix(9_000)
        assertEquals(cached, locate(listOf(provider), listOf(cached)))
        assertEquals(0, provider.started)
    }

    @Test fun coarseSystemCacheOlderThanTwoMinutesRemainsAnHonestFallback() = runBlocking {
        val stalled = Provider()
        val cached = fix(8 * 60_000)
        assertEquals(cached, withTimeout(1_000) { locate(listOf(stalled), listOf(cached)) })
        assertEquals(8 * 60_000L, LocationFixPolicy.ageMillis(cached, now, elapsed))
        assertEquals(1, stalled.cancelled)
    }

    @Test fun precisePermissionCannotExtendCacheToTenMinutes() = runBlocking {
        val cached = fix(8 * 60_000, 30f)
        assertNull(locate(emptyList(), listOf(cached), approximate = false))
    }

    @Test fun aFreshProviderWinsOverOlderCache() = runBlocking {
        val fresh = fix()
        val provider = Provider(fresh, true)
        assertEquals(fresh, locate(listOf(provider), listOf(fix(60_000)), cacheGrace = 500))
        assertEquals(1, provider.cancelled)
    }

    @Test fun anOldCallbackCannotDisplaceNewerCachedCoordinates() = runBlocking {
        val cached = fix(30_000)
        assertEquals(cached, locate(listOf(Provider(fix(8 * 60_000), true)), listOf(cached)))
    }

    @Test fun nullProviderDoesNotDefeatAnotherProviderAndAllRequestsStopOnSuccess() = runBlocking {
        val empty = Provider(null, true)
        val fresh = fix()
        val success = Provider(fresh, true)
        val pending = Provider()
        assertEquals(fresh, locate(listOf(empty, success, pending)))
        assertEquals(listOf(1, 1, 1), listOf(empty.cancelled, success.cancelled, pending.cancelled))
    }

    @Test fun allProvidersUnavailableReturnWithoutWaitingForTimeout() = runBlocking {
        assertNull(withTimeout(1_000) { locate(listOf(Provider(null, true)), timeout = 60_000) })
    }

    @Test fun noFineFixDoesNotHoldCityWeatherUntilTheLongTimeout() = runBlocking {
        val networkFix = fix(accuracy = 900f)
        val network = Provider(networkFix, true)
        val stalledGps = Provider()
        assertEquals(networkFix, withTimeout(1_000) {
            locate(listOf(network, stalledGps), approximate = false, preferStreet = true, timeout = 60_000)
        })
        assertEquals(1, stalledGps.cancelled)
    }

    @Test fun fineFixWithinGraceCanStillImproveNetworkCoordinates() = runBlocking {
        val fine = fix(accuracy = 30f)
        assertEquals(fine, locate(listOf(Provider(fix(accuracy = 900f), true), Provider(fine, true)),
            approximate = false, preferStreet = true))
    }

    @Test fun cancellationStopsEveryNativeRequestAndLateRepliesCannotCommit() = runBlocking {
        val first = Provider()
        val second = Provider()
        var committed = false
        val pending = async { locate(listOf(first, second), timeout = 60_000).also { committed = true } }
        yield()
        pending.cancelAndJoin()
        first.callback?.invoke(fix())
        second.callback?.invoke(fix())
        assertFalse(committed)
        assertEquals(1, first.cancelled)
        assertEquals(1, second.cancelled)
    }

    @Test fun malformedAndFifteenMinuteMeasurementsCannotSucceed() = runBlocking {
        assertNull(locate(listOf(Provider(fix().copy(latitude = Double.NaN), true)), listOf(fix(15 * 60_000))))
        assertNull(locate(listOf(Provider(fix(accuracy = 30_000f), true))))
    }

    @Test fun aThrowingProviderDoesNotLeakOrCancelOtherSources() = runBlocking {
        val fresh = fix()
        var stopped = false
        val requests = listOf(LocationFixRequest { throw SecurityException("provider unavailable") },
            LocationFixRequest { reply -> reply(fresh); ({ stopped = true }) })
        assertEquals(fresh, acquireLocationFix(requests, emptyList(), true, false, { now }, { elapsed }))
        assertTrue(stopped)
    }

    @Test fun aProviderThatNeverRepliesIsBoundedAndCancelled() = runBlocking {
        val provider = Provider()
        assertNull(withTimeout(1_000) { locate(listOf(provider), timeout = 5) })
        assertEquals(1, provider.cancelled)
    }

    @Test fun cancellationDuringRegistrationStillStopsAlreadyRegisteredProviders() = runBlocking {
        var cancelled = false
        val pending = LocationFixRequest { ({ cancelled = true }) }
        val reject = LocationFixRequest { throw kotlinx.coroutines.CancellationException("caller left") }
        val outcome = async {
            acquireLocationFix(listOf(pending, reject), emptyList(), true, false, { now }, { elapsed })
        }
        outcome.join()
        assertTrue(outcome.isCancelled)
        assertTrue(cancelled)
    }
}
