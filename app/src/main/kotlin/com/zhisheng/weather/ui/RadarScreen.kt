package com.zhisheng.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarTimeline
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val OFFICIAL_RADAR_URL = "https://www.nmc.cn/publish/radar/chinaall.html"
private const val RADAR_BASE_STYLE = "https://maps.rainviewer.com/styles/m2/style.json"

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
    var state by remember(city?.locationKey, retry) { mutableStateOf<RadarScreenState>(RadarScreenState.Loading) }

    LaunchedEffect(city?.locationKey, retry) {
        if (city == null) {
            state = RadarScreenState.Error("先在主页选择一座城市")
            return@LaunchedEffect
        }
        state = RadarScreenState.Loading
        state = try {
            val timeline = RadarRepository.loadTimeline()
            if (timeline.frames.isEmpty()) RadarScreenState.Error("当前位置暂时没有可用回波帧")
            else RadarScreenState.Ready(timeline)
        } catch (_: Exception) {
            RadarScreenState.Error("雷达数据连接失败")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ZhishengBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        val loaded = (state as? RadarScreenState.Ready)?.timeline
        FeaturePageHeader(
            title = "雷达回波",
            subtitle = "RADAR ECHO",
            onBack = onBack,
            trailing = loaded?.let { timeline ->
                {
                    Text(
                        "${timeline.loadedFrames}/${timeline.expectedFrames}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timeline.loadedFrames == timeline.expectedFrames) ZhishengMint else ZhishengOrange,
                    )
                }
            },
        )
        when (val current = state) {
            RadarScreenState.Loading -> RadarLoading()
            is RadarScreenState.Error -> FeatureErrorState(
                title = current.message,
                action = "重新读取",
                onAction = { retry++ },
                extra = { OfficialRadarLink() },
            )
            is RadarScreenState.Ready -> RadarContent(
                city = city!!,
                utcOffsetSeconds = utcOffsetSeconds,
                timeline = current.timeline,
            )
        }
    }
}

@Composable
private fun RadarLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("> RADAR LINK", style = MaterialTheme.typography.titleMedium, color = ZhishengMint, letterSpacing = 1.sp)
        Text("正在读取当前位置的最新回波…", style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(13) { index ->
                Box(
                    Modifier.weight(1f).height(5.dp)
                        .background(if (index == 12) ZhishengOrange else ZhishengMint.copy(alpha = 0.28f))
                )
            }
        }
    }
}

@Composable
private fun RadarContent(city: City, utcOffsetSeconds: Int?, timeline: RadarTimeline) {
    var selectedTime by rememberSaveable(city.locationKey) { mutableLongStateOf(0L) }
    var playing by rememberSaveable(city.locationKey) { mutableStateOf(true) }
    val frames = timeline.frames

    LaunchedEffect(frames.map(RadarFrame::timeMillis)) {
        if (frames.none { it.timeMillis == selectedTime }) selectedTime = timeline.latest?.timeMillis ?: 0L
    }
    LaunchedEffect(playing, frames.map(RadarFrame::timeMillis)) {
        while (playing && frames.size > 1) {
            kotlinx.coroutines.delay(650)
            val current = frames.indexOfFirst { it.timeMillis == selectedTime }.coerceAtLeast(0)
            selectedTime = frames[(current + 1) % frames.size].timeMillis
        }
    }

    val selected = frames.firstOrNull { it.timeMillis == selectedTime } ?: timeline.latest
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item {
            RadarHero(city, selected, utcOffsetSeconds, timeline, playing) { playing = !playing }
        }
        item { FeatureSectionTitle(1, "回波视野", "ECHO FIELD") }
        item {
            RadarMapViewport(
                city = city,
                timeline = timeline,
                selectedTime = selected?.timeMillis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).aspectRatio(1f),
            )
        }
        item {
            RadarTimelineBar(
                frames = frames,
                selectedTime = selected?.timeMillis,
                utcOffsetSeconds = utcOffsetSeconds,
                onSelect = { selectedTime = it; playing = false },
            )
        }
        item { FeatureSectionTitle(2, "读图辅助", "READOUT") }
        item { RadarLegend() }
        item { FeatureSectionTitle(3, "数据说明", "SOURCE NOTE") }
        item {
            TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("RAINVIEWER // PAST RADAR", style = MaterialTheme.typography.labelMedium, color = ZhishengCyan)
                    Text(
                        "显示约过去 2 小时的降水回波，不是未来预测。图上无明显回波，不等于地面一定无降水。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextSecondary,
                    )
                    Text(
                        if (timeline.staleMetadata) "META // 网络不稳定，当前使用已保存的帧目录" else "META // 实时目录 · 可见地图瓦片由引擎自动缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (timeline.staleMetadata) ZhishengOrange else ZhishengTextTertiary,
                    )
                    OfficialRadarLink()
                }
            }
        }
    }
}

