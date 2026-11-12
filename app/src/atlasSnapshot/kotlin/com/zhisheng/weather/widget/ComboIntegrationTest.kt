package com.zhisheng.weather.widget

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComboIntegrationTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun cachedForecastAdvancesAcrossMidnightWithoutReplacingItsWeatherData() = frameworkViews {
        val zone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            val night = java.time.OffsetDateTime.parse("2026-11-10T22:00:00+08:00").toInstant().toEpochMilli()
            val morning = night + 9 * 3_600_000L
            val base = widgetPreviewSample(night)
            val cached = base.copy(weather = base.weather.copy(hourly = (0 until 24).map { index ->
                base.weather.hourly.first().copy(timeMillis = night + index * 3_600_000L)
            }))
            for (style in WidgetStyle.entries) {
                fun bind(time: Long) = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.COMBO),
                    WidgetConfig.defaults(style), cached, 344, 250, time, interactive = false)
                val view = bind(night).apply(paparazzi.context, FrameLayout(paparazzi.context))
                assertEquals("11/11", view.findViewById<TextView>(DAY_SLOTS[0].sublabel).text.toString())
                bind(morning).reapply(paparazzi.context, view)
                assertEquals("07:00", view.findViewById<TextView>(HOUR_SLOTS[0].label).text.toString())
                assertEquals("11/12", view.findViewById<TextView>(DAY_SLOTS[0].sublabel).text.toString())
                assertEquals(night, cached.weather.fetchedAt)
            }
        } finally { java.util.TimeZone.setDefault(zone) }
    }

    @Test fun reappliedForecastCanBeHiddenRestoredAndLoseStaleSlots() = frameworkViews {
        val now = java.time.OffsetDateTime.parse("2026-11-10T12:00:00+08:00").toInstant().toEpochMilli()
        val sample = widgetPreviewSample(now)
        for (style in WidgetStyle.entries) for (set in WidgetIconSet.entries) {
            val config = WidgetConfig.defaults(style).copy(iconSet = set.key)
            fun bind(c: WidgetConfig = config, data: WidgetSnapshot = sample) =
                WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.COMBO), c, data,
                    344, 250, now, interactive = false)
            val view = bind().apply(paparazzi.context, FrameLayout(paparazzi.context))
            bind(config.copy(showForecast = false)).reapply(paparazzi.context, view)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_forecast).visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_daily_forecast).visibility)
            bind().reapply(paparazzi.context, view)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_forecast).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_daily_forecast).visibility)

            val shorter = sample.copy(weather = sample.weather.copy(hourly = sample.weather.hourly.take(2),
                daily = sample.weather.daily.take(2)))
            bind(data = shorter).reapply(paparazzi.context, view)
            HOUR_SLOTS.drop(2).forEach { assertEquals(View.GONE, view.findViewById<View>(it.root).visibility) }
            assertEquals(View.GONE, view.findViewById<View>(DAY_SLOTS[1].root).visibility)
            val empty = sample.copy(weather = sample.weather.copy(hourly = emptyList(), daily = emptyList()))
            bind(data = empty).reapply(paparazzi.context, view)
            assertEquals("—", view.findViewById<TextView>(HOUR_SLOTS[0].label).text.toString())
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_daily_forecast).visibility)
            bind().reapply(paparazzi.context, view)
            assertEquals(6, HOUR_SLOTS.count { view.findViewById<View>(it.root).visibility == View.VISIBLE })
            assertEquals(2, DAY_SLOTS.count { view.findViewById<View>(it.root).visibility == View.VISIBLE })
        }
    }

    private fun frameworkViews(block: () -> Unit) {
        val inflater = LayoutInflater.from(paparazzi.context)
        fun field(name: String) = LayoutInflater::class.java.getDeclaredField(name).apply { isAccessible = true }
        val first = field("mFactory"); val second = field("mFactory2"); val set = field("mFactorySet")
        val previous = Triple(first.get(inflater), second.get(inflater), set.getBoolean(inflater))
        first.set(inflater, null); second.set(inflater, null); set.setBoolean(inflater, false)
        try { block() } finally {
            first.set(inflater, previous.first); second.set(inflater, previous.second); set.setBoolean(inflater, previous.third)
        }
    }
}
