package com.zhisheng.weather.widget

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Android 17 counts every bitmap/icon across all size variants in a RemoteViews parcel. */
class WidgetBitmapBudget internal constructor(val maxBytes: Long, val scale: Float = 1f) {
    private val images = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    var usedBytes: Long = 0
        private set

    fun record(image: Any, allocationBytes: Int) {
        if (images.add(image)) usedBytes += allocationBytes.coerceAtLeast(0).toLong()
    }

    fun dimension(requested: Int, minimum: Int, maximum: Int): Int =
        (requested.coerceIn(minimum, maximum) * scale).roundToInt().coerceIn(minimum, maximum)

    val fits: Boolean get() = usedBytes <= maxBytes

    fun reducedScale(): Float = (scale * sqrt(maxBytes.toDouble() / usedBytes.coerceAtLeast(1)) * .9)
        .toFloat().coerceIn(.01f, scale)
}

/** Leave 25% headroom below the platform's 1.5 * screen pixels * 4 byte limit. */
internal fun widgetBitmapLimit(widthPixels: Int, heightPixels: Int): Long =
    widthPixels.coerceAtLeast(1).toLong() * heightPixels.coerceAtLeast(1) * 9 / 2