@Composable
private fun RadarHero(
    city: City,
    selected: RadarFrame?,
    utcOffsetSeconds: Int?,
    timeline: RadarTimeline,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(city.displayName, style = MaterialTheme.typography.headlineSmall, color = ZhishengOrange)
        Text(Fmt.coordinates(city.latitude, city.longitude), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(13.dp))
        TerminalPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.let { Fmt.clock(it.timeMillis, utcOffsetSeconds) } ?: "--:--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ZhishengMint,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "PAST FRAME  ${timeline.loadedFrames}/${timeline.expectedFrames}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 1.sp,
                    )
                }
                RadarAction(if (playing) "暂停" else "播放", if (playing) "II" else ">") { onPlay() }
            }
        }
    }
}

@Composable
private fun RadarAction(label: String, glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.background(ZhishengMint.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 17.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = ZhishengText)
        }
    }
}

@Composable
private fun RadarMapViewport(
    city: City,
    timeline: RadarTimeline,
    selectedTime: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { readyMap ->
                readyMap.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                readyMap.setMinZoomPreference(2.0)
                readyMap.setMaxZoomPreference(12.0)
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(city.latitude, city.longitude))
                    .zoom(5.8)
                    .build()
                map = readyMap
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

    LaunchedEffect(map, city.locationKey, timeline.host, timeline.frames.map(RadarFrame::path)) {
        val readyMap = map ?: return@LaunchedEffect
        mapReady = false
        readyMap.setStyle(RADAR_BASE_STYLE) { style ->
            timeline.frames.forEach { frame ->
                val sourceId = radarSourceId(frame)
                val tileSet = TileSet("2.1.0", timeline.tileTemplate(frame)).apply { maxZoom = 7f }
                style.addSource(RasterSource(sourceId, tileSet, 256))
                style.addLayer(
                    RasterLayer(radarLayerId(frame), sourceId).withProperties(
                        rasterOpacity(0.82f),
                        visibility(if (frame.timeMillis == selectedTime) Property.VISIBLE else Property.NONE),
                    )
                )
            }
            val citySourceId = "zhisheng-city-source"
            style.addSource(
                GeoJsonSource(
                    citySourceId,
                    Feature.fromGeometry(Point.fromLngLat(city.longitude, city.latitude)),
                )
            )
            style.addLayer(
                CircleLayer("zhisheng-city-marker", citySourceId).withProperties(
                    circleRadius(5.5f),
                    circleColor(android.graphics.Color.rgb(255, 138, 53)),
                    circleStrokeColor(android.graphics.Color.WHITE),
                    circleStrokeWidth(1.5f),
                )
            )
            mapReady = true
        }
    }

    LaunchedEffect(map, mapReady, selectedTime) {
        if (!mapReady) return@LaunchedEffect
        map?.getStyle { style ->
            timeline.frames.forEach { frame ->
                style.getLayer(radarLayerId(frame))?.setProperties(
                    visibility(if (frame.timeMillis == selectedTime) Property.VISIBLE else Property.NONE)
                )
            }
        }
    }

    TerminalPanel(modifier) {
        Box(Modifier.fillMaxSize().background(ZhishengCard)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
            )
            if (!mapReady) {
                Box(
                    Modifier.fillMaxSize().background(ZhishengCard.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("正在建立交互地图…", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                }
            }
            Text(
                "双指缩放 · 单指拖动",
                Modifier.align(Alignment.BottomEnd).background(ZhishengBg.copy(alpha = 0.82f)).padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextSecondary,
            )
            Text(
                "● ${city.name}",
                Modifier.align(Alignment.TopStart).background(ZhishengBg.copy(alpha = 0.82f)).padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengOrange,
            )
        }
    }
}

private fun radarSourceId(frame: RadarFrame) = "radar-source-${frame.timeMillis}"
private fun radarLayerId(frame: RadarFrame) = "radar-layer-${frame.timeMillis}"

@Composable
private fun RadarTimelineBar(
    frames: List<RadarFrame>,
    selectedTime: Long?,
    utcOffsetSeconds: Int?,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(frames, key = RadarFrame::timeMillis) { frame ->
                val selected = frame.timeMillis == selectedTime
                Column(
                    Modifier.width(46.dp).clickable { onSelect(frame.timeMillis) }.padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(if (selected) 7.dp else 4.dp).background(if (selected) ZhishengOrange else ZhishengMint.copy(alpha = 0.42f)))
                    Spacer(Modifier.height(5.dp))
                    Text(Fmt.clock(frame.timeMillis, utcOffsetSeconds), style = MaterialTheme.typography.labelSmall, color = if (selected) ZhishengText else ZhishengTextTertiary)
                }
            }
        }
        Text(
            "左早 → 右新  ·  可点选单帧",
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RadarLegend() {
    val colors = listOf(
        Color(0xFF27D7FF) to "弱",
        Color(0xFF45FF70) to "轻",
        Color(0xFFFFE24B) to "中",
        Color(0xFFFF8A35) to "强",
        Color(0xFFFF3F68) to "很强",
    )
    TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text("降水回波强度", style = MaterialTheme.typography.labelMedium, color = ZhishengText)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                colors.forEach { (color, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(7.dp).background(color))
                        Spacer(Modifier.height(5.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                    }
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
