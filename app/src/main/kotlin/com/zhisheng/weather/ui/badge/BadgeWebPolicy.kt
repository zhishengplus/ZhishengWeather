package com.zhisheng.weather.ui.badge

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Return an APK asset path only. This renderer has no general-purpose URL/file loader. */
internal fun badgeAssetPath(url: String, method: String = "GET"): String? = runCatching {
    require(url.length <= 512 && method == "GET")
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "badge.local.invalid" && uri.port == -1 &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    val path = uri.rawPath.removePrefix("/")
    require(path.length in 1..240 && path.matches(Regex("[A-Za-z0-9_./-]+")) &&
        path.split('/').none { it == ".." || it == "." || it.isEmpty() })
    val permitted = if (path.startsWith("viewer/")) path.endsWith(".html") || path.endsWith(".js") ||
        path.endsWith(".mjs") || path == "viewer/font.ttf"
    else !path.contains('/') && (path == "model.json" || path == "cabinet-layout.json" || path == "clearsky-scene.json" || path == "clearsky-softboxes.hdr" || path.endsWith(".glb") || path.endsWith(".png") ||
        path.endsWith(".jpg") || path.endsWith(".webp"))
    require(permitted)
    path
}.getOrNull()

/** Idempotent lifecycle gate also used by asynchronous JS callbacks after page disposal. */
internal class BadgeViewLifetime {
    private val generation = AtomicLong(0)
    val hostGeneration: Long get() = generation.get()
    fun newHost() { pause(); generation.incrementAndGet() }
    fun isCurrentHost(value: Long): Boolean = isAlive && value == hostGeneration
    private val alive = AtomicBoolean(true)
    private val resumed = AtomicBoolean(false)
    val isAlive: Boolean get() = alive.get()
    val canRender: Boolean get() = alive.get() && resumed.get()
    fun resume(): Boolean = alive.get() && !resumed.getAndSet(true)
    fun pause(): Boolean = resumed.getAndSet(false)
    fun destroy(): Boolean { resumed.set(false); return alive.getAndSet(false) }
}

/** Foreground startup budget ends at a committed visual frame, not a JS ready message. */
internal class BadgeStartupDeadline(private val budgetMillis: Long = 20_000L) {
    private var remaining = budgetMillis
    private var started: Long? = null
    private var complete = false
    fun reset() { remaining = budgetMillis; started = null; complete = false }
    fun resume(now: Long): Long? {
        if (complete) return null
        if (started == null) started = now
        return (remaining - (now - started!!).coerceAtLeast(0)).coerceAtLeast(0)
    }
    fun pause(now: Long) {
        started?.let { remaining = (remaining - (now - it).coerceAtLeast(0)).coerceAtLeast(0) }
        started = null
    }
    fun committed() { complete = true; started = null }
}

internal fun badgeCriticalResource(url: String, method: String, mainFrame: Boolean, cabinet: Boolean): Boolean {
    if (mainFrame) return true
    // A model is one tile. Its rejected load must not hide every other owned model.
    if (badgeModelCacheHash(url, method) != null) return !cabinet
    val path = badgeAssetPath(url, method) ?: return false
    return path.endsWith(".mjs") || path.endsWith(".js") || !cabinet && path.endsWith(".glb") ||
        path in setOf("model.json", "cabinet-layout.json", "clearsky-softboxes.hdr", "viewer/font.ttf")
}

/** Downloaded models are served only from the native verified cache map, never arbitrary file paths. */
internal fun badgeModelCacheHash(url: String, method: String = "GET"): String? = runCatching {
    require(url.length<=200 && method=="GET");val uri=URI(url)
    require(uri.scheme=="https" && uri.host=="badge.local.invalid" && uri.port == -1 && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null)
    Regex("""/models/([a-f0-9]{64})\.glb""").matchEntire(uri.rawPath)?.groupValues?.get(1)
}.getOrNull()
