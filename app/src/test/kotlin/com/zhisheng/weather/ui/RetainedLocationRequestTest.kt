package com.zhisheng.weather.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RetainedLocationRequestTest {
    @Test fun recreatingTheCallerRetainsTheResultAndSuccessCommit() = runBlocking {
        val owner = RetainedLocationRequest(this)
        val fix = CompletableDeferred<Unit>()
        var commits = 0
        val firstCaller = async { owner.run { fix.await(); commits++; true } }
        yield()
        firstCaller.cancelAndJoin()
        val restoredCaller = async { owner.awaitResult() }
        fix.complete(Unit)
        yield()
        assertTrue(restoredCaller.await())
        assertEquals(1, commits)
    }

    @Test fun concurrentCallersShareOneLocationRequest() = runBlocking {
        val owner = RetainedLocationRequest(this)
        val fix = CompletableDeferred<Unit>()
        var attempts = 0
        val first = async { owner.run { attempts++; fix.await(); true } }
        val second = async { owner.run { attempts++; true } }
        yield()
        fix.complete(Unit)
        yield()
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, attempts)
    }

    @Test fun completedSuccessSurvivesUntilExplicitlyCleared() = runBlocking {
        val owner = RetainedLocationRequest(this)
        val first = async { owner.run { true } }
        yield()
        assertTrue(first.await())
        assertTrue(owner.awaitResult())
        owner.cancel()
        assertFalse(owner.awaitResult())
    }

    @Test fun failureCanBeRetriedButRestorationDoesNotRetryIt() = runBlocking {
        val owner = RetainedLocationRequest(this)
        var attempts = 0
        val failed = async { owner.run { attempts++; false } }
        yield()
        assertFalse(failed.await())
        assertFalse(owner.awaitResult())
        assertEquals(1, attempts)
        val retry = async { owner.run { attempts++; true } }
        yield()
        assertTrue(retry.await())
        assertEquals(2, attempts)
    }

    @Test fun choosingAManualCityCancelsTheOwnedOperation() = runBlocking {
        val owner = RetainedLocationRequest(this)
        val fix = CompletableDeferred<Unit>()
        var committed = false
        val caller = async { owner.run { fix.await(); committed = true; true } }
        yield()
        owner.cancel()
        fix.complete(Unit)
        yield()
        caller.join()
        assertTrue(caller.isCancelled)
        assertFalse(committed)
        assertFalse(owner.awaitResult())
    }

    @Test fun anAbsentRequestDoesNotPretendToHaveLocated() = runBlocking {
        assertFalse(RetainedLocationRequest(this).awaitResult())
    }
}
