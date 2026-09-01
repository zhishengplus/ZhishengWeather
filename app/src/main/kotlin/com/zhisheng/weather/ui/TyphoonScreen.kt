/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V4 */
/* Hallmark · genre: atmospheric technical utility · macrostructure: Workbench · design-system: design.md · designed-as-app */
package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.data.TyphoonDetail
import com.zhisheng.weather.data.TyphoonForecastTrack
import com.zhisheng.weather.data.TyphoonLoad
import com.zhisheng.weather.data.TyphoonRepository
import com.zhisheng.weather.data.TyphoonStorm
import com.zhisheng.weather.data.TyphoonTrackPoint
import com.zhisheng.weather.data.TyphoonWindRadii
import com.zhisheng.weather.data.parseTyphoonTime
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.Property.LINE_CAP_BUTT
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.layers.Property.LINE_JOIN_BEVEL
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TY_REFRESH_MILLIS = 10 * 60_000L
private const val TY_STALE_MILLIS = 24 * 60 * 60_000L
private const val TY_MIN_ZOOM = 2.2
private const val TY_MAX_ZOOM = 16.0
private const val TY_HIT_RADIUS_PX = 48f

private data class DisplayPoint(val point: TyphoonTrackPoint, val forecast: Boolean)

@Composable
fun TyphoonScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<TyphoonLoad<List<TyphoonStorm>>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<TyphoonLoad<TyphoonDetail>?>(null) }

    LaunchedEffect(refreshToken) {
        catalog = TyphoonRepository.loadCatalog(context, force = refreshToken > 0)
        val storms = catalog?.value.orEmpty()
        if (selectedId !in storms.map { it.id }) selectedId = storms.firstOrNull()?.id
    }
    LaunchedEffect(selectedId, refreshToken) {
        val storm = catalog?.value?.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        detail = TyphoonRepository.loadDetail(context, storm, force = refreshToken > 0)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TY_REFRESH_MILLIS)
            refreshToken++
        }
    }

    val loaded = detail?.value
    if (loaded != null) {
        TyphoonWorkbench(
            detail = loaded,
            load = requireNotNull(detail),
            storms = catalog?.value.orEmpty(),
            selectedId = selectedId,
            catalogNotice = catalog?.error,
            onSelectStorm = { selectedId = it },
            onRefresh = { refreshToken++ },
            onBack = onBack,
        )
    } else {
        TyphoonLoadingPage(
            catalog = catalog,
            storms = catalog?.value.orEmpty(),
            selectedId = selectedId,
            onSelectStorm = { selectedId = it },
            detailError = detail?.error,
            onRetry = { refreshToken++ },
            onBack = onBack,
        )
    }
}

@Composable
private fun TyphoonLoadingPage(
    catalog: TyphoonLoad<List<TyphoonStorm>>?,
    storms: List<TyphoonStorm>,
    selectedId: String?,
    onSelectStorm: (String) -> Unit,
    detailError: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    Column(Modifier.fillMaxSize().background(palette.bg).statusBarsPadding().navigationBarsPadding()) {
        FeaturePageHeader(
            title = "台风路径",
            subtitle = "TYPHOON OBSERVATORY",
            onBack = onBack,
            trailing = { TyphoonIconAction(Icons.Filled.Refresh, "刷新台风资料", onRetry) },
        )
        if (storms.isNotEmpty()) StormStrip(storms, selectedId, onSelectStorm, palette)
        when {
            catalog == null -> FeatureBootLoader(
                channel = "TYPHOON LINK",
                lines = listOf("连接国内台风资料源", "读取当前编号", "校准路径与预报时次"),
                status = "正在建立台风观测链路…",
            )
            catalog.value.isNullOrEmpty() -> TyphoonEmpty(catalog.error ?: "当前没有可显示的台风资料", onRetry)
            detailError != null -> TyphoonEmpty(detailError, onRetry)
            else -> FeatureBootLoader(
                channel = "TRACK DECODE",
                lines = listOf("读取实况节点", "核对中心强度", "展开多机构预报"),
                status = "正在整理路径…",
            )
        }
    }
}

