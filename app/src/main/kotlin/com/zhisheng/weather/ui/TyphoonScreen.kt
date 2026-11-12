package com.zhisheng.weather.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.components.HomeBackdrop
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.data.*
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** New selections never caption the previous storm's track. */
@Composable
fun TyphoonScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var year by rememberSaveable { mutableIntStateOf(currentTyphoonYear()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var catalog by remember(year) { mutableStateOf<TyphoonLoad<List<TyphoonStorm>>?>(null) }
    var detail by remember(year) { mutableStateOf<TyphoonLoad<TyphoonDetail>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(false) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    var lastRefreshAt by remember { mutableLongStateOf(0L) }
    var catalogRefreshSeen by remember { mutableIntStateOf(0) }
    var detailRefreshSeen by remember { mutableIntStateOf(0) }
    LaunchedEffect(year, refresh) {
        refreshing = true
        try {
            val force = catalogRefreshSeen != refresh
            catalogRefreshSeen = refresh
            catalog = TyphoonRepository.loadCatalog(context, force = force, year = year)
            val storms = catalog?.value.orEmpty()
            selectedId = storms.firstOrNull { it.id == selectedId }?.id ?: storms.firstOrNull()?.id
            catalogRevision++
            lastRefreshAt = System.currentTimeMillis()
        } finally { refreshing = false }
    }
    val storm = catalog?.value?.firstOrNull { it.id == selectedId }
    LaunchedEffect(storm, catalogRevision) {
        if (storm == null) { detail = null; return@LaunchedEffect }
        if (detail?.value?.storm?.id != storm.id) detail = null
        detailLoading = true
        try {
            val force = detailRefreshSeen != refresh
            detailRefreshSeen = refresh
            detail = TyphoonRepository.loadDetail(context, storm, force = force)
        }
        finally { detailLoading = false }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (lastRefreshAt != 0L && System.currentTimeMillis() - lastRefreshAt >= 10 * 60_000L) refresh++
            while (true) { delay(10 * 60_000L); refresh++ }
        }
    }
    ProvideWeatherPresentationClock {
        TyphoonPage(year, catalog, selectedId,
            detail?.takeIf { it.value == null || it.value.storm.id == selectedId }, refreshing || detailLoading,
            onYear = { if (year != it) { year = it; selectedId = null } }, onStorm = { selectedId = it },
            onRefresh = { refresh++ }, onBack = onBack)
    }
}

/** Map stays mounted while the floating tools fold away. */
@Composable
internal fun TyphoonPage(
    year: Int, catalog: TyphoonLoad<List<TyphoonStorm>>?, selectedId: String?,
    load: TyphoonLoad<TyphoonDetail>?, refreshing: Boolean = false,
    onYear: (Int) -> Unit, onStorm: (String) -> Unit, onRefresh: () -> Unit, onBack: () -> Unit,
    mapContent: (@Composable (TyphoonDetail, TyphoonForecastTrack?, TyphoonMapSelection?, Boolean, Int, (TyphoonMapSelection) -> Unit) -> Unit)? = null,
) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var info by rememberSaveable { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(true) }
    val detail = load?.value
    if (detail != null) {
        TyphoonWorkbench(detail, load, refreshing, satellite, { satellite = !satellite },
            onPicker = { picker = true }, onInfo = { info = true }, onRefresh = onRefresh, onBack = onBack,
            mapContent = mapContent)
    } else {
        Column(Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding()) {
            TyphoonTopBar(null, load?.stale == true, refreshing, onBack, { info = true }, Modifier.padding(12.dp))
            TextButton(onClick = { picker = true }, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("$year 年 · 选择台风", color = LocalZhishengPalette.current.mint)
            }
            when {
                catalog == null || (selectedId != null && load == null) -> TyphoonStatus("正在读取台风资料", "正在获取官方路径与发布时次", true, onRefresh)
                catalog.error != null || load?.error != null -> TyphoonStatus("资料暂时无法连接", "请检查网络后重试，也可切换年份查看已有资料。", false, onRefresh)
                else -> TyphoonStatus("$year 年暂无台风资料", "切换年份，可查看历史台风路径。", false, onRefresh)
            }
        }
    }
    if (picker) TyphoonPicker(year, catalog, selectedId, onYear, { onStorm(it); picker = false }, onRefresh, { picker = false })
    if (info) TyphoonInfoDialog(detail?.source, { info = false })
}

