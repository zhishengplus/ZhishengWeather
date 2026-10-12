package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import java.security.MessageDigest

/** Labels only, bounded and process-only; no history of a user's travels on disk. */
internal class PlaceResolutionCache(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val ttlMillis: Long = 300_000,
) {
    private data class Entry(val scope: String, val lat: Double, val lon: Double, val label: City, val time: Long)
    private val entries = ArrayDeque<Entry>()
    private fun hash(scope: String) = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Synchronized fun get(scope: String, lat: Double, lon: Double): City? {
        val now = clock()
        entries.removeAll { now < it.time || now - it.time >= ttlMillis }
        val identity = hash(scope)
        return entries.lastOrNull { it.scope == identity &&
            kotlin.math.abs(it.lat - lat) <= 0.00001 && kotlin.math.abs(it.lon - lon) <= 0.00001 }?.label
    }

    @Synchronized fun put(scope: String, lat: Double, lon: Double, label: City) {
        entries.addLast(Entry(hash(scope), lat, lon, label, clock()))
        while (entries.size > 32) entries.removeFirst()
    }
}

internal fun updateMeasuredLabel(saved: City, enriched: City): City? {
    if (saved.coordinateKind != CoordinateKind.DEVICE || saved.locationKey != enriched.locationKey ||
        saved.latitude != enriched.latitude || saved.longitude != enriched.longitude ||
        saved.locationSampleTimeMillis != enriched.locationSampleTimeMillis) return null
    return saved.copy(name = enriched.name, affiliation = enriched.affiliation,
        street = enriched.street, addressSource = enriched.addressSource,
        precisePermissionGranted = enriched.precisePermissionGranted)
}
