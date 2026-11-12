package com.zhisheng.weather.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import kotlin.math.abs

/** Launcher options describe the usable content area in dp, not physical pixels. */
internal fun widgetExactSizes(options: Bundle): List<SizeF> = if (Build.VERSION.SDK_INT >= 31) {
    options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        .filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
        .distinct().take(16)
} else emptyList()

internal fun widgetLegacySizes(options: Bundle, kind: WidgetKind): Pair<SizeF, SizeF> {
    fun dimension(key: String, fallback: Int) = options.getInt(key, fallback).takeIf { it > 0 } ?: fallback
    val minW = dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, kind.width)
    val minH = dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, kind.height)
    val maxW = dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW).coerceAtLeast(minW)
    val maxH = dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH).coerceAtLeast(minH)
    // Android's legacy RemoteViews constructor takes landscape first, portrait second.
    return SizeF(maxW.toFloat(), minH.toFloat()) to SizeF(minW.toFloat(), maxH.toFloat())
}

internal fun widgetEditorSize(options: Bundle, kind: WidgetKind, landscape: Boolean): SizeF? {
    val exact = widgetExactSizes(options)
    val (wide, tall) = widgetLegacySizes(options, kind)
    val estimated = if (landscape) wide else tall
    if (exact.isNotEmpty()) {
        return exact.minByOrNull { abs(it.width - estimated.width) + abs(it.height - estimated.height) }
    }
    val hasBounds = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) > 0 &&
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) > 0
    return estimated.takeIf { hasBounds }
}

/** Height buckets for 时日预报. Metrics still scale continuously inside each bucket. */
internal enum class ComboSizeBucket { SHORT, REGULAR, TALL, EXTRA_TALL }

internal fun comboSizeBucket(heightDp: Int) = when {
    heightDp < 224 -> ComboSizeBucket.SHORT
    heightDp < 300 -> ComboSizeBucket.REGULAR
    heightDp < 380 -> ComboSizeBucket.TALL
    else -> ComboSizeBucket.EXTRA_TALL
}

/**
 * Layout measurements for 时日预报 derived from the launcher host size (dp).
 * Sections always fill [height - verticalPadding]. Hourly icons stay square and
 * are capped by column width so they are never stretched.
 */
internal data class ComboLayoutMetrics(
    val headerHeight: Float,
    val hourlyHeight: Float,
    val dailyHeight: Float,
    val heroIcon: Float,
    val tempSize: Float,
    val tempWidth: Float,
    val tempHeight: Float,
    val hourlyIcon: Float,
    val hourlyLabelSize: Float,
    val hourlyLabelHeight: Float,
    val hourlyTempSize: Float,
    val hourlyTempHeight: Float,
    val dailyRowHeight: Float,
    val dailyIcon: Float,
    val dailyWeekdaySize: Float,
    val dailyWeekdayHeight: Float,
    val dailyDateSize: Float,
    val dailyDateHeight: Float,
    val dailyTempSize: Float,
    val dailyTempHeight: Float,
    val dailyPrecipSize: Float,
    val dailyPrecipHeight: Float,
    val dailyDrop: Float,
)

