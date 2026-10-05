package com.zhisheng.weather.data

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Some pre-33 system geocoders ignore interruption. Bound the workers and do not
// queue calls behind a stuck service; callers can immediately use other sources.
private val geocoderWorkers = ThreadPoolExecutor(
    0, 2, 30L, TimeUnit.SECONDS, SynchronousQueue(),
    { task -> Thread(task, "weather-geocoder").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy(),
)

/** A timeout releases the caller even if the Android binder service remains stuck. */
internal suspend fun <T> geocoderCall(block: () -> T?): T? = suspendCancellableCoroutine { cont ->
    try {
        val future = geocoderWorkers.submit {
            val value = try { block() } catch (_: Exception) { null }
            if (cont.isActive) cont.resume(value)
        }
        cont.invokeOnCancellation { future.cancel(true) }
    } catch (_: RejectedExecutionException) {
        if (cont.isActive) cont.resume(null)
    }
}
