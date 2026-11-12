package com.zhisheng.weather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Local-only redraw so hourly slots and 明天/后天 rows advance without a network fetch.
 *
 * The hourly strip drops a slot 30 minutes after it starts, and the daily rows
 * roll over at midnight, so redraw shortly after every :00 and :30. The alarm is
 * a non-wakeup, inexact [AlarmManager.setAndAllowWhileIdle]: it never wakes the
 * phone and needs no exact-alarm permission; when the screen is off it is simply
 * delivered at the next wake, which is exactly when the widget becomes visible.
 */
object WidgetRedrawAlarm {
    const val ACTION_REDRAW = "com.zhisheng.weather.widget.REDRAW"
    private const val REQUEST_CODE = 0x5A57
    private const val SLACK_MS = 30_000L
    private const val SCREEN_ON_THROTTLE_MS = 3 * 60_000L

    @Volatile private var lastRenderAt = 0L
    @Volatile private var screenReceiverRegistered = false

    /** Next half-hour boundary (+30 s) strictly after [now], in [zone]. */
    internal fun nextTrigger(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val hour = local.truncatedTo(ChronoUnit.HOURS)
        val candidates = listOf(hour, hour.plusMinutes(30), hour.plusHours(1), hour.plusHours(1).plusMinutes(30))
            .map { it.toInstant().toEpochMilli() + SLACK_MS }
        return candidates.first { it > now }
    }

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(c, REQUEST_CODE,
        Intent(c, FreshWidgetProvider::class.java).setAction(ACTION_REDRAW),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context) {
        val ctx = c.applicationContext
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(ctx)
        if (WidgetRuntime.instances(ctx).isEmpty()) {
            alarms.cancel(intent)
            return
        }
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextTrigger(System.currentTimeMillis()), intent) }
            .onFailure { android.util.Log.w("DesktopWeather", "Redraw alarm unavailable", it) }
    }

    internal fun markRendered(now: Long = System.currentTimeMillis()) { lastRenderAt = now }

    /** While the process is alive, also redraw when the user turns on / unlocks the phone. */
    @Synchronized fun registerScreenReceiver(c: Context) {
        if (screenReceiverRegistered) return
        val ctx = c.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (System.currentTimeMillis() - lastRenderAt < SCREEN_ON_THROTTLE_MS) return
                if (WidgetRuntime.instances(context).isEmpty()) return
                val pending = goAsync()
                WidgetRuntime.scope.launch {
                    try { withTimeout(8_000) { WidgetRuntime.renderAll(context); schedule(context) } }
                    catch (e: Exception) { android.util.Log.w("DesktopWeather", "Screen-on redraw deferred", e) }
                    finally { pending.finish() }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(receiver, filter)
            screenReceiverRegistered = true
        }.onFailure { android.util.Log.w("DesktopWeather", "Screen receiver unavailable", it) }
    }
}