@Composable
private fun TyphoonWorkbench(
    detail: TyphoonDetail,
    load: TyphoonLoad<TyphoonDetail>,
    storms: List<TyphoonStorm>,
    selectedId: String?,
    catalogNotice: String?,
    onSelectStorm: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val forecast = detail.forecasts.firstOrNull()
    val displayPoints = remember(detail.observed, forecast) {
        detail.observed.map { DisplayPoint(it, false) } + forecast?.points.orEmpty().map { DisplayPoint(it, true) }
    }
    var selectedKey by remember(detail.storm.id) { mutableStateOf(pointKey(detail.observed.lastOrNull(), false)) }
    val selected = displayPoints.firstOrNull { pointKey(it.point, it.forecast) == selectedKey }
        ?: displayPoints.lastOrNull { !it.forecast }
        ?: displayPoints.lastOrNull()
    var showInfo by remember { mutableStateOf(false) }
    var recenterToken by remember(detail.storm.id) { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        TyphoonVectorMap(
            detail = detail,
            forecast = forecast,
            selected = selected,
            recenterToken = recenterToken,
            onSelect = { selectedKey = pointKey(it.point, it.forecast) },
            modifier = Modifier.fillMaxSize(),
        )
        TyphoonTopChrome(
            detail = detail,
            load = load,
            storms = storms,
            selectedId = selectedId,
            catalogNotice = catalogNotice,
            palette = palette,
            onSelectStorm = onSelectStorm,
            onBack = onBack,
            onRefresh = onRefresh,
            onRecenter = { recenterToken++ },
            onInfo = { showInfo = true },
        )
        TyphoonBottomController(
            modifier = Modifier.align(Alignment.BottomCenter),
            detail = detail,
            forecast = forecast,
            displayPoints = displayPoints,
            selected = selected,
            onSelect = { selectedKey = pointKey(it.point, it.forecast) },
            palette = palette,
        )
        if (showInfo) TyphoonInfoDialog(detail, onDismiss = { showInfo = false })
    }
}

