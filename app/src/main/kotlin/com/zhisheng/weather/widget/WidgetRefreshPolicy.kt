package com.zhisheng.weather.widget

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Fixed bindings remain valid independently of the app's saved-city list. */
internal fun widgetRefreshTargets(
    configs: List<WidgetConfig>,
    selected: City?,
    cities: List<City>,
    locatedKey: String?,
    automationInterval: Int?,
): List<Pair<City, Int>> = buildList {
    configs.forEach { config ->
        resolveWidgetCity(config, selected, cities, locatedKey)?.let { add(it to config.intervalMinutes) }
    }
    if (automationInterval != null && selected != null) add(selected to automationInterval)
}

internal fun isCurrentWidgetRefreshCity(city: City, targets: List<Pair<City, Int>>): Boolean =
    targets.any { weatherCityIdentity(it.first) == weatherCityIdentity(city) }

/** null means temporarily unstable; an empty list means a stable context with no bindings. */
internal suspend fun readStableWidgetRefreshTargets(
    contextVersion: () -> Long?,
    read: suspend () -> List<Pair<City, Int>>,
): List<Pair<City, Int>>? {
    repeat(3) {
        val generation = contextVersion()
        if (generation != null) {
            val targets = read()
            if (contextVersion() == generation) return targets
        }
        yield()
    }
    return null
}

internal fun shouldRetryWidgetRefresh(failed: Boolean, runAttemptCount: Int): Boolean =
    failed && runAttemptCount in 0..1

/** Revalidate after the suspending cache write; a changed/deleted binding must not receive old data. */
internal suspend fun publishWidgetRefresh(
    data: WeatherData,
    isCurrent: suspend () -> Boolean,
    save: suspend () -> Unit,
    publish: () -> Unit,
): Boolean {
    if (data.current == null || data.error != null || !isCurrent()) return false
    try {
        save()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        // The independently stored widget snapshot can still display this valid network result.
    }
    currentCoroutineContext().ensureActive()
    if (!isCurrent()) return false
    publish()
    return true
}
