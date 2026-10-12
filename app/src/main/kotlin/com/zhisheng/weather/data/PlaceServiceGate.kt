package com.zhisheng.weather.data

import java.security.MessageDigest

/** Bounded process-only failure state, independent for search/reverse and each credential. */
internal object PlaceServiceGate {
    private data class Entry(val failure: PlaceFailure, val untilNanos: Long)
    private val failures = linkedMapOf<String, Entry>()
    private fun identity(credential: String, capability: String): String =
        capability + ":" + MessageDigest.getInstance("SHA-256").digest(credential.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Synchronized fun check(credential: String, capability: String) {
        val key = identity(credential, capability)
        val state = failures[key] ?: return
        if (System.nanoTime() < state.untilNanos) throw PlaceServiceException(state.failure)
        failures.remove(key)
    }

    @Synchronized fun record(credential: String, capability: String, failure: PlaceFailure, retryAfterMillis: Long? = null) {
        val duration = when (failure) {
            PlaceFailure.AUTH -> 24 * 3_600_000L
            PlaceFailure.QUOTA -> (retryAfterMillis ?: 300_000L).coerceIn(1_000L, 24 * 3_600_000L)
            else -> 30_000L
        }
        failures[identity(credential, capability)] = Entry(failure, System.nanoTime() + duration * 1_000_000)
        while (failures.size > 24) failures.remove(failures.keys.first())
    }

    @Synchronized fun clear(credential: String, capability: String) {
        failures.remove(identity(credential, capability))
    }
}

internal fun placeRetryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    if (value == null || value.length > 200) return null
    value.trim().toLongOrNull()?.let { return it.coerceIn(0, 86_400) * 1_000 }
    return runCatching {
        (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli() - nowMillis).coerceIn(0, 86_400_000)
    }.getOrNull()
}
