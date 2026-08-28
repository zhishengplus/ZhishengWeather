/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V4 */
/* Hallmark · component: minute precipitation + wind compass · genre: atmospheric
 * theme: existing Zhisheng terminal · contrast: pass
 */
package com.zhisheng.weather.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.BriefingEmote
import com.zhisheng.weather.model.BriefingKind
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.HeroTemps
import com.zhisheng.weather.model.TyphoonInfo
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.R
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.TelemetryMetric
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.WeatherViewModel
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.components.WeatherAmbience
import com.zhisheng.weather.ui.components.isNightAt
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMint
import androidx.compose.ui.graphics.lerp as colorLerp
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengRed
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.ZhishengWarning
import com.zhisheng.weather.ui.theme.alertLevelColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
// 枳生天气 · 磷光数据终端主屏
// 布局序：状态行 → Hero → 预警 → 逐时(曲线) → 分钟降水 → 逐日(归一化温度条)
//        → 遥测卡格 → 空气质量 → 生活指数 → 昨日复盘 → 台风 → 枳生页脚
// ═══════════════════════════════════════════════════════════

private sealed interface HomeContentSnapshot {
    data object Empty : HomeContentSnapshot
    data object Loading : HomeContentSnapshot
    data class Error(val message: String) : HomeContentSnapshot
    data class Data(
        val weather: WeatherData,
        val city: com.zhisheng.weather.model.City?,
        val staleAgeMillis: Long?,
    ) : HomeContentSnapshot
}

private sealed interface HomeContentKey {
    data object Empty : HomeContentKey
    data object Loading : HomeContentKey
    data class Error(val message: String) : HomeContentKey
    data class Data(val cityKey: String) : HomeContentKey
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WeatherViewModel,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRadarClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var cityDeckVisible by remember { mutableStateOf(false) }
    var cityDeckStart by remember { mutableIntStateOf(0) }
    var cityDeckPosition by remember { mutableFloatStateOf(0f) }
    var cityDeckDrag by remember { mutableFloatStateOf(0f) }
    var cityDeckVerticalDrag by remember { mutableFloatStateOf(0f) }
    var cityDeckPinned by remember { mutableStateOf(false) }
    var cityDeckExpansion by remember { mutableFloatStateOf(0f) }
    var weatherContentScrolling by remember { mutableStateOf(false) }
    val cityContentSnapshots = remember { mutableMapOf<String, HomeContentSnapshot.Data>() }
    val selectedCityIndex = uiState.cities.indexOfFirst {
        it.locationKey == uiState.selectedCity?.locationKey
    }.coerceAtLeast(0)

    LaunchedEffect(uiState.selectedCity?.locationKey) {
        weatherContentScrolling = false
    }

    BackHandler(enabled = cityDeckVisible) {
        cityDeckVisible = false
        cityDeckPinned = false
        cityDeckExpansion = 0f
    }

    // 氛围层要知道现在是不是夜里：国标现象码（小米 weathercn）没有昼夜变体，
    // 只看 condition 的话夜里的晴天也会走白天那套。每分钟对一次表，
    // 日落之后主屏立刻换成星点，不必等下一次天气刷新（v0.0.9）。
    var epochMinute by remember { mutableStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            epochMinute = System.currentTimeMillis() / 60_000L
        }
    }
    val nowMinutes = uiState.weather?.utcOffsetSeconds?.let { offset ->
        Math.floorMod(epochMinute + offset / 60L, 24L * 60L).toInt()
    } ?: java.time.LocalTime.now().run { hour * 60 + minute }
    val todayAstro = uiState.weather?.todayDaily(epochMinute * 60_000L)
    val night = isNightAt(todayAstro?.sunrise, todayAstro?.sunset, nowMinutes)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CityDrawer(
                uiState = uiState,
                onSelect = { key ->
                    viewModel.selectCity(key)
                    scope.launch { drawerState.close() }
                },
                onRemove = viewModel::removeCity,
                onAddCity = {
                    scope.launch { drawerState.close() }
                    onSearchClick()
                },
            )
        },
    ) {
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
        Box(modifier = Modifier.fillMaxSize().background(ZhishengBg)) {
            WeatherAmbience(
                weather = uiState.weather,
                level = uiState.prefs.ambience,
                night = night,
            )
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    cityName = uiState.selectedCity?.displayName ?: "枳生天气",
                    loading = uiState.loading,
                    onMenu = { scope.launch { drawerState.open() } },
                    onRefresh = { viewModel.refresh() },
                    onSettings = onSettingsClick,
                )
                PullToRefreshBox(
                    isRefreshing = uiState.loading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 0.0.9-debug：cities 占位期（citiesLoaded=false）不判空态，
                        // 渲染 loading——否则已存城市的用户每次冷启动闪一屏"未接入城市"。
                        val weatherSnapshot = uiState.weather
                        val contentKey: HomeContentKey = when {
                            uiState.citiesLoaded && uiState.cities.isEmpty() -> HomeContentKey.Empty
                            uiState.loading && weatherSnapshot == null -> HomeContentKey.Loading
                            weatherSnapshot?.error != null && weatherSnapshot.current == null ->
                                HomeContentKey.Error(weatherSnapshot.error.orEmpty())
                            weatherSnapshot != null -> {
                                val cityKey = uiState.selectedCity?.locationKey ?: "__current__"
                                cityContentSnapshots[cityKey] = HomeContentSnapshot.Data(
                                    weather = weatherSnapshot,
                                    city = uiState.selectedCity,
                                    staleAgeMillis = uiState.staleAgeMillis,
                                )
                                HomeContentKey.Data(cityKey)
                            }
                            else -> HomeContentKey.Loading
                        }
                        // Crossfade 的退出帧必须持有上一城市的完整快照。若在 lambda 内继续读取
                        // uiState.weather，selectCity() 清空天气后旧 "data" 帧仍会组合并触发 NPE。
                        Crossfade(targetState = contentKey, animationSpec = tween(200, easing = FastOutSlowInEasing), label = "content") { page ->
                            when (page) {
                                HomeContentKey.Empty -> EmptyState(onSearchClick)
                                is HomeContentKey.Error -> ErrorState(page.message, onSearchClick)
                                // 0.0.9-debug 修复：按城市 key 包一层。换城市时若只使用统一的
                                // "data" key，WeatherContent 不重建，原城市停在半截的滚动深度、
                                // 逐日展开行、预警展开态全部原样带进新城市。key 换城市即
                                // 整个子树重建：列表回顶、展开态清零（entered 交错动画随
                                // 重建重放一次，语义正确——这就是新城市首次入场）。
                                is HomeContentKey.Data -> {
                                    val snapshot = cityContentSnapshots[page.cityKey]
                                    if (snapshot == null) {
                                        BootState(uiState.prefs.bootAnim)
                                    } else androidx.compose.runtime.key(page.cityKey) {
                                        val weatherListState = rememberLazyListState()
                                        val scrolling = weatherListState.isScrollInProgress
                                        LaunchedEffect(page.cityKey, scrolling) {
                                            weatherContentScrolling = scrolling
                                        }
                                        WeatherContent(
                                            data = snapshot.weather,
                                            city = snapshot.city,
                                            unit = uiState.tempUnit,
                                            showTyphoon = uiState.showTyphoon,
                                            prefs = uiState.prefs,
                                            staleAgeMillis = snapshot.staleAgeMillis,
                                            listState = weatherListState,
                                            onHistoryClick = onHistoryClick,
                                            onRadarClick = onRadarClick,
                                        )
                                    }
                                }
                                HomeContentKey.Loading -> BootState(uiState.prefs.bootAnim)
                            }
                        }
                    }
                }
            }
            CityDeckOverlay(
                visible = cityDeckVisible,
                pinned = cityDeckPinned,
                cities = uiState.cities,
                position = cityDeckPosition,
                expansion = cityDeckExpansion,
                onPinnedDrag = { dragX ->
                    // 卡组展开后降低位移阻力；卡片自身仍用弹簧追随，保留一点硬件惯性。
                    val stepPx = with(density) { 72.dp.toPx() }
                    cityDeckPosition = clampCityDeckPosition(
                        cityDeckPosition - dragX / stepPx,
                        uiState.cities.size,
                    )
                },
                onPinnedDragEnd = {
                    cityDeckPosition = cityDeckPosition.roundToInt().toFloat()
                },
                onSelect = { key ->
                    cityDeckVisible = false
                    cityDeckPinned = false
                    cityDeckExpansion = 0f
                    if (key != uiState.selectedCity?.locationKey) viewModel.selectCity(key)
                },
                onDismiss = {
                    cityDeckVisible = false
                    cityDeckPinned = false
                    cityDeckExpansion = 0f
                },
            )
            CityTouchSensor(
                // 手势进行中显现；卡组锁定、松手后立即退场，不在主页常驻。
                active = cityDeckVisible && !cityDeckPinned,
                scrolling = weatherContentScrolling && !cityDeckVisible,
                enabled = uiState.cities.size > 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .pointerInput(uiState.cities, uiState.selectedCity?.locationKey, uiState.cities.size > 1) {
                        if (uiState.cities.size <= 1) return@pointerInput
                        val stepPx = with(density) { 78.dp.toPx() }
                        val pinThresholdPx = with(density) { 156.dp.toPx() }
                        var pinnedThisGesture = false
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                cityDeckStart = selectedCityIndex
                                cityDeckPosition = selectedCityIndex.toFloat()
                                cityDeckDrag = 0f
                                cityDeckVerticalDrag = 0f
                                cityDeckPinned = false
                                cityDeckExpansion = 0f
                                pinnedThisGesture = false
                                cityDeckVisible = uiState.cities.size > 1
                                if (cityDeckVisible) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (cityDeckVisible) {
                                    cityDeckDrag += dragAmount.x
                                    cityDeckVerticalDrag += dragAmount.y
                                    cityDeckPosition = clampCityDeckPosition(
                                        cityDeckStart - cityDeckDrag / stepPx,
                                        uiState.cities.size,
                                    )
                                    if (!pinnedThisGesture) {
                                        cityDeckExpansion = (-cityDeckVerticalDrag / pinThresholdPx)
                                            .coerceIn(0f, 0.76f)
                                        val upwardIntent = -cityDeckVerticalDrag >= abs(cityDeckDrag) * 1.10f
                                        if (cityDeckVerticalDrag <= -pinThresholdPx && upwardIntent) {
                                            pinnedThisGesture = true
                                            cityDeckPinned = true
                                            cityDeckExpansion = 1f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                cityDeckPosition = cityDeckPosition.roundToInt().toFloat()
                                if (!pinnedThisGesture) {
                                    val target = uiState.cities.getOrNull(cityDeckPosition.roundToInt())
                                    cityDeckVisible = false
                                    cityDeckPinned = false
                                    cityDeckExpansion = 0f
                                    if (target != null && target.locationKey != uiState.selectedCity?.locationKey) {
                                        viewModel.selectCity(target.locationKey)
                                    }
                                }
                            },
                            onDragCancel = {
                                if (!pinnedThisGesture) {
                                    cityDeckVisible = false
                                    cityDeckPinned = false
                                    cityDeckExpansion = 0f
                                }
                            },
                        )
                    },
            )
            if (uiState.prefs.scanlines) Scanlines()
        }
    }
}

