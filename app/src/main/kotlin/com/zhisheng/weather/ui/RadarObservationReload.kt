package com.zhisheng.weather.ui

import kotlinx.coroutines.CancellationException

internal fun radarObservationUsable(timeMillis: Long?, nowMillis: Long): Boolean =
    timeMillis != null && nowMillis - timeMillis in -5 * 60_000L..45 * 60_000L

internal sealed interface RadarReloadResult<out T> {
    data class Loaded<T>(val value: T, val stale: Boolean) : RadarReloadResult<T>
    data class Failed(val error: Exception) : RadarReloadResult<Nothing>
}

internal suspend fun <T> reloadRadarObservation(previous: T?, previousUsable: Boolean,
    onLoading: () -> Unit, load: suspend () -> T): RadarReloadResult<T> {
    if (previous == null || !previousUsable) onLoading()
    return try {
        RadarReloadResult.Loaded(load(), stale = false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (previous != null && previousUsable) RadarReloadResult.Loaded(previous, stale = true)
        else RadarReloadResult.Failed(error)
    }
}
