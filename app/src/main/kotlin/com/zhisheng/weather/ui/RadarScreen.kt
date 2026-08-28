package com.zhisheng.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarTimeline
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val OFFICIAL_RADAR_URL = "https://www.nmc.cn/publish/radar/chinaall.html"
private const val RAINVIEWER_URL = "https://www.rainviewer.com/"

// 相机：初始城市级视野；瓦片最高 z7，再往上只有拉伸，限制在 9 防止回波糊成一团
private const val RADAR_START_ZOOM = 6.8
private const val RADAR_MIN_ZOOM = 3.0
private const val RADAR_MAX_ZOOM = 9.0

private const val FRAME_INTERVAL_MS = 650L
private const val LATEST_FRAME_HOLD_MS = 1400L
private const val TILE_WAIT_TIMEOUT_MS = 8_000L
private const val METADATA_REFRESH_MS = 5 * 60_000L
private const val RADAR_FRAME_OPACITY = 0.82f

private val RADAR_RING_KM = listOf(50.0, 100.0, 150.0, 200.0)
private const val RING_STEPS = 96
private const val GRATICULE_STEP_DEG = 2.0

private sealed interface RadarScreenState {
    data object Loading : RadarScreenState
    data class Ready(val timeline: RadarTimeline) : RadarScreenState
    data class Error(val message: String) : RadarScreenState
}

