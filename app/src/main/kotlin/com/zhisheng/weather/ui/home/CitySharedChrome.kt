package com.zhisheng.weather.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.DisplayPrefs
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.ProvideCityPageMotion
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import com.zhisheng.weather.ui.theme.*
import kotlin.math.abs
import kotlin.math.floor

/** Only the pair containing the continuous page position changes composition. */
internal data class CityPageSegment(val from: Int, val to: Int)

internal fun cityPageSegment(position: Float, count: Int): CityPageSegment {
    if (count <= 1) return CityPageSegment(0, 0)
    val p = position.takeIf(Float::isFinite)?.coerceIn(0f, count - 1f) ?: 0f
    val first = floor(p).toInt()
    return CityPageSegment(first, (first + 1).coerceAtMost(count - 1))
}

/** A restrained crossfade leaves space between readings instead of doubling their strokes. */
internal fun cityHeroOpacity(index: Int, position: Float): Float {
    val weight = (1f - abs(index - position)).coerceIn(0f, 1f)
    return weight * weight
}

internal class CityChromeEntry(
    val city: City,
    val backdrop: GraphicsLayer,
    val scroll: LazyListState,
) {
    var data by mutableStateOf<WeatherData?>(null)
    var bodyOrigin by mutableStateOf(Offset.Zero)
    var heroHeight by mutableIntStateOf(0)
    var heroTop by mutableFloatStateOf(0f)
    var heroAttached by mutableStateOf(false)
    var heroPortalReady by mutableStateOf(false)
}

internal class CitySharedChromeState {
    val entries = mutableStateMapOf<String, CityChromeEntry>()
    var headerHeight by mutableIntStateOf(0)
    var rootTop by mutableFloatStateOf(0f)
}

internal val LocalCitySharedChrome = staticCompositionLocalOf<CitySharedChromeState?> { null }
internal val LocalCityChromeEntry = staticCompositionLocalOf<CityChromeEntry?> { null }

/** The list retains a measured slot; the actual hero is composed once in the shared overlay. */
@Composable
internal fun SharedHeroSlot(entry: CityChromeEntry, fallback: @Composable () -> Unit) {
    DisposableEffect(entry) {
        entry.heroAttached = true
        onDispose { entry.heroAttached = false; entry.heroPortalReady = false }
    }
    val ready = entry.heroHeight > 0
    SideEffect { entry.heroPortalReady = ready }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().onGloballyPositioned { entry.heroTop = it.positionInRoot().y }) {
        if (ready) Spacer(Modifier.height(with(density) { entry.heroHeight.toDp() }))
        else CompositionLocalProvider(LocalWeatherContinuity provides null) { fallback() }
    }
}

