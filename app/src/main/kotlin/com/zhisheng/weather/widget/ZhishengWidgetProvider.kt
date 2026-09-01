package com.zhisheng.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.MainActivity
import com.zhisheng.weather.R
import com.zhisheng.weather.data.WidgetCache
import com.zhisheng.weather.data.WidgetSnapshot
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.WidgetBackgroundMode
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.conditionIconRes
import com.zhisheng.weather.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

internal fun widgetAqiText(snap: WidgetSnapshot): String? {
    val value = snap.aqi ?: return null
    val standard = when (snap.aqiStandard) {
        "中国" -> "国标"
        "美国" -> "美标"
        "欧洲" -> "欧标"
        "日本" -> "日标"
        "QWeather" -> "QAQI"
        else -> snap.aqiStandard
    }
    return listOf("AQI $value", snap.aqiLevel, standard)
        .filter(String::isNotBlank)
        .joinToString(" · ")
}

// 磷光腕表玻璃桌面小组件（0.1.3）
// 五个 Provider = 桌面选择器里五个独立条目（4x1 / 2x2 / 4x2 / 2x4 / 4x4）；
// 每个仍可拉伸，布局按实际尺寸自适应。
// 数据来自 WidgetCache（主 App 抓取后写入），小组件本身不发网络请求。
open class ZhishengWidgetProvider : AppWidgetProvider() {

