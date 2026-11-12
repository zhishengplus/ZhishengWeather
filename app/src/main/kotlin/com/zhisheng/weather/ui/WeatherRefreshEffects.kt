package com.zhisheng.weather.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Optional disk/widget failures must not discard weather, but cancellation must stop publication. */
internal suspend fun completeWeatherRefresh(
    persist: suspend () -> Unit,
    renderWidgets: suspend () -> Unit,
    scheduleReminders: () -> Unit,
) {
    for (effect in listOf(persist, renderWidgets)) {
        currentCoroutineContext().ensureActive()
        try {
            effect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the successful weather result when an optional side effect fails.
        }
    }
    currentCoroutineContext().ensureActive()
    scheduleReminders()
}
