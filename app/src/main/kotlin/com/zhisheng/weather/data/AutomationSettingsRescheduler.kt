package com.zhisheng.weather.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize side effects; the callback reads persisted settings only after acquiring its turn. */
internal class AutomationSettingsRescheduler(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun request(reschedule: suspend () -> Unit) = scope.launch {
        mutex.withLock { reschedule() }
    }
}
