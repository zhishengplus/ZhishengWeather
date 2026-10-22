package com.zhisheng.weather.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive

/** Shared delivery boundary for settings changes and notification side effects. */
internal class ReminderDeliveryGate {
    private val guard = Any()
    private var contextGeneration = 0L
    private var pendingContextChanges = 0
    private var contextSettled = CompletableDeferred(Unit)
    fun contextVersion(): Long? = synchronized(guard) { contextGeneration.takeIf { pendingContextChanges == 0 } }

    /** A newly requested fix waits for settings being persisted by its own setup flow. */
    suspend fun awaitContextVersion(): Long {
        while (true) {
            val settled = synchronized(guard) {
                if (pendingContextChanges == 0) return contextGeneration
                contextSettled
            }
            settled.await()
        }
    }

    fun updateSettings(action: () -> Unit) = synchronized(guard) { action() }

    suspend fun <T> changeContext(action: suspend () -> T): T {
        synchronized(guard) {
            if (pendingContextChanges == 0) contextSettled = CompletableDeferred()
            contextGeneration++
            pendingContextChanges++
        }
        // DataStore suspends outside the monitor. Both the beginning and ending
        // invalidate preparation, including failed/cancelled or overlapping edits.
        try { return action() }
        finally {
            synchronized(guard) {
                contextGeneration++
                pendingContextChanges--
                if (pendingContextChanges == 0) contextSettled.complete(Unit)
            }
        }
    }

    fun <T> changeContextNow(action: () -> T): T = synchronized(guard) {
        contextGeneration++
        try { action() } finally { contextGeneration++ }
    }

    /** Applies a prepared context only while it still belongs to the latest persisted context. */
    suspend fun <T> applySnapshot(
        readSettings: () -> AutomationSettings,
        prepare: suspend () -> T,
        apply: (T, AutomationSettings) -> Unit,
    ): Boolean {
        val owner = currentCoroutineContext()
        val generation = synchronized(guard) {
            owner.ensureActive()
            if (pendingContextChanges != 0) return false
            contextGeneration
        }
        val snapshot = prepare()
        return synchronized(guard) {
            owner.ensureActive()
            if (pendingContextChanges != 0 || generation != contextGeneration) return false
            // Cancellation of scheduled work also needs this boundary when all
            // reminder switches are off. Settings are read after every suspension.
            apply(snapshot, readSettings())
            true
        }
    }

    suspend fun <T> deliver(
        readSettings: () -> AutomationSettings,
        prepare: suspend () -> T?,
        plan: (T, AutomationSettings) -> List<WeatherReminder>,
        send: (T, WeatherReminder) -> Unit,
        recordReceipt: (WeatherReminder) -> Unit,
    ) {
        val owner = currentCoroutineContext()
        val generation = synchronized(guard) {
            owner.ensureActive()
            if (pendingContextChanges != 0 || !readSettings().remindersEnabled) return
            contextGeneration
        }
        val prepared = prepare() ?: return
        // No suspension or provider work inside this boundary. A completed settings
        // update (including notification cancellation) precedes every later send.
        synchronized(guard) {
            owner.ensureActive()
            if (pendingContextChanges != 0 || generation != contextGeneration) return
            val settings = readSettings()
            if (!settings.remindersEnabled) return
            for (reminder in plan(prepared, settings)) {
                try {
                    send(prepared, reminder)
                } catch (_: SecurityException) {
                    break
                }
                recordReceipt(reminder)
            }
        }
    }
}

internal val reminderDeliveryGate = ReminderDeliveryGate()

private val dailyReminderTag = Regex(":(morning|evening):\\d{4}-\\d{2}-\\d{2}$")

internal fun shouldCancelWeatherReminder(tag: String?, settings: AutomationSettings): Boolean {
    if (tag?.startsWith("weather-reminder:") != true) return false
    if (!settings.remindersEnabled) return true
    // City keys include colons (geo:latitude:longitude). Types are suffixes,
    // so splitting from the start would mistake a city fragment for the type.
    val key = tag.removePrefix("weather-reminder:")
    if (key.endsWith(":rain")) return !settings.rainEnabled
    return when (dailyReminderTag.find(key)?.groupValues?.get(1)) {
        "morning" -> !settings.morningEnabled
        "evening" -> !settings.eveningEnabled
        else -> false
    }
}
