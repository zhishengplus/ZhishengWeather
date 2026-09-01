/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V5 */
/* Hallmark · genre: atmospheric technical utility · macrostructure: Workbench · design-system: DESIGN.md · designed-as-app */
package com.zhisheng.weather.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.home.Scanlines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val RADAR_MAX_ZOOM = 16.0

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
fun RadarScreen(
    city: City?,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<RadarScreenState>(RadarScreenState.Loading) }
    val radarSource by SettingsRepository.radarSource.collectAsState(initial = RadarSource.RAINVIEWER)
    // 彩云不可用自动回退 RainViewer 后的说明，显示在顶部提示条
    var fallbackNotice by remember { mutableStateOf<String?>(null) }

    // 切城市 / 重试 / 切源都会重载；切城市时保留画面只挪相机，不打断浏览
    LaunchedEffect(city?.locationKey, retry, radarSource) {
        if (city == null) {
            state = RadarScreenState.Error("先在主页选择一座城市")
            return@LaunchedEffect
        }
        if (state !is RadarScreenState.Ready) state = RadarScreenState.Loading
        fallbackNotice = null
        state = try {
            radarLoadState(city, radarSource)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: CaiyunRadarException) {
            if (radarSource == RadarSource.CAIYUN) {
                // 彩云不可用：回退到 RainViewer 并保留原因，而不是把用户困在错误页；
                // 偏好同步落回 RainViewer，避免每次进页面都重撞一次彩云
                fallbackNotice = listOfNotNull(caiyunRadarMessage(e.reason), e.message?.takeIf(String::isNotBlank))
                    .distinct()
                    .joinToString(" · ")
                runCatching { SettingsRepository.setRadarSource(RadarSource.RAINVIEWER) }
                runCatching { radarLoadState(city, RadarSource.RAINVIEWER) }
                    .getOrElse { RadarScreenState.Error("雷达数据连接失败") }
            } else {
                RadarScreenState.Error(e.message ?: "雷达数据连接失败")
            }
        } catch (_: Exception) {
            if (state is RadarScreenState.Ready) state else RadarScreenState.Error("雷达数据连接失败")
        }
    }

    // 页面停留期间每 5 分钟复核帧目录（彩云图片 URL 带时效签名，同样需要定期换新）
    val readyState = state as? RadarScreenState.Ready
    LaunchedEffect(readyState?.source, readyState?.feed?.playbackFrames?.map(RadarFrame::frameKey)) {
        val ready = readyState ?: return@LaunchedEffect
        val cityNow = city ?: return@LaunchedEffect
        while (true) {
            delay(METADATA_REFRESH_MS)
            val fresh = try {
                radarLoadState(cityNow, ready.source)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                continue
            }
            val changed = fresh.feed.playbackFrames.map(RadarFrame::frameKey) !=
                ready.feed.playbackFrames.map(RadarFrame::frameKey)
            if (changed || fresh.staleMetadata != ready.staleMetadata) {
                state = fresh
            }
        }
    }

    Box(Modifier.fillMaxSize().background(ZhishengBg)) {
        when (val current = state) {
            RadarScreenState.Loading -> RadarLoading(onBack)
            is RadarScreenState.Error -> RadarError(current.message, current.detail, onBack) { retry++ }
            is RadarScreenState.Ready -> RadarInstrumentPage(
                city = city!!,
                source = current.source,
                feed = current.feed,
                staleMetadata = current.staleMetadata,
                coverage = current.coverage,
                fallbackNotice = fallbackNotice,
                utcOffsetSeconds = utcOffsetSeconds,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun RadarLoading(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FeaturePageHeader("雷达回波", "RADAR LINK", onBack)
        FeatureBootLoader(
            channel = "RADAR OBSERVATORY",
            lines = listOf(
                "RADAR PORT .......... OPEN",
                "LOAD MAP PROJECTION . OK",
                "SYNC FRAME MANIFEST ...",
                "WARM RASTER LAYERS ....",
            ),
            status = "正在接收当前位置的最新回波",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RadarError(message: String, detail: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FeaturePageHeader("雷达回波", "RADAR LINK", onBack)
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
                },
            )
        }
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
    var fallbackGeo by remember { mutableStateOf<WeatherMapFallbackGeo?>(null) }
    var styleEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(6_000)
        hintVisible = false
    }

    LaunchedEffect(Unit) {
        if (hasTiandituToken()) return@LaunchedEffect
        fallbackGeo = withContext(Dispatchers.IO) {
            WeatherMapFallbackGeo(
                china = context.assets.open("geo/china_boundaries.geojson").bufferedReader().use { it.readText() },
                coast = context.assets.open("geo/world_coastline.geojson").bufferedReader().use { it.readText() },
                worldBorders = context.assets.open("geo/world_borders.geojson").bufferedReader().use { it.readText() },
            )
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // 全帧以极低透明度参与渲染，地图空闲后才开始播放，避免第一轮逐帧白等。
            addOnDidFinishLoadingMapListener {
                if (mapReadyState.value) radarTilesReady = true
            }
            addOnDidBecomeIdleListener {
                if (mapReadyState.value) {
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

    // 天地图只在主题/底图就绪时加载一次；回波帧变化只替换叠加层，避免整图闪白。
    LaunchedEffect(mapRef.value, palette.isLight, fallbackGeo) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        if (!hasTiandituToken() && fallbackGeo == null) return@LaunchedEffect
        mapReady = false
        radarTilesReady = false
        tileError = false
        baseStyleApplied = false
        val camera = readyMap.cameraPosition
        readyMap.setStyle(weatherMapBaseStyle(palette, fallbackGeo)) { _ ->
            if (lastStyledCity.value != null) {
                readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
            }
            baseStyleApplied = true
            mapReady = true
            styleEpoch++
        }
    }

    LaunchedEffect(mapRef.value, mapReady, styleEpoch, city.locationKey, feed.host, framesKey, source, palette) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        if (!mapReady || styleEpoch == 0) return@LaunchedEffect
        radarTilesReady = false
        tileError = false
        val labelAnchor = if (hasTiandituToken()) TIANDITU_LABEL_LAYER else null
        val initialTime = selectedTime.takeIf { it != 0L }
            ?: feed.past.lastOrNull()?.timeMillis
            ?: playbackFrames.firstOrNull()?.timeMillis
            ?: 0L
        readyMap.getStyle { style ->
            clearRadarOverlays(style)
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

    // 自动播放：等首屏瓦片就绪（最多 8 秒兜底）再起步，避免动画先于数据出现。
    // 序列 = 过去实测回波 → 未来外推；未解锁时只有过去段。
    LaunchedEffect(playing, framesKey) {
        if (!playing || playbackFrames.size < 2) return@LaunchedEffect
        if (!radarTilesReady) {
            withTimeoutOrNull(TILE_WAIT_TIMEOUT_MS) { snapshotFlow { radarTilesReady }.first { it } }
        }
        while (true) {
            val idx = playbackFrames.indexOfFirst { it.timeMillis == selectedTime }.takeIf { it >= 0 } ?: playbackFrames.lastIndex
            delay(if (idx == playbackFrames.lastIndex) LATEST_FRAME_HOLD_MS else FRAME_INTERVAL_MS)
            selectedTime = playbackFrames[(idx + 1) % playbackFrames.size].timeMillis
        }
    }

    LaunchedEffect(mapRef.value, mapReady, selectedTime, framesKey) {
        if (!mapReady) return@LaunchedEffect
        mapRef.value?.getStyle { style ->
            (frames.map { it to false } + futureFrames.map { it to true }).forEach { (frame, future) ->
                val layerId = if (frame.isImageFrame) caiyunLayerId(frame) else radarLayerId(frame, future)
                style.getLayer(layerId)?.setProperties(
                    rasterOpacity(if (frame.timeMillis == selectedTime) RADAR_FRAME_OPACITY else RADAR_PREFETCH_OPACITY),
                )
            }
        }
    }

    // 彩云图片帧按需取位图：选中帧与下一帧解码后贴进 ImageSource（磁盘短缓存命中时很快）
    LaunchedEffect(mapRef.value, mapReady, selectedTime, source, framesKey) {
        if (!mapReady || source != RadarSource.CAIYUN) return@LaunchedEffect
        val selIdx = playbackFrames.indexOfFirst { it.timeMillis == selectedTime }.coerceAtLeast(0)
        val pending = listOfNotNull(
            playbackFrames.getOrNull(selIdx),
            playbackFrames.getOrNull(selIdx + 1),
        ).filter(RadarFrame::isImageFrame)
        pending.forEach { frame ->
            CaiyunRadarRepository.loadBitmap(context, frame)?.let { bitmap ->
                mapRef.value?.getStyle { style ->
                    (style.getSourceAs<ImageSource>(caiyunSourceId(frame)))?.setImage(bitmap)
                }
            }
        }
    }

    val selected = playbackFrames.firstOrNull { it.timeMillis == selectedTime } ?: feed.past.lastOrNull()
    val selectedIndex = playbackFrames.indexOfFirst { it.timeMillis == selected?.timeMillis }.coerceAtLeast(0)
    val statusText = when {
        !baseStyleApplied -> if (hasTiandituToken()) "正在载入天地图底图" else "正在载入本机矢量底图"
        staleMetadata -> "帧目录来自短时缓存，时间可能滞后"
        tileError -> "回波瓦片加载不稳，稍后自动重试"
        !radarTilesReady && mapReady -> "正在接收回波瓦片"
        else -> null
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 与主页同一台「屏幕」的 CRT 扫描线；不拦截触摸，仅在静止图层之上
        Scanlines()

        RadarTopChrome(
            city = city,
            source = source,
            coverage = coverage,
            hintVisible = hintVisible,
            statusText = fallbackNotice ?: statusText,
            onBack = onBack,
            onRecenter = {
                mapRef.value?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(city.latitude, city.longitude), RADAR_START_ZOOM),
                )
            },
            onInfo = { showInfo = true },
            onSource = { showSourceDialog = true },
        )

        RadarScanOverlay(visible = mapReady && !radarTilesReady)

        RadarBottomController(
            modifier = Modifier.align(Alignment.BottomCenter),
            source = source,
            frames = playbackFrames,
            nowIndex = nowBoundary,
            futureUnlocked = futureUnlocked,
            selected = selected,
            selectedIndex = selectedIndex,
            playing = playing,
            utcOffsetSeconds = utcOffsetSeconds,
            onTogglePlaying = { playing = !playing },
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
    val (coverageText, coverageColor) = if (source == RadarSource.CAIYUN) {
        "全国拼图 · 企业套餐" to palette.mint
    } else when (coverage) {
        RadarCoverageState.AVAILABLE -> "雷达覆盖内" to palette.mint
        RadarCoverageState.OUTSIDE -> "当前区域暂缺雷达覆盖" to palette.orange
        RadarCoverageState.UNKNOWN -> "覆盖状态待确认" to palette.textTertiary
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
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
            RadarTextAction("SRC", "切换雷达数据源", onSource)
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
        Modifier.size(44.dp).background(palette.surface.copy(alpha = 0.94f)).border(1.dp, palette.cardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = palette.text)
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
        Text("正在把最新回波贴到地图", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
private fun RadarBottomController(
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
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().background(palette.surface.copy(alpha = 0.96f))
                .border(1.dp, palette.cardBorder).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadarPlayButton(playing = playing, enabled = frames.size > 1, onToggle = onTogglePlaying)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (viewingFuture) palette.cyan else palette.mint,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${selected?.let { Fmt.date(it.timeMillis, utcOffsetSeconds) } ?: "--"} · " +
                            if (viewingFuture) "未来外推 · ${source.cn} · 约5分钟/帧" else "过去回波 · ${source.cn} · 实测",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                    )
                }
                Text("${selectedIndex + 1}/${frames.size}", style = MaterialTheme.typography.labelSmall, color = palette.orange)
            }
            Spacer(Modifier.height(4.dp))
            RadarScrubber(frames, nowIndex, selectedIndex, utcOffsetSeconds, onSelect)
            if (!futureUnlocked) {
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
            RadarLegend(Modifier.fillMaxWidth())
            RadarAttribution(Modifier.fillMaxWidth(), source)
        }
    }
}

@Composable
private fun RadarPlayButton(playing: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val accent = if (playing) palette.mint else palette.orange
    Box(
        Modifier.size(48.dp).background(accent.copy(alpha = 0.12f)).border(1.dp, accent)
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
                drawRect(palette.orange, topLeft = Offset(selX - 5f, midY - 5f), size = Size(10f, 10f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            Text(
                "过去 ${frames.firstOrNull()?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "现在 ${(frames.getOrNull(boundaryTick) ?: frames.lastOrNull())?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--"}",
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
private fun RadarLegend(modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val colors = listOf(
        Color(0xFF27D7FF),
        Color(0xFF45FF70),
        Color(0xFFFFE24B),
        Color(0xFFFF8A35),
        Color(0xFFFF3F68),
    )
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(6.dp)) {
            colors.forEach { color -> Box(Modifier.weight(1f).fillMaxSize().background(color)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("弱回波", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("强回波", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
        }
    }
}

@Composable
private fun RadarAttribution(modifier: Modifier = Modifier, source: RadarSource) {
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    Row(modifier.height(28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (source == RadarSource.CAIYUN) "彩云雷达 ↗" else "RainViewer 回波 ↗",
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
private fun RadarInfoDialog(
    source: RadarSource,
    staleMetadata: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(role = Role.Button, onClickLabel = "关闭") { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据说明", style = MaterialTheme.typography.titleMedium, color = palette.orange)
                Text(
                    if (source == RadarSource.CAIYUN) "彩云拼图 // RADAR LINK" else "RAINVIEWER // RADAR LINK",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.cyan,
                )
                Text(
                    (if (source == RadarSource.CAIYUN) {
                        "· 过去约 2 小时为彩云区域拼图实测，5 分钟一帧；\n" +
                            "· 未来约 2 小时为彩云外推预报图，约 5 分钟一帧；\n" +
                            "· 彩云雷达图属企业套餐增值接口，Token 需开通雷达权限；\n" +
                            "· 图片 URL 带时效签名，仅短缓存几分钟，离线时旧图不会长期保留；\n" +
                            "· 帧图片由彩云按 Web Mercator 生成，直接叠加在天地图底图上；\n" +
                            "· 图上无明显回波，不等于地面一定无降水，防灾以当地气象部门为准。"
                    } else {
                        "· RainViewer 当前公开接口只提供过去 2 小时实测回波，约 10 分钟一帧，无需 API Key；\n" +
                            "· RainViewer 不提供未来回波，未来约 2 小时外推仅在已开通权限的彩云拼图中显示；\n" +
                            "· 图上无明显回波，不等于地面一定无降水；\n" +
                            "· 页面会读取 RainViewer 覆盖掩膜，区分“无明显回波”和“暂缺雷达覆盖”；\n" +
                            "· 覆盖掩膜更新频率较低，边界附近仍应以当地气象部门信息为准；\n" +
                            "· 彩云拼图可在「SRC」中切换，实况与预报同样为实测与外推；"
                    }) +
                        "\n· 底图使用国家地理信息公共服务平台天地图（$TIANDITU_ATTRIBUTION）；\n" +
                        "· 中文注记随缩放由天地图提供，覆盖城市、区县、乡镇与道路；\n" +
                        "· 台湾省按中国省级行政区显示；底图仅用于回波定位，不替代专业地图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
                Text(
                    when {
                        staleMetadata -> "META // 网络不稳定，当前使用已保存的帧目录"
                        source == RadarSource.CAIYUN -> "META // 帧目录实时获取 · 图片短缓存 5 分钟"
                        else -> "META // 帧目录实时获取 · 地图瓦片由引擎缓存"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (staleMetadata) palette.orange else palette.textTertiary,
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
private fun RadarSourceDialog(
    current: RadarSource,
    caiyunConfigured: Boolean,
    onSelect: (RadarSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(role = Role.Button, onClickLabel = "关闭") { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("雷达数据源", style = MaterialTheme.typography.titleMedium, color = palette.orange)
                Text("RADAR SOURCE // SELECT", style = MaterialTheme.typography.labelMedium, color = palette.cyan)
                RadarSourceChoice(
                    title = "RainViewer",
                    detail = "全球过去 2 小时回波 · 免费 · 无需 Key",
                    selected = current == RadarSource.RAINVIEWER,
                    onClick = { onSelect(RadarSource.RAINVIEWER) },
                )
                RadarSourceChoice(
                    title = "彩云拼图",
                    detail = if (caiyunConfigured) {
                        "全国拼图 · 企业套餐 · 实况 + 外推"
                    } else {
                        "未配置彩云 Token · 前往 设置 → 实验室"
                    },
                    selected = current == RadarSource.CAIYUN,
                    enabled = caiyunConfigured,
                    onClick = { onSelect(RadarSource.CAIYUN) },
                )
                Text(
                    "过去段与未来段随所选数据源一起切换；彩云不可用时自动回到 RainViewer",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                )
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
private fun RadarSourceChoice(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val border = if (selected) palette.cyan else palette.cardBorder
    val textColor = when {
        selected -> palette.cyan
        enabled -> palette.text
        else -> palette.textTertiary
    }
    Box(
        Modifier.fillMaxWidth()
            .background(if (selected) palette.cyan.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected) "● " else "○ ", style = MaterialTheme.typography.titleSmall, color = textColor)
                Text(title, style = MaterialTheme.typography.titleSmall, color = textColor, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 2)
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
            rasterOpacity(if (frame.timeMillis == selectedTime) RADAR_FRAME_OPACITY else RADAR_PREFETCH_OPACITY),
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
