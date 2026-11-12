package com.zhisheng.weather.ui

import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.data.TyphoonDetail
import com.zhisheng.weather.data.TyphoonForecastTrack
import com.zhisheng.weather.data.TyphoonTrackPoint
import com.zhisheng.weather.data.TyphoonWindRadii
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property.LINE_CAP_BUTT
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.layers.Property.LINE_JOIN_BEVEL
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal data class TyphoonMapSelection(val point: TyphoonTrackPoint, val forecast: Boolean)

/** Physical pixels covered by UI above the full-size map; zero means the unobstructed map. */
internal data class TyphoonMapInsets(
    val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0,
)
internal fun typhoonMapConfigured(): Boolean = validTyphoonMapToken(BuildConfig.TDT_TOKEN)

@Composable
internal fun TyphoonMap(
    detail: TyphoonDetail, forecast: TyphoonForecastTrack?, selected: TyphoonMapSelection?,
    showWind: Boolean, recenterToken: Int, onSelect: (TyphoonMapSelection) -> Unit,
    modifier: Modifier = Modifier, privacyAccepted: Boolean,
    onMapStatus: (loaded: Boolean, approvalNumber: String?) -> Unit = { _, _ -> },
    satellite: Boolean = false,
    contentInsets: TyphoonMapInsets = TyphoonMapInsets(),
    onEmptyMapClick: () -> Unit = {},
) {
    if (!privacyAccepted || !typhoonMapConfigured()) return
    var retry by remember { mutableIntStateOf(0) }
    key(retry) {
        TyphoonNativeMap(detail, forecast, selected, showWind, recenterToken, onSelect, modifier,
            onMapStatus, satellite, contentInsets, onEmptyMapClick, onRetry = { retry++ })
    }
}

