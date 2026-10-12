package com.zhisheng.weather.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AutomationSettings(
    val foregroundMinutes: Int = 5,
    val backgroundEnabled: Boolean = false,
    val backgroundMinutes: Int = 30,
    val morningEnabled: Boolean = false,
    val morningMinute: Int = 7 * 60,
    val eveningEnabled: Boolean = false,
    val eveningMinute: Int = 19 * 60,
    val rainEnabled: Boolean = false,
) {
    val remindersEnabled get() = morningEnabled || eveningEnabled || rainEnabled
    val needsBackground get() = backgroundEnabled || remindersEnabled
    val checkMinutes get() = when {
        rainEnabled -> minOf(backgroundMinutes, 15)
        morningEnabled || eveningEnabled -> minOf(backgroundMinutes, 60)
        else -> backgroundMinutes
    }
    fun normalized() = copy(
        foregroundMinutes = foregroundMinutes.takeIf { it in setOf(5, 10, 15, 30) } ?: 5,
        backgroundMinutes = backgroundMinutes.takeIf { it in setOf(15, 30, 60, 120) } ?: 30,
        morningMinute = morningMinute.coerceIn(0, 1439),
        eveningMinute = eveningMinute.coerceIn(0, 1439),
    )
}

/** Independent store: widget settings and provider credentials retain their existing ownership. */
object AutomationSettingsRepository {
    private val mutable = MutableStateFlow(AutomationSettings())
    val settings: StateFlow<AutomationSettings> = mutable
    fun read(context: Context): AutomationSettings {
        val p = context.getSharedPreferences("weather_automation", Context.MODE_PRIVATE)
        return AutomationSettings(p.getInt("foreground", 5), p.getBoolean("background", false),
            p.getInt("interval", 30), p.getBoolean("morning", false), p.getInt("morning_time", 420),
            p.getBoolean("evening", false), p.getInt("evening_time", 1140), p.getBoolean("rain", false)).normalized()
    }
    fun init(context: Context) { mutable.value = read(context) }
    fun save(context: Context, settings: AutomationSettings) {
        val s = settings.normalized()
        reminderDeliveryGate.updateSettings {
            context.getSharedPreferences("weather_automation", Context.MODE_PRIVATE).edit()
                .putInt("foreground", s.foregroundMinutes).putBoolean("background", s.backgroundEnabled)
                .putInt("interval", s.backgroundMinutes).putBoolean("morning", s.morningEnabled)
                .putInt("morning_time", s.morningMinute).putBoolean("evening", s.eveningEnabled)
                .putInt("evening_time", s.eveningMinute).putBoolean("rain", s.rainEnabled).apply()
            mutable.value = s
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager?.activeNotifications?.filter { shouldCancelWeatherReminder(it.tag, s) }
                ?.forEach { manager.cancel(it.tag, it.id) }
        }
        com.zhisheng.weather.widget.WidgetRefresh.schedule(context.applicationContext)
        com.zhisheng.weather.widget.WidgetRefresh.request(context.applicationContext)
        WeatherReminderSchedule.request(context.applicationContext)
    }
}
