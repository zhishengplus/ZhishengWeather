package com.zhisheng.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** A single provider request returns its cancellation action, even for inline callbacks. */
internal fun interface LocationFixRequest {
    fun start(onResult: (LocationFix?) -> Unit): () -> Unit
}

/** Races real providers without waiting for street precision or leaving requests running. */
internal suspend fun acquireLocationFix(
    requests: List<LocationFixRequest>,
    cached: List<LocationFix>,
    approximate: Boolean,
    preferStreet: Boolean,
    nowMillis: () -> Long,
    elapsedNanos: () -> Long,
    timeoutMillis: Long = 25_000L,
    cachedGraceMillis: Long = 1_500L,
    precisionGraceMillis: Long = 1_000L,
): LocationFix? {
    fun usable(fix: LocationFix) =
        LocationFixPolicy.acceptsCallback(fix, nowMillis(), elapsedNanos(), approximate)
    fun recent(fix: LocationFix) = LocationFixPolicy.isCurrent(fix, nowMillis(), elapsedNanos())
    fun street(fix: LocationFix) = recent(fix) && LocationFixPolicy.canResolveStreet(fix)
    fun age(fix: LocationFix) = LocationFixPolicy.ageMillis(fix, nowMillis(), elapsedNanos()) ?: Long.MAX_VALUE
    val fallback = LocationFixPolicy.cachedFix(cached, nowMillis(), elapsedNanos(), approximate)
    if (fallback != null && age(fallback) <= 10_000 && (!preferStreet || street(fallback))) return fallback
    if (requests.isEmpty()) return fallback

    data class Reply(val index: Int, val fix: LocationFix?)
    val replies = Channel<Reply>(Channel.UNLIMITED)
    val cancellations = mutableListOf<() -> Unit>()
    val completed = mutableSetOf<Int>()
    var best: LocationFix? = null
    val started = System.nanoTime()
    val hardDeadline = started + timeoutMillis * 1_000_000
    var usableDeadline = fallback?.let { started + cachedGraceMillis * 1_000_000 } ?: Long.MAX_VALUE
    fun result(): LocationFix? {
        val choices = listOfNotNull(best, fallback).filter(::usable)
        return (if (preferStreet) choices.firstOrNull(::street) else null)
            ?: choices.minWithOrNull(compareBy<LocationFix> { age(it) }
                .thenBy { it.accuracyMeters ?: Float.POSITIVE_INFINITY })
    }
    try {
        requests.forEachIndexed { index, request ->
            try {
                cancellations += request.start { replies.trySend(Reply(index, it)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                replies.trySend(Reply(index, null))
            }
        }
        while (completed.size < requests.size) {
            val remainingNanos = minOf(hardDeadline, usableDeadline) - System.nanoTime()
            if (remainingNanos <= 0) break
            val reply = withTimeoutOrNull((remainingNanos / 1_000_000).coerceAtLeast(1)) {
                replies.receive()
            } ?: break
            if (!completed.add(reply.index)) continue
            val fix = reply.fix?.takeIf(::usable) ?: continue
            val previous = best
            if (previous == null || (recent(fix) && !recent(previous)) ||
                (recent(fix) == recent(previous) &&
                    (fix.accuracyMeters ?: Float.POSITIVE_INFINITY) <
                    (previous.accuracyMeters ?: Float.POSITIVE_INFINITY))) best = fix
            if (!preferStreet || street(fix)) return result()
            // A city-level fix is already a success. Give GPS a short chance to
            // improve it, rather than withholding weather for the entire timeout.
            usableDeadline = minOf(usableDeadline, System.nanoTime() + precisionGraceMillis * 1_000_000)
        }
        return result()
    } finally {
        replies.close()
        cancellations.asReversed().forEach { cancel -> runCatching { cancel() } }
    }
}