/**
 * 开发者氛围实验室复用的真实首页表面。
 * data/city/prefs 全由调用方以内存值传入，不持有 ViewModel，也不会写入缓存或城市选择。
 */
@Composable
fun SimulatedWeatherSurface(
    data: WeatherData,
    city: com.zhisheng.weather.model.City,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    unit: String = "c",
    night: Boolean = false,
    header: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(ZhishengBg)) {
        WeatherAmbience(weather = data, level = prefs.ambience, night = night)
        Column(Modifier.fillMaxSize()) {
            header()
            Box(Modifier.weight(1f)) {
                androidx.compose.runtime.key(data.current?.condition, data.current?.profile?.intensity) {
                    val listState = rememberLazyListState()
                    WeatherContent(
                        data = data,
                        city = city,
                        unit = unit,
                        showTyphoon = false,
                        prefs = prefs,
                        staleAgeMillis = null,
                        listState = listState,
                    )
                }
            }
        }
        if (prefs.scanlines) Scanlines()
    }
}

@Composable
private fun CityTouchSensor(
    active: Boolean,
    scrolling: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (active) ZhishengCyan else ZhishengMint
    val gestureAlpha by animateFloatAsState(
        targetValue = if (active && enabled) 1f else 0f,
        animationSpec = tween(if (active) 180 else 260),
        label = "city-sensor-alpha",
    )
    val breath = remember { Animatable(0f) }
    val breathing = scrolling && enabled
    LaunchedEffect(breathing) {
        if (!breathing) {
            // 滚动结束后保留一小段余光，再慢慢退下；新的滚动会立刻取消退场并接管。
            delay(720)
            breath.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        } else {
            if (breath.value < 0.20f) {
                breath.animateTo(0.20f, tween(420, easing = FastOutSlowInEasing))
            }
            while (true) {
                breath.animateTo(0.38f, tween(900, easing = FastOutSlowInEasing))
                breath.animateTo(0.20f, tween(1_100, easing = FastOutSlowInEasing))
            }
        }
    }
    val visualAlpha = if (!enabled) 0f else if (active) gestureAlpha else breath.value
    val scan = remember { Animatable(-0.24f) }
    LaunchedEffect(active) {
        if (!active) {
            scan.snapTo(-0.24f)
        } else {
            while (true) {
                scan.snapTo(-0.24f)
                scan.animateTo(1.24f, tween(920, easing = FastOutSlowInEasing))
                delay(260)
            }
        }
    }
    val sensorSurface = ZhishengSurface
    Canvas(
        modifier = modifier
            .zIndex(30f)
            .width(92.dp)
            // 48dp 隐形热区；真正的玻璃胶囊只有 34dp，视觉不变但更容易按中。
            .height(48.dp)
            .semantics {
                contentDescription = if (enabled) {
                    "城市切换传感器，长按后左右滑动选择，向上推可展开并松手"
                } else {
                    "城市切换传感器，当前没有可切换城市"
                }
            },
    ) {
        val stroke = 1.dp.toPx()
        val visualTop = 7.dp.toPx()
        val visualHeight = 34.dp.toPx()
        val visualLeft = 4.dp.toPx()
        val visualWidth = size.width - visualLeft * 2f
        val visualSize = androidx.compose.ui.geometry.Size(visualWidth, visualHeight)
        val visualCorner = CornerRadius(visualHeight / 2f)
        // 只在手势期间显现的终端玻璃感应槽：冷色双层折射边缘 + 单向扫描光。
        // 上下滚动时仅保留低亮度呼吸，真正的城市手势才点亮扫描光。
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.18f * visualAlpha),
                    sensorSurface.copy(alpha = 0.68f * visualAlpha),
                    base.copy(alpha = 0.08f * visualAlpha),
                ),
                startY = visualTop,
                endY = visualTop + visualHeight,
            ),
            topLeft = Offset(visualLeft, visualTop),
            size = visualSize,
            cornerRadius = visualCorner,
        )
        // 宽而淡的两层边缘光只负责“辉光”，最内侧 1dp 才是玻璃实体边框。
        drawRoundRect(
            color = base.copy(alpha = 0.07f * visualAlpha),
            topLeft = Offset(visualLeft, visualTop),
            size = visualSize,
            cornerRadius = visualCorner,
            style = Stroke(7.dp.toPx()),
        )
        drawRoundRect(
            color = base.copy(alpha = 0.14f * visualAlpha),
            topLeft = Offset(visualLeft, visualTop),
            size = visualSize,
            cornerRadius = visualCorner,
            style = Stroke(3.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.19f * visualAlpha),
            topLeft = Offset(visualLeft + stroke, visualTop + stroke),
            size = androidx.compose.ui.geometry.Size(visualWidth - stroke * 2f, visualHeight - stroke * 2f),
            cornerRadius = visualCorner,
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = base.copy(alpha = 0.70f * visualAlpha),
            topLeft = Offset(visualLeft, visualTop),
            size = visualSize,
            cornerRadius = visualCorner,
            style = Stroke(stroke),
        )
        val scanX = size.width * scan.value
        val scanAlpha = if (active) 1f else 0f
        listOf(-5f to 0.08f, 0f to 0.34f, 5f to 0.08f).forEach { (offsetDp, glowAlpha) ->
            drawLine(
                color = Color.White.copy(alpha = glowAlpha * visualAlpha * scanAlpha),
                start = Offset(scanX + offsetDp.dp.toPx(), visualTop + visualHeight * 0.25f),
                end = Offset(scanX + offsetDp.dp.toPx(), visualTop + visualHeight * 0.75f),
                strokeWidth = if (offsetDp == 0f) stroke * 1.6f else stroke * 3f,
            )
        }
        val arrowY = visualTop + visualHeight * 0.57f
        val arrowHalf = 7.dp.toPx()
        val arrowRise = 4.dp.toPx()
        // 辉光底层 + 清晰光芯，仍是终端符号而不是普通 Material 图标。
        drawLine(
            base.copy(alpha = 0.18f * visualAlpha),
            Offset(size.width * 0.5f - arrowHalf, arrowY),
            Offset(size.width * 0.5f, arrowY - arrowRise),
            stroke * 4f,
        )
        drawLine(
            base.copy(alpha = 0.18f * visualAlpha),
            Offset(size.width * 0.5f, arrowY - arrowRise),
            Offset(size.width * 0.5f + arrowHalf, arrowY),
            stroke * 4f,
        )
        drawLine(
            Color.White.copy(alpha = 0.72f * visualAlpha),
            Offset(size.width * 0.5f - arrowHalf, arrowY),
            Offset(size.width * 0.5f, arrowY - arrowRise),
            stroke * 1.25f,
        )
        drawLine(
            Color.White.copy(alpha = 0.72f * visualAlpha),
            Offset(size.width * 0.5f, arrowY - arrowRise),
            Offset(size.width * 0.5f + arrowHalf, arrowY),
            stroke * 1.25f,
        )
    }
}

