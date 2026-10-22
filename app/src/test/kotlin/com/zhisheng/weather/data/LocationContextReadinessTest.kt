package com.zhisheng.weather.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocationContextReadinessTest {
    @Test fun firstFixWaitsForAllOverlappingSetupWrites() = runBlocking {
        val gate = ReminderDeliveryGate()
        val consent = CompletableDeferred<Unit>()
        val precision = CompletableDeferred<Unit>()
        val a = launch(start = CoroutineStart.UNDISPATCHED) { gate.changeContext { consent.await() } }
        val b = launch(start = CoroutineStart.UNDISPATCHED) { gate.changeContext { precision.await() } }
        var fixes = 0
        val fix = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitContextVersion().also { fixes++ } }
        assertFalse(fix.isCompleted)
        consent.complete(Unit)
        a.join()
        assertFalse("One remaining setup write must still hold the first fix", fix.isCompleted)
        precision.complete(Unit)
        b.join()
        assertEquals(gate.contextVersion(), fix.await())
        assertEquals(1, fixes)
        gate.changeContextNow { }
        assertNotEquals("Later settings changes must still invalidate the acquired version", fix.await(), gate.contextVersion())
    }

    @Test fun failedSetupWriteReleasesWaitingFix() = runBlocking {
        val gate = ReminderDeliveryGate()
        val finish = CompletableDeferred<Unit>()
        val write = launch(start = CoroutineStart.UNDISPATCHED) {
            try { gate.changeContext { finish.await(); error("write failed") } } catch (_: IllegalStateException) { }
        }
        val fix = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitContextVersion() }
        assertFalse(fix.isCompleted)
        finish.complete(Unit)
        write.join()
        assertEquals(gate.contextVersion(), fix.await())
    }

    @Test fun cancelledSetupWriteReleasesWaitingFix() = runBlocking {
        val gate = ReminderDeliveryGate()
        val write = launch(start = CoroutineStart.UNDISPATCHED) { gate.changeContext { awaitCancellation() } }
        val fix = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitContextVersion() }
        assertFalse(fix.isCompleted)
        write.cancelAndJoin()
        assertEquals(gate.contextVersion(), fix.await())
    }

    @Test fun leavingLocationFlowCancelsWaitWithoutCancellingSettingsOrNextRequest() = runBlocking {
        val gate = ReminderDeliveryGate()
        val finish = CompletableDeferred<Unit>()
        val write = launch(start = CoroutineStart.UNDISPATCHED) { gate.changeContext { finish.await() } }
        val abandoned = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitContextVersion() }
        abandoned.cancelAndJoin()
        assertTrue(write.isActive)
        val next = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitContextVersion() }
        assertFalse(next.isCompleted)
        finish.complete(Unit)
        write.join()
        assertEquals(gate.contextVersion(), next.await())
    }
}
