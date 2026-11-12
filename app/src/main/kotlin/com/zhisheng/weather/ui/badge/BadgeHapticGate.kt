package com.zhisheng.weather.ui.badge

/** Pure semantic gate. JS cannot request a reward or provide a receipt. No delayed vibration queue. */
internal class BadgeHapticGate {
    var active = false
    var enabled = true
    var reducedMotion = false
    private var lastAt: Long? = null
    private val rewarded = mutableSetOf<String>()
    fun interaction(event: String, now: Long): Boolean {
        if (event !in setOf("touch", "front", "back", "reset", "entry")) return false
        if (!active || !enabled || reducedMotion) return false
        val previous = lastAt
        if (previous != null && now - previous < 180) return false
        lastAt = now
        return true
    }
    fun success(grantId: String, now: Long): Boolean {
        if (grantId.isBlank() || !rewarded.add(grantId)) return false
        // Consume even while disabled/background: resuming must never replay a delayed success.
        if (!active || !enabled) return false
        lastAt = now
        return true
    }
}
