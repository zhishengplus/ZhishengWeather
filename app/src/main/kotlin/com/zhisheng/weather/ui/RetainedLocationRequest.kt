package com.zhisheng.weather.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Owns a first-use operation across Activity recreation; explicit departure cancels it. */
internal class RetainedLocationRequest(private val scope: CoroutineScope) {
    private var pending: Deferred<Boolean>? = null

    suspend fun run(block: suspend () -> Boolean): Boolean {
        val request = pending?.takeIf { it.isActive } ?: scope.async { block() }.also { pending = it }
        return request.await()
    }

    suspend fun awaitResult(): Boolean = pending?.await() ?: false

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
