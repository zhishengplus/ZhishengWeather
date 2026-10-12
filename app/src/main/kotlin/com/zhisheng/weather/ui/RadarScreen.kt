package com.zhisheng.weather.ui

import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.VistaMapToolbar
import com.zhisheng.weather.ui.components.VistaMapTool

import com.zhisheng.weather.ui.theme.isPhosphorVista

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.data.CaiyunRadarException
import com.zhisheng.weather.data.CaiyunRadarReason
import com.zhisheng.weather.data.CaiyunRadarRepository
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.RadarCoverageState
import com.zhisheng.weather.model.RadarFeed
import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarSource
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.home.Scanlines
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterFadeDuration
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.sin

private const val OFFICIAL_RADAR_URL = "https://www.nmc.cn/publish/radar/chinaall.html"
private const val RAINVIEWER_URL = "https://www.rainviewer.com/"
private const val CAIYUN_RADAR_DOC_URL = "https://docs.caiyunapp.com/weather-api/v1/6-radar.html"

// 相机：回波瓦片最高 z7；底图允许继续放大，方便用户像普通地图一样辨认道路与位置。
private const val RADAR_START_ZOOM = 6.8
private const val RADAR_MIN_ZOOM = 3.0
private const val RADAR_MAX_ZOOM = 20.0

private const val FRAME_INTERVAL_MS = 650L
private const val LATEST_FRAME_HOLD_MS = 1400L
private const val TILE_WAIT_TIMEOUT_MS = 8_000L
private const val METADATA_REFRESH_MS = 5 * 60_000L
private const val RADAR_FRAME_OPACITY = 0.82f
private const val RADAR_PREFETCH_OPACITY = 0.001f
private const val RADAR_CROSSFADE_MS = 240L


private sealed interface RadarScreenState {
    data object Loading : RadarScreenState
    data class Ready(
        val source: RadarSource,
        val feed: RadarFeed,
        val staleMetadata: Boolean = false,
        val coverage: RadarCoverageState = RadarCoverageState.UNKNOWN,
    ) : RadarScreenState
    data class Error(val message: String, val detail: String? = null) : RadarScreenState
}

// 加载指定数据源的帧集；失败抛异常，由调用方决定回退还是报错
private suspend fun radarLoadState(city: City, source: RadarSource): RadarScreenState.Ready {
    when (source) {
        RadarSource.RAINVIEWER -> {
            val timeline = RadarRepository.loadTimeline(city)
            currentCoroutineContext().ensureActive()
            if (timeline.frames.isEmpty()) throw CaiyunRadarException(
                CaiyunRadarReason.EMPTY_FRAMES, "当前位置暂时没有可用回波帧",
            )
            return RadarScreenState.Ready(
                source = RadarSource.RAINVIEWER,
                feed = RadarFeed(past = timeline.frames, future = timeline.futureFrames, host = timeline.host),
                staleMetadata = timeline.staleMetadata,
                coverage = timeline.coverage,
            )
        }
        RadarSource.CAIYUN -> {
            val token = SecretStore.currentCaiyun().token
            val feed = CaiyunRadarRepository.loadFeed(city.longitude, city.latitude, token)
            currentCoroutineContext().ensureActive()
            return RadarScreenState.Ready(source = RadarSource.CAIYUN, feed = feed)
        }
    }
}

internal fun caiyunRadarMessage(reason: CaiyunRadarReason): String = when (reason) {
    CaiyunRadarReason.NOT_CONFIGURED -> "未配置彩云 Token"
    CaiyunRadarReason.NO_PERMISSION -> "彩云雷达权限未开通"
    CaiyunRadarReason.SERVICE_UNAVAILABLE -> "彩云雷达服务不可用"
    CaiyunRadarReason.EMPTY_FRAMES -> "彩云未返回可用帧"
}

