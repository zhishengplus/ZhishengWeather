package com.zhisheng.weather.ui

/** Monotonic refresh timing survives lifecycle pauses without starting duplicate timers. */
internal class RadarRefreshClock(createdAtElapsed: Long) {
    private var lastSuccessElapsed = createdAtElapsed
    private var lastAttemptElapsed = createdAtElapsed

    fun attempted(nowElapsed: Long) {
        lastAttemptElapsed = nowElapsed
    }

    fun succeeded(nowElapsed: Long) {
        lastSuccessElapsed = nowElapsed
    }

    fun remainingDelay(nowElapsed: Long, intervalMillis: Long): Long {
        val elapsed = (nowElapsed - maxOf(lastSuccessElapsed, lastAttemptElapsed)).coerceAtLeast(0L)
        return (intervalMillis - elapsed).coerceIn(0L, intervalMillis)
    }
}