@Composable
private fun CityDeckOverlay(
    visible: Boolean,
    pinned: Boolean,
    cities: List<com.zhisheng.weather.model.City>,
    position: Float,
    expansion: Float,
    onPinnedDrag: (Float) -> Unit,
    onPinnedDragEnd: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val expanded by animateFloatAsState(
        targetValue = if (pinned) 1f else expansion,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "city-deck-expansion",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140)),
        modifier = Modifier.fillMaxSize().zIndex(20f),
    ) {
        var dealt by remember { mutableStateOf(false) }
        val edgeGlowTransition = rememberInfiniteTransition(label = "city-card-edge-glow")
        val edgeGlowPulse by edgeGlowTransition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "city-card-edge-pulse",
        )
        LaunchedEffect(Unit) {
            delay(18)
            dealt = true
        }
        val selected = position.roundToInt().coerceIn(0, cities.lastIndex.coerceAtLeast(0))
        Column(
            modifier = Modifier.fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.94f))
                .pointerInput(pinned, cities.size) {
                    if (pinned) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onPinnedDrag(dragAmount.x)
                            },
                            onDragEnd = onPinnedDragEnd,
                            onDragCancel = onPinnedDragEnd,
                        )
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 24.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "CITY DECK // 城市切换",
                style = MaterialTheme.typography.titleMedium,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (pinned) "已展开 · 左右滑动或点选卡片" else "保持按住 · 左右选择 · 向上推可松手",
                style = MaterialTheme.typography.labelSmall,
                color = if (pinned) ZhishengMint else ZhishengTextTertiary,
            )
            if (pinned) {
                Text(
                    "[ 关闭卡组 ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengCyan,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = "关闭城市卡组", onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val spacing = with(LocalDensity.current) { (82f + 42f * expanded).dp.toPx() }
                cities.forEachIndexed { index, city ->
                    val relative = index - position
                    val distance = abs(relative)
                    if (distance <= 4.2f) {
                        val targetX = if (dealt) relative * spacing else 0f
                        val fanRotation = 6.2f - 5f * expanded
                        val distanceScale = 0.075f - 0.025f * expanded
                        val targetRotation = if (dealt) relative.coerceIn(-3f, 3f) * -fanRotation else 0f
                        val targetScale = if (dealt) (1f - distance * distanceScale).coerceAtLeast(0.78f) else 0.88f
                        val x by animateFloatAsState(
                            targetX,
                            spring(
                                dampingRatio = if (pinned) 0.78f else 0.82f,
                                stiffness = if (pinned) Spring.StiffnessMedium else Spring.StiffnessMediumLow,
                            ),
                            label = "city-card-x-$index",
                        )
                        val rotation by animateFloatAsState(
                            targetRotation,
                            spring(
                                dampingRatio = if (pinned) 0.76f else 0.78f,
                                stiffness = if (pinned) Spring.StiffnessMedium else Spring.StiffnessMediumLow,
                            ),
                            label = "city-card-r-$index",
                        )
                        val scale by animateFloatAsState(
                            targetScale,
                            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium),
                            label = "city-card-s-$index",
                        )
                        val focused = index == selected
                        val cardShape = RoundedCornerShape(18.dp)
                        val cardGlow = if (focused) ZhishengCyan else ZhishengMint
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(10f - distance)
                                .width(236.dp)
                                .height(306.dp)
                                .graphicsLayer {
                                    translationX = x
                                    rotationZ = rotation
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (1f - distance * 0.14f).coerceAtLeast(0.38f)
                                    cameraDistance = 18f * density
                                    rotationY = relative.coerceIn(-2f, 2f) * (-4f + 3f * expanded)
                                },
                        ) {
                            if (focused) {
                                // 显式绘制两层放大光晕：不依赖不同厂商对彩色系统阴影的实现。
                                // 低频轻呼吸只作用于当前卡，其他卡仍保持安静。
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = 1.045f
                                            scaleY = 1.034f
                                            alpha = edgeGlowPulse
                                        }
                                        .border(7.dp, ZhishengCyan.copy(alpha = 0.09f), cardShape),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = 1.022f
                                            scaleY = 1.017f
                                            alpha = edgeGlowPulse
                                        }
                                        .border(4.dp, ZhishengCyan.copy(alpha = 0.16f), cardShape),
                                )
                            }
                            Column(
                                modifier = Modifier
                                .fillMaxSize()
                                .shadow(
                                    elevation = if (focused) 24.dp else 8.dp,
                                    shape = cardShape,
                                    ambientColor = cardGlow.copy(alpha = if (focused) 0.30f else 0.08f),
                                    spotColor = cardGlow.copy(alpha = if (focused) 0.22f else 0.05f),
                                )
                                .background(ZhishengSurface, cardShape)
                                .border(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = if (focused) ZhishengCyan else ZhishengCardBorder,
                                    shape = cardShape,
                                )
                                .clickable(
                                    enabled = pinned,
                                    role = Role.Button,
                                    onClickLabel = "切换到${city.displayName}",
                                ) { onSelect(city.locationKey) }
                                .padding(18.dp),
                            ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "CARD %02d".format(index + 1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (focused) ZhishengCyan else ZhishengTextTertiary,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(8.dp)
                                        .background(if (focused) ZhishengMint else ZhishengCardBorder, RoundedCornerShape(4.dp)),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                city.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (focused) ZhishengMint else ZhishengText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (city.contextLabel.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    city.contextLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZhishengTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(color = if (focused) ZhishengCyan.copy(alpha = 0.45f) else ZhishengCardBorder)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                Fmt.coordinates(city.latitude, city.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextTertiary,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    pinned && focused -> "// TAP TO SWITCH"
                                    pinned -> "// SELECTABLE"
                                    focused -> "// RELEASE TO SWITCH"
                                    else -> "// STANDBY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (focused) ZhishengOrange else ZhishengTextTertiary,
                            )
                            }
                        }
                    }
                }
            }
            Text(
                "%02d / %02d".format(selected + 1, cities.size),
                style = MaterialTheme.typography.labelMedium,
                color = ZhishengCyan,
                letterSpacing = 2.sp,
                // 按住状态下底部还有独立的玻璃感应器；给计数器留出完整避让区，
                // 卡组锁定后感应器退场，计数器再回到正常底位。
                modifier = Modifier.padding(bottom = if (pinned) 0.dp else 64.dp),
            )
        }
    }
}

// —— 扫描线氛围层（3dp 周期，不拦截触摸）——
// 深色 = CRT 扫描线（白 2.5%）；浅色 = 纸面细纹（墨线 2%，v0.0.5）
@Composable
private fun Scanlines() {
    val lineColor = LocalZhishengPalette.current.run {
        if (isLight) text.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.025f)
    }
    Box(modifier = Modifier.fillMaxSize().drawWithCache {
        val step = 3.dp.toPx()
        val scanPath = Path()
        var y = 0f
        while (y < size.height) {
            scanPath.moveTo(0f, y)
            scanPath.lineTo(size.width, y)
            y += step
        }
        onDrawBehind { drawPath(scanPath, lineColor, style = Stroke(width = 1f)) }
    })
}

@Composable
private fun TopBar(
    cityName: String,
    loading: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Menu, contentDescription = "城市列表", tint = ZhishengTextSecondary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = cityName,
                style = MaterialTheme.typography.titleMedium,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ZHISHENG WEATHER TERMINAL",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                letterSpacing = 1.5.sp,
            )
        }
        // 刷新中持续旋转：原来是静态 360f，视觉上等于没转（v0.0.2）
        val angle = if (loading) {
            val spin = rememberInfiniteTransition(label = "spin")
            val animatedAngle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "angle",
            )
            animatedAngle
        } else {
            0f
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = if (loading) "正在刷新" else "刷新",
                tint = if (loading) ZhishengMint else ZhishengOrange,
                modifier = Modifier.size(20.dp).rotate(if (loading) angle else 0f),
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = ZhishengTextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// —— 交错入场动画容器（50ms 步进，300ms，M3 标准缓动） ——
// entered 由 WeatherContent 统一持有：只在数据首次入场时播放一次交错动画。
// 开关不能 remember 在 item 内部——LazyColumn 快滑时新入屏的 item 才现场组合，
// 逐项重置开关会重放淡入（还有 index*50ms 延迟），表现为快滑时卡片空白、停下才冒出来。
// 状态提升后，滚动中/回收后重组的卡片读到 entered=true，animateFloatAsState 初值即 1f，直接可见。
@Composable
private fun Stagger(index: Int, entered: Boolean, content: @Composable (Modifier) -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (entered) 1f else 0f, tween(300, delayMillis = index * 50, easing = FastOutSlowInEasing), label = "sa",
    )
    val dy by androidx.compose.animation.core.animateFloatAsState(
        if (entered) 0f else 20f, tween(300, delayMillis = index * 50, easing = FastOutSlowInEasing), label = "sd",
    )
    content(
        Modifier.graphicsLayerAlpha(alpha, dy)
    )
}

private fun Modifier.graphicsLayerAlpha(a: Float, t: Float) =
    this.then(Modifier.graphicsLayer { alpha = a; translationY = t })

@Composable
private fun WeatherContent(
    data: WeatherData,
    city: com.zhisheng.weather.model.City?,
    unit: String,
    showTyphoon: Boolean,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    staleAgeMillis: Long?,
    listState: LazyListState,
    onHistoryClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
) {
    // 入场动画总开关：状态提升到 LazyColumn 之上，只驱动一次交错入场（v0.0.1 修复快滑闪卡）
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // 区块编号改为渲染时现算：原来靠一个与渲染顺序不一致的 visible 数组预推，
    // 某些区块缺失时编号会跳号/错位（v0.0.2）
    var seq = 0
    var stagger = 0
    val nextIndex = { ++seq }
    val nextStagger = { stagger++ }

    val currentDaily = data.currentAndFutureDaily()
    val todayDaily = data.todayDaily()
    val showHourly = data.hourly.isNotEmpty()
    val showPrecip = prefs.showPrecip && Nowcast.shouldShowPrecipModule(data, System.currentTimeMillis())
    val showDaily = currentDaily.isNotEmpty()
    val showTele = prefs.showTelemetry && data.current?.let { current ->
        prefs.telemetryMetrics.any { metric -> telemetryMetricAvailable(metric, current, todayDaily) }
    } == true
    val showAqi = prefs.showAqi && data.aqi != null
    val showIndices = prefs.showIndices && lifeIndexItems(data, prefs.lifeIndexMetrics).isNotEmpty()
    val showYesterday = prefs.showYesterday && data.yesterday != null
    val showTy = showTyphoon && data.typhoons.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { StatusLine(city, data, staleAgeMillis) }
        data.current?.let { cur ->
            item { Stagger(nextStagger(), entered) { m -> HeroSection(cur, data, unit, prefs, m) } }
        }
        if (data.alerts.isNotEmpty()) {
            item { Stagger(nextStagger(), entered) { m -> AlertSection(data.alerts.take(3), m) } }
        }
        prefs.moduleOrder.forEach { module ->
            val visible = when (module) {
                HomeModule.HOURLY -> showHourly
                HomeModule.PRECIP -> showPrecip
                HomeModule.SPACETIME -> prefs.showSpacetime
                HomeModule.DAILY -> showDaily
                HomeModule.TELEMETRY -> showTele
                HomeModule.AQI -> showAqi
                HomeModule.INDICES -> showIndices
                HomeModule.YESTERDAY -> showYesterday
                HomeModule.TYPHOON -> showTy
            }
            if (!visible) return@forEach

            val animationIndex = nextStagger()
            val n = nextIndex()
            item(key = "title_${module.key}") { SectionTitle(n, module.cn, module.en) }
            item(key = "module_${module.key}") {
                Stagger(animationIndex, entered) { m ->
                    when (module) {
                        HomeModule.HOURLY -> HourlySection(data.hourly, unit, prefs.windUnit, data.utcOffsetSeconds, m)
                        HomeModule.PRECIP -> PrecipCard(data, m)
                        HomeModule.SPACETIME -> SpacetimeObservatory(
                            modifier = m,
                            onHistoryClick = onHistoryClick,
                            onRadarClick = onRadarClick,
                        )
                        HomeModule.DAILY -> DailySection(currentDaily, unit, prefs.windUnit, data.utcOffsetSeconds, m)
                        HomeModule.TELEMETRY -> data.current?.let { TelemetryGrid(it, todayDaily, unit, prefs, m) }
                        HomeModule.AQI -> data.aqi?.let { AqiCard(it, m) }
                        HomeModule.INDICES -> IndicesRow(data, prefs.lifeIndexMetrics, m)
                        HomeModule.YESTERDAY -> data.yesterday?.let { YesterdayCard(it, todayDaily, unit, m) }
                        HomeModule.TYPHOON -> TyphoonCard(data.typhoons, m)
                    }
                }
            }
        }
        item { Stagger(nextStagger(), entered) { m -> Footer(data, m) } }
    }
}

