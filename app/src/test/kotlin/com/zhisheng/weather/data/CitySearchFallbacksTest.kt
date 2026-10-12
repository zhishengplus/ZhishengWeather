package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CitySearchFallbacksTest {
    private val city = City("北京", "北京", 39.9, 116.4, "beijing")
    @Test fun allServicesFailReportsFailureRatherThanAnEmptySearch() = runBlocking {
        try {
            searchCityWithFallbacks(listOf({ throw IOException("offline") }, { throw IOException("unavailable") }))
            fail("An unavailable search cannot be presented as no matching place")
        } catch (_: IOException) { }
    }
    @Test fun aWorkingFallbackStillReturnsResults() = runBlocking {
        assertEquals(listOf(city), searchCityWithFallbacks(listOf({ throw IOException() }, { listOf(city) })))
    }
    @Test fun aSuccessfulEmptyResponseKeepsThePlaceNameAdvice() = runBlocking {
        assertTrue(searchCityWithFallbacks(listOf({ emptyList() }, { throw IOException() })).isEmpty())
    }
    @Test fun leavingSearchCancelsWithoutTryingTheNextProvider() = runBlocking {
        var called = false
        try {
            searchCityWithFallbacks(listOf({ throw CancellationException("left") }, { called = true; listOf(city) }))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(called)
    }
}
