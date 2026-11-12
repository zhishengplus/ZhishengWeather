package com.zhisheng.weather.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AutomationSettingsReschedulerTest {
    @Test fun aPausedOldPlanCannotOverwriteTheLatestSavedInterval() = runBlocking {
        withTimeout(5_000) {
            val rescheduler = AutomationSettingsRescheduler(this)
            var storedInterval = 15
            var scheduledInterval = 0
            val applyingOld = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val applyingNew = CompletableDeferred<Unit>()
            val first = rescheduler.request {
                val interval = storedInterval
                applyingOld.complete(Unit)
                releaseOld.await()
                scheduledInterval = interval
            }
            applyingOld.await()
            storedInterval = 60
            val second = rescheduler.request {
                applyingNew.complete(Unit)
                scheduledInterval = storedInterval
            }
            yield()
            val overlapped = applyingNew.isCompleted
            releaseOld.complete(Unit)
            first.join()
            second.join()
            assertFalse("A later save must wait until the earlier side effects finish", overlapped)
            assertEquals(60, scheduledInterval)
        }
    }

    @Test fun disablingBackgroundWhileAnOldPlanWaitsLeavesNoPeriodicWork() = runBlocking {
        withTimeout(5_000) {
            val rescheduler = AutomationSettingsRescheduler(this)
            var backgroundEnabled = true
            var periodicWorkExists = false
            val readingOld = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val first = rescheduler.request {
                val enabled = backgroundEnabled
                readingOld.complete(Unit)
                releaseOld.await()
                periodicWorkExists = enabled
            }
            readingOld.await()
            backgroundEnabled = false
            val second = rescheduler.request { periodicWorkExists = backgroundEnabled }
            yield()
            releaseOld.complete(Unit)
            first.join()
            second.join()
            assertFalse("The completed close must be the final scheduling decision", periodicWorkExists)
        }
    }
}
