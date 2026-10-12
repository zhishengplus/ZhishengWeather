package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    @Test fun periodicTickAtFiveMinutesDoesNotWaitForSuccessCompletionOffset() {
        val gate = RefreshGate()
        val startedAt = 1_000_000L
        gate.success("a", startedAt + 20_000L)
        assertTrue(gate.shouldFetch("a", startedAt + 300_000L, 300_000L, null, intervalElapsed = true))
    }

    @Test fun periodicTickCannotRestartInFlightRequestForSameCity() {
        val gate = RefreshGate()
        gate.success("a", 1_020_000L)
        assertFalse(gate.shouldFetch("a", 1_300_000L, 300_000L, "a", intervalElapsed = true))
    }

    @Test fun periodicTickStillHonorsFailureBackoffUntilThirtySeconds() {
        val gate = RefreshGate()
        gate.success("a", 1_020_000L)
        gate.failure("a", 1_290_000L)
        assertFalse(gate.shouldFetch("a", 1_319_999L, 300_000L, null, intervalElapsed = true))
        assertTrue(gate.shouldFetch("a", 1_320_000L, 300_000L, null, intervalElapsed = true))
    }

    @Test fun ordinaryForegroundResumeKeepsCompletionTimeThrottle() {
        val gate = RefreshGate()
        gate.success("a", 1_020_000L)
        assertFalse(gate.shouldFetch("a", 1_300_000L, 300_000L, null, intervalElapsed = false))
        assertFalse(gate.shouldFetch("a", 1_300_000L, 300_000L, null))
        assertTrue(gate.shouldFetch("a", 1_320_000L, 300_000L, null, intervalElapsed = false))
    }

    @Test fun failedRequestCanRetryWithoutWaitingFullInterval() {
        val gate = RefreshGate()
        assertTrue(gate.shouldFetch("a", 1_000_000, 300_000, null))
        gate.failure("a", 1_000_000)
        assertFalse(gate.shouldFetch("a", 1_020_000, 300_000, null))
        assertTrue(gate.shouldFetch("a", 1_031_000, 300_000, null))
    }
    @Test fun successIsThrottledButOtherCityIsNot() {
        val gate = RefreshGate()
        gate.success("a", 1_000_000)
        assertFalse(gate.shouldFetch("a", 1_299_999, 300_000, null))
        assertTrue(gate.shouldFetch("a", 1_300_000, 300_000, null))
        assertTrue(gate.shouldFetch("b", 1_010_000, 300_000, "a"))
    }
    @Test fun inFlightIsNotRestartedAndClockGoingBackwardDoesNotFreezeRefresh() {
        val gate = RefreshGate()
        assertFalse(gate.shouldFetch("a", 1_000_000, 300_000, "a"))
        gate.success("a", 1_000_000)
        assertTrue(gate.shouldFetch("a", 900_000, 300_000, null))
    }
}
