package com.zhisheng.weather.widget

import android.content.Context
import android.widget.RemoteViews

/** Budget the whole parcel, including every responsive size, before publishing it. */
internal fun budgetedWidgetViews(context: Context, build: (WidgetBitmapBudget) -> RemoteViews): RemoteViews {
    val metrics = context.resources.displayMetrics
    val limit = widgetBitmapLimit(metrics.widthPixels, metrics.heightPixels)
    return budgetedWidgetViews(limit, build)
}

internal fun budgetedWidgetViews(limit: Long, build: (WidgetBitmapBudget) -> RemoteViews): RemoteViews {
    var scale = 1f
    repeat(4) {
        val budget = WidgetBitmapBudget(limit, scale)
        val views = build(budget)
        if (budget.fits) return views
        scale = budget.reducedScale()
    }
    // Do not send an over-budget parcel: target 37 makes that a fatal error.
    error("Widget bitmap budget exceeded")
}
