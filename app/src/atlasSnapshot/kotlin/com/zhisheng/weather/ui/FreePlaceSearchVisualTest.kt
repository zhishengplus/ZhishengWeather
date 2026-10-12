package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.PlaceFailure
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the production panel and its actual measured text and callback semantics. */
class FreePlaceSearchVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun liveInputSurvivesRapidEditsAndCancelledOnlineRequests() {
        for (style in InterfaceStyle.entries) {
            var phase = 0
            var onlineStarted = false
            var cancelled = false
            val city = City("金川区", "甘肃省·金昌市", 38.5, 102.2, "geo:local:1", coordinateKind = CoordinateKind.ADMINISTRATIVE)
            val other = city.copy(name = "兰州市", locationKey = "geo:local:2")
            capture("free_search_live_${style.key}", style, true, 360, 680, 1f,
                SearchUiState(), {}, {}, duration = 3_000L, content = {
                    LiveSearchScreen({}, {}, localSearch = { if (it == "jinchuan") listOf(city) else listOf(other) },
                        onlineSearch = { _, _ ->
                            onlineStarted = true
                            try { kotlinx.coroutines.delay(10_000); com.zhisheng.weather.data.PlaceSearchOutcome(listOf(city)) }
                            finally { cancelled = true }
                        }, ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)
                }) { root ->
                val field = nodes(root).first { it.config.getOrNull(SemanticsActions.SetText) != null }
                val value = field.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
                when (phase) {
                    0 -> {
                        for (text in listOf("j", "ji", "jin", "jinchuan")) {
                            assertTrue(field.config[SemanticsActions.SetText].action?.invoke(androidx.compose.ui.text.AnnotatedString(text)) == true)
                        }
                        phase = 1; false
                    }
                    1 -> if (value == "jinchuan" && "金川区" in labels(root)) {
                        assertTrue(action(root, "联网搜索").config[SemanticsActions.OnClick].action?.invoke() == true)
                        phase = 2; false
                    } else false
                    2 -> if (onlineStarted) {
                        assertTrue(field.config[SemanticsActions.SetText].action?.invoke(androidx.compose.ui.text.AnnotatedString("lanzhou")) == true)
                        phase = 3; false
                    } else false
                    else -> if (value == "lanzhou" && "兰州市" in labels(root) && cancelled) {
                        assertFalse("Cancelled response cannot reintroduce old results", "金川区" in labels(root))
                        assertFalse("Cancelled progress cannot remain attached to a new query", "搜索中…" in labels(root))
                        assertTextWraps(root)
                        true
                    } else false
                }
            }
        }
    }

    @Test fun offlineResultsRemainSelectableDuringOnlineFailureAndProgress() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for ((width, height, font) in listOf(Triple(360, 680, 1f), Triple(320, 600, 2f))) {
                for (busy in listOf(false, true)) {
                    var selected: City? = null
                    var searches = 0
                    var retryChecked = busy
                    var scrollRequested = false
                    val city = City("金川区", "甘肃省·金昌市", 38.5, 102.2, "geo:local:geonames:CN:1",
                        coordinateKind = CoordinateKind.ADMINISTRATIVE)
                    val state = SearchUiState(query = "金川", results = listOf(city), searching = busy,
                        onlineAttempted = true, failure = if (busy) null else PlaceFailure.UNAVAILABLE)
                    capture("free_search_${style.key}_${light}_${width}_$busy", style, light, width, height, font,
                        state, { selected = it }, { searches++ }) { root ->
                        val labels = labels(root)
                        assertTrue("金川区" in labels)
                        assertTrue(labels.any { "区域位置" in it })
                        assertFalse("Representative points must not claim GPS precision", labels.any { "精确坐标" in it || "[精确]" in it })
                        if (!retryChecked) {
                            assertTrue(labels.any { "本地结果仍可选择" in it })
                            val retry = action(root, "重试")
                            assertActionFits(root, retry)
                            assertTrue(retry.config[SemanticsActions.OnClick].action?.invoke() == true)
                            assertEquals(1, searches)
                            retryChecked = true
                        }
                        val result = action(root, "金川区")
                        // Results belong to the scrollable list. Exercise real scrolling before checking full reachability.
                        if (!actionFits(root, result)) {
                            val scroll = nodes(root).firstOrNull { it.config.getOrNull(SemanticsActions.ScrollToIndex) != null }
                                ?: error("Missing results scroll action")
                            assertTrue("A result must fit its actual list viewport", result.size.height <= scroll.size.height)
                            // Dispatch once and wait for the next measured frame.
                            // Re-dispatching ScrollBy before each draw restarts its animation.
                            if (!scrollRequested) {
                                val viewport = scroll.boundsInRoot
                                val delta = (result.positionInRoot.y + result.size.height - viewport.bottom + 18f).coerceAtLeast(1f)
                                assertTrue(scroll.config[SemanticsActions.ScrollBy].action?.invoke(0f, delta) == true)
                                scrollRequested = true
                            }
                            return@capture false
                        }
                        assertActionFits(root, result)
                        assertTrue(result.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals(city, selected)
                        assertTextWraps(root)
                        true
                    }
                }
            }
        }
    }

    @Test fun switchingRegionCancelsOldOnlineResultsAndKeepsTheNewCountryVisible() {
        for (style in InterfaceStyle.entries) {
            var phase=0;var domesticStarted=false;var domesticCancelled=false
            val domestic=City("金川区","甘肃",38.5,102.2,"domestic",countryCode="CN")
            val overseas=City("伦敦","大伦敦",51.5,-.12,"overseas",countryCode="GB",countryName="英国")
            capture("region_switch_${style.key}",style,true,360,680,1f,SearchUiState(),{},{},duration=3_000L,content={
                LiveSearchScreen({},{},localSearch={listOf(domestic)},onlineSearch={_,_->error("Regional search must be used")},
                    ioDispatcher=kotlinx.coroutines.Dispatchers.Unconfined,regionalSearch={_,_,region->
                        if(region==com.zhisheng.weather.model.PlaceRegion.DOMESTIC) {
                            domesticStarted=true
                            try {kotlinx.coroutines.delay(10_000);com.zhisheng.weather.data.PlaceSearchOutcome(listOf(domestic))}
                            finally {domesticCancelled=true}
                        } else com.zhisheng.weather.data.PlaceSearchOutcome(listOf(overseas,domestic))
                    })
            }) { root ->
                when(phase) {
                    0 -> { val field=nodes(root).first {it.config.getOrNull(SemanticsActions.SetText)!=null}
                        field.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString("london"));phase=1;false }
                    1 -> if("金川区" in labels(root)) {action(root,"联网搜索").config[SemanticsActions.OnClick].action!!.invoke();phase=2;false} else false
                    2 -> if(domesticStarted) {action(root,"海外").config[SemanticsActions.OnClick].action!!.invoke();phase=3;false} else false
                    3 -> if(domesticCancelled && "联网搜索" in labels(root)) {action(root,"联网搜索").config[SemanticsActions.OnClick].action!!.invoke();phase=4;false} else false
                    else -> if("伦敦" in labels(root)) {
                        assertFalse("Domestic response cannot leak into overseas results","金川区" in labels(root))
                        assertTrue(labels(root).any {"英国" in it});assertTextWraps(root);true
                    } else false
                }
            }
        }
    }

    @Test fun personalAmapHintTracksEnabledStateAndRegionWithoutLosingLocalResults() {
        val domestic = com.zhisheng.weather.model.PlaceRegion.DOMESTIC
        val overseas = com.zhisheng.weather.model.PlaceRegion.OVERSEAS
        val city = City("金川区", "甘肃", 38.5, 102.2, "hint-city", countryCode = "CN")
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            var stage = 0
            val region = androidx.compose.runtime.mutableStateOf(domestic)
            val enabled = androidx.compose.runtime.mutableStateOf(true)
            capture("amap_hint_${style.key}_$light", style, light, 320, 600, 2f, SearchUiState(), {}, {},
                content = {
                    SearchScreenContent(SearchUiState(query = "金川区", results = if (region.value == domestic) listOf(city) else emptyList(), region = region.value),
                        {}, {}, {}, {}, {}, amapEnhanced = enabled.value)
                }) { root ->
                assertTextWraps(root)
                val hasHint = labels(root).any { it.startsWith("已接入你的高德 Key") }
                val renderedDomestic = labels(root).any { it.startsWith("国内 ·") }
                when (stage) {
                    0 -> { assertTrue(hasHint); region.value = overseas; stage = 1; false }
                    1 -> if (!renderedDomestic && !hasHint) { region.value = domestic; enabled.value = false; stage = 2; false } else false
                    2 -> if (renderedDomestic && !hasHint && "金川区" in labels(root)) { enabled.value = true; stage = 3; false } else false
                    else -> if (renderedDomestic && hasHint && "金川区" in labels(root)) true else false
                }
            }
        }
    }

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        font: Float, state: SearchUiState, onPick: (City) -> Unit, onSearch: () -> Unit,
        duration: Long = 1_500L, content: (@androidx.compose.runtime.Composable () -> Unit)? = null,
        check: (SemanticsNode) -> Boolean) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        var measurementFailure: Throwable? = null
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg), contentAlignment = Alignment.Center) {
                        if (content != null) content() else SearchScreenContent(state, onQueryChanged = {}, onScopeChanged = {},
                            onSearch = onSearch, onCityPicked = onPick, onBack = {})
                    }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        } ?: error("Missing production Compose semantics")
                        try { checked = check(owner.unmergedRootSemanticsNode) } catch (failure: Throwable) { measurementFailure = failure; checked = true }
                    }
                    if (!checked) this@apply.postInvalidateOnAnimation()
                    return true
                }
            })
        }
        paparazzi.gif(host, name, end = duration, fps = 4)
        measurementFailure?.let { throw AssertionError("Measured scenario: $name", it) }
        assertTrue("Production location actions must finish before disposal: $name", checked)
    }

    private fun actionFits(root: SemanticsNode, node: SemanticsNode): Boolean {
        val b = node.boundsInRoot
        val v = root.boundsInRoot
        return b.top >= v.top - 1f && b.bottom <= v.bottom + 1f && b.left >= v.left - 1f &&
            b.right <= v.right + 1f && b.height + 1f >= node.size.height && b.width + 1f >= node.size.width
    }
    private fun assertActionFits(root: SemanticsNode, node: SemanticsNode) {
        val bounds = node.boundsInRoot
        val viewport = root.boundsInRoot
        assertTrue("An action must retain a 48dp touch target", node.size.height >= 143)
        assertTrue("Action must remain completely reachable: bounds=$bounds viewport=$viewport size=${node.size}", bounds.top >= viewport.top - 1f &&
            bounds.bottom <= viewport.bottom + 1f && bounds.left >= viewport.left - 1f &&
            bounds.right <= viewport.right + 1f && bounds.height + 1f >= node.size.height &&
            bounds.width + 1f >= node.size.width)
    }
    private fun assertTextWraps(root: SemanticsNode) {
        nodes(root).forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                assertFalse("Text must wrap in its measured width: ${layout.layoutInput.text.text}; size=${layout.size}; overflow=${layout.didOverflowHeight}; lines=" +
                    (0 until layout.lineCount).joinToString { line -> "${layout.getLineLeft(line)},${layout.getLineRight(line)},${layout.getLineBottom(line)}" },
                    layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                        layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                            layout.getLineBottom(line) > layout.size.height + 1f
                    })
            }
        }
    }
    private fun labels(root: SemanticsNode) = nodes(root).flatMap {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
    }
    private fun action(root: SemanticsNode, label: String) = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && (label in labels(node) || nodes(node).any {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        })
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
