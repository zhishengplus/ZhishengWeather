package com.zhisheng.weather.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class WeatherCurveTest {
    @Test fun straightRainfallRampHasNoScallopedMinuteSteps() {
        val points = (0..60).map { Offset(it.toFloat(), it * 0.2f) }
        weatherCurveTangents(points).forEach { assertEquals(0.2f, it, 0.00001f) }
    }

    @Test fun rainfallCurveNeverOvershootsSamplesOrInventsNegativeRain() {
        val points = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(3f, 0.8f),
            Offset(4f, 1.0f), Offset(9f, 0.1f), Offset(10f, 0f), Offset(12f, 0f))
        val slopes = weatherCurveTangents(points)
        for (i in 0 until points.lastIndex) {
            val a = points[i]; val b = points[i + 1]; val dx = b.x - a.x
            for (j in 0..100) {
                val t = j / 100f; val u = 1f - t
                val y = u*u*u*a.y + 3*u*u*t*(a.y + slopes[i]*dx/3) +
                    3*u*t*t*(b.y - slopes[i+1]*dx/3) + t*t*t*b.y
                assertTrue(y >= minOf(a.y,b.y) - 0.00001f)
                assertTrue(y <= maxOf(a.y,b.y) + 0.00001f)
            }
        }
    }
}
