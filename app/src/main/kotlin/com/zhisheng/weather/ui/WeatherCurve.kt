package com.zhisheng.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** Monotone Hermite interpolation: preserves samples and cannot invent rainfall extrema. */
internal fun weatherCurveTangents(points: List<Offset>): FloatArray {
    if (points.size < 2) return FloatArray(points.size)
    val slopes = FloatArray(points.lastIndex) { i ->
        (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x).coerceAtLeast(0.001f)
    }
    return FloatArray(points.size) { i ->
        when (i) {
            0 -> slopes.first()
            points.lastIndex -> slopes.last()
            else -> {
                val a = slopes[i - 1]
                val b = slopes[i]
                if (a * b <= 0f) 0f else {
                    val left = points[i].x - points[i - 1].x
                    val right = points[i + 1].x - points[i].x
                    val w1 = 2f * right + left
                    val w2 = right + 2f * left
                    (w1 + w2) / (w1 / a + w2 / b)
                }
            }
        }
    }
}

internal fun weatherCurve(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    val slopes = weatherCurveTangents(points)
    moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val step = (b.x - a.x) / 3f
        cubicTo(a.x + step, a.y + slopes[i] * step,
            b.x - step, b.y - slopes[i + 1] * step, b.x, b.y)
    }
}
