package com.zhisheng.weather.widget

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherCondition
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetLivingSkyTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val sunset = Instant.parse("2026-06-20T10:40:00Z").toEpochMilli()
    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    @Test fun allFiveCardsHonourOpacityThemeAndNativeCorners() {
        for (kind in WidgetKind.entries) for (light in listOf(true, false)) for (opacity in listOf(0, 45, 100)) {
            val height = if (kind == WidgetKind.SCENE) 380 else if (kind == WidgetKind.NOW) 194 else kind.height
            val frame = FrameLayout(paparazzi.context).apply {
                setBackgroundColor(Color.rgb(55, 74, 95))
                layoutParams = ViewGroup.LayoutParams(dp(kind.width), dp(height))
            }
            val config = WidgetConfig(theme = if (light) "light" else "dark", opacity = opacity)
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, kind), config,
                widgetPreviewSample(sunset), kind.width, height, now = sunset, interactive = false,
                previewBackground = Color.rgb(55, 74, 95), livingSkyEnabled = true, ambienceLevel = AmbienceLevel.INTENSE)
                .apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(kind.width), dp(height)))
            paparazzi.snapshot(frame, "sky_${kind.name.lowercase()}_${light}_${opacity}")
            val surface = view.findViewById<ImageView>(R.id.widget_surface)
            assertEquals(opacity * 255 / 100, surface.imageAlpha)
            assertTrue(surface.clipToOutline)
            val outline = Outline()
            surface.outlineProvider.getOutline(surface, outline)
            assertTrue(outline.canClip())
            assertEquals(26f * paparazzi.context.resources.displayMetrics.density, outline.radius, .01f)
            assertEquals("Outline must never add an opaque layer below a transparent card", 0f, outline.alpha, 0f)
            if (opacity == 0) assertTrue(surface.drawable is GradientDrawable)
            else assertTrue((surface.drawable as BitmapDrawable).bitmap.allocationByteCount <= 65_536)
            if (kind == WidgetKind.NOW) assertEquals(surface.width, surface.height)
        }
    }

    @Test fun originalBackgroundAndIconChoiceSurviveDisablingOrMissingData() {
        val context = paparazzi.context
        val snapshot = widgetPreviewSample(sunset)
        for (style in WidgetStyle.entries) for (enabled in listOf(false, true)) {
            val cfg = WidgetConfig.defaults(style)
            val empty = snapshot.copy(weather = snapshot.weather.copy(current = null))
            for (data in listOf(snapshot, empty)) {
                val view = WidgetBinder.bind(context, 1, WidgetInstance(1, style, WidgetKind.NOW), cfg,
                    data, 164, 164, now = sunset, interactive = false, livingSkyEnabled = enabled).apply(context, FrameLayout(context))
                if (style == WidgetStyle.CLASSIC || !enabled || data.weather.current == null)
                    assertTrue(view.findViewById<ImageView>(R.id.widget_surface).drawable is GradientDrawable)
            }
        }
        assertNull(widgetSky(WidgetStyle.VISTA, snapshot, true, true, AmbienceLevel.OFF, sunset))
        assertNull(widgetSky(WidgetStyle.VISTA, snapshot.copy(city = snapshot.city.copy(latitude = Double.NaN)), true, true, AmbienceLevel.INTENSE, sunset))
    }

    @Test fun opaqueAutoTextRemainsReadableAcrossEverySolarPhaseAndWeather() {
        val context = paparazzi.context
        for (hour in listOf(0, 3, 9, 10, 11, 16)) for (light in listOf(true, false))
        for (condition in listOf(WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.RAIN, WeatherCondition.FOG)) {
            val now = Instant.parse("2026-06-20T${hour.toString().padStart(2, '0')}:40:00Z").toEpochMilli()
            val sample = widgetPreviewSample(now)
            val data = sample.copy(weather = sample.weather.copy(current = sample.weather.current!!.copy(condition = condition)))
            val sky = widgetSky(WidgetStyle.VISTA, data, light, true, AmbienceLevel.INTENSE, now)!!
            val config = WidgetConfig(theme = if (light) "light" else "dark", opacity = 100)
            val palette = widgetPalette(context, WidgetStyle.VISTA, config, Color.BLACK, sky.contrastColors)
            for (background in sky.contrastColors) {
                assertTrue("Main text contrast $condition/$hour/$light", ColorUtils.calculateContrast(palette.ink, background) >= 4.5)
                assertTrue("Secondary text contrast $condition/$hour/$light", ColorUtils.calculateContrast(
                    ColorUtils.compositeColors(palette.muted, background), background) >= 4.5)
            }
        }
    }

    @Test fun selectedCityAndOpacityDoNotChangeSolarPhaseOrManualTextChoice() {
        val sample = widgetPreviewSample(sunset)
        val local = widgetSky(WidgetStyle.VISTA, sample, true, true, AmbienceLevel.INTENSE, sunset)!!
        val elsewhere = widgetSky(WidgetStyle.VISTA, sample.copy(city = sample.city.copy(longitude = -74.0)), true, true, AmbienceLevel.INTENSE, sunset)!!
        assertNotEquals(local.state.solar!!.altitude, elsewhere.state.solar!!.altitude)
        val ctx = paparazzi.context
        for (text in listOf("light", "dark")) for (opacity in listOf(0, 45, 100)) {
            val config = WidgetConfig(text = text, opacity = opacity)
            assertEquals(widgetPalette(ctx, WidgetStyle.VISTA, config).ink,
                widgetPalette(ctx, WidgetStyle.VISTA, config, skyColors = local.contrastColors).ink)
        }
    }
}