@Composable
private fun TyphoonNativeMap(
    detail: TyphoonDetail, forecast: TyphoonForecastTrack?, selected: TyphoonMapSelection?,
    showWind: Boolean, recenterToken: Int, onSelect: (TyphoonMapSelection) -> Unit,
    modifier: Modifier, onMapStatus: (Boolean, String?) -> Unit, satellite: Boolean,
    contentInsets: TyphoonMapInsets, onEmptyMapClick: () -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val palette = LocalZhishengPalette.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeStyle by remember { mutableStateOf<Style?>(null) }
    var styleGeneration by remember { mutableIntStateOf(0) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var fittedRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var serviceAvailable by remember(satellite) { mutableStateOf<Boolean?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    var destroyed by remember { mutableStateOf(false) }
    var lastSelected by remember(detail.storm.id) { mutableStateOf<TyphoonMapSelection?>(null) }
    val selectCallback by rememberUpdatedState(onSelect)
    val emptyClickCallback by rememberUpdatedState(onEmptyMapClick)
    val statusCallback by rememberUpdatedState(onMapStatus)
    val allPoints = remember(detail.observed, forecast) {
        detail.observed.filter(::validTyphoonMapPoint).map { TyphoonMapSelection(it, false) } +
            forecast?.points.orEmpty().filter(::validTyphoonMapPoint).map { TyphoonMapSelection(it, true) }
    }
    val pointsState by rememberUpdatedState(allPoints)
    val densityState by rememberUpdatedState(density)
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            getMapAsync { map ->
                com.zhisheng.weather.ui.UiRefreshRate.applyViewTree(this)
                if (destroyed) return@getMapAsync
                map.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isDoubleTapGesturesEnabled = true
                    isQuickZoomGesturesEnabled = true
                    isAttributionEnabled = false
                    isLogoEnabled = false
                }
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(18.0)
                map.addOnMapClickListener { coordinate ->
                    if (destroyed) return@addOnMapClickListener false
                    val tap = map.projection.toScreenLocation(coordinate)
                    val nearest = pointsState.minByOrNull { item ->
                        val screen = map.projection.toScreenLocation(LatLng(item.point.latitude, item.point.longitude))
                        (screen.x - tap.x).pow(2) + (screen.y - tap.y).pow(2)
                    }
                    val hit = nearest?.let { item ->
                        val screen = map.projection.toScreenLocation(LatLng(item.point.latitude, item.point.longitude))
                        (screen.x - tap.x).pow(2) + (screen.y - tap.y).pow(2) <= (28 * densityState).pow(2)
                    } == true
                    if (hit && nearest != null) selectCallback(nearest) else emptyClickCallback()
                    true
                }
                mapRef = map
            }
        }
    }
    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        fun start() { if (!started && !destroyed) { mapView.onStart(); started = true } }
        fun resume() { if (!resumed && !destroyed) { start(); mapView.onResume(); resumed = true } }
        fun pause() { if (resumed && !destroyed) { mapView.onPause(); resumed = false } }
        fun stop() { if (started && !destroyed) { pause(); mapView.onStop(); started = false } }
        fun destroy() {
            if (!destroyed) {
                stop()
                destroyed = true
                activeStyle = null
                mapRef = null
                mapView.onDestroy()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> destroy()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { if (!destroyed) mapView.onLowMemory() }
        }
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(memory)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        statusCallback(false, null)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memory)
            destroy()
        }
    }
    DisposableEffect(mapRef, satellite, palette.isLight) {
        val map = mapRef
        if (map == null || destroyed) return@DisposableEffect onDispose { }
        val generation = ++styleGeneration
        var valid = true
        activeStyle = null
        loaded = false
        loadFailed = false
        timedOut = false
        statusCallback(false, TYPHOON_MAP_APPROVAL)
        val finished = MapView.OnDidFinishLoadingMapListener {
            if (valid && !destroyed && activeStyle != null && map.style === activeStyle) {
                loaded = true
                loadFailed = false
            }
        }
        val failed = MapView.OnDidFailLoadingMapListener {
            // Error messages can contain the credential-bearing URL: never log or display them.
            if (valid && !destroyed) loadFailed = true
        }
        mapView.addOnDidFinishLoadingMapListener(finished)
        mapView.addOnDidFailLoadingMapListener(failed)
        map.setStyle(typhoonMapBaseStyle(palette, satellite)) { style ->
            if (valid && !destroyed && generation == styleGeneration && map.style === style) {
                installTyphoonOverlayScaffold(style, palette)
                activeStyle = style
            }
        }
        onDispose {
            valid = false
            mapView.removeOnDidFinishLoadingMapListener(finished)
            mapView.removeOnDidFailLoadingMapListener(failed)
        }
    }
    LaunchedEffect(satellite) {
        serviceAvailable = verifyTyphoonMapService(satellite)
    }
    LaunchedEffect(loaded, styleGeneration) {
        if (!loaded) { delay(20_000); timedOut = true } else timedOut = false
    }
    LaunchedEffect(loaded, loadFailed, serviceAvailable) {
        statusCallback(loaded && !loadFailed && serviceAvailable == true, TYPHOON_MAP_APPROVAL)
    }
    LaunchedEffect(activeStyle, detail, forecast, palette.isLight) {
        val style = activeStyle
        if (style != null && !destroyed && mapRef?.style === style) {
            applyTyphoonTracks(style, detail, forecast, palette)
        }
    }
    LaunchedEffect(activeStyle, selected, showWind, mapSize, contentInsets) {
        val style = activeStyle
        if (style != null && !destroyed && mapRef?.style === style) {
            applyTyphoonSelection(style, selected, showWind)
        }
        val map = mapRef
        val point = selected?.point?.takeIf(::validTyphoonMapPoint)
        val viewportInsets = contentInsets.withMapMargin(mapSize, (32 * density).roundToInt())
        if (map != null && point != null && viewportInsets != null && lastSelected != null && lastSelected != selected && !destroyed) {
            // A point click can open a panel in the same frame. Wait for its measured insets.
            withFrameNanos { }
            if (!destroyed && mapRef === map) {
                map.keepSelectedInView(LatLng(point.latitude, point.longitude), mapSize, viewportInsets)
            }
        }
        lastSelected = selected
    }
    LaunchedEffect(mapRef, activeStyle, mapSize, detail.storm.id, recenterToken, allPoints, contentInsets) {
        val map = mapRef ?: return@LaunchedEffect
        val request = detail.storm.id to recenterToken
        if (activeStyle == null || destroyed || mapSize.width == 0 || mapSize.height == 0 || fittedRequest == request) {
            return@LaunchedEffect
        }
        val viewportInsets = contentInsets.withMapMargin(mapSize, (32 * density).roundToInt()) ?: return@LaunchedEffect
        // Let the parent's first overlay measurement settle before fitting this storm once.
        withFrameNanos { }
        if (destroyed || mapRef !== map || activeStyle == null || map.style !== activeStyle) return@LaunchedEffect
        val points = allPoints.map { LatLng(it.point.latitude, it.point.longitude) }.distinct()
        if (points.isNotEmpty()) {
            if (points.size == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 5.0))
                map.keepSelectedInView(points.first(), mapSize, viewportInsets, forceCenter = true)
            } else {
                // Unwrap the longitude interval for storms crossing the date line.
                val longitudes = typhoonMapCameraLongitudes(points.map { it.longitude })
                val cameraPoints = points.zip(longitudes) { point, longitude -> LatLng(point.latitude, longitude) }
                val update = CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(cameraPoints).build(),
                    viewportInsets.left, viewportInsets.top, viewportInsets.right, viewportInsets.bottom,
                )
                if (recenterToken > 0 && ValueAnimator.areAnimatorsEnabled()) map.animateCamera(update, 350)
                else map.moveCamera(update)
            }
            // Insets/theme/base-layer changes do not reset a user's pan or zoom.
            fittedRequest = request
        }
    }
    Box(modifier.background(palette.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it })
        if (!loaded || loadFailed || serviceAvailable != true) {
            val failed = timedOut || loadFailed || serviceAvailable == false
            Column(Modifier.align(Alignment.Center).padding(24.dp)
                .background(palette.surface.copy(alpha = 0.96f), RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (failed) "地图暂时未能载入" else "正在载入天地图", color = palette.text)
                if (failed) {
                    Text("请检查网络或地图服务授权后重试，路径资料仍可查看。", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text("重新载入") }
                }
            }
        }
        val attributionBottom = with(LocalDensity.current) { contentInsets.bottom.coerceAtLeast(0).toDp() }
        val attributionRight = with(LocalDensity.current) { contentInsets.right.coerceAtLeast(0).toDp() }
        val attributionLeft = with(LocalDensity.current) { contentInsets.left.coerceAtLeast(0).toDp() }
        Text(TYPHOON_MAP_ATTRIBUTION, Modifier.align(Alignment.BottomEnd)
            .padding(start = attributionLeft + 6.dp, end = attributionRight + 6.dp, bottom = attributionBottom + 6.dp)
            .background(palette.surface.copy(alpha = 0.94f), RoundedCornerShape(4.dp)).padding(4.dp),
            color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

/** Leave a valid, nonzero visible rectangle even on narrow layouts; never fit through covered UI. */
private fun TyphoonMapInsets.withMapMargin(size: IntSize, margin: Int): TyphoonMapInsets? {
    if (size.width <= 0 || size.height <= 0) return null
    val l = left.coerceIn(0, size.width)
    val r = right.coerceIn(0, size.width)
    val t = top.coerceIn(0, size.height)
    val b = bottom.coerceIn(0, size.height)
    val visibleWidth = size.width - l - r
    val visibleHeight = size.height - t - b
    if (visibleWidth <= 1 || visibleHeight <= 1) return null
    val horizontalMargin = margin.coerceIn(0, (visibleWidth - 1) / 2)
    val verticalMargin = margin.coerceIn(0, (visibleHeight - 1) / 2)
    return TyphoonMapInsets(t + verticalMargin, b + verticalMargin, l + horizontalMargin, r + horizontalMargin)
}

private fun MapLibreMap.keepSelectedInView(
    target: LatLng, size: IntSize, insets: TyphoonMapInsets, forceCenter: Boolean = false,
) {
    val screen = projection.toScreenLocation(target)
    if (!screen.x.isFinite() || !screen.y.isFinite()) return
    val left = insets.left.toFloat()
    val top = insets.top.toFloat()
    val right = (size.width - insets.right).toFloat()
    val bottom = (size.height - insets.bottom).toFloat()
    if (!forceCenter && screen.x in left..right && screen.y in top..bottom) return
    val offsetX = screen.x - (left + right) / 2f
    val offsetY = screen.y - (top + bottom) / 2f
    if (abs(offsetX) < 0.5f && abs(offsetY) < 0.5f) return
    val anchor = cameraPosition.target?.let(projection::toScreenLocation) ?: return
    val shiftedTarget = projection.fromScreenLocation(PointF(anchor.x + offsetX, anchor.y + offsetY))
    if (shiftedTarget.latitude.isFinite() && shiftedTarget.longitude.isFinite()) {
        // Translate at the current zoom instead of resetting the camera or persisting new map padding.
        moveCamera(CameraUpdateFactory.newLatLng(shiftedTarget))
    }
}

internal fun validTyphoonMapPoint(point: TyphoonTrackPoint): Boolean =
    point.latitude.isFinite() && point.longitude.isFinite() && point.latitude in -85.0..85.0 && point.longitude in -180.0..180.0

internal fun splitTyphoonMapLine(points: List<TyphoonTrackPoint>): List<List<TyphoonTrackPoint>> {
    val result = mutableListOf<List<TyphoonTrackPoint>>()
    var current = mutableListOf<TyphoonTrackPoint>()
    for (point in points) {
        if (!validTyphoonMapPoint(point)) {
            if (current.isNotEmpty()) result += current
            current = mutableListOf()
            continue
        }
        if (current.isNotEmpty() && abs(current.last().longitude - point.longitude) > 180.0) {
            result += current
            current = mutableListOf()
        }
        current += point
    }
    if (current.isNotEmpty()) result += current
    return result
}

/** Smallest longitude interval, keeping a crossing Pacific track in one camera view. */
internal fun typhoonMapCameraLongitudes(longitudes: List<Double>): List<Double> {
    if (longitudes.size < 2 || longitudes.max() - longitudes.min() <= 180.0) return longitudes
    val sorted = longitudes.sorted()
    val gapEnd = sorted.indices.maxBy { i ->
        (if (i == sorted.lastIndex) sorted.first() + 360.0 else sorted[i + 1]) - sorted[i]
    }
    val start = sorted[(gapEnd + 1) % sorted.size]
    return longitudes.map { if (it < start) it + 360.0 else it }
}

private fun emptyFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyArray<Feature>())

