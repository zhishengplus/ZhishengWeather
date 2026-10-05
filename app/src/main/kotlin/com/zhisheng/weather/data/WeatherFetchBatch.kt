package com.zhisheng.weather.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Owns requests independently of the collecting coroutine: a blocking HTTP execute() must not
 * make coroutineScope wait beyond the collection deadline. close() cancels outstanding work;
 * blocking calls may finish at their transport timeout, but cannot publish into a later refresh.
 * The shared permit pool also bounds such lingering calls across successive refreshes.
 */
internal class WeatherFetchBatch<T : Any>(
    requests: List<suspend () -> T?>,
    owner: Job,
) : AutoCloseable {
    private val job = SupervisorJob()
    private val events = Channel<Pair<Int, T?>>(Channel.UNLIMITED)
    private val size = requests.size

    // Non-structural on purpose: a child job would make the owner wait for requests that ignore
    // cancellation, which is exactly what this class exists to avoid. invokeOnCompletion still
    // takes the requests down when an owner is cancelled before collect() ever gets to run.
    private val ownerLink = owner.invokeOnCompletion { job.cancel() }

    init {
        val scope = CoroutineScope(Dispatchers.IO + job)
        requests.forEachIndexed { index, request ->
            scope.launch {
                val value = try { permits.withPermit { request() } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
                events.trySend(index to value)
            }
        }
    }

    suspend fun collect(
        timeoutMs: Long,
        settleAfterUsableMs: Long? = null,
        isUsable: (T) -> Boolean = { true },
    ): List<T?> {
        val results = MutableList<T?>(size) { null }
        var remaining = size
        var deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0) * 1_000_000L
        var usableSeen = false
        try {
            while (remaining > 0) {
                coroutineContext.ensureActive()
                // Drain already completed work even when the caller gives no extra waiting time.
                val event = events.tryReceive().getOrNull() ?: run {
                    val waitNanos = deadline - System.nanoTime()
                    if (waitNanos <= 0) return results
                    withTimeoutOrNull((waitNanos / 1_000_000L).coerceAtLeast(1)) { events.receive() }
                        ?: return results
                }
                results[event.first] = event.second
                remaining--
                if (!usableSeen && event.second?.let(isUsable) == true) {
                    usableSeen = true
                    if (settleAfterUsableMs != null) {
                        deadline = minOf(deadline, System.nanoTime() + settleAfterUsableMs * 1_000_000L)
                    }
                }
            }
            return results
        } finally {
            close()
        }
    }

    override fun close() {
        ownerLink.dispose()
        job.cancel()
        events.cancel()
    }

    private companion object {
        val permits = Semaphore(16)
    }
}
