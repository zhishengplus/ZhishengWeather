package com.zhisheng.weather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.zhisheng.weather.R
import com.zhisheng.weather.data.LunarCalendar
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Shared by the launcher and editor: the preview is the actual widget. */
object WidgetBinder {
    private const val MINIMUM_NUMBER_SIZE_DP = 9f
    fun bind(context: Context, widgetId: Int, instance: WidgetInstance, config: WidgetConfig,
             snapshot: WidgetSnapshot?, hostWidth: Int, hostHeight: Int, now: Long = System.currentTimeMillis(), interactive: Boolean = true,
             previewBackground: Int? = null, livingSkyEnabled: Boolean = false,
             ambienceLevel: com.zhisheng.weather.data.AmbienceLevel = com.zhisheng.weather.data.AmbienceLevel.SUBTLE): RemoteViews {
        val basePalette = widgetPalette(context, instance.style, config, previewBackground)
        val sky = widgetSky(instance.style, snapshot, basePalette.isLight, livingSkyEnabled, ambienceLevel, now)
        val p = if (sky == null) basePalette else widgetPalette(context, instance.style, config, previewBackground, sky.contrastColors)
        val legible = config.protectText && config.opacity < 70
        // Two grid rows can be taller than two columns. NOW is a square card:
        // retain the launcher's slot, but center the visible card inside it.
        // Measure every text row against that card, not the larger host slot.
        val squareSide = minOf(hostWidth, hostHeight)
        val width = if (instance.kind == WidgetKind.NOW) squareSide else hostWidth
        val height = if (instance.kind == WidgetKind.NOW) squareSide else hostHeight
        val cardHeight = height
        val nominalNowInset = when {
            instance.kind != WidgetKind.NOW -> 0
            minOf(width, cardHeight) >= 160 -> 16
            minOf(width, cardHeight) >= 148 -> 12
            else -> 8
        }
        val nowMinimumContent = if (minOf(width, height) < 140) 89 else 122
        val nowInset = if (instance.kind == WidgetKind.NOW) {
            minOf(nominalNowInset, ((height - nowMinimumContent) / 2).coerceAtLeast(2))
        } else 0
        val compact = when (instance.kind) {
            WidgetKind.NOW -> minOf(width, cardHeight) < 140
            WidgetKind.BAND -> height < 160
            WidgetKind.WEEK -> height < 184
            WidgetKind.PULSE -> height < 72
            WidgetKind.SCENE -> height < 440
        }
        // A typical four-row phone slot is about 340–430dp tall. It has room
        // for readable type, but not the extra hourly section of the tall layout.
        val roomyScene = instance.kind == WidgetKind.SCENE && compact && width >= 300 && height >= 340
        val weatherStrip = instance.kind == WidgetKind.BAND || instance.kind == WidgetKind.WEEK
        // Keep text away from the rounded corners even when a two-row slot uses
        // the compact layout. Reserve the header and forecast before adding
        // vertical breathing room, so shorter launcher slots still fit.
        val stripHorizontalInset = when {
            width >= 300 -> 24
            width >= 240 -> 18
            else -> 12
        }
        val stripMinimumContent = when {
            instance.kind == WidgetKind.BAND -> if (compact) 94 else 126
            instance.style == WidgetStyle.CLASSIC -> if (compact) 40 else 46
            compact -> 62 + if (height < 140) 30 else if (height >= 160 && android.os.Build.VERSION.SDK_INT >= 31) 66 else 54
            else -> 152
        }
        val stripVerticalInset = minOf(if (instance.kind == WidgetKind.BAND) 20 else 16,
            ((height - stripMinimumContent) / 2).coerceAtLeast(2))
        val pulseInset = if (instance.kind == WidgetKind.PULSE && compact) ((height - 40) / 2).coerceIn(2, 8) else 0
        val name = "widget_${instance.style.key}_${instance.kind.name.lowercase(Locale.ROOT)}" +
            (if (roomyScene) "_roomy" else if (compact) "_compact" else "") + if (legible) "_legible_${if (ColorUtils.calculateLuminance(p.ink) > .5) "light" else "dark"}" else ""
        val v = RemoteViews(context.packageName, context.resources.getIdentifier(name, "layout", context.packageName).takeIf { it != 0 } ?: layoutFor(instance.style, instance.kind))
        if (instance.kind == WidgetKind.NOW) {
            // Root padding constrains both background and content on Android 8+
            // without relying on the layout-size setters added in Android 12.
            val density = context.resources.displayMetrics.density
            val horizontalPx = ((hostWidth - width) * density / 2f).roundToInt()
            val verticalPx = ((hostHeight - height) * density / 2f).roundToInt()
            v.setViewPadding(R.id.widget_root, horizontalPx, verticalPx, horizontalPx, verticalPx)
        }
        val weather = snapshot?.weather
        val current = weather?.current
        val kind = instance.kind
        val style = instance.style
        val zone = cityZone(weather?.utcOffsetSeconds)
        val today = weather?.todayDaily(now)
        listOf(R.id.widget_time, R.id.widget_temp, R.id.widget_condition, R.id.widget_city,
            R.id.widget_metric_1, R.id.widget_metric_2, R.id.widget_metric_3,
            R.id.widget_metric_4, R.id.widget_metric_5, R.id.widget_metric_6).forEach { v.setTextColor(it, p.ink) }
        listOf(R.id.widget_date, R.id.widget_range, R.id.widget_secondary, R.id.widget_air,
            R.id.widget_lunar, R.id.widget_footer, R.id.widget_metric_label_1, R.id.widget_metric_label_2, R.id.widget_metric_label_3,
            R.id.widget_metric_label_4, R.id.widget_metric_label_5, R.id.widget_metric_label_6).forEach { v.setTextColor(it, p.muted) }
        if (kind == WidgetKind.SCENE) {
            listOf(R.id.widget_scene_hourly_title, R.id.widget_scene_day_title).forEach { v.setTextColor(it, p.muted) }
            v.setTextColor(R.id.widget_scene_briefing_text, p.ink)
        }
        v.setTextColor(R.id.widget_badge, p.accent)
        listOf(R.id.widget_edit, R.id.widget_pin).forEach { v.setInt(it, "setColorFilter", p.muted) }
        // SCENE's XML weights distribute the true remaining height on every API,
        // including when hourly data is absent or system font scale changes.
        if (kind == WidgetKind.NOW || (kind == WidgetKind.PULSE && compact)) {
            // RemoteViews.setViewPadding takes physical pixels, unlike XML's dp.
            val insetPx = ((if (kind == WidgetKind.NOW) nowInset else pulseInset) * context.resources.displayMetrics.density).roundToInt()
            v.setViewPadding(R.id.widget_content, insetPx, insetPx, insetPx, insetPx)
        }
        if (weatherStrip) {
            val density = context.resources.displayMetrics.density
            val horizontalPx = (stripHorizontalInset * density).roundToInt()
            val verticalPx = (stripVerticalInset * density).roundToInt()
            v.setViewPadding(R.id.widget_content, horizontalPx, verticalPx, horizontalPx, verticalPx)
        }
        val surfaceRes = when (style) {
            WidgetStyle.VISTA -> if (p.isLight) R.drawable.widget_vista_surface else R.drawable.widget_vista_surface_dark
            WidgetStyle.CLASSIC -> if (p.isLight) R.drawable.widget_classic_surface_light else R.drawable.widget_classic_surface
        }
        // GradientDrawable draws at the host's density and bounds. A dp-sized
        // bitmap was upscaled on high-density phones, blurring the card edges.
        if (sky != null && config.opacity > 0) v.setImageViewBitmap(R.id.widget_surface, WidgetSkyBitmap.of(sky))
        else v.setImageViewResource(R.id.widget_surface, surfaceRes)
        v.setInt(R.id.widget_surface, "setImageAlpha", config.opacity.coerceIn(0, 100) * 255 / 100)
        if (android.os.Build.VERSION.SDK_INT >= 31 && kind == WidgetKind.WEEK && style == WidgetStyle.VISTA && compact && height >= 160) {
            // A normal two-row launcher slot has room for larger artwork. Keep
            // the smallest supported slot at the original compact sizes.
            v.setViewLayoutWidth(R.id.widget_icon, 46f, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(R.id.widget_icon, 46f, TypedValue.COMPLEX_UNIT_DIP)
        }
        val timeFormat = if (config.clock24) "HH:mm" else "h:mm"
        v.setCharSequence(R.id.widget_time, "setFormat12Hour", timeFormat)
        v.setCharSequence(R.id.widget_time, "setFormat24Hour", timeFormat)
        val clockSize = when (kind) { WidgetKind.PULSE -> 34f; WidgetKind.WEEK -> if (compact) 32f else 42f; WidgetKind.SCENE -> if (roomyScene) 40f else if (compact) 32f else 39f; else -> 46f }
        val tempSize = when (kind) { WidgetKind.PULSE -> 28f; WidgetKind.WEEK -> 28f; WidgetKind.NOW -> 48f; WidgetKind.BAND -> 32f; WidgetKind.SCENE -> if (roomyScene) 40f else 34f }
        // A home-screen widget has very little horizontal room. Use the district/city
        // name as the visible label and keep the full street address for accessibility.
        put(v, R.id.widget_city, cityLabel(snapshot?.city) ?: if (config.locationMode == "located") "等待定位" else "选择地点")
        put(v, R.id.widget_temp, temperature(current?.temperature, config))
        // Measure the actual number before assigning a font size: -18° and 108°
        // must remain whole even when the launcher only gives a narrow two-cell slot.
        val padding = if (weatherStrip) stripHorizontalInset * 2 else if (kind == WidgetKind.NOW) nowInset * 2 else if (compact && kind == WidgetKind.PULSE) pulseInset * 2 else if (roomyScene) 28 else if (compact) 16 else if (kind == WidgetKind.PULSE) 20 else if (style == WidgetStyle.CLASSIC && kind != WidgetKind.SCENE) 28 else 32
        val verticalPadding = if (weatherStrip) stripVerticalInset * 2 else padding
        val tempWidth = when (kind) {
            WidgetKind.NOW -> (width - padding - if (compact) 34 else 52).toFloat()
            WidgetKind.PULSE -> ((width - 32) / 2 - 49).toFloat()
            WidgetKind.WEEK -> (width - padding) / 2.55f - if (compact) 40 else 52
            WidgetKind.BAND -> if (compact) (width - padding) * .36f else 90f
            WidgetKind.SCENE -> if (roomyScene) (width - padding) / 2.05f - 54f else (width - padding) / 2.42f - if (compact) 46 else 62
        }.toFloat()
        val tempHeight = when {
            roomyScene -> 48f
            kind == WidgetKind.NOW -> if (compact) 38f else 50f
            compact && (kind == WidgetKind.PULSE || kind == WidgetKind.BAND) -> 23f
            kind == WidgetKind.PULSE -> 30f
            compact -> 30f
            else -> 42f
        }
        fitText(context, v, R.id.widget_temp, temperature(current?.temperature, config), tempSize, tempWidth, tempHeight, config.textScale)
        fitText(context, v, R.id.widget_time, if (config.clock24) "23:59" else "12:59", clockSize,
            (width - padding) * if (kind == WidgetKind.PULSE) .51f else .55f,
            if (roomyScene) 48f else if (compact) (if (kind == WidgetKind.PULSE) 23f else 28f) else when (kind) { WidgetKind.PULSE -> 35f; WidgetKind.WEEK -> 46f; else -> 54f }, config.textScale)
        put(v, R.id.widget_condition, current?.weatherText?.takeIf(String::isNotBlank) ?: current?.condition?.label ?: "暂无天气")
        put(v, R.id.widget_range, "${temperature(today?.low, config)} / ${temperature(today?.high, config)}")
        // NOW shows air quality in the lower reading; the legacy air TextView
        // remains hidden solely to keep old RemoteViews resource IDs stable.
        put(v, R.id.widget_air, "")
        // All widget layouts keep this 1sp legacy TextView only for RemoteViews ID
        // compatibility. Showing it draws a tiny line outside the visible card.
        put(v, R.id.widget_secondary, "")
        if (kind == WidgetKind.SCENE) {
            val briefing = weather?.let { Nowcast.briefing(it, config.tempUnit, now) }
            val summary = briefing?.text?.takeIf(String::isNotBlank)
                ?: weather?.forecastSummary?.takeIf(String::isNotBlank)
                ?: if (current != null) "留意接下来几小时的天气变化" else "打开应用，获取此地天气"
            // Keep both clauses; TextView wraps into its reserved two-line area.
            // An exceptionally long provider summary may ellipsize, while its
            // complete wording remains available to accessibility services.
            put(v, R.id.widget_scene_briefing_text, summary)
            v.setContentDescription(R.id.widget_scene_briefing_text, summary)
            // Target grid cells can become shorter than minHeight in landscape.
            // Drop secondary sections instead of cropping the current weather.
            v.setViewVisibility(R.id.widget_scene_briefing, if (compact && height < 280) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.widget_metrics, if (compact && height < 250) View.GONE else View.VISIBLE)
            val dayVisibility = if (compact && height < 180) View.GONE else View.VISIBLE
            v.setViewVisibility(R.id.widget_scene_day_title, dayVisibility)
            v.setViewVisibility(R.id.widget_forecast, dayVisibility)
            listOf(R.id.widget_scene_hourly_title, R.id.widget_scene_day_title).forEach {
                fitText(context, v, it, "未来天气趋势", if (roomyScene) 11f else 10f, (width - padding).toFloat(), if (roomyScene) 14f else 12f)
            }
            // Use a short measuring sample to cap line height without shrinking a
            // whole paragraph to one line; the native TextView still wraps/ellipsizes.
            fitText(context, v, R.id.widget_scene_briefing_text, "留意接下来的天气", if (roomyScene) 12f else if (compact) 11f else 12f,
                (width - padding).toFloat(), if (compact && !roomyScene) 17f else 18f)
            if (config.showWeatherGirl && briefing != null) {
                val res = when (briefing.emote) {
                    BriefingEmote.SUNNY -> R.drawable.weather_girl_emote_sunny
                    BriefingEmote.CLOUDY -> R.drawable.weather_girl_emote_cloudy
                    BriefingEmote.RAIN -> R.drawable.weather_girl_emote_rain
                    BriefingEmote.HOT -> R.drawable.weather_girl_emote_hot
                    BriefingEmote.COLD -> R.drawable.weather_girl_emote_cold
                    BriefingEmote.WIND -> R.drawable.weather_girl_emote_wind
                    BriefingEmote.NIGHT -> R.drawable.weather_girl_emote_night
                    BriefingEmote.ALERT -> R.drawable.weather_girl_emote_alert
                }
                v.setImageViewResource(R.id.widget_scene_girl, res)
                v.setViewVisibility(R.id.widget_scene_girl, View.VISIBLE)
            } else v.setViewVisibility(R.id.widget_scene_girl, View.GONE)
        }
        put(v, R.id.widget_badge, if (widgetStale(weather, now, config.intervalMinutes)) "待更新" else "")
        put(v, R.id.widget_footer, weather?.fetchedAt?.let { (if (widgetStale(weather, now, config.intervalMinutes)) "上次更新 " else "更新于 ") + time(it, zone) } ?: "等待天气数据")
        put(v, R.id.widget_lunar, if (config.showLunar) runCatching { LunarCalendar.dayLabel(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()) }.getOrNull().orEmpty() else "")
        run {
            val cityWidth = when (kind) {
                WidgetKind.PULSE -> (width - padding - 12) / 2.34f - if (compact) 39 else 49
                WidgetKind.WEEK -> if (style == WidgetStyle.VISTA) (width - padding) / 2.55f - (if (compact) 40 else 52) else (width - padding) * .6f
                WidgetKind.NOW -> (width - padding - if (compact) 0 else 64).toFloat()
                WidgetKind.BAND -> (width - padding) * .36f
                else -> (width - padding).toFloat()
            }
            val detailHeight = 14f
            fitText(context, v, R.id.widget_city, cityLabel(snapshot?.city) ?: "等待定位", if (roomyScene) 13f else 12f, cityWidth, if (roomyScene) 16f else detailHeight)
            fitText(context, v, R.id.widget_condition, current?.weatherText ?: current?.condition?.label ?: "暂无天气", 12f,
                if (kind == WidgetKind.BAND) (width - padding) * .36f else if (kind == WidgetKind.NOW && !compact) (width - padding) * .42f else (width - padding).toFloat(), detailHeight)
            fitText(context, v, R.id.widget_range, "${temperature(today?.low, config)} / ${temperature(today?.high, config)}", 12f,
                if (kind == WidgetKind.NOW) (width - padding) * (if (compact) 1f else .58f) else (width - padding) * (if (kind == WidgetKind.BAND) .36f else .45f), detailHeight, config.textScale)
            fitText(context, v, R.id.widget_date, "12月30日 周三", if (roomyScene) 13f else 12f, (width - padding) * (if (roomyScene) .51f else .55f), if (roomyScene) 17f else if (compact) 15f else 16f)
            fitText(context, v, R.id.widget_lunar, "八月二十一", 10f, (width - padding) * .55f, 12f)
            val updateText = weather?.fetchedAt?.let { (if (widgetStale(weather, now, config.intervalMinutes)) "上次 " else "更新 ") + time(it, zone) } ?: "待更新"
            put(v, R.id.widget_footer, updateText)
            fitText(context, v, R.id.widget_footer, updateText, if (kind == WidgetKind.NOW || roomyScene) 10f else 9f,
                if (kind == WidgetKind.WEEK) (width - padding) / 2.55f else if (kind == WidgetKind.NOW) 59f else (width - padding).toFloat(), 12f)
            if (compact && kind == WidgetKind.BAND) v.setViewVisibility(R.id.widget_footer, View.GONE)
            fitText(context, v, R.id.widget_air, "空气 ${weather?.aqi?.level ?: weather?.aqi?.value ?: "—"}", 11f, (width - padding) * .5f, 16f)
        }
        icon(context, v, R.id.widget_icon, WidgetIconSet.from(config.iconSet), weather?.let { phaseAwareCondition(current?.condition, it, now) }, p,
            ambient = sky?.palette)
        val labels = intArrayOf(R.id.widget_metric_label_1, R.id.widget_metric_label_2, R.id.widget_metric_label_3,
            R.id.widget_metric_label_4, R.id.widget_metric_label_5, R.id.widget_metric_label_6)
        val sceneMetrics = config.metrics.take(6)
        intArrayOf(R.id.widget_metric_1, R.id.widget_metric_2, R.id.widget_metric_3,
            R.id.widget_metric_4, R.id.widget_metric_5, R.id.widget_metric_6).forEachIndexed { index, id ->
            // The 2×2 card uses its lower area for two useful readings. Its air
            // toggle controls the second reading without adding another cramped row.
            val metric = if (kind == WidgetKind.NOW) when (index) {
                0 -> "feels"
                1 -> if (config.showAir) "air" else "humidity"
                else -> null
            } else if (kind == WidgetKind.SCENE) sceneMetrics.getOrNull(index) else config.metrics.getOrNull(index)
            put(v, labels[index], metricLabels[metric].orEmpty())
            val metricText = when (metric) {
                "feels" -> temperature(current?.feelsLike, config)
                "humidity" -> current?.humidity?.let { "${it.roundToInt()}%" } ?: "—"
                "wind" -> current?.windSpeed?.let(Fmt::windForce) ?: "—"
                "air" -> weather?.aqi?.let { air ->
                    val number = air.value?.toString()
                    // Long status words make a two-cell card illegible. The full
                    // status remains in the app and in this view's accessibility text.
                    val shortLevel = air.level?.takeIf { it.length <= 2 || number == null }
                    listOfNotNull(shortLevel, number).joinToString(" ").ifBlank { "—" }
                } ?: "—"
                "pressure" -> current?.pressure?.let { "${it.roundToInt()} hPa" } ?: "—"
                "uv" -> current?.uvIndex?.toString() ?: "—"
                "rain" -> weather?.hourly?.firstOrNull { it.timeMillis >= now - 30 * 60_000L }?.precipProb
                    ?.takeIf { it in 0..100 }?.let { "$it%" }
                    ?: today?.precipProbability?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                else -> ""
            }
            put(v, id, metricText)
            if (kind == WidgetKind.NOW && index == 1 && config.showAir) {
                v.setContentDescription(id, weather?.aqi?.let { "空气${it.level ?: "未知"}，AQI ${it.value ?: "未知"}" } ?: "空气质量暂无数据")
            }
            run {
                val metricWidth = if (kind == WidgetKind.NOW) (width - padding) / 2f - 26f else (width - padding) / 3f
                fitText(context, v, labels[index], metricLabels[metric].orEmpty(), 10f, metricWidth, 14f)
                val metricSize = if (kind == WidgetKind.SCENE) (if (roomyScene) 14f else 11f) else if (kind == WidgetKind.NOW) 13f else 14f
                fitText(context, v, id, metricText, metricSize, metricWidth,
                    if (kind == WidgetKind.SCENE) (if (roomyScene) 18f else 16f) else if (kind == WidgetKind.NOW) 16f else 19f, config.textScale)
            }
        }
        if (kind in listOf(WidgetKind.BAND, WidgetKind.WEEK, WidgetKind.SCENE)) {
            v.removeAllViews(R.id.widget_forecast)
            val hours = weather?.hourly.orEmpty().filter { it.timeMillis >= now - 30 * 60_000L }.take(6)
            val availableDays = weather?.currentAndFutureDaily(now).orEmpty().take(5)
            val maxDays = when {
                kind == WidgetKind.SCENE && width < 300 -> 4
                kind == WidgetKind.WEEK && style == WidgetStyle.CLASSIC ->
                    ((height - verticalPadding - if (compact) 20 else 26) / 20).coerceIn(1, 5)
                kind == WidgetKind.WEEK -> {
                    // Five wide Fahrenheit ranges can be unreadable in a narrow
                    // launcher slot. Reserve readable text plus each cell's gap,
                    // then show as many complete days as the slot accommodates.
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        textSize = MINIMUM_NUMBER_SIZE_DP * context.resources.displayMetrics.density
                    }
                    val widest = availableDays.maxOfOrNull { day ->
                        paint.measureText("${temperature(day.low, config)}/${temperature(day.high, config)}") /
                            context.resources.displayMetrics.density
                    } ?: 24f
                    ((width - padding) / (widest.coerceAtLeast(24f) + 10f)).toInt().coerceIn(1, 5)
                }
                else -> 5
            }
            val days = availableDays.take(maxDays)
            val isHours = kind == WidgetKind.BAND
            val isRows = style == WidgetStyle.CLASSIC && kind == WidgetKind.WEEK
            if (kind == WidgetKind.SCENE && !compact) {
                v.removeAllViews(R.id.widget_hourly_forecast)
                val sceneHours = hours.take(5)
                val hourlyVisibility = if (sceneHours.isEmpty()) View.GONE else View.VISIBLE
                v.setViewVisibility(R.id.widget_scene_hourly_title, hourlyVisibility)
                v.setViewVisibility(R.id.widget_hourly_forecast, hourlyVisibility)
                sceneHours.forEach { h ->
                    val cell = RemoteViews(context.packageName, R.layout.widget_forecast_column)
                    put(cell, R.id.widget_cell_label, time(h.timeMillis, zone))
                    put(cell, R.id.widget_cell_temp, temperature(h.temperature, config))
                    put(cell, R.id.widget_cell_extra, h.precipProb?.takeIf { it in 0..100 }?.let { "$it%" }.orEmpty())
                    val cellWidth = (width - padding).toFloat() / sceneHours.size.coerceAtLeast(1)
                    // These four rows share a 70dp minimum, including a 30dp icon
                    // and three 1dp margins. Bound every text row at large font scale.
                    fitText(context, cell, R.id.widget_cell_label, time(h.timeMillis, zone), 10f, cellWidth - 2, 12f, config.textScale)
                    fitText(context, cell, R.id.widget_cell_temp, temperature(h.temperature, config), 11f, cellWidth - 8, 13f, config.textScale)
                    fitText(context, cell, R.id.widget_cell_extra, "100%", 10f, cellWidth - 2, 12f, config.textScale)
                    cell.setTextColor(R.id.widget_cell_label, p.muted)
                    cell.setTextColor(R.id.widget_cell_temp, p.ink)
                    cell.setTextColor(R.id.widget_cell_extra, p.accent)
                    icon(context, cell, R.id.widget_cell_icon, WidgetIconSet.from(config.iconSet),
                        weather?.let { phaseAwareCondition(h.condition, it, h.timeMillis) } ?: h.condition, p, 40f)
                    v.addView(R.id.widget_hourly_forecast, cell)
                }
            }
            val low = days.mapNotNull { it.low }.minOrNull()
            val high = days.mapNotNull { it.high }.maxOrNull()
            val count = (if (isHours) hours.size else days.size).coerceAtLeast(1)
            repeat(count) { index ->
                val cellLayout = when {
                    isRows -> if (compact) R.layout.widget_forecast_row_compact else R.layout.widget_forecast_row
                    isHours -> if (compact) R.layout.widget_forecast_column_compact else R.layout.widget_forecast_column
                    roomyScene -> R.layout.widget_forecast_scene_roomy
                    kind == WidgetKind.SCENE && compact -> R.layout.widget_forecast_scene_compact
                    kind == WidgetKind.SCENE -> R.layout.widget_forecast_scene
                    kind == WidgetKind.WEEK && compact -> R.layout.widget_forecast_week_compact
                    kind == WidgetKind.WEEK -> R.layout.widget_forecast_week
                    compact && index == 0 && count > 1 -> R.layout.widget_forecast_week_start_compact
                    compact && index == count - 1 && count > 1 -> R.layout.widget_forecast_week_end_compact
                    compact -> R.layout.widget_forecast_week_compact
                    index == 0 && count > 1 -> R.layout.widget_forecast_week_start
                    index == count - 1 && count > 1 -> R.layout.widget_forecast_week_end
                    else -> R.layout.widget_forecast_week
                }
                val cell = RemoteViews(context.packageName, cellLayout)
                if (kind == WidgetKind.WEEK && style == WidgetStyle.VISTA && compact && height < 140) {
                    cell.setViewVisibility(R.id.widget_cell_icon, View.GONE)
                }
                if (compact && android.os.Build.VERSION.SDK_INT >= 31) {
                    val iconSize = if (roomyScene) 34f else if (isRows) 18f else if (kind == WidgetKind.WEEK && style == WidgetStyle.VISTA && height >= 160) 36f else 24f
                    cell.setViewLayoutHeight(R.id.widget_cell_icon, iconSize, TypedValue.COMPLEX_UNIT_DIP)
                    cell.setViewLayoutWidth(R.id.widget_cell_icon, iconSize, TypedValue.COMPLEX_UNIT_DIP)
                }
                val hour = hours.getOrNull(index)
                val day = days.getOrNull(index)
                val cellLabel = if (isHours) hour?.let { time(it.timeMillis, zone) } ?: "—"
                    else day?.let {
                        val date = Instant.ofEpochMilli(it.dateMillis).atZone(zone)
                        val todayLabel = date.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                        if (kind == WidgetKind.SCENE) (if (todayLabel) "今天" else date.format(DateTimeFormatter.ofPattern("E", Locale.CHINA))) + " ${date.dayOfMonth}"
                        else if (todayLabel) "今天" else date.format(DateTimeFormatter.ofPattern("E", Locale.CHINA))
                    } ?: "—"
                val cellTemp = if (isHours) temperature(hour?.temperature, config)
                    else if (isRows) "${temperature(day?.low, config)} / ${temperature(day?.high, config)}"
                    else "${temperature(day?.low, config)}/${temperature(day?.high, config)}"
                // Date, artwork and temperature have their own aligned rows.
                put(cell, R.id.widget_cell_label, cellLabel)
                fitText(context, cell, R.id.widget_cell_label, cellLabel, if (isRows) 12f else 11f,
                    if (isRows) 42f else (width - padding).toFloat() / count - 2f, if (isRows) (if (compact) 16f else 20f) else 14f,
                    if (isHours) config.textScale else null)
                put(cell, R.id.widget_cell_temp, cellTemp)
                val cellTempSp = 11f
                fitText(context, cell, R.id.widget_cell_temp, cellTemp, cellTempSp,
                    if (isRows) 65f else (width - padding).toFloat() / count - 8f,
                    if (isRows) (if (compact) 16f else 20f) else 14f, config.textScale)
                val extra = if (isHours) hour?.precipProb?.takeIf { it in 0..100 }?.let { "$it%" }
                    else if (isRows) day?.precipProbability?.takeIf { it in 0..100 }?.let { "$it%" }
                    else null
                put(cell, R.id.widget_cell_extra, extra.orEmpty())
                fitText(context, cell, R.id.widget_cell_extra, "100%", 10f, if (isRows) 38f else (width - padding).toFloat() / count - 2f, 12f, config.textScale)
                if (compact && isHours) cell.setViewVisibility(R.id.widget_cell_extra, View.GONE)
                cell.setTextColor(R.id.widget_cell_label, p.muted)
                cell.setTextColor(R.id.widget_cell_temp, p.ink)
                cell.setTextColor(R.id.widget_cell_extra, p.accent)
                icon(context, cell, R.id.widget_cell_icon, WidgetIconSet.from(config.iconSet),
                    if (isHours) hour?.let { h -> weather?.let { phaseAwareCondition(h.condition, it, h.timeMillis) } ?: h.condition }
                    else day?.condition, p, 40f)
                if (isRows) cell.setImageViewBitmap(R.id.widget_cell_chart, rangeBar(context,
                    (width - padding - 42 - 18 - 65 - 20).coerceAtLeast(8), if (compact) 8 else 10,
                    day?.low, day?.high, low, high, p))
                v.addView(R.id.widget_forecast, cell)
            }
        }
        // The final beta9 widget language deliberately has no alarm badge.
        v.setViewVisibility(R.id.widget_alarm, View.GONE)
        v.setViewVisibility(R.id.widget_alarm_hit, View.GONE)
        put(v, R.id.widget_alarm_label, "")
        if (interactive) clicks(context, v, widgetId, style, config, snapshot?.city)
        v.setContentDescription(R.id.widget_root, "${snapshot?.city?.displayName.orEmpty()}，${temperature(current?.temperature, config)}，${current?.condition?.label ?: "天气待更新"}")
        return v
    }
    fun temperature(value: Double?, config: WidgetConfig): String = value?.takeIf(Double::isFinite)?.let {
        "${(if (config.tempUnit == "f") it * 1.8 + 32 else it).roundToInt()}°"
    } ?: "—"
    private fun cityLabel(city: City?): String? = city?.name?.takeIf(String::isNotBlank) ?: city?.displayName?.takeIf(String::isNotBlank)
    private fun time(t: Long, zone: ZoneId) = Instant.ofEpochMilli(t).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    private fun put(v: RemoteViews, id: Int, text: String) { v.setViewVisibility(id, if (text.isEmpty()) View.GONE else View.VISIBLE); v.setTextViewText(id, text) }
    private fun icon(context: Context, v: RemoteViews, id: Int, iconSet: WidgetIconSet, condition: WeatherCondition?, p: WidgetPalette, sizeDp: Float = 64f,
                     ambient: com.zhisheng.weather.ui.components.NaturalLightPalette? = null) {
        WidgetIcons.bind(v, id, condition, iconSet, p.isLight, (sizeDp * context.resources.displayMetrics.density).roundToInt(), ambient)
    }
    internal fun layoutFor(style: WidgetStyle, kind: WidgetKind): Int = when (style) {
        WidgetStyle.VISTA -> when (kind) { WidgetKind.PULSE -> R.layout.widget_vista_pulse; WidgetKind.NOW -> R.layout.widget_vista_now; WidgetKind.BAND -> R.layout.widget_vista_band; WidgetKind.WEEK -> R.layout.widget_vista_week; WidgetKind.SCENE -> R.layout.widget_vista_scene }
        WidgetStyle.CLASSIC -> when (kind) { WidgetKind.PULSE -> R.layout.widget_classic_pulse; WidgetKind.NOW -> R.layout.widget_classic_now; WidgetKind.BAND -> R.layout.widget_classic_band; WidgetKind.WEEK -> R.layout.widget_classic_week; WidgetKind.SCENE -> R.layout.widget_classic_scene }
    }
    fun action(c: Context, id: Int, style: WidgetStyle, action: String, city: City? = null, widgetHit: String? = null): PendingIntent {
        if (action == "none") return PendingIntent.getBroadcast(c, 0,
            Intent(c, FreshWidgetProvider::class.java).setAction("com.zhisheng.weather.widget.NO_OP")
                .setData(Uri.parse("zhisheng://widget/$id/none/${widgetHit ?: "body"}")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val intent = Intent(c, WidgetLaunchActivity::class.java).setAction(action)
            .setData(Uri.parse("zhisheng://widget/$id/${style.key}/$action/${widgetHit ?: "body"}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("widget_style", style.key).putExtra("city", city?.locationKey)
            .apply { if (widgetHit != null) putExtra("widget_hit", widgetHit) }
        return PendingIntent.getActivity(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun clicks(c: Context, v: RemoteViews, id: Int, style: WidgetStyle, cfg: WidgetConfig, city: City?) {
        fun set(view: Int, kind: String) { v.setOnClickPendingIntent(view, action(c, id, style, kind, city, view.toString())) }
        // Every weather/background hit target obeys the same user selection,
        // including the explicit no-op action.
        listOf(R.id.widget_root, R.id.widget_content, R.id.widget_surface,
            R.id.widget_weather_hit, R.id.widget_city_hit, R.id.widget_city,
            R.id.widget_icon, R.id.widget_temp, R.id.widget_condition, R.id.widget_forecast,
            R.id.widget_range, R.id.widget_footer, R.id.widget_air, R.id.widget_metrics,
            R.id.widget_metric_1, R.id.widget_metric_2, R.id.widget_metric_3,
            R.id.widget_metric_4, R.id.widget_metric_5, R.id.widget_metric_6,
            R.id.widget_metric_label_1, R.id.widget_metric_label_2, R.id.widget_metric_label_3,
            R.id.widget_metric_label_4, R.id.widget_metric_label_5, R.id.widget_metric_label_6,
            R.id.widget_metric_hit_1, R.id.widget_metric_hit_2, R.id.widget_metric_hit_3,
            R.id.widget_metric_hit_4, R.id.widget_metric_hit_5, R.id.widget_metric_hit_6,
            R.id.widget_scene_briefing, R.id.widget_scene_briefing_text, R.id.widget_scene_girl,
            R.id.widget_hourly_forecast, R.id.widget_scene_hourly_title, R.id.widget_scene_day_title).forEach { set(it, cfg.clickWeather) }
        set(R.id.widget_clock_hit, cfg.clickTime); set(R.id.widget_time, cfg.clickTime)
        set(R.id.widget_date, cfg.clickDate); set(R.id.widget_lunar, cfg.clickDate)
        set(R.id.widget_edit, "settings")
    }
    private fun fitText(c: Context, views: RemoteViews, id: Int, text: String, preferredSp: Float, widthDp: Float, heightDp: Float,
                        numberScale: Float? = null) {
        val metrics = c.resources.displayMetrics
        val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        // Fit the upper end once, leaving space for the full slider range. Fitting
        // each requested size independently cancels scaling whenever a row is full.
        val maximumScale = if (numberScale != null) WidgetMaximumNumberScale else 1f
        var size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, preferredSp * maximumScale, metrics)
        paint.textSize = size
        val width = paint.measureText(text).coerceAtLeast(1f)
        // StaticLayout includes fallback CJK font metrics; Latin-only fontMetrics
        // underestimates Chinese date/condition rows at large system font sizes.
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, kotlin.math.ceil(width.toDouble()).toInt().coerceAtLeast(1))
            .setIncludePad(false).setMaxLines(1)
        if (android.os.Build.VERSION.SDK_INT >= 28) layout.setUseLineSpacingFromFallbacks(true)
        val height = layout.build().height.toFloat().coerceAtLeast(1f)
        size *= minOf(1f, (widthDp.coerceAtLeast(24f) * metrics.density - 1) / width, (heightDp * metrics.density - 1) / height)
        if (numberScale != null) {
            // Secondary numbers must not become microscopic just to preserve a
            // percentage. Interpolate from a readable floor to the measured cap;
            // primary numbers retain the full proportional range. Never exceed
            // the cap when an unusually narrow host or long value cannot fit.
            val small = maxOf(size * WidgetMinimumNumberScale / maximumScale,
                minOf(MINIMUM_NUMBER_SIZE_DP * metrics.density, size))
            val progress = (normalizedWidgetNumberScale(numberScale) - WidgetMinimumNumberScale) /
                (WidgetMaximumNumberScale - WidgetMinimumNumberScale)
            size = small + (size - small) * progress
        }
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, size)
    }
    private fun rangeBar(context: Context, widthDp: Int, heightDp: Int, low: Double?, high: Double?, min: Double?, max: Double?, p: WidgetPalette, current: Double? = null): Bitmap {
        val density = context.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap((widthDp * density).roundToInt().coerceIn(16, 1024),
            (heightDp * density).roundToInt().coerceIn(8, 64), Bitmap.Config.ARGB_8888)
        bmp.density = Bitmap.DENSITY_NONE
        val c = Canvas(bmp); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.scale(bmp.width / 240f, bmp.height / 20f)
        paint.color = ColorUtils.setAlphaComponent(p.muted, 40); c.drawRoundRect(3f, 7f, 237f, 13f, 3f, 3f, paint)
        if (low == null || high == null || min == null || max == null) return bmp
        fun x(v: Double) = (5 + ((v - min) / (max - min).coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * 230).toFloat()
        paint.shader = LinearGradient(0f, 0f, 240f, 0f, Color.rgb(74, 173, 208), Color.rgb(239, 172, 92), Shader.TileMode.CLAMP)
        paint.alpha = 255
        c.drawRoundRect(x(low), 7f, maxOf(x(high), x(low) + 6f), 13f, 3f, 3f, paint)
        if (current != null) { paint.shader = null; paint.color = p.ink; c.drawCircle(x(current), 10f, 4f, paint) }
        return bmp
    }
}
