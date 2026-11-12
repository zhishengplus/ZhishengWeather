@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.zhisheng.weather.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.internal.DraggableAnchors
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ScrollableSheetCompatibilityTest {
    private fun sheet(
        initial: SheetValue = SheetValue.Expanded,
        skipPartial: Boolean = true,
        confirm: (SheetValue) -> Boolean = scrollableSheetConfirmValueChange,
    ): SheetState = SheetState(
        skipPartiallyExpanded = skipPartial,
        density = Density(3.25f),
        initialValue = initial,
        confirmValueChange = confirm,
    ).also { state ->
        state.anchoredDraggableState.updateAnchors(DraggableAnchors {
            SheetValue.Expanded at 162f
            if (!skipPartial) SheetValue.PartiallyExpanded at 1456f
            SheetValue.Hidden at 2912f
        })
    }

    @Test fun expandedFlingNeedsNeitherAJobNorAnAnimationFrameAtItsExactAnchor() {
        val clock = BroadcastFrameClock()
        runBlocking(clock) {
            for (skipPartial in listOf(false, true)) {
                val state = sheet(skipPartial = skipPartial)
                val originalDragState = state.anchoredDraggableState
                val originalAnimationSpec = originalDragState.animationSpec
                var dismissals = 0
                for (velocity in listOf(-6000f, -1500f, 0f, 1500f, 6000f)) {
                    assertTrue(canSkipScrollableSheetSettle(state))
                    assertNull(launchScrollableSheetSettle(state, velocity) { dismissals++ })
                    assertFalse(state.anchoredDraggableState.isAnimationRunning)
                    assertFalse(clock.hasAwaiters)
                    assertEquals(162f, state.requireOffset(), 0f)
                    assertEquals(SheetValue.Expanded, state.currentValue)
                    assertEquals(SheetValue.Expanded, state.targetValue)
                }
                assertEquals(0, dismissals)
                assertSame(originalDragState, state.anchoredDraggableState)
                assertSame(originalAnimationSpec, state.anchoredDraggableState.animationSpec)
            }
        }
    }

    @Test fun originalMaterial3StillSetsDragTargetBeforeTheFirstFrameAtTheSameAnchor() {
        val clock = BroadcastFrameClock()
        runBlocking(clock) {
            val state = sheet()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { state.settle(0f) }
            try {
                // This is the gap a zero-duration AnimationSpec cannot remove.
                assertTrue(state.anchoredDraggableState.isAnimationRunning)
                assertTrue(clock.hasAwaiters)
                assertFalse(job.isCompleted)
                assertEquals(162f, state.requireOffset(), 0f)
                assertFalse(canSkipScrollableSheetSettle(state))
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test fun aQueuedFlingRechecksTheAnchorBeforeStartingAnAnimation() {
        val clock = BroadcastFrameClock()
        runBlocking(clock) {
            val state = sheet()
            state.anchoredDraggableState.dispatchRawDelta(1f)
            var dismissals = 0
            val job = launchScrollableSheetSettle(state, 1500f) { dismissals++ }
            assertNotNull(job)
            // The launch is queued on this event loop. A newer gesture returns to the anchor first.
            state.anchoredDraggableState.dispatchRawDelta(-1f)
            yield()
            assertTrue(job!!.isCompleted)
            assertFalse(clock.hasAwaiters)
            assertFalse(state.anchoredDraggableState.isAnimationRunning)
            assertEquals(SheetValue.Expanded, state.currentValue)
            assertEquals(0, dismissals)
        }
    }

    @Test fun tinyRealMovementAndAllMovingFramesKeepTheOriginalSpecStatesAndVelocity() {
        for (delta in listOf(Math.nextUp(162f) - 162f, 1f, 180f, 1700f)) {
            for (velocity in listOf(-1500f, 0f, 1500f)) {
                val clock = BroadcastFrameClock()
                runBlocking(clock) {
                    val original = sheet()
                    val compatible = sheet()
                    original.anchoredDraggableState.dispatchRawDelta(delta)
                    compatible.anchoredDraggableState.dispatchRawDelta(delta)
                    assertFalse(canSkipScrollableSheetSettle(compatible))
                    assertSame(original.anchoredDraggableState.animationSpec,
                        compatible.anchoredDraggableState.animationSpec)
                    var dismissals = 0
                    val originalJob = launch { original.settle(velocity) }
                    val compatibleJob = launchScrollableSheetSettle(compatible, velocity) { dismissals++ }!!
                    try {
                        yield()
                        for (frame in 0..40) {
                            clock.sendFrame(frame * 8_333_333L)
                            yield()
                            assertSheetEquals(original, compatible)
                        }
                        assertTrue(originalJob.isCompleted)
                        assertTrue(compatibleJob.isCompleted)
                        assertEquals(if (compatible.isVisible) 0 else 1, dismissals)
                    } finally {
                        originalJob.cancelAndJoin()
                        compatibleJob.cancelAndJoin()
                    }
                }
            }
        }
    }

    @Test fun partialHiddenAndCustomConfirmationStatesUseTheCompleteOriginalSettle() {
        for (initial in SheetValue.entries) for (allowed in listOf(false, true)) {
            val originalConfirmations = mutableListOf<SheetValue>()
            val compatibleConfirmations = mutableListOf<SheetValue>()
            val original = sheet(initial, skipPartial = false, confirm = {
                originalConfirmations += it
                allowed
            })
            val compatible = sheet(initial, skipPartial = false, confirm = {
                compatibleConfirmations += it
                allowed
            })
            originalConfirmations.clear()
            compatibleConfirmations.clear()
            val clock = BroadcastFrameClock()
            runBlocking(clock) {
                assertFalse(canSkipScrollableSheetSettle(compatible))
                var dismissals = 0
                val originalJob = launch { original.settle(1500f) }
                val compatibleJob = launchScrollableSheetSettle(compatible, 1500f) { dismissals++ }!!
                try {
                    yield()
                    assertTrue(clock.hasAwaiters)
                    for (frame in 0..40) {
                        clock.sendFrame(frame * 8_333_333L)
                        yield()
                        assertSheetEquals(original, compatible)
                    }
                    assertTrue(originalJob.isCompleted)
                    assertTrue(compatibleJob.isCompleted)
                    assertEquals(originalConfirmations, compatibleConfirmations)
                    assertEquals(if (compatible.isVisible) 0 else 1, dismissals)
                } finally {
                    originalJob.cancelAndJoin()
                    compatibleJob.cancelAndJoin()
                }
            }
        }
        assertFalse(canSkipScrollableSheetSettle(sheet(SheetValue.PartiallyExpanded, skipPartial = false)))
        assertFalse(canSkipScrollableSheetSettle(sheet(SheetValue.Hidden)))
    }

    @Test fun unfinishedShowHideAndUnmeasuredSheetsCannotTakeTheShortcut() {
        for (initial in SheetValue.entries) {
            val unmeasured = SheetState(
                skipPartiallyExpanded = false,
                density = Density(3.25f),
                initialValue = initial,
                confirmValueChange = scrollableSheetConfirmValueChange,
            )
            assertFalse(canSkipScrollableSheetSettle(unmeasured))
        }
        val clock = BroadcastFrameClock()
        runBlocking(clock) {
            val state = sheet()
            val hide = launch(start = CoroutineStart.UNDISPATCHED) { state.hide() }
            try {
                assertEquals(162f, state.requireOffset(), 0f)
                assertEquals(SheetValue.Hidden, state.targetValue)
                assertTrue(state.anchoredDraggableState.isAnimationRunning)
                assertFalse(canSkipScrollableSheetSettle(state))
            } finally {
                hide.cancelAndJoin()
            }
        }
    }

    private fun assertSheetEquals(expected: SheetState, actual: SheetState) {
        assertEquals(expected.requireOffset(), actual.requireOffset(), 0f)
        assertEquals(expected.currentValue, actual.currentValue)
        assertEquals(expected.targetValue, actual.targetValue)
        assertEquals(expected.isVisible, actual.isVisible)
        assertEquals(expected.anchoredDraggableState.isAnimationRunning,
            actual.anchoredDraggableState.isAnimationRunning)
        assertEquals(expected.anchoredDraggableState.lastVelocity,
            actual.anchoredDraggableState.lastVelocity, 0f)
    }
}
