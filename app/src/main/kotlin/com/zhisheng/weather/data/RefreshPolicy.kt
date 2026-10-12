package com.zhisheng.weather.data

/** Only successful fetches consume the normal interval; failures get a short backoff. */
internal class RefreshGate {
    private var successfulKey: String? = null
    private var successfulAt: Long = 0
    private var failedKey: String? = null
    private var failedAt: Long = 0
    fun shouldFetch(key: String, now: Long, intervalMillis: Long, activeKey: String?, intervalElapsed: Boolean = false): Boolean {
        if (activeKey == key) return false
        if (failedKey == key) return now - failedAt !in 0 until 30_000L
        // The foreground loop already waited the interval from its previous tick.
        // Reapplying it from request completion would skip every other tick after slow requests.
        if (intervalElapsed) return true
        return successfulKey != key || now - successfulAt !in 0 until intervalMillis
    }
    fun success(key: String, now: Long) { successfulKey = key; successfulAt = now; failedKey = null }
    fun failure(key: String, now: Long) { failedKey = key; failedAt = now }
}
