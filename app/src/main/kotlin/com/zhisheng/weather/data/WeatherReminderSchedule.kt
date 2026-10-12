package com.zhisheng.weather.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class DailyReminderCheck(val kind: String, val dueAt: Long, val key: String)

private const val DAILY_WINDOW_MS = 90 * 60_000L
private const val FINISHED_RETENTION_MS = 7 * 24 * 3_600_000L

internal fun dailyReminderCheckIdentity(check: DailyReminderCheck): String =
    "${check.key.length}:${check.key}|${check.dueAt}"

/** Scheduler completion is separate from a successful notification delivery receipt. */
internal fun finishedDailyReminderChecks(previous: Set<String>, completed: DailyReminderCheck?, now: Long): Set<String> {
    val retained = previous.filterTo(mutableSetOf()) { identity ->
        val dueAt = identity.substringAfterLast('|').toLongOrNull()
        dueAt != null && dueAt >= now - FINISHED_RETENTION_MS
    }
    // An early execution (e.g. after the wall clock moves backwards) must keep its
    // future appointment available. An exhausted/dismissed due check is terminal.
    if (completed != null && completed.dueAt <= now && completed.dueAt >= now - FINISHED_RETENTION_MS)
        retained += dailyReminderCheckIdentity(completed)
    return retained
}

private val refreshableReminderStates = setOf(ReminderCheckState.NO_DATA, ReminderCheckState.CONFIG_CHANGED,
    ReminderCheckState.STALE_DATA, ReminderCheckState.NO_FORECAST, ReminderCheckState.NO_TIMEZONE,
    ReminderCheckState.FAILED)

private fun needsReminderData(
    appointment: DailyReminderCheck?,
    settings: AutomationSettings,
    checks: List<ReminderCheck>,
    now: Long,
    receipts: Map<String, Long>,
): Boolean {
    val enabled = enabledReminderKinds(settings).toSet()
    if (appointment != null && (appointment.kind !in enabled || appointment.key in receipts ||
        now - appointment.dueAt !in 0 until DAILY_WINDOW_MS)) return false
    val relevant = checks.filter { it.kind in enabled && (appointment == null || it.kind == appointment.kind) }
    val sentKinds = relevant.filter { it.state == ReminderCheckState.SENT }.map { it.kind }.toSet()
    return relevant.any { it.kind !in sentKinds && it.state in refreshableReminderStates }
}

/** WorkManager counts the first execution as zero; at most three executions may check again. */
internal fun shouldRetryReminderCheck(
    appointment: DailyReminderCheck?,
    settings: AutomationSettings,
    checks: List<ReminderCheck>,
    now: Long,
    runAttemptCount: Int,
    receipts: Map<String, Long> = emptyMap(),
): Boolean = runAttemptCount in 0..1 && needsReminderData(appointment, settings, checks, now, receipts)

/** A concurrent city/source edit invalidates preparation; only fresh snapshots can apply work. */
internal suspend fun <T> applyLatestReminderSchedule(
    gate: ReminderDeliveryGate,
    readSettings: () -> AutomationSettings,
    prepare: suspend () -> T,
    apply: (T, AutomationSettings) -> Unit,
): Boolean {
    repeat(3) { attempt ->
        if (gate.applySnapshot(readSettings, prepare, apply)) return true
        if (attempt < 2) yield()
    }
    return false
}

/** The next local appointment for each enabled daily reminder, ordered by time. */
internal fun nextDailyReminderChecks(
    cityKey: String,
    utcOffsetSeconds: Int?,
    settings: AutomationSettings,
    receipts: Map<String, Long>,
    now: Long,
    completed: DailyReminderCheck? = null,
    finishedChecks: Set<String> = emptySet(),
): List<DailyReminderCheck> {
    val offset = utcOffsetSeconds?.takeIf { it in -64_800..64_800 } ?: return emptyList()
    val zone = ZoneOffset.ofTotalSeconds(offset)
    val localDay = Instant.ofEpochMilli(now).atOffset(zone).toLocalDate()
    val normalized = settings.normalized()
    fun next(kind: String, minute: Int): DailyReminderCheck =
        generateSequence(if ("$cityKey:$kind:$localDay" in receipts) localDay else localDay.minusDays(1)) { it.plusDays(1) }.map { date ->
            DailyReminderCheck(kind, date.atTime(minute / 60, minute % 60).atOffset(zone)
                .toInstant().toEpochMilli(), "$cityKey:$kind:$date")
        }.first { check ->
            val inTime = check.dueAt > now || now - check.dueAt in 0 until DAILY_WINDOW_MS
            val finished = completed != null && completed.key == check.key &&
                completed.dueAt == check.dueAt && check.dueAt <= now
            inTime && check.key !in receipts && !finished && dailyReminderCheckIdentity(check) !in finishedChecks
        }
    return buildList {
        if (normalized.morningEnabled) add(next("morning", normalized.morningMinute))
        if (normalized.eveningEnabled) add(next("evening", normalized.eveningMinute))
    }.sortedBy { it.dueAt }
}