@Composable
private fun TyphoonWorkbench(
    detail: TyphoonDetail, load: TyphoonLoad<TyphoonDetail>, refreshing: Boolean,
    satellite: Boolean, onToggleBase: () -> Unit, onPicker: () -> Unit, onInfo: () -> Unit,
    onRefresh: () -> Unit, onBack: () -> Unit,
    mapContent: (@Composable (TyphoonDetail, TyphoonForecastTrack?, TyphoonMapSelection?, Boolean, Int, (TyphoonMapSelection) -> Unit) -> Unit)?,
) {
    val p = LocalZhishengPalette.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    var focused by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showForecast by rememberSaveable(detail.storm.id) { mutableStateOf(true) }
    var showWind by rememberSaveable { mutableStateOf(true) }
    val forecast = detail.forecasts.firstOrNull()?.takeIf { showForecast }
    val points = remember(detail, forecast) { typhoonDisplayPoints(detail, forecast) }
    var selectedKey by rememberSaveable(detail.storm.id) { mutableStateOf<String?>(null) }
    val selected = points.firstOrNull { typhoonPointKey(it) == selectedKey } ?: points.lastOrNull { !it.forecast }
    val index = points.indexOf(selected).coerceAtLeast(0)
    var playing by remember(detail.storm.id) { mutableStateOf(false) }
    var recenter by remember { mutableIntStateOf(0) }
    var pointDialog by remember { mutableStateOf(false) }
    var topPixels by remember { mutableIntStateOf(0) }
    var bottomPixels by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val mapLayer = rememberGraphicsLayer()
    val owner = LocalView.current
    val vista = isPhosphorVista
    val currentIndex by rememberUpdatedState(index)
    val currentPoints by rememberUpdatedState(points)
    val select: (TyphoonMapSelection) -> Unit = { playing = false; selectedKey = typhoonPointKey(it) }
    val latest = { playing = false; selectedKey = null }
    val play = {
        if (!playing && (selectedKey == null || index == points.lastIndex)) selectedKey = points.firstOrNull()?.let(::typhoonPointKey)
        playing = !playing
    }
    val focus = { expanded = false; focused = true }
    LaunchedEffect(playing, lifecycle) {
        if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (playing) {
                delay(900)
                val next = currentIndex + 1
                if (next >= currentPoints.size) playing = false else selectedKey = typhoonPointKey(currentPoints[next])
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = focused || expanded) { if (expanded) expanded = false else focused = false }
    TyphoonImmersiveMap(focused)
    BoxWithConstraints(Modifier.fillMaxSize().zhishengScreen().onGloballyPositioned { origin = it.positionInRoot() }) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val sheetWidth = if (wide) 360.dp else maxWidth
        val insets = if (focused) TyphoonMapInsets() else TyphoonMapInsets(top = topPixels,
            bottom = if (wide) 0 else bottomPixels,
            left = if (wide) with(density) { sheetWidth.roundToPx() + 12.dp.roundToPx() } else 0,
            right = with(density) { if (expanded) 0 else 62.dp.roundToPx() })
        val paneMaxHeight = maxHeight * .74f
        Box(Modifier.fillMaxSize().testTag("typhoon-map-viewport")
            .semantics {
                if (focused) {
                    contentDescription = "台风地图，点按显示工具"
                    onClick(label = "显示台风工具") { focused = false; true }
                }
            }.drawWithContent {
                if (vista && !focused) {
                    mapLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(mapLayer)
                } else drawContent()
            }) {
            if (mapContent != null) mapContent(detail, forecast, selected, showWind, recenter, select)
            else TyphoonMapHost(detail, forecast, selected, showWind, recenter, select, satellite, insets, { focused = false })
        }
        CompositionLocalProvider(LocalHomeBackdrop provides HomeBackdrop(mapLayer, origin, owner)) {
            if (!focused) {
                TyphoonTopBar(detail, load.stale, refreshing, onBack, onInfo,
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .onSizeChanged { topPixels = it.height }.statusBarsPadding().padding(12.dp).testTag("typhoon-toolbar"))
                if (!expanded) Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.typhoonGlass(compact = true)) {
                        IconButton(onClick = onToggleBase, modifier = Modifier.size(48.dp)) {
                            PhosphorIcon(R.drawable.ph_map_trifold, if (satellite) "切换标准地图" else "切换卫星影像", Modifier.size(22.dp), p.text)
                        }
                    }
                    Box(Modifier.typhoonGlass(compact = true)) {
                        IconButton(onClick = { showWind = !showWind }, modifier = Modifier.size(48.dp).semantics { this.selected = showWind }) {
                            PhosphorIcon(R.drawable.ph_wind, if (showWind) "隐藏风圈" else "显示风圈", Modifier.size(22.dp), if (showWind) p.mint else p.text)
                        }
                    }
                    Box(Modifier.typhoonGlass(compact = true)) { TyAction(R.drawable.ph_crosshair, "显示完整路径", { recenter++ }) }
                }
                if (expanded) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { expanded = false })
                Box(Modifier.align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .width(sheetWidth).onSizeChanged { bottomPixels = it.height }.navigationBarsPadding().padding(12.dp)) {
                    if (expanded) TyphoonExpandedPanel(detail, load, points, selected, showWind, showForecast,
                        onSelect = select, onWind = { showWind = !showWind },
                        onForecast = { playing = false; showForecast = !showForecast },
                        onPointDetails = { pointDialog = true }, onRefresh = onRefresh, refreshing = refreshing,
                        onClose = { expanded = false }, onFocus = focus,
                        modifier = Modifier.fillMaxWidth().heightIn(max = paneMaxHeight))
                    else TyphoonSummary(detail, points, selected, playing, select, play, latest, onPicker,
                        onExpand = { expanded = true }, onFocus = focus,
                        modifier = Modifier.fillMaxWidth().testTag("typhoon-summary"))
                }
            }
        }
    }
    if (pointDialog && selected != null) TyphoonPointDialog(selected, { pointDialog = false })
}