@Composable
internal fun RadarMapScreen(
    city: City?,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val storedSource by SettingsRepository.radarSource.collectAsState(initial = null)
    val accepted by rememberRainviewerDisclosureAccepted()
    val switchScope = rememberCoroutineScope()
    val radarSource = storedSource
    var showErrorSources by remember { mutableStateOf(false) }
    val caiyunRuntime by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    if (radarSource == null) {
        RadarLoading(onBack)
        return
    }
    if (radarSource == RadarSource.RAINVIEWER && !accepted) {
        RadarUseNotice(onBack) { acceptRainviewerDisclosure(context) }
        return
    }
    var state by remember(city?.locationKey, radarSource) { mutableStateOf<RadarScreenState>(RadarScreenState.Loading) }
    val refreshClock = remember(city?.locationKey, radarSource) {
        RadarRefreshClock(SystemClock.elapsedRealtime())
    }

    // One lifecycle-owned loop survives the Loading/Ready/Error transitions it produces.
    // Expired frames disappear before reload, but expiration never blocks the new request.
    LaunchedEffect(city?.locationKey, retry, radarSource, lifecycle) {
        var attempted = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (city == null) {
                state = RadarScreenState.Error("先在主页选择一座城市")
                return@repeatOnLifecycle
            }
            while (true) {
                val previous = state as? RadarScreenState.Ready
                val usable = previous?.let {
                    radarObservationUsable(it.feed.past.maxOfOrNull(RadarFrame::timeMillis), System.currentTimeMillis())
                } == true
                val reloadImmediately = !attempted || state == RadarScreenState.Loading || (previous != null && !usable)
                if (!reloadImmediately) {
                    delay(refreshClock.remainingDelay(SystemClock.elapsedRealtime(), METADATA_REFRESH_MS))
                }
                // The frame can expire while waiting for the normal refresh interval.
                val previousUsable = previous?.let {
                    radarObservationUsable(it.feed.past.maxOfOrNull(RadarFrame::timeMillis), System.currentTimeMillis())
                } == true
                attempted = true
                refreshClock.attempted(SystemClock.elapsedRealtime())
                when (val result = reloadRadarObservation(previous, previousUsable,
                    onLoading = { state = RadarScreenState.Loading },
                    load = { radarLoadState(city, radarSource) })) {
                    is RadarReloadResult.Loaded -> {
                        if (!result.stale) refreshClock.succeeded(SystemClock.elapsedRealtime())
                        state = result.value.copy(staleMetadata = result.value.staleMetadata || result.stale)
                    }
                    is RadarReloadResult.Failed -> {
                        val error = result.error
                        state = if (error is CaiyunRadarException && radarSource == RadarSource.CAIYUN) {
                            RadarScreenState.Error(caiyunRadarMessage(error.reason),
                                "所选数据源未改变，可重试或改用 RainViewer 历史回波。")
                        } else RadarScreenState.Error("雷达数据连接失败", "暂无当前可用的雷达观测，请重试或查看官方图。")
                    }
                }
            }
        }
    }
    if (showErrorSources) RadarSourceDialog(radarSource, caiyunRuntime.ready,
        onSelect = { chosen -> showErrorSources = false; switchScope.launch { SettingsRepository.setRadarSource(chosen) } },
        onDismiss = { showErrorSources = false })
    Box(Modifier.fillMaxSize().zhishengScreen()) {
        when (val current = state) {
            RadarScreenState.Loading -> RadarLoading(onBack)
            is RadarScreenState.Error -> RadarError(current.message, current.detail, onBack,
                onRetry = { retry++ },
                onSwitchSource = { showErrorSources = true })
            is RadarScreenState.Ready -> RadarInstrumentPage(
                city = city!!,
                source = current.source,
                feed = current.feed,
                staleMetadata = current.staleMetadata,
                coverage = current.coverage,
                fallbackNotice = null,
                utcOffsetSeconds = utcOffsetSeconds,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun RadarUseNotice(onBack: () -> Unit, onAccept: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().zhishengScreen()) {

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("RainViewer 历史回波", color = palette.text, style = MaterialTheme.typography.titleLarge)
            Text("用于个人、学习和小型非商业社区参考。公开接口提供过去约两小时的雷达拼图，不提供未来回波或红外卫星图。",
                color = palette.textSecondary)
            Text("覆盖因地区而异，图上无颜色不代表地面没有降雨。放大只方便定位，回波最高精度为地图 7 级。防灾以当地气象部门为准。",
                color = palette.textSecondary)
            Text("天气回波由 RainViewer 提供 ↗", color = palette.mint,
                modifier = Modifier.clickable(role = Role.Button) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rainviewer.com/api.html"))) }
                }.padding(vertical = 8.dp))
            Box(Modifier.fillMaxWidth().zhishengCompactPanel().clickable(role = Role.Button, onClick = onAccept)
                .padding(16.dp), contentAlignment = Alignment.Center) {
                Text("了解并打开地图", color = palette.mint)
            }
            OfficialRadarLink()
        }
    }
}

@Composable
private fun RadarLoading(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (isPhosphorVista) {
            // The enclosing precipitation page owns the header.
        } else {

        }
        FeatureBootLoader(
            channel = "雷达观测",
            lines = if (isPhosphorVista) emptyList() else listOf(
                "RADAR PORT .......... OPEN",
                "LOAD MAP PROJECTION . OK",
                "SYNC FRAME MANIFEST ...",
                "WARM RASTER LAYERS ....",
            ),
            status = if (isPhosphorVista) "正在看看附近下没下雨" else "正在接收当前位置的最新回波",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RadarError(message: String, detail: String?, onBack: () -> Unit, onRetry: () -> Unit,
    onSwitchSource: (() -> Unit)? = null) {
    val palette = LocalZhishengPalette.current
    Column(Modifier.fillMaxSize()) {
        if (isPhosphorVista) {
            // Keep recovery actions scrollable beneath the shared header.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    vistaRadarErrorTitle(message),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(if (onSwitchSource == null) "检查网络后可以再试一次" else "检查网络或服务接入，也可以更换数据源",
                    style = MaterialTheme.typography.bodySmall, color = palette.textTertiary)
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.zhishengCompactPanel()
                        .clickable(role = Role.Button, onClick = onRetry)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("再试一次", style = MaterialTheme.typography.labelMedium, color = palette.mint)
                }
                Spacer(Modifier.height(12.dp))
                onSwitchSource?.let { switch -> RadarTextAction("更换数据源", "选择另一套雷达图", switch) }
                OfficialRadarLink()
            }
            return
        }

        Box(Modifier.weight(1f)) {
            FeatureErrorState(
                title = message,
                action = "重新读取",
                onAction = onRetry,
                extra = {
                    if (!detail.isNullOrBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    OfficialRadarLink()
                    onSwitchSource?.let { switch -> RadarTextAction("更换数据源", "选择另一套雷达图", switch) }
                },
            )
        }
    }
}