private fun Style.addTyphoonLayer(layer: org.maplibre.android.style.layers.Layer, belowLabels: Boolean) {
    val anchor = if (belowLabels) TYPHOON_TDT_LABEL_LAYER else null
    if (anchor != null) addLayerBelow(layer, anchor) else addLayer(layer)
}

private fun installTyphoonOverlayScaffold(style: Style, palette: ZhishengPalette) {
    val empty = emptyFeatures()
    listOf("ty-wind7", "ty-wind10", "ty-wind12").forEach { id ->
        style.addSource(GeoJsonSource("$id-source", empty))
    }
    addWindLayers(style, "ty-wind7", Color(0xFF4A6E8C), 0.32f)
    addWindLayers(style, "ty-wind10", Color(0xFFFFA911), 0.24f)
    addWindLayers(style, "ty-wind12", Color(0xFFFF5341), 0.22f)

    style.addSource(GeoJsonSource("ty-observed-casing-source", empty))
    style.addTyphoonOverlay(trackLineLayer("ty-observed-casing", "ty-observed-casing-source", trackHalo(palette), 3.6f))
    style.addSource(GeoJsonSource("ty-observed-line-source", empty))
    style.addTyphoonOverlay(trackLineLayer("ty-observed-line", "ty-observed-line-source", Color(0xFFFB5614), 2.1f))
    style.addSource(GeoJsonSource("ty-forecast-line-source", empty))
    style.addTyphoonOverlay(trackLineLayer("ty-forecast-line", "ty-forecast-line-source", Color(0xFFFF4050), 2.4f, dashed = true))
    for (group in 0..6) {
        addPointGroupLayer(style, "ty-observed-points-$group", false, group)
        addPointGroupLayer(style, "ty-forecast-points-$group", true, group)
    }
    style.addSource(GeoJsonSource("ty-selected-source", empty))
    style.addTyphoonOverlay(
        CircleLayer("ty-current-halo", "ty-selected-source").withProperties(
            circleRadius(13f),
            circleColor(Color(0xFFFB5614).copy(alpha = 0.22f).toArgb()),
            circleOpacity(1f),
        ),
    )
    style.addTyphoonOverlay(
        CircleLayer("ty-current-body", "ty-selected-source").withProperties(
            circleRadius(6.2f),
            circleColor(Color(0xFFFB5614).toArgb()),
            circleStrokeColor(Color(0xF2FFFFFF).toArgb()),
            circleStrokeWidth(1.6f),
        ),
    )
    style.addTyphoonOverlay(
        CircleLayer("ty-current-eye", "ty-selected-source").withProperties(
            circleRadius(2.1f),
            circleColor(Color(0xFFF7F7F7).toArgb()),
        ),
    )
}

