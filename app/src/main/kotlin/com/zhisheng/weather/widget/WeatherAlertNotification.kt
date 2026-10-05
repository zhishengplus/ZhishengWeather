package com.zhisheng.weather.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zhisheng.weather.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Local reminders from the selected weather feed, independent of the persistent weather card. */
internal object WeatherAlertNotification {
    const val CHANNEL = "weather_official_alerts"
    private const val TAG = "weather-alert:"
    private const val ID = 921
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "天气预警", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "所选天气地点的新预警与更新，后台检查可能受系统省电影响"
            })
    }

    suspend fun refresh(context: Context) = lock.withLock {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@withLock
        val settings = WidgetStore.notification(context)
        val displayed = manager.activeNotifications.filter { it.tag?.startsWith(TAG) == true }
        if (!settings.alertsEnabled) {
            displayed.forEach { manager.cancel(it.tag, ID) }
            return@withLock
        }
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return@withLock
        val snapshot = WidgetRuntime.snapshot(context, settings.config) ?: return@withLock
        val now = System.currentTimeMillis()
        val weather = snapshot.weather
        // A stale cache or failed fetch cannot reissue old warnings or prove their withdrawal.
        if (weather.error != null || weather.fetchedAt == null || now - weather.fetchedAt !in -300_000..3_600_000L) return@withLock
        val preferences = context.getSharedPreferences("weather_alert_receipts", Context.MODE_PRIVATE)
        val previous = runCatching { json.decodeFromString<Map<String, AlertReceipt>>(preferences.getString("receipts", "{}")!!) }.getOrDefault(emptyMap())
        val plan = planWeatherAlerts(snapshot.city.locationKey, weather.alerts, previous, now, weather.utcOffsetSeconds)
        displayed.filter { it.tag?.removePrefix(TAG) !in plan.activeKeys }.forEach { manager.cancel(it.tag, ID) }
        try {
            plan.deliveries.forEach { item ->
                val alert = item.alert
                manager.notify(TAG + item.key, ID, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ph_warning)
                    .setContentTitle("${snapshot.city.name} · ${alert.title}")
                    .setContentText(alert.detail?.takeIf(String::isNotBlank) ?: "点按查看天气预警详情")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(alert.detail ?: alert.title))
                    .setContentIntent(WidgetBinder.action(context, -1, WidgetStyle.from(settings.style), "app", snapshot.city, "alert-${item.key}"))
                    .setCategory(NotificationCompat.CATEGORY_EVENT).setAutoCancel(true)
                    .setTimeoutAfter((item.expiresAt - now).coerceAtLeast(1)).build())
            }
            preferences.edit().putString("receipts", json.encodeToString(plan.receipts)).apply()
        } catch (_: SecurityException) {
            // Permission can change between the capability check and notify; keep messages retryable.
        }
    }
}