    // 子类固定档位；null = 按实际尺寸自适应
    protected open val forcedLayout: Int? = null

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetSyncWorker.refreshNow(context)
                return
            }
            val pending = goAsync()
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val snap = runCatching { WidgetCache.load(context) }.getOrNull()
                    val light = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                        Configuration.UI_MODE_NIGHT_YES
                    val backgroundMode = SettingsRepository.widgetBackgroundMode.first()
                    val feedback = build(context, manager, widgetId, snap, light, backgroundMode).apply {
                        setLocalizedTextViewText(R.id.w_refresh, "…")
                        setContentDescription(R.id.w_refresh, context.getString(R.string.widget_refreshing))
                        if (forcedLayout != R.layout.widget_small && forcedLayout != R.layout.widget_nano) {
                            setLocalizedTextViewText(R.id.w_upd, context.getString(R.string.widget_refreshing))
                        }
                    }
                    // MIUI 会合并局部 RemoteViews 更新；完整重绘后稍作停留才能形成肉眼可见反馈。
                    manager.updateAppWidget(widgetId, feedback)
                    delay(REFRESH_PAINT_DELAY_MS)
                    WidgetSyncWorker.refreshNow(context)
                    // 恢复不再依赖网络任务状态：KEEP、排队、重试或断网都不能让“…”卡死。
                    delay(REFRESH_FALLBACK_DELAY_MS)
                    val latest = runCatching { WidgetCache.load(context) }.getOrNull()
                    manager.updateAppWidget(
                        widgetId,
                        build(context, manager, widgetId, latest, light, backgroundMode),
                    )
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        renderAsync(context, manager, intArrayOf(id))
    }

    private fun renderAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val snap = runCatching { WidgetCache.load(context) }
                    .onFailure { android.util.Log.e(TAG, "读取小组件缓存失败", it) }
                    .getOrNull()
                // 小组件主题（v0.0.5 修订）：只跟系统深浅，不跟 App 内手动主题——
                // App 切浅色时小组件保持系统外观，与选择器深色预览一致（用户反馈后调整）
                val light = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                    Configuration.UI_MODE_NIGHT_YES
                val backgroundMode = SettingsRepository.widgetBackgroundMode.first()
                ids.forEach { id ->
                    runCatching {
                        val views = build(context, manager, id, snap, light, backgroundMode)
                        manager.updateAppWidget(id, views)
                    }.onFailure {
                        // 单个实例失败不阻断其他尺寸，同时留下可诊断日志。
                        android.util.Log.e(TAG, "小组件渲染失败 id=$id", it)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun build(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        snap: WidgetSnapshot?,
        light: Boolean,
        backgroundMode: WidgetBackgroundMode,
    ): RemoteViews {
        val opts = manager.getAppWidgetOptions(id)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val layout = forcedLayout ?: when {
            minW >= 250 && minH >= 250 -> R.layout.widget_large
            minW < 180 && minH >= 220 -> R.layout.widget_tower
            minW >= 250 -> R.layout.widget_medium
            else -> R.layout.widget_small
        }
        val hasUpdate = layout != R.layout.widget_small && layout != R.layout.widget_nano
        val hasHourly = layout == R.layout.widget_medium
        val hasDaily = layout == R.layout.widget_large
        val spacious = layout == R.layout.widget_large
        val v = RemoteViews(context.packageName, layout)
        if (light) applyLightSkin(context, v) // XML 默认深色磷光，浅色按资源表整体换肤（v0.0.5）
        applyBackgroundMode(v, light, backgroundMode)

        // 整块点击进 App
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        v.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val refreshIntent = Intent(context, this::class.java)
            .setAction(ACTION_REFRESH)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        v.setOnClickPendingIntent(
            R.id.w_refresh,
            PendingIntent.getBroadcast(
                context,
                200_000 + id,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        v.setLocalizedTextViewText(
            R.id.w_date,
            Fmt.date(System.currentTimeMillis(), snap?.utcOffsetSeconds),
        )
        // 2x2 布局已移除 w_upd 控件（主动舍弃更新时间，v0.0.4）；其余档位正常显示
        if (hasUpdate) v.setViewVisibility(R.id.w_upd, View.VISIBLE)

        val snapshotExpired = snap?.let {
            it.temp != null && it.updateMillis > 0L &&
                System.currentTimeMillis() - it.updateMillis >= 24 * 60 * 60_000L
        } == true
        if (snap == null || snap.temp == null || snapshotExpired) {
            // 空态兜底文案资源化（v0.0.4）
            v.setLocalizedTextViewText(
                R.id.w_city,
                snap?.city?.takeIf(String::isNotBlank) ?: context.getString(R.string.widget_name),
            )
            v.setLocalizedTextViewText(R.id.w_temp, context.getString(R.string.widget_value_placeholder))
            v.setLocalizedTextViewText(
                R.id.w_range,
                when {
                    snapshotExpired -> context.getString(R.string.widget_stale_expired)
                    snap?.city.isNullOrBlank() -> context.getString(R.string.widget_sync_hint)
                    else -> context.getString(R.string.widget_refreshing)
                },
            )
            v.setLocalizedTextViewText(R.id.w_details, context.getString(R.string.widget_details_placeholder))
            if (hasUpdate) {
                v.setLocalizedTextViewText(R.id.w_upd, context.getString(R.string.widget_update_placeholder))
            }
            if (hasHourly) {
                listOf(R.id.h1_i, R.id.h2_i, R.id.h3_i, R.id.h4_i)
                    .forEach { v.setViewVisibility(it, View.INVISIBLE) }
            }
            if (hasDaily) {
                listOf(R.id.d1_row, R.id.d2_row, R.id.d3_row, R.id.d4_row, R.id.d5_row, R.id.d6_row, R.id.d7_row)
                    .forEach { v.setViewVisibility(it, View.GONE) }
                v.setViewVisibility(R.id.w_aqi, View.GONE)
                v.setViewVisibility(R.id.w_trend_section, View.GONE)
                v.setViewVisibility(R.id.w_life_section, View.GONE)
            }
            applyIconTone(context, v, R.id.w_icon, "CLOUDY", light)
            return v
        }

        v.setLocalizedTextViewText(
            R.id.w_city,
            widgetCityLabel(
                raw = snap.city,
                compact = layout == R.layout.widget_nano ||
                    layout == R.layout.widget_small ||
                    layout == R.layout.widget_tower,
            ).ifBlank { context.getString(R.string.widget_name) },
        )
        v.setLocalizedTextViewText(R.id.w_temp, "${snap.temp}°")
        v.setLocalizedTextViewText(
            R.id.w_range,
            buildString {
                if (snap.text.isNotBlank()) append(snap.text)
                if (snap.high != null && snap.low != null) {
                    if (isNotEmpty()) append("  ·  ")
                    append("${snap.high}° / ${snap.low}°")
                }
            },
        )
        v.setImageViewResource(R.id.w_icon, iconRes(snap.conditionName))
        applyIconTone(context, v, R.id.w_icon, snap.conditionName, light)
        val coreDetails = buildList {
            snap.feelsLike?.let { add("体感$it°") }
            snap.humidity?.let { add("湿度$it%") }
        }.joinToString(" · ")
        val details = if (spacious && snap.windText.isNotBlank()) {
            listOf(coreDetails, "风${snap.windText.replace(" ", "")}")
                .filter(String::isNotBlank)
                .joinToString("\n")
        } else {
            coreDetails
        }
        v.setLocalizedTextViewText(
            R.id.w_details,
            details.ifBlank { "体感-- · 湿度--" },
        )
        if (layout == R.layout.widget_tower) {
            val aux = buildList {
                widgetAqiText(snap)?.let(::add)
                snap.rainChance?.let { add("降水 $it%") }
                snap.windText.takeIf(String::isNotBlank)?.let { add("风 ${it.replace(" ", "")}") }
            }.joinToString("\n")
            v.setLocalizedTextViewText(R.id.w_aux, aux)
            v.setViewVisibility(R.id.w_aux, if (aux.isBlank()) View.GONE else View.VISIBLE)
        }
        if (hasUpdate) {
            v.setLocalizedTextViewText(
                R.id.w_upd,
                listOfNotNull(sourceShort(snap.source), updateLabel(context, snap))
                    .joinToString(" · ")
                    .ifBlank { context.getString(R.string.widget_update_placeholder) },
            )
        }

        if (hasHourly) {
            val hourIds = listOf(
                Triple(R.id.h1_t, R.id.h1_i, R.id.h1_v),
                Triple(R.id.h2_t, R.id.h2_i, R.id.h2_v),
                Triple(R.id.h3_t, R.id.h3_i, R.id.h3_v),
                Triple(R.id.h4_t, R.id.h4_i, R.id.h4_v),
            )
            hourIds.forEachIndexed { i, (tId, iId, vId) ->
                val h = snap.hours.getOrNull(i)
                if (h == null) {
                    v.setLocalizedTextViewText(tId, "")
                    v.setLocalizedTextViewText(vId, "")
                    v.setViewVisibility(iId, View.INVISIBLE)
                } else {
                    v.setLocalizedTextViewText(tId, h.label)
                    v.setLocalizedTextViewText(vId, h.temp?.let { "$it°" } ?: "--")
                    v.setImageViewResource(iId, iconRes(h.conditionName))
                    applyIconTone(context, v, iId, h.conditionName, light)
                    v.setViewVisibility(iId, View.VISIBLE)
                }
            }
        }

        if (hasDaily) {
            val trendHours = snap.hours.take(6).filter { it.temp != null }
            val trend = temperatureTrendBitmap(trendHours, light)
            if (trend != null) {
                v.setViewVisibility(R.id.w_trend_section, View.VISIBLE)
                v.setImageViewBitmap(R.id.w_temp_trend, trend)
                v.setLocalizedTextViewText(
                    R.id.w_trend_range,
                    "${trendHours.first().temp}° → ${trendHours.last().temp}°",
                )
            } else {
                v.setViewVisibility(R.id.w_trend_section, View.GONE)
            }

            if (snap.lifeTips.isEmpty()) {
                v.setViewVisibility(R.id.w_life_section, View.GONE)
            } else {
                v.setViewVisibility(R.id.w_life_section, View.VISIBLE)
                v.setLocalizedTextViewText(
                    R.id.w_life_line,
                    snap.lifeTips.joinToString("  ·  ") { "${it.label} ${it.value}" },
                )
            }

            val dayIds = listOf(
                intArrayOf(R.id.d1_t, R.id.d1_i, R.id.d1_b, R.id.d1_v),
                intArrayOf(R.id.d2_t, R.id.d2_i, R.id.d2_b, R.id.d2_v),
                intArrayOf(R.id.d3_t, R.id.d3_i, R.id.d3_b, R.id.d3_v),
                intArrayOf(R.id.d4_t, R.id.d4_i, R.id.d4_b, R.id.d4_v),
                intArrayOf(R.id.d5_t, R.id.d5_i, R.id.d5_b, R.id.d5_v),
                intArrayOf(R.id.d6_t, R.id.d6_i, R.id.d6_b, R.id.d6_v),
                intArrayOf(R.id.d7_t, R.id.d7_i, R.id.d7_b, R.id.d7_v),
            )
            val dayRowIds = listOf(
                R.id.d1_row, R.id.d2_row, R.id.d3_row, R.id.d4_row,
                R.id.d5_row, R.id.d6_row, R.id.d7_row,
            )
            val dailyTemps = snap.days.take(7).flatMap { listOfNotNull(it.low, it.high) }
            val dailyMin = dailyTemps.minOrNull()
            val dailyMax = dailyTemps.maxOrNull()
            dayIds.forEachIndexed { i, ids ->
                val (tId, iId, bId, vId) = ids
                val d = snap.days.getOrNull(i)
                if (d == null) {
                    v.setViewVisibility(dayRowIds[i], View.GONE)
                } else {
                    v.setViewVisibility(dayRowIds[i], View.VISIBLE)
                    v.setLocalizedTextViewText(tId, d.label)
                    v.setLocalizedTextViewText(
                        vId,
                        if (d.high != null && d.low != null) "${d.low}° ~ ${d.high}°" else "--",
                    )
                    v.setImageViewResource(iId, iconRes(d.conditionName))
                    applyIconTone(context, v, iId, d.conditionName, light)
                    v.setViewVisibility(iId, View.VISIBLE)
                    val rangeBar = dailyRangeBitmap(
                        low = d.low,
                        high = d.high,
                        globalLow = dailyMin,
                        globalHigh = dailyMax,
                        active = i == 0,
                        light = light,
                    )
                    if (rangeBar == null) {
                        v.setViewVisibility(bId, View.INVISIBLE)
                    } else {
                        v.setImageViewBitmap(bId, rangeBar)
                        v.setViewVisibility(bId, View.VISIBLE)
                    }
                }
            }
            val status = buildList {
                widgetAqiText(snap)?.let(::add)
                snap.rainChance?.let { add("降水 $it%") }
            }.joinToString("  ·  ")
            if (status.isNotBlank()) {
                v.setViewVisibility(R.id.w_aqi, View.VISIBLE)
                v.setLocalizedTextViewText(R.id.w_aqi, status)
            } else {
                v.setViewVisibility(R.id.w_aqi, View.GONE)
            }
        }
        return v
    }

    // 浅色换肤（v0.0.5）：文本色/背景/装饰条整体切换到纸面终端资源；
    // 五种布局 id 并集一次应用，缺失 id 的动作会被 RemoteViews 静默跳过
    private fun applyLightSkin(context: Context, v: RemoteViews) {
        fun color(res: Int) = context.getColor(res)
        v.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_light)
        v.setTextColor(R.id.w_city, color(R.color.widget_light_text_primary))
        v.setTextColor(R.id.w_date, color(R.color.widget_light_text_secondary))
        v.setTextColor(R.id.w_temp, color(R.color.widget_light_text_primary))
        v.setTextColor(R.id.w_range, color(R.color.widget_light_text_secondary))
        v.setTextColor(R.id.w_details, color(R.color.widget_light_text_tertiary))
        v.setTextColor(R.id.w_upd, color(R.color.widget_light_text_tertiary))
        v.setTextColor(R.id.w_aqi, color(R.color.widget_light_accent_cyan))
        v.setTextColor(R.id.w_trend_label, color(R.color.widget_light_text_tertiary))
        v.setTextColor(R.id.w_trend_range, color(R.color.widget_light_accent_cyan))
        v.setTextColor(R.id.w_refresh, color(R.color.widget_light_accent_cyan))
        v.setTextColor(R.id.w_life_label, color(R.color.widget_light_accent_orange))
        v.setTextColor(R.id.w_life_line, color(R.color.widget_light_text_secondary))
        v.setTextColor(R.id.w_aux, color(R.color.widget_light_accent_cyan))
        listOf(R.id.h1_t, R.id.h2_t, R.id.h3_t, R.id.h4_t)
            .forEach { v.setTextColor(it, color(R.color.widget_light_text_tertiary)) }
        listOf(R.id.h1_v, R.id.h2_v, R.id.h3_v, R.id.h4_v)
            .forEach { v.setTextColor(it, color(R.color.widget_light_text_primary)) }
        listOf(R.id.d1_t, R.id.d2_t, R.id.d3_t, R.id.d4_t, R.id.d5_t, R.id.d6_t, R.id.d7_t)
            .forEach { v.setTextColor(it, color(R.color.widget_light_text_secondary)) }
        listOf(R.id.d1_v, R.id.d2_v, R.id.d3_v, R.id.d4_v, R.id.d5_v, R.id.d6_v, R.id.d7_v)
            .forEach { v.setTextColor(it, color(R.color.widget_light_text_primary)) }
        v.setInt(R.id.widget_accent_bar, "setBackgroundResource", R.drawable.widget_accent_light)
        v.setInt(R.id.widget_rule_bar, "setBackgroundResource", R.drawable.widget_rule_light)
        v.setInt(R.id.widget_rule_bar_2, "setBackgroundResource", R.drawable.widget_rule_light)
        v.setImageViewResource(R.id.widget_live_dot, R.drawable.widget_live_dot_light)
    }

    private fun applyBackgroundMode(v: RemoteViews, light: Boolean, mode: WidgetBackgroundMode) {
        val background = when (mode) {
            WidgetBackgroundMode.TRANSPARENT -> R.drawable.widget_bg_transparent
            WidgetBackgroundMode.GLASS -> if (light) R.drawable.widget_bg_light else R.drawable.widget_bg
            WidgetBackgroundMode.OPAQUE -> if (light) R.drawable.widget_bg_opaque_light else R.drawable.widget_bg_opaque
        }
        v.setInt(R.id.widget_root, "setBackgroundResource", background)
    }

    // 小尺寸只显示第一级地名（例如“金川区”），避免完整行政区划把地名本身挤没。
    // 中大尺寸保留两级，仍能辨认同名地区，又不会出现“金川区·金...”式残缺。
    private fun widgetCityLabel(raw: String, compact: Boolean): String {
        val parts = raw
            .split(Regex("\\s*[·•/]\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (parts.isEmpty()) return raw.trim()
        return parts.take(if (compact) 1 else 2).joinToString(" · ")
    }

    // RemoteViews 不能承载自定义 View，因此绘制一条硬件刻度带。
    // 不画统计图式折线；温度只用刻度高度和读数表达，橙色仅标记当前第一点。
    private fun temperatureTrendBitmap(hours: List<com.zhisheng.weather.data.WidgetHour>, light: Boolean): Bitmap? {
        val samples = hours.take(6).mapNotNull { hour -> hour.temp?.let { hour.label to it } }
        if (samples.size < 2) return null

        val width = 640
        val height = 76
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cyan = Color.parseColor(if (light) "#075F73" else "#20F0FF")
        val orange = Color.parseColor(if (light) "#9B4408" else "#FF9830")
        val quiet = Color.parseColor(if (light) "#A0526169" else "#9AAABAB8")
        val minTemp = samples.minOf { it.second }
        val maxTemp = samples.maxOf { it.second }
        val span = (maxTemp - minTemp).coerceAtLeast(2)
        val left = 42f
        val right = width - 42f
        val baseline = 39f
        val step = (right - left) / (samples.size - 1)
        fun x(index: Int) = left + step * index
        fun tickTop(temp: Int) = baseline - 9f - ((temp - minTemp).toFloat() / span) * 15f

        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = quiet
            strokeWidth = 2f
        }
        canvas.drawLine(left, baseline, right, baseline, guide)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = quiet
            textSize = 16f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            textSize = 19f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        samples.forEachIndexed { index, sample ->
            val px = x(index)
            val active = index == 0
            val signal = if (active) orange else cyan
            canvas.drawLine(
                px,
                tickTop(sample.second),
                px,
                baseline,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = signal
                    strokeWidth = if (active) 5f else 3f
                    strokeCap = Paint.Cap.SQUARE
                },
            )
            valuePaint.color = signal
            canvas.drawText("${sample.second}°", px, 17f, valuePaint)
            canvas.drawText(sample.first, px, 69f, labelPaint)
        }
        return bitmap
    }

    // 逐日温度轨道：灰色全轨代表七天总温域，亮色段代表当天最低到最高温。
    // 这让原先空着的中列承担真实比较任务，而不是增加无含义装饰。
    private fun dailyRangeBitmap(
        low: Int?,
        high: Int?,
        globalLow: Int?,
        globalHigh: Int?,
        active: Boolean,
        light: Boolean,
    ): Bitmap? {
        if (low == null || high == null || globalLow == null || globalHigh == null) return null
        val width = 320
        val height = 24
        val left = 8f
        val right = width - 8f
        val center = height / 2f
        val span = (globalHigh - globalLow).coerceAtLeast(1)
        fun x(temp: Int): Float = left + (temp - globalLow).toFloat() / span * (right - left)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val quiet = Color.parseColor(if (light) "#58636D73" else "#5A9DAEB7")
        val signal = Color.parseColor(
            if (active) {
                if (light) "#B96A1B" else "#FF9830"
            } else {
                if (light) "#087389" else "#20F0FF"
            },
        )
        canvas.drawLine(
            left,
            center,
            right,
            center,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = quiet
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            },
        )
        val segment = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = signal
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        val start = x(minOf(low, high))
        val end = x(maxOf(low, high))
        canvas.drawLine(start, center, end.coerceAtLeast(start + 2f), center, segment)
        canvas.drawCircle(start, center, 4f, segment)
        canvas.drawCircle(end, center, 4f, segment)
        return bitmap
    }

    // 快照新鲜度（v0.0.4）：超过 3 小时显示「x小时前」，超过 24 小时提示过期，
    // 不再让几天前的旧数据伪装成「今天 HH:mm」。负龄为设备时钟回拨，退回显示时刻。
    private fun timeLabel(context: Context, snap: WidgetSnapshot): String {
        val ageMs = System.currentTimeMillis() - snap.updateMillis
        return when {
            ageMs < 3 * 3_600_000L ->
                snap.updateMillis.takeIf { it > 0 }
                    ?.let { Fmt.clock(it, snap.utcOffsetSeconds) }
                    ?: context.getString(R.string.widget_update_placeholder)
            ageMs < 24 * 3_600_000L -> context.getString(R.string.widget_stale_hours, ageMs / 3_600_000L)
            else -> context.getString(R.string.widget_stale_expired)
        }
    }

    private fun updateLabel(context: Context, snap: WidgetSnapshot): String {
        val label = timeLabel(context, snap)
        return if (label == context.getString(R.string.widget_stale_expired)) {
            label
        } else {
            context.getString(R.string.widget_updated_at, label)
        }
    }

    private fun sourceShort(source: String): String? = when (source) {
        "QWEATHER" -> "和风"
        "CAIYUN" -> "彩云"
        "XIAOMI" -> "小米"
        "OPEN-METEO" -> "公共源"
        else -> source.takeIf { it.isNotBlank() }
    }

    // 图标资源映射收敛在 model/ConditionIcons.kt（与 Compose 侧共用同一真源，v0.0.4）
    private fun iconRes(name: String): Int = conditionIconRes(
        runCatching { WeatherCondition.valueOf(name) }.getOrNull()
    ) ?: R.drawable.weather_cloud

    // 浅色模式的五种小组件共用同一支深青色图标墨水；天气差异只由图形表达，
    // 避免同一组件族同时出现灰云、青雨、琥珀太阳而像混用了多套图标系统。
    private fun applyIconTone(context: Context, views: RemoteViews, id: Int, _name: String, light: Boolean) {
        if (!light) return
        views.setInt(id, "setColorFilter", context.getColor(R.color.widget_light_accent_cyan))
    }

    companion object {
        private const val TAG = "ZhishengWidget"
        private const val ACTION_REFRESH = "com.zhisheng.weather.widget.REFRESH"
        private const val REFRESH_PAINT_DELAY_MS = 350L
        private const val REFRESH_FALLBACK_DELAY_MS = 1_650L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // 主 App 抓到新数据后调用，立即刷新所有已放置的小组件（三个规格都刷）
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            listOf(
                ZhishengWidgetSmall::class.java,
                ZhishengWidgetMedium::class.java,
                ZhishengWidgetLarge::class.java,
                ZhishengWidgetNano::class.java,
                ZhishengWidgetTower::class.java,
            ).forEach { cls ->
                val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(
                        Intent(context, cls)
                            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    )
                }
            }
        }
    }
}

class ZhishengWidgetSmall : ZhishengWidgetProvider() {
    override val forcedLayout = R.layout.widget_small
}

class ZhishengWidgetMedium : ZhishengWidgetProvider() {
    override val forcedLayout = R.layout.widget_medium
}

class ZhishengWidgetLarge : ZhishengWidgetProvider() {
    override val forcedLayout = R.layout.widget_large
}

class ZhishengWidgetNano : ZhishengWidgetProvider() {
    override val forcedLayout = R.layout.widget_nano
}

class ZhishengWidgetTower : ZhishengWidgetProvider() {
    override val forcedLayout = R.layout.widget_tower
}

private fun RemoteViews.setLocalizedTextViewText(viewId: Int, text: CharSequence) {
    setTextViewText(viewId, uiText(text.toString()))
}