private fun addPointGroupLayer(
    style: Style,
    id: String,
    forecast: Boolean,
    group: Int,
) {
    val fill = typhoonLevelColor(group)
    style.addSource(GeoJsonSource("$id-source", emptyFeatures()))
    style.addTyphoonOverlay(
        CircleLayer(id, "$id-source").withProperties(
            circleRadius(if (forecast) 4.2f else 4.6f),
            circleColor(if (forecast) Color(0xF2FFFFFF).toArgb() else fill.toArgb()),
            circleOpacity(0.92f),
            circleStrokeColor(if (forecast) fill.toArgb() else Color(0x4D6E6E6E).toArgb()),
            circleStrokeWidth(if (forecast) 1.7f else 1.2f),
        ),
    )
}

private fun applyTyphoonTracks(
    style: Style,
    detail: TyphoonDetail,
    forecast: TyphoonForecastTrack?,
    palette: ZhishengPalette,
) {
    val observedLine = detail.observed
    setGeoLine(style, "ty-observed-casing", observedLine)
    setGeoLine(style, "ty-observed-line", observedLine)
    style.getLayer("ty-observed-casing")?.setProperties(lineColor(trackHalo(palette).toArgb()))
    val forecastLine = listOfNotNull(detail.observed.lastOrNull()) + forecast?.points.orEmpty()
    setGeoLine(style, "ty-forecast-line", forecastLine)
    style.getLayer("ty-forecast-line")?.setProperties(lineColor(Color(0xFFFF4050).toArgb()))
    applyPointGroups(
        style,
        "ty-observed-points",
        detail.observed.filter(::validTyphoonMapPoint),
        false,
    )
    applyPointGroups(
        style,
        "ty-forecast-points",
        forecast?.points.orEmpty().filter(::validTyphoonMapPoint),
        true,
    )
}