@Composable
private fun TyphoonTopChrome(
    detail: TyphoonDetail,
    load: TyphoonLoad<TyphoonDetail>,
    storms: List<TyphoonStorm>,
    selectedId: String?,
    catalogNotice: String?,
    palette: ZhishengPalette,
    onSelectStorm: (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRecenter: () -> Unit,
    onInfo: () -> Unit,
) {
    val latest = detail.observed.lastOrNull()
    val stale = parseTyphoonTime(latest?.time)?.let { System.currentTimeMillis() - it > TY_STALE_MILLIS } ?: true
    val warning = warningVisual(detail.storm.warningLevel, palette)
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TyphoonIconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(7.dp))
            Column(
                Modifier.weight(1f).height(48.dp)
                    .background(palette.surface.copy(alpha = 0.96f))
                    .border(1.dp, palette.cardBorder)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    "台风 · ${(storms.firstOrNull { it.id == selectedId } ?: detail.storm).name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WarningDot(warning, palette)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            selectedId != null && selectedId != detail.storm.id -> "正在切换台风"
                            warning != null -> warning.label
                            stale -> "资料较旧"
                            detail.storm.active -> "实况追踪"
                            else -> "历史路径"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = warning?.textColor ?: if (stale || load.fromCache) palette.orange else palette.textSecondary,
                        maxLines = 1,
                    )
                    Text(" · ${formatTyphoonTime(latest?.time)}", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.width(6.dp))
            TyphoonTextAction("◎", "回到台风路径", onRecenter)
            Spacer(Modifier.width(6.dp))
            TyphoonIconAction(Icons.Filled.Refresh, "刷新台风资料", onRefresh)
            Spacer(Modifier.width(6.dp))
            TyphoonIconAction(Icons.Filled.Info, "台风资料说明", onInfo)
        }
        StormStrip(storms, selectedId, onSelectStorm, palette)
        val notice = catalogNotice ?: load.error
        if (notice != null) {
            Text(
                notice,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .background(palette.bg.copy(alpha = 0.90f)).border(1.dp, palette.cardBorder)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.orange,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StormStrip(
    storms: List<TyphoonStorm>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    palette: ZhishengPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .background(palette.bg.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        storms.forEach { storm ->
            val selected = storm.id == selectedId
            val warning = warningVisual(storm.warningLevel, palette)
            Column(
                Modifier.clickable(role = Role.Tab) { onSelect(storm.id) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WarningDot(warning, palette)
                    if (warning != null) Spacer(Modifier.width(5.dp))
                    Text(
                        "${storm.id.takeLast(2)} ${storm.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) palette.text else palette.textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(42.dp).height(2.dp).background(if (selected) palette.mint else Color.Transparent))
            }
        }
    }
}

private data class WarningVisual(val label: String, val color: Color, val textColor: Color, val outlined: Boolean = false)

internal fun normalizedTyphoonWarningLevel(raw: String?): String? = when (raw?.trim()?.lowercase(Locale.ROOT)) {
    "white", "白", "白色", "1" -> "white"
    "blue", "蓝", "蓝色", "2" -> "blue"
    "yellow", "黄", "黄色", "3" -> "yellow"
    "orange", "橙", "橙色", "4" -> "orange"
    "red", "红", "红色", "5" -> "red"
    else -> null
}

private fun warningVisual(raw: String?, palette: ZhishengPalette): WarningVisual? = when (normalizedTyphoonWarningLevel(raw)) {
    "white" -> WarningVisual("白色预警", Color.White, palette.text, outlined = true)
    "blue" -> WarningVisual("蓝色预警", Color(0xFF2587E8), Color(0xFF2587E8))
    "yellow" -> WarningVisual("黄色预警", palette.warning, palette.warning)
    "orange" -> WarningVisual("橙色预警", palette.orange, palette.orange)
    "red" -> WarningVisual("红色预警", palette.red, palette.red)
    else -> null
}

@Composable
private fun WarningDot(warning: WarningVisual?, palette: ZhishengPalette) {
    if (warning == null) return
    Box(
        Modifier.size(8.dp).background(warning.color)
            .then(if (warning.outlined) Modifier.border(1.dp, palette.textTertiary) else Modifier),
    )
}

@Composable
private fun TyphoonBottomController(
    modifier: Modifier,
    detail: TyphoonDetail,
    forecast: TyphoonForecastTrack?,
    displayPoints: List<DisplayPoint>,
    selected: DisplayPoint?,
    onSelect: (DisplayPoint) -> Unit,
    palette: ZhishengPalette,
) {
    val selectedIndex = displayPoints.indexOf(selected).coerceAtLeast(0)
    Column(
        modifier.fillMaxWidth().background(palette.surface.copy(alpha = 0.97f))
            .border(1.dp, palette.cardBorder)
            .navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.let { if (it.forecast) "中央气象台预报" else "实况节点" } ?: "路径节点",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected?.forecast == true) palette.cyan else palette.mint,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    selected?.point?.let(::pointHeadline) ?: "暂无节点资料",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selected?.point?.let(::pointDetailLine) ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${selectedIndex + 1}/${displayPoints.size}", style = MaterialTheme.typography.labelMedium, color = palette.orange, fontWeight = FontWeight.Bold)
                Text(
                    selected?.point?.let { "${formatCoord(it.longitude, "E")}  ${formatCoord(it.latitude, "N")}" } ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
            }
        }
        if (displayPoints.size > 1) {
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { value -> displayPoints.getOrNull(value.roundToInt())?.let(onSelect) },
                valueRange = 0f..displayPoints.lastIndex.toFloat(),
                modifier = Modifier.fillMaxWidth().height(34.dp)
                    .semantics { contentDescription = "拖动查看完整台风路径时序" },
                colors = SliderDefaults.colors(
                    thumbColor = if (selected?.forecast == true) palette.cyan else palette.mint,
                    activeTrackColor = palette.textSecondary,
                    inactiveTrackColor = palette.cardBorder,
                ),
            )
        }
        Text(
            "实况 ${detail.observed.size} 点${forecast?.let { " · 预报 ${it.points.size} 点" }.orEmpty()} · 拖动时间轴或点选路径节点",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textTertiary,
            maxLines = 1,
        )
        TiandituAttribution(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TyphoonVectorMap(
    detail: TyphoonDetail,
    forecast: TyphoonForecastTrack?,
    selected: DisplayPoint?,
    recenterToken: Int,
    onSelect: (DisplayPoint) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = LocalZhishengPalette.current
    var fallbackGeo by remember { mutableStateOf<WeatherMapFallbackGeo?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableIntStateOf(0) }
    var lastFollowedKey by remember(detail.storm.id) { mutableStateOf<String?>(null) }
    val allPoints = remember(detail.observed, forecast) {
        detail.observed.map { DisplayPoint(it, false) } + forecast?.points.orEmpty().map { DisplayPoint(it, true) }
    }
    val pointsState = rememberUpdatedState(allPoints)
    val selectState = rememberUpdatedState(onSelect)

    LaunchedEffect(Unit) {
        if (hasTiandituToken()) return@LaunchedEffect
        fallbackGeo = withContext(Dispatchers.IO) {
            runCatching {
                WeatherMapFallbackGeo(
                    china = context.assets.open("geo/china_boundaries.geojson").bufferedReader().use { it.readText() },
                    coast = context.assets.open("geo/world_coastline.geojson").bufferedReader().use { it.readText() },
                )
            }.getOrNull()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
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
                map.setMinZoomPreference(TY_MIN_ZOOM)
                map.setMaxZoomPreference(TY_MAX_ZOOM)
                map.addOnMapClickListener { latLng ->
                    val tap = map.projection.toScreenLocation(latLng)
                    val nearest = pointsState.value.minByOrNull { item ->
                        val p = map.projection.toScreenLocation(LatLng(item.point.latitude, item.point.longitude))
                        (p.x - tap.x).pow(2) + (p.y - tap.y).pow(2)
                    } ?: return@addOnMapClickListener false
                    val p = map.projection.toScreenLocation(LatLng(nearest.point.latitude, nearest.point.longitude))
                    val hit = sqrt((p.x - tap.x).pow(2) + (p.y - tap.y).pow(2)) <= TY_HIT_RADIUS_PX
                    if (hit) selectState.value(nearest)
                    hit
                }
                mapRef = map
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapRef, fallbackGeo, palette.isLight) {
        val map = mapRef ?: return@LaunchedEffect
        if (!hasTiandituToken() && fallbackGeo == null) return@LaunchedEffect
        map.setStyle(weatherMapBaseStyle(palette, fallbackGeo)) { style ->
            installTyphoonOverlayScaffold(style, palette)
            styleReady++
        }
    }
    LaunchedEffect(mapRef, styleReady, detail, forecast, selected, palette) {
        val map = mapRef ?: return@LaunchedEffect
        if (styleReady == 0) return@LaunchedEffect
        map.getStyle { style -> applyTyphoonTracks(style, detail, forecast, selected, palette) }
    }
    LaunchedEffect(mapRef, styleReady, selected, palette) {
        val map = mapRef ?: return@LaunchedEffect
        if (styleReady == 0) return@LaunchedEffect
        map.getStyle { style -> applyTyphoonSelection(style, selected) }
    }
    LaunchedEffect(selected, detail.storm.id) {
        val map = mapRef ?: return@LaunchedEffect
        val current = selected ?: return@LaunchedEffect
        val key = pointKey(current.point, current.forecast)
        if (lastFollowedKey != null && key != lastFollowedKey) {
            map.keepSelectedInView(LatLng(current.point.latitude, current.point.longitude))
        }
        lastFollowedKey = key
    }
    LaunchedEffect(mapRef, styleReady, detail.storm.id) {
        val map = mapRef ?: return@LaunchedEffect
        if (styleReady == 0) return@LaunchedEffect
        map.moveCamera(CameraUpdateFactory.newCameraPosition(trackCamera(detail.observed + forecast?.points.orEmpty())))
    }
    LaunchedEffect(mapRef, styleReady, recenterToken) {
        val map = mapRef ?: return@LaunchedEffect
        if (styleReady == 0 || recenterToken == 0) return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(trackCamera(detail.observed + forecast?.points.orEmpty())),
            420,
        )
    }

    Box(modifier.background(palette.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }
}

private fun MapLibreMap.keepSelectedInView(target: LatLng) {
    val bounds = projection.visibleRegion.latLngBounds
    if (!bounds.contains(target)) moveCamera(CameraUpdateFactory.newLatLng(target))
}

private fun emptyFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyArray<Feature>())

private fun Style.addTyphoonLayer(layer: org.maplibre.android.style.layers.Layer, belowLabels: Boolean) {
    if (belowLabels && getLayer(TIANDITU_LABEL_LAYER) != null) addLayerBelow(layer, TIANDITU_LABEL_LAYER)
    else addLayer(layer)
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
    style.addLayer(trackLineLayer("ty-observed-casing", "ty-observed-casing-source", trackHalo(palette), 3.6f))
    style.addSource(GeoJsonSource("ty-observed-line-source", empty))
    style.addLayer(trackLineLayer("ty-observed-line", "ty-observed-line-source", Color(0xFFFB5614), 2.1f))
    style.addSource(GeoJsonSource("ty-forecast-line-source", empty))
    style.addLayer(trackLineLayer("ty-forecast-line", "ty-forecast-line-source", Color(0xFFFF4050), 2.4f, dashed = true))
    for (group in 0..6) {
        addPointGroupLayer(style, "ty-observed-points-$group", false, group)
        addPointGroupLayer(style, "ty-forecast-points-$group", true, group)
    }
    style.addSource(GeoJsonSource("ty-selected-source", empty))
    style.addLayer(
        CircleLayer("ty-current-halo", "ty-selected-source").withProperties(
            circleRadius(13f),
            circleColor(Color(0xFFFB5614).copy(alpha = 0.22f).toArgb()),
            circleOpacity(1f),
        ),
    )
    style.addLayer(
        CircleLayer("ty-current-body", "ty-selected-source").withProperties(
            circleRadius(6.2f),
            circleColor(Color(0xFFFB5614).toArgb()),
            circleStrokeColor(Color(0xF2FFFFFF).toArgb()),
            circleStrokeWidth(1.6f),
        ),
    )
    style.addLayer(
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
    style.addLayer(
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
    selected: DisplayPoint?,
    palette: ZhishengPalette,
) {
    val observedLine = typhoonPolyline(detail.observed)
    setGeoLine(style, "ty-observed-casing", observedLine)
    setGeoLine(style, "ty-observed-line", observedLine)
    style.getLayer("ty-observed-casing")?.setProperties(lineColor(trackHalo(palette).toArgb()))
    val forecastLine = detail.observed.lastOrNull()?.let { listOf(it) + forecast?.points.orEmpty() }.orEmpty()
        .let(::typhoonPolyline)
    setGeoLine(style, "ty-forecast-line", forecastLine)
    style.getLayer("ty-forecast-line")?.setProperties(lineColor(Color(0xFFFF4050).toArgb()))
    val skip = selected?.let { pointKey(it.point, it.forecast) }
    applyPointGroups(
        style,
        "ty-observed-points",
        detail.observed.filter { pointKey(it, false) != skip },
        false,
    )
    applyPointGroups(
        style,
        "ty-forecast-points",
        forecast?.points.orEmpty().filter { pointKey(it, true) != skip },
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

private fun applyTyphoonSelection(style: Style, selected: DisplayPoint?) {
    val windPoint = selected?.takeUnless { it.forecast }?.point
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
    val accent = if (selected.forecast) Color(0xFFFF4050) else Color(0xFFFB5614)
    style.getLayer("ty-current-halo")?.setProperties(circleColor(accent.copy(alpha = 0.22f).toArgb()))
    style.getLayer("ty-current-body")?.setProperties(circleColor(accent.toArgb()))
}

private fun setGeoLine(style: Style, id: String, points: List<Point>) {
    val source = style.getSourceAs<GeoJsonSource>("$id-source") ?: return
    if (points.size >= 2) source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
    else source.setGeoJson(emptyFeatures())
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
    val ring = windCircleRing(center, radii)
    if (ring.size < 4) return null
    return Polygon.fromLngLats(listOf(ring))
}

private fun trackCamera(points: List<TyphoonTrackPoint>): CameraPosition {
    if (points.isEmpty()) return CameraPosition.Builder().target(LatLng(24.0, 125.0)).zoom(3.8).build()
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val span = max(maxLon - minLon, (maxLat - minLat) * 1.45)
    val zoom = when {
        span < 4.0 -> 6.1
        span < 8.0 -> 5.3
        span < 14.0 -> 4.6
        span < 24.0 -> 3.9
        span < 38.0 -> 3.3
        else -> 2.7
    }
    return CameraPosition.Builder().target(LatLng((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)).zoom(zoom).build()
}

@Composable
private fun TyphoonInfoDialog(detail: TyphoonDetail, onDismiss: () -> Unit) {
    val palette = LocalZhishengPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = RectangleShape,
        title = { Text("台风路径说明", color = palette.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据来源：${detail.source}", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text("地图使用国家地理信息公共服务平台天地图（$TIANDITU_ATTRIBUTION）；中文注记随缩放覆盖城市、区县与岛屿。台湾省按中国省级行政区显示。", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text("实况路径为橙色折线，点位按国标强度配色。当前点之后的红色虚线是中央气象台预报路径。风圈按东北、东南、西南、西北四个象限等半径圆弧拼接；青、橙、红分别表示7级、10级、12级风圈。已结束的编号通常不再发布预报。", color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text("预报路径会随官方发布调整，防灾避险请以中央气象台和当地政府最新预警为准。", color = palette.orange, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Text("知道了", color = palette.mint, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
        },
    )
}

@Composable
private fun TyphoonIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.size(44.dp).background(palette.surface.copy(alpha = 0.96f)).border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = palette.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TyphoonTextAction(label: String, description: String, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.size(44.dp).background(palette.surface.copy(alpha = 0.96f)).border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = palette.text)
    }
}

@Composable
private fun TyphoonEmpty(message: String, onRetry: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TYPHOON LINK STANDBY", style = MaterialTheme.typography.labelMedium, color = palette.orange, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        Spacer(Modifier.height(14.dp))
        Text("[ 重新连接 ]", style = MaterialTheme.typography.titleSmall, color = palette.mint, modifier = Modifier.clickable(onClick = onRetry).padding(10.dp))
    }
}

private fun pointHeadline(point: TyphoonTrackPoint): String = buildString {
    append(formatTyphoonTime(point.time))
    append(" · ")
    append(point.intensity?.takeIf(String::isNotBlank) ?: point.windLevel?.let { "${it}级" } ?: "强度待发布")
}

private fun pointDetailLine(point: TyphoonTrackPoint): String = buildString {
    point.windSpeedMs?.let { append("风速 ${format1(it)} m/s") }
    point.pressureHpa?.let { if (isNotEmpty()) append(" · "); append("气压 $it hPa") }
    point.moveDirection?.takeIf(String::isNotBlank)?.let { if (isNotEmpty()) append(" · "); append("向$it") }
    point.moveSpeedKmh?.let { append(" ${format1(it)} km/h") }
    if (isEmpty()) append("详细资料待发布")
}

private fun pointKey(point: TyphoonTrackPoint?, forecast: Boolean): String? = point?.let {
    "${if (forecast) 'F' else 'O'}:${it.time}:${it.latitude}:${it.longitude}"
}

private fun formatTyphoonTime(raw: String?): String {
    val epoch = parseTyphoonTime(raw) ?: return raw?.takeIf(String::isNotBlank) ?: "时间待发布"
    return DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
        .withZone(ZoneId.of("Asia/Shanghai"))
        .format(Instant.ofEpochMilli(epoch))
}

private fun formatCoord(value: Double, suffix: String): String = "${format1(abs(value))}°$suffix"
private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