@Composable
private fun TyphoonTopBar(detail: TyphoonDetail?, stale: Boolean, refreshing: Boolean,
    onBack: () -> Unit, onInfo: () -> Unit, modifier: Modifier) {
    val p = LocalZhishengPalette.current
    val status = detail?.let { typhoonObservationStatus(it, stale, weatherPresentationTime()) }
    Row(modifier.typhoonGlass(compact = true).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        TyAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text("台风追踪", color = p.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (status != null) Text(status.substringBefore('·').trim() + " · " + status.takeLast(5),
                color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
        }
        if (refreshing) CircularProgressIndicator(Modifier.padding(14.dp).size(20.dp), color = p.mint, strokeWidth = 2.dp)
        TyAction(Icons.Filled.Info, "图例与资料来源", onInfo)
    }
}

@Composable
private fun TyphoonSummary(detail: TyphoonDetail, points: List<TyphoonMapSelection>, selected: TyphoonMapSelection?,
    playing: Boolean, onSelect: (TyphoonMapSelection) -> Unit, onPlay: () -> Unit, onLatest: () -> Unit,
    onPicker: () -> Unit, onExpand: () -> Unit, onFocus: () -> Unit, modifier: Modifier) {
    val p = LocalZhishengPalette.current
    val compact = LocalDensity.current.fontScale > 1.4f
    val dragThreshold = with(LocalDensity.current) { 38.dp.toPx() }
    val focusAction by rememberUpdatedState(onFocus)
    val expandAction by rememberUpdatedState(onExpand)
    Column(modifier.typhoonGlass().pointerInput(Unit) {
        var travel = 0f
        detectVerticalDragGestures(onDragStart = { travel = 0f }, onDragEnd = {
            if (travel > dragThreshold) focusAction() else if (travel < -dragThreshold) expandAction()
        }) { change, delta -> change.consume(); travel += delta }
    }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "选择年份和台风", onClick = onPicker)
                .heightIn(min = 48.dp).padding(vertical = 4.dp)) {
                Text(detail.storm.name + "  ⌄", color = p.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${detail.storm.id} · ${selected?.point?.intensity ?: "强度待发布"}",
                    color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(selected?.point?.windLevel?.toString() ?: "—", color = p.text,
                    fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.SemiBold)
                Text("级", color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
            }
            TyAction(Icons.Filled.KeyboardArrowUp, "展开台风详情", onExpand)
            TyAction(Icons.Filled.KeyboardArrowDown, "收起全部控件", onFocus)
        }
        Text("${formatTyphoonTime(selected?.point?.time)} · ${if (selected?.forecast == true) "预报" else "实况"}",
            color = if (selected?.forecast == true) p.cyan else p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium)
        TyphoonPlayback(points, selected, playing, onSelect, onPlay, onLatest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TyphoonPlayback(points: List<TyphoonMapSelection>, selected: TyphoonMapSelection?, playing: Boolean,
    onSelect: (TyphoonMapSelection) -> Unit, onPlay: () -> Unit, onLatest: () -> Unit) {
    val p = LocalZhishengPalette.current
    val index = points.indexOf(selected).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.clip(CircleShape).background(p.mint.copy(alpha = .14f))) {
            TyAction(if (playing) tyPauseIcon else Icons.Filled.PlayArrow, if (playing) "暂停回放" else "播放路径", onPlay, points.size > 1)
        }
        if (points.size > 1) Slider(value = index.toFloat(), onValueChange = { points.getOrNull(it.roundToInt())?.let(onSelect) },
            valueRange = 0f..points.lastIndex.toFloat(), modifier = Modifier.weight(1f).height(48.dp)
                .semantics { contentDescription = "台风时间轴，滑动选择时次" },
            thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(p.mint).border(2.dp, p.surface, CircleShape)) },
            track = { Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val y = size.height / 2f
                drawLine(p.text.copy(alpha = .18f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                val x = size.width * index / points.lastIndex.toFloat()
                drawLine(p.mint, Offset(0f, y), Offset(x, y), 3.dp.toPx(), StrokeCap.Round)
                points.forEachIndexed { i, value ->
                    if (i % 4 == 0 || value == selected) drawCircle(if (i <= index) p.mint else p.text.copy(alpha = .78f),
                        2.dp.toPx(), Offset(size.width * i / points.lastIndex.toFloat(), y))
                }
            } }) else Spacer(Modifier.weight(1f))
        TextButton(onClick = onLatest, modifier = Modifier.heightIn(min = 48.dp)
            .semantics { contentDescription = "最新实况" }) {
            Text("最新", color = p.mint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TyphoonExpandedPanel(detail: TyphoonDetail, load: TyphoonLoad<TyphoonDetail>, points: List<TyphoonMapSelection>,
    selected: TyphoonMapSelection?, showWind: Boolean, showForecast: Boolean, onSelect: (TyphoonMapSelection) -> Unit,
    onWind: () -> Unit, onForecast: () -> Unit, onPointDetails: () -> Unit, onRefresh: () -> Unit, refreshing: Boolean,
    onClose: () -> Unit, onFocus: () -> Unit, modifier: Modifier) {
    val p = LocalZhishengPalette.current
    val point = selected?.point
    Column(modifier.typhoonGlass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(detail.storm.name, color = p.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("台风详情 · 北京时间", color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
            }
            TyAction(Icons.Filled.KeyboardArrowDown, "收起全部控件", onFocus)
            TyAction(Icons.Filled.Close, "收起台风详情", onClose)
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${formatTyphoonTime(point?.time)} · ${if (selected?.forecast == true) "中央气象台预报" else "实况位置"}",
            color = p.text, style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics: @Composable (Modifier, String, String) -> Unit = { m, label, value ->
                Column(m) { Text(value, color = p.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(label, color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall) }
            }
            if (maxWidth / LocalDensity.current.fontScale < 240.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics(Modifier.fillMaxWidth(), "中心风力", point?.windLevel?.let { "$it 级" } ?: "—")
                metrics(Modifier.fillMaxWidth(), "最大风速", point?.windSpeedMs?.let { "${tyNumber(it)} m/s" } ?: "—")
                metrics(Modifier.fillMaxWidth(), "中心气压", point?.pressureHpa?.let { "$it hPa" } ?: "—")
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics(Modifier.weight(1f), "中心风力", point?.windLevel?.let { "$it 级" } ?: "—")
                metrics(Modifier.weight(1f), "最大风速", point?.windSpeedMs?.let { "${tyNumber(it)} m/s" } ?: "—")
                metrics(Modifier.weight(1f), "中心气压", point?.pressureHpa?.let { "$it hPa" } ?: "—")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(showWind, onWind, label = { Text("风圈") })
            FilterChip(showForecast, onForecast, enabled = detail.forecasts.any { it.points.isNotEmpty() }, label = { Text(if (detail.forecasts.isEmpty()) "暂无预报" else "预报路径") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val index = points.indexOf(selected)
            TyAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一个时次", { points.getOrNull(index - 1)?.let(onSelect) }, index > 0)
            TextButton(onClick = onPointDetails) { Text("位置与风圈详情", color = p.mint) }
            TyAction(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一个时次", { points.getOrNull(index + 1)?.let(onSelect) }, index < points.lastIndex)
        }
        Text(when {
            selected?.forecast == true -> "虚线为预报路径，后续可能调整；风圈不作外推。"
            point?.let { listOf(it.radius7, it.radius10, it.radius12).none { r -> r?.available == true } } != false -> "该时次未提供风圈；缺测不表示没有大风。"
            else -> "风圈表示 7、10、12 级风范围，按四个象限绘制。"
        }, color = p.text.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
        if (load.fromCache) Text(if (load.stale) "离线缓存 · 请留意资料时次" else "最近缓存 · ${load.fetchedAt?.let(::formatTyphoonFetchedTime).orEmpty()}",
            color = if (load.stale) p.orange else p.text.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
        load.error?.let { Text(it, color = p.orange, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "正在刷新" else "刷新台风资料", color = p.mint) }
        }
    }
}

@Composable
private fun TyphoonImmersiveMap(focused: Boolean) {
    val context = LocalContext.current
    DisposableEffect(focused, context) {
        var candidate = context
        while (candidate is android.content.ContextWrapper && candidate !is android.app.Activity) candidate = candidate.baseContext
        val activity = candidate as? android.app.Activity
        val window = activity?.window
        val before = window?.decorView?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        if (focused) {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (focused) {
                if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
                if (before?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) != false) controller?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                if (before?.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()) != false) controller?.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
}

@Composable
private fun TyphoonMapHost(detail: TyphoonDetail, forecast: TyphoonForecastTrack?, selected: TyphoonMapSelection?,
    showWind: Boolean, recenter: Int, onSelect: (TyphoonMapSelection) -> Unit, satellite: Boolean,
    insets: TyphoonMapInsets, onReveal: () -> Unit) {
    val context = LocalContext.current
    val p = LocalZhishengPalette.current
    if (typhoonMapConfigured()) {
        TyphoonMap(detail, forecast, selected, showWind, recenter, onSelect, Modifier.fillMaxSize(), privacyAccepted = true, satellite = satellite,
            contentInsets = insets, onEmptyMapClick = onReveal)
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 52.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        PhosphorIcon(R.drawable.ph_map_trifold, null, Modifier.size(36.dp), p.textTertiary)
        Spacer(Modifier.height(12.dp))
        Text("地图服务尚未配置", color = p.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("仍可查看路径时次、强度和风圈数据，也可打开官方台风图。", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { openTyphoonUrl(context, "https://typhoon.slt.zj.gov.cn/") }) { Text("打开官方台风图", color = p.mint) }
    }
}

@Composable private fun TyphoonPicker(year: Int, catalog: TyphoonLoad<List<TyphoonStorm>>?, selectedId: String?,
    onYear: (Int) -> Unit, onStorm: (String) -> Unit, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalZhishengPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var years by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        com.zhisheng.weather.ui.HighRefreshDialogContent()
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(tyShape()).background(p.surface).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择台风", Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TyAction(Icons.Filled.Close, "关闭", onDismiss)
            }
            Box {
                TextButton(onClick = { years = true }) { Text("$year 年  ▾", color = p.mint) }
                DropdownMenu(years, { years = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    com.zhisheng.weather.ui.HighRefreshDialogContent()
                    (currentTyphoonYear() downTo FIRST_TYPHOON_YEAR).forEach { value ->
                        DropdownMenuItem(text = { Text("$value 年") }, onClick = { years = false; query = ""; onYear(value) })
                    }
                }
            }
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索名称或编号") }, shape = tyShape())
            Spacer(Modifier.height(10.dp))
            val matches = catalog?.value.orEmpty().filter { it.name.contains(query, true) || it.id.contains(query) || it.englishName.contains(query, true) }
            when {
                catalog == null -> Text("正在读取 $year 年台风资料…", color = p.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
                catalog.error != null -> {
                    Text(catalog.error, color = p.orange, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = onRefresh) { Text("重新获取", color = p.mint) }
                }
                matches.isEmpty() -> Text("暂无匹配资料，可换个年份或名称。", color = p.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(matches, key = { it.id }) { storm ->
                    Column(Modifier.fillMaxWidth().clip(tyShape())
                        .background(if (storm.id == selectedId) p.mint.copy(alpha = .10f) else Color.Transparent)
                        .semantics { selected = storm.id == selectedId }
                        .clickable(role = Role.RadioButton) { onStorm(storm.id) }.padding(12.dp)) {
                        Text("${storm.name} · ${storm.id}", color = p.text, style = MaterialTheme.typography.titleSmall)
                        Text(if (storm.active) "正在活动" else "历史路径", color = if (storm.active) p.mint else p.textTertiary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable private fun TyphoonPointDialog(selection: TyphoonMapSelection, onDismiss: () -> Unit) {
    val p = LocalZhishengPalette.current
    val point = selection.point
    AlertDialog(onDismissRequest = onDismiss, containerColor = p.surface, title = { com.zhisheng.weather.ui.HighRefreshDialogContent(); Text("时次详情", color = p.text) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${if (selection.forecast) "预报" else "实况"} · ${formatTyphoonTime(point.time)}（北京时）", color = p.text)
            Text("经纬度  ${typhoonCoordinate(point.latitude, true)}  ${typhoonCoordinate(point.longitude, false)}", color = p.textSecondary)
            Text("移动方向  ${point.moveDirection?.takeIf { it.isNotBlank() } ?: "未提供"}\n移动速度  ${point.moveSpeedKmh?.let { "${tyNumber(it)} km/h" } ?: "未提供"}", color = p.textSecondary)
            Text("风圈半径 · 公里", color = p.text, fontWeight = FontWeight.Bold)
            Text("顺序：东北 / 东南 / 西南 / 西北", color = p.textTertiary, style = MaterialTheme.typography.labelSmall)
            listOf(7 to point.radius7, 10 to point.radius10, 12 to point.radius12).forEach { (level, radii) ->
                Text("$level 级  ${typhoonRadiiLabel(radii)}", color = p.textSecondary)
            }
            Text("“—”表示资料未提供。部分象限缺失时不补造完整风圈。", color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable private fun TyphoonInfoDialog(source: String?, onDismiss: () -> Unit) {
    val p = LocalZhishengPalette.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = p.surface, title = { com.zhisheng.weather.ui.HighRefreshDialogContent(); Text("图例与来源", color = p.text) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("实线为已观测路径，虚线为中央气象台预报。回放按资料时次逐点展示，不代表台风移动的真实速度。", color = p.textSecondary)
            listOf("热带低压", "热带风暴", "强热带风暴", "台风", "强台风", "超强台风").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(typhoonLevelColor(i + 1)).border(1.dp, p.textTertiary, CircleShape))
                    Text(label, Modifier.padding(start = 10.dp), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("风圈按东北、东南、西南、西北四象限绘制。7、10、12 级风圈分别以蓝、橙、红表示；缺失资料不作外推。", color = p.textSecondary)
            Text("资料：${source ?: "浙江省水利厅台风路径实时发布系统"}\n时间统一为北京时间。底图来自天地图，保留官方中文地名和地图标识。", color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
            Text("预报可能调整。防灾避险请以中央气象台和当地政府最新预警为准。", color = p.orange, style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })
}
@Composable private fun TyphoonStatus(title: String, message: String, busy: Boolean, onRefresh: () -> Unit) {
    val p = LocalZhishengPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = p.mint)
        Spacer(Modifier.height(16.dp)); Text(title, color = p.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Text(message, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
        if (!busy) TextButton(onClick = onRefresh) { Text("重新获取", color = p.mint) }
    }
}
@Composable private fun TyAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, label, Modifier.size(22.dp), tint = LocalZhishengPalette.current.text.copy(alpha = if (enabled) 1f else .32f))
    }
}
@Composable private fun tyShape() = if (isPhosphorVista) RoundedCornerShape(20.dp) else RectangleShape
private fun openTyphoonUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { android.widget.Toast.makeText(context, "未找到可打开网页的浏览器", android.widget.Toast.LENGTH_SHORT).show() }
}
internal fun typhoonDisplayPoints(detail: TyphoonDetail, forecast: TyphoonForecastTrack?) =
    detail.observed.map { TyphoonMapSelection(it, false) } + forecast?.points.orEmpty().map { TyphoonMapSelection(it, true) }
internal fun typhoonPointKey(item: TyphoonMapSelection) = "${item.forecast}:${item.point.time}:${item.point.latitude}:${item.point.longitude}"
internal fun typhoonCoordinate(value: Double, latitude: Boolean): String =
    "${tyNumber(abs(value))}°${if (latitude) { if (value >= 0) "N" else "S" } else { if (value >= 0) "E" else "W" }}"
internal fun typhoonRadiiLabel(r: TyphoonWindRadii?): String =
    listOf(r?.northEastKm, r?.southEastKm, r?.southWestKm, r?.northWestKm).joinToString(" / ") { it?.let(::tyNumber) ?: "—" }
internal fun typhoonObservationStatus(detail: TyphoonDetail, stale: Boolean, now: Long = System.currentTimeMillis()): String {
    val latest = detail.observed.lastOrNull()?.time
    val old = detail.storm.active && (parseTyphoonTime(latest)?.let { now - it > 6 * 60 * 60_000L } ?: true)
    val state = when { stale -> "缓存资料"; !detail.storm.active -> "历史路径"; old -> "实况较旧"; else -> "最新实况" }
    return "$state · ${formatTyphoonTime(latest)}"
}
internal fun formatTyphoonTime(raw: String?): String = parseTyphoonTime(raw)?.let(::formatTyphoonFetchedTime) ?: "时次未提供"
private fun formatTyphoonFetchedTime(epoch: Long): String = DateTimeFormatter.ofPattern("MM月dd日 HH:mm", Locale.CHINA)
    .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(epoch))
private fun tyNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)

@Composable private fun TyAction(resource: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick, modifier = Modifier.size(48.dp)) {
        PhosphorIcon(resource, label, Modifier.size(22.dp), LocalZhishengPalette.current.text)
    }
}
private val tyPauseIcon = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f); close()
        moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close()
    }
}.build()