private fun applyPointGroups(
    style: Style,
    prefix: String,
    points: List<TyphoonTrackPoint>,
    forecast: Boolean,
) {
    val grouped = points.groupBy(::typhoonIntensityGroup)
    for (group in 0..6) {
        val id = "$prefix-$group"
        val fill = typhoonLevelColor(group)
        val features = grouped[group].orEmpty().map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
        style.getSourceAs<GeoJsonSource>("$id-source")?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
        style.getLayer(id)?.setProperties(
            circleRadius(if (forecast) 4.2f else 4.6f),
            circleColor(if (forecast) Color(0xF2FFFFFF).toArgb() else fill.toArgb()),
            circleOpacity(0.92f),
            circleStrokeColor(if (forecast) fill.toArgb() else Color(0x4D6E6E6E).toArgb()),
            circleStrokeWidth(if (forecast) 1.7f else 1.2f),
        )
    }
}

private fun applyTyphoonSelection(style: Style, selected: TyphoonMapSelection?, showWind: Boolean) {
    if (selected != null && !validTyphoonMapPoint(selected.point)) {
        applyTyphoonSelection(style, null, false)
        return
    }
    val windPoint = selected?.takeIf { showWind && !it.forecast }?.point
    setWindSource(style, "ty-wind7", windPoint, windPoint?.radius7)
    setWindSource(style, "ty-wind10", windPoint, windPoint?.radius10)
    setWindSource(style, "ty-wind12", windPoint, windPoint?.radius12)
    val selectedSource = style.getSourceAs<GeoJsonSource>("ty-selected-source") ?: return
    if (selected == null) {
        selectedSource.setGeoJson(emptyFeatures())
        return
    }
    selectedSource.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(selected.point.longitude, selected.point.latitude)),
    )
    val accent = typhoonIntensityColor(selected.point)
    style.getLayer("ty-current-halo")?.setProperties(circleColor(accent.copy(alpha = 0.22f).toArgb()))
    style.getLayer("ty-current-body")?.setProperties(circleColor(accent.toArgb()))
}

