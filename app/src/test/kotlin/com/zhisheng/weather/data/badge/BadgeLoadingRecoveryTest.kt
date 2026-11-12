package com.zhisheng.weather.data.badge

import com.zhisheng.weather.ui.badge.BadgeStartupDeadline
import com.zhisheng.weather.ui.badge.badgeCriticalResource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.Assert.*
import org.junit.Test

class BadgeLoadingRecoveryTest {
    @Test fun ownershipPollingNeverSilentlyRestartsFailedModelsButExplicitRetryCanRun() {
        var state: BadgeModelUiState? = null; var requests = 0
        fun prepare(retry: Boolean = false) {
            if (shouldPrepareBadgeModel(state, retry, false)) { requests++; state = BadgeModelUiState("error", attempt = requests.toLong()) }
        }
        prepare(); repeat(240) { prepare() }; assertEquals(1, requests)
        prepare(true); assertEquals(2, requests)
        assertFalse(shouldPrepareBadgeModel(state, true, true))
        assertFalse(shouldPrepareBadgeModel(BadgeModelUiState("ready"), false, false))
    }

    @Test fun onlyAChangedSignedAssetBindingReopensAutomaticPreparationAfterFailure() {
        val grant = BadgeGrant(1,"clear-sky-crown","fixture","subject","grant",88,1,modelAssetId="original")
        val error = BadgeModelUiState("error",attempt=1,requestedAssetId="original")
        assertFalse(badgeModelBindingChanged(error, grant))
        assertTrue(badgeModelBindingChanged(error, grant.copy(modelAssetId="replacement")))
        assertFalse(badgeModelBindingChanged(error, grant.copy(engravingPublicId="text only")))
        assertFalse(badgeModelBindingChanged(null, grant))
    }
    @Test fun duplicateReadyAndResumeEventsDoNotExtendTheUncommittedFirstFrameBudget() {
        val startup = BadgeStartupDeadline()
        assertEquals(20_000L, startup.resume(0))
        assertEquals(15_000L, startup.resume(5_000))
        assertEquals(1_000L, startup.resume(19_000))
        assertEquals(0L, startup.resume(20_001))
        startup.committed(); assertNull(startup.resume(30_000))
        startup.reset(); assertEquals(20_000L, startup.resume(30_000))
    }

    @Test fun backgroundTimeDoesNotConsumeOrResetTheRemainingStartupBudget() {
        val startup = BadgeStartupDeadline()
        startup.resume(0); startup.pause(8_000)
        assertEquals(12_000L, startup.resume(90_000))
        assertEquals(0L, startup.resume(102_000))
    }

    @Test fun oneModelHttpFailureRemainsLocalWhileSceneCodeAndEnvironmentAreFatal() {
        val origin = "https://badge.local.invalid/"
        for (path in listOf("first-light.glb", "models/${"a".repeat(64)}.glb")) {
            assertFalse(badgeCriticalResource(origin + path, "GET", false, true))
            assertTrue(badgeCriticalResource(origin + path, "GET", false, false))
        }
        for (path in listOf("viewer/gallery-viewer.mjs", "model.json", "clearsky-softboxes.hdr"))
            assertTrue(badgeCriticalResource(origin + path, "GET", false, true))
        assertTrue(badgeCriticalResource(origin + "viewer/cabinet.html", "GET", true, true))
        assertFalse(badgeCriticalResource("https://other.invalid/model.json", "GET", false, true))
    }

    @Test fun expiredModelAndQueueWaitBecomeRetryableFailuresWithoutLeakingPermits() = runBlocking {
        val queue = Semaphore(1)
        try {
            withBadgeModelPermit(queue) { withinBadgeModelBudget(15) { awaitCancellation() } }
            fail("A stalled model must finish with a retryable state")
        } catch (error: BadgeClientException) { assertEquals("MODEL_PREPARATION_TIMEOUT", error.code) }
        assertEquals(1, queue.availablePermits)
        queue.acquire()
        try {
            withBadgeModelPermit(queue, 15) { fail("A timed out queue must not start another download") }
            fail()
        } catch (error: BadgeClientException) { assertEquals("MODEL_PREPARATION_TIMEOUT", error.code) }
        assertEquals(0, queue.availablePermits)
        queue.release(); assertEquals(1, queue.availablePermits)
    }

    @Test fun leavingThePageCancelsPendingWorkAndReleasesAnAcquiredPermit() = runBlocking {
        val queue = Semaphore(1); val entered = CompletableDeferred<Unit>()
        val task = launch {
            withBadgeModelPermit(queue) { entered.complete(Unit); withinBadgeModelBudget(60_000) { awaitCancellation() } }
        }
        entered.await(); task.cancelAndJoin(); assertEquals(1, queue.availablePermits)
        try {
            withTimeout(15) { withinBadgeModelBudget(60_000) { awaitCancellation() } }
            fail()
        } catch (_: CancellationException) { /* Parent cancellation must not become a tile failure. */ }
    }

    @Test fun anUncooperativeReadStillEndsTheLoadingStateWithoutWaitingForTheReadToReturn() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = java.util.concurrent.CountDownLatch(1)
        val expired = CompletableDeferred<Unit>()
        val work = launch(Dispatchers.IO) { entered.complete(Unit); release.await(5, java.util.concurrent.TimeUnit.SECONDS) }
        var current = true
        val guard = watchBadgeModelWork(this, work, 15, { current }, { expired.complete(Unit) })
        try {
            entered.await(); withTimeout(1_000) { expired.await() }
            assertTrue(work.isCancelled); assertFalse(work.isCompleted)
        } finally { current = false; release.countDown(); work.join(); guard.cancelAndJoin() }
    }

    @Test fun aDeadlineFromAnOldVisitCannotCancelReplacementWork() = runBlocking {
        val work = launch { awaitCancellation() }; var expired = false
        val guard = watchBadgeModelWork(this, work, 15, { false }, { expired = true })
        guard.join(); assertFalse(expired); assertTrue(work.isActive); work.cancelAndJoin()
    }
}
