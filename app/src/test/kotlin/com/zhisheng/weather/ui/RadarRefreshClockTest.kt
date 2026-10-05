package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarRefreshClockTest {
    private val interval = 300_000L

    @Test fun longBackgroundStayRefreshesImmediately() {
        val clock = RadarRefreshClock(1_000L)
        clock.succeeded(2_000L)
        assertEquals(0L, clock.remainingDelay(7_202_000L, interval))
    }

    @Test fun shortBackgroundStayOnlyWaitsTheRemainingInterval() {
        val clock = RadarRefreshClock(1_000L)
        clock.succeeded(2_000L)
        assertEquals(180_000L, clock.remainingDelay(122_000L, interval))
        assertEquals(0L, clock.remainingDelay(302_000L, interval))
    }

    @Test fun aFailedAttemptDoesNotRetryImmediatelyOnResume() {
        val clock = RadarRefreshClock(1_000L)
        clock.succeeded(2_000L)
        clock.attempted(602_000L)
        assertEquals(290_000L, clock.remainingDelay(612_000L, interval))
    }

    @Test fun successfulCompletionStartsTheNextInterval() {
        val clock = RadarRefreshClock(1_000L)
        clock.attempted(301_000L)
        clock.succeeded(306_000L)
        assertEquals(interval, clock.remainingDelay(306_000L, interval))
    }
}
