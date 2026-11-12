package com.zhisheng.weather.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Launchers (HyperOS in particular) reapply a new RemoteViews onto the existing
 * widget tree. 逐时天光 / 时日预报 must then show the new hours and days using
 * only leaf setters on fixed slots, without growing or keeping stale children.
 */
class WidgetForecastReapplyTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5, theme = "android:Theme.Material.Light.NoActionBar")

    private fun at(text: String) = java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test fun overnightReapplyAdvancesHourlyAndDailySlots() {
        val night = at("2026-11-10T22:00:00+08:00")
        val morning = at("2026-11-11T07:00:00+08:00")
        val zone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            for (kind in listOf(WidgetKind.COMBO, WidgetKind.BAND)) for (style in WidgetStyle.entries) {
                val config = WidgetConfig.defaults(style)
                val (w, h) = if (kind == WidgetKind.COMBO) 344 to 250 else 344 to 160
                fun bind(now: Long) = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind), config,
                    widgetPreviewSample(now), w, h, now, interactive = false)
                val first = bind(night)
                val restore = stripAppCompatFactory()
                val view = try { first.apply(paparazzi.context, FrameLayout(paparazzi.context)) } finally { restore() }
                val forecast = view.findViewById<ViewGroup>(R.id.widget_forecast)
                val childCount = forecast.childCount
                fun label(id: Int) = view.findViewById<TextView>(id).text.toString()
                assertEquals("$kind ${style.key} night", "22:00", label(HOUR_SLOTS[0].label))
                val next = bind(morning)
                assertEquals("same launcher layout is reused", first.layoutId, next.layoutId)
                next.reapply(paparazzi.context, view)
                assertEquals("$kind ${style.key} slots must not grow on reapply", childCount, forecast.childCount)
                assertEquals("$kind ${style.key} morning first hour", "07:00", label(HOUR_SLOTS[0].label))
                assertEquals("$kind ${style.key} morning last hour", "12:00", label(HOUR_SLOTS[5].label))
                if (kind == WidgetKind.COMBO) {
                    assertEquals("周四", label(DAY_SLOTS[0].label))
                    assertEquals("11/12", label(DAY_SLOTS[0].sublabel))
                    assertEquals("周五", label(DAY_SLOTS[1].label))
                }
                val visible = HOUR_SLOTS.count { view.findViewById<View>(it.root).visibility == View.VISIBLE }
                assertEquals(6, visible)
            }
        } finally {
            java.util.TimeZone.setDefault(zone)
        }
    }

    private fun stripAppCompatFactory(): () -> Unit {
        val inflater = LayoutInflater.from(paparazzi.context)
        val cls = LayoutInflater::class.java
        fun field(name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
        val f1 = field("mFactory"); val f2 = field("mFactory2"); val fs = field("mFactorySet")
        val previous = Triple(f1.get(inflater), f2.get(inflater), fs.getBoolean(inflater))
        f1.set(inflater, null); f2.set(inflater, null); fs.setBoolean(inflater, false)
        return { f1.set(inflater, previous.first); f2.set(inflater, previous.second); fs.setBoolean(inflater, previous.third) }
    }
}
