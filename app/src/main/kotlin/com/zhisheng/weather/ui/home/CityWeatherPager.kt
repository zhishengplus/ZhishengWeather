package com.zhisheng.weather.ui.home

import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.zhisheng.weather.ui.components.ProvideCityPageMotion
import com.zhisheng.weather.ui.components.cityPageActivity
import com.zhisheng.weather.ui.components.CityPageActivity

// A forecast strip owns its horizontal gesture all the way to its ends. Vertical motion
// still reaches the weather list and pull-to-refresh; exhausted horizontal motion stops here.
private val chartScrollBoundary = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}
internal fun Modifier.cityChartGestures() = nestedScroll(chartScrollBoundary)

/** One lazy, keyed page per city. Only a stopped page commits selection and starts a fetch. */
@Composable
internal fun CityWeatherPager(
    cities: List<City>,
    selectedKey: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPaging: (Boolean) -> Unit = {},
    onPagerReady: (PagerState) -> Unit = {},
    overlay: @Composable BoxScope.(PagerState) -> Unit = {},
    content: @Composable (City, PagerState) -> Unit,
) {
    val keys = cities.map { it.locationKey }
    val pager = rememberPagerState(initialPage = keys.indexOf(selectedKey).coerceAtLeast(0)) { cities.size }
    val selection = remember { CityPagerSelection() }
    val viewport = remember { SurfacePageCoordinates() }
    val currentCities by rememberUpdatedState(cities)
    val currentSelected by rememberUpdatedState(selectedKey)
    val select by rememberUpdatedState(onSelect)
    val paging by rememberUpdatedState(onPaging)
    DisposableEffect(pager) { onDispose { paging(false) } }
    LaunchedEffect(keys, selectedKey) {
        val external = selection.observe(selectedKey, keys)
        val target = keys.indexOf(selectedKey)
        if (target >= 0 && (external || !pager.isScrollInProgress && pager.currentPage >= keys.size)) {
            if (pager.settledPage != target || pager.currentPageOffsetFraction != 0f) pager.scrollToPage(target)
        }
    }
    // Freeze city-local artwork and glow on the same composition as the gesture.
    // The separate sky keeps its own continuous transition and frame budget.
    val scrolling by remember { derivedStateOf { pager.isScrollInProgress } }
    SideEffect { paging(scrolling); onPagerReady(pager) }
    LaunchedEffect(pager) {
        var moved = false
        snapshotFlow { pager.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (scrolling) moved = true
            else if (moved) {
                moved = false
                currentCities.getOrNull(pager.settledPage)?.locationKey?.let { key ->
                    if (selection.request(key, currentSelected)) select(key)
                }
            }
        }
    }
    Box(modifier.onPlaced { viewport.coordinates = it }) {
    HorizontalPager(
        state = pager,
        key = { keys[it] },
        userScrollEnabled = enabled && cities.size > 1,
        beyondViewportPageCount = 1,
        // Critically damped: the page eases out and does not bounce. The stiffness
        // stays at 500 so a full-page release still settles inside the gesture window.
        flingBehavior = PagerDefaults.flingBehavior(pager,
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 500f)),
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val coordinates = remember { SurfacePageCoordinates(viewport) }
        CompositionLocalProvider(LocalSurfacePageCoordinates provides coordinates) {
            // No page-sized layer here. The page indicator reads the drag offset
            // every frame; wrapping it with the weather list forces that list,
            // its glass and its blur to be recorded again on every frame.
            Box(Modifier.fillMaxSize().onPlaced { coordinates.coordinates = it }) {
                val activity = cityPageActivity(index, pager.currentPage, pager.targetPage,
                    pager.settledPage, scrolling, enabled)
                ProvideCityPageMotion(activity == CityPageActivity.ACTIVE) {
                    content(cities[index], pager)
                }
            }
        }
    }
    ProvideCityPageMotion(enabled && !scrolling) { overlay(pager) }
    }
}

/** Compact moving capsule, capped at seven dots even with a long saved-city list. */
@Composable
internal fun CityPageIndicator(cities: List<City>, city: City, pager: PagerState, modifier: Modifier = Modifier) {
    if (cities.size < 2) return
    val scope = rememberCoroutineScope()
    val index = cities.indexOfFirst { it.locationKey == city.locationKey }.coerceAtLeast(0)
    val tint = ZhishengTextSecondary
    val vista = isPhosphorVista
    val active = if (vista) ZhishengMint else ZhishengText
    Canvas(modifier.height(14.dp).widthIn(max = 100.dp).fillMaxWidth().semantics {
        contentDescription = "城市，第${index + 1}页，共${cities.size}页"
        customActions = buildList {
            if (index > 0) add(CustomAccessibilityAction("上一个城市") {
                scope.launch { pager.animateScrollToPage(index - 1) }; true
            })
            if (index < cities.lastIndex) add(CustomAccessibilityAction("下一个城市") {
                scope.launch { pager.animateScrollToPage(index + 1) }; true
            })
        }
    }) {
        val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, cities.lastIndex.toFloat())
        val window = cityIndicatorWindow(cities.size, position)
        val step = 11.dp.toPx()
        val radius = 2.dp.toPx()
        val centerY = size.height / 2
        val scroll = (position - 3f).coerceIn(0f, (cities.size - 7).coerceAtLeast(0).toFloat())
        val viewport = minOf(size.width, (minOf(cities.size, 7) - 1) * step + radius * 3.5f)
        clipRect(right = viewport) {
            for (dot in window.first..minOf(window.last + 1, cities.lastIndex)) {
                val x = radius + (dot - scroll) * step
                val edge = cities.size > 7 && (dot < scroll + 1 && scroll > 0f ||
                    dot > scroll + 5 && scroll < cities.size - 7f)
                drawCircle(tint.copy(alpha = .35f), if (edge) radius * .65f else radius, Offset(x, centerY))
            }
            val x = radius + (position - scroll) * step
            // Vista's small light capsule stretches with the finger and closes at rest.
            // It shares the pager's position; no second animation can lag behind a reversal.
            val stretch = if (vista) 3.dp.toPx() * (abs(pager.currentPageOffsetFraction) * 2f).coerceIn(0f, 1f) else 0f
            val start = Offset(x - stretch * .5f, centerY)
            val end = Offset(x + 3.dp.toPx() + stretch * .5f, centerY)
            if (vista) drawLine(active.copy(alpha = .12f), start, end, radius * 3.2f, StrokeCap.Round)
            drawLine(active, start, end, radius * 2, StrokeCap.Round)
        }
    }
}