/** Await the callback so a completed/cancelled effect cannot attach an old frame to a new style. */
private suspend fun useRadarStyle(map: MapLibreMap, action: (Style) -> Unit) =
    suspendCancellableCoroutine<Unit> { continuation ->
        map.getStyle { style ->
            if (continuation.isActive) continuation.resumeWith(runCatching { action(style) })
        }
    }

@Composable
private fun RadarInstrumentPage(
    city: City,
    source: RadarSource,
    feed: RadarFeed,
    staleMetadata: Boolean,
    coverage: RadarCoverageState,
    fallbackNotice: String?,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = LocalZhishengPalette.current
    val frames = feed.past
    val futureFrames = feed.future
    val playbackFrames = feed.playbackFrames
    val nowBoundary = feed.nowBoundaryIndex
    val futureUnlocked = futureFrames.isNotEmpty()
    val framesKey = remember(frames, futureFrames) {
        playbackFrames.joinToString("|") { it.frameKey }
    }
    var showSourceDialog by remember { mutableStateOf(false) }
    val caiyunRt by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val scope = rememberCoroutineScope()

    var selectedTime by rememberSaveable(city.locationKey) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(city.locationKey) { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(true) }
    val mapReadyState = remember { mutableStateOf(false) }
    var mapReady by mapReadyState
    var radarTilesReady by remember { mutableStateOf(false) }
    var tileError by remember { mutableStateOf(false) }
    val baseStyleAppliedState = remember { mutableStateOf(false) }
    var baseStyleApplied by baseStyleAppliedState
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val lastStyledCity = remember { mutableStateOf<String?>(null) }
    var styleEpoch by remember { mutableIntStateOf(0) }
    val currentSource by rememberUpdatedState(source)
    val imageStates = remember(source, framesKey, styleEpoch) { mutableStateMapOf<String, RadarImageState>() }
    var imageRetry by remember(source, framesKey, styleEpoch) { mutableIntStateOf(0) }
    var overlaysInstalled by remember(source, framesKey, styleEpoch) { mutableStateOf(false) }
    fun selectedFrameReady(): Boolean {
        val frame = playbackFrames.firstOrNull { it.timeMillis == selectedTime }
        return overlaysInstalled && radarFrameReady(frame, frame?.let { imageStates[it.frameKey] }, radarTilesReady)
    }

    LaunchedEffect(Unit) {
        delay(6_000)
        hintVisible = false
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // 全帧以极低透明度参与渲染，地图空闲后才开始播放，避免第一轮逐帧白等。
            addOnDidFinishLoadingMapListener {
                if (mapReadyState.value && currentSource == RadarSource.RAINVIEWER) radarTilesReady = true
            }
            addOnDidBecomeIdleListener {
                if (mapReadyState.value && currentSource == RadarSource.RAINVIEWER) {
                    radarTilesReady = true
                    tileError = false
                }
            }
            addOnDidFailLoadingMapListener {
                tileError = true
            }
            getMapAsync { readyMap ->
                readyMap.uiSettings.apply {
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

    // 开源底图只在主题切换时重载；回波帧变化只替换叠加层，避免整图闪白。
    LaunchedEffect(mapRef.value, palette.isLight) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        mapReady = false
        radarTilesReady = false
        tileError = false
        baseStyleApplied = false
        val camera = readyMap.cameraPosition
        suspendCancellableCoroutine<Unit> { continuation ->
            readyMap.setStyle(weatherMapBaseStyle(palette)) { _ ->
                if (continuation.isActive) continuation.resumeWith(runCatching {
                    if (lastStyledCity.value != null) readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                    baseStyleApplied = true
                    mapReady = true
                    styleEpoch++
                    Unit
                })
            }
        }
    }

    LaunchedEffect(mapRef.value, mapReady, styleEpoch, city.locationKey, feed.host, framesKey, source, palette) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        if (!mapReady || styleEpoch == 0) return@LaunchedEffect
        radarTilesReady = false
        tileError = false
        overlaysInstalled = false
        val initialTime = selectedTime.takeIf { it != 0L }
            ?: feed.past.lastOrNull()?.timeMillis
            ?: playbackFrames.firstOrNull()?.timeMillis
            ?: 0L
        useRadarStyle(readyMap) { style ->
            clearRadarOverlays(style)
            val labelAnchor = mapLabelAnchorId(style)
            if (source == RadarSource.CAIYUN) {
                installCaiyunOverlays(style, frames + futureFrames, initialTime, labelAnchor)
            } else {
                installRadarOverlays(
                    style = style,
                    host = feed.host,
                    frames = frames,
                    futureFrames = futureFrames,
                    selectedTime = initialTime,
                    baseLabelLayerId = labelAnchor,
                )
            }
            installCityMarker(style, city, palette)
            overlaysInstalled = true
            if (lastStyledCity.value != city.locationKey) {
                readyMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(city.latitude, city.longitude)).zoom(RADAR_START_ZOOM).build(),
                    ),
                )
                lastStyledCity.value = city.locationKey
            }
        }
    }

    LaunchedEffect(framesKey) {
        if (playbackFrames.none { it.timeMillis == selectedTime }) {
            selectedTime = feed.past.lastOrNull()?.timeMillis
                ?: playbackFrames.firstOrNull()?.timeMillis
                ?: 0L
        }
    }

    LaunchedEffect(playing, framesKey, lifecycle, source, styleEpoch) {
        if (!playing || playbackFrames.size < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (source == RadarSource.CAIYUN) {
                    snapshotFlow { selectedFrameReady() }.first { it }
                } else if (!radarTilesReady) {
                    withTimeoutOrNull(TILE_WAIT_TIMEOUT_MS) { snapshotFlow { radarTilesReady }.first { it } }
                }
                val idx = playbackFrames.indexOfFirst { it.timeMillis == selectedTime }.takeIf { it >= 0 } ?: playbackFrames.lastIndex
                delay(if (idx == playbackFrames.lastIndex) LATEST_FRAME_HOLD_MS else FRAME_INTERVAL_MS)
                selectedTime = playbackFrames[(idx + 1) % playbackFrames.size].timeMillis
            }
        }
    }

    LaunchedEffect(mapRef.value, mapReady, selectedTime, framesKey, imageStates.toMap(), overlaysInstalled) {
        if (!mapReady || !overlaysInstalled) return@LaunchedEffect
        mapRef.value?.let { readyMap -> useRadarStyle(readyMap) { style ->
            (frames.map { it to false } + futureFrames.map { it to true }).forEach { (frame, future) ->
                val layerId = if (frame.isImageFrame) caiyunLayerId(frame) else radarLayerId(frame, future)
                style.getLayer(layerId)?.setProperties(rasterOpacity(radarFrameOpacity(frame, selectedTime, imageStates[frame.frameKey])))
            }
        } }
    }

    // Only a successfully attached bitmap marks an image ready. Leaving, switching or restyling cancels the load.
    LaunchedEffect(mapRef.value, mapReady, overlaysInstalled, selectedTime, source, framesKey, lifecycle, imageRetry, styleEpoch) {
        if (!mapReady || !overlaysInstalled || source != RadarSource.CAIYUN || playbackFrames.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val selIdx = playbackFrames.indexOfFirst { it.timeMillis == selectedTime }.coerceAtLeast(0)
            val pending = listOfNotNull(playbackFrames.getOrNull(selIdx),
                playbackFrames.getOrNull((selIdx + 1) % playbackFrames.size)).distinctBy { it.frameKey }.filter(RadarFrame::isImageFrame)
            pending.forEach { frame ->
                if (imageStates[frame.frameKey] == RadarImageState.READY) return@forEach
                imageStates[frame.frameKey] = RadarImageState.LOADING
                try {
                    val bitmap = CaiyunRadarRepository.loadBitmap(context, frame)
                    currentCoroutineContext().ensureActive()
                    if (bitmap == null) {
                        imageStates[frame.frameKey] = RadarImageState.FAILED
                        if (frame.timeMillis == selectedTime) playing = false
                    } else {
                        val readyMap = mapRef.value ?: return@forEach
                        useRadarStyle(readyMap) { style ->
                            val image = style.getSourceAs<ImageSource>(caiyunSourceId(frame))
                            if (image != null) {
                                image.setImage(bitmap)
                                imageStates[frame.frameKey] = RadarImageState.READY
                            } else {
                                imageStates[frame.frameKey] = RadarImageState.FAILED
                                if (frame.timeMillis == selectedTime) playing = false
                            }
                        }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    imageStates[frame.frameKey] = RadarImageState.FAILED
                    if (frame.timeMillis == selectedTime) playing = false
                }
            }
        }
    }

    val selected = playbackFrames.firstOrNull { it.timeMillis == selectedTime } ?: feed.past.lastOrNull()
    val selectedIndex = playbackFrames.indexOfFirst { it.timeMillis == selected?.timeMillis }.coerceAtLeast(0)
    val frameReady = selectedFrameReady()
    val imageFailed = source == RadarSource.CAIYUN && selected?.let { imageStates[it.frameKey] } == RadarImageState.FAILED
    val statusText = if (imageFailed) "这一帧未加载成功" else if (source == RadarSource.CAIYUN && !frameReady && mapReady) "正在加载这时刻的雷达图" else if (isPhosphorVista) {
        vistaRadarMapStatus(baseStyleApplied, staleMetadata, tileError, frameReady, mapReady)
    } else when {
        !baseStyleApplied -> "正在载入开源底图"
        staleMetadata -> "暂时显示上次更新的雷达图"
        tileError -> "雷达图加载较慢，正在重试"
        !frameReady && mapReady -> "正在加载雷达图"
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 与主页同一台「屏幕」的 CRT 扫描线；不拦截触摸，仅在静止图层之上
        if (!isPhosphorVista) Scanlines()

        RadarTopChrome(
            city = city,
            source = source,
            coverage = coverage,
            hintVisible = hintVisible,
            statusText = fallbackNotice?.let { if (isPhosphorVista) vistaRadarFallbackNotice(it) else it } ?: statusText,
            onBack = onBack,
            onRecenter = {
                mapRef.value?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(city.latitude, city.longitude), RADAR_START_ZOOM),
                )
            },
            onInfo = { showInfo = true },
            onSource = { showSourceDialog = true },
        )

        RadarScanOverlay(visible = mapReady && !frameReady && !imageFailed)
        if (imageFailed) {
            Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 104.dp)
                .zhishengPanel().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { imageRetry++ }) { Text("重试这一帧", color = palette.mint) }
                TextButton(onClick = { showSourceDialog = true }) { Text("更换来源", color = palette.mint) }
            }
        }

        RadarBottomController(
            modifier = Modifier.align(Alignment.BottomCenter).heightIn(max = maxHeight * .48f).verticalScroll(rememberScrollState()),
            source = source,
            frames = playbackFrames,
            nowIndex = nowBoundary,
            futureUnlocked = futureUnlocked,
            selected = selected,
            selectedIndex = selectedIndex,
            playing = playing,
            utcOffsetSeconds = utcOffsetSeconds,
            onTogglePlaying = {
                if (imageFailed) { imageRetry++; playing = true } else playing = !playing
            },
            onSelect = { index ->
                playbackFrames.getOrNull(index)?.let { selectedTime = it.timeMillis }
                playing = false
            },
        )

        if (showInfo) {
            RadarInfoDialog(
                source = source,
                staleMetadata = staleMetadata,
                onDismiss = { showInfo = false },
            )
        }

        if (showSourceDialog) {
            RadarSourceDialog(
                current = source,
                caiyunConfigured = caiyunRt.ready,
                onSelect = { newSource ->
                    showSourceDialog = false
                    if (newSource != source) {
                        scope.launch { SettingsRepository.setRadarSource(newSource) }
                    }
                },
                onDismiss = { showSourceDialog = false },
            )
        }
    }
}

