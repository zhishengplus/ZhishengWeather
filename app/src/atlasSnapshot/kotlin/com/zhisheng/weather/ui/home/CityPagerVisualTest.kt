package com.zhisheng.weather.ui.home

import android.os.SystemClock
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.*
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

/** Uses the production pager, header, page and chart boundary; no ViewModel/network/device data. */
class CityPagerVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val cities = listOf("金川区·金川西路", "成都市", "上海市", "北京市", "兰州市", "镇原县", "天津市", "南京市", "杭州市")
        .mapIndexed { i, name -> City(name, "原生离线夹具", 30.0 + i, 105.0, "fixture-$i") }

    @Test fun realHeadersFitBothSkinsAndLargeTypeWithAccessibleNavigation() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false))
            for ((width, height, font) in listOf(Triple(360, 700, 1f), Triple(320, 620, 2f), Triple(640, 360, 2f))) {
                capture("city_header_${style.key}_${light}_${width}_$font", width, height, font, light, style,
                    content = {
                        CityWeatherPager(cities, cities[0].locationKey, true, {}, Modifier.fillMaxSize()) { city, pager ->
                            HomeCityPage(city, null, null, HomeUiState(cities = cities), true, null, 0,
                                {}, {}, {}, {}, {}, { CityPageIndicator(cities, city, pager) }, {}, {}, {}, {}, {}, {})
                        }
                    }, check = { _, root, _ ->
                        val indicator = nodes(root).first { it.config.getOrNull(SemanticsProperties.ContentDescription)
                            ?.contains("城市，第1页，共9页") == true }
                        fits(root, indicator)
                        assertEquals(listOf("下一个城市"), indicator.config[SemanticsActions.CustomActions].map { it.label })
                        for (node in nodes(root)) {
                            val layouts = mutableListOf<TextLayoutResult>()
                            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                            layouts.forEach { layout ->
                                // Check actual measured line bounds, retaining the established 480dpi
                                // fixture's one-pixel tolerance after converting this host to 160dpi.
                                val tolerance = 1f / 3f
                                assertFalse("$style/$width/font$font: ${layout.layoutInput.text}; size=${layout.size}; " +
                                    "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}; " +
                                    "lines=${(0 until layout.lineCount).map { listOf(layout.getLineLeft(it), layout.getLineRight(it), layout.getLineBottom(it)) }}",
                                    layout.didOverflowHeight || (0 until layout.lineCount).any {
                                        layout.getLineLeft(it) < -tolerance || layout.getLineRight(it) > layout.size.width + tolerance ||
                                            layout.getLineBottom(it) > layout.size.height + tolerance
                                    })
                            }
                        }
                        for (label in listOf("城市列表", "刷新", "设置")) {
                            val node = nodes(root).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
                            fits(root, node)
                        }
                        true
                    })
            }
    }

    @Test fun nativeFingerDragFollowsBeforeReleaseAndCommitsOnlyAfterStopping() {
        var pager: PagerState? = null
        val selected = mutableStateOf(cities[0].locationKey)
        val requests = mutableListOf<String>()
        var phase = 0
        var releasedAt = 0L
        var sawTwoPages = false
        capture("city_native_follow_release", content = {
            CityWeatherPager(cities.take(3), selected.value, true, { requests += it; selected.value = it }, Modifier.fillMaxSize()) { city, state ->
                pager = state
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(city.name, color = ZhishengText)
                    Text(if (city == cities[0]) "10°" else "21°", color = ZhishengText)
                    CityPageIndicator(cities.take(3), city, state)
                }
            }
        }, duration = 3_000, check = { host, root, now ->
            when {
                phase == 0 -> { touch(host, MotionEvent.ACTION_DOWN, 300f, 180f, now); phase++; false }
                phase in 1..10 -> {
                    touch(host, MotionEvent.ACTION_MOVE, 300f - phase * 24f, 180f, now)
                    assertTrue("Dragging must not select/fetch a city", requests.isEmpty())
                    if (phase >= 5) {
                        assertTrue("Native page geometry must actually follow the held finger", pager!!.currentPageOffsetFraction != 0f)
                        val labels = nodes(root).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                        if ("10°" in labels && "21°" in labels) sawTwoPages = true
                    }
                    phase++; false
                }
                phase == 11 -> { touch(host, MotionEvent.ACTION_UP, 60f, 180f, now); releasedAt = now; phase++; false }
                now - releasedAt > 700 -> {
                    assertTrue("Adjacent real city content must be composed during the drag", sawTwoPages)
                    assertEquals("Release now=$now, releasedAt=$releasedAt; ${pagerState(pager)}",
                        listOf(cities[1].locationKey), requests)
                    assertEquals(1, pager!!.settledPage)
                    assertEquals(0f, pager!!.currentPageOffsetFraction, .001f)
                    true
                }
                else -> false
            }
        })
    }

    @Test fun exhaustedHorizontalChartDoesNotHandItsDragOrFlingToThePager() {
        var pager: PagerState? = null
        var phase = 0
        var releasedAt = 0L
        var chartPosition = 0
        val requests = mutableListOf<String>()
        capture("city_chart_boundary", content = {
            CityWeatherPager(cities.take(3), cities[1].locationKey, true, { requests += it }, Modifier.fillMaxSize()) { _, state ->
                pager = state
                val chart = rememberScrollState()
                chartPosition = chart.value
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(100.dp))
                    Row(Modifier.height(100.dp).cityChartGestures().horizontalScroll(chart)) {
                        repeat(12) { Text("${it}时", Modifier.width(80.dp), color = ZhishengText) }
                    }
                }
            }
        }, duration = 3_000, check = { host, _, now ->
            when {
                phase == 0 -> { touch(host, MotionEvent.ACTION_DOWN, 100f, 140f, now); phase++; false }
                phase in 1..8 -> { touch(host, MotionEvent.ACTION_MOVE, 100f + phase * 25f, 140f, now); phase++; false }
                phase == 9 -> { touch(host, MotionEvent.ACTION_UP, 300f, 140f, now); releasedAt = now; phase++; false }
                now - releasedAt > 650 -> {
                    assertEquals(0, chartPosition)
                    assertEquals(1, pager!!.settledPage)
                    assertEquals(0f, pager!!.currentPageOffsetFraction, .001f)
                    assertTrue(requests.isEmpty()); true
                }
                else -> false
            }
        })
    }

    @Test fun aHorizontalSwipeCanTakeOverAnAlreadyFlingingVerticalList() {
        var pager: PagerState? = null
        val lists = mutableMapOf<String, LazyListState>()
        val selected = mutableStateOf(cities[1].locationKey)
        val requests = mutableListOf<String>()
        var phase = 0
        var releasedAt = 0L
        var startVerticalFling: () -> Unit = {}
        var flingStartedAt = 0L
        capture("city_cross_axis_inertia", duration = 3_000, content = {
            CityWeatherPager(cities.take(3), selected.value, true, { requests += it; selected.value = it }, Modifier.fillMaxSize()) { city, state ->
                pager = state
                val list = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val fling = ScrollableDefaults.flingBehavior()
                lists[city.locationKey] = list
                if (city == cities[1]) startVerticalFling = {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { list.scroll { with(fling) { performFling(4_000f) } } }
                }
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    items(30) { Text("${city.name} 第${it}行", Modifier.fillMaxWidth().height(90.dp), color = ZhishengText) }
                }
            }
        }, check = { host, _, now ->
            when {
                // Seed a genuine Foundation decay fling at a controlled velocity. The following
                // horizontal gesture still travels through native MotionEvent dispatch.
                phase == 0 -> { flingStartedAt = now; startVerticalFling(); phase++; false }
                phase == 1 && now == flingStartedAt -> false
                phase == 1 -> {
                    assertTrue("The fixture must actually have vertical inertia before the horizontal touch", lists[cities[1].locationKey]!!.isScrollInProgress)
                    assertTrue("The real decay fling must have moved the vertical list before the next touch; " +
                        "now=$now, startedAt=$flingStartedAt; ${pagerState(pager)}; " +
                        "list=${lists[cities[1].locationKey]!!.firstVisibleItemIndex}:" +
                        "${lists[cities[1].locationKey]!!.firstVisibleItemScrollOffset}",
                        lists[cities[1].locationKey]!!.firstVisibleItemIndex > 0 || lists[cities[1].locationKey]!!.firstVisibleItemScrollOffset > 0)
                    touch(host, MotionEvent.ACTION_DOWN, 50f, 300f, now); phase++; false
                }
                phase in 2..10 -> { touch(host, MotionEvent.ACTION_MOVE, 50f + (phase - 1) * 28f, 300f, now); phase++; false }
                phase == 11 -> { touch(host, MotionEvent.ACTION_UP, 302f, 300f, now); releasedAt = now; phase++; false }
                now - releasedAt > 700 -> {
                    assertEquals("The same horizontal gesture must switch cities without a second attempt", 0, pager!!.settledPage)
                    assertEquals(listOf(cities[0].locationKey), requests); true
                }
                else -> false
            }
        })
    }

    @Test fun horizontalDragKeepsGlassSamplesStableWhileVerticalScrollStillMovesThem() {
        val rootSamples = mutableListOf<Offset>()
        val pageSamples = mutableListOf<Offset>()
        var list: LazyListState? = null
        var pager: PagerState? = null
        val requests = mutableListOf<String>()
        var verticalScroll: () -> Unit = {}
        var phase = 0
        var releasedAt = 0L
        var baselineY = 0f
        capture("city_stable_glass_samples", duration = 3_000, content = {
            CityWeatherPager(cities.take(3), cities[0].locationKey, true, { requests += it }, Modifier.padding(start = 12.dp).fillMaxSize()) { city, state ->
                pager = state
                val coordinates = LocalSurfacePageCoordinates.current!!
                val scroll = rememberLazyListState()
                val scope = rememberCoroutineScope()
                if (city == cities[0]) {
                    list = scroll
                    verticalScroll = { scope.launch { scroll.scrollToItem(0, 40) } }
                }
                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize()) {
                    items(20) { row ->
                        Box(Modifier.fillMaxWidth().height(100.dp).onGloballyPositioned {
                            if (city == cities[0] && row == 0) {
                                rootSamples += it.positionInRoot()
                                pageSamples += coordinates.positionOf(it)
                            }
                        }.zhishengPanel()) { Text("${city.name} / $row", color = ZhishengText) }
                    }
                }
            }
        }, check = { host, _, now ->
            when {
                phase == 0 -> {
                    rootSamples.clear(); pageSamples.clear()
                    touch(host, MotionEvent.ACTION_DOWN, 280f, 250f, now); phase++; false
                }
                phase in 1..8 -> {
                    touch(host, MotionEvent.ACTION_MOVE, 280f - phase * 10f, 250f, now)
                    phase++; false
                }
                phase == 9 -> {
                    touch(host, MotionEvent.ACTION_UP, 200f, 250f, now)
                    releasedAt = now; phase++; false
                }
                phase == 10 && now - releasedAt > 700 -> {
                    assertEquals("A slow short drag must return to the same city", 0, pager!!.settledPage)
                    assertEquals("Short drag now=$now, releasedAt=$releasedAt; ${pagerState(pager)}",
                        0f, pager!!.currentPageOffsetFraction, .001f)
                    assertTrue("A cancelled short drag must not fetch another city", requests.isEmpty())
                    assertTrue("The native drag must move the page across several frames", rootSamples.map { it.x }.distinct().size >= 5)
                    assertTrue("Horizontal motion must not invalidate every glass sample", pageSamples.map { it.x }.distinct().size <= 1)
                    assertEquals("A viewport inset must remain in the sample coordinate", 12f, pageSamples.last().x, .01f)
                    baselineY = pageSamples.last().y
                    verticalScroll(); phase++; false
                }
                phase == 11 && list!!.firstVisibleItemScrollOffset == 40 -> {
                    assertEquals("Vertical motion must still update the glass sample", baselineY - 40f, pageSamples.last().y, .01f)
                    true
                }
                else -> false
            }
        })
    }

    private var downTime = 0L
    private fun pagerState(pager: PagerState?): String = pager?.let {
        "pager(current=${it.currentPage}, settled=${it.settledPage}, target=${it.targetPage}, " +
            "fraction=${it.currentPageOffsetFraction}, scrolling=${it.isScrollInProgress})"
    } ?: "pager=null"
    private fun touch(host: View, action: Int, x: Float, y: Float, time: Long) {
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        try { host.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun capture(name: String, width: Int = 360, height: Int = 640, font: Float = 1f,
        light: Boolean = true, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
        content: @Composable () -> Unit, duration: Long = 500,
        check: (View, SemanticsNode, Long) -> Boolean) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width, screenHeight = height,
            density = Density.MEDIUM, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        var failure: Throwable? = null
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                    Box(Modifier.fillMaxSize().background(ZhishengBg)) { content() }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view -> view.javaClass.methods
                            .firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }?.invoke(view) as? SemanticsOwner }
                            ?: error("Missing Compose semantics")
                        try { checked = check(this@apply, owner.unmergedRootSemanticsNode, SystemClock.uptimeMillis()) }
                        catch (t: Throwable) { failure = t; checked = true }
                    }
                    return true
                }
            })
        }
        paparazzi.gif(host, name, end = duration, fps = if (duration > 500) 12 else 2)
        failure?.let { throw AssertionError("Native scenario $name", it) }
        assertTrue("Native scenario did not finish: $name", checked)
    }
    private fun fits(root: SemanticsNode, node: SemanticsNode) {
        val viewport = root.boundsInRoot; val bounds = node.boundsInRoot
        assertTrue("Native control must remain fully visible: $bounds in $viewport", bounds.top >= viewport.top - 1f/3 &&
            bounds.left >= viewport.left - 1f/3 && bounds.right <= viewport.right + 1f/3 && bounds.bottom <= viewport.bottom + 1f/3 &&
            bounds.height + 1f/3 >= node.size.height && bounds.width + 1f/3 >= node.size.width)
    }
    private fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::nodes)
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
}