// —— 状态行：坐标 / 更新时间 / 数据源 ——
@Composable
private fun StatusLine(city: com.zhisheng.weather.model.City?, data: WeatherData, staleAgeMillis: Long?) {
    val coord = city?.let { Fmt.coordinates(it.latitude, it.longitude) } ?: "----"
    // 离线缓存兜底时标注缓存年龄（<10 分钟不打扰，只给正常更新时间）
    val updText = if (staleAgeMillis != null && staleAgeMillis >= 10 * 60_000L) {
        "UPD ${staleAgeMillis / 60_000L}分钟前 · 缓存"
    } else {
        "UPD ${data.updateTime?.let { Fmt.stamp(it, data.utcOffsetSeconds) } ?: "--"}"
    }
    val srcText = "SRC ${dataSourceShortLabel(data.dataSource)}${supplementShortLabel(data)}"
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = coord,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                updText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (staleAgeMillis != null && staleAgeMillis >= 10 * 60_000L) ZhishengOrange else ZhishengTextTertiary,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            srcText,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            letterSpacing = 1.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// —— Hero：大温度 + 数字滚动 + 大图标 ——
@Composable
private fun HeroSection(
    cur: CurrentWeather,
    data: WeatherData,
    unit: String,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = cur.weatherText ?: cur.condition?.label ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = ZhishengOrange,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.Top) {
                    AnimatedTemp(cur.temperature, unit)
                    Text(
                        text = "°",
                        style = MaterialTheme.typography.displayLarge,
                        color = ZhishengOrange,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                val range = HeroTemps.range(
                    data.daily,
                    data.yesterday,
                    System.currentTimeMillis(),
                    Fmt.zoneId(data.utcOffsetSeconds),
                )
                Text(
                    text = buildString {
                        if (HeroTemps.showFeelsLike(cur.temperature, cur.feelsLike)) {
                            append("体感${Fmt.temp(cur.feelsLike, unit)}°")
                        }
                        if (range.hasAny) {
                            if (isNotEmpty()) append("  ")
                            range.left?.let { append("${range.leftLabel}${Fmt.temp(it, unit)}°") }
                            range.right?.let {
                                if (range.left != null) append(" ")
                                append("${range.rightLabel}${Fmt.temp(it, unit)}°")
                            }
                        }
                        if (isEmpty()) append("—")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ZhishengTextSecondary,
                    maxLines = 1,
                )
                // 风况直接进 Hero：最常看的一项，不用再往下滚到遥测区
                windLabel(cur, prefs.windUnit)?.let { w ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "风 $w",
                        style = MaterialTheme.typography.labelMedium,
                        color = ZhishengTextTertiary,
                        maxLines = 1,
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) {
                // 六边形 AT 力场底纹（Canvas lambda 非 composable 上下文，颜色提前取值）
                val hexOuter = ZhishengOrange.copy(alpha = 0.22f)
                val hexInner = ZhishengCyan.copy(alpha = 0.12f)
                Canvas(modifier = Modifier.size(116.dp)) {
                    val c = center
                    val r = size.minDimension / 2f
                    val path = Path().apply {
                        for (i in 0 until 6) {
                            val a = Math.toRadians(60.0 * i - 30.0)
                            val p = Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat())
                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                        close()
                    }
                    drawPath(path, hexOuter, style = Stroke(1.5f))
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            val r2 = r * 0.82f
                            for (i in 0 until 6) {
                                val a = Math.toRadians(60.0 * i - 30.0)
                                val p = Offset(c.x + r2 * Math.cos(a).toFloat(), c.y + r2 * Math.sin(a).toFloat())
                                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                            close()
                        },
                        hexInner,
                        style = Stroke(1f),
                    )
                }
                WeatherIcon(cur.condition, Modifier.size(76.dp))
            }
        }
        Nowcast.briefing(data, unit, System.currentTimeMillis())?.let { briefing ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "天气娘提示：${briefing.text}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(briefingEmoteRes(briefing.emote)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp)),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = briefing.text,
                    style = MaterialTheme.typography.titleSmall,
                    color = briefingColor(briefing),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun briefingEmoteRes(emote: BriefingEmote): Int = when (emote) {
    BriefingEmote.SUNNY -> R.drawable.weather_girl_emote_sunny
    BriefingEmote.CLOUDY -> R.drawable.weather_girl_emote_cloudy
    BriefingEmote.RAIN -> R.drawable.weather_girl_emote_rain
    BriefingEmote.HOT -> R.drawable.weather_girl_emote_hot
    BriefingEmote.COLD -> R.drawable.weather_girl_emote_cold
    BriefingEmote.WIND -> R.drawable.weather_girl_emote_wind
    BriefingEmote.NIGHT -> R.drawable.weather_girl_emote_night
    BriefingEmote.ALERT -> R.drawable.weather_girl_emote_alert
}

@Composable
private fun briefingColor(briefing: com.zhisheng.weather.model.HeroBriefing): Color = when {
    briefing.alertLevel != null -> alertLevelColor(briefing.alertLevel)
    briefing.kind == BriefingKind.PRECIPITATION -> ZhishengOrange
    briefing.kind == BriefingKind.TEMPERATURE && briefing.emote == BriefingEmote.COLD -> ZhishengCyan
    briefing.kind == BriefingKind.TEMPERATURE -> ZhishengOrange
    briefing.kind == BriefingKind.WIND -> ZhishengCyan
    briefing.kind == BriefingKind.AIR_QUALITY || briefing.kind == BriefingKind.VISIBILITY -> ZhishengWarning
    briefing.kind == BriefingKind.UV -> ZhishengOrange
    else -> ZhishengMint
}

// 温度数字滚动（400ms，emphasizedDecelerate 近似）
@Composable
private fun AnimatedTemp(celsius: Double?, unit: String) {
    if (celsius == null) {
        // 无数据显示 "--"，而不是误导性的 "0"（v0.0.1）
        Text(
            text = "--",
            style = MaterialTheme.typography.displayLarge,
            color = ZhishengText,
            fontWeight = FontWeight.Bold,
        )
        return
    }
    val target = if (unit == "f") celsius * 9.0 / 5.0 + 32.0 else celsius
    val anim = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        anim.animateTo(target.toFloat(), tween(400))
    }
    Text(
        text = anim.value.roundToInt().toString(),
        style = MaterialTheme.typography.displayLarge,
        color = ZhishengText,
        fontWeight = FontWeight.Bold,
    )
}

// —— 预警横幅：警示斜纹 + 按等级着色边框 ——
@Composable
private fun AlertSection(alerts: List<AlertInfo>, modifier: Modifier) {
    // 展开态按标题记忆：原来按列表位置 remember，预警条数变化时展开态会错位到别条（v0.0.2）
    val expandedTitles = remember { mutableStateListOf<String>() }
    // 单一闪烁时钟：原来每条预警各起一个 while(true)，多条预警时多个协程各自计时（v0.0.2）
    val blinkOn = rememberBlink()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        alerts.forEach { alert ->
            val expanded = alert.title in expandedTitles
            // v0.0.4：三源等级归一后按国标四档着色，未识别档退回警报红
            val c = alertLevelColor(alert.severity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RectangleShape)
                    .background(ZhishengCard)
                    .border(1.dp, c.copy(alpha = 0.7f), RectangleShape)
                    .clickable {
                        if (expanded) expandedTitles.remove(alert.title)
                        else expandedTitles.add(alert.title)
                    }
                    .padding(0.dp),
            ) {
                // 顶部警示斜纹
                Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
                    hazardStripes(this, c.copy(alpha = 0.75f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlinkDot(blinkOn, c)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(alert.title, style = MaterialTheme.typography.titleSmall, color = c, fontWeight = FontWeight.Bold)
                        alert.pubTime?.let {
                            Text(formatAlertTime(it), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                        }
                    }
                    Text(
                        if (expanded) "[-]" else "[+]",
                        style = MaterialTheme.typography.labelMedium,
                        color = c,
                    )
                }
                if (expanded && !alert.detail.isNullOrBlank()) {
                    HorizontalDivider(color = c.copy(alpha = 0.3f), thickness = 1.dp)
                    Text(
                        alert.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun hazardStripes(scope: DrawScope, color: Color) {
    with(scope) {
        val w = 10f
        var x = -size.height
        while (x < size.width) {
            val path = Path().apply {
                moveTo(x, size.height)
                lineTo(x + size.height, 0f)
                lineTo(x + size.height + w, 0f)
                lineTo(x + w, size.height)
                close()
            }
            drawPath(path, color)
            x += w * 2.4f
        }
    }
}

// 1Hz 闪烁时钟：整个预警区共用一个，随 composable 离开屏幕自动停
@Composable
private fun rememberBlink(): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            on = !on
        }
    }
    return on
}

@Composable
private fun BlinkDot(on: Boolean, color: Color? = null) {
    // 默认取主题警报红（composable getter 不能出现在默认参数表达式里，v0.0.5）
    val c = color ?: ZhishengRed
    Box(
        Modifier
            .size(8.dp)
            .background(if (on) c else c.copy(alpha = 0.25f)),
    )
}

@Composable
private fun SpacetimeObservatory(
    modifier: Modifier = Modifier,
    onHistoryClick: () -> Unit,
    onRadarClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WeatherToolEntry(
            index = "01",
            title = "往年同日",
            subtitle = "5/10 年对照",
            modifier = Modifier.weight(1f),
            onClick = onHistoryClick,
        )
        WeatherToolEntry(
            index = "02",
            title = "雷达回波",
            subtitle = "近 2 小时",
            modifier = Modifier.weight(1f),
            onClick = onRadarClick,
        )
    }
}

@Composable
private fun WeatherToolEntry(
    index: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(ZhishengSurface, RectangleShape)
            .border(1.dp, ZhishengCardBorder, RectangleShape)
            .clickable(role = Role.Button, onClickLabel = "打开$title", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$index/", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ZhishengText, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, maxLines = 1)
        }
        Text(
            "→",
            style = MaterialTheme.typography.labelMedium,
            color = ZhishengMint,
        )
    }
}

@Composable
private fun SectionTitle(index: Int, title: String, en: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d//".format(index), style = MaterialTheme.typography.titleSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = ZhishengTextSecondary, letterSpacing = 2.sp)
        Spacer(Modifier.width(8.dp))
        Text(en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        Text("─".repeat(6), style = MaterialTheme.typography.labelSmall, color = ZhishengCardBorder)
    }
}

// —— 角括号 HUD 卡片 ——
@Composable
private fun HudCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RectangleShape)
            .background(ZhishengSurface)
            .then(Modifier.hudBorder())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun Modifier.hudBorder() = this
    .border(1.dp, ZhishengCardBorder, RectangleShape)
    .padding(0.dp)
    .then(
        Modifier.drawCornerBrackets(ZhishengOrange)
    )

private fun Modifier.drawCornerBrackets(color: Color) = this.then(
    Modifier.drawWithContent {
        drawContent()
        val len = 7.dp.toPx()
        val w = 1.6.dp.toPx()
        // 四角 L 形
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), w)
        drawLine(color, Offset(0f, 0f), Offset(0f, len), w)
        drawLine(color, Offset(size.width, 0f), Offset(size.width - len, 0f), w)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, len), w)
        drawLine(color, Offset(0f, size.height), Offset(len, size.height), w)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - len), w)
        drawLine(color, Offset(size.width, size.height), Offset(size.width - len, size.height), w)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - len), w)
    }
)

