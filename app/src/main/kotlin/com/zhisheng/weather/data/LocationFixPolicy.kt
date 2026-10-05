package com.zhisheng.weather.data

/** Provider measurements, kept separate from Android callbacks so acceptance can be tested. */
internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val wallTimeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val accuracyMeters: Float?,
)

internal object LocationFixPolicy {
    private const val MAX_AGE_MILLIS = 2 * 60_000L

    fun ageMillis(fix: LocationFix, nowMillis: Long, elapsedNanos: Long): Long? {
        // Monotonic time is immune to clock corrections; a future value belongs to
        // another boot or a malformed provider and must not fall back to wall time.
        if (fix.elapsedRealtimeNanos > 0) {
            if (elapsedNanos < fix.elapsedRealtimeNanos) return null
            return (elapsedNanos - fix.elapsedRealtimeNanos) / 1_000_000
        }
        if (fix.wallTimeMillis <= 0 || nowMillis < fix.wallTimeMillis) return null
        return nowMillis - fix.wallTimeMillis
    }

    fun acceptsCallback(fix: LocationFix, nowMillis: Long, elapsedNanos: Long): Boolean =
        fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
            fix.longitude.isFinite() && fix.longitude in -180.0..180.0 &&
            (fix.accuracyMeters == null || (fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0f..20_000f)) &&
            ageMillis(fix, nowMillis, elapsedNanos)?.let { it <= MAX_AGE_MILLIS } == true

    fun cachedFix(fixes: List<LocationFix>, nowMillis: Long, elapsedNanos: Long): LocationFix? =
        fixes.filter { acceptsCallback(it, nowMillis, elapsedNanos) }
            .minWithOrNull(compareBy<LocationFix> { ageMillis(it, nowMillis, elapsedNanos) }
                .thenBy { it.accuracyMeters ?: Float.POSITIVE_INFINITY })

    fun canResolveStreet(fix: LocationFix): Boolean =
        fix.accuracyMeters?.let { it.isFinite() && it in 0f..500f } == true
}

internal fun shouldApplyLocation(startedKey: String?, selectedKey: String?): Boolean =
    startedKey == selectedKey
