package com.zhisheng.weather.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

class WeatherFetchBatchTest {
    @Test fun readyWeatherDoesNotWaitForBlockingAuxiliarySource() = runBlocking {
        val blockingStarted = CountDownLatch(1)
        val batch = WeatherFetchBatch(listOf<suspend () -> String?>(
            { blockingStarted.await(2, TimeUnit.SECONDS); "primary" },
            { blockingStarted.countDown(); Thread.sleep(900); "slow air" },
        ), coroutineContext.job)
        lateinit var result: List<String?>
        val elapsed = measureTimeMillis {
            result = batch.collect(timeoutMs = 2_000, settleAfterUsableMs = 60) { it == "primary" }
        }
        assertEquals("primary", result[0])
        assertNull("late auxiliary result must not hold up valid weather", result[1])
        assertTrue("ready weather waited ${elapsed}ms", elapsed < 600)
    }

    @Test fun airOnlyResponseDoesNotCutOffSlowerUsableWeather() = runBlocking {
        val batch = WeatherFetchBatch(listOf<suspend () -> String?>(
            { "air" },
            { delay(180); "weather" },
        ), coroutineContext.job)
        val result = batch.collect(timeoutMs = 1_000, settleAfterUsableMs = 20) { it == "weather" }
        assertEquals(listOf("air", "weather"), result)
    }

    @Test fun completedSourcesWithinCollectionWindowAreAllRetained() = runBlocking {
        val batch = WeatherFetchBatch(listOf<suspend () -> String?>(
            { "xiaomi" },
            { delay(50); "nmc" },
            { delay(80); "models" },
        ), coroutineContext.job)
        assertEquals(listOf("xiaomi", "nmc", "models"), batch.collect(1_000, 300))
    }

    @Test fun failingSupplementDoesNotDiscardSuccessfulSibling() = runBlocking {
        val batch = WeatherFetchBatch(listOf<suspend () -> String?>(
            { throw java.io.IOException("offline") },
            { "usable" },
        ), coroutineContext.job)
        assertEquals(listOf(null, "usable"), batch.collect(1_000))
    }

    @Test fun totalBudgetReturnsMissingResultsEvenWhenBlockingSourceIgnoresCancellation() = runBlocking {
        val batch = WeatherFetchBatch(listOf<suspend () -> String?>(
            { Thread.sleep(900); "late weather" },
        ), coroutineContext.job)
        lateinit var result: List<String?>
        val elapsed = measureTimeMillis { result = batch.collect(80) }
        assertEquals(listOf<String?>(null), result)
        assertTrue("total budget waited ${elapsed}ms", elapsed < 600)
    }

    @Test fun callerCancellationPropagatesAndCancelsCooperativeRequests() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        // The collector owns the batch: requests start before collect() suspends, so cancelling the
        // collector has to reach them whether or not collect() ever got as far as its finally block.
        val request = async {
            WeatherFetchBatch(listOf<suspend () -> String?>(
                { try { started.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) } },
            ), coroutineContext.job).collect(5_000)
        }
        started.await()
        request.cancel()
        try { request.await(); fail("must propagate cancellation") }
        catch (_: CancellationException) { }
        withTimeout(1_000) { stopped.await() }
    }

    @Test fun ownerCancelledBeforeCollectRunsStillStopsCooperativeRequests() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        // Nothing ever calls collect() here, so close() cannot run. The owner link is the only path
        // that can release the in-flight request; without it the coroutine outlives its caller.
        val owner = Job()
        WeatherFetchBatch(listOf<suspend () -> String?>(
            { try { started.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) } },
        ), owner)
        started.await()
        owner.cancel()
        withTimeout(1_000) { stopped.await() }
    }
}
