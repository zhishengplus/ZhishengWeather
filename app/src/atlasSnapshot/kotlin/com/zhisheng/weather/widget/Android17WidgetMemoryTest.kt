package com.zhisheng.weather.widget

import android.util.SizeF
import android.widget.FrameLayout
import android.widget.RemoteViews
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Android17WidgetMemoryTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun ordinaryCardsKeepFullResolutionAndLayout() {
        val context = paparazzi.context
        val now = 1_781_952_000_000L
        val sample = widgetPreviewSample(now)
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) {
            val budget = WidgetBitmapBudget(widgetBitmapLimit(1080, 1920))
            val config = WidgetConfig.defaults(style)
            val remote = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind), config, sample,
                kind.width, kind.height, now, interactive = false, livingSkyEnabled = true,
                ambienceLevel = AmbienceLevel.INTENSE, bitmapBudget = budget)
            assertTrue("$style/$kind: ${budget.usedBytes}", budget.fits)
            assertEquals(1f, budget.scale, 0f)
            assertEquals(budget.usedBytes, parcelBitmapBytes(remote))
            val frame = FrameLayout(context)
            frame.addView(remote.apply(context, frame), FrameLayout.LayoutParams(
                (kind.width * context.resources.displayMetrics.density).toInt(),
                (kind.height * context.resources.displayMetrics.density).toInt()))
            paparazzi.snapshot(frame, "memory_${style.key}_${kind.name.lowercase()}")
        }
    }

    @Test fun allSixteenSizeVariantsShareOneBudgetAndOversizeImagesAreReduced() {
        val context = paparazzi.context
        val now = 1_781_952_000_000L
        val sample = widgetPreviewSample(now).let { it.copy(weather = it.weather.copy(
            rainMinutes = (0 until 120).map { index ->
                com.zhisheng.weather.model.MinutePrecip(now + index * 60_000L, (index % 10) / 10f)
            })) }
        val limit = 1_000_000L
        val passes = mutableListOf<WidgetBitmapBudget>()
        val remote = budgetedWidgetViews(limit) { budget ->
            passes += budget
            RemoteViews((0 until 16).associate { index ->
                val width = 320 + index * 30
                val height = 560 + index * 10
                SizeF(width.toFloat(), height.toFloat()) to WidgetBinder.bind(context, 1,
                    WidgetInstance(1, WidgetStyle.VISTA, WidgetKind.SCENE),
                    WidgetConfig(showPrecipitation = true), sample, width, height, now,
                    interactive = false, livingSkyEnabled = true, ambienceLevel = AmbienceLevel.INTENSE,
                    bitmapBudget = budget)
            })
        }
        assertTrue(passes.first().usedBytes > limit)
        assertTrue(passes.size in 2..4)
        assertTrue(passes.last().fits)
        assertTrue(passes.last().scale < 1f)
        assertEquals(passes.last().usedBytes, parcelBitmapBytes(remote))
        println("Widget parcel: ${passes.first().usedBytes} -> ${passes.last().usedBytes} bytes; scale=${passes.last().scale}")
        val frame = FrameLayout(context)
        frame.addView(remote.apply(context, frame), FrameLayout.LayoutParams(960, 1680))
        paparazzi.snapshot(frame, "memory_sixteen_sizes_reduced")
    }

    @Test fun clearingCachesDoesNotRecycleImagesHeldByRemoteViews() {
        val icon = WidgetIcons.bitmap(WeatherCondition.CLEAR, true, 144)
        val now = 1_781_952_000_000L
        val sky = widgetSky(WidgetStyle.VISTA, widgetPreviewSample(now), true, true, AmbienceLevel.INTENSE, now)!!
        val background = WidgetSkyBitmap.of(sky)
        com.zhisheng.weather.ZhishengApplication().onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertFalse(icon.isRecycled)
        assertFalse(background.isRecycled)
        assertNotSame(icon, WidgetIcons.bitmap(WeatherCondition.CLEAR, true, 144))
        assertNotSame(background, WidgetSkyBitmap.of(sky))
    }

    // Test-only verification of Android's own parcel accounting. No hidden API
    // reflection is used by production code.
    private fun parcelBitmapBytes(views: RemoteViews): Long =
        (RemoteViews::class.java.getDeclaredMethod("estimateMemoryUsage").invoke(views) as Int).toLong()
}
