package com.zhisheng.weather.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhisheng.weather.R
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.ui.Fmt
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetNotification {
    const val CHANNEL = "weather_widget_live"
    fun isEnabled(context: Context) = WidgetStore.notification(context).enabled
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@withContext
        WeatherAlertNotification.refresh(context)
        val setting = WidgetStore.notification(context)
        if (!setting.enabled) { manager.cancel(R.id.widget_notification_id); return@withContext }
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@withContext
        val snapshot = WidgetRuntime.snapshot(context, setting.config)
        val weather = snapshot?.weather
        val style = WidgetStyle.from(setting.style)
        val cfg = setting.config
        val today = weather?.todayDaily(System.currentTimeMillis())
        val current = weather?.current
        val temp = WidgetBinder.temperature(current?.temperature, cfg)
        val title = "${snapshot?.city?.name ?: "等待地点"}  $temp  ${current?.weatherText ?: current?.condition?.label ?: "暂无天气"}"
        val summary = "${WidgetBinder.temperature(today?.low, cfg)} / ${WidgetBinder.temperature(today?.high, cfg)}" +
            (if (cfg.showAir) "   空气 ${weather?.aqi?.level ?: weather?.aqi?.value ?: "—"}" else "")
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "常驻天气", NotificationManager.IMPORTANCE_LOW).apply { description = "当前天气与未来三日预报"; setShowBadge(false) })
        val refresh = PendingIntent.getBroadcast(context, -9180, Intent(context, FreshWidgetProvider::class.java).setAction(WidgetRefresh.ACTION_REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ph_cloud)
            .setContentTitle(title).setContentText(summary).setContentIntent(WidgetBinder.action(context, -1, style, "app", snapshot?.city, "notification"))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views(context, setting, snapshot, expanded = false))
            .setCustomBigContentView(views(context, setting, snapshot, expanded = true))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS).addAction(R.drawable.ph_arrow_clockwise, "刷新", refresh)
            .addAction(R.drawable.ph_gear, "设置", WidgetBinder.action(context, -1, style, "notification_settings"))
            .build()
        manager.notify(R.id.widget_notification_id, notification)
    }

    /** One binder for the collapsed row, expanded card and visual checks. */
    internal fun views(context: Context, setting: NotificationSettings, snapshot: WidgetSnapshot?, expanded: Boolean, now: Long = System.currentTimeMillis()): RemoteViews {
        val result = RemoteViews(context.packageName, if (expanded) R.layout.widget_notification else R.layout.widget_notification_compact)
        val cfg = setting.config
        val weather = snapshot?.weather
        val current = weather?.current
        val today = weather?.todayDaily(now)
        val range = "${WidgetBinder.temperature(today?.low, cfg)} / ${WidgetBinder.temperature(today?.high, cfg)}"
        val condition = current?.weatherText ?: current?.condition?.label ?: "暂无天气"
        val iconSet = WidgetIconSet.from(cfg.iconSet)
        val light = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        result.setTextViewText(R.id.notification_title, snapshot?.city?.name ?: "等待地点")
        result.setTextViewText(R.id.notification_temp, WidgetBinder.temperature(current?.temperature, cfg))
        result.setTextViewText(R.id.notification_condition, if (expanded) condition else "$condition · $range")
        WidgetIcons.bind(result, R.id.notification_icon, weather?.let { phaseAwareCondition(current?.condition, it, now) }, iconSet, light,
            ((if (expanded) 48f else 40f) * context.resources.displayMetrics.density).roundToInt())
        if (!expanded) return result
        result.setTextViewText(R.id.notification_today_range, range)
        result.setTextViewText(R.id.notification_update, weather?.fetchedAt?.let { (if (widgetStale(weather, now, cfg.intervalMinutes)) "上次 " else "更新 ") + Instant.ofEpochMilli(it).atZone(cityZone(weather.utcOffsetSeconds)).format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)) } ?: "待更新")
        val direction = current?.windDirectionDeg?.takeIf(Double::isFinite)?.let {
            val normalized = (it % 360 + 360) % 360
            listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")[(normalized / 45).roundToInt() % 8]
        }
        val wind = listOfNotNull(direction, Fmt.windForce(current?.windSpeed?.takeIf { it.isFinite() && it >= 0 })).joinToString(" ").ifBlank { "—" }
        val chance = weather?.hourly.orEmpty().filter { it.timeMillis >= now && it.timeMillis < now + 3 * 3_600_000L }.mapNotNull { it.precipProb?.takeIf { it in 0..100 } }.maxOrNull()
        val air = listOfNotNull(weather?.aqi?.level?.takeIf(String::isNotBlank), weather?.aqi?.value?.toString()).joinToString(" ").ifBlank { "—" }
        val metrics = listOf(
            "体感" to WidgetBinder.temperature(current?.feelsLike, cfg),
            "湿度" to (current?.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { "${it.roundToInt()}%" } ?: "—"),
            "风向风力" to wind,
            (if (cfg.showAir) "空气质量" else "气压") to (if (cfg.showAir) air else Fmt.pressure(current?.pressure, "hpa") ?: "—"),
            "3小时降水" to (chance?.let { "最高 $it%" } ?: "—"),
            "紫外线指数" to (current?.uvIndex?.takeIf { it >= 0 }?.toString() ?: "—"),
        )
        val metricLabels = intArrayOf(R.id.notification_metric_label_1, R.id.notification_metric_label_2, R.id.notification_metric_label_3, R.id.notification_metric_label_4, R.id.notification_metric_label_5, R.id.notification_metric_label_6)
        val metricValues = intArrayOf(R.id.notification_metric_1, R.id.notification_metric_2, R.id.notification_metric_3, R.id.notification_metric_4, R.id.notification_metric_5, R.id.notification_metric_6)
        metrics.forEachIndexed { index, (label, value) ->
            result.setTextViewText(metricLabels[index], label)
            result.setTextViewText(metricValues[index], value)
        }
        val days = weather?.currentAndFutureDaily(now).orEmpty().drop(1).take(3)
        val labels = intArrayOf(R.id.notification_day_1, R.id.notification_day_2, R.id.notification_day_3)
        val icons = intArrayOf(R.id.notification_icon_1, R.id.notification_icon_2, R.id.notification_icon_3)
        val ranges = intArrayOf(R.id.notification_range_1, R.id.notification_range_2, R.id.notification_range_3)
        labels.forEachIndexed { index, id ->
            val day = days.getOrNull(index)
            result.setTextViewText(id, day?.let { Instant.ofEpochMilli(it.dateMillis).atZone(cityZone(weather?.utcOffsetSeconds)).format(DateTimeFormatter.ofPattern("E", Locale.CHINA)) } ?: "—")
            result.setTextViewText(ranges[index], day?.let { "${WidgetBinder.temperature(it.low, cfg)} / ${WidgetBinder.temperature(it.high, cfg)}" } ?: "—")
            WidgetIcons.bind(result, icons[index], day?.condition, iconSet, light,
                (32f * context.resources.displayMetrics.density).roundToInt())
        }
        return result
    }
}
