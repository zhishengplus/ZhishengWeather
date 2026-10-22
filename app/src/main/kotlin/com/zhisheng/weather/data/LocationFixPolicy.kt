package com.zhisheng.weather.data

/** Provider measurements, kept separate from Android callbacks so acceptance can be tested. */
internal data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val wallTimeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val accuracyMeters: Float?,
    val provider: String? = null,
)

internal object LocationFixPolicy {
    private const val MAX_AGE_MILLIS = 2 * 60_000L
    // Android's coarse-location cache can be refreshed only once per ten minutes.
    // Keep its real age; this allowance must never enable street-level precision.
    private const val MAX_APPROXIMATE_AGE_MILLIS = 10 * 60_000L

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

    fun acceptsCallback(fix: LocationFix, nowMillis: Long, elapsedNanos: Long, approximate: Boolean = false): Boolean =
        fix.latitude.isFinite() && fix.latitude in -90.0..90.0 &&
            fix.longitude.isFinite() && fix.longitude in -180.0..180.0 &&
            (fix.accuracyMeters == null || (fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0f..20_000f)) &&
            ageMillis(fix, nowMillis, elapsedNanos)?.let {
                it <= if (approximate) MAX_APPROXIMATE_AGE_MILLIS else MAX_AGE_MILLIS
            } == true

    fun cachedFix(fixes: List<LocationFix>, nowMillis: Long, elapsedNanos: Long, approximate: Boolean = false): LocationFix? =
        fixes.filter { acceptsCallback(it, nowMillis, elapsedNanos, approximate) }
            .minWithOrNull(compareBy<LocationFix> { ageMillis(it, nowMillis, elapsedNanos) }
                .thenBy { it.accuracyMeters ?: Float.POSITIVE_INFINITY })

    fun isCurrent(fix: LocationFix, nowMillis: Long, elapsedNanos: Long): Boolean =
        ageMillis(fix, nowMillis, elapsedNanos)?.let { it <= MAX_AGE_MILLIS } == true

    fun canResolveStreet(fix: LocationFix): Boolean =
        fix.accuracyMeters?.let { it.isFinite() && it > 0f && it <= 100f } == true
}

internal fun measuredLocationCity(fix: LocationFix, preciseGranted: Boolean): com.zhisheng.weather.model.City =
    coordinateLocation(fix.latitude, fix.longitude).copy(
        coordinateKind = com.zhisheng.weather.model.CoordinateKind.DEVICE,
        locationAccuracyMeters = fix.accuracyMeters,
        locationSampleTimeMillis = fix.wallTimeMillis,
        locationProvider = fix.provider,
        precisePermissionGranted = preciseGranted,
    )

/** Label-only enrichment can never replace a measurement or invent a weather ID. */
internal fun applyResolvedLabel(
    measured: com.zhisheng.weather.model.City,
    label: com.zhisheng.weather.model.City,
    street: String?,
    source: String,
): com.zhisheng.weather.model.City = measured.copy(
    name = label.name, affiliation = label.affiliation, street = street, addressSource = source,
)

internal fun shouldApplyLocation(startedKey: String?, selectedKey: String?): Boolean =
    startedKey == selectedKey