/** Daily appointments are persisted separately from network-constrained widget refreshes. */
object WeatherReminderSchedule {
    private const val STORE = "weather_reminder_schedule"
    private const val TRACKED_NAMES = "scheduled_names"
    private const val FINISHED_CHECKS = "finished_daily_checks"
    private const val PREPARATION = "timezone_preparation"
    private const val NOW = "weather-reminder-check-now"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduleLock = Mutex()

    fun request(context: Context) = enqueueNow(context.applicationContext, ExistingWorkPolicy.REPLACE)

    fun schedule(context: Context, completed: DailyReminderCheck? = null) {
        val ctx = context.applicationContext
        scope.launch {
            try { scheduleLatest(ctx, completed, allowPreparation = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Log.w("WeatherReminders", "Scheduling deferred", failure) }
        }
    }

    private fun enqueueNow(context: Context, policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, policy,
            OneTimeWorkRequestBuilder<WeatherReminderWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES).build())
    }

    internal suspend fun afterCheck(context: Context, completed: DailyReminderCheck?, terminal: () -> Boolean = { false }) =
        scheduleLatest(context.applicationContext, completed, allowPreparation = false, terminal = terminal)

    private data class Snapshot(val cityKey: String?, val sourceKey: String, val modules: String,
        val utcOffsetSeconds: Int?)

    private suspend fun scheduleLatest(context: Context, completed: DailyReminderCheck?, allowPreparation: Boolean,
        terminal: () -> Boolean = { false }) =
        scheduleLock.withLock {
            if (completed != null) reminderDeliveryGate.updateSettings {
                // This record concerns only the completed identity, so it is safe
                // even while a new city/source is being persisted. Record before
                // suspend preparation so context invalidation cannot lose it.
                val preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val previous = preferences.getStringSet(FINISHED_CHECKS, emptySet()).orEmpty().toSet()
                val finished = finishedDailyReminderChecks(previous, completed.takeIf { terminal() }, now)
                preferences.edit().putStringSet(FINISHED_CHECKS, finished).apply()
            }
            val applied = applyLatestReminderSchedule(reminderDeliveryGate,
                { AutomationSettingsRepository.read(context) }, {
                    // All DataStore and compatibility reads may suspend. None of
                    // them can overlap an enqueue after this snapshot is validated.
                    val city = CityRepository.selectedCity.first()
                    val source = SettingsRepository.sourcePref.first()
                    val modules = SettingsRepository.moduleSources.first().encode()
                    val cached = city?.let { WeatherCache.load(context, it)?.data }
                        ?.takeIf { WeatherRepository.cacheCompatible(it, source) }
                    Snapshot(city?.locationKey, source.key, modules,
                        cached?.utcOffsetSeconds?.takeIf { it in -64_800..64_800 })
                }, { snapshot, settings ->
                    applySchedule(context, snapshot, settings, completed, allowPreparation)
                })
            if (!applied) Log.i("WeatherReminders", "Scheduling deferred while weather context changes")
        }

    /** Every side effect is synchronous and runs inside ReminderDeliveryGate's monitor. */
    private fun applySchedule(context: Context, snapshot: Snapshot, settings: AutomationSettings,
        completed: DailyReminderCheck?, allowPreparation: Boolean) {
        val now = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val finished = finishedDailyReminderChecks(preferences.getStringSet(FINISHED_CHECKS, emptySet()).orEmpty(), null, now)
        // Only the scheduler consults this set. Foreground, periodic and explicit
        // notification checks can still send when data or permission recovers.
        preferences.edit().putStringSet(FINISHED_CHECKS, finished).apply()
        val checks = snapshot.cityKey?.let { cityKey -> nextDailyReminderChecks(cityKey,
            snapshot.utcOffsetSeconds, settings, WeatherReminderNotification.receipts(context, now), now, completed, finished)
        }.orEmpty()
        val work = WorkManager.getInstance(context)
        val wanted = checks.map { workName(it) }.toSet()
        val ownName = completed?.let(::workName)
        val previous = preferences.getStringSet(TRACKED_NAMES, emptySet()).orEmpty().toSet()
        previous.filter { it !in wanted && it != ownName }.forEach { work.cancelUniqueWork(it) }
        // Keep the running/retrying appointment discoverable for later user changes;
        // completion itself must not cancel or replace its own WorkManager request.
        val edit = preferences.edit().putStringSet(TRACKED_NAMES, wanted + listOfNotNull(ownName))
        checks.forEach { check ->
            val name = workName(check)
            val policy = if (name == ownName && check.dueAt > now)
                ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
            work.enqueueUniqueWork(name, policy,
                OneTimeWorkRequestBuilder<WeatherReminderWorker>()
                    .setInputData(workDataOf("kind" to check.kind, "due_at" to check.dueAt, "key" to check.key))
                    .setInitialDelay((check.dueAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES).build())
        }
        if (snapshot.utcOffsetSeconds != null || snapshot.cityKey == null ||
            (!settings.morningEnabled && !settings.eveningEnabled)) {
            edit.remove(PREPARATION)
        } else if (allowPreparation) {
            val preparation = "${snapshot.cityKey}|${snapshot.sourceKey}|${settings.morningEnabled}:${settings.morningMinute}" +
                "|${settings.eveningEnabled}:${settings.eveningMinute}|${snapshot.modules}"
            if (preferences.getString(PREPARATION, null) != preparation) {
                edit.putString(PREPARATION, preparation)
                enqueueNow(context, ExistingWorkPolicy.KEEP)
            }
        }
        // Worker completion never creates another timezone preparation request.
        edit.apply()
    }

    private fun workName(check: DailyReminderCheck): String {
        val identity = dailyReminderCheckIdentity(check)
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "weather-reminder-${check.kind}-$hash"
    }
}