@Composable
fun RadarScreen(
    city: City?,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<RadarScreenState>(RadarScreenState.Loading) }

    // 雷达目录与城市无关（全球一份）；切城市时保留画面只挪相机，不打断浏览
    LaunchedEffect(city?.locationKey, retry) {
        if (city == null) {
            state = RadarScreenState.Error("先在主页选择一座城市")
            return@LaunchedEffect
        }
        if (state !is RadarScreenState.Ready) state = RadarScreenState.Loading
        state = try {
            val timeline = RadarRepository.loadTimeline()
            if (timeline.frames.isEmpty()) RadarScreenState.Error("当前位置暂时没有可用回波帧")
            else RadarScreenState.Ready(timeline)
        } catch (_: Exception) {
            if (state is RadarScreenState.Ready) state else RadarScreenState.Error("雷达数据连接失败")
        }
    }

    // 页面停留期间每 5 分钟复核目录；帧目录或缓存状态变化时才替换，避免无谓的样式重建
    val readyTimeline = (state as? RadarScreenState.Ready)?.timeline
    LaunchedEffect(readyTimeline) {
        if (readyTimeline == null) return@LaunchedEffect
        while (true) {
            delay(METADATA_REFRESH_MS)
            val fresh = runCatching { RadarRepository.loadTimeline() }.getOrNull() ?: continue
            if (fresh.frames.isEmpty()) continue
            val framesChanged = fresh.frames.map(RadarFrame::path) != readyTimeline.frames.map(RadarFrame::path)
            if (framesChanged || fresh.staleMetadata != readyTimeline.staleMetadata) {
                state = RadarScreenState.Ready(fresh)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(ZhishengBg)) {
        when (val current = state) {
            RadarScreenState.Loading -> RadarLoading()
            is RadarScreenState.Error -> FeatureErrorState(
                title = current.message,
                action = "重新读取",
                onAction = { retry++ },
                extra = { OfficialRadarLink() },
            )
            is RadarScreenState.Ready -> RadarInstrumentPage(
                city = city!!,
                timeline = current.timeline,
                utcOffsetSeconds = utcOffsetSeconds,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun RadarLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("> RADAR LINK", style = MaterialTheme.typography.titleMedium, color = ZhishengMint, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text("正在读取当前位置的最新回波…", style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(13) { index ->
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .background(if (index == 12) ZhishengOrange else ZhishengMint.copy(alpha = 0.28f)),
                )
            }
        }
    }
}

@Composable
private fun RadarInstrumentPage(
    city: City,
    timeline: RadarTimeline,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = LocalZhishengPalette.current
    val frames = timeline.frames
    val framesKey = remember(frames) { frames.joinToString("|") { it.path } }

    var selectedTime by rememberSaveable(city.locationKey) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(city.locationKey) { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(true) }
    var mapReady by remember { mutableStateOf(false) }
    var radarTilesReady by remember { mutableStateOf(false) }
    var tileError by remember { mutableStateOf(false) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val lastStyledCity = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(6_000)
        hintVisible = false
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // 瓦片就绪信号：全帧以透明度 0 常驻渲染，地图空闲即代表本视野所有帧已到位
            addOnDidFinishLoadingMapListener {
                radarTilesReady = true
                tileError = false
            }
            addOnDidBecomeIdleListener { radarTilesReady = true }
            addOnDidFailLoadingMapListener { tileError = true }
            getMapAsync { readyMap ->
                readyMap.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                readyMap.setMinZoomPreference(RADAR_MIN_ZOOM)
                readyMap.setMaxZoomPreference(RADAR_MAX_ZOOM)
                mapRef.value = readyMap
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

    // 本地终端底图：不再加载 RainViewer 浅色远程样式（境内不稳且与黑底终端风冲突），
    // 背景 + 经纬网格 + 距离环全部本地生成；13 帧全部常驻，切换帧只改透明度，
    // 这样所有瓦片从进场就开始并行预取，播放不会出现空白夹帧。
    LaunchedEffect(mapRef.value, city.locationKey, timeline.host, framesKey, palette) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        mapReady = false
        radarTilesReady = false
        tileError = false
        val camera = readyMap.cameraPosition
        val builder = Style.Builder()
            .withLayer(
                BackgroundLayer("zhisheng-radar-background").withProperties(backgroundColor(palette.bg.toArgb())),
            )
            .withSource(GeoJsonSource("zhisheng-radar-grid", FeatureCollection.fromFeatures(graticuleFeatures())))
            .withLayer(
                LineLayer("zhisheng-radar-grid-layer", "zhisheng-radar-grid").withProperties(
                    lineColor(palette.cyan.toArgb()),
                    lineWidth(0.7f),
                    lineOpacity(0.22f),
                ),
            )
            .withSource(
                GeoJsonSource(
                    "zhisheng-radar-rings",
                    FeatureCollection.fromFeatures(radarRingFeatures(city.latitude, city.longitude)),
                ),
            )
            .withLayer(
                LineLayer("zhisheng-radar-rings-layer", "zhisheng-radar-rings").withProperties(
                    lineColor(palette.cyan.toArgb()),
                    lineWidth(1f),
                    lineOpacity(0.5f),
                    lineDasharray(arrayOf(3f, 3f)),
                ),
            )
        frames.forEach { frame ->
            builder.withSource(
                RasterSource(
                    radarSourceId(frame),
                    TileSet("2.1.0", timeline.tileTemplate(frame)).apply { maxZoom = 7f },
                    256,
                ),
            )
            builder.withLayer(
                RasterLayer(radarLayerId(frame), radarSourceId(frame)).withProperties(
                    rasterOpacity(if (frame.timeMillis == selectedTime) RADAR_FRAME_OPACITY else 0f),
                ),
            )
        }
        builder.withSource(
            GeoJsonSource("zhisheng-radar-city", Feature.fromGeometry(Point.fromLngLat(city.longitude, city.latitude))),
        )
        builder.withLayer(
            CircleLayer("zhisheng-radar-city-marker", "zhisheng-radar-city").withProperties(
                circleRadius(5.5f),
                circleColor(palette.orange.toArgb()),
                circleStrokeColor(android.graphics.Color.WHITE),
                circleStrokeWidth(1.5f),
            ),
        )
        readyMap.setStyle(builder) {
            if (lastStyledCity.value != city.locationKey) {
                readyMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(city.latitude, city.longitude)).zoom(RADAR_START_ZOOM).build(),
                    ),
                )
                lastStyledCity.value = city.locationKey
            } else {
                // 主题切换 / 帧目录刷新时保持用户当前的视角
                readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
            }
            mapReady = true
        }
    }

    LaunchedEffect(framesKey) {
        if (frames.none { it.timeMillis == selectedTime }) selectedTime = timeline.latest?.timeMillis ?: 0L
    }

    // 自动播放：等首屏瓦片就绪（最多 8 秒兜底）再起步，避免动画先于数据出现
    LaunchedEffect(playing, framesKey) {
        if (!playing || frames.size < 2) return@LaunchedEffect
        if (!radarTilesReady) {
            withTimeoutOrNull(TILE_WAIT_TIMEOUT_MS) { snapshotFlow { radarTilesReady }.first { it } }
        }
        while (true) {
            val idx = frames.indexOfFirst { it.timeMillis == selectedTime }.takeIf { it >= 0 } ?: frames.lastIndex
            delay(if (idx == frames.lastIndex) LATEST_FRAME_HOLD_MS else FRAME_INTERVAL_MS)
            selectedTime = frames[(idx + 1) % frames.size].timeMillis
        }
    }

    LaunchedEffect(mapRef.value, mapReady, selectedTime, framesKey) {
        if (!mapReady) return@LaunchedEffect
        mapRef.value?.getStyle { style ->
            frames.forEach { frame ->
                style.getLayer(radarLayerId(frame))?.setProperties(
                    rasterOpacity(if (frame.timeMillis == selectedTime) RADAR_FRAME_OPACITY else 0f),
                )
            }
        }
    }

    val selected = frames.firstOrNull { it.timeMillis == selectedTime } ?: timeline.latest
    val selectedIndex = selected?.let { sel -> frames.indexOfFirst { it.timeMillis == sel.timeMillis } }?.coerceAtLeast(0) ?: 0

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp)
                        .background(palette.surface.copy(alpha = 0.92f))
                        .border(1.dp, palette.cardBorder)
                        .clickable(role = Role.Button, onClickLabel = "返回", onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = palette.text)
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.background(palette.surface.copy(alpha = 0.92f)).border(1.dp, palette.cardBorder)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("雷达回波", style = MaterialTheme.typography.titleSmall, color = palette.orange, fontWeight = FontWeight.Bold)
                    Text(
                        "● ${city.displayName}  ${Fmt.coordinates(city.latitude, city.longitude)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                    )
                }
                Spacer(Modifier.weight(1f))
                OverlayAction("复位") {
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(city.latitude, city.longitude), RADAR_START_ZOOM),
                    )
                }
                Spacer(Modifier.width(8.dp))
                OverlayAction("说明") { showInfo = true }
            }
            val statusText = when {
                tileError -> "回波加载不稳定 · 稍候会自动重试"
                !radarTilesReady && mapReady -> "正在接收回波瓦片…"
                timeline.staleMetadata -> "帧目录来自本机缓存 · 时间可能滞后"
                else -> null
            }
            AnimatedVisibility(visible = hintVisible || statusText != null, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.background(palette.bg.copy(alpha = 0.82f))
                            .border(1.dp, palette.cardBorder)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (statusText != null) statusText else "双指缩放 · 单指拖动 · 时间轴可拖动",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.orange,
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(palette.bg.copy(alpha = 0.94f))
                .drawBehind { drawLine(palette.cardBorder, Offset.Zero, Offset(size.width, 0f), 2f) }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ZhishengMint,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${selected?.let { Fmt.date(it.timeMillis, utcOffsetSeconds) } ?: "--"} · 过去 2 小时 · 10 分钟一帧 · ${frames.size} 帧",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                    )
                }
                RadarPlayButton(playing = playing, enabled = frames.size > 1) { playing = !playing }
            }
            Spacer(Modifier.height(6.dp))
            RadarScrubber(
                frames = frames,
                selectedIndex = selectedIndex,
                utcOffsetSeconds = utcOffsetSeconds,
                onSelect = { index ->
                    frames.getOrNull(index)?.let { selectedTime = it.timeMillis }
                    playing = false
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarLegend(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "RainViewer ↗",
                    modifier = Modifier.clickable(role = Role.Button) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RAINVIEWER_URL))) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengMint,
                )
            }
        }

        if (showInfo) {
            RadarInfoDialog(timeline = timeline, onDismiss = { showInfo = false })
        }
    }
}

