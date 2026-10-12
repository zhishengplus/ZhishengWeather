package com.zhisheng.weather.widget

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.R
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the editor's actual layout, semantics actions and saved configuration. */
class WidgetStudioCompletionTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test fun narrowLargeTypeClickChoicesStayCompleteAndSaveTheSelectedAction() {
        for (style in WidgetStyle.entries) for (editing in listOf(false, true)) {
            // A new window/session owns every dynamic case, including its recomposer and native view clocks.
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                screenWidth = 960, screenHeight = 1851, fontScale = 1.5f,
                orientation = ScreenOrientation.PORTRAIT))
            var saved: WidgetConfig? = null
            val host = editor(style, editing, WidgetKind.WEEK) { saved = it }
            var checked = false
            var tabRequested = false
            var headingsChecked = false
            var finalLabels = emptyList<String>()
            runDuringRender(host, listOf(
                waitForClickTab@ { root ->
                    finalLabels = nodes(root).mapNotNull(::text)
                    if (!headingsChecked) {
                        for (label in listOf(style.title, WidgetKind.WEEK.name(style))) {
                            val heading = nodes(root).firstOrNull { text(it) == label }
                                ?: error("Missing complete compact heading: $label")
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue(heading.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
                            assertFalse("$style/$editing compact title is clipped at 617dp height: $label", layouts.single().hasVisualOverflow)
                            assertTrue("$label is outside the visible editor", heading.boundsInRoot.height > 0f)
                        }
                        val body = nodes(root).single { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
                        assertTrue("Compact editor leaves too little room to use the controls",
                            body.boundsInRoot.height >= 160f * paparazzi.context.resources.displayMetrics.density)
                        headingsChecked = true
                    }
                    if (nodes(root).any { text(it) == "系统时钟" }) return@waitForClickTab true
                    if (!tabRequested) {
                        click(root, "点击")
                        tabRequested = true
                    }
                    false
                },
                checkChoices@ { root ->
                    if (nodes(root).none { text(it) == "系统时钟" }) return@checkChoices false
                    for (label in listOf("系统时钟", "系统日历", "编辑组件", "无操作", "天气主页")) {
                        val texts = nodes(root).filter { text(it) == label }
                        assertTrue("Missing $label", texts.isNotEmpty())
                        texts.forEach { node ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue("No layout for $label", node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
                            val layout = layouts.single()
                            val target = clickable(node)
                            val geometry = "$style / $label: overflowWidth=${layout.didOverflowWidth}, overflowHeight=${layout.didOverflowHeight}, " +
                                "textSize=${layout.size}, lines=${layout.lineCount}, targetSize=${target.size}, " +
                                "paragraphWidth=${layout.multiParagraph.width}, intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth}, " +
                                "lineRight=${layout.getLineRight(0)}, constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}"
                            println(geometry)
                            assertFalse("Clipped at 320dp with 150% type: $geometry", layout.hasVisualOverflow)
                            val density = paparazzi.context.resources.displayMetrics.density
                            assertTrue("$label has a short touch target", target.size.height >= 48f * density - 1f)
                        }
                    }
                    // The first editor action belongs to time; the date and weather actions stay unchanged.
                    val firstEdit = clickable(nodes(root).first { text(it) == "编辑组件" })
                    assertTrue("The first action must actually be reachable in the 617dp viewport",
                        firstEdit.boundsInRoot.height >= 48f * paparazzi.context.resources.displayMetrics.density - 1f)
                    click(root, "编辑组件")
                    checked = true
                    true
                },
                saveSelectedAction@ { root ->
                    val selectedTimeAction = nodes(root).first { text(it) == "编辑组件" }
                    if (clickable(selectedTimeAction).config.getOrNull(SemanticsProperties.Selected) != true)
                        return@saveSelectedAction false
                    click(root, if (editing) "保存修改" else "添加到桌面")
                    true
                },
            ))
            // Multiple native frames are needed for a tab click to recompose before inspecting its content.
            paparazzi.gif(host, "studio_click_choices_${style.key}_${editing}_320x617dp_150", end = 2_000, fps = 8)
            assertTrue("$style click tab never finished recomposing; choice layout was not assessed; last labels=$finalLabels", checked)
            assertEquals("settings", saved?.clickTime)
            assertEquals("calendar", saved?.clickDate)
            assertEquals("app", saved?.clickWeather)
        }
    }

    @Test fun previewContentsKeepWallpaperAndDoneReachableOnShortLargeTypeScreens() {
        for (style in WidgetStyle.entries) for (allKinds in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                screenWidth = 960, screenHeight = 960, fontScale = 1.5f, orientation = ScreenOrientation.LANDSCAPE))
            val wallpaper = mutableIntStateOf(1)
            var doneCount = 0
            var selection: WidgetKind? = null
            var checked = false
            var scrolled = false
            var finalScroll = 0f
            var initialChecked = false
            var wallpaperRequested = false
            var finalDescriptions = emptyList<String>()
            var requestedScroll = 0f
            val sample = widgetPreviewSample()
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = style == WidgetStyle.VISTA,
                        homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS, softGlow = false) {
                        WidgetPreviewDialogContent(style, WidgetKind.SCENE,
                            WidgetConfig.defaults(style).copy(opacity = 0, protectText = false), sample,
                            wallpaper.intValue, allKinds, null, { wallpaper.intValue = 2 }, { selection = it },
                            { doneCount++ }, previewLabel = "示例天气",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
                    }
                } }
            }
            runDuringRender(host, listOf(
                waitForWallpaper@ { root ->
                    finalDescriptions = nodes(root).flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                    if (nodes(root).any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description -> "壁纸3" in description } == true })
                        return@waitForWallpaper true
                    if (!initialChecked) {
                        val doneNode = nodes(root).single { text(it) == "完成" }
                        val doneTarget = clickable(doneNode)
                        assertTrue("Complete action left the short viewport", doneTarget.boundsInRoot.top >= 0f &&
                            doneTarget.boundsInRoot.bottom <= 300f * paparazzi.context.resources.displayMetrics.density + 1f)
                        val cities = descendants(host).filterIsInstance<TextView>().filter { it.id == R.id.widget_city && it.visibility == View.VISIBLE }
                        assertEquals("Preview must bind all requested real widgets", if (allKinds) 5 else 1, cities.size)
                        assertTrue("Dark wallpaper was lost by the native preview", cities.all { ColorUtils.calculateLuminance(it.currentTextColor) > .5 })
                        initialChecked = true
                    }
                    if (!wallpaperRequested) {
                        click(root, "试壁纸")
                        wallpaperRequested = true
                        assertEquals("Wallpaper action used the wrong semantics ancestor", 2, wallpaper.intValue)
                        println("$style/allKinds=$allKinds: actual wallpaper state=${wallpaper.intValue}, old tree=$finalDescriptions")
                    }
                    false
                },
                afterWallpaper@ { root ->
                    if (nodes(root).none { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description -> "壁纸3" in description } == true })
                        return@afterWallpaper false
                    val cities = descendants(host).filterIsInstance<TextView>().filter { it.id == R.id.widget_city && it.visibility == View.VISIBLE }
                    assertTrue("Trying a light wallpaper did not update real widget text", cities.isNotEmpty() &&
                        cities.all { ColorUtils.calculateLuminance(it.currentTextColor) < .3 })
                    val scrollNode = nodes(root).single { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
                    val scroll = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
                    assertTrue("Short preview needs an actual scrollable area", scroll.maxValue() > 0f)
                    click(root, "完成")
                    assertEquals("Complete callback before scrolling used a wrong semantics ancestor", 1, doneCount)
                    println("$style/allKinds=$allKinds: request scroll from ${scroll.value()}, max=${scroll.maxValue()}")
                    requestedScroll = minOf(scroll.maxValue(), 140f * paparazzi.context.resources.displayMetrics.density)
                    assertTrue(scrollNode.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 140f * paparazzi.context.resources.displayMetrics.density) == true)
                    Snapshot.sendApplyNotifications()
                    checked = true
                    true
                },
                afterScroll@ { root ->
                    val scrollNode = nodes(root).single { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
                    finalScroll = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange].value()
                    println("$style/allKinds=$allKinds: rendered scroll=$finalScroll")
                    if (finalScroll < requestedScroll - 1f) return@afterScroll false
                    if (allKinds) {
                        val item = nodes(root).single { text(it) == "${WidgetKind.NOW.name(style)} · 2 × 2" }
                        assertTrue("Style selection remains outside the viewport after scrolling", item.boundsInRoot.height > 0f)
                        click(root, "${WidgetKind.NOW.name(style)} · 2 × 2")
                    }
                    val doneNode = nodes(root).single { text(it) == "完成" }
                    assertTrue("Scrolling moved the fixed complete action", clickable(doneNode).boundsInRoot.top >= 0f)
                    click(root, "完成")
                    scrolled = true
                    true
                },
            ))
            paparazzi.gif(host, "studio_preview_${style.key}_${allKinds}_short_150", end = 2_000, fps = 8)
            assertTrue("$style/allKinds=$allKinds preview did not recompose: actual state=${wallpaper.intValue}, tree=$finalDescriptions", checked)
            assertTrue("Scroll action did not settle in native frames: $style/allKinds=$allKinds/value=$finalScroll", scrolled)
            assertEquals("Complete callback after scrolling used a wrong semantics ancestor", 2, doneCount)
            if (allKinds) assertEquals(WidgetKind.NOW, selection)
        }
    }

    @Test fun hostSizeNoticeMatchesTheSectionsHiddenByTheFormalBinder() {
        val sample = widgetPreviewSample()
        for (style in WidgetStyle.entries) for (height in listOf(170, 180, 249, 250, 279, 280)) {
            val config = WidgetConfig.defaults(style)
            val frame = FrameLayout(paparazzi.context)
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.SCENE),
                config, sample, 344, height, interactive = false).apply(paparazzi.context, frame)
            val notice = widgetEditorSizeNotice(WidgetKind.SCENE, SizeF(344f, height.toFloat()), config)
            assertEquals("Weather hint notice disagrees with real visibility at $height dp",
                view.findViewById<View>(R.id.widget_scene_briefing).visibility == View.GONE, notice?.contains("天气提示") == true)
            assertEquals("Metrics notice disagrees with real visibility at $height dp",
                view.findViewById<View>(R.id.widget_metrics).visibility == View.GONE, notice?.contains("天气读数") == true)
            assertEquals("Forecast notice disagrees with real visibility at $height dp",
                view.findViewById<View>(R.id.widget_forecast).visibility == View.GONE, notice?.contains("天气预报") == true)
            val rainConfig = config.copy(showPrecipitation = true)
            val rainView = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.SCENE),
                rainConfig, sample, 344, height, interactive = false).apply(paparazzi.context, frame)
            assertEquals("Rain notice disagrees with real visibility at $height dp",
                rainView.findViewById<View>(R.id.widget_precip_section).visibility == View.GONE,
                widgetEditorSizeNotice(WidgetKind.SCENE, SizeF(344f, height.toFloat()), rainConfig)?.contains("短时降水柱图") == true)
        }
        assertNull(widgetEditorSizeNotice(WidgetKind.SCENE, null, WidgetConfig()))
        assertNull(widgetEditorSizeNotice(WidgetKind.WEEK, SizeF(344f, 170f), WidgetConfig()))
    }

    @Test fun hostWidthAndCompactHeightNoticeMatchActualRangeAndHourlyVisibility() {
        val sample = widgetPreviewSample()
        for (style in WidgetStyle.entries) for (width in listOf(239, 240)) for (height in listOf(439, 440)) {
            val config = WidgetConfig.defaults(style)
            val frame = FrameLayout(paparazzi.context)
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.SCENE),
                config, sample, width, height, interactive = false).apply(paparazzi.context, frame)
            val notice = widgetEditorSizeNotice(WidgetKind.SCENE, SizeF(width.toFloat(), height.toFloat()), config)
            assertEquals("Range notice disagrees with real visibility at ${width}x${height}",
                view.findViewById<View>(R.id.widget_range).visibility == View.GONE, notice?.contains("今日温差") == true)
            assertEquals("Hourly notice disagrees with real layout at ${width}x${height}",
                view.findViewById<View>(R.id.widget_hourly_forecast)?.visibility != View.VISIBLE, notice?.contains("逐时预报") == true)
            if (width == 239) assertTrue("Narrow hosts need a usable width action", notice?.contains("拉宽") == true)
        }
        val forecastOff = WidgetConfig(showForecast = false)
        assertNull(widgetEditorSizeNotice(WidgetKind.SCENE, SizeF(240f, 439f), forecastOff))
        assertTrue(widgetEditorSizeNotice(WidgetKind.SCENE, SizeF(239f, 439f), forecastOff)?.contains("拉宽") == true)
    }

    private fun editor(style: WidgetStyle, editing: Boolean, kind: WidgetKind, save: (WidgetConfig) -> Unit): ComposeView {
        val sample = widgetPreviewSample()
        return ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                WidgetStudio(style, if (editing) 1 else null, kind, WidgetConfig.defaults(style),
                    listOf(sample.city), sample.city, null, NotificationSettings(), {}, {}, { _, config -> save(config) }, sample)
            } }
        }
    }

    private fun runDuringRender(host: ComposeView, steps: List<(SemanticsNode) -> Boolean>) {
        var next = 0
        host.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (next < steps.size) {
                    val owner = descendants(host).firstNotNullOfOrNull { view ->
                        view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                            ?.invoke(view) as? SemanticsOwner
                    } ?: error("Production Compose semantics owner was not created")
                    if (steps[next](owner.unmergedRootSemanticsNode)) next++
                    // Drawing-time writes are outside a Compose UI event loop. Publish real snapshot changes
                    // before Paparazzi advances the next Handler/Choreographer frame.
                    Snapshot.sendApplyNotifications()
                    host.postInvalidateOnAnimation()
                }
                return true
            }
        })
    }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun clickable(node: SemanticsNode): SemanticsNode =
        if (node.config.getOrNull(SemanticsActions.OnClick) != null) node else node.parent?.let(::clickable)
            ?: error("No production click action for ${text(node)}")
    private fun click(root: SemanticsNode, label: String) {
        val node = nodes(root).firstOrNull { text(it) == label } ?: error("Missing production action: $label")
        val target = clickable(node)
        println("Actual action $label: textNode=${node.id}, target=${target.id}, selected=${target.config.getOrNull(SemanticsProperties.Selected)}")
        assertTrue("Action did not accept click: $label", target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
        Snapshot.sendApplyNotifications()
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
