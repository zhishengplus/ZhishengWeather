package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaceSearchCoordinatorTest {
    private val city = City("金川公园", "甘肃省·金昌市", 38.5, 102.2, "geo:poi:1")

    @Test fun personalProviderSuccessAvoidsAllLaterRequests() = runBlocking {
        var fallbackCalls = 0
        val result = coordinatePlaceSearch(listOf(
            { listOf(city) }, { fallbackCalls++; emptyList() },
        ))
        assertEquals(listOf(city), result.cities)
        assertEquals(0, fallbackCalls)
    }

    @Test fun personalAuthFailureStillAllowsFreeSystemResult() = runBlocking {
        val result = coordinatePlaceSearch(listOf(
            { throw PlaceServiceException(PlaceFailure.AUTH) }, { listOf(city) },
        ))
        assertEquals(listOf(city), result.cities)
        assertNull(result.failure)
    }

    @Test fun allFailuresAreNotReportedAsNoMatchingPlace() = runBlocking {
        val result = coordinatePlaceSearch(listOf({ throw IOException() }, { throw IOException() }))
        assertEquals(PlaceFailure.UNAVAILABLE, result.failure)
    }

    @Test fun genuineEmptyResultIsNotTurnedIntoAnOutage() = runBlocking {
        val result = coordinatePlaceSearch(listOf({ emptyList() }, { throw IOException() }))
        assertNull(result.failure)
        assertTrue(result.cities.isEmpty())
    }

    @Test fun cancellationNeverStartsAFallback() = runBlocking {
        var fallbackCalls = 0
        try {
            coordinatePlaceSearch(listOf({ throw CancellationException() }, { fallbackCalls++; listOf(city) }))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(0, fallbackCalls)
    }

    @Test fun configurationChangeDiscardsAResponseAlreadyInFlight() = runBlocking {
        var current = true
        val result = coordinatePlaceSearch(listOf({ current = false; listOf(city) }), isCurrent = { current })
        assertTrue(result.cities.isEmpty())
        assertEquals(PlaceFailure.CONFIG_CHANGED, result.failure)
    }

    @Test fun changingConfigurationAwayAndBackInvalidatesTheOldRequest() = runBlocking {
        val original = PlaceCredentials(amap = "same-key", revision = PlaceConfigurationRevision.current())
        val result = coordinatePlaceSearch(listOf({
            PlaceConfigurationRevision.changed()
            PlaceConfigurationRevision.changed()
            listOf(city)
        }), isCurrent = { original == original.copy(revision = PlaceConfigurationRevision.current()) })
        assertEquals(PlaceFailure.CONFIG_CHANGED, result.failure)
        assertTrue(result.cities.isEmpty())
        assertFalse(original.toString().contains("same-key"))
    }

    @Test fun wholeRequestBudgetCancelsSlowProvidersAndDoesNotMultiply() = runBlocking {
        val start = System.nanoTime()
        val result = coordinatePlaceSearch(List(5) { { delay(300); listOf(city) } }, totalMillis = 120, providerMillis = 80)
        assertEquals(PlaceFailure.TIMEOUT, result.failure)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
    }

    @Test fun authAndQuotaAreDistinguishedFromOtherServiceErrors() {
        assertEquals(PlaceFailure.AUTH, amapFailure("10001"))
        assertEquals(PlaceFailure.QUOTA, amapFailure("10003"))
        assertEquals(PlaceFailure.UNAVAILABLE, amapFailure("unexpected"))
    }

    @Test fun baiduInvalidAkIsNotConfusedWithSuccessfulStatus200() {
        assertEquals(PlaceFailure.AUTH, baiduFailure(200))
        assertEquals(PlaceFailure.AUTH, baiduFailure(5))
        assertEquals(PlaceFailure.QUOTA, baiduFailure(302))
        assertEquals(PlaceFailure.QUOTA, baiduFailure(401))
    }

    @Test fun nearbyDistinctPoisAreNotCollapsedAndRepeatedSourceIdIs() {
        val a = city.copy(name = "东门", street = "东门", locationKey = "geo:poi:amap:one")
        val b = city.copy(name = "西门", street = "西门", locationKey = "geo:poi:amap:two")
        assertEquals(2, mergePlaceSearchResults(listOf(a), listOf(a, b)).size)
    }
}
