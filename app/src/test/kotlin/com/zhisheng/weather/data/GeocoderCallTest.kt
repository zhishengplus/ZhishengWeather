package com.zhisheng.weather.data

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class GeocoderCallTest {
    @Test fun genuineEmptyAddressAndServiceErrorStayDifferentOnOldAndroid() = runBlocking {
        assertNull(geocoderCall<String>(strict = true) { null })
        try {
            geocoderCall<String>(strict = true) { throw java.io.IOException("service failure") }
            fail("A failing service is not an empty successful lookup")
        } catch (error: PlaceServiceException) { assertEquals(PlaceFailure.UNAVAILABLE, error.failure) }
    }

    @Test fun geocoderTimeoutDoesNotWaitForStuckSystemService() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val result = CompletableFuture.supplyAsync {
            runBlocking {
                withTimeoutOrNull(200L) {
                    geocoderCall {
                        entered.countDown()
                        // Model a binder service that ignores interruption.
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { }
                        }
                        "late address"
                    }
                }
            }
        }
        try {
            assertTrue("System call must start", entered.await(2, TimeUnit.SECONDS))
            val returnedBeforeService = try {
                assertNull(result.get(2, TimeUnit.SECONDS))
                true
            } catch (_: java.util.concurrent.TimeoutException) { false }
            assertTrue("Location timeout must return even while the system geocoder is blocked", returnedBeforeService)
        } finally {
            release.countDown()
            result.get(3, TimeUnit.SECONDS)
        }
    }
}
