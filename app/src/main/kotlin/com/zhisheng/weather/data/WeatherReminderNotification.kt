package com.zhisheng.weather.data

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zhisheng.weather.MainActivity
import com.zhisheng.weather.R
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.widget.WidgetBinder
import com.zhisheng.weather.widget.WidgetRuntime
import com.zhisheng.weather.widget.WidgetStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WeatherReminderNotification {
    const val CHANNEL = "weather_daily_rain_reminders"
    private const val TEST_TAG = "weather-reminder-test"
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val changes = MutableStateFlow(0L)
    internal val revision: StateFlow<Long> = changes
    private data class Prepared(val city: City?, val weather: WeatherData?, val compatible: Boolean)

    private fun channel(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "早晚天气与短时降水", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "所选地点的早晚天气和短时降水提醒；显示与声音跟随系统通知设置"
        })
        return manager
    }

    internal fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun receipts(context: Context, now: Long): Map<String, Long> = validReminderReceipts(runCatching {
        json.decodeFromString<Map<String, Long>>(context.getSharedPreferences("weather_reminder_receipts", Context.MODE_PRIVATE)
            .getString("sent", "{}")!!)
    }.getOrDefault(emptyMap()), now)

    private suspend fun prepare(context: Context): Prepared {
        val city = CityRepository.selectedCity.first()
        val weather = city?.let { WeatherCache.load(context, it)?.data }
        val source = SettingsRepository.sourcePref.first()
        return Prepared(city, weather, weather?.let { WeatherRepository.cacheCompatible(it, source) } ?: true)
    }

    private fun evaluate(context: Context, prepared: Prepared, settings: AutomationSettings, now: Long): ReminderCheckResult = when {
        !allowed(context) -> reminderChecksWithState(settings, now, ReminderCheckState.NOTIFICATIONS_BLOCKED, "通知被系统关闭，请先开启天气通知")
        !prepared.compatible -> reminderChecksWithState(settings, now, ReminderCheckState.CONFIG_CHANGED, "天气来源已改变，更新预报后再检查")
        else -> evaluateWeatherReminders(prepared.city?.locationKey, prepared.weather, settings, receipts(context, now), now)
    }

    /** Read-only UI status. Test notifications and status reads never consume weather receipts. */
    internal suspend fun readStatus(context: Context): ReminderPanelStatus = withContext(Dispatchers.IO) {
        val prepared = prepare(context)
        val now = System.currentTimeMillis()
        val settings = AutomationSettingsRepository.read(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("weather_reminder_status", Context.MODE_PRIVATE)
        val sameContext = prefs.getString("city", null) == prepared.city?.locationKey &&
            prefs.getString("fingerprint", null) == prepared.weather?.configurationFingerprint &&
            prefs.getString("settings", null) == settings.toString()
        val checked = evaluate(context, prepared, settings, now)
        val failedKinds = prefs.getString("states", "").orEmpty().split(", ")
            .filter { it.endsWith("=FAILED") }.map { it.substringBefore('=') }.toSet()
        val status = checked.copy(checks = checked.checks.map { check ->
            if (sameContext && prefs.getLong("fetched_at", 0L) == (prepared.weather?.fetchedAt ?: 0L) &&
                check.kind in failedKinds && check.state == ReminderCheckState.READY)
                check.copy(state = ReminderCheckState.FAILED, detail = "上次发送未完成，点击立即检查重试", reminder = null)
            else check
        })
        ReminderPanelStatus(prepared.city?.displayName, prepared.weather?.utcOffsetSeconds,
            status,
            prefs.getLong("checked_at", 0L).takeIf { sameContext && it > 0L },
            quietNotifications = manager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ||
                (manager?.getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) < NotificationManager.IMPORTANCE_HIGH,
            backgroundRestricted = Build.VERSION.SDK_INT >= 28 &&
                context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true)
    }

    suspend fun refresh(context: Context) { checkNow(context) }

    /** Cached delivery runs before any optional network request, with the existing context/settings gate. */
    internal suspend fun checkNow(context: Context, forceRefresh: Boolean = false): ReminderCheckResult = withContext(Dispatchers.IO) {
        if (!AutomationSettingsRepository.read(context).remindersEnabled) return@withContext ReminderCheckResult(emptyList(), System.currentTimeMillis())
        if (forceRefresh) checkNow(context, forceRefresh = false)
        if (forceRefresh && AutomationSettingsRepository.read(context).remindersEnabled && allowed(context)) {
            val city = CityRepository.selectedCity.first()
            val source = SettingsRepository.sourcePref.first()
            if (city != null) {
                try {
                    val data = withTimeout(25_000) { WeatherRepository.fetchWeather(city, source) }
                    if (data.current != null && data.error == null && CityRepository.selectedCity.first()?.locationKey == city.locationKey &&
                        SettingsRepository.sourcePref.first() == source && WeatherRepository.cacheCompatible(data, source)) {
                        WeatherCache.save(context, city.locationKey, data)
                        WidgetRuntime.publishAndRender(context, city, data)
                    }
                } catch (cancelled: CancellationException) {
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                } catch (_: Exception) { /* Retain an independently valid cache on a failed fetch. */ }
            }
        }
        lock.withLock {
            val manager = channel(context)
            var result = ReminderCheckResult(emptyList(), System.currentTimeMillis())
            var preparedResult: Prepared? = null
            var planned = false
            var checkedSettings: AutomationSettings? = null
            val sent = mutableSetOf<String>()
            try {
                reminderDeliveryGate.deliver(
                    readSettings = { AutomationSettingsRepository.read(context) },
                    prepare = { prepare(context).also { preparedResult = it } },
                    plan = { prepared, settings ->
                        planned = true
                        checkedSettings = settings
                        result = evaluate(context, prepared, settings, System.currentTimeMillis())
                        result.checks.mapNotNull { it.reminder }
                    },
                    send = { prepared, reminder ->
                        val city = requireNotNull(prepared.city)
                        if (manager == null || !allowed(context)) throw SecurityException("Weather notifications unavailable")
                        manager.notify("weather-reminder:${reminder.key}", 924,
                            NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ph_cloud)
                                .setContentTitle("${city.displayName} · ${reminder.title}").setContentText(reminder.text)
                                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
                                .setContentIntent(WidgetBinder.action(context, -1, WidgetStyle.VISTA, "app", city, reminder.key))
                                .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_EVENT)
                                .setTimeoutAfter((reminder.expiresAt - result.checkedAt).coerceAtLeast(1)).build())
                    },
                    recordReceipt = { reminder ->
                        val next = receipts(context, result.checkedAt).toMutableMap()
                        next[reminder.key] = result.checkedAt
                        context.getSharedPreferences("weather_reminder_receipts", Context.MODE_PRIVATE).edit()
                            .putString("sent", json.encodeToString(next)).apply()
                        sent += reminder.key
                    })
                result = finishReminderDelivery(result, sent, allowed(context))
                if (!planned) result = reminderChecksWithState(AutomationSettingsRepository.read(context),
                    System.currentTimeMillis(), ReminderCheckState.CONFIG_CHANGED, "地点或设置正在更新，稍后重新检查")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                result = reminderChecksWithState(AutomationSettingsRepository.read(context), System.currentTimeMillis(),
                    ReminderCheckState.FAILED, "这次检查未完成，点击立即检查重试")
                android.util.Log.w("WeatherReminder", "Reminder check unavailable")
            }
            // A skipped/invalidate preparation must not claim a successful check for a new context.
            if (planned && result.checks.isNotEmpty()) {
                context.getSharedPreferences("weather_reminder_status", Context.MODE_PRIVATE).edit()
                    .putLong("checked_at", result.checkedAt).putString("city", preparedResult?.city?.locationKey)
                    .putString("fingerprint", preparedResult?.weather?.configurationFingerprint)
                    .putString("settings", checkedSettings.toString())
                    .putLong("fetched_at", preparedResult?.weather?.fetchedAt ?: 0L)
                    .putString("states", result.checks.joinToString { "${it.kind}=${it.state.name}" }).apply()
                changes.value += 1
                android.util.Log.i("WeatherReminder", result.checks.joinToString { "${it.kind}=${it.state.name}" })
            }
            result
        }
    }

    internal fun testNotification(context: Context): String {
        val manager = channel(context)
        if (manager == null || !allowed(context)) return "通知尚未允许，请先打开天气通知设置"
        return try {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val action = PendingIntent.getActivity(context, 925, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(TEST_TAG, 925, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ph_cloud)
                .setContentTitle("枳生天气 · 测试通知").setContentText("通知通道可用。早晚提醒在设定时刻后检查，降水提醒需要有效短时预报。")
                .setContentIntent(action).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_EVENT)
                .setTimeoutAfter(5 * 60_000L).build())
            "测试通知已提交，请查看通知栏；横幅和声音跟随系统设置"
        } catch (_: SecurityException) { "系统未允许这条通知，请打开天气通知设置" }
        catch (_: Exception) { "测试通知未发出，请重试或检查系统通知设置" }
    }
}