@Composable
private fun RadarTopChrome(
    city: City,
    source: RadarSource,
    coverage: RadarCoverageState,
    hintVisible: Boolean,
    statusText: String?,
    onBack: () -> Unit,
    onRecenter: () -> Unit,
    onInfo: () -> Unit,
    onSource: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val (coverageText, coverageColor) = if (isPhosphorVista) {
        vistaRadarCoverageLine(source, coverage) to when {
            source == RadarSource.CAIYUN -> palette.mint
            coverage == RadarCoverageState.AVAILABLE -> palette.mint
            coverage == RadarCoverageState.OUTSIDE -> palette.orange
            else -> palette.textTertiary
        }
    } else if (source == RadarSource.CAIYUN) {
        "全国拼图 · 企业套餐" to palette.mint
    } else when (coverage) {
        RadarCoverageState.AVAILABLE -> "雷达覆盖内" to palette.mint
        RadarCoverageState.OUTSIDE -> "当前区域暂缺雷达覆盖" to palette.orange
        RadarCoverageState.UNKNOWN -> "覆盖状态待确认" to palette.textTertiary
    }
    if (isPhosphorVista) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).zhishengPanel().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(coverageText, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = palette.text)
                VistaMapTool(R.drawable.ph_crosshair, "回到这里", onRecenter)
                VistaMapTool(R.drawable.ph_database, "更换回波来源", onSource)
                VistaMapTool(R.drawable.ph_info, "回波说明", onInfo)
            }
            if (hintVisible || statusText != null) Text(
                statusText ?: "两指放大能看到街道",
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadarIconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f).height(44.dp)
                    .background(palette.surface.copy(alpha = 0.94f))
                    .border(1.dp, palette.cardBorder)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text("雷达 · ${city.displayName}", style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("● $coverageText", style = MaterialTheme.typography.labelSmall, color = coverageColor, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            RadarTextAction("◎", "回到当前城市", onRecenter)
            Spacer(Modifier.width(6.dp))
            RadarTextAction("来源", "切换雷达数据源", onSource)
            Spacer(Modifier.width(6.dp))
            RadarIconAction(Icons.Filled.Info, "雷达说明", onInfo)
        }
        AnimatedVisibility(visible = hintVisible || statusText != null, enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.background(palette.bg.copy(alpha = 0.86f)).border(1.dp, palette.cardBorder)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        statusText ?: "双指缩放 · 双击放大 · 拖动地图",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (statusText != null) palette.orange else palette.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarIconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.size(44.dp).background(palette.surface.copy(alpha = 0.94f)).border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = palette.text)
    }
}