/** Runs local notification checks even when the network-constrained widget worker cannot run. */
class WeatherReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind")?.takeIf { it == "morning" || it == "evening" }
        val key = inputData.getString("key")
        val dueAt = inputData.getLong("due_at", 0)
        val appointment = if (kind != null && key != null && dueAt > 0) DailyReminderCheck(kind, dueAt, key) else null
        val owner = currentCoroutineContext()
        var terminal = false
        try {
            var result = WeatherReminderNotification.checkNow(applicationContext)
            val initialNow = System.currentTimeMillis()
            if (needsReminderData(appointment, AutomationSettingsRepository.read(applicationContext), result.checks,
                initialNow, WeatherReminderNotification.receipts(applicationContext, initialNow))) {
                result = WeatherReminderNotification.checkNow(applicationContext, forceRefresh = true)
            }
            val now = System.currentTimeMillis()
            val retry = shouldRetryReminderCheck(appointment, AutomationSettingsRepository.read(applicationContext),
                result.checks, now, runAttemptCount, WeatherReminderNotification.receipts(applicationContext, now))
            terminal = !retry
            return if (retry) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("WeatherReminders", "Reminder check deferred", failure)
            val now = System.currentTimeMillis()
            val settings = AutomationSettingsRepository.read(applicationContext)
            val failed = reminderChecksWithState(settings, now, ReminderCheckState.FAILED, "检查失败").checks
            val retry = shouldRetryReminderCheck(appointment, settings, failed, now, runAttemptCount,
                WeatherReminderNotification.receipts(applicationContext, now))
            terminal = !retry
            return if (retry) Result.retry() else Result.success()
        } finally {
            withContext(NonCancellable) {
                try { WeatherReminderSchedule.afterCheck(applicationContext, appointment) {
                    terminal && owner.isActive && !isStopped
                } }
                catch (failure: Exception) { Log.w("WeatherReminders", "Next reminder scheduling deferred", failure) }
            }
        }
    }

}