private fun setGeoLine(style: Style, id: String, points: List<TyphoonTrackPoint>) {
    val source = style.getSourceAs<GeoJsonSource>("$id-source") ?: return
    val segments = splitTyphoonMapLine(points).filter { it.size >= 2 }
        .map { Feature.fromGeometry(LineString.fromLngLats(typhoonPolyline(it))) }
    source.setGeoJson(FeatureCollection.fromFeatures(segments.toTypedArray()))
}

private fun setWindSource(style: Style, id: String, point: TyphoonTrackPoint?, radii: TyphoonWindRadii?) {
    val source = style.getSourceAs<GeoJsonSource>("$id-source") ?: return
    val polygon = point?.let { windPolygon(it, radii) }
    if (polygon != null) source.setGeoJson(Feature.fromGeometry(polygon))
    else source.setGeoJson(emptyFeatures())
}

private fun addWindLayers(style: Style, id: String, color: Color, opacity: Float) {
    style.addTyphoonLayer(
        FillLayer("$id-fill", "$id-source").withProperties(fillColor(color.toArgb()), fillOpacity(opacity)),
        belowLabels = true,
    )
    style.addTyphoonLayer(
        LineLayer("$id-line", "$id-source").withProperties(
            lineColor(color.toArgb()),
            lineOpacity(0.78f),
            lineWidth(1.35f),
            lineCap(LINE_CAP_ROUND),
            lineJoin(LINE_JOIN_ROUND),
        ),
        belowLabels = true,
    )
}

private fun trackLineLayer(
    id: String,
    source: String,
    color: Color,
    width: Float,
    dashed: Boolean = false,
): LineLayer {
    val layer = LineLayer(id, source).withProperties(
        lineColor(color.toArgb()),
        lineWidth(width),
        lineOpacity(1f),
        lineCap(if (dashed) LINE_CAP_BUTT else LINE_CAP_ROUND),
        lineJoin(if (dashed) LINE_JOIN_BEVEL else LINE_JOIN_ROUND),
    )
    if (dashed) layer.setProperties(lineDasharray(arrayOf(2.4f, 1.8f)))
    return layer
}

private fun trackHalo(palette: ZhishengPalette): Color =
    if (palette.isLight) Color(0xF2FFFFFF) else Color(0xEE050508)

private fun windPolygon(center: TyphoonTrackPoint, radii: TyphoonWindRadii?): Polygon? {
    if (radii?.available != true) return null
    if (listOfNotNull(radii.northEastKm, radii.southEastKm, radii.southWestKm, radii.northWestKm)
            .any { !it.isFinite() || it < 0.0 || it > 2_000.0 }) return null
    val ring = windCircleRing(center, radii)
    if (ring.size < 4) return null
    return Polygon.fromLngLats(listOf(ring))
}

private fun Style.addTyphoonOverlay(layer: org.maplibre.android.style.layers.Layer) {
    addLayerBelow(layer, TYPHOON_TDT_LABEL_LAYER)
}