@Composable
private fun RadarTextAction(label: String, description: String, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).background(palette.surface.copy(alpha = 0.94f)).border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (label.length == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium, color = palette.text)
    }
}

@Composable
private fun RadarScanOverlay(visible: Boolean) {
    if (!visible) return
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    val animationsEnabled = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
            .getOrDefault(true)
    }
    val angle = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "radar-scan")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
            label = "radar-scan-angle",
        )
        animated
    } else {
        315f
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(118.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.42f
            drawCircle(palette.bg.copy(alpha = 0.72f), radius + 14f, center)
            drawCircle(palette.cardBorder, radius, center, style = Stroke(width = 2f))
            drawCircle(palette.cardBorder, radius * 0.58f, center, style = Stroke(width = 1f))
            drawLine(palette.cardBorder, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
            drawLine(palette.cardBorder, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
            val rad = Math.toRadians(angle.toDouble())
            val end = Offset(center.x + cos(rad).toFloat() * radius, center.y + sin(rad).toFloat() * radius)
            drawLine(palette.cyan, center, end, 3f, cap = StrokeCap.Round)
            drawCircle(palette.orange, 4f, center)
        }
        Spacer(Modifier.height(8.dp))
        Text("正在加载降雨情况", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
internal fun RadarBottomController(
    modifier: Modifier,
    source: RadarSource,
    frames: List<RadarFrame>,
    nowIndex: Int,
    futureUnlocked: Boolean,
    selected: RadarFrame?,
    selectedIndex: Int,
    playing: Boolean,
    utcOffsetSeconds: Int?,
    onTogglePlaying: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val viewingFuture = selectedIndex > nowIndex
    val panel = if (isPhosphorVista) {
        Modifier.fillMaxWidth().zhishengPanel().padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        Modifier.fillMaxWidth().background(palette.surface.copy(alpha = 0.96f)).border(1.dp, palette.cardBorder).padding(horizontal = 12.dp, vertical = 10.dp)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = if (isPhosphorVista) 12.dp else 10.dp, vertical = 8.dp)) {
        Column(panel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarPlayButton(playing = playing, enabled = frames.size > 1, onToggle = onTogglePlaying)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--",
                        style = if (isPhosphorVista) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        color = if (isPhosphorVista) palette.text else if (viewingFuture) palette.cyan else palette.mint,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isPhosphorVista) {
                            if (viewingFuture) "未来外推 · ${source.cn}" else "历史回波 · ${source.cn}"
                        } else {
                            "${selected?.let { Fmt.date(it.timeMillis, utcOffsetSeconds) } ?: "--"} · " +
                                if (viewingFuture) "未来外推 · ${source.cn} · 约5分钟/帧" else "过去回波 · ${source.cn} · 实测"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isPhosphorVista) {
                    Text("${selectedIndex + 1}/${frames.size}", style = MaterialTheme.typography.labelSmall, color = palette.orange)
                }
            }
            Spacer(Modifier.height(4.dp))
            RadarScrubber(frames, nowIndex, selectedIndex, utcOffsetSeconds, onSelect)
            vistaRadarLimitLine(source, futureUnlocked)?.takeIf { isPhosphorVista }?.let { line ->
                Spacer(Modifier.height(6.dp))
                Text(line, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.fillMaxWidth())
            }
            if (!isPhosphorVista && !futureUnlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (source == RadarSource.RAINVIEWER) {
                        "RainViewer 仅提供过去 2 小时实测回波 · 未来外推请切换彩云拼图"
                    } else {
                        "彩云未返回未来外推帧 · 可能正在生成，稍后自动重试"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(5.dp))
            RadarLegend(source, Modifier.fillMaxWidth())
            RadarAttribution(Modifier.fillMaxWidth(), source)
        }
    }
}

@Composable
private fun RadarPlayButton(playing: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    val accent = if (isPhosphorVista) palette.text else if (playing) palette.mint else palette.orange
    Box(
        Modifier.size(48.dp)
            .clip(chrome.compactShape)
            .then(if (isPhosphorVista) Modifier.zhishengCompactPanel() else Modifier.background(accent.copy(alpha = 0.12f), chrome.compactShape).border(1.dp, accent, chrome.compactShape))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (playing) "暂停" else "播放", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (playing) "Ⅱ" else "▶",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RadarScrubber(
    frames: List<RadarFrame>,
    nowIndex: Int,
    selectedIndex: Int,
    utcOffsetSeconds: Int?,
    onSelect: (Int) -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val roundThumb = isPhosphorVista
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    fun indexAt(x: Float): Int {
        if (frames.isEmpty() || trackWidthPx <= 0f) return 0
        return (x / trackWidthPx * frames.size).toInt().coerceIn(0, frames.lastIndex)
    }
    // 分界刻度：过去/未来两段的交界，「现在」用橙色标记
    val boundaryTick = (nowIndex + 1).coerceIn(0, frames.size)
    val hasFutureSegment = boundaryTick < frames.size
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
                if (hasFutureSegment) {
                    // 过去段：mint 已播放填充；未来段：cyan 已播放填充
                    val boundaryX = boundaryTick / n.toFloat() * size.width
                    val pastFillEnd = if (selectedIndex <= nowIndex) {
                        (selectedIndex + 1f) / n * size.width
                    } else {
                        boundaryX
                    }
                    drawLine(
                        palette.mint.copy(alpha = 0.45f),
                        Offset(0f, midY),
                        Offset(pastFillEnd, midY),
                        6f,
                        cap = StrokeCap.Butt,
                    )
                    if (selectedIndex > nowIndex) {
                        val futureFillEnd = (selectedIndex + 1f) / n * size.width
                        drawLine(
                            palette.cyan.copy(alpha = 0.40f),
                            Offset(boundaryX, midY),
                            Offset(futureFillEnd, midY),
                            6f,
                            cap = StrokeCap.Butt,
                        )
                    }
                } else {
                    val fillEnd = (selectedIndex + 1f) / n * size.width
                    drawLine(
                        palette.mint.copy(alpha = 0.45f),
                        Offset(0f, midY),
                        Offset(fillEnd, midY),
                        6f,
                        cap = StrokeCap.Butt,
                    )
                }
                for (i in 0..n) {
                    val x = i / n.toFloat() * size.width
                    val tickColor = when {
                        i == boundaryTick -> palette.orange
                        hasFutureSegment && i > boundaryTick -> palette.cyan.copy(alpha = 0.55f)
                        else -> palette.textTertiary.copy(alpha = 0.7f)
                    }
                    drawLine(
                        tickColor,
                        Offset(x, midY - 4f),
                        Offset(x, midY + 4f),
                        if (i == boundaryTick) 2f else 1.5f,
                    )
                }
                val selX = (selectedIndex + 0.5f) / n * size.width
                drawLine(palette.orange, Offset(selX, 4f), Offset(selX, size.height - 4f), 3f)
                if (roundThumb) {
                    drawCircle(palette.orange, 7f, Offset(selX, midY))
                } else {
                    drawRect(palette.orange, topLeft = Offset(selX - 5f, midY - 5f), size = Size(10f, 10f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(
                if (isPhosphorVista) {
                    "刚才 ${frames.firstOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}"
                } else {
                    "过去 ${frames.firstOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "现在 ${radarNowFrame(frames, nowIndex)?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.orange,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasFutureSegment) {
                    "未来 ${frames.lastOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}"
                } else {
                    "最新 ${frames.lastOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (hasFutureSegment) palette.cyan else palette.mint,
            )
        }
    }
}

@Composable
private fun RadarLegend(source: RadarSource, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    if (source == RadarSource.CAIYUN) {
        Text("回波配色以彩云原图说明为准", modifier, color = palette.textTertiary,
            style = MaterialTheme.typography.labelSmall)
        return
    }
    // RainViewer Universal Blue (scheme 2), official 15/20/30/35/45 dBZ samples.
    val colors = listOf(
        Color(0xFF88DDEE), Color(0xFF00A3E0), Color(0xFF005588),
        Color(0xFFFFEE00), Color(0xFFFF4400),
    )
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(6.dp)) {
            colors.forEach { color -> Box(Modifier.weight(1f).fillMaxSize().background(color)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("弱回波 · 15 dBZ", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("45 dBZ · 强回波", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
        }
    }
}

@Composable
private fun RadarAttribution(modifier: Modifier = Modifier, source: RadarSource) {
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (source == RadarSource.CAIYUN) {
                if (isPhosphorVista) "彩云天气 ↗" else "彩云雷达 ↗"
            } else {
                if (isPhosphorVista) "降雨图来源 ↗" else "RainViewer 回波 ↗"
            },
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "打开雷达数据源说明") {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(if (source == RadarSource.CAIYUN) CAIYUN_RADAR_DOC_URL else RAINVIEWER_URL),
                            ),
                        )
                    }
                }
                .padding(vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.mint,
            maxLines = 1,
        )
        TiandituAttribution(Modifier.padding(vertical = 5.dp))
    }
}

@Composable
private fun RadarInfoDialog(source: RadarSource, staleMetadata: Boolean, onDismiss: () -> Unit) {
    WeatherOverlayDialog(onDismiss) { modifier ->
        RadarInfoPanel(source, staleMetadata, onDismiss, modifier) { OfficialRadarLink() }
    }
}

@Composable
private fun RadarSourceDialog(current: RadarSource, caiyunConfigured: Boolean,
    onSelect: (RadarSource) -> Unit, onDismiss: () -> Unit) {
    WeatherOverlayDialog(onDismiss) { modifier -> RadarSourcePanel(current, caiyunConfigured, onSelect, onDismiss, modifier) }
}

@Composable
private fun OfficialRadarLink() {
    val context = LocalContext.current
    Text(
        if (isPhosphorVista) "去看中央气象台的官方图  ↗" else "中央气象台官方雷达图  ↗",
        modifier = Modifier.clickable(role = Role.Button) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_RADAR_URL))) }
        }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = ZhishengMint,
    )
}

