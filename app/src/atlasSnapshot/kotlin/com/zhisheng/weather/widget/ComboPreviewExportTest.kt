package com.zhisheng.weather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Inflates production RemoteViews via WidgetBinder and writes real screenshots. */
class ComboPreviewExportTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    /** Real renders land in app/build/combo-preview unless -DcomboPreviewDir is set. */
    private val outRoot = File(System.getProperty("comboPreviewDir") ?: "build/combo-preview").apply { mkdirs() }

    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    @Test fun exportRealComboPreviews() {
        val now = java.time.OffsetDateTime.parse("2026-11-10T12:00:00+02:00").toInstant().toEpochMilli()
        val sample = sunnyDaytimeSample(widgetPreviewSample(now))
        val wallpaperColor = 0xFF9BB83A.toInt()
        val sizes = listOf(
            Triple("4x2", 344, 180),
            Triple("4x3", 344, 250),
            Triple("4x4", 344, 333),
            Triple("4x5", 344, 417),
        )
        val outDir = File(outRoot, "combo").apply { mkdirs() }
        val rendered = linkedMapOf<String, Bitmap>()
        for ((label, width, height) in sizes) {
            for (theme in listOf("light", "dark")) {
                val frame = render(width, height, theme, sample, now, wallpaperColor)
                paparazzi.snapshot(frame, "combo_${label}_${theme}")
                val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                frame.draw(Canvas(bitmap))
                val file = File(outDir, "combo-${label}-${theme}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                rendered["$label-$theme"] = bitmap
                assertComboStructure(frame.getChildAt(1), width, height, theme)
            }
        }
        writeCombined(rendered)
        assertTrue(File(outRoot, "widget-real-preview.png").length() in 1 until 5_000_000)
        assertTrue(File(outDir, "combo-4x3-light.png").isFile)
        assertTrue(File(outDir, "combo-4x3-dark.png").isFile)
        assertTrue(File(outDir, "combo-4x4-light.png").isFile)
        assertTrue(File(outDir, "combo-4x4-dark.png").isFile)
    }

    /** Daytime sample with sunny hero, mixed hourly slots and one sunny daily row. */
    private fun sunnyDaytimeSample(base: WidgetSnapshot): WidgetSnapshot {
        val hours = listOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.RAIN, WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY)
        val w = base.weather
        return base.copy(weather = w.copy(
            current = w.current!!.copy(condition = WeatherCondition.CLEAR, weatherText = "晴"),
            hourly = w.hourly.mapIndexed { i, h -> h.copy(condition = hours[i % hours.size],
                precipProb = if (hours[i % hours.size] == WeatherCondition.RAIN) 60 else 10) },
            daily = w.daily.mapIndexed { i, d -> when (i) {
                // Combo daily rows start tomorrow (index 1 = 周三, index 2 = 周四).
                1 -> d.copy(condition = WeatherCondition.CLEAR, precipProbability = 5)
                2 -> d.copy(condition = WeatherCondition.RAIN, precipProbability = 40)
                else -> d
            } },
        ))
    }

    private fun render(widthDp: Int, heightDp: Int, theme: String, sample: WidgetSnapshot, now: Long, wallpaperColor: Int): FrameLayout {
        val pad = dp(24)
        val width = dp(widthDp)
        val height = dp(heightDp)
        val outerW = width + pad * 2
        val outerH = height + pad * 2
        val context = paparazzi.context
        val outer = FrameLayout(context).apply { layoutParams = ViewGroup.LayoutParams(outerW, outerH) }
        val wallpaper = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(yellowGreenWallpaper(outerW, outerH))
        }
        outer.addView(wallpaper, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val config = WidgetConfig.defaults(WidgetStyle.VISTA).copy(theme = theme)
        val remote = WidgetBinder.bind(
            context, 1, WidgetInstance(1, WidgetStyle.VISTA, WidgetKind.COMBO), config, sample,
            widthDp, heightDp, now, interactive = false, previewBackground = wallpaperColor,
        )
        // Paparazzi's AppCompat inflater turns ImageView into AppCompatImageView,
        // which rejects RemoteViews.setImageResource. Use framework widgets so the
        // production RemoteViews actions apply exactly as a launcher would.
        val restore = stripAppCompatFactory(context)
        val view = try {
            remote.apply(context, FrameLayout(context))
        } finally {
            restore()
        }
        outer.addView(view, FrameLayout.LayoutParams(width, height, Gravity.CENTER))
        outer.measure(
            View.MeasureSpec.makeMeasureSpec(outerW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(outerH, View.MeasureSpec.EXACTLY),
        )
        outer.layout(0, 0, outerW, outerH)
        return outer
    }

    private fun offsetX(view: View, ancestor: View): Int {
        var x = 0
        var current: View? = view
        val seen = hashSetOf<View>()
        while (current != null && current != ancestor && seen.add(current)) {
            x += current.left
            current = current.parent as? View
        }
        return x
    }

    private fun assertComboStructure(widget: View, widthDp: Int, heightDp: Int, theme: String) {
        val tag = "$theme ${widthDp}x${heightDp}"
        val forecast = widget.findViewById<ViewGroup>(R.id.widget_forecast)
        val visibleSlots = HOUR_SLOTS.filter { widget.findViewById<View>(it.root).visibility == View.VISIBLE }
        val hours = visibleSlots.map { widget.findViewById<View>(it.root) }
        assertTrue("$tag hourly missing", hours.size >= 4)
        visibleSlots.forEachIndexed { index, slot ->
            val extra = widget.findViewById<View>(slot.extra)
            assertEquals("hourly precip must be gone: $tag #$index", View.GONE, extra.visibility)
            val icon = widget.findViewById<ImageView>(slot.icon)
            assertEquals("hourly icons stay square", icon.layoutParams.width, icon.layoutParams.height)
        }
        val firstHour = hours.first()
        val lastHour = hours.last()
        assertEquals("$tag hourly should start at the content edge", 0, firstHour.left)
        assertTrue("$tag hourly should end at the content edge: right=${lastHour.right} width=${forecast.width}",
            kotlin.math.abs(lastHour.right - forecast.width) <= dp(2))

        assertEquals("two future days", 2, DAY_SLOTS.count { widget.findViewById<View>(it.root).visibility == View.VISIBLE })
        val (d0, d1) = DAY_SLOTS
        val weekday = widget.findViewById<TextView>(d0.label)
        val date = widget.findViewById<TextView>(d0.sublabel)
        assertEquals("周三", weekday.text.toString())
        assertEquals("11/11", date.text.toString())
        assertEquals("周四", widget.findViewById<TextView>(d1.label).text.toString())
        assertEquals("11/12", widget.findViewById<TextView>(d1.sublabel).text.toString())
        assertTrue("$tag date should be smaller than weekday: ${date.textSize} vs ${weekday.textSize}",
            date.textSize < weekday.textSize - 0.5f)
        assertTrue("$tag date should be more muted than weekday",
            Color.alpha(date.currentTextColor) < Color.alpha(weekday.currentTextColor))
        val weekdayCenter = (weekday.left + weekday.right) / 2
        val dateCenter = (date.left + date.right) / 2
        assertTrue("$tag date should be centered under weekday: $dateCenter vs $weekdayCenter",
            kotlin.math.abs(weekdayCenter - dateCenter) <= dp(2))

        val drop = widget.findViewById<View>(d0.drop)
        val percent = widget.findViewById<View>(d0.extra)
        val dropGap = percent.left - drop.right
        assertTrue("$tag droplet should hug percent (2-4dp): gap=$dropGap",
            dropGap in dp(2)..dp(5))
        val drop2 = widget.findViewById<View>(d1.drop)
        val percent2 = widget.findViewById<View>(d1.extra)
        val percentRight0 = offsetX(percent, widget) + percent.width
        val percentRight1 = offsetX(percent2, widget) + percent2.width
        assertTrue("$tag percent right edges should align: $percentRight0 vs $percentRight1",
            kotlin.math.abs(percentRight0 - percentRight1) <= dp(1))
        assertTrue("$tag second droplet should also hug percent",
            percent2.left - drop2.right in dp(2)..dp(5))
        assertDropletsAligned(widget, tag)

        val hero = widget.findViewById<ImageView>(R.id.widget_icon)
        assertEquals("hero icon stays square", hero.layoutParams.width, hero.layoutParams.height)
        val temp = widget.findViewById<View>(R.id.widget_temp)
        val firstHourLeft = offsetX(firstHour, widget)
        val lastHourRight = offsetX(lastHour, widget) + lastHour.width
        val tempLeft = offsetX(temp, widget)
        val heroRight = offsetX(hero, widget) + hero.width
        val percentRight = offsetX(percent, widget) + percent.width
        assertTrue("$tag first hourly vs hero temp: $firstHourLeft vs $tempLeft",
            kotlin.math.abs(firstHourLeft - tempLeft) <= dp(4))
        assertTrue("$tag last hourly vs hero icon: $lastHourRight vs $heroRight",
            kotlin.math.abs(lastHourRight - heroRight) <= dp(4))
        assertTrue("$tag last hourly vs daily percent: $lastHourRight vs $percentRight",
            kotlin.math.abs(lastHourRight - percentRight) <= dp(4))

        val header = widget.findViewById<View>(R.id.widget_header)
        val dailySection = widget.findViewById<View>(R.id.widget_daily_forecast)
        val occupied = header.height + forecast.height + dailySection.height
        val content = widget.findViewById<View>(R.id.widget_content)
        val inner = content.height - content.paddingTop - content.paddingBottom
        assertTrue("sections should fill $widthDp x $heightDp: occupied=$occupied inner=$inner",
            occupied + dp(6) >= inner)
    }

    /** Both daily droplets share one x; each still sits 2-5dp from its own number's glyphs. */
    private fun assertDropletsAligned(widget: View, tag: String) {
        val (d0, d1) = DAY_SLOTS
        val drops = listOf(d0, d1).map { widget.findViewById<View>(it.drop) }
        val texts = listOf(d0, d1).map { widget.findViewById<TextView>(it.extra) }
        val x0 = offsetX(drops[0], widget)
        val x1 = offsetX(drops[1], widget)
        assertEquals("$tag droplets must share one x (${texts[0].text} vs ${texts[1].text})", x0, x1)
        texts.forEachIndexed { i, text ->
            val glyphLeft = offsetX(text, widget) + text.layout.getLineLeft(0).toInt()
            val gap = glyphLeft - (offsetX(drops[i], widget) + drops[i].width)
            assertTrue("$tag droplet ${text.text} should hug its number: gap=$gap", gap in dp(2)..dp(5))
        }
    }

    @Test fun dropletsAlignForZeroAndHundredPercent() {
        val now = java.time.OffsetDateTime.parse("2026-11-10T12:00:00+02:00").toInstant().toEpochMilli()
        val base = sunnyDaytimeSample(widgetPreviewSample(now))
        for ((first, second) in listOf(0 to 100, 100 to 0, 5 to 40, 99 to 1)) {
            val sample = base.copy(weather = base.weather.copy(daily = base.weather.daily.mapIndexed { i, d ->
                when (i) { 1 -> d.copy(precipProbability = first); 2 -> d.copy(precipProbability = second); else -> d }
            }))
            for ((label, height) in listOf("4x2" to 180, "4x3" to 250, "4x4" to 333, "4x5" to 417)) {
                for (theme in listOf("light", "dark")) {
                    val frame = render(344, height, theme, sample, now, 0xFF9BB83A.toInt())
                    assertDropletsAligned(frame.getChildAt(1), "$theme $label $first%/$second%")
                    if (first == 0 && label == "4x4" && theme == "light") {
                        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                        frame.draw(Canvas(bitmap))
                        File(File(outRoot, "combo").apply { mkdirs() }, "combo-4x4-light-0-100.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                }
            }
        }
    }

    private fun stripAppCompatFactory(context: android.content.Context): () -> Unit {
        val inflater = LayoutInflater.from(context)
        val cls = LayoutInflater::class.java
        fun field(name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
        val factory = field("mFactory")
        val factory2 = field("mFactory2")
        val factorySet = field("mFactorySet")
        val previous = Triple(factory.get(inflater), factory2.get(inflater), factorySet.getBoolean(inflater))
        factory.set(inflater, null)
        factory2.set(inflater, null)
        factorySet.setBoolean(inflater, false)
        return {
            factory.set(inflater, previous.first)
            factory2.set(inflater, previous.second)
            factorySet.setBoolean(inflater, previous.third)
        }
    }

    private fun yellowGreenWallpaper(width: Int, height: Int): Bitmap {
        val small = Bitmap.createBitmap((width / 8).coerceAtLeast(8), (height / 8).coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(small)
        canvas.drawColor(0xFFA8C43D.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(small.width * 0.28f, small.height * 0.22f, small.width * 0.85f,
            intArrayOf(0xFFF3F47A.toInt(), 0x00A8C43D.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, small.width.toFloat(), small.height.toFloat(), paint)
        paint.shader = RadialGradient(small.width * 0.82f, small.height * 0.78f, small.width * 0.75f,
            intArrayOf(0xFF6FA12A.toInt(), 0x006FA12A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, small.width.toFloat(), small.height.toFloat(), paint)
        paint.shader = RadialGradient(small.width * 0.55f, small.height * 0.45f, small.width * 0.55f,
            intArrayOf(0xFFD4E35A.toInt(), 0x00D4E35A.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, small.width.toFloat(), small.height.toFloat(), paint)
        return Bitmap.createScaledBitmap(small, width, height, true)
    }

    private fun writeCombined(rendered: Map<String, Bitmap>) {
        val keys = listOf(
            "4x3-light" to "4x3 light",
            "4x3-dark" to "4x3 dark",
            "4x4-light" to "4x4 light",
            "4x4-dark" to "4x4 dark",
        )
        val tileW = keys.maxOf { rendered.getValue(it.first).width }
        val labelH = 56
        val gap = 28
        val rowHeights = listOf(
            labelH + rendered.getValue("4x3-light").height,
            labelH + rendered.getValue("4x4-light").height,
        )
        val width = gap * 3 + tileW * 2
        val height = gap * 3 + rowHeights.sum()
        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFFC6D94A.toInt(), 0xFF8FBF3A.toInt(), 0xFF6B9A2E.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF243014.toInt()
            textSize = 36f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        keys.forEachIndexed { index, (key, caption) ->
            val col = index % 2
            val row = index / 2
            val tile = rendered.getValue(key)
            val x = gap + col * (tileW + gap) + (tileW - tile.width) / 2
            val y0 = gap + (if (row == 0) 0 else gap + rowHeights[0])
            canvas.drawText(caption, x.toFloat(), (y0 + 40).toFloat(), text)
            canvas.drawBitmap(tile, x.toFloat(), (y0 + labelH).toFloat(), null)
        }
        File(outRoot, "widget-real-preview.png").outputStream().use {
            combined.compress(Bitmap.CompressFormat.PNG, 90, it)
        }
        combined.recycle()
    }
}