@Composable
private fun OverlayAction(label: String, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.background(palette.surface.copy(alpha = 0.92f))
            .border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.text)
    }
}

@Composable
private fun RadarPlayButton(playing: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val accent = if (playing) palette.mint else palette.orange
    Box(
        Modifier.width(108.dp).height(44.dp)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (playing) "暂停" else "播放") { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (playing) "II" else ">", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(if (playing) "暂停" else "播放", style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@Composable
private fun RadarScrubber(
    frames: List<RadarFrame>,
    selectedIndex: Int,
    utcOffsetSeconds: Int?,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalZhishengPalette.current
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    fun indexAt(x: Float): Int {
        if (frames.isEmpty() || trackWidthPx <= 0f) return 0
        return (x / trackWidthPx * frames.size).toInt().coerceIn(0, frames.lastIndex)
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(42.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(frames) { detectTapGestures { offset -> onSelect(indexAt(offset.x)) } }
                .pointerInput(frames) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> onSelect(indexAt(offset.x)) },
                        onHorizontalDrag = { change, _ -> onSelect(indexAt(change.position.x)) },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val n = frames.size.coerceAtLeast(1)
                val midY = size.height / 2f
                drawLine(palette.cardBorder, Offset(0f, midY), Offset(size.width, midY), 3f)
                val fillEnd = (selectedIndex + 1f) / n * size.width
                drawLine(
                    palette.mint.copy(alpha = 0.45f),
                    Offset(0f, midY),
                    Offset(fillEnd, midY),
                    6f,
                    cap = StrokeCap.Butt,
                )
                for (i in 0..n) {
                    val x = i / n.toFloat() * size.width
                    drawLine(
                        palette.textTertiary.copy(alpha = 0.7f),
                        Offset(x, midY - 4f),
                        Offset(x, midY + 4f),
                        1.5f,
                    )
                }
                val selX = (selectedIndex + 0.5f) / n * size.width
                drawLine(palette.orange, Offset(selX, 4f), Offset(selX, size.height - 4f), 3f)
                drawRect(palette.orange, topLeft = Offset(selX - 5f, midY - 5f), size = Size(10f, 10f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(
                frames.firstOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "最新 ${frames.lastOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.mint,
            )
        }
    }
}

@Composable
private fun RadarLegend(modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val colors = listOf(
        Color(0xFF27D7FF) to "弱",
        Color(0xFF45FF70) to "轻",
        Color(0xFFFFE24B) to "中",
        Color(0xFFFF8A35) to "强",
        Color(0xFFFF3F68) to "很强",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 7.dp).background(color))
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            }
        }
    }
}