private fun rainviewerTileTemplate(host: String, frame: RadarFrame, tileSize: Int = 256): String =
    host.trimEnd('/') + frame.path + "/$tileSize/{z}/{x}/{y}/2/1_1.png"

private fun clearRadarOverlays(style: Style) {
    style.layers.map { it.id }.filter { id ->
        id.startsWith("radar-layer-") || id.startsWith("caiyun-layer-") || id == "zhisheng-radar-city-marker"
    }.forEach { style.removeLayer(it) }
    style.sources.map { it.id }.filter { id ->
        id.startsWith("radar-source-") || id.startsWith("caiyun-img-") || id == "zhisheng-radar-city"
    }.forEach { style.removeSource(it) }
}

private fun installRadarOverlays(
    style: Style,
    host: String,
    frames: List<RadarFrame>,
    futureFrames: List<RadarFrame>,
    selectedTime: Long,
    baseLabelLayerId: String?,
) {
    val labelAnchor = baseLabelLayerId?.takeIf { style.getLayer(it) != null }

    (frames.map { it to false } + futureFrames.map { it to true }).forEach { (frame, future) ->
        style.addSource(
            RasterSource(
                radarSourceId(frame, future),
                TileSet("2.1.0", rainviewerTileTemplate(host, frame)).apply { maxZoom = 7f },
                256,
            ),
        )
        val layer = RasterLayer(radarLayerId(frame, future), radarSourceId(frame, future)).withProperties(
            rasterOpacity(0f),
            rasterFadeDuration(RADAR_CROSSFADE_MS.toFloat()),
        )
        if (labelAnchor != null) style.addLayerBelow(layer, labelAnchor) else style.addLayer(layer)
        layer.setRasterOpacityTransition(TransitionOptions(RADAR_CROSSFADE_MS, 0L))
    }
}