// —— 逐时：横向滚动 + 连续温度曲线 + 降水概率 ——
// v0.0.2 重做：原实现每格各画「本格中心→本格右边」的半段贝塞尔，格与格首尾不相接，
// 视觉上是一串断开的小弧线（用户反馈"那个线很丑"）。现改为每格画
// 「左邻中点→本格中心→右邻中点」的连续折线 + 渐隐面积填充，跨格严丝合缝。
@Composable
private fun HourlySection(
    hourly: List<HourlyWeather>,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    modifier: Modifier,
) {
    val temps = hourly.mapNotNull { h -> conv(h.temperature, unit) }
    val minT = temps.minOrNull() ?: 0.0
    val maxT = temps.maxOrNull() ?: 1.0
    // 0.0.9-debug 修复：原实现每格独立用 ±40 分钟双向容差判「现在」，
    // :20-:40 之间上一整点与下一整点同时命中，两格都标「现在」并高亮。
    // 改为在父层算唯一「现在」格：优先取包含当前时刻的小时格（10:50 属于
    // 10:00 格），找不到（该格已被 dropPastHourly 裁掉）再退回 40 分钟
    // 窗口内最近的一格；均无则不标。
    val nowMs = System.currentTimeMillis()
    val nowIdx = WeatherConsistency.currentHourIndex(hourly, nowMs)
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            // key=时间戳：数据刷新时按身份复用 item，不整列重绑（v0.0.1）
            LazyRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                itemsIndexed(hourly, key = { _, h -> h.timeMillis }) { i, h ->
                    HourlyItem(
                        h = h,
                        prev = hourly.getOrNull(i - 1),
                        next = hourly.getOrNull(i + 1),
                        unit = unit,
                        minT = minT,
                        maxT = maxT,
                        isNow = i == nowIdx,
                        windUnit = windUnit,
                        utcOffsetSeconds = utcOffsetSeconds,
                    )
                }
            }
            // 图例：底部两行数字分别是降水概率与风速，去掉每格的 km/h 后在此说明一次
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hourly.any { it.precipProb != null && it.precipProb > 0 }) {
                    Box(Modifier.size(width = 6.dp, height = 2.dp).background(ZhishengCyan))
                    Spacer(Modifier.width(5.dp))
                    Text("降水概率", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                    Spacer(Modifier.width(14.dp))
                }
                Box(Modifier.size(width = 6.dp, height = 2.dp).background(ZhishengTextTertiary))
                Spacer(Modifier.width(5.dp))
                Text(Fmt.windUnitLabel(windUnit), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                Spacer(Modifier.weight(1f))
                Text(
                    "${hourly.size}H",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
        }
    }
}

private fun conv(c: Double?, unit: String): Double? =
    c?.let { if (unit == "f") it * 9.0 / 5.0 + 32.0 else it }

// 归一化温度条参数：返回 (lo, hi, widthFraction)，均限制在 [0,1]。
// low/high 为数据源原始摄氏度；weekMin/weekMax 为已按 unit 换算的显示温度
// （与 DailySection 调用约定一致：weekMin/weekMax 由 lows/highs 经 conv 预算）。
// 提取为纯函数以便对 lo 接近 1 的极端温度分布做回归（v0.0.3）。
// 原内联写法 (hi-lo).coerceIn(0.03f, 1f-lo) 当 lo>0.97 时下界大于上界，
// Float.coerceIn 会抛 IllegalArgumentException，致逐日区域整体崩溃。
internal fun tempBarParams(
    low: Double?,
    high: Double?,
    weekMin: Double,
    weekMax: Double,
    unit: String,
): Triple<Float, Float, Float> {
    val range = (weekMax - weekMin).coerceAtLeast(1.0)
    val a = (((conv(low, unit) ?: weekMin) - weekMin) / range).toFloat()
    val b = (((conv(high, unit) ?: weekMax) - weekMin) / range).toFloat()
    val lo = minOf(a, b).coerceIn(0f, 1f)
    val hi = maxOf(a, b).coerceIn(0f, 1f)
    // 空间允许时保底 0.03f 可见；lo 接近 1 时收缩宽度，避免下界超过上界且不溢出右边界。
    val maxW = (1f - lo).coerceAtLeast(0f)
    val minW = minOf(0.03f, maxW)
    val w = (hi - lo).coerceIn(minW, maxW)
    return Triple(lo, hi, w)
}

// 昨日温差：按当前显示单位换算后取整再相减，保证 ΔT 与高低温读数一致（v0.0.3）。
// 原代码直接用原始摄氏度相减，华氏度模式下 ΔT 会和高低温读数对不上。
internal fun tempDelta(todayHigh: Double?, yesterdayHigh: Double?, unit: String): Int? {
    if (todayHigh == null || yesterdayHigh == null) return null
    return (conv(todayHigh, unit) ?: todayHigh).roundToInt() -
        (conv(yesterdayHigh, unit) ?: yesterdayHigh).roundToInt()
}

@Composable
private fun HourlyItem(
    h: HourlyWeather,
    prev: HourlyWeather?,
    next: HourlyWeather?,
    unit: String,
    minT: Double,
    maxT: Double,
    isNow: Boolean,
    windUnit: String,
    utcOffsetSeconds: Int?,
) {
    val itemW = 54.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(itemW),
    ) {
        Text(
            text = if (isNow) "现在" else Fmt.hour(h.timeMillis, utcOffsetSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = if (isNow) ZhishengMint else ZhishengTextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        // 条件缺失时 WeatherIcon 不绘制内容，但图标槽仍必须保留；否则该格后续的
        // 温度与 Canvas 会整体上移，连续曲线在格子边界出现“飞线”。
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            WeatherIcon(h.condition, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(2.dp))
        // 温度读数放在曲线正上方，视线不用来回跳
        Text(
            text = Fmt.temp(h.temperature, unit)?.let { "$it°" } ?: "--",
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengText,
        )
        Spacer(Modifier.height(3.dp))
        // 连续曲线：左半段接上一格中点，右半段接下一格中点（颜色提前取值，Canvas lambda 非 composable）
        val curveMint = ZhishengMint
        val curveBg = ZhishengBg
        Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            val range = (maxT - minT).coerceAtLeast(1.0).toFloat()
            val top = 4f
            val usable = size.height - top - 4f
            fun yOf(v: Double?): Float? = v?.let {
                size.height - 4f - ((it - minT).toFloat() / range) * usable
            }

            val cx = size.width / 2f
            val yCur = yOf(conv(h.temperature, unit)) ?: return@Canvas
            val yPrev = yOf(prev?.let { conv(it.temperature, unit) })
            val yNext = yOf(next?.let { conv(it.temperature, unit) })

            // 左右邻的中点：与相邻格画出的同一点重合，所以跨格连续
            val pLeft = yPrev?.let { Offset(0f, (it + yCur) / 2f) }
            val pRight = yNext?.let { Offset(size.width, (it + yCur) / 2f) }
            val pCur = Offset(cx, yCur)

            // 面积填充（曲线到底边），极淡，给折线一点体积感
            val fill = Path().apply {
                moveTo(pLeft?.x ?: cx, pLeft?.y ?: yCur)
                lineTo(pCur.x, pCur.y)
                pRight?.let { lineTo(it.x, it.y) }
                lineTo(pRight?.x ?: cx, size.height)
                lineTo(pLeft?.x ?: cx, size.height)
                close()
            }
            drawPath(fill, curveMint.copy(alpha = 0.07f))

            // 折线本体
            val line = Path().apply {
                moveTo(pLeft?.x ?: cx, pLeft?.y ?: yCur)
                lineTo(pCur.x, pCur.y)
                pRight?.let { lineTo(it.x, it.y) }
            }
            drawPath(line, curveMint.copy(alpha = 0.75f), style = Stroke(1.6f))

            // 「现在」格用实心亮点强调，其余用小空心点
            if (isNow) {
                drawCircle(curveMint, 3.2f, pCur)
            } else {
                drawCircle(curveBg, 2.6f, pCur)
                drawCircle(curveMint.copy(alpha = 0.85f), 2.6f, pCur, style = Stroke(1.2f))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = Fmt.probability(h.precipProb) ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengCyan,
        )
        Text(
            text = Fmt.windValue(h.windSpeed, windUnit) ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
        )
    }
}

// —— 短时降水：先回答何时开始/停止，再展示原生时间粒度 ——
@Composable
private fun PrecipCard(data: WeatherData, modifier: Modifier) {
    // 0.0.9-debug 修复：离线缓存兜底时（staleAgeMillis 可 ≥10 分钟），分钟序列
    // 仍从抓取时刻起画——已过去的柱被画在紧贴 "NOW" 标签的位置，像是正在下。
    // 绘制前裁掉 2 分钟窗口之前的历史柱；全裁空就保持空，绝不把过期雨柱复活成“现在”。
    val nowMillis = System.currentTimeMillis()
    val minutes = data.rainMinutes
        .filter { it.timeMillis >= nowMillis - Nowcast.NOW_WINDOW_MS }
        .sortedBy { it.timeMillis }
    val rainDistanceKm = data.rainDistanceKm
    val precipNow = data.current.let { cur ->
        cur != null && (cur.condition?.isPrecipitation == true || (cur.precipMm ?: 0.0) > 0.05)
    }
    val chartCeiling = Nowcast.precipChartCeiling(minutes)
    val timing = Nowcast.rainTiming(minutes, nowMillis, currentPrecip = precipNow)
    val dry = Nowcast.precipCardClearWindow(minutes, nowMillis, precipNow)
    val timingLabel = Nowcast.rainTimingLabel(timing)
    val meta = data.rainMeta
    val horizonMinutes = meta?.horizonMinutes?.coerceIn(30, 180) ?: 120
    val peak = minutes.maxOfOrNull { it.precip }?.coerceAtLeast(0f) ?: 0f
    val currentRate = data.current?.precipMm?.toFloat()?.takeIf { it > 0f }
    val distanceLabel = rainDistanceKm?.takeIf { it > 0.0 }?.let { km ->
        if (km == Math.floor(km)) km.toInt().toString() else String.format(Locale.US, "%.1f", km)
    }
    val statusText = timingLabel ?: when {
        !dry -> data.rainNowcast?.trim()?.takeIf { it.isNotEmpty() } ?: "未来 2 小时有降水"
        distanceLabel != null -> "近处无雨 · 雨区距此 $distanceLabel km"
        else -> "未来 2 小时无降水"
    }
    val intervalMinutes = meta?.intervalMinutes?.takeIf { it > 0 }
        ?: minutes.zipWithNext { a, b -> ((b.timeMillis - a.timeMillis) / Nowcast.MINUTE_MS).toInt() }
            .firstOrNull { it > 0 }
        ?: 1
    val source = Nowcast.sourceLabel(meta?.source ?: data.blockSources["minutely"] ?: data.dataSource)
    val sourceLine = buildString {
        append(source)
        append(" · ")
        append(intervalMinutes)
        append("分钟级")
        (meta?.updateTime ?: data.updateTime)?.let {
            append(" · 更新于 ")
            append(Fmt.clock(it, data.utcOffsetSeconds))
        }
    }
    val peakLabel = when {
        peak <= 0f && currentRate != null ->
            "当前 ${String.format(Locale.US, "%.1f", currentRate)} mm/h · 未来雨势暂缺"
        peak <= 0f && precipNow -> "当前有雨 · 未来雨势暂缺"
        peak in 0f..0.05f -> "峰值 <0.1 mm/h · ${Nowcast.intensityLabel(peak)}"
        else -> "峰值 ${String.format(Locale.US, "%.1f", peak)} mm/h · ${Nowcast.intensityLabel(peak)}"
    }
    // Canvas lambda 非 composable，颜色提前取值。
    val barCyan = ZhishengCyan.copy(alpha = 0.85f)
    val barBorder = ZhishengCardBorder
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (dry) statusText
                    else "$statusText，$peakLabel"
                },
        ) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(if (dry) ZhishengMint else ZhishengOrange),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (dry) "CLEAR WINDOW" else "SHORT-TERM PRECIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dry) ZhishengMint else ZhishengOrange,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!dry) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        peakLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengCyan,
                        maxLines = 1,
                    )
                } else {
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.width(76.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            "2H",
                            style = MaterialTheme.typography.headlineSmall,
                            color = ZhishengMint,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            "CLEAR SIGNAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                            maxLines = 1,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(if (dry) 10.dp else 28.dp)) {
                val baseline = size.height - 1.dp.toPx()
                drawLine(barBorder, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                if (!dry && minutes.isNotEmpty() && chartCeiling > 0f) {
                    val horizonMs = horizonMinutes * Nowcast.MINUTE_MS
                    val bucketWidth = (size.width * intervalMinutes / horizonMinutes.toFloat())
                        .coerceIn(1.dp.toPx(), 18.dp.toPx())
                    val minWetHeight = 2.dp.toPx()
                    minutes.forEach { minute ->
                        if (minute.precip > 0f) {
                            val scaled = (minute.precip / chartCeiling).coerceIn(0f, 1f)
                            val hgt = (scaled * (size.height - 2.dp.toPx())).coerceAtLeast(minWetHeight)
                            val x = ((minute.timeMillis - nowMillis).toFloat() / horizonMs)
                                .coerceIn(0f, 1f) * size.width
                            drawRect(
                                color = barCyan,
                                topLeft = Offset(x, baseline - hgt),
                                size = androidx.compose.ui.geometry.Size(bucketWidth * 0.82f, hgt),
                            )
                        }
                    }
                } else if (!dry && currentRate != null) {
                    // 分钟接口没有有效曲线时，只画一个“现在”的实况柱，不把它延伸到未来。
                    val hgt = (size.height * 0.68f).coerceAtLeast(2.dp.toPx())
                    drawRect(
                        color = barCyan,
                        topLeft = Offset(0f, baseline - hgt),
                        size = androidx.compose.ui.geometry.Size(5.dp.toPx(), hgt),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("现在", "30", "60", "90", "120 分钟").forEachIndexed { index, label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == 0) {
                            if (dry) ZhishengMint else ZhishengOrange
                        } else ZhishengTextTertiary,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            4 -> TextAlign.End
                            else -> TextAlign.Center
                        },
                    )
                }
            }
            Spacer(Modifier.height(if (dry) 2.dp else 5.dp))
            Text(sourceLine, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
    }
}