internal fun comboLayoutMetrics(
    width: Int,
    height: Int,
    compact: Boolean,
    horizontalPadding: Int,
    verticalPadding: Int,
): ComboLayoutMetrics {
    val contentH = (height - verticalPadding).toFloat().coerceAtLeast(120f)
    val contentW = (width - horizontalPadding).toFloat().coerceAtLeast(160f)
    // 4×3 (344×250) with a 16dp vertical inset leaves about 218dp of content.
    val scale = (contentH / 218f).coerceIn(0.72f, 2.1f)
    val hourlyLabelSize = (12f * scale).coerceIn(10f, 16f)
    val hourlyTempSize = (13.5f * scale).coerceIn(11f, 18f)
    val hourlyLabelHeight = hourlyLabelSize + 2f
    val hourlyTempHeight = hourlyTempSize + 3f
    val columnWidth = contentW / 6f
    val hourlyIconCap = (columnWidth - 4f).coerceIn(22f, 72f)
    val hourlyIconDesired = (36f * scale).coerceIn(if (compact) 22f else 28f, hourlyIconCap)
    val hourlyMin = hourlyLabelHeight + hourlyIconDesired + hourlyTempHeight + 8f
    val headerMin = if (compact) 36f else 48f
    // Header growth is also width-capped: a huge hero would crowd 多云 and the city stack.
    val headerMax = if (compact) 58f else 96f
    val heroCap = if (compact) 40f else 88f
    val dailyRowMin = if (compact) 28f else 36f
    val dailyIconCap = if (compact) 40f else 56f
    val dailyIcon = ((if (compact) 28f else 36f) * scale).coerceIn(if (compact) 24f else 28f, dailyIconCap)
    val dailyWeekdaySize = ((if (compact) 12f else 14f) * scale).coerceIn(11f, 18f)
    val dailyDateSize = (dailyWeekdaySize * 0.70f).coerceIn(9f, 13f)
    val dailyTempSize = ((if (compact) 12f else 14f) * scale).coerceIn(12f, 16f)
    val dailyPrecipSize = ((if (compact) 10f else 12f) * scale).coerceIn(10f, 13f)
    val dailyTextStack = dailyWeekdaySize + 3f + dailyDateSize + 3f
    val dailyContent = maxOf(dailyIcon, dailyTextStack)
    // Hug the row to its content (same tightness as 4×3). Leftover host height
    // goes to the header and hourly section, not to inter-row gaps.
    var dailyHeight = (dailyContent + 6f).coerceAtLeast(dailyRowMin) * 2f + 4f
    var headerHeight = ((if (compact) 44f else 58f) * scale).coerceIn(headerMin, headerMax)
    var hourlyHeight = contentH - headerHeight - dailyHeight
    if (hourlyHeight < hourlyMin) {
        var deficit = hourlyMin - hourlyHeight
        val dailyFloor = dailyContent * 2f + 8f + 4f
        val takeDaily = minOf(deficit, (dailyHeight - dailyFloor).coerceAtLeast(0f))
        dailyHeight -= takeDaily
        deficit -= takeDaily
        val takeHeader = minOf(deficit, (headerHeight - headerMin).coerceAtLeast(0f))
        headerHeight -= takeHeader
        hourlyHeight = contentH - headerHeight - dailyHeight
    } else {
        val slack = hourlyHeight - hourlyMin
        if (slack > 6f) {
            val addHeader = minOf(slack * 0.70f, headerMax - headerHeight)
            headerHeight += addHeader
            hourlyHeight = contentH - headerHeight - dailyHeight
        }
        // Hourly icons are width-capped, so extra height past a small comfort
        // pad would just be an empty well — send what the header can still use.
        // Do not grow daily icons further: that pulls 周三/周四 apart.
        val hourlyPacked = hourlyLabelHeight + hourlyIconCap + hourlyTempHeight + 8f
        val hourlyComfort = hourlyPacked + 20f
        if (hourlyHeight > hourlyComfort) {
            val move = minOf(hourlyHeight - hourlyComfort, (headerMax - headerHeight).coerceAtLeast(0f))
            headerHeight += move
            hourlyHeight -= move
        }
    }
    val dailyRowHeight = dailyHeight / 2f
    val heroIcon = (headerHeight - if (compact) 4f else 6f).coerceIn(if (compact) 28f else 40f, if (compact) headerHeight else heroCap)
    val tempHeight = (headerHeight * 0.74f).coerceIn(22f, headerHeight - 2f)
    val tempSize = (headerHeight * 0.66f).coerceIn(26f, 68f)
    val tempWidth = (contentW * 0.36f).coerceIn(78f, 160f)
    val hourlyIcon = (hourlyHeight - hourlyLabelHeight - hourlyTempHeight - 8f).coerceIn(22f, hourlyIconCap)
    val dailyTempHeight = (dailyTempSize + 6f).coerceAtMost(dailyRowHeight - 4f)
    return ComboLayoutMetrics(
        headerHeight = headerHeight,
        hourlyHeight = hourlyHeight,
        dailyHeight = dailyHeight,
        heroIcon = heroIcon,
        tempSize = tempSize,
        tempWidth = tempWidth,
        tempHeight = tempHeight,
        hourlyIcon = hourlyIcon,
        hourlyLabelSize = hourlyLabelSize,
        hourlyLabelHeight = hourlyLabelHeight,
        hourlyTempSize = hourlyTempSize,
        hourlyTempHeight = hourlyTempHeight,
        dailyRowHeight = dailyRowHeight,
        dailyIcon = dailyIcon,
        dailyWeekdaySize = dailyWeekdaySize,
        dailyWeekdayHeight = dailyWeekdaySize + 3f,
        dailyDateSize = dailyDateSize,
        dailyDateHeight = dailyDateSize + 2f,
        dailyTempSize = dailyTempSize,
        dailyTempHeight = dailyTempHeight,
        dailyPrecipSize = dailyPrecipSize,
        dailyPrecipHeight = dailyPrecipSize + 4f,
        dailyDrop = (dailyPrecipSize + 1f).coerceIn(10f, 18f),
    )
}
