package com.zhisheng.weather.widget

import com.zhisheng.weather.data.WidgetSnapshot

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    @Test
    fun widgetAqiKeepsItsNationalStandardVisible() {
        assertEquals(
            "AQI 46 · 优 · 美标",
            widgetAqiText(WidgetSnapshot(aqi = 46, aqiLevel = "优", aqiStandard = "美国")),
        )
        assertEquals(
            "AQI 35 · 国标",
            widgetAqiText(WidgetSnapshot(aqi = 35, aqiStandard = "中国")),
        )
    }

    @Test
    fun widgetBackgroundOffersTransparentGlassAndOpaqueModes() {
        val root = sequenceOf(File("app/src/main"), File("src/main")).first { it.isDirectory }
        val settings = File(root, "kotlin/com/zhisheng/weather/data/SettingsRepository.kt").readText()
        val screen = File(root, "kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()
        val provider = File(root, "kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt").readText()

        assertTrue(settings.contains("TRANSPARENT(\"transparent\", \"全透明\")"))
        assertTrue(settings.contains("GLASS(\"glass\", \"玻璃\")"))
        assertTrue(settings.contains("OPAQUE(\"opaque\", \"不透明\")"))
        assertTrue(screen.contains("桌面组件底色"))
        assertTrue(screen.contains("ZhishengWidgetProvider.refreshAll(context)"))
        assertTrue(provider.contains("WidgetBackgroundMode.TRANSPARENT -> R.drawable.widget_bg_transparent"))
        assertTrue(provider.contains("WidgetBackgroundMode.OPAQUE -> if (light) R.drawable.widget_bg_opaque_light else R.drawable.widget_bg_opaque"))

        val transparent = File(root, "res/drawable/widget_bg_transparent.xml").readText()
        val darkOpaque = File(root, "res/drawable/widget_bg_opaque.xml").readText()
        val lightOpaque = File(root, "res/drawable/widget_bg_opaque_light.xml").readText()
        assertTrue(transparent.contains("#00000000"))
        assertTrue(darkOpaque.contains("#FF"))
        assertTrue(lightOpaque.contains("#FF"))
    }

    @Test
    fun everyWidgetLayoutUsesOnlyRemoteViewsSupportedElements() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        val widgetLayouts = layoutDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("widget_") && it.extension == "xml" }

        assertTrue("Expected widget XML layouts", widgetLayouts.isNotEmpty())
        val allowed = setOf("LinearLayout", "TextView", "ImageView", "ViewFlipper", "include")
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        widgetLayouts.forEach { file ->
            val document = parser.parse(file)
            val nodes = document.getElementsByTagName("*")
            val unsupported = (0 until nodes.length)
                .map { nodes.item(it).nodeName.substringAfterLast('.') }
                .filterNot { it in allowed }
                .distinct()
            assertTrue(
                "${file.name} contains RemoteViews-unsupported elements: $unsupported",
                unsupported.isEmpty(),
            )
        }
    }

    @Test
    fun primaryWidgetsExposeReadableDateAndDetailFields() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()

        listOf("widget_small.xml", "widget_medium.xml", "widget_large.xml").forEach { name ->
            val document = parser.parse(File(layoutDir, name))
            val xml = File(layoutDir, name).readText()
            assertTrue("$name must show a date", xml.contains("@+id/w_date"))
            assertTrue("$name must show current details", xml.contains("@+id/w_details"))

            val nodes = document.getElementsByTagName("*")
            val temp = (0 until nodes.length).map { nodes.item(it) }
                .first { it.attributes?.getNamedItem("android:id")?.nodeValue == "@+id/w_temp" }
            val icon = (0 until nodes.length).map { nodes.item(it) }
                .first { it.attributes?.getNamedItem("android:id")?.nodeValue == "@+id/w_icon" }
            val tempSize = temp.attributes.getNamedItem("android:textSize").nodeValue.removeSuffix("sp").toFloat()
            val iconSize = icon.attributes.getNamedItem("android:layout_width").nodeValue.removeSuffix("dp").toFloat()
            assertTrue("$name temperature is too small: $tempSize", tempSize >= 44f)
            assertTrue("$name icon is too small: $iconSize", iconSize >= 44f)
        }
    }

    @Test
    fun widgetPanelUsesAVisibleCornerRadius() {
        val drawable = sequenceOf(
            File("app/src/main/res/drawable/widget_bg.xml"),
            File("src/main/res/drawable/widget_bg.xml"),
        ).first { it.isFile }.readText()
        val radius = Regex("android:radius=\"([0-9.]+)dp\"")
            .find(drawable)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        assertTrue("Widget corner radius must be visibly rounded", radius >= 16f)
        val alphaChannels = Regex("#[0-9A-Fa-f]{8}")
            .findAll(drawable)
            .map { it.value.substring(1, 3).toInt(16) }
            .toList()
        assertTrue("Widget glass must contain translucent layers", alphaChannels.any { it in 1..254 })
        assertTrue(
            "Widget shell should use one quiet border instead of stacked outlines",
            Regex("<stroke\\b").findAll(drawable).count() == 1,
        )
    }

    @Test
    fun compactWidgetsAlwaysReserveAWeightedReadableCityLine() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        listOf("widget_small.xml", "widget_nano.xml", "widget_tower.xml").forEach { name ->
            val xml = File(layoutDir, name).readText()
            val cityNode = Regex(
                "android:id=\"@\\+id/w_city\"[\\s\\S]*?android:maxLines=\"1\"",
            ).find(xml)?.value.orEmpty()
            assertTrue("$name must reserve a readable single-line city slot", cityNode.isNotBlank())
            assertTrue(
                "$name city slot must fill or consume the remaining row width",
                cityNode.contains("android:layout_width=\"match_parent\"") ||
                    (cityNode.contains("android:layout_width=\"0dp\"") && cityNode.contains("android:layout_weight=\"1\"")),
            )
        }

        val source = sequenceOf(
            File("app/src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
            File("src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
        ).first { it.isFile }.readText()
        assertTrue(source.contains("parts.take(if (compact) 1 else 2)"))
    }

    @Test
    fun hourlyComplicationsUseEqualUnboxedWatchColumns() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        (1..4).forEach { index ->
            val xml = File(layoutDir, "widget_hour_$index.xml").readText()
            assertTrue(xml.contains("@+id/h${index}_cell"))
            assertTrue(xml.contains("android:layout_weight=\"1\""))
            assertTrue("Hourly cells must not carry unexplained decorative ticks", !xml.contains("widget_hour_tick"))
            assertTrue("Hourly columns must not look like calculator keys", !xml.contains("widget_hour_cell"))
        }
    }

    @Test
    fun largeWidgetCanRenderSevenDatedForecastRows() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        val large = File(layoutDir, "widget_large.xml").readText()
        assertTrue("Seven-day area must consume the remaining 4x4 height", large.contains("android:layout_height=\"0dp\" android:layout_weight=\"1\" android:orientation=\"vertical\""))
        (1..7).forEach { index ->
            assertTrue("Large widget is missing forecast row $index", large.contains("@layout/widget_day_$index"))
            val dayFile = File(layoutDir, "widget_day_$index.xml")
            assertTrue(dayFile.isFile)
            assertTrue("Forecast row $index must share the remaining height", dayFile.readText().contains("android:layout_weight=\"1\""))
            assertTrue("Forecast row $index must visualize its real temperature span", dayFile.readText().contains("@+id/d${index}_b"))
        }

        val builder = sequenceOf(
            File("app/src/main/kotlin/com/zhisheng/weather/ui/WidgetSnapshotBuilder.kt"),
            File("src/main/kotlin/com/zhisheng/weather/ui/WidgetSnapshotBuilder.kt"),
        ).first { it.isFile }.readText()
        assertTrue(builder.contains("currentAndFutureDaily(nowMillis).take(7)"))
        assertTrue(builder.contains("drop(upcomingStart).take(6)"))
        assertTrue(builder.contains("Fmt.dayOfMonth"))
    }

    @Test
    fun largeWidgetUsesItsMiddleForRealComplicationsAndActions() {
        val root = sequenceOf(File("app/src/main"), File("src/main")).first { it.isDirectory }
        val large = File(root, "res/layout/widget_large.xml").readText()
        listOf(
            "@+id/w_trend_section",
            "@+id/w_temp_trend",
            "@+id/w_life_section",
            "@+id/w_refresh",
        ).forEach { id -> assertTrue("Large widget is missing $id", large.contains(id)) }
        assertTrue("Large widget should not duplicate medium hourly cards", !large.contains("@layout/widget_hour_"))

        val provider = File(
            root,
            "kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt",
        ).readText()
        assertTrue(provider.contains("setImageViewBitmap(R.id.w_temp_trend"))
        assertTrue(provider.contains("dailyRangeBitmap("))
        assertTrue(provider.contains("WidgetSyncWorker.refreshNow(context)"))
        assertTrue(provider.contains("PendingIntent.getBroadcast"))
        assertTrue(provider.contains("setLocalizedTextViewText(R.id.w_refresh, \"…\")"))
        assertTrue(provider.contains("setLocalizedTextViewText(R.id.w_upd, context.getString(R.string.widget_refreshing))"))
        assertTrue(provider.contains("delay(REFRESH_PAINT_DELAY_MS)"))
        assertTrue(provider.contains("delay(REFRESH_FALLBACK_DELAY_MS)"))
    }

    @Test
    fun everyWidgetOffersAReadableRefreshTarget() {
        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        listOf("nano", "small", "medium", "tower", "large").forEach { size ->
            val xml = File(layoutDir, "widget_$size.xml").readText()
            assertTrue("widget_$size must expose refresh", xml.contains("@+id/w_refresh"))
            assertTrue("widget_$size refresh must have a 44dp touch axis", xml.contains("android:layout_width=\"44dp\""))
            assertTrue("widget_$size refresh must have a 44dp touch height", xml.contains("android:layout_height=\"44dp\""))
            assertTrue("widget_$size refresh needs an accessibility label", xml.contains("@string/widget_refresh_desc"))
        }
    }

    @Test
    fun everyWidgetProviderHasLauncherCompatiblePreviewImage() {
        val resDir = sequenceOf(
            File("app/src/main/res"),
            File("src/main/res"),
        ).first { it.isDirectory }

        listOf("small", "medium", "large").forEach { size ->
            val provider = File(resDir, "xml/widget_info_$size.xml").readText()
            assertTrue(
                "widget_info_$size.xml must declare a static previewImage",
                provider.contains("android:previewImage=\"@drawable/widget_preview_$size\""),
            )
            assertTrue(
                "widget_preview_$size.png is missing",
                File(resDir, "drawable-nodpi/widget_preview_$size.png").isFile,
            )
        }
        listOf("nano", "tower").forEach { size ->
            val provider = File(resDir, "xml/widget_info_$size.xml").readText()
            assertTrue(provider.contains("android:previewLayout=\"@layout/widget_$size\""))
            assertTrue(File(resDir, "layout/widget_$size.xml").isFile)
        }
    }

    @Test
    fun widgetDetailsAreSizedPerLayoutInsteadOfBeingEllipsized() {
        val source = sequenceOf(
            File("app/src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
            File("src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
        ).first { it.isFile }.readText()

        assertTrue(source.contains("spacious && snap.windText.isNotBlank()"))
        // v0.0.4：2x2 主动舍弃更新时间——布局不再保留恒 GONE 的 w_upd 占位，
        // Provider 仅对非 small 档位写 w_upd（原断言检查 GONE 分支，随死控件移除更新）
        assertTrue(source.contains("if (hasUpdate) v.setViewVisibility(R.id.w_upd, View.VISIBLE)"))
        assertTrue(source.contains("snap.rainChance?.let { add(\"降水 ${'$'}it%\") }"))
        assertTrue("small and medium details must not include wind", !source.contains("layout != R.layout.widget_small && snap.windText"))

        val layoutDir = sequenceOf(
            File("app/src/main/res/layout"),
            File("src/main/res/layout"),
        ).first { it.isDirectory }
        val smallXml = File(layoutDir, "widget_small.xml").readText()
        assertTrue("widget_small must not carry the hidden w_upd placeholder", !smallXml.contains("@+id/w_upd"))
    }

    @Test
    fun widgetFamilyHasFiveHardwareFormFactorsAndOneLightIconTone() {
        val source = sequenceOf(
            File("app/src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
            File("src/main/kotlin/com/zhisheng/weather/widget/ZhishengWidgetProvider.kt"),
        ).first { it.isFile }.readText()
        val manifest = sequenceOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
        ).first { it.isFile }.readText()

        listOf("Small", "Medium", "Large", "Nano", "Tower").forEach { size ->
            assertTrue("Missing $size widget provider", manifest.contains("ZhishengWidget$size"))
        }
        assertTrue(source.contains("context.getColor(R.color.widget_light_accent_cyan)"))
        assertTrue("Light widget icons must not mix semantic gray/orange/cyan tones", !source.contains("widget_light_icon_sun"))
        assertTrue("Light widget icons must not mix semantic gray/orange/cyan tones", !source.contains("widget_light_icon_cloud"))
        assertTrue(source.contains("setColorFilter"))
        assertTrue(source.contains("\"CAIYUN\" -> \"彩云\""))
    }
}