// 彩云图片帧：每帧一个 ImageSource，按四角坐标贴到地图；位图懒加载后 setImage 替换占位图
private fun installCaiyunOverlays(
    style: Style,
    frames: List<RadarFrame>,
    selectedTime: Long,
    baseLabelLayerId: String?,
) {
    val labelAnchor = baseLabelLayerId?.takeIf { style.getLayer(it) != null }
    frames.filter(RadarFrame::isImageFrame).forEach { frame ->
        val quad = frame.toLatLngQuad() ?: return@forEach
        val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        style.addSource(ImageSource(caiyunSourceId(frame), quad, placeholder))
        val layer = RasterLayer(caiyunLayerId(frame), caiyunSourceId(frame)).withProperties(
            rasterOpacity(if (frame.timeMillis == selectedTime) RADAR_FRAME_OPACITY else RADAR_PREFETCH_OPACITY),
            rasterFadeDuration(RADAR_CROSSFADE_MS.toFloat()),
        )
        if (labelAnchor != null) style.addLayerBelow(layer, labelAnchor) else style.addLayer(layer)
        layer.setRasterOpacityTransition(TransitionOptions(RADAR_CROSSFADE_MS, 0L))
    }
}

private fun installCityMarker(style: Style, city: City, palette: ZhishengPalette) {
    style.addSource(
        GeoJsonSource("zhisheng-radar-city", Feature.fromGeometry(Point.fromLngLat(city.longitude, city.latitude))),
    )
    style.addLayer(
        CircleLayer("zhisheng-radar-city-marker", "zhisheng-radar-city").withProperties(
            circleRadius(5.5f),
            circleColor(palette.orange.toArgb()),
            circleStrokeColor(palette.text.toArgb()),
            circleStrokeWidth(1.5f),
        ),
    )
}