// —— 逐日：全周归一化温度区间条 ——
@Composable
private fun DailySection(
    daily: List<DailyWeather>,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    modifier: Modifier,
) {
    val lows = daily.mapNotNull { conv(it.low, unit) }
    val highs = daily.mapNotNull { conv(it.high, unit) }
    val weekMin = lows.minOrNull() ?: 0.0
    val weekMax = highs.maxOrNull() ?: 1.0
    var expandedMillis by remember { mutableStateOf<Long?>(null) }

    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            daily.forEachIndexed { index, d ->
                val expanded = expandedMillis == d.dateMillis
                val isToday = Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天"
                if (index > 0 && Fmt.isDifferentMonth(daily[index - 1].dateMillis, d.dateMillis, utcOffsetSeconds)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Fmt.month(d.dateMillis, utcOffsetSeconds),
                            modifier = Modifier.width(50.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            textAlign = TextAlign.Center,
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = ZhishengCyan.copy(alpha = 0.35f),
                            thickness = 1.dp,
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expandedMillis = if (expanded) null else d.dateMillis }
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.width(50.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isToday) ZhishengMint else ZhishengText,
                                maxLines = 1,
                            )
                            Text(
                                text = Fmt.dayOfMonth(d.dateMillis, utcOffsetSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) ZhishengMint.copy(alpha = 0.8f) else ZhishengTextTertiary,
                                maxLines = 1,
                            )
                        }
                        WeatherIcon(d.condition, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Fmt.probability(d.precipProbability) ?: "  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            modifier = Modifier.width(30.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        Text(
                            Fmt.temp(d.low, unit)?.let { "$it°" } ?: "--",
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhishengTextTertiary,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End,
                        )
                        // 归一化温度条
                        BoxWithConstraints(
                            Modifier.padding(horizontal = 8.dp).weight(1f).height(4.dp)
                                .background(ZhishengTextTertiary.copy(alpha = 0.3f), RectangleShape)
                        ) {
                            // lo/hi/w 经 tempBarParams 统一归一：源数据偶发把高低温写反（小米 from/to
                            // 语义不定），且 lo 接近 1 时需收缩宽度避免 coerceIn 下界超过上界（v0.0.3）
                            val (lo, _, w) = tempBarParams(d.low, d.high, weekMin, weekMax, unit)
                            Box(
                                Modifier
                                    .offset(x = maxWidth * lo)
                                    .width(maxWidth * w)
                                    .fillMaxHeight()
                                    .background(tempColor(d.low), RectangleShape),
                            )
                        }
                        Text(
                            Fmt.temp(d.high, unit)?.let { "$it°" } ?: "--",
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhishengText,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                    if (expanded) {
                        DailyExpanded(d, windUnit)
                    }
                }
                if (index < daily.size - 1) {
                    HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun DailyExpanded(d: DailyWeather, windUnit: String) {
    Column(Modifier.padding(start = 56.dp, top = 2.dp, bottom = 6.dp, end = 4.dp)) {
        d.weatherText?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengMint)
            Spacer(Modifier.height(4.dp))
        }
        d.windSpeed?.let {
            Text("风 ${Fmt.wind(it, windUnit)}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Spacer(Modifier.height(4.dp))
        }
        Row {
            d.sunrise?.let {
                Text("日出 $it", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
                Spacer(Modifier.width(14.dp))
            }
            d.sunset?.let {
                Text("日落 $it", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
            }
        }
        d.precipMm?.takeIf { it > 0.0 }?.let { mm ->
            Spacer(Modifier.height(4.dp))
            Text(
                "降水 ${if (mm == Math.floor(mm)) mm.toInt().toString() else String.format(java.util.Locale.US, "%.1f", mm)} mm",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengCyan,
            )
        }
        if (d.moonPhase != null || d.moonrise != null || d.moonset != null) {
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "月相 ${Fmt.moonPhaseZh(d.moonPhase) ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengCyan,
                )
                Spacer(Modifier.width(14.dp))
                d.moonrise?.let {
                    Text("月出 $it", style = MaterialTheme.typography.labelSmall, color = ZhishengCyan)
                    Spacer(Modifier.width(14.dp))
                }
                d.moonset?.let {
                    Text("月落 $it", style = MaterialTheme.typography.labelSmall, color = ZhishengCyan)
                }
            }
        }
    }
}

// 温度色：两段插值 钢青 → 翡翠 → 琥珀（单段青→橙的中点会发灰发脏，v0.0.5 盘查）
@Composable
private fun tempColor(low: Double?): Color {
    val t = (((low ?: 10.0) + 10.0) / 45.0).toFloat().coerceIn(0f, 1f)
    return if (t < 0.5f) {
        colorLerp(ZhishengCyan, ZhishengMint, t * 2f)
    } else {
        colorLerp(ZhishengMint, ZhishengOrange, (t - 0.5f) * 2f)
    }
}

// —— 遥测卡格：2 列 HUD 小卡 ——
@Composable
private fun TelemetryGrid(
    cur: CurrentWeather,
    today: DailyWeather?,
    unit: String,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    modifier: Modifier,
) {
    // 没数的格不画：小米实况没有 1 时降水，硬留第九格会 -- 还在右侧留空（v0.0.7）。
    val items = listOf(
        TelemetryMetric.HUMIDITY to Triple("湿度", "HUMIDITY", cur.humidity?.let { "${it.roundToInt()}%" }),
        TelemetryMetric.WIND to Triple("风向风速", "WIND", windLabel(cur, prefs.windUnit)),
        TelemetryMetric.PRESSURE to Triple("气压", "PRESS", Fmt.pressure(cur.pressure, prefs.pressureUnit)),
        TelemetryMetric.UV to Triple("紫外线", "UV", cur.uvIndex?.let { uvText(it) }),
        TelemetryMetric.VISIBILITY to Triple("能见度", "VIS", cur.visibility?.let { "${it.roundToInt()} km" }),
        TelemetryMetric.DEW_POINT to Triple("露点", "DEW", cur.dewPoint?.let { "${Fmt.temp(it, unit)}°" }),
        TelemetryMetric.CLOUD_COVER to Triple("云量", "CLOUD", cur.cloudCover?.let { "${it.roundToInt()}%" }),
        TelemetryMetric.WIND_GUST to Triple("阵风", "GUST", Fmt.wind(cur.windGust, prefs.windUnit)),
        TelemetryMetric.PRECIPITATION to Triple("1时降水", "PRECIP", cur.precipMm?.let { String.format(Locale.US, "%.1f mm/h", it) }),
    ).filter { (metric, _) -> metric in prefs.telemetryMetrics }
        .mapNotNull { (_, item) ->
            val (cn, en, value) = item
            value?.let { Triple(cn, en, it) }
        }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (cn, en, value) ->
                    TeleCell(cn, en, value, cur, Modifier.weight(1f).fillMaxHeight())
                }
                // 奇数项让最后一格占满整行，不再人为保留一个空白右栏。
            }
            Spacer(Modifier.height(8.dp))
        }
        // 日月宽卡：公共源不提供月出月落时由本地天文计算补齐。
        if (TelemetryMetric.LUMINARY in prefs.telemetryMetrics && today != null && (
                today.sunrise != null || today.sunset != null || today.moonPhase != null ||
                    today.moonrise != null || today.moonset != null
                )
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RectangleShape)
                    .background(ZhishengSurface)
                    .border(1.dp, ZhishengCardBorder, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    TeleLabel("日月", "LUMINARY")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        today?.sunrise?.let {
                            Text("日出 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                            Text(it, style = MaterialTheme.typography.titleSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(18.dp))
                        today?.sunset?.let {
                            Text("日落 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                            Text(it, style = MaterialTheme.typography.titleSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("月相 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                        Text(
                            Fmt.moonPhaseZh(today.moonPhase) ?: "--",
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhishengCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("月出 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                        Text(today.moonrise ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(18.dp))
                        Text("月落 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                        Text(today.moonset ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun telemetryMetricAvailable(
    metric: TelemetryMetric,
    cur: CurrentWeather,
    today: DailyWeather?,
): Boolean = when (metric) {
    TelemetryMetric.HUMIDITY -> cur.humidity != null
    TelemetryMetric.WIND -> cur.windSpeed != null || cur.windDirectionDeg != null
    TelemetryMetric.PRESSURE -> cur.pressure != null
    TelemetryMetric.UV -> cur.uvIndex != null
    TelemetryMetric.VISIBILITY -> cur.visibility != null
    TelemetryMetric.DEW_POINT -> cur.dewPoint != null
    TelemetryMetric.CLOUD_COVER -> cur.cloudCover != null
    TelemetryMetric.WIND_GUST -> cur.windGust != null
    TelemetryMetric.PRECIPITATION -> cur.precipMm != null
    TelemetryMetric.LUMINARY -> today?.let {
        it.sunrise != null || it.sunset != null || it.moonPhase != null ||
            it.moonrise != null || it.moonset != null
    } == true
}

@Composable
private fun TeleCell(
    cn: String,
    en: String,
    value: String,
    cur: CurrentWeather,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RectangleShape)
            .background(ZhishengSurface)
            .border(1.dp, ZhishengCardBorder, RectangleShape)
            .drawCornerBrackets(ZhishengOrange.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            TeleLabel(cn, en)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (en == "WIND" && cur.windDirectionDeg != null) {
                    WindCompass(cur.windDirectionDeg)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WindCompass(degrees: Double) {
    val ring = ZhishengCardBorder
    val north = ZhishengOrange
    val vector = ZhishengCyan
    val surface = ZhishengSurface
    val shadow = ZhishengBg.copy(alpha = 0.92f)
    val rimHighlight = ZhishengTextTertiary.copy(alpha = 0.65f)
    val needleHighlight = ZhishengText.copy(alpha = 0.55f)
    val hubHighlight = ZhishengText.copy(alpha = 0.7f)
    Box(
        Modifier
            .size(38.dp)
            .semantics { contentDescription = "风向 ${degrees.roundToInt()} 度" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ovalLeft = 2.dp.toPx()
            val ovalTop = 10.dp.toPx()
            val ovalSize = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), 22.dp.toPx())
            // 扁椭圆底座 + 上缘高光 / 下缘阴影，制造悬浮罗盘的纵深。
            drawOval(shadow, Offset(ovalLeft, ovalTop + 2.dp.toPx()), ovalSize)
            drawOval(surface, Offset(ovalLeft, ovalTop), ovalSize)
            drawOval(ring, Offset(ovalLeft, ovalTop), ovalSize, style = Stroke(1.2.dp.toPx()))
            drawArc(
                color = rimHighlight,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(ovalLeft + 1.dp.toPx(), ovalTop + 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(ovalSize.width - 2.dp.toPx(), ovalSize.height - 2.dp.toPx()),
                style = Stroke(0.8.dp.toPx()),
            )
            val c = Offset(size.width / 2f, ovalTop + ovalSize.height / 2f)
            drawLine(north, Offset(c.x, ovalTop - 2.dp.toPx()), Offset(c.x, ovalTop + 3.dp.toPx()), 1.8.dp.toPx())
            drawCircle(ring, 3.8.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        // 指针与文字共用同一“来向”角度：北=0°向上、东=90°向右，不再额外翻转 180°。
        Canvas(Modifier.fillMaxSize().rotate(degrees.toFloat())) {
            val c = Offset(size.width / 2f, 21.dp.toPx())
            val headY = 3.dp.toPx()
            val tailY = 30.dp.toPx()
            val depth = Offset(1.4.dp.toPx(), 1.6.dp.toPx())
            drawLine(shadow, Offset(c.x, tailY) + depth, Offset(c.x, headY + 6.dp.toPx()) + depth, 4.4.dp.toPx())
            drawLine(vector.copy(alpha = 0.45f), Offset(c.x, tailY), Offset(c.x, headY + 6.dp.toPx()), 4.dp.toPx())
            drawLine(vector, Offset(c.x - 0.7.dp.toPx(), tailY), Offset(c.x - 0.7.dp.toPx(), headY + 6.dp.toPx()), 1.7.dp.toPx())
            val headShadow = Path().apply {
                moveTo(c.x + depth.x, headY + depth.y)
                lineTo(c.x - 5.dp.toPx() + depth.x, headY + 8.dp.toPx() + depth.y)
                lineTo(c.x + 5.dp.toPx() + depth.x, headY + 8.dp.toPx() + depth.y)
                close()
            }
            drawPath(headShadow, shadow)
            val head = Path().apply {
                moveTo(c.x, headY)
                lineTo(c.x - 5.dp.toPx(), headY + 8.dp.toPx())
                lineTo(c.x + 5.dp.toPx(), headY + 8.dp.toPx())
                close()
            }
            drawPath(head, vector)
            drawLine(
                needleHighlight,
                Offset(c.x - 1.5.dp.toPx(), headY + 2.dp.toPx()),
                Offset(c.x - 3.6.dp.toPx(), headY + 6.5.dp.toPx()),
                0.8.dp.toPx(),
            )
            drawCircle(shadow, 3.7.dp.toPx(), Offset(c.x, tailY) + depth)
            drawCircle(north, 3.2.dp.toPx(), Offset(c.x, tailY))
            drawCircle(shadow, 3.8.dp.toPx(), c)
            drawCircle(vector, 3.2.dp.toPx(), c)
            drawCircle(hubHighlight, 1.dp.toPx(), Offset(c.x - 0.8.dp.toPx(), c.y - 0.8.dp.toPx()))
        }
    }
}

@Composable
private fun TeleLabel(cn: String, en: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 8.dp).background(ZhishengOrange))
        Spacer(Modifier.width(6.dp))
        Text(cn, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
    }
}

private fun windLabel(cur: CurrentWeather, windUnit: String): String? {
    val dir = com.zhisheng.weather.data.WeatherRepository.windDirection(cur.windDirectionDeg)
    val speed = Fmt.wind(cur.windSpeed, windUnit)
    return when {
        dir != null && speed != null -> "$dir $speed"
        dir != null -> dir
        speed != null -> speed
        else -> null
    }
}

private fun uvText(uv: Int): String = when {
    uv <= 2 -> "$uv 弱"
    uv <= 5 -> "$uv 中等"
    uv <= 7 -> "$uv 强"
    uv <= 10 -> "$uv 很强"
    else -> "$uv 极强"
}

// —— AQI ——
@Composable
private fun AqiCard(aqi: AqiInfo, modifier: Modifier) {
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = aqi.value?.toString() ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    color = aqiColor(aqi.value),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(aqi.level ?: "空气质量", style = MaterialTheme.typography.titleMedium, color = aqiColor(aqi.value), fontWeight = FontWeight.Bold)
                    Text("AQI // AIR QUALITY INDEX", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                aqi.primary?.let {
                    Text("首要污染物 $it", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 刻度尺 + 游标
            Box(Modifier.fillMaxWidth().height(4.dp).background(ZhishengCardBorder, RectangleShape)) {
                Box(
                    Modifier
                        .fillMaxWidth((aqi.value?.toFloat() ?: 0f).coerceIn(0f, 500f) / 500f)
                        .height(4.dp)
                        .background(aqiColor(aqi.value), RectangleShape),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PollutantChip("PM2.5", aqi.pm25, Modifier.weight(1f))
                PollutantChip("PM10", aqi.pm10, Modifier.weight(1f))
                PollutantChip("O3", aqi.o3, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PollutantChip("NO2", aqi.no2, Modifier.weight(1f))
                PollutantChip("SO2", aqi.so2, Modifier.weight(1f))
                PollutantChip("CO", aqi.co, Modifier.weight(1f))
            }
            // 健康建议（v0.0.4：小米 suggest 接入，其余源无此行）
            aqi.suggest?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }
    }
}

@Composable
private fun PollutantChip(name: String, value: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RectangleShape)
            .background(ZhishengCard)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Spacer(Modifier.weight(1f))
        Text(value ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengText)
    }
}

@Composable
private fun aqiColor(value: Int?): Color = when {
    value == null -> ZhishengTextTertiary
    value <= 50 -> ZhishengMint
    value <= 100 -> ZhishengMint.copy(alpha = 0.8f)
    value <= 150 -> ZhishengOrange
    value <= 200 -> ZhishengOrange.copy(alpha = 0.85f)
    value <= 300 -> ZhishengRed
    else -> ZhishengRed.copy(alpha = 0.8f)
}

// —— 生活指数 ——
internal data class LifeIndexUi(
    val name: String,
    val en: String,
    val value: String,
    val positive: Boolean? = null,
)

internal fun clampCityDeckPosition(position: Float, cityCount: Int): Float {
    if (cityCount <= 0) return 0f
    return position.coerceIn(0f, (cityCount - 1).toFloat())
}

internal fun lifeIndexItems(data: WeatherData, selected: Set<LifeIndexMetric>): List<LifeIndexUi> = buildList {
    data.carWashOk?.takeIf { LifeIndexMetric.CAR_WASH in selected }?.let {
        add(LifeIndexUi(LifeIndexMetric.CAR_WASH.cn, LifeIndexMetric.CAR_WASH.en, if (it) "适宜" else "不适宜", it))
    }
    data.sportsOk?.takeIf { LifeIndexMetric.SPORTS in selected }?.let {
        add(LifeIndexUi(LifeIndexMetric.SPORTS.cn, LifeIndexMetric.SPORTS.en, if (it) "适宜" else "不适宜", it))
    }
    data.extraIndices.forEach { index ->
        val value = index.category.trim()
        if (value.isEmpty()) return@forEach
        val metric = LifeIndexMetric.fromEnglish(index.en)
        if (metric != null) {
            if (metric in selected) add(LifeIndexUi(metric.cn, metric.en, value))
        } else if (index.name.isNotBlank()) {
            add(LifeIndexUi(index.name, index.en, value))
        }
    }
}.distinctBy { it.name }

@Composable
private fun IndicesRow(data: WeatherData, selected: Set<LifeIndexMetric>, modifier: Modifier) {
    val items = lifeIndexItems(data, selected)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    LifeIndexCard(item, Modifier.weight(1f).fillMaxHeight())
                }
                // 奇数项独占最后一行，避免人为留下半屏空栏。
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LifeIndexCard(item: LifeIndexUi, modifier: Modifier = Modifier) {
    val accent = when (item.positive) {
        true -> ZhishengMint
        false -> ZhishengOrange
        null -> ZhishengCardBorder
    }
    Column(
        modifier
            .clip(RectangleShape)
            .background(ZhishengSurface)
            .border(1.dp, accent.copy(alpha = if (item.positive == null) 1f else 0.5f), RectangleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 8.dp).background(ZhishengOrange))
            Spacer(Modifier.width(6.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.labelMedium,
                color = ZhishengTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                item.en,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                letterSpacing = 0.7.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium,
                color = item.positive?.let { accent } ?: ZhishengText,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.positive?.let {
                Spacer(Modifier.width(6.dp))
                Text(if (it) "[OK]" else "[NG]", style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
    }
}

// —— 昨日复盘 ——
@Composable
private fun YesterdayCard(y: YesterdayInfo, today: DailyWeather?, unit: String, modifier: Modifier) {
    HudCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (y.condition != null) {
                WeatherIcon(y.condition, Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
            }
            if (y.high != null && y.low != null) {
                Text(
                    "${Fmt.temp(y.high, unit)}° / ${Fmt.temp(y.low, unit)}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
            }
            y.aqi?.let {
                Text("AQI $it", style = MaterialTheme.typography.labelMedium, color = aqiColor(it))
            }
            Spacer(Modifier.weight(1f))
            tempDelta(today?.high, y.high, unit)?.let { diff ->
                Text(
                    "ΔT ${if (diff >= 0) "+" else ""}$diff°",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (diff > 0) ZhishengOrange else ZhishengMint,
                )
            }
        }
    }
}

// —— 台风 ——
@Composable
private fun TyphoonCard(typhoons: List<TyphoonInfo>, modifier: Modifier) {
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            typhoons.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        t.type ?: "TY",
                        style = MaterialTheme.typography.labelMedium,
                        color = ZhishengOrange,
                        modifier = Modifier.width(34.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(t.name ?: "", style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                    Spacer(Modifier.width(8.dp))
                    t.ename?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                    }
                    Spacer(Modifier.weight(1f))
                    t.windSpeed?.let {
                        Text("${it.roundToInt()}m/s", style = MaterialTheme.typography.labelMedium, color = ZhishengCyan)
                    }
                }
            }
        }
    }
}

// —— 枳生页脚 ——
@Composable
private fun Footer(data: WeatherData, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ZHISHENG CORE // SENSOR-1 · FORECAST-2 · DISPLAY-3",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            letterSpacing = 1.5.sp,
        )
        Text(
            "${dataSourceSummary(data)} · 枳生天气 v${com.zhisheng.weather.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
        )
    }
}

private fun dataSourceLabel(source: String?): String = when (source) {
    "QWEATHER" -> "数据来自和风天气"
    "CAIYUN" -> "数据来自彩云天气"
    "XIAOMI" -> "数据来自小米公开接口"
    "OPEN-METEO" -> "数据来自 Open-Meteo"
    else -> "DATA ${source ?: "--"}"
}

private fun dataSourceSummary(data: WeatherData): String {
    val supplements = data.blockSources.values
        .filter { it != data.dataSource }
        .distinct()
        .map(::dataSourceShortLabel)
    return if (supplements.isEmpty()) dataSourceLabel(data.dataSource)
    else "${dataSourceLabel(data.dataSource)} · 部分数据由 ${supplements.joinToString("/")} 提供"
}

private fun supplementShortLabel(data: WeatherData): String {
    val extras = data.blockSources.values.filter { it != data.dataSource }.distinct()
    return if (extras.isEmpty()) "" else extras.joinToString(prefix = "+", separator = "+") { dataSourceShortLabel(it) }
}

private fun dataSourceShortLabel(source: String?): String = when (source) {
    "QWEATHER" -> "和风"
    "CAIYUN" -> "彩云"
    "XIAOMI" -> "小米"
    "OPEN-METEO" -> "OPEN-METEO"
    else -> source ?: "--"
}

// —— 启动加载：枳生终端自检序列 ——
@Composable
private fun BootState(bootAnim: Boolean = true) {
    val lines = listOf(
        "ZHISHENG WEATHER TERMINAL v${com.zhisheng.weather.BuildConfig.VERSION_NAME}",
        "ZHISHENG CORE ... ONLINE",
        "SYNC ATMOSPHERIC DATA ...",
    )
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // 关闭开机动画时直接全部显示，不逐行打字延迟（v0.0.3：bootAnim 设置项此前无人读取）
        if (!bootAnim) {
            count = lines.size
            return@LaunchedEffect
        }
        lines.indices.forEach { i ->
            kotlinx.coroutines.delay(260)
            count = i + 1
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            lines.take(count).forEach { l ->
                Text(
                    "> $l",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengMint,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "█",
                style = MaterialTheme.typography.bodySmall,
                color = ZhishengMint,
            )
        }
    }
}

@Composable
private fun EmptyState(onSearchClick: () -> Unit) {
    // 终端打字序列：与开屏 BootState 同款，逐字敲出 + █ 光标；文案不点名任何具体城市
    val lines = listOf(
        "NO CITY // 未接入城市",
        "SEARCH ANY CITY // 输入任意城市名",
        "AWAITING INPUT ...",
    )
    var doneCount by remember { mutableIntStateOf(0) }
    var chars by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        lines.forEachIndexed { i, l ->
            chars = 0
            l.indices.forEach { c ->
                kotlinx.coroutines.delay(26)
                chars = c + 1
            }
            kotlinx.coroutines.delay(240)
            doneCount = i + 1
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WeatherIcon(WeatherCondition.CLEAR, Modifier.size(64.dp).alpha(0.6f))
        Spacer(Modifier.height(24.dp))
        Column(Modifier.align(Alignment.Start)) {
            lines.take(doneCount).forEach { l ->
                Text(
                    "> $l",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengMint,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (doneCount < lines.size) {
                Text(
                    "> " + lines[doneCount].take(chars),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengMint,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                "█",
                style = MaterialTheme.typography.bodySmall,
                color = ZhishengMint,
            )
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .clip(RectangleShape)
                .background(ZhishengSurface)
                .border(1.dp, ZhishengMint.copy(alpha = 0.6f), RectangleShape)
                .drawCornerBrackets(ZhishengMint)
                .clickable(role = Role.Button, onClickLabel = "添加城市") { onSearchClick() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("[ + ADD CITY ]", style = MaterialTheme.typography.titleSmall, color = ZhishengMint, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ErrorState(message: String, onSearchClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("!! LINK FAILURE", style = MaterialTheme.typography.titleMedium, color = ZhishengRed, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
        Spacer(Modifier.height(16.dp))
        Text(
            "[ 换一个城市试试 ]",
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengMint,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "换一个城市") { onSearchClick() }
                .padding(8.dp),
        )
    }
}

// —— 城市抽屉 ——
@Composable
private fun CityDrawer(
    uiState: HomeUiState,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddCity: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZhishengSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            Text("00//", style = MaterialTheme.typography.titleSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("城市", style = MaterialTheme.typography.titleMedium, color = ZhishengText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("CITY LIST", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.5.sp)
        }
        if (uiState.cities.isEmpty()) {
            Text("还没有保存的城市", style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
        }
        uiState.cities.forEachIndexed { i, city ->
            val selected = city.locationKey == uiState.selectedCity?.locationKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(if (selected) ZhishengCard else Color.Transparent)
                    .clickable { onSelect(city.locationKey) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%02d".format(i + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) ZhishengOrange else ZhishengTextTertiary,
                )
                Spacer(Modifier.width(10.dp))
                if (selected) {
                    Box(Modifier.size(width = 3.dp, height = 14.dp).background(ZhishengMint))
                    Spacer(Modifier.width(8.dp))
                }
                // 城市名 + 归属地：同名城市（金川区@金昌 vs 金川县@阿坝）必须可区分（v0.0.1）
                Column(Modifier.weight(1f)) {
                    Text(
                        city.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) ZhishengMint else ZhishengText,
                    )
                    if (city.contextLabel.isNotBlank()) {
                        Text(
                            city.contextLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                        )
                    }
                }
                IconButton(onClick = { onRemove(city.locationKey) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "删除${city.displayName}", tint = ZhishengTextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RectangleShape)
                .background(ZhishengCard)
                .border(1.dp, ZhishengMint.copy(alpha = 0.5f), RectangleShape)
                .clickable { onAddCity() }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = ZhishengMint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("添加城市", style = MaterialTheme.typography.titleSmall, color = ZhishengMint, letterSpacing = 1.sp)
        }
    }
}

private fun formatAlertTime(s: String): String = try {
    s.substring(0, minOf(16, s.length)).replace("T", " ")
} catch (_: Exception) {
    s
}