@Composable
private fun RadarInfoDialog(timeline: RadarTimeline, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(role = Role.Button, onClickLabel = "关闭") { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据说明", style = MaterialTheme.typography.titleMedium, color = palette.orange)
                Text("RAINVIEWER // PAST RADAR", style = MaterialTheme.typography.labelMedium, color = palette.cyan)
                Text(
                    "· 显示约过去 2 小时的降水回波，10 分钟一帧，不是未来预测；\n" +
                        "· 图上无明显回波，不等于地面一定无降水；\n" +
                        "· 虚线圆环距市中心 50 / 100 / 150 / 200 公里；\n" +
                        "· 回波数据来自 RainViewer，仅覆盖有雷达站的部分区域。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Text(
                    if (timeline.staleMetadata) "META // 网络不稳定，当前使用已保存的帧目录" else "META // 帧目录实时获取 · 地图瓦片由引擎缓存",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timeline.staleMetadata) palette.orange else palette.textTertiary,
                )
                OfficialRadarLink()
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().background(palette.mint.copy(alpha = 0.14f))
                        .clickable(role = Role.Button) { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("关闭", style = MaterialTheme.typography.labelMedium, color = palette.mint)
                }
            }
        }
    }
}

@Composable
private fun OfficialRadarLink() {
    val context = LocalContext.current
    Text(
        "中央气象台官方雷达图  ↗",
        modifier = Modifier.clickable(role = Role.Button) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_RADAR_URL))) }
        }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = ZhishengMint,
    )
}

private fun radarSourceId(frame: RadarFrame) = "radar-source-${frame.timeMillis}"
private fun radarLayerId(frame: RadarFrame) = "radar-layer-${frame.timeMillis}"

// 经纬网格：全球 2° 一格，2 点直线在墨卡托投影下即完整经线/纬线
private fun graticuleFeatures(): List<Feature> {
    val features = mutableListOf<Feature>()
    var lon = -180.0
    while (lon <= 180.0) {
        features += Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(Point.fromLngLat(lon, -85.0), Point.fromLngLat(lon, 85.0)),
            ),
        )
        lon += GRATICULE_STEP_DEG
    }
    var lat = -84.0
    while (lat <= 84.0) {
        features += Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(Point.fromLngLat(-180.0, lat), Point.fromLngLat(180.0, lat)),
            ),
        )
        lat += GRATICULE_STEP_DEG
    }
    return features
}

private fun radarRingFeatures(latitude: Double, longitude: Double): List<Feature> =
    RADAR_RING_KM.map { km -> Feature.fromGeometry(ringPolygon(latitude, longitude, km)) }

private fun ringPolygon(latitude: Double, longitude: Double, radiusKm: Double): Polygon {
    val points = (0..RING_STEPS).map { step ->
        val bearing = Math.toRadians(step * 360.0 / RING_STEPS)
        val (pLat, pLon) = destinationPoint(latitude, longitude, radiusKm, bearing)
        Point.fromLngLat(pLon, pLat)
    }
    return Polygon.fromLngLats(listOf(points))
}

// 球面终点公式：给定起点、距离、方位角求落点，保证距离环是真实地理圆
private fun destinationPoint(latitudeDeg: Double, longitudeDeg: Double, distanceKm: Double, bearingRad: Double): Pair<Double, Double> {
    val earthRadiusKm = 6371.0088
    val angular = distanceKm / earthRadiusKm
    val lat1 = Math.toRadians(latitudeDeg)
    val lon1 = Math.toRadians(longitudeDeg)
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearingRad))
    val lon2 = lon1 + atan2(sin(bearingRad) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
    val lonDeg = (Math.toDegrees(lon2) + 540.0).mod(360.0) - 180.0
    return Math.toDegrees(lat2) to lonDeg
}
