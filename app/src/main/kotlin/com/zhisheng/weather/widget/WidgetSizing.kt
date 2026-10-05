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