internal fun RadarFrame.toLatLngQuad(): LatLngQuad? {
    val south = southLat ?: return null
    val west = westLng ?: return null
    val north = northLat ?: return null
    val east = eastLng ?: return null
    // 接口边界顺序 [南纬, 西经, 北纬, 东经] → MapLibre 四角（左上、右上、右下、左下）
    return LatLngQuad(
        LatLng(north, west),
        LatLng(north, east),
        LatLng(south, east),
        LatLng(south, west),
    )
}

// 帧图层 id 带上方向前缀：过去实测与未来外推即使时间戳重叠也不会互相覆盖
private fun radarSourceId(frame: RadarFrame, future: Boolean) =
    "radar-source-" + (if (future) "f" else "p") + "-${frame.timeMillis}"
private fun radarLayerId(frame: RadarFrame, future: Boolean) =
    "radar-layer-" + (if (future) "f" else "p") + "-${frame.timeMillis}"
private fun caiyunSourceId(frame: RadarFrame) = "caiyun-img-${frame.timeMillis}"
private fun caiyunLayerId(frame: RadarFrame) = "caiyun-layer-${frame.timeMillis}"

internal fun radarNowFrame(frames: List<RadarFrame>, nowIndex: Int): RadarFrame? =
    frames.getOrNull(nowIndex)