@Composable
internal fun CitySharedChromeHost(
    state: CitySharedChromeState,
    cities: List<City>,
    pager: PagerState,
    ui: HomeUiState,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    scrollEnabled: Boolean = true,
) {
    val segment by remember(pager, cities.size) {
        derivedStateOf { cityPageSegment(pager.currentPage + pager.currentPageOffsetFraction, cities.size) }
    }
    val selected = cities.firstOrNull { it.locationKey == ui.selectedCity?.locationKey }
        ?: cities.getOrNull(pager.settledPage) ?: return
    val selectedEntry = state.entries[selected.locationKey]
    val sky = LocalHomeBackdrop.current
    val density = LocalDensity.current
    val chrome = LocalZhishengChrome.current
    val floating = isPhosphorVista && ui.prefs.homeSurfaceStyle == com.zhisheng.weather.data.HomeSurfaceStyle.FRAGRANCE_GLASS
    val selectionReady = cities.getOrNull(pager.settledPage)?.locationKey == ui.selectedCity?.locationKey
    val reverseDrag = LocalLayoutDirection.current != LayoutDirection.Rtl
    Box(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { state.rootTop = it.positionInRoot().y },
        contentAlignment = Alignment.TopCenter) {
        for (index in setOf(segment.from, segment.to)) {
            val city = cities.getOrNull(index) ?: continue
            val entry = state.entries[city.locationKey] ?: continue
            val data = entry.data ?: continue
            val current = data.current ?: continue
            key(city.locationKey) {
                ProvideCityPageMotion(!pager.isScrollInProgress && city == selected) {
                    CompositionLocalProvider(LocalHomeSurfaceStyle provides ui.prefs.homeSurfaceStyle) {
                        HeroSection(current, data, ui.tempUnit, ui.prefs,
                            Modifier.widthIn(max = chrome.contentMaxWidth).fillMaxWidth()
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .onSizeChanged { entry.heroHeight = it.height }
                                .graphicsLayer {
                                    translationY = entry.heroTop - state.rootTop
                                    alpha = if (entry.heroAttached && entry.heroPortalReady)
                                        cityHeroOpacity(index, pager.currentPage + pager.currentPageOffsetFraction) else 0f
                                }
                                .then(if (city == selected && entry.heroAttached && entry.heroPortalReady) Modifier else Modifier.clearAndSetSemantics {}),
                            city.locationKey)
                    }
                }
            }
        }
        Box(Modifier.widthIn(max = chrome.contentMaxWidth).fillMaxWidth()
            .onSizeChanged { state.headerHeight = it.height }) {
            if (floating && selectedEntry != null) {
                Box(Modifier.matchParentSize().glassScrollHeader(
                    backdrop = selectedEntry.backdrop, backdropOrigin = { selectedEntry.bodyOrigin },
                    underlay = sky?.layer, underlayOrigin = sky?.origin ?: Offset.Zero,
                    strength = { if (selectedEntry.scroll.firstVisibleItemIndex > 0) 1f else
                        (selectedEntry.scroll.firstVisibleItemScrollOffset / with(density) { 48.dp.toPx() }).coerceIn(0f, 1f) },
                ))
            }
            TopBar(selected.displayName, selectionReady && !pager.isScrollInProgress && (ui.loading || ui.locating),
                onMenu, { if (selectionReady && !pager.isScrollInProgress) onRefresh() }, onSettings,
                Modifier.fillMaxWidth().scrollable(state = pager, orientation = Orientation.Horizontal,
                    enabled = scrollEnabled, reverseDirection = reverseDrag,
                    flingBehavior = PagerDefaults.flingBehavior(pager,
                        snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 500f))),
                { CityPageIndicator(cities, selected, pager) },
                heading = { SharedCityHeading(cities, pager, segment, selected.locationKey) })
        }
    }
}

@Composable
private fun SharedCityHeading(cities: List<City>, pager: PagerState, segment: CityPageSegment, selectedKey: String) {
    val vista = isPhosphorVista
    val distance = with(LocalDensity.current) { 16.dp.toPx() }
    val titleCities = listOfNotNull(cities.firstOrNull { it.locationKey == selectedKey }) +
        setOf(segment.from, segment.to).mapNotNull { cities.getOrNull(it) }.filter { it.locationKey != selectedKey }
    // Only the persisted city's title determines height. A transparent neighbour must
    // never change every city's list padding before it is actually selected.
    Layout(modifier = Modifier.fillMaxWidth().clipToBounds(), content = {
        for (city in titleCities) {
            val index = cities.indexOf(city)
            Column(Modifier.graphicsLayer {
                val delta = index - (pager.currentPage + pager.currentPageOffsetFraction)
                alpha = (1f - abs(delta)).coerceIn(0f, 1f)
                translationX = delta.coerceIn(-1f, 1f) * distance
            }.then(if (city.locationKey == selectedKey) Modifier else Modifier.clearAndSetSemantics {})) {
                val (title, address) = if (vista) vistaLocationHeading(city.displayName) else city.displayName to ""
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = if (vista) ZhishengText else ZhishengOrange, fontWeight = FontWeight.Bold)
                if (address.isNotEmpty()) Text(address, style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }) { measurables, constraints ->
        val measured = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)) }
        val first = measured.firstOrNull()
        layout(constraints.constrainWidth(first?.width ?: 0), constraints.constrainHeight(first?.height ?: 0)) {
            measured.forEach { it.placeRelative(0, 0) }
        }
    }
}
