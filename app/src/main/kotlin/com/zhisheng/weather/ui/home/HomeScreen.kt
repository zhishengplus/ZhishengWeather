@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhisheng.weather.ui.home

import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.LocalWeatherPreviewTime
import com.zhisheng.weather.data.HomeSurfaceStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.i18n.uiText

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.FlowRow
import com.zhisheng.weather.ui.components.weatherSharedBounds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.model.TyphoonInfo
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.R
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.TelemetryMetric
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.WeatherViewModel
import com.zhisheng.weather.ui.rememberWorldHeadingDegrees
import com.zhisheng.weather.ui.windNeedleScreenRotation
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.components.CityOutlineMap
import com.zhisheng.weather.ui.theme.vistaSoftGlow
import com.zhisheng.weather.ui.components.WeatherAmbience
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.isNightAt
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.vistaTemperatureInk
import androidx.compose.ui.graphics.lerp as colorLerp
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengRed
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.ZhishengWarning
import com.zhisheng.weather.ui.theme.alertLevelColor
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.glassScrollHeader
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.components.NaturalWeatherSurface
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import com.zhisheng.weather.ui.components.VistaWeatherArtwork
import com.zhisheng.weather.ui.theme.vistaClick
import com.zhisheng.weather.ui.theme.zhishengScreen
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
// 枳生天气 · 磷光数据终端主屏
// Vista 布局序：元数据 → Hero → 预警 → 逐时 → 五日/更多 → 降水 → 遥测 → 空气质量
//            → 气象视界 → 台风 → 生活指数 → 昨日回看 → 页脚。
// 经典主题仍严格使用用户保存的 moduleOrder。
// ═══════════════════════════════════════════════════════════

/** Vista 首页的安全性和使用频率优先序；可用性只会移除块，绝不重排其余内容。 */
internal enum class VistaHomeBlock {
    METADATA,
    HERO,
    ALERTS,
    HOURLY,
    DAILY,
    PRECIPITATION,
    TELEMETRY,
    AQI,
    ATLAS,
    TYPHOON,
    INDICES,
    YESTERDAY,
}

internal fun vistaHomeBlocks(
    available: Set<VistaHomeBlock>,
    moduleOrder: List<HomeModule>? = null,
): List<VistaHomeBlock> {
    val ordered = moduleOrder?.map { module ->
        when (module) {
            HomeModule.HOURLY -> VistaHomeBlock.HOURLY
            HomeModule.PRECIP -> VistaHomeBlock.PRECIPITATION
            HomeModule.SPACETIME -> VistaHomeBlock.ATLAS
            HomeModule.DAILY -> VistaHomeBlock.DAILY
            HomeModule.TELEMETRY -> VistaHomeBlock.TELEMETRY
            HomeModule.AQI -> VistaHomeBlock.AQI
            HomeModule.INDICES -> VistaHomeBlock.INDICES
            HomeModule.YESTERDAY -> VistaHomeBlock.YESTERDAY
            HomeModule.TYPHOON -> VistaHomeBlock.TYPHOON
        }
    } ?: VistaHomeBlock.entries
    return (listOf(VistaHomeBlock.METADATA, VistaHomeBlock.HERO, VistaHomeBlock.ALERTS) +
        ordered + VistaHomeBlock.entries).distinct().filter(available::contains)
}

private sealed interface HomeContentSnapshot {
    data object Empty : HomeContentSnapshot
    data object Loading : HomeContentSnapshot
    data class Error(val message: String) : HomeContentSnapshot
    data class Data(
        val weather: WeatherData,
        val sceneWeather: com.zhisheng.weather.model.SceneWeatherData?,
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
    ambienceActive: Boolean = true,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRadarClick: () -> Unit,
    onDailyForecastClick: () -> Unit,
    onPrecipitationClick: () -> Unit = {},
    onHourlyClick: () -> Unit = {},
    onTyphoonClick: () -> Unit,
    dismissCitiesRequest: Int = 0,
) {
    val uiState by viewModel.uiState.collectAsState()
    // A transient city drawer must not be restored by the portrait SaveableStateProvider.
    // Reset on the configuration frame, before the outgoing orientation can flash an open drawer.
    val drawerOrientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    val drawerState = remember(drawerOrientation) { androidx.compose.material3.DrawerState(DrawerValue.Closed) }
    LaunchedEffect(dismissCitiesRequest) {
        if (dismissCitiesRequest > 0) drawerState.snapTo(DrawerValue.Closed)
    }
    val scope = rememberCoroutineScope()
    val chrome = LocalZhishengChrome.current
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
    var scrollToTopRequest by remember { mutableIntStateOf(0) }
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
    // Keep the last sky while the next city's weather is being fetched.
    var lastAtmosphereWeather by remember { mutableStateOf(uiState.weather) }
    androidx.compose.runtime.SideEffect {
        if (uiState.weather != null) lastAtmosphereWeather = uiState.weather
    }
    val atmosphereWeather = uiState.weather ?: lastAtmosphereWeather
    val epochMinute = weatherPresentationTime() / 60_000L
    val sceneLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(sceneLifecycleOwner) {
        sceneLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshSceneIfNeeded()
                delay(60_000)
            }
        }
    }
    val nowMinutes = atmosphereWeather?.utcOffsetSeconds?.let { offset ->
        Math.floorMod(epochMinute + offset / 60L, 24L * 60L).toInt()
    } ?: java.time.LocalTime.now().run { hour * 60 + minute }
    val todayAstro = atmosphereWeather?.todayDaily(epochMinute * 60_000L)
    val night = isNightAt(todayAstro?.sunrise, todayAstro?.sunset, nowMinutes)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CityDrawer(
                uiState = uiState,
                active = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open,
                onBack = { scope.launch { drawerState.close() } },
                onSelect = { key ->
                    viewModel.selectCity(key)
                    scope.launch { drawerState.close() }
                },
                onToggleFavorite = viewModel::toggleCityFavorite,
                onRemove = viewModel::removeCity,
                onLocate = viewModel::locateCurrentCity,
                onClearLocateMessage = viewModel::clearLocateMessage,
                onAddCity = {
                    // Keep the city page in place beneath search; Back returns to this list.
                    onSearchClick()
                },
            )
        },
    ) {
        BackHandler(enabled = ambienceActive && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
        // 氛围层视差：列表滚动越深，天空/云层越往上退（按城市列表取最大滚动深度）
        val ambienceParallax = remember { mutableFloatStateOf(0f) }
        val floatingGlassHeader = isPhosphorVista &&
            uiState.prefs.homeSurfaceStyle == HomeSurfaceStyle.FRAGRANCE_GLASS
        val scrollBackdrop = rememberGraphicsLayer()
        var scrollBackdropOrigin by remember { mutableStateOf(Offset.Zero) }
        val activeWeatherList = remember { mutableStateOf<LazyListState?>(null) }
        LaunchedEffect(uiState.selectedCity?.locationKey) { activeWeatherList.value = null }
        var topBarHeightPx by remember { mutableIntStateOf(0) }
        val topBarInset = if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() }
            else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        NaturalWeatherSurface(atmosphereWeather, uiState.prefs.ambience, night,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (drawerState.isOpen && drawerState.targetValue == DrawerValue.Open) 0f else 1f
            }, active = ambienceActive && !drawerState.isOpen && drawerState.targetValue != DrawerValue.Open,
            parallax = { ambienceParallax.floatValue }, city = uiState.selectedCity) {
            val skyBackdrop = LocalHomeBackdrop.current
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                PullToRefreshBox(
                    isRefreshing = uiState.loading || uiState.locating,
                    onRefresh = { viewModel.refreshFromUser() },
                    modifier = Modifier.widthIn(max = chrome.contentMaxWidth).fillMaxSize()
                        .then(if (floatingGlassHeader) Modifier else Modifier.padding(top = topBarInset))
                        .navigationBarsPadding()
                        .then(if (floatingGlassHeader) Modifier
                            .onGloballyPositioned { scrollBackdropOrigin = it.positionInRoot() }
                            .drawWithContent {
                                scrollBackdrop.record { this@drawWithContent.drawContent() }
                                drawLayer(scrollBackdrop)
                            } else Modifier),
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
                                    sceneWeather = uiState.sceneWeather,
                                    city = uiState.selectedCity,
                                    staleAgeMillis = uiState.staleAgeMillis,
                                )
                                // 会话级转场快照加上限：超限淘汰最早写入的城市，防止长会话内存无限增长
                                //（快照仅用于 Crossfade 退出帧，重新进入会重算）
                                while (cityContentSnapshots.size > 12) {
                                    cityContentSnapshots.remove(cityContentSnapshots.keys.first())
                                }
                                HomeContentKey.Data(cityKey)
                            }
                            else -> HomeContentKey.Loading
                        }
                        // Crossfade 的退出帧必须持有上一城市的完整快照。若在 lambda 内继续读取
                        // uiState.weather，selectCity() 清空天气后旧 "data" 帧仍会组合并触发 NPE。
                        var displayedContentKey by remember { mutableStateOf(contentKey) }
                        val contentOpacity = remember { Animatable(1f) }
                        LaunchedEffect(contentKey) {
                            if (displayedContentKey != contentKey) {
                                contentOpacity.animateTo(0f, tween(90))
                                displayedContentKey = contentKey
                            }
                            contentOpacity.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                        }
                        // A single content tree: outgoing/incoming temperature glyphs never overlap.
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentOpacity.value }) {
                            val page = displayedContentKey
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
                                        BootState()
                                    } else androidx.compose.runtime.key(page.cityKey) {
                                        val weatherListState = rememberLazyListState()
                                        LaunchedEffect(scrollToTopRequest) {
                                            if (scrollToTopRequest > 0 && page.cityKey == uiState.selectedCity?.locationKey) {
                                                weatherListState.animateScrollToItem(0)
                                            }
                                        }
                                        val scrolling = weatherListState.isScrollInProgress
                                        LaunchedEffect(page.cityKey, scrolling) {
                                            weatherContentScrolling = scrolling
                                        }
                                        LaunchedEffect(weatherListState, page.cityKey) {
                                            activeWeatherList.value = weatherListState
                                        }
                                        // 氛围层视差数据源：滚动越深天空退得越远（只采样，不重组氛围层）
                                        LaunchedEffect(weatherListState, page.cityKey == uiState.selectedCity?.locationKey) {
                                            if (page.cityKey == uiState.selectedCity?.locationKey)
                                                com.zhisheng.weather.ui.components.observeNaturalScroll(weatherListState) {
                                                    ambienceParallax.floatValue = it
                                                }
                                        }
                                        WeatherContent(
                                            data = snapshot.weather,
                                            sceneWeather = snapshot.sceneWeather,
                                            city = snapshot.city,
                                            unit = uiState.tempUnit,
                                            showTyphoon = uiState.showTyphoon,
                                            prefs = uiState.prefs,
                                            staleAgeMillis = snapshot.staleAgeMillis,
                                            listState = weatherListState,
                                            topInset = if (floatingGlassHeader) topBarInset else 0.dp,
                                            onHistoryClick = onHistoryClick,
                                            onRadarClick = onRadarClick,
                                            onDailyForecastClick = onDailyForecastClick,
                                            onPrecipitationClick = onPrecipitationClick,
                                            onHourlyClick = onHourlyClick,
                                            onTyphoonClick = onTyphoonClick,
                                        )
                                    }
                                }
                                HomeContentKey.Loading -> BootState()
                            }
                        }
                    }
                }
                TopBar(
                    cityName = uiState.selectedCity?.displayName ?: "枳生天气",
                    loading = uiState.loading || uiState.locating,
                    onMenu = { scope.launch { drawerState.open() } },
                    onRefresh = { viewModel.refreshFromUser() },
                    onSettings = onSettingsClick,
                    modifier = Modifier.widthIn(max = chrome.contentMaxWidth).fillMaxWidth()
                        .onSizeChanged { topBarHeightPx = it.height }
                        .then(if (floatingGlassHeader) Modifier.glassScrollHeader(
                            backdrop = scrollBackdrop,
                            backdropOrigin = scrollBackdropOrigin,
                            underlay = skyBackdrop?.layer,
                            underlayOrigin = skyBackdrop?.origin ?: Offset.Zero,
                            strength = {
                                val list = activeWeatherList.value
                                if (list == null) 0f else {
                                    val index = list.firstVisibleItemIndex
                                    val offset = list.firstVisibleItemScrollOffset
                                    if (index > 0) 1f else (offset / with(density) { 48.dp.toPx() })
                                        .coerceIn(0f, 1f)
                                }
                            },
                        ) else Modifier),
                )
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
                // 长按成立后立即显现；左右滑动并松手即可切换，向上推则锁定卡组。
                active = cityDeckVisible && !cityDeckPinned,
                scrolling = weatherContentScrolling && !cityDeckVisible,
                enabled = uiState.weather != null || uiState.cities.size > 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .pointerInput(uiState.selectedCity?.locationKey) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()
                            }
                            if (up != null && !cityDeckVisible) scrollToTopRequest++
                        }
                    }
                    .semantics {
                        onClick(label = "回到顶部") {
                            scrollToTopRequest++
                            true
                        }
                    }
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
                                // detectDragGesturesAfterLongPress 已经确认长按成立；此时才亮出
                                // 卡组，所以普通的主页横向滑动仍不会触发城市切换。
                                cityDeckVisible = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
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
                                        cityDeckVisible = true
                                        cityDeckPinned = true
                                        cityDeckExpansion = 1f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDragEnd = {
                                cityDeckPosition = cityDeckPosition.roundToInt().toFloat()
                                if (!pinnedThisGesture) {
                                    val targetKey = uiState.cities
                                        .getOrNull(cityDeckPosition.roundToInt())
                                        ?.locationKey
                                    cityDeckVisible = false
                                    cityDeckPinned = false
                                    cityDeckExpansion = 0f
                                    if (targetKey != null && targetKey != uiState.selectedCity?.locationKey) {
                                        viewModel.selectCity(targetKey)
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
    referenceTimeMillis: Long? = null,
    livingSkyOverride: Boolean? = null,
    header: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWeatherPreviewTime provides referenceTimeMillis) {
    val previewScroll = remember { mutableFloatStateOf(0f) }
    NaturalWeatherSurface(data, prefs.ambience, night, Modifier.fillMaxSize(), parallax = { previewScroll.floatValue },
        city = city, livingSkyOverride = livingSkyOverride) {
        Column(Modifier.fillMaxSize()) {
            header()
            Box(Modifier.weight(1f)) {
                androidx.compose.runtime.key(data.current?.condition, data.current?.profile?.intensity) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState) {
                        com.zhisheng.weather.ui.components.observeNaturalScroll(listState) { previewScroll.floatValue = it }
                    }
                    WeatherContent(
                        data = data,
                        sceneWeather = null,
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
}

@Composable
internal fun CityTouchSensor(
    active: Boolean,
    scrolling: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    val visibility = remember { Animatable(if (enabled && (active || scrolling)) 1f else 0f) }
    LaunchedEffect(active, scrolling, enabled) {
        if (enabled && (active || scrolling)) visibility.animateTo(1f, tween(180))
        else {
            if (enabled) delay(1_000)
            visibility.animateTo(0f, tween(460))
        }
    }
    val depth by animateFloatAsState(if (active) 1.08f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 420f), label = "city-hold-depth")
    Box(
        modifier.zIndex(30f).width(92.dp).height(48.dp)
            .graphicsLayer {
                scaleX = depth; scaleY = 1f / depth
                alpha = if (enabled) visibility.value else 0f
                translationY = (1f - visibility.value) * 5.dp.toPx()
            }
            .semantics {
                contentDescription = if (enabled) "轻点回到顶部；长按后左右滑动切换城市，向上推展开城市卡组"
                    else "当前没有可切换城市"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(30.dp).width(68.dp).clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(
                    palette.surface.copy(alpha = 0.94f), palette.surface.copy(alpha = 0.76f))))
                .vistaSoftGlow(panel = true)
                .border(0.75.dp, Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = if (palette.isLight) 0.96f else 0.32f),
                    palette.mint.copy(alpha = 0.20f),
                    palette.cardBorder.copy(alpha = 0.42f))), RoundedCornerShape(50))
                .drawBehind {
                    drawLine(palette.mint.copy(alpha = if (active) 0.7f else 0.28f),
                        Offset(size.width * 0.40f, size.height - 4.dp.toPx()),
                        Offset(size.width * 0.60f, size.height - 4.dp.toPx()),
                        1.dp.toPx(), StrokeCap.Round)
                }.padding(bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("长按", color = palette.textSecondary, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun CityDeckOverlay(
    visible: Boolean,
    pinned: Boolean,
    cities: List<com.zhisheng.weather.model.City>,
    position: Float,
    expansion: Float,
    onPinnedDrag: (Float) -> Unit,
    onPinnedDragEnd: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    outline: @Composable (com.zhisheng.weather.model.City, Modifier) -> Unit = { city, modifier ->
        CityOutlineMap(city.name, city.affiliation, city.latitude, city.longitude, modifier)
    },
) {
    val vista = isPhosphorVista
    val expanded by animateFloatAsState(
        targetValue = if (pinned) 1f else expansion,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "city-deck-expansion",
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f), animationSpec = tween(220)),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f), animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(20f),
    ) {
        val preview = androidx.compose.ui.platform.LocalInspectionMode.current
        var dealt by remember { mutableStateOf(preview) }
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
                .background(ZhishengBg.copy(alpha = if (vista) 1f else 0.94f))
                .vistaSoftGlow()
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
                if (vista) "城市切换" else "CITY DECK // 城市切换",
                style = MaterialTheme.typography.titleMedium,
                color = if (vista) ZhishengText else ZhishengOrange,
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
                    if (vista) "关闭" else "[ 关闭卡组 ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengCyan,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = "关闭城市卡组", onClick = onDismiss)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
                        val fanRotation = if (vista) 2.4f - 2f * expanded else 6.2f - 5f * expanded
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
                        val cardShape = RoundedCornerShape(if (vista) 22.dp else 18.dp)
                        val cardGlow = if (focused) ZhishengCyan else ZhishengMint
                        val cardAlpha = (1f - distance * 0.14f).coerceAtLeast(0.38f)
                        val tiltY = relative.coerceIn(-2f, 2f) * (-4f + 3f * expanded)
                        val borderWidth = if (focused && !vista) 2.dp else 1.dp
                        val borderColor = if (vista) {
                            if (focused) ZhishengCyan.copy(alpha = 0.35f) else ZhishengCardBorder.copy(alpha = 0.45f)
                        } else if (focused) ZhishengCyan else ZhishengCardBorder
                        val innerShape = RoundedCornerShape((if (vista) 22.dp else 18.dp) - borderWidth)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(10f - distance)
                                .width(236.dp)
                                .height(if (vista) 350.dp else 306.dp),
                        ) {
                            if (focused && !vista) {
                                // 放大后的实心圆角层当光晕：旋转时跟卡片同一套 outline clip，避免细线边框阶梯锯齿。
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            cityCardLayer(
                                                x = x,
                                                rotation = rotation,
                                                scaleXValue = scale * 1.045f,
                                                scaleYValue = scale * 1.034f,
                                                alphaValue = edgeGlowPulse * cardAlpha,
                                                tiltY = tiltY,
                                                shape = cardShape,
                                            )
                                        }
                                        .background(ZhishengCyan.copy(alpha = 0.09f), cardShape),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            cityCardLayer(
                                                x = x,
                                                rotation = rotation,
                                                scaleXValue = scale * 1.022f,
                                                scaleYValue = scale * 1.017f,
                                                alphaValue = edgeGlowPulse * cardAlpha,
                                                tiltY = tiltY,
                                                shape = cardShape,
                                            )
                                        }
                                        .background(ZhishengCyan.copy(alpha = 0.16f), cardShape),
                                )
                            }
                            Column(
                                modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cityCardLayer(
                                        x = x,
                                        rotation = rotation,
                                        scaleXValue = scale,
                                        scaleYValue = scale,
                                        alphaValue = cardAlpha,
                                        tiltY = tiltY,
                                        shape = cardShape,
                                    )
                                    shadowElevation = (if (vista) { if (focused) 8.dp else 2.dp } else if (focused) 24.dp else 8.dp).toPx()
                                    ambientShadowColor = cardGlow.copy(alpha = if (vista) 0.08f else if (focused) 0.30f else 0.08f)
                                    spotShadowColor = cardGlow.copy(alpha = if (vista) 0.06f else if (focused) 0.22f else 0.05f)
                                }
                                .background(borderColor)
                                .padding(borderWidth)
                                .clip(innerShape)
                                .background(ZhishengSurface)
                                .vistaSoftGlow(panel = true)
                                .clickable(
                                    enabled = pinned,
                                    role = Role.Button,
                                    onClickLabel = "切换到${city.displayName}",
                                ) { onSelect(city.locationKey) }
                                .padding(18.dp),
                            ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (vista) "城市 %02d".format(index + 1) else "CARD %02d".format(index + 1),
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
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                outline(city, Modifier.fillMaxSize().padding(4.dp))
                            }
                            Text(
                                city.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (vista) ZhishengText else if (focused) ZhishengMint else ZhishengText,
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
                                    vista && pinned && focused -> "点按切换"
                                    vista && pinned -> "可选择"
                                    vista && focused -> "松手切换"
                                    vista -> "待选"
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
                "地图功能由 PickGear 贡献",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
// 雷达页同样使用本层，保持整机同一台「屏幕」的观感（v0.1.5）
@Composable
internal fun Scanlines() {
    if (isPhosphorVista) return
    if (!com.zhisheng.weather.data.ReleaseFeatures.weatherAtmosphere) return
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
internal fun TopBar(
    cityName: String,
    loading: Boolean,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .then(if (isPhosphorVista) Modifier.heightIn(min = 64.dp) else Modifier.height(56.dp))
            .padding(horizontal = if (isPhosphorVista) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier.size(48.dp),
        ) {
            if (isPhosphorVista) PhosphorIcon(R.drawable.ph_list, uiText("城市列表"), Modifier.size(22.dp), ZhishengTextSecondary)
            else Icon(Icons.Filled.Menu, contentDescription = uiText("城市列表"), tint = ZhishengTextSecondary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)
            .then(if (isPhosphorVista) Modifier.padding(vertical = 8.dp) else Modifier)) {
            val (title, address) = if (isPhosphorVista) vistaLocationHeading(cityName) else cityName to ""
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPhosphorVista) ZhishengText else ZhishengOrange,
                fontWeight = FontWeight.Bold,
                maxLines = if (isPhosphorVista) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (address.isNotEmpty()) Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = ZhishengTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!isPhosphorVista) Text(
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
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(48.dp),
        ) {
            if (isPhosphorVista) PhosphorIcon(
                R.drawable.ph_arrow_clockwise, if (loading) "正在刷新" else "刷新",
                Modifier.size(20.dp).rotate(if (loading) angle else 0f),
                if (loading) ZhishengMint else ZhishengTextSecondary,
            ) else Icon(
                Icons.Filled.Refresh,
                contentDescription = if (loading) "正在刷新" else "刷新",
                tint = if (loading) ZhishengMint else ZhishengOrange,
                modifier = Modifier.size(20.dp).rotate(if (loading) angle else 0f),
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(48.dp),
        ) {
            if (isPhosphorVista) PhosphorIcon(R.drawable.ph_gear, uiText("设置"), Modifier.size(20.dp), ZhishengTextSecondary)
            else Icon(Icons.Filled.Settings, contentDescription = uiText("设置"), tint = ZhishengTextSecondary, modifier = Modifier.size(20.dp))
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
    if (isPhosphorVista) {
        content(Modifier)
        return
    }
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
    sceneWeather: com.zhisheng.weather.model.SceneWeatherData?,
    city: com.zhisheng.weather.model.City?,
    unit: String,
    showTyphoon: Boolean,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    staleAgeMillis: Long?,
    listState: LazyListState,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    onHistoryClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onDailyForecastClick: () -> Unit = {},
    onPrecipitationClick: () -> Unit = {},
    onHourlyClick: () -> Unit = {},
    onTyphoonClick: () -> Unit = {},
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

    val presentationTime = weatherPresentationTime()
    val currentDaily = data.currentAndFutureDaily(presentationTime)
    val todayDaily = data.todayDaily(presentationTime)
    val showHourly = data.hourly.isNotEmpty()
    val showPrecip = prefs.showPrecip && (Nowcast.shouldShowPrecipModule(data, weatherPresentationTime()) ||
        data.rainMinutes.isNotEmpty() || data.rainHistory.isNotEmpty() || !data.rainNowcast.isNullOrBlank() || data.rainDistanceKm != null)
    val showDaily = currentDaily.isNotEmpty()
    val showTele = prefs.showTelemetry && data.current?.let { current ->
        prefs.telemetryMetrics.any { metric -> telemetryMetricAvailable(metric, current, todayDaily) }
    } == true
    val showAqi = prefs.showAqi && data.aqi != null
    val showIndices = prefs.showIndices && lifeIndexItems(data, prefs.lifeIndexMetrics).isNotEmpty()
    val showYesterday = prefs.showYesterday && data.yesterday != null
    // 台风路径使用独立国内权威数据源，不能再被当前天气供应商是否附带
    // typhoon 字段决定入口是否出现。用户关闭模块时才隐藏。
    val showTy = showTyphoon && com.zhisheng.weather.data.ReleaseFeatures.typhoon
    val showAtlas = if (isPhosphorVista) prefs.showSpacetime || vistaScenePages(sceneWeather, prefs).isNotEmpty()
    else prefs.showSpacetime || prefs.showSkyPhotography || prefs.showCoastWeather
    val vista = isPhosphorVista
    val vistaBlocks = vistaHomeBlocks(buildSet {
        add(VistaHomeBlock.METADATA)
        if (data.current != null) add(VistaHomeBlock.HERO)
        if (vistaVisibleAlerts(data.alerts).isNotEmpty()) add(VistaHomeBlock.ALERTS)
        if (showHourly) add(VistaHomeBlock.HOURLY)
        if (showDaily) add(VistaHomeBlock.DAILY)
        if (showPrecip) add(VistaHomeBlock.PRECIPITATION)
        if (showTele) add(VistaHomeBlock.TELEMETRY)
        if (showAqi) add(VistaHomeBlock.AQI)
        if (showTy) add(VistaHomeBlock.TYPHOON)
        if (showAtlas) add(VistaHomeBlock.ATLAS)
        if (showIndices) add(VistaHomeBlock.INDICES)
        if (showYesterday) add(VistaHomeBlock.YESTERDAY)
    }, prefs.moduleOrder)

    CompositionLocalProvider(LocalHomeSurfaceStyle provides prefs.homeSurfaceStyle) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = 40.dp),
    ) {
        fun LazyListScope.addHomeModule(module: HomeModule, compactLeading: Boolean = false) {
            val animationIndex = nextStagger()
            val n = nextIndex()
            item(key = "title_${module.key}") {
                if (module == HomeModule.SPACETIME) AtlasSectionHeading(n) else SectionTitle(
                    index = n,
                    title = module.cn,
                    en = module.en,
                    prominent = module.isPrimaryHomeModule(),
                    compactLeading = compactLeading,
                )
            }
            item(key = "module_${module.key}") {
                com.zhisheng.weather.ui.components.RainLandingTarget(vista && n == 1) {
                Stagger(animationIndex, entered) { m ->
                    when (module) {
                        HomeModule.HOURLY -> HourlySection(data, unit, prefs.windUnit, data.utcOffsetSeconds, m, onHourlyClick)
                        HomeModule.PRECIP -> PrecipCard(data, m, onPrecipitationClick)
                        HomeModule.SPACETIME -> WeatherAtlas(
                            weather = data,
                            scene = sceneWeather,
                            city = city,
                            unit = unit,
                            prefs = prefs,
                            modifier = m,
                            onHistoryClick = onHistoryClick,
                            onRadarClick = onRadarClick,
                        )
                        HomeModule.DAILY -> DailySection(
                            currentDaily,
                            unit,
                            prefs.windUnit,
                            data.utcOffsetSeconds,
                            m,
                            onDailyForecastClick,
                        )
                        HomeModule.TELEMETRY -> data.current?.let { TelemetryGrid(it, todayDaily, unit, prefs, m, city, data.utcOffsetSeconds) }
                        HomeModule.AQI -> data.aqi?.let { AqiCard(it, m) }
                        HomeModule.INDICES -> IndicesRow(data, prefs.lifeIndexMetrics, m, unit)
                        HomeModule.YESTERDAY -> data.yesterday?.let { YesterdayCard(it, todayDaily, unit, prefs.windUnit, m) }
                        HomeModule.TYPHOON -> TyphoonCard(data.typhoons, m, onTyphoonClick)
                    }
                }
                }
            }
        }

        if (vista) {
            var compactAfterHero = vistaVisibleAlerts(data.alerts).isEmpty()
            fun emitModule(module: HomeModule) {
                addHomeModule(module, compactLeading = compactAfterHero)
                compactAfterHero = false
            }
            vistaBlocks.forEach { block ->
                when (block) {
                    VistaHomeBlock.METADATA -> item { StatusLine(city, data, staleAgeMillis) }
                    VistaHomeBlock.HERO -> data.current?.let { cur ->
                        item { Stagger(nextStagger(), entered) { m -> HeroSection(cur, data, unit, prefs, m) } }
                    }
                    VistaHomeBlock.ALERTS -> {
                        compactAfterHero = false
                        item {
                            Stagger(nextStagger(), entered) { m -> AlertSection(vistaVisibleAlerts(data.alerts).take(3), m) }
                        }
                    }
                    VistaHomeBlock.HOURLY -> emitModule(HomeModule.HOURLY)
                    VistaHomeBlock.DAILY -> emitModule(HomeModule.DAILY)
                    VistaHomeBlock.PRECIPITATION -> emitModule(HomeModule.PRECIP)
                    VistaHomeBlock.TELEMETRY -> emitModule(HomeModule.TELEMETRY)
                    VistaHomeBlock.AQI -> emitModule(HomeModule.AQI)
                    VistaHomeBlock.TYPHOON -> emitModule(HomeModule.TYPHOON)
                    VistaHomeBlock.ATLAS -> emitModule(HomeModule.SPACETIME)
                    VistaHomeBlock.INDICES -> emitModule(HomeModule.INDICES)
                    VistaHomeBlock.YESTERDAY -> emitModule(HomeModule.YESTERDAY)
                }
            }
        } else {
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
                    HomeModule.SPACETIME -> showAtlas
                    HomeModule.DAILY -> showDaily
                    HomeModule.TELEMETRY -> showTele
                    HomeModule.AQI -> showAqi
                    HomeModule.INDICES -> showIndices
                    HomeModule.YESTERDAY -> showYesterday
                    HomeModule.TYPHOON -> showTy
                }
                if (visible) addHomeModule(module)
            }
        }
        item { Stagger(nextStagger(), entered) { m -> Footer(data, m) } }
    }
    }
}

/** 核心预报保留完整章节力度，辅助资料降低一级，避免九个模块同时抢视线。 */
private fun HomeModule.isPrimaryHomeModule(): Boolean = when (this) {
    HomeModule.HOURLY,
    HomeModule.PRECIP,
    HomeModule.DAILY,
    HomeModule.SPACETIME
    -> true
    HomeModule.TELEMETRY,
    HomeModule.AQI,
    HomeModule.INDICES,
    HomeModule.YESTERDAY,
    HomeModule.TYPHOON
    -> false
}

// —— 状态行：坐标 / 更新时间 / 数据源 ——
@Composable
private fun StatusLine(city: com.zhisheng.weather.model.City?, data: WeatherData, staleAgeMillis: Long?) {
    val coord = city?.let { Fmt.coordinates(it.latitude, it.longitude, precise = it.isPreciseLocation) } ?: "----"
    val updateStamp = homeWeatherUpdateStamp(data)
    // 离线缓存兜底时标注缓存年龄（<10 分钟不打扰，只给正常更新时间）
    val updText = if (staleAgeMillis != null && staleAgeMillis >= 10 * 60_000L) {
        "UPD ${staleAgeMillis / 60_000L}分钟前 · 缓存"
    } else {
        "${updateStamp?.code ?: "UPD"} ${updateStamp?.let { Fmt.stamp(it.timeMillis, data.utcOffsetSeconds) } ?: "--"}"
    }
    val gpsText = if (data.locationMatch?.preciseGps == true) " · GPS" else ""
    val sourceTimeText = if (data.fetchedAt != null) data.updateTime?.let {
        " · DATA ${Fmt.stamp(it, data.utcOffsetSeconds)}"
    }.orEmpty() else ""
    val srcText = "SRC ${dataSourceShortLabel(data.dataSource)}${supplementShortLabel(data)}$gpsText$sourceTimeText"
    if (isPhosphorVista) {
        val now = weatherPresentationTime()
        val time = updateStamp?.let { vistaUpdateTime(it.timeMillis, now, data.utcOffsetSeconds) }
        val updateLabel = buildString {
            if (time != null) append("${updateStamp.label} $time")
            if (staleAgeMillis != null && staleAgeMillis >= 10 * 60_000L) {
                if (isNotEmpty()) append(" · ")
                append("${staleAgeMillis / 60_000L} 分钟前保存")
            }
        }
        val locationLabel = if (data.locationMatch?.preciseGps == true) "GPS" else "城市位置"
        var details by rememberSaveable(city?.locationKey) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .vistaClick(if (details) "收起数据来源" else "查看数据来源与位置") { details = !details },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(updateLabel.ifEmpty { "更新时间暂缺" }, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (staleAgeMillis != null && staleAgeMillis >= 10 * 60_000L) ZhishengOrange else ZhishengTextTertiary)
                Text("数据来源", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                val angle by animateFloatAsState(if (details) 90f else 0f, tween(200), label = "source-chevron")
                PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(12.dp).rotate(angle), ZhishengTextTertiary)
            }
            AnimatedVisibility(details, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${dataSourceShortLabel(data.dataSource)}${supplementShortLabel(data)}",
                        style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    if (data.fetchedAt != null) data.updateTime?.let {
                        Text("数据发布于 ${vistaUpdateTime(it, now, data.utcOffsetSeconds)}",
                            style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                    }
                    Text("$coord · $locationLabel", modifier = Modifier.semantics {
                        contentDescription = "经纬度 $coord，" + if (data.locationMatch?.preciseGps == true) "GPS 精确定位" else "城市位置"
                    },
                        style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
                }
            }
        }
        return
    }
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
    val nowMillis = weatherPresentationTime()
    val showVistaAlerts = isPhosphorVista && vistaVisibleAlerts(data.alerts).isNotEmpty()
    val heroModifier = if (isPhosphorVista) {
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = if (showVistaAlerts) 4.dp else 8.dp)
    } else {
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
    }
    // The primary reading belongs to the sky, while supporting modules use glass.
    // A full-height hero card makes the first screen feel boxed in and adds dead space.
    Column(modifier = heroModifier) {
        if (isPhosphorVista) VistaCurrentOverview(cur, data, unit, prefs.windUnit, prefs.showAqi)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = cur.weatherText ?: cur.condition?.label ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = ZhishengOrange,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.Top) {
                    AnimatedTemp(cur.temperature, unit)
                    // 只改主屏大温度这一处单位：° → ℃（华氏模式对应 °F），
                    // 其余模块的度数表达保持原样不动。
                    Text(
                        text = if (unit == "f") "°F" else "℃",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.headlineLarge,
                        color = ZhishengOrange,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                val range = HeroTemps.range(
                    data.daily,
                    data.yesterday,
                    nowMillis,
                    Fmt.zoneId(data.utcOffsetSeconds),
                    hourly = data.hourly,
                )
                val temperatureFacts = buildList {
                        if (HeroTemps.showFeelsLike(cur.temperature, cur.feelsLike)) {
                            add("体感${Fmt.temp(cur.feelsLike, unit)}°")
                        }
                        range.left?.let { add("${range.leftLabel}${Fmt.temp(it, unit)}°") }
                        range.right?.let { add("${range.rightLabel}${Fmt.temp(it, unit)}°") }
                        if (isEmpty()) add("—")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    temperatureFacts.forEach { fact ->
                        Text(
                            text = fact,
                            style = MaterialTheme.typography.labelMedium,
                            color = ZhishengTextSecondary,
                        )
                        }
                }
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
                if (!isPhosphorVista) Canvas(modifier = Modifier.size(116.dp)) {
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
                WeatherIcon(
                    phaseAwareCondition(cur.condition, data, nowMillis),
                    Modifier.size(if (isPhosphorVista) 88.dp else 76.dp),
                )
            }
        }
        Nowcast.briefing(data, unit, nowMillis)?.let { briefing ->
            val briefingText = com.zhisheng.weather.i18n.weatherBriefingText(
                briefing.text, com.zhisheng.weather.i18n.LocalAppLanguage.current,
            )
            val copy = briefingCopy(briefingText)
            val copyColor = briefingColor(briefing)
            if (isPhosphorVista) {
                if (prefs.homeBriefingStyle != HomeBriefingStyle.OFF) {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = if (showVistaAlerts) 8.dp else 0.dp)
                        .padding(top = 3.dp, bottom = 3.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${uiText("天气播报")}：$briefingText" },
                        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (prefs.homeBriefingStyle == HomeBriefingStyle.WEATHER_GIRL) {
                            Image(painterResource(briefingEmoteRes(briefing.emote)), contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(copy.lead, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ZhishengText)
                            copy.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = ZhishengTextSecondary) }
                        }
                    }
                }
            } else when (prefs.homeBriefingStyle) {
                HomeBriefingStyle.WEATHER_GIRL -> {
                    val emotePlacement = briefingEmotePlacement(briefing.emote)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .drawBehind {
                                val baselineY = size.height - 1.dp.toPx()
                                drawLine(
                                    color = copyColor.copy(alpha = 0.18f),
                                    start = Offset(86.dp.toPx(), baselineY),
                                    end = Offset(size.width, baselineY),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${uiText("天气娘提示")}：$briefingText"
                            },
                    ) {
                        Image(
                            painter = painterResource(briefingEmoteRes(briefing.emote)),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                // 各表情 PNG 的透明边界不同：按有效轮廓校正，让发梢右缘与落地线一致。
                                .offset(x = emotePlacement.x.dp, y = emotePlacement.y.dp)
                                .size(94.dp)
                                .alpha(0.96f),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 88.dp, end = 2.dp, bottom = 9.dp)
                                .zIndex(1f),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                text = copy.lead,
                                style = MaterialTheme.typography.titleSmall,
                                color = copyColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = if (copy.detail == null) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            copy.detail?.let { detail ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = copyColor.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Medium,
                                    // 温差、防晒等建议常比标题长；保留两行，不能把关键动作截成省略号。
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                HomeBriefingStyle.TIPS -> {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .drawBehind {
                                val baselineY = size.height - 1.dp.toPx()
                                drawLine(
                                    color = copyColor.copy(alpha = 0.18f),
                                    start = Offset.Zero.copy(y = baselineY),
                                    end = Offset(size.width, baselineY),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${uiText("天气提示")}：$briefingText"
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .background(copyColor),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (isPhosphorVista) "天气提示" else "TIPS //",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextTertiary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = briefingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = copyColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 关闭时不渲染容器、分隔线或 Spacer，主界面自然衔接，不留下空白占位。
                HomeBriefingStyle.OFF -> Unit
            }
        }
    }
}

@Composable
internal fun VistaCurrentOverview(
    cur: CurrentWeather,
    data: WeatherData,
    unit: String,
    windUnit: String,
    showAqi: Boolean,
) {
    // Today's range is not HeroTemps' time-dependent left/right pair.
    val today = data.todayDaily(weatherPresentationTime())
    val temperature = Fmt.temp(cur.temperature, unit) ?: "—"
    val low = Fmt.temp(today?.low, unit)?.let { "$it°" } ?: "—"
    val high = Fmt.temp(today?.high, unit)?.let { "$it°" } ?: "—"
    val condition = phaseAwareCondition(cur.condition, data, weatherPresentationTime())
    val hasArtwork = condition != null && condition != WeatherCondition.UNKNOWN
    val weatherLabel = cur.weatherText ?: cur.condition?.label ?: "天气暂缺"
    val palette = LocalZhishengPalette.current
    val fragranceGlass = LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val stacked = hasArtwork && (fontScale > 1.3f || maxWidth < 300.dp)
        val artSize = if (stacked) 100.dp else minOf(120.dp, maxWidth * 0.35f)
        val numberWidth = if (!hasArtwork) maxWidth.value - 20f else if (stacked) maxWidth.value - 40f * fontScale else maxWidth.value - artSize.value - 20f
        val numberSize = minOf(124f, numberWidth / ((temperature.length * 0.49f) * fontScale)).coerceAtLeast(42f)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = if (!hasArtwork) 132.dp else if (stacked) 188.dp else if (fragranceGlass) 164.dp else 204.dp)) {
                if (hasArtwork) VistaWeatherArtwork(condition,
                    Modifier.align(if (stacked) Alignment.TopEnd else Alignment.CenterEnd).offset(x = 8.dp, y = 12.dp)
                        .weatherSharedBounds("current-condition").size(artSize)
                        .drawBehind {
                            // 浅色主题白云贴灰白底不足 3:1（WCAG 1.4.11），垫一层柔影把图形托起来
                            if (palette.isLight) {
                                drawCircle(
                                    Brush.radialGradient(listOf(Color(0x26334559), Color.Transparent)),
                                    radius = size.minDimension * 0.62f, center = center,
                                )
                            }
                        }, hero = true)
                Column(Modifier.align(if (stacked) Alignment.BottomStart else Alignment.CenterStart).padding(top = if (stacked) 108.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(temperature, modifier = Modifier.weatherSharedBounds("current-temperature"),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = numberSize.sp,
                                lineHeight = numberSize.sp, fontWeight = FontWeight.Light, letterSpacing = (-5).sp),
                            color = ZhishengText, maxLines = 1)
                        Text(if (unit == "f") "°F" else "℃", Modifier.padding(top = 12.dp, start = 2.dp),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), color = ZhishengText)
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(end = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(weatherLabel, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium), color = ZhishengText)
                    Fmt.temp(cur.feelsLike, unit)?.let {
                        Text("体感 $it°", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.End) {
                    Text("$high / $low", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = ZhishengText)
                    Text("最高 / 最低", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                windLabel(cur, windUnit)?.let { VistaObservationLabel(R.drawable.ph_wind, it) }
                if (showAqi && data.aqi?.value != null) VistaObservationLabel(R.drawable.ph_leaf,
                    "空气质量 ${data.aqi?.value}", aqiColor(data.aqi?.value))
            }
        }
    }
}

@Composable
private fun VistaObservationLabel(icon: Int, label: String, tint: Color = ZhishengTextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PhosphorIcon(icon, null, Modifier.size(14.dp), tint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint)
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

private data class BriefingEmotePlacement(val x: Int, val y: Int)

// 256px 原图的有效 alpha 边界并不等宽；这里统一到约 80dp 的视觉右缘、78dp 的视觉底缘。
private fun briefingEmotePlacement(emote: BriefingEmote): BriefingEmotePlacement = when (emote) {
    BriefingEmote.SUNNY -> BriefingEmotePlacement(x = -10, y = 0)
    BriefingEmote.CLOUDY -> BriefingEmotePlacement(x = -6, y = 0)
    BriefingEmote.RAIN -> BriefingEmotePlacement(x = 0, y = 0)
    BriefingEmote.HOT -> BriefingEmotePlacement(x = 6, y = 0)
    BriefingEmote.COLD -> BriefingEmotePlacement(x = -10, y = 3)
    BriefingEmote.WIND -> BriefingEmotePlacement(x = -5, y = 3)
    BriefingEmote.NIGHT -> BriefingEmotePlacement(x = 3, y = 3)
    BriefingEmote.ALERT -> BriefingEmotePlacement(x = 8, y = 3)
}

internal data class BriefingCopy(val lead: String, val detail: String?)

internal fun briefingCopy(text: String): BriefingCopy {
    // 是否分行首先由自然标点决定，而不是只按总字数决定。18 字在多数手机上仍放不下，
    // 旧逻辑会把“外面有点风，骑车时会比走路更有感觉。”硬塞成一行并截尾。
    val splitAt = text.indices.firstOrNull { index ->
        index in 4..14 && text[index] in setOf('，', '：', '；', '。') && text.length - index > 4
    }
    if (splitAt == null) return BriefingCopy(text, null)
    val lead = text.substring(0, splitAt).trim().trimEnd('，', '：', '；', '。')
    val detail = text.substring(splitAt + 1).trim()
    return BriefingCopy(lead, detail.takeIf { it.isNotEmpty() })
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
    if (isPhosphorVista) {
        VistaAlerts(alerts, modifier)
        return
    }
    // 展开态按标题记忆：原来按列表位置 remember，预警条数变化时展开态会错位到别条（v0.0.2）
    val expandedTitles = remember { mutableStateListOf<String>() }
    // 单一闪烁时钟：原来每条预警各起一个 while(true)，多条预警时多个协程各自计时（v0.0.2）
    val blinkOn = rememberBlink()
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp),
    ) {
        alerts.forEachIndexed { index, alert ->
            val expanded = alert.title in expandedTitles
            // v0.0.4：三源等级归一后按国标四档着色，未识别档退回警报红
            val c = alertLevelColor(alert.severity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (index == alerts.lastIndex) 0.dp else 8.dp)
                    .zhishengCompactPanel(
                        containerColor = ZhishengCard,
                        borderColor = c.copy(alpha = 0.7f),
                    )
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
            title = "天气回看",
            subtitle = "过去7天 · 往年同日",
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
            .zhishengPanel()
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
internal fun SectionTitle(index: Int, title: String, en: String, prominent: Boolean, compactLeading: Boolean = false) {
    if (isPhosphorVista) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = LocalZhishengChrome.current.pagePadding,
                end = LocalZhishengChrome.current.pagePadding,
                top = when {
                    compactLeading -> 8.dp
                    else -> 32.dp
                },
                bottom = 16.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengText,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = 20.dp,
            top = if (prominent) 16.dp else 13.dp,
            bottom = if (prominent) 8.dp else 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d//".format(index),
            style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = if (prominent) ZhishengOrange else ZhishengOrange.copy(alpha = 0.82f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = if (prominent) ZhishengTextSecondary else ZhishengTextTertiary,
            letterSpacing = if (prominent) 1.6.sp else 1.1.sp,
        )
        Spacer(Modifier.width(if (prominent) 8.dp else 6.dp))
        Text(
            en,
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary.copy(alpha = if (prominent) 1f else 0.72f),
            letterSpacing = if (prominent) 1.4.sp else 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "─".repeat(if (prominent) 6 else 3),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengCardBorder.copy(alpha = if (prominent) 1f else 0.65f),
        )
    }
}

// —— 角括号 HUD 卡片 ——
@Composable
private fun HudCard(
    modifier: Modifier = Modifier,
    edgeContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    Box(
        modifier = modifier
            .padding(horizontal = if (isPhosphorVista) chrome.pagePadding else 16.dp)
            .then(if (isPhosphorVista) Modifier.zhishengPanel()
                else Modifier.clip(chrome.panelShape).background(if (palette.isLight) ZhishengCard else ZhishengSurface).hudBorder())
            .padding(horizontal = if (isPhosphorVista && edgeContent) 0.dp else if (isPhosphorVista) chrome.panelPadding else 14.dp,
                vertical = if (isPhosphorVista) 16.dp else 12.dp),
    ) {
        content()
    }
}

@Composable
private fun Modifier.hudBorder(): Modifier {
    val chrome = LocalZhishengChrome.current
    return this
    .border(1.dp, ZhishengCardBorder, chrome.panelShape)
    .padding(0.dp)
    .then(
        if (isPhosphorVista) Modifier else Modifier.drawCornerBrackets(ZhishengOrange)
    )
}

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

/** 统一的 Phosphor regular 图标入口；授权与来源见 THIRD_PARTY_NOTICES.md。 */
@Composable
private fun VistaModuleIcon(
    resourceId: Int,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = ZhishengMint,
) {
    PhosphorIcon(resourceId, uiText(description), modifier.size(18.dp), tint)
}

// Vista uses one shared chart; classic retains its original compact hourly columns.
@Composable
internal fun HourlySection(
    data: WeatherData,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    modifier: Modifier,
    onOpenDetail: () -> Unit = {},
) {
    val nowMs = weatherPresentationTime()
    val displayItems = hourlyDisplayItems(data.current, data.hourly, nowMs, data.aqi?.value)
    val hourly = displayItems.map { it.weather }
    val temps = hourly.mapNotNull { h -> conv(h.temperature, unit) }
    val minT = temps.minOrNull() ?: 0.0
    val maxT = temps.maxOrNull() ?: 1.0
    // sp 会随系统字体缩放，逐时格宽也必须同步放大；否则首列的 26° / 56%
    // 会从 LazyRow 视口两侧溢出后被裁掉（部分 vivo / OriginOS 设备可复现）。
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    val hourlyItemWidth = (if (isPhosphorVista) 72.dp else 54.dp) * fontScale
    // 0.0.9-debug 修复：原实现每格独立用 ±40 分钟双向容差判「现在」，
    // :20-:40 之间上一整点与下一整点同时命中，两格都标「现在」并高亮。
    // 改为在父层算唯一「现在」格：优先取包含当前时刻的小时格（10:50 属于
    // 10:00 格），找不到（该格已被 dropPastHourly 裁掉）再退回 40 分钟
    // 窗口内最近的一格；均无则不标。
    val showPrecipProbability = vistaHourlyShowsPrecip(hourly)
    val classicHourlyState = rememberLazyListState()
    val hourlyHaptic = LocalHapticFeedback.current
    LaunchedEffect(classicHourlyState, hourlyHaptic) {
        var lastHour = -1
        snapshotFlow {
            if (classicHourlyState.isScrollInProgress) classicHourlyState.firstVisibleItemIndex else -1
        }.collect { hour ->
            if (hour >= 0 && lastHour >= 0 && hour != lastHour) {
                hourlyHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHour = hour
        }
    }
    // 趋势从实时观测开始，与用户眼前的首个“现在”列对齐；没有实况时再退回逐时源。
    // 不能把已经过去的当前整点或更远时段峰值写成含混的“最高温”。
    val trendHours = displayItems.dropWhile { !it.isNow }
        .map { it.weather }
        .ifEmpty { data.hourly }
    val outlookText = hourlyTrendText(trendHours, nowMs, unit)
    HudCard(modifier = modifier.fillMaxWidth(), edgeContent = isPhosphorVista) {
        // 点卡进逐时详情：温度/空气质量/紫外线/风力四张图表（横滑与点击互不干扰）
        Column(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = "查看逐时详情", onClick = onOpenDetail)) {
            if (isPhosphorVista) {
                Text(outlookText, style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = LocalZhishengChrome.current.panelPadding))
            } else {
                Text(
                    text = outlookText,
                    style = MaterialTheme.typography.labelMedium,
                    color = ZhishengTextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(if (isPhosphorVista) 18.dp else 14.dp))
            // 经典终端保留原先的分格逐时卡片：逐格拼接曲线 + 图标/降水/风力/时间纵向排列，
            // 用户明确表示这版比共享坐标图表更适合经典终端的观感。
            if (isPhosphorVista) {
                VistaHourlyForecast(displayItems, data, unit, utcOffsetSeconds)
            } else LazyRow(
                state = classicHourlyState,
                contentPadding = PaddingValues(start = 0.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(displayItems, key = { i, item ->
                    if (item.isNow) "now-${item.weather.timeMillis}" else "hour-$i-${item.weather.timeMillis}"
                }) { i, item ->
                    val h = item.weather
                    HourlyItem(
                        h = h,
                        visualCondition = phaseAwareCondition(h.condition, data, h.timeMillis),
                        prev = hourly.getOrNull(i - 1),
                        next = hourly.getOrNull(i + 1),
                        unit = unit,
                        minT = minT,
                        maxT = maxT,
                        isNow = item.isNow,
                        showPrecipProbability = showPrecipProbability,
                        utcOffsetSeconds = utcOffsetSeconds,
                        itemWidth = if (isPhosphorVista && i == 0) 54.dp * fontScale else hourlyItemWidth,
                        extendLeft = i == 0,
                    )
                }
            }
        }
    }
}

internal data class HourlyDisplayItem(
    val weather: HourlyWeather,
    val isNow: Boolean,
)

// 和风高档套餐可返回 240 小时。完整数据继续留在 WeatherData 中供趋势和详情使用，
// 主页横向卡片只承担“今天接下来怎么变”的速览职责，不能随套餐等级无限变长。
internal const val HOME_HOURLY_ITEM_LIMIT = 24

/** 当前小时整点预报在左，实时观测紧随其后，再连接未来整点，和数据含义一一对应。 */
internal fun hourlyDisplayItems(
    current: CurrentWeather?,
    hourly: List<HourlyWeather>,
    nowMillis: Long,
    currentAqi: Int? = null,
): List<HourlyDisplayItem> {
    if (hourly.isEmpty()) {
        return current?.let {
            listOf(HourlyDisplayItem(currentAsHourly(it, null, nowMillis, currentAqi), isNow = true))
        }.orEmpty()
    }
    val sorted = hourly.distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    // Keep only a real forecast in the containing hour, never a stale/future point
    // selected by a tolerance heuristic. The observation exists independently of it.
    val anchor = sorted.lastOrNull { it.timeMillis <= nowMillis && nowMillis - it.timeMillis < 3_600_000L }
    val previous = sorted.lastOrNull { it.timeMillis < nowMillis && nowMillis - it.timeMillis <= 3_600_000L }
    val future = sorted.filter { it.timeMillis > nowMillis }
    return buildList {
        (if (current != null) previous else anchor)?.let { add(HourlyDisplayItem(it, isNow = false)) }
        current?.let { add(HourlyDisplayItem(currentAsHourly(it, anchor, nowMillis, currentAqi), isNow = true)) }
        future.forEach { add(HourlyDisplayItem(it, isNow = false)) }
    }.take(HOME_HOURLY_ITEM_LIMIT)
}

private fun currentAsHourly(
    current: CurrentWeather,
    anchor: HourlyWeather?,
    nowMillis: Long,
    currentAqi: Int?,
): HourlyWeather = HourlyWeather(
    timeMillis = nowMillis,
    temperature = current.temperature,
    condition = current.condition,
    windSpeed = current.windSpeed,
    precipProb = anchor?.precipProb,
    aqi = currentAqi,
    profile = current.profile ?: anchor?.profile,
    feelsLike = current.feelsLike,
    windDirectionDeg = current.windDirectionDeg,
    windGust = current.windGust,
    precipMm = current.precipMm,
    humidity = current.humidity,
    pressure = current.pressure,
    visibility = current.visibility,
    dewPoint = current.dewPoint,
    cloudCover = current.cloudCover,
    uvIndex = current.uvIndex,
)

/**
 * 逐时卡片只解释温度趋势；是否马上下雨由紧邻的分钟降水模块独占回答，
 * 避免两个模块连续重复“无降水”。只读取统一后的逐时温度，不根据天气图标猜结论。
 * 用可核对的起点→转折点→终点表达，不把六小时内的阶段峰值叫成“最高温”。
 */
internal fun hourlyTrendText(
    hourly: List<HourlyWeather>,
    nowMillis: Long,
    unit: String,
): String {
    val horizon = hourly
        .filter { it.timeMillis >= nowMillis - 5 * 60 * 1000L }
        .take(6)
        .mapNotNull { point -> conv(point.temperature, unit) }
    if (horizon.size < 2) return "逐小时温度与风力"

    val first = horizon.first()
    val last = horizon.last()
    val peak = horizon.maxOrNull() ?: first
    val trough = horizon.minOrNull() ?: first
    val peakIndex = horizon.indexOfFirst { it == peak }
    val troughIndex = horizon.indexOfFirst { it == trough }
    val threshold = if (unit == "f") 4.0 else 2.0
    fun degrees(value: Double): String = "${value.roundToInt()}°"

    return when {
        peakIndex in 1 until horizon.lastIndex &&
            peak - first >= threshold && peak - last >= threshold ->
            "接下来几小时先升后降 · ${degrees(first)} → ${degrees(peak)} → ${degrees(last)}"
        troughIndex in 1 until horizon.lastIndex &&
            first - trough >= threshold && last - trough >= threshold ->
            "接下来几小时先降后升 · ${degrees(first)} → ${degrees(trough)} → ${degrees(last)}"
        last - first >= threshold ->
            "接下来几小时逐渐升温 · ${degrees(first)} → ${degrees(last)}"
        first - last >= threshold ->
            "接下来几小时逐渐降温 · ${degrees(first)} → ${degrees(last)}"
        peak.roundToInt() == trough.roundToInt() ->
            "接下来几小时气温稳定 · ${degrees(first)}"
        else ->
            "接下来几小时气温平稳 · ${degrees(trough)}–${degrees(peak)}"
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
    visualCondition: WeatherCondition?,
    prev: HourlyWeather?,
    next: HourlyWeather?,
    unit: String,
    minT: Double,
    maxT: Double,
    isNow: Boolean,
    showPrecipProbability: Boolean,
    utcOffsetSeconds: Int?,
    itemWidth: androidx.compose.ui.unit.Dp,
    extendLeft: Boolean,
) {
    val vista = isPhosphorVista
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(itemWidth),
    ) {
        Text(
            text = Fmt.temp(h.temperature, unit)?.let { "$it°" } ?: "--",
            style = if (vista) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.titleSmall,
            color = ZhishengText,
        )
        Spacer(Modifier.height(2.dp))
        // 连续曲线：左半段接上一格中点，右半段接下一格中点（颜色提前取值，Canvas lambda 非 composable）
        val curveMint = ZhishengMint
        val curveCyan = ZhishengCyan
        val curveBg = ZhishengSurface
        val curveText = ZhishengText
        Canvas(modifier = Modifier.fillMaxWidth().height(if (vista) 24.dp else 34.dp)) {
            val range = (maxT - minT).coerceAtLeast(1.0).toFloat()
            val top = 5.dp.toPx()
            val usable = size.height - top * 2f
            fun yOf(v: Double?): Float? = v?.let {
                size.height - top - ((it - minT).toFloat() / range) * usable
            }

            val cx = size.width / 2f
            val yCur = yOf(conv(h.temperature, unit)) ?: return@Canvas
            val yPrev = yOf(prev?.let { conv(it.temperature, unit) })
            val yNext = yOf(next?.let { conv(it.temperature, unit) })

            // 左右邻的中点：与相邻格画出的同一点重合，所以跨格连续
            val pLeft = yPrev?.let { Offset(0f, (it + yCur) / 2f) }
                ?: if (extendLeft) Offset(0f, yCur) else null
            val pRight = yNext?.let { Offset(size.width, (it + yCur) / 2f) }
            val pCur = Offset(cx, yCur)

            // 每格使用相同的水平切线，边界点与相邻格重合；滚动时仍是一条连续的柔和曲线。
            val line = Path().apply {
                val startPoint = pLeft ?: pCur
                moveTo(startPoint.x, startPoint.y)
                if (pLeft != null) {
                    cubicTo(cx / 3f, pLeft.y + (yCur - (yPrev ?: yCur)) / 4f,
                        cx * 2f / 3f, yCur, cx, yCur)
                }
                if (pRight != null) {
                    cubicTo(cx + cx / 3f, yCur,
                        size.width - cx / 3f, pRight.y - ((yNext ?: yCur) - yCur) / 4f,
                        pRight.x, pRight.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(pRight?.x ?: cx, size.height)
                lineTo(pLeft?.x ?: cx, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(
                curveCyan.copy(alpha = 0.07f), curveCyan.copy(alpha = 0f),
            )))
            drawPath(line, curveCyan.copy(alpha = 0.72f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (isNow) {
                drawCircle(curveBg, 4.dp.toPx(), pCur)
                drawCircle(curveCyan, 2.5.dp.toPx(), pCur)
            }

        }
        // 图标与曲线之间留出一段安静的呼吸区，当前点由曲线上的光芯直接定位。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                WeatherIcon(visualCondition, Modifier.fillMaxSize())
            }
        }
        if (showPrecipProbability) {
            val precip = vistaHourlyPrecipLabel(h.precipProb)
            Text(
                text = precip,
                style = MaterialTheme.typography.labelSmall,
                color = if (vista && (h.precipProb ?: 0) <= 0) ZhishengTextTertiary else ZhishengCyan,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = hourlyWindLabel(h) ?: "--",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (isNow) "现在" else Fmt.hour(h.timeMillis, utcOffsetSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = if (isNow) ZhishengMint else ZhishengTextTertiary,
        )
    }
}

// —— 短时降水：先回答何时开始/停止，再展示原生时间粒度 ——
@Composable
internal fun PrecipCard(data: WeatherData, modifier: Modifier, onClick: () -> Unit = {}) {
    val presentation = com.zhisheng.weather.ui.rememberPrecipitationPresentation(data)
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看降水详情", onClick = onClick)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!isPhosphorVista) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("短时降水", Modifier.weight(1f), color = ZhishengText,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("查看详情", color = ZhishengCyan, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(presentation.summary,
                        color = ZhishengText,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    // 雨强读数归位到这里：与降水结论同卡、同源、同一视线；
                    // 只有拿到逐分钟数据时才展开，距离型状态保持"一行结论"
                    if (!presentation.dry && isPhosphorVista) {
                        data.current?.precipMm?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                            Text("当前雨强 ${String.format(Locale.US, "%.1f", it)} mm/h",
                                color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (isPhosphorVista) PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(18.dp), ZhishengTextTertiary)
            }
            // 主卡只保留结论、趋势和必要的来源标识，详细解释放到点击后的详情页。
            // 预报时段内全程无降水时，一条平贴底部的零值线没有信息量，整条收起。
            when {
                presentation.dry -> Unit
                presentation.points.isNotEmpty() || presentation.history.isNotEmpty() -> {
                    com.zhisheng.weather.ui.PrecipitationTimeline(presentation, data.utcOffsetSeconds)
                    Text(com.zhisheng.weather.ui.precipitationCompactSource(data, presentation),
                        color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                else -> Text("暂无降水趋势", color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DailyClassicForecast(
    daily: List<DailyWeather>,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    modifier: Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var expandedMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val canExpand = daily.size > 5

    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            DailyCompactPreview(
                daily = daily,
                unit = unit,
                utcOffsetSeconds = utcOffsetSeconds,
                visibleCount = if (showAll) daily.size else 5,
                expandedMillis = expandedMillis,
                windUnit = windUnit,
                onDayClick = { day ->
                    expandedMillis = if (expandedMillis == day.dateMillis) null else day.dateMillis
                },
            )
            if (canExpand) {
                HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.65f), thickness = 1.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(role = Role.Button) {
                            showAll = !showAll
                            if (!showAll && expandedMillis !in daily.take(5).map(DailyWeather::dateMillis)) {
                                expandedMillis = null
                            }
                        }
                        .semantics {
                            contentDescription = if (showAll) "收起到前五天天气" else "展开后续逐日天气"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (showAll) "[ 收起至前 5 天 ]" else "[ 展开后续 ${daily.size - 5} 天 ]",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZhishengMint,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (showAll) "↑" else "↓",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZhishengCyan,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCompactPreview(
    daily: List<DailyWeather>,
    unit: String,
    utcOffsetSeconds: Int?,
    visibleCount: Int = 3,
    expandedMillis: Long? = null,
    windUnit: String = "kmh",
    onDayClick: (DailyWeather) -> Unit,
) {
    val visible = daily.take(visibleCount)
    val lows = daily.mapNotNull { conv(it.low, unit) }
    val highs = daily.mapNotNull { conv(it.high, unit) }
    val weekMin = lows.minOrNull() ?: 0.0
    val weekMax = highs.maxOrNull() ?: 1.0

    Column {
        visible.forEachIndexed { index, d ->
            val isToday = Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天"
            if (index > 0 && Fmt.isDifferentMonth(visible[index - 1].dateMillis, d.dateMillis, utcOffsetSeconds)) {
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
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onDayClick(d) },
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
                BoxWithConstraints(
                    Modifier.padding(horizontal = 8.dp).weight(1f).height(4.dp)
                        .background(ZhishengTextTertiary.copy(alpha = 0.3f), RectangleShape),
                ) {
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
                if (expandedMillis == d.dateMillis) {
                    DailyExpanded(d, windUnit)
                }
            }
            if (index < visible.lastIndex) {
                HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.5f), thickness = 1.dp)
            }
        }
    }
}

private data class DailyDayNightVisual(
    val dayLabel: String,
    val nightLabel: String,
    val dayCondition: WeatherCondition,
    val nightCondition: WeatherCondition,
)

private fun dailyDayNightVisual(daily: DailyWeather): DailyDayNightVisual {
    val fallback = daily.condition ?: WeatherCondition.UNKNOWN
    val text = daily.weatherText?.trim().orEmpty().ifBlank { fallback.label }
    val parts = text.split("转", limit = 2).map(String::trim)
    val dayLabel = parts.firstOrNull().orEmpty().ifBlank { fallback.label }
    val nightLabel = parts.getOrNull(1).orEmpty().ifBlank { dayLabel }
    return DailyDayNightVisual(
        dayLabel = dayLabel,
        nightLabel = nightLabel,
        dayCondition = forecastConditionForLabel(dayLabel, night = false, fallback),
        nightCondition = forecastConditionForLabel(nightLabel, night = true, fallback),
    )
}

private fun forecastConditionForLabel(
    label: String,
    night: Boolean,
    fallback: WeatherCondition,
): WeatherCondition = when {
    "雷" in label -> WeatherCondition.THUNDERSTORM
    "冰雹" in label -> WeatherCondition.HAIL
    "雨夹雪" in label -> WeatherCondition.SLEET
    "冻" in label && "雨" in label -> WeatherCondition.FREEZING_RAIN
    "雪" in label -> WeatherCondition.SNOW
    "小雨" in label || "毛毛雨" in label -> WeatherCondition.DRIZZLE
    "雨" in label -> WeatherCondition.RAIN
    "雾" in label -> WeatherCondition.FOG
    "霾" in label -> WeatherCondition.HAZE
    "沙" in label || "尘" in label -> WeatherCondition.SAND
    "风" in label -> WeatherCondition.WIND
    "阴" in label -> WeatherCondition.OVERCAST
    "云" in label -> if (night) WeatherCondition.PARTLY_CLOUDY_NIGHT else WeatherCondition.PARTLY_CLOUDY
    "晴" in label -> if (night) WeatherCondition.CLEAR_NIGHT else WeatherCondition.CLEAR
    night && fallback == WeatherCondition.CLEAR -> WeatherCondition.CLEAR_NIGHT
    night && fallback == WeatherCondition.PARTLY_CLOUDY -> WeatherCondition.PARTLY_CLOUDY_NIGHT
    else -> fallback
}

@Composable
private fun DailyForecastStrip(
    days: List<DailyWeather>,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
) {
    if (days.isEmpty()) return
    val palette = LocalZhishengPalette.current
    val scrollState = rememberScrollState()
    val stripHeight = 326.dp
    val chartTop = 118.dp
    val chartBottom = 216.dp
    val converted = days.flatMap { listOfNotNull(conv(it.low, unit), conv(it.high, unit)) }
    val minimum = (converted.minOrNull() ?: 0.0) - 1.0
    val maximum = (converted.maxOrNull() ?: minimum + 1.0) + 1.0
    val span = (maximum - minimum).coerceAtLeast(1.0)
    fun yOffset(value: Double): androidx.compose.ui.unit.Dp {
        val ratio = ((value - minimum) / span).toFloat().coerceIn(0f, 1f)
        return chartBottom - (chartBottom - chartTop) * ratio
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = if (days.size <= 3) maxWidth / days.size else 88.dp
        val totalWidth = columnWidth * days.size
        Box(
            Modifier.fillMaxWidth().height(stripHeight).horizontalScroll(scrollState),
        ) {
            Canvas(Modifier.width(totalWidth).height(stripHeight)) {
                val columnPx = columnWidth.toPx()
                val chartTopPx = chartTop.toPx()
                val chartBottomPx = chartBottom.toPx()
                fun x(index: Int): Float = columnPx * (index + 0.5f)
                fun y(value: Double): Float = chartBottomPx -
                    ((value - minimum) / span).toFloat() * (chartBottomPx - chartTopPx)

                val todayIndex = days.indexOfFirst {
                    Fmt.dailyDayLabel(it.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天"
                }
                if (todayIndex >= 0) {
                    drawRect(
                        palette.mint.copy(alpha = 0.055f),
                        topLeft = Offset(columnPx * todayIndex, 0f),
                        size = Size(columnPx, size.height),
                    )
                }
                repeat(days.size + 1) { index ->
                    drawLine(
                        palette.cardBorder.copy(alpha = 0.7f),
                        Offset(columnPx * index, 0f),
                        Offset(columnPx * index, size.height),
                        1f,
                    )
                }
                repeat(3) { guide ->
                    val guideY = chartTopPx + guide * (chartBottomPx - chartTopPx) / 2f
                    drawLine(
                        palette.cardBorder.copy(alpha = 0.85f),
                        Offset(0f, guideY),
                        Offset(size.width, guideY),
                        1f,
                    )
                }

                fun drawSeries(selector: (DailyWeather) -> Double?, color: Color) {
                    val path = Path()
                    var drawing = false
                    days.forEachIndexed { index, day ->
                        val value = selector(day)?.let { conv(it, unit) }
                        if (value == null) {
                            drawing = false
                        } else if (!drawing) {
                            path.moveTo(x(index), y(value))
                            drawing = true
                        } else {
                            path.lineTo(x(index), y(value))
                        }
                    }
                    drawPath(
                        path,
                        color.copy(alpha = 0.82f),
                        style = Stroke(width = 2.6f, cap = StrokeCap.Round),
                    )
                    days.forEachIndexed { index, day ->
                        selector(day)?.let { raw ->
                            conv(raw, unit)?.let { value ->
                                drawCircle(color, radius = 4.1f, center = Offset(x(index), y(value)))
                            }
                        }
                    }
                }
                drawSeries(DailyWeather::high, palette.orange)
                drawSeries(DailyWeather::low, palette.cyan)
            }

            Row(Modifier.width(totalWidth).height(stripHeight)) {
                days.forEach { d ->
                    val visual = dailyDayNightVisual(d)
                    val isToday = Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天"
                    Box(Modifier.width(columnWidth).height(stripHeight)) {
                        Text(
                            Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds),
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) ZhishengMint else ZhishengTextSecondary,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Text(
                            Fmt.dayOfMonth(d.dateMillis, utcOffsetSeconds),
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 31.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                            maxLines = 1,
                        )
                        WeatherIcon(
                            visual.dayCondition,
                            Modifier.align(Alignment.TopCenter).offset(y = 52.dp).size(29.dp),
                        )
                        Text(
                            visual.dayLabel,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 84.dp).padding(horizontal = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.high?.let { raw ->
                            conv(raw, unit)?.let { value ->
                                Text(
                                    "${Fmt.temp(raw, unit)}°",
                                    modifier = Modifier.align(Alignment.TopCenter).offset(y = yOffset(value) - 20.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ZhishengOrange,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        d.low?.let { raw ->
                            conv(raw, unit)?.let { value ->
                                Text(
                                    "${Fmt.temp(raw, unit)}°",
                                    modifier = Modifier.align(Alignment.TopCenter).offset(y = yOffset(value) + 6.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ZhishengCyan,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        WeatherIcon(
                            visual.nightCondition,
                            Modifier.align(Alignment.TopCenter).offset(y = 237.dp).size(27.dp),
                        )
                        Text(
                            visual.nightLabel,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 266.dp).padding(horizontal = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Fmt.probability(d.precipProbability)?.let { "降水 $it" } ?: "降水 --",
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 286.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (d.precipProbability != null) ZhishengCyan else ZhishengTextTertiary,
                            maxLines = 1,
                        )
                        Text(
                            d.windSpeed?.let { "风 ${Fmt.wind(it, windUnit)}" } ?: "风 --",
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = 305.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// —— 逐日：首页只承担快速扫读，完整 15 日信息进入独立页面 ——
@Composable
internal fun DailySection(
    daily: List<DailyWeather>,
    unit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    modifier: Modifier,
    onView15Days: () -> Unit,
) {
    if (isPhosphorVista) {
        VistaDailyOverview(daily, unit, windUnit, utcOffsetSeconds, modifier, onView15Days)
        return
    }
    val lows = daily.mapNotNull { conv(it.low, unit) }
    val highs = daily.mapNotNull { conv(it.high, unit) }
    val weekMin = lows.minOrNull() ?: 0.0
    val weekMax = highs.maxOrNull() ?: 1.0
    // 与 VistaDailyOverview 同一判定：大字号、窄屏或华氏度时行内放不下
    // 日期 + 天气文字 + 两端温度 + 温度条，改为两行排版，避免截断。
    val enlarged = LocalDensity.current.fontScale > 1.2f ||
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 || unit == "f"
    var expandedMillis by remember { mutableStateOf<Long?>(null) }
    val visibleDays = daily.take(5)
    val detailDayCount = daily.take(15).size
    val detailLabel = if (detailDayCount >= 15) "查看近15日天气" else "查看未来${detailDayCount}日天气"

    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
            visibleDays.forEachIndexed { index, d ->
                val expanded = expandedMillis == d.dateMillis
                val isToday = Fmt.dailyDayLabel(d.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天"
                if (index > 0 && Fmt.isDifferentMonth(visibleDays[index - 1].dateMillis, d.dateMillis, utcOffsetSeconds)) {
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
                    if (enlarged) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.width(66.dp),
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
                                text = d.weatherText?.trim()?.takeIf(String::isNotBlank)
                                    ?: d.condition?.label
                                    ?: "未知",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "最低 ${Fmt.temp(d.low, unit)?.let { "$it°" } ?: "--"}   最高 ${Fmt.temp(d.high, unit)?.let { "$it°" } ?: "--"}",
                            modifier = Modifier.padding(start = 72.dp, top = 2.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                        )
                    } else Row(
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
                            text = d.weatherText?.trim()?.takeIf(String::isNotBlank)
                                ?: d.condition?.label
                                ?: "未知",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                            // 和风常返回“少云转晴”等五字日夜组合；58dp 会只在该源截尾。
                            // 加宽后仍给温度区间条保留超过三分之一的卡片宽度。
                            modifier = Modifier.width(72.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                                    // 位置和长度已经表达温度区间；颜色只表达阅读状态：
                                    // 今天用薄荷绿，后续日期统一钢青，避免整张表被绿色淹没。
                                    .background(
                                        if (isToday) ZhishengMint else ZhishengCyan.copy(alpha = 0.68f),
                                        RectangleShape,
                                    ),
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
                if (index < visibleDays.lastIndex) {
                    HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                }
            }
            HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.72f), thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onView15Days)
                    .semantics { contentDescription = detailLabel }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
                    .height(38.dp)
                    .zhishengCompactPanel(
                        containerColor = Color.Transparent,
                        borderColor = ZhishengCardBorder.copy(alpha = 0.78f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    detailLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = ZhishengText,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun VistaDailyOverview(
    daily: List<DailyWeather>, unit: String, windUnit: String, utcOffsetSeconds: Int?,
    modifier: Modifier, onMore: () -> Unit,
) {
    val days = daily.take(5)
    val lows = days.mapNotNull { conv(it.low, unit) }
    val highs = days.mapNotNull { conv(it.high, unit) }
    val low = lows.minOrNull() ?: 0.0
    val high = highs.maxOrNull() ?: 1.0
    val palette = LocalZhishengPalette.current
    val enlarged = LocalDensity.current.fontScale > 1.2f ||
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 || unit == "f"
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    HudCard(modifier) {
        Column {

            days.forEachIndexed { index, day ->
                val open = expanded == day.dateMillis
                Column(Modifier.fillMaxWidth().vistaClick(if (open) "收起当日详情" else "展开当日详情") { expanded = if (open) null else day.dateMillis }.padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.width(if (enlarged) 66.dp else 46.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Fmt.dailyDayLabel(day.dateMillis, utcOffsetSeconds = utcOffsetSeconds), style = MaterialTheme.typography.bodyMedium, color = if (index == 0) ZhishengMint else ZhishengText)
                            Text(Fmt.dayOfMonth(day.dateMillis, utcOffsetSeconds), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                        }
                        WeatherIcon(day.condition, Modifier.size(28.dp))
                        Text(day.weatherText?.takeIf { it.isNotBlank() } ?: day.condition?.label ?: "天气暂缺", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary, maxLines = if (enlarged) 2 else 1, overflow = TextOverflow.Ellipsis)
                        if (!enlarged) {
                            Text("${Fmt.temp(day.low, unit) ?: "—"}°", Modifier.width(34.dp), style = MaterialTheme.typography.labelLarge, color = ZhishengTextSecondary, textAlign = TextAlign.End)
                            Canvas(Modifier.width(42.dp).height(8.dp)) {
                                val (start, _, width) = tempBarParams(day.low, day.high, low, high, unit)
                                drawLine(palette.cardBorder.copy(alpha = 0.35f), Offset(0f, center.y), Offset(size.width, center.y), 5.dp.toPx(), StrokeCap.Round)
                                if (day.low != null && day.high != null) drawLine(vistaTemperatureInk(day.high, palette.isLight), Offset(size.width * start, center.y), Offset(size.width * (start + width), center.y), 5.dp.toPx(), StrokeCap.Round)
                            }
                            Text("${Fmt.temp(day.high, unit) ?: "—"}°", Modifier.width(34.dp), style = MaterialTheme.typography.labelLarge, color = ZhishengText, textAlign = TextAlign.End)
                        }
                    }
                    if (enlarged) Text("最低 ${Fmt.temp(day.low, unit) ?: "—"}°  最高 ${Fmt.temp(day.high, unit) ?: "—"}°", style = MaterialTheme.typography.bodyMedium, color = ZhishengText)
                    AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                        Column {
                        Text(Fmt.dayOfMonth(day.dateMillis, utcOffsetSeconds), style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
                        DailyExpanded(day, windUnit)
                        }
                    }
                }
                if (index < days.lastIndex) HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.20f))
            }
            HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.20f))
            Box(Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onMore).heightIn(min = 48.dp).padding(top = 8.dp),
                contentAlignment = Alignment.Center) {
                Text("查看未来${daily.take(15).size}日天气", textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
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
        if (d.windSpeed != null || d.windDirectionDeg != null) {
            Text(
                dailyWindLabel(d, windUnit),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
            Spacer(Modifier.height(4.dp))
        }
        d.aqi?.let {
            Text("AQI $it", style = MaterialTheme.typography.labelSmall, color = aqiColor(it))
            Spacer(Modifier.height(4.dp))
        }
        // 与“多天天气”页 vistaForecastFacts 同一字段集：首页展开也要能看到
        // 阵风、湿度、云量和紫外线，不因入口不同而少信息。
        d.windGust?.takeIf { it.isFinite() && it >= 0 }?.let { gust ->
            Fmt.wind(gust, windUnit)?.let { gustText ->
                Text("阵风 $gustText", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                Spacer(Modifier.height(4.dp))
            }
        }
        val humidCloudParts = buildList {
            d.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("湿度 ${it.toInt()}%") }
            d.cloudCover?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("云量 ${it.toInt()}%") }
        }
        if (humidCloudParts.isNotEmpty()) {
            Text(
                humidCloudParts.joinToString("   "),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
            Spacer(Modifier.height(4.dp))
        }
        d.uvIndex?.takeIf { it >= 0 }?.let { uv ->
            Text("紫外线指数 $uv", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
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
        Fmt.probability(d.precipProbability)?.let { probability ->
            Spacer(Modifier.height(4.dp))
            Text(
                "降水概率 $probability",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengCyan,
            )
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
internal fun TelemetryGrid(
    cur: CurrentWeather,
    today: DailyWeather?,
    unit: String,
    prefs: com.zhisheng.weather.ui.DisplayPrefs,
    modifier: Modifier,
    city: com.zhisheng.weather.model.City?,
    utcOffsetSeconds: Int? = null,
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
        // 当前雨强归位到"短时降水"卡（同源数据同卡收口），遥测网格保持 2 列收尾完整
    ).filter { (metric, _) -> metric in prefs.telemetryMetrics }
        .mapNotNull { (_, item) ->
            val (cn, en, value) = item
            value?.let { Triple(cn, en, it) }
        }
    val showLuminary = TelemetryMetric.LUMINARY in prefs.telemetryMetrics && today != null && (
        today.sunrise != null || today.sunset != null || today.moonPhase != null ||
            today.moonrise != null || today.moonset != null
        )
    val palette = LocalZhishengPalette.current

    // 同级遥测读数合并为一个连续面板，用分隔线组织，不再一项套一张卡。
    if (items.isEmpty() && !showLuminary) return
    if (isPhosphorVista) {
        val columns = when {
            LocalDensity.current.fontScale > 1.25f -> 1
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600 -> 3
            else -> 2
        }
        Column(modifier.fillMaxWidth().padding(horizontal = LocalZhishengChrome.current.pagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (items.isNotEmpty()) Column(Modifier.fillMaxWidth().zhishengPanel().padding(horizontal = 16.dp, vertical = 4.dp)) {
                val rows = items.chunked(columns)
                rows.forEachIndexed { index, row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { (cn, en, value) ->
                            TeleCell(cn, en, value, cur, city, Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if (index < rows.lastIndex) HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.18f))
                }
            }
            if (showLuminary) Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp)) {
                VistaLuminary(today, utcOffsetSeconds)
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isPhosphorVista) LocalZhishengChrome.current.pagePadding else 16.dp)
            .zhishengPanel(containerColor = if (palette.isLight) ZhishengCard else ZhishengSurface),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val columns = when {
            !isPhosphorVista -> 2
            LocalDensity.current.fontScale > 1.25f -> 1
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600 -> 3
            else -> 2
        }
        val rows = items.chunked(columns)
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                rowItems.forEachIndexed { itemIndex, (cn, en, value) ->
                    TeleCell(cn, en, value, cur, city, Modifier.weight(1f).fillMaxHeight())
                    if (!isPhosphorVista && itemIndex < rowItems.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(ZhishengCardBorder.copy(alpha = 0.72f)),
                        )
                    }
                }
                if (isPhosphorVista) repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
            if (!isPhosphorVista && (rowIndex < rows.lastIndex || showLuminary)) {
                HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.72f), thickness = 1.dp)
            }
        }
        // 日月宽卡：公共源不提供月出月落时由本地天文计算补齐。
        if (showLuminary) {
            if (isPhosphorVista && items.isNotEmpty()) HorizontalDivider(
                Modifier.padding(horizontal = 16.dp), color = ZhishengCardBorder.copy(alpha = 0.5f))
            Box(Modifier.fillMaxWidth().padding(horizontal = if (isPhosphorVista) 16.dp else 12.dp, vertical = if (isPhosphorVista) 16.dp else 10.dp)) {
                if (isPhosphorVista) {
                    VistaLuminary(today, utcOffsetSeconds)
                } else {
                    Column {
                        TeleLabel("日月", "LUMINARY")
                        Spacer(Modifier.height(6.dp))
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
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("月相 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                            Text(
                                Fmt.moonPhaseZh(today?.moonPhase) ?: "--",
                                style = MaterialTheme.typography.titleSmall,
                                color = ZhishengCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("月出 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                            Text(today?.moonrise ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengCyan, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(18.dp))
                            Text("月落 ", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                            Text(today?.moonset ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengCyan, fontWeight = FontWeight.Bold)
                        }
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
    city: com.zhisheng.weather.model.City?,
    modifier: Modifier = Modifier,
) {
    if (isPhosphorVista) {
        Column(modifier.heightIn(min = 76.dp).padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cn, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (en == "WIND" && cur.windDirectionDeg != null) {
                        WindCompass(cur.windDirectionDeg, city?.latitude, city?.longitude)
                    } else PhosphorIcon(telemetryIcon(cn), null, Modifier.size(18.dp), ZhishengCyan.copy(alpha = 0.7f))
                }
            }
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp),
                color = ZhishengText, fontWeight = FontWeight.Medium)
        }
        return
    }
    Column(modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
        TeleLabel(cn, en)
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (en == "WIND" && cur.windDirectionDeg != null) {
                WindCompass(cur.windDirectionDeg, city?.latitude, city?.longitude)
                Spacer(Modifier.width(8.dp))
            }
            // 湿度 / 云量 / 紫外线给经典终端也补上读数条（方形刻度，横向一排）。
            // 轨道用注释灰的三成透明度：非文字图形在近黑底上仍可辨认（≥3:1）。
            val fraction = vistaMeterFraction(en, cur)
            if (fraction != null) {
                ClassicMeterBar(fraction, vistaMeterAccent(en, cur), ZhishengTextTertiary.copy(alpha = 0.35f))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                value,
                modifier = if (fraction != null) Modifier.weight(1f) else Modifier,
                style = MaterialTheme.typography.titleMedium,
                color = ZhishengText,
                fontWeight = FontWeight.Bold,
                textAlign = if (fraction != null) TextAlign.End else TextAlign.Start,
                maxLines = 1,
            )
        }
    }
}

private fun telemetryIcon(label: String): Int = when (label) {
    "湿度" -> R.drawable.ph_drop
    "风向风速", "阵风" -> R.drawable.ph_wind
    "气压" -> R.drawable.ph_gauge
    "紫外线" -> R.drawable.ph_sun
    "能见度" -> R.drawable.ph_eye
    "露点" -> R.drawable.ph_thermometer
    "云量" -> R.drawable.ph_cloud
    "当前雨强" -> R.drawable.ph_cloud_rain
    else -> R.drawable.ph_wave_sine
}

@Composable
private fun WindCompass(degrees: Double, latitude: Double?, longitude: Double?) {
    val ring = ZhishengCardBorder
    val north = ZhishengOrange
    val needle = ZhishengCyan
    val hubFill = ZhishengSurface
    val heading = rememberWorldHeadingDegrees(latitude, longitude)
    val needleDeg = heading?.let { windNeedleScreenRotation(degrees.toFloat(), it) } ?: degrees.toFloat()
    Box(
        Modifier
            // 与 titleMedium 行高对齐，湿度/风向这一行才不会比气压紫外线更高。
            .size(22.dp)
            .semantics { contentDescription = uiText("风向 ${degrees.roundToInt()} 度") },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f - 0.8.dp.toPx()
            val tick = 2.2.dp.toPx()
            drawCircle(ring, r, c, style = Stroke(1.dp.toPx()))
            if (heading == null) {
                drawLine(north, Offset(c.x, c.y - r), Offset(c.x, c.y - r + tick), 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
            val pip = 1.5.dp.toPx()
            drawLine(ring, Offset(c.x + r, c.y), Offset(c.x + r - pip, c.y), 1.dp.toPx())
            drawLine(ring, Offset(c.x, c.y + r), Offset(c.x, c.y + r - pip), 1.dp.toPx())
            drawLine(ring, Offset(c.x - r, c.y), Offset(c.x - r + pip, c.y), 1.dp.toPx())
        }
        // 有朝向时箭头锁在真实来风方向；立着拿、平放都能用，不要求当前城市来自定位。
        Canvas(Modifier.fillMaxSize().rotate(needleDeg)) {
            val c = center
            val dart = Path().apply {
                moveTo(c.x, 2.2.dp.toPx())
                lineTo(c.x - 2.6.dp.toPx(), 8.4.dp.toPx())
                lineTo(c.x - 0.6.dp.toPx(), 8.4.dp.toPx())
                lineTo(c.x - 0.6.dp.toPx(), size.height - 3.4.dp.toPx())
                lineTo(c.x, size.height - 2.2.dp.toPx())
                lineTo(c.x + 0.6.dp.toPx(), size.height - 3.4.dp.toPx())
                lineTo(c.x + 0.6.dp.toPx(), 8.4.dp.toPx())
                lineTo(c.x + 2.6.dp.toPx(), 8.4.dp.toPx())
                close()
            }
            drawPath(dart, needle)
            drawCircle(hubFill, 1.7.dp.toPx(), c)
            drawCircle(needle, 1.7.dp.toPx(), c, style = Stroke(0.9.dp.toPx()))
        }
    }
}

@Composable
private fun TeleLabel(cn: String, en: String) {
    if (isPhosphorVista) {
        Text(cn, style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
        return
    }
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
internal fun AqiCard(aqi: AqiInfo, modifier: Modifier) {
    if (isPhosphorVista) {
        // 与五天天气温差条同语言（20260919）：细圆角轨道 + 当前等级色单色填充 + 档界细线；
        // 污染物两行平铺；提示语保持下线。整页只出现当前档一个强调色。
        val fillColor = aqiColor(aqi.value)
        HudCard(modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(aqi.value?.toString() ?: "—", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium, color = aqiColor(aqi.value))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(aqi.level ?: "空气质量", style = MaterialTheme.typography.titleMedium, color = aqiColor(aqi.value))
                        Text("AQI${Fmt.aqiStandardLabel(aqi.standard)?.let { " · $it" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    }
                    aqi.primary?.takeIf(String::isNotBlank)?.let {
                        Column(Modifier.widthIn(max = 96.dp), horizontalAlignment = Alignment.End) {
                            Text("首要污染物", style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextTertiary, maxLines = 1)
                            Text(it, style = MaterialTheme.typography.titleSmall, color = aqiColor(aqi.value),
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val trackColor = ZhishengCardBorder.copy(alpha = .55f)
                val tickColor = ZhishengText.copy(alpha = .30f)
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val radius = size.height / 2f
                    drawRoundRect(trackColor, Offset.Zero,
                        Size(size.width, size.height), CornerRadius(radius))
                    val frac = ((aqi.value?.toFloat() ?: 0f) / 300f).coerceIn(0.02f, 1f)
                    drawRoundRect(fillColor, Offset.Zero, Size(size.width * frac, size.height), CornerRadius(radius))
                    // 档界细线：50/100/150/200（300 即右端点，不画）
                    listOf(50f, 100f, 150f, 200f).forEach { bound ->
                        val x = size.width * bound / 300f
                        drawLine(tickColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AqiFactCell("PM2.5", aqi.pm25, Modifier.weight(1f))
                    AqiFactCell("PM10", aqi.pm10, Modifier.weight(1f))
                    AqiFactCell("O₃", aqi.o3, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AqiFactCell("NO₂", aqi.no2, Modifier.weight(1f))
                    AqiFactCell("SO₂", aqi.so2, Modifier.weight(1f))
                    AqiFactCell("CO", aqi.co, Modifier.weight(1f))
                }
            }
        }
        return
    }
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
                Column(Modifier.weight(1f)) {
                    Text(
                        aqi.level ?: "空气质量",
                        style = MaterialTheme.typography.titleMedium,
                        color = aqiColor(aqi.value),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "AQI${Fmt.aqiStandardLabel(aqi.standard)?.let { " · $it" }.orEmpty()}" + if (isPhosphorVista) "" else " // AIR QUALITY INDEX",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                aqi.primary?.let {
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.widthIn(max = 112.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            "首要污染物",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                            maxLines = 1,
                        )
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            color = aqiColor(aqi.value),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PollutantChip("PM2.5", aqi.pm25, aqi.pollutantUnits["pm2p5"], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(ZhishengCardBorder.copy(alpha = 0.65f)))
                PollutantChip("PM10", aqi.pm10, aqi.pollutantUnits["pm10"], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(ZhishengCardBorder.copy(alpha = 0.65f)))
                PollutantChip("O3", aqi.o3, aqi.pollutantUnits["o3"], Modifier.weight(1f))
            }
            HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.65f), thickness = 1.dp)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PollutantChip("NO2", aqi.no2, aqi.pollutantUnits["no2"], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(ZhishengCardBorder.copy(alpha = 0.65f)))
                PollutantChip("SO2", aqi.so2, aqi.pollutantUnits["so2"], Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(ZhishengCardBorder.copy(alpha = 0.65f)))
                PollutantChip("CO", aqi.co, aqi.pollutantUnits["co"], Modifier.weight(1f))
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
private fun PollutantChip(name: String, value: String?, unit: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "--", style = MaterialTheme.typography.titleSmall, color = ZhishengText, maxLines = 1)
            if (value != null && !unit.isNullOrBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 8.sp, color = ZhishengTextTertiary, maxLines = 1)
            }
        }
    }
}

/** 色带版空气质量卡的紧凑污染物格：名称小字 + 读数，无单位行（六项单位同为国标浓度单位）。 */
@Composable
private fun AqiFactCell(name: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
        Text(
            value?.takeIf { it.isNotBlank() && it != "--" && it != "—" } ?: "--",
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengText,
        )
    }
}

@Composable
private fun aqiColor(value: Int?): Color = when {
    value == null -> ZhishengTextTertiary
    value <= 50 -> ZhishengMint
    value <= 100 -> ZhishengMint.copy(alpha = if (isPhosphorVista) 1f else 0.8f)
    value <= 150 -> ZhishengOrange
    value <= 200 -> ZhishengOrange.copy(alpha = if (isPhosphorVista) 1f else 0.85f)
    value <= 300 -> ZhishengRed
    else -> ZhishengRed.copy(alpha = if (isPhosphorVista) 1f else 0.8f)
}

// —— 生活指数 ——
internal data class LifeIndexUi(
    val name: String,
    val en: String,
    val value: String,
    val positive: Boolean? = null,
    val period: String? = null,
)

internal fun clampCityDeckPosition(position: Float, cityCount: Int): Float {
    if (cityCount <= 0) return 0f
    return position.coerceIn(0f, (cityCount - 1).toFloat())
}

private fun GraphicsLayerScope.cityCardLayer(
    x: Float,
    rotation: Float,
    scaleXValue: Float,
    scaleYValue: Float,
    alphaValue: Float,
    tiltY: Float,
    shape: Shape,
) {
    translationX = x
    rotationZ = rotation
    scaleX = scaleXValue
    scaleY = scaleYValue
    alpha = alphaValue
    cameraDistance = 18f * density
    rotationY = tiltY
    this.shape = shape
    clip = true
    compositingStrategy = CompositingStrategy.Offscreen
}

internal fun lifeIndexItems(data: WeatherData, selected: Set<LifeIndexMetric>): List<LifeIndexUi> = buildList {
    // Supplier life indices can describe the whole day. The current UV card must
    // agree with the current numeric reading, including a valid zero at night.
    data.current?.uvIndex?.takeIf { it in 0..50 && LifeIndexMetric.UV in selected }?.let { uv ->
        add(LifeIndexUi(LifeIndexMetric.UV.cn, LifeIndexMetric.UV.en, uvText(uv), period = "当前"))
    }
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
            if (metric in selected) add(LifeIndexUi(metric.cn, metric.en, value,
                period = if (metric == LifeIndexMetric.UV) "今日预报" else null))
        } else if (index.name.isNotBlank()) {
            add(LifeIndexUi(index.name, index.en, value))
        }
    }
}.distinctBy { it.name }

@Composable
internal fun IndicesRow(data: WeatherData, selected: Set<LifeIndexMetric>, modifier: Modifier, unit: String = "c") {
    var advice by remember { mutableStateOf<LifeIndexUi?>(null) }
    val items = lifeIndexItems(data, selected)
    advice?.let { selection ->
        items.firstOrNull { it.name == selection.name && it.en == selection.en }?.let { item ->
            LifeAdviceSheet(item.name, item.en, item.value, data, unit, item.period) { advice = null }
        }
    }
    if (items.isEmpty()) return
    if (isPhosphorVista) {
        HudCard(modifier.fillMaxWidth()) {
            val columns = if (LocalDensity.current.fontScale > 1.25f) 1 else 2
            val rows = items.chunked(columns)
            Column {
                rows.forEachIndexed { index, row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { item ->
                            Column(Modifier.weight(1f).clickable { advice = item }.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                                    PhosphorIcon(lifeIndexIcon(item.en), null, Modifier.size(18.dp), ZhishengTextTertiary)
                                }
                                Text(item.value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = when (item.positive) { true -> ZhishengMint; false -> ZhishengOrange; null -> ZhishengText })
                                item.period?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary) }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if (index < rows.lastIndex) HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.18f))
                }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(if (isPhosphorVista) Modifier.zhishengPanel() else Modifier),
    ) {
        if (isPhosphorVista) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VistaModuleIcon(R.drawable.ph_person_simple_walk, "生活指数", tint = ZhishengOrange)
                Spacer(Modifier.width(8.dp))
                Text("生活建议", style = MaterialTheme.typography.labelLarge, color = ZhishengTextSecondary)
            }
            HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.5f))
        }
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    LifeIndexCard(item, Modifier.weight(1f).fillMaxHeight().clickable { advice = item })
                }
                // 奇数项独占最后一行，避免人为留下半屏空栏。
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

internal fun lifeIndexIcon(english: String): Int = when (LifeIndexMetric.fromEnglish(english)) {
    LifeIndexMetric.CAR_WASH -> R.drawable.ph_drop
    LifeIndexMetric.SPORTS -> R.drawable.ph_person_simple_walk
    LifeIndexMetric.DRESS, LifeIndexMetric.DRYING -> R.drawable.ph_t_shirt
    LifeIndexMetric.UV, LifeIndexMetric.SUNSCREEN -> R.drawable.ph_sun
    LifeIndexMetric.FISHING -> R.drawable.ph_waves
    LifeIndexMetric.TRAVEL -> R.drawable.ph_map_trifold
    LifeIndexMetric.TRAFFIC -> R.drawable.ph_navigation_arrow
    LifeIndexMetric.SUNGLASSES -> R.drawable.ph_eye
    LifeIndexMetric.ALLERGY, LifeIndexMetric.AIR_POLLUTION -> R.drawable.ph_leaf
    LifeIndexMetric.COMFORT, LifeIndexMetric.COLD, LifeIndexMetric.AIR_CONDITIONER -> R.drawable.ph_thermometer
    LifeIndexMetric.MAKEUP -> R.drawable.ph_sparkle
    null -> R.drawable.ph_info
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
            .then(
                if (isPhosphorVista) Modifier else Modifier.zhishengCompactPanel(
                    borderColor = accent.copy(alpha = if (item.positive == null) 1f else 0.5f),
                ),
            )
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
            if (!isPhosphorVista) Text(
                item.en,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                letterSpacing = 0.7.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.height(4.dp))
        item.period?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary) }
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
                Text(if (isPhosphorVista) { if (it) "适宜" else "注意" } else { if (it) "[OK]" else "[NG]" }, style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
    }
}

// —— 昨日复盘 ——
@Composable
private fun YesterdayCard(y: YesterdayInfo, today: DailyWeather?, unit: String, windUnit: String, modifier: Modifier) {
    if (isPhosphorVista) {
        HudCard(modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${Fmt.temp(y.high, unit) ?: "—"}° / ${Fmt.temp(y.low, unit) ?: "—"}°",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                    WeatherIcon(y.condition, Modifier.size(30.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    y.aqi?.let { Text("AQI $it", style = MaterialTheme.typography.bodySmall, color = aqiColor(it)) }
                    tempDelta(today?.high, y.high, unit)?.let { diff ->
                        Text(when { diff > 0 -> "最高温较昨高 $diff°"; diff < 0 -> "最高温较昨低 ${kotlin.math.abs(diff)}°"; else -> "最高温与昨日持平" },
                            style = MaterialTheme.typography.bodySmall, color = if (diff > 0) ZhishengOrange else ZhishengMint)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    yesterdayDetailLabels(y, windUnit).forEach { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    }
                }
            }
        }
        return
    }
    HudCard(modifier = modifier.fillMaxWidth()) {
        Column {
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
            yesterdayDetailLabels(y, windUnit).forEach { label ->
                Spacer(Modifier.height(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }
    }
}

// —— 台风 ——
@Composable
private fun TyphoonCard(typhoons: List<TyphoonInfo>, modifier: Modifier, onClick: () -> Unit) {
    HudCard(
        modifier = modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "查看台风路径", onClick = onClick),
    ) {
        Column {
            if (typhoons.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("西北太平洋台风路径", style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                        Text(
                            if (isPhosphorVista) "查看台风位置和未来路径" else "实况节点 · 强度变化 · 多机构预报",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                        )
                    }
                    Text("查看  →", style = MaterialTheme.typography.labelMedium, color = ZhishengMint, fontWeight = FontWeight.Bold)
                }
            }
            typhoons.forEach { t ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    typhoonPointLabel(t)?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                            modifier = Modifier.padding(start = 34.dp, top = 2.dp),
                        )
                    }
                }
            }
            if (typhoons.isNotEmpty()) {
                Text(
                    "查看实时路径与官方预报  →",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZhishengMint,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.End).padding(top = 5.dp),
                )
            }
        }
    }
}

internal fun hourlyWindLabel(hour: HourlyWeather): String? {
    val direction = com.zhisheng.weather.data.WeatherRepository.windDirection(hour.windDirectionDeg)
    val force = Fmt.windForce(hour.windSpeed)
    return listOfNotNull(direction, force).takeIf { it.isNotEmpty() }?.joinToString(" ")
}

internal fun dailyWindLabel(day: DailyWeather, windUnit: String): String {
    val direction = com.zhisheng.weather.data.WeatherRepository.windDirection(day.windDirectionDeg)
    val speed = day.windSpeed?.let { Fmt.wind(it, windUnit) }
    return "风 " + listOfNotNull(direction, speed).joinToString(" · ")
}

internal fun yesterdayDetailLabels(yesterday: YesterdayInfo, windUnit: String): List<String> = buildList {
    val start = yesterday.weatherStart?.takeIf { it != WeatherCondition.UNKNOWN }
    val end = yesterday.weatherEnd?.takeIf { it != WeatherCondition.UNKNOWN }
    when {
        start != null && end != null && start != end -> add("天气 ${start.label}转${end.label}")
        start != null -> add("天气 ${start.label}")
        end != null -> add("天气 ${end.label}")
    }
    val startDirection = com.zhisheng.weather.data.WeatherRepository.windDirection(yesterday.windDirectionStartDeg)
    val endDirection = com.zhisheng.weather.data.WeatherRepository.windDirection(yesterday.windDirectionEndDeg)
    val direction = when {
        startDirection != null && endDirection != null && startDirection != endDirection -> "$startDirection 转 $endDirection"
        startDirection != null -> startDirection
        else -> endDirection
    }
    val speed = listOfNotNull(yesterday.windSpeedStart, yesterday.windSpeedEnd)
        .maxOrNull()?.let { Fmt.wind(it, windUnit) }
    listOfNotNull(direction, speed).takeIf { it.isNotEmpty() }
        ?.let { add("风 ${it.joinToString(" · ")}") }
    val sun = listOfNotNull(
        yesterday.sunrise?.takeIf(String::isNotBlank)?.let { "日出 $it" },
        yesterday.sunset?.takeIf(String::isNotBlank)?.let { "日落 $it" },
    )
    if (sun.isNotEmpty()) add(sun.joinToString(" · "))
}

internal fun typhoonPointLabel(typhoon: TyphoonInfo): String? {
    val code = typhoon.id?.takeIf(String::isNotBlank)?.let { "编号 $it" }
    val point = if (typhoon.latitude != null && typhoon.longitude != null) {
        val lat = String.format(
            java.util.Locale.US,
            "%.2f°%s",
            kotlin.math.abs(typhoon.latitude),
            if (typhoon.latitude >= 0.0) "N" else "S",
        )
        val lon = String.format(
            java.util.Locale.US,
            "%.2f°%s",
            kotlin.math.abs(typhoon.longitude),
            if (typhoon.longitude >= 0.0) "E" else "W",
        )
        "$lat  $lon"
    } else null
    return listOfNotNull(code, point).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// —— 枳生页脚 ——
@Composable
private fun Footer(data: WeatherData, modifier: Modifier) {
    if (isPhosphorVista) {
        Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("枳生天气", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            Text(dataSourceSummary(data), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
                color = ZhishengTextTertiary, textAlign = TextAlign.Center)
            Text("v${com.zhisheng.weather.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary)
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isPhosphorVista) "枳生天气" else "ZHISHENG CORE // SENSOR-1 · FORECAST-2 · DISPLAY-3",
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
    "NMC" -> "数据来自中央气象台"
    "ZHISHENG" -> "数据来自枳生天气源"
    else -> "DATA ${source ?: "--"}"
}

private fun dataSourceSummary(data: WeatherData): String {
    // 枳生天气源：列出实际参与融合的源（多源共识），而不是"部分数据由…提供"
    if (data.dataSource == "ZHISHENG") {
        val names = data.fusionSources.distinct().map(::dataSourceShortLabel)
        return if (names.isEmpty()) dataSourceLabel(data.dataSource)
        else "${dataSourceLabel(data.dataSource)} · 多源共识：${names.joinToString("/")}"
    }
    val supplements = data.blockSources.values
        .filter { it != data.dataSource }
        .distinct()
        .map(::dataSourceShortLabel)
    return if (supplements.isEmpty()) dataSourceLabel(data.dataSource)
    else "${dataSourceLabel(data.dataSource)} · 部分数据由 ${supplements.joinToString("/")} 提供"
}

private fun supplementShortLabel(data: WeatherData): String {
    // 融合源不罗列参与者（行太长），用数量表达
    if (data.dataSource == "ZHISHENG") {
        val n = data.fusionSources.distinct().size
        return if (n <= 1) "" else "·融合${n}源"
    }
    val extras = data.blockSources.values.filter { it != data.dataSource }.distinct()
    return if (extras.isEmpty()) "" else extras.joinToString(prefix = "+", separator = "+") { dataSourceShortLabel(it) }
}

internal fun dataSourceShortLabel(source: String?): String = when {
    source == null -> "--"
    // Open-Meteo 多模型成员（"OPEN-METEO:ecmwf_ifs025" 等）归并展示
    source.startsWith("OPEN-METEO:") -> "OPEN-METEO"
    else -> when (source) {
        "QWEATHER" -> "和风"
        "CAIYUN" -> "彩云"
        "XIAOMI" -> "小米"
        "OPEN-METEO" -> "OPEN-METEO"
        "NMC" -> "中央气象台"
        "ZHISHENG" -> "枳生"
        "SIMULATION" -> "效果预览"
        else -> source
    }
}

// 天气读取期间的静态加载提示，不播放开场或自检序列。
@Composable
private fun BootState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherIcon(WeatherCondition.CLEAR, Modifier.size(56.dp).alpha(0.72f))
            Spacer(Modifier.height(16.dp))
            Text("正在读取天气", style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
        }
    }
}

@Composable
private fun EmptyState(onSearchClick: () -> Unit) {
    if (isPhosphorVista) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherIcon(WeatherCondition.CLEAR, Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text("从一座城市开始", style = MaterialTheme.typography.headlineSmall, color = ZhishengText)
            Spacer(Modifier.height(8.dp))
            Text("添加城市，查看此刻天气与未来变化", style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().zhishengPanel().clickable(role = Role.Button, onClick = onSearchClick).heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                Text("添加城市", style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
            }
        }
        return
    }
    // 无城市时的终端提示，文案不点名任何具体城市。
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
                .zhishengCompactPanel(borderColor = ZhishengMint.copy(alpha = 0.6f))
                .then(if (isPhosphorVista) Modifier else Modifier.drawCornerBrackets(ZhishengMint))
                .clickable(role = Role.Button, onClickLabel = "添加城市") { onSearchClick() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(if (isPhosphorVista) "添加城市" else "[ + ADD CITY ]", style = MaterialTheme.typography.titleSmall, color = ZhishengMint, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ErrorState(message: String, onSearchClick: () -> Unit) {
    if (isPhosphorVista) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            PhosphorIcon(R.drawable.ph_warning, null, Modifier.size(36.dp), ZhishengWarning)
            Spacer(Modifier.height(24.dp))
            Text("暂时无法获取天气", style = MaterialTheme.typography.headlineSmall, color = ZhishengText,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().zhishengPanel()
                .clickable(role = Role.Button, onClickLabel = "换一个城市", onClick = onSearchClick)
                .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Text("换一个城市试试", style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
            }
        }
        return
    }
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
    active: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLocate: () -> Unit,
    onClearLocateMessage: () -> Unit,
    onAddCity: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preciseEnabled by SettingsRepository.preciseLocationEnabled.collectAsState(initial = true)
    val precisePermissionAsked by SettingsRepository.preciseLocationPermissionAsked.collectAsState(initial = false)
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var locationServicesOff by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionDenied && LocationSource.hasPermission(context)) permissionDenied = false
        if (locationServicesOff && LocationSource.locationEnabledOnDevice(context)) locationServicesOff = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        scope.launch {
            SettingsRepository.setPreciseLocationPermissionAsked()
            if (LocationSource.hasPermission(context)) {
                permissionDenied = false
                onLocate()
            } else {
                permissionDenied = true
            }
        }
    }
    val requestPreciseLocation = {
        onClearLocateMessage()
        permissionDenied = false
        locationServicesOff = !LocationSource.locationEnabledOnDevice(context)
        if (!locationServicesOff) {
            scope.launch {
                val canLocate = LocationSource.hasPermission(context) &&
                    (!preciseEnabled || LocationSource.hasPrecisePermission(context) || precisePermissionAsked)
                if (canLocate) onLocate()
                else permissionLauncher.launch(LocationSource.requestedPermissions(precise = preciseEnabled))
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhishengScreen()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                if (isPhosphorVista) {
                    PhosphorIcon(R.drawable.ph_arrow_left, uiText("返回"), Modifier.size(22.dp), ZhishengText)
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回"), tint = ZhishengText)
                }
            }
            if (!isPhosphorVista) Text("00//", style = MaterialTheme.typography.titleSmall, color = ZhishengOrange, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(if (isPhosphorVista) "城市管理" else "城市", style = MaterialTheme.typography.titleMedium, color = ZhishengText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (!isPhosphorVista) Text("CITY LIST", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.5.sp)
            val favoriteCount = uiState.cities.count { it.isFavorite }
            if (favoriteCount > 0) {
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPhosphorVista) PhosphorIcon(R.drawable.ph_star, null, Modifier.size(15.dp), ZhishengOrange)
                    Text(
                        " $favoriteCount/${com.zhisheng.weather.data.CityRepository.MAX_FAVORITES}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengOrange,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
        if (isPhosphorVista) {
            CityWeatherList(uiState, Modifier.weight(1f).fillMaxWidth(),
                onAddCity, onSelect, onToggleFavorite, onRemove, active)
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            if (uiState.cities.isEmpty()) {
                item {
                    Text(
                        "还没有保存的城市",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextTertiary,
                    )
                }
            }
            itemsIndexed(
                items = uiState.cities,
                key = { _, city -> city.locationKey },
            ) { i, city ->
                val selected = city.locationKey == uiState.selectedCity?.locationKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPhosphorVista) Modifier.background(if (selected) ZhishengCard.copy(alpha = 0.72f) else Color.Transparent)
                            else Modifier.zhishengCompactPanel(containerColor = if (selected) ZhishengCard else Color.Transparent, borderColor = Color.Transparent)
                        )
                        .clickable { onSelect(city.locationKey) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isPhosphorVista) Text(
                        "%02d".format(i + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ZhishengOrange else ZhishengTextTertiary,
                    )
                    if (!isPhosphorVista) Spacer(Modifier.width(10.dp))
                    if (selected) {
                        Box(Modifier.size(width = 3.dp, height = 14.dp).background(ZhishengMint))
                        Spacer(Modifier.width(8.dp))
                    }
                    // 城市名 + 归属地：同名城市（金川区@金昌 vs 金川县@阿坝）必须可区分（v0.0.1）
                    Column(Modifier.weight(1f)) {
                        Text(
                            city.name,
                            style = if (isPhosphorVista) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
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
                    IconButton(
                        onClick = { onToggleFavorite(city.locationKey) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        PhosphorIcon(
                            R.drawable.ph_star,
                            contentDescription = if (city.isFavorite) "${uiText("取消收藏")} ${city.displayName}" else "${uiText("收藏")} ${city.displayName}",
                            tint = if (city.isFavorite) ZhishengOrange else ZhishengTextTertiary,
                            modifier = Modifier.size(21.dp).semantics {
                                contentDescription = if (city.isFavorite) {
                                    "${uiText("取消收藏")} ${city.displayName}"
                                } else {
                                    "${uiText("收藏")} ${city.displayName}"
                                }
                            },
                        )
                    }
                    IconButton(onClick = { onRemove(city.locationKey) }, modifier = Modifier.size(48.dp)) {
                        if (isPhosphorVista) {
                            PhosphorIcon(R.drawable.ph_trash, uiText("删除${city.displayName}"), Modifier.size(18.dp), ZhishengTextTertiary)
                        } else {
                            Icon(Icons.Filled.Close, contentDescription = uiText("删除${city.displayName}"), tint = ZhishengTextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        }
        // 操作区独立吃掉系统导航栏和额外底部余量；列表再长、屏幕再矮也只压缩中间列表。
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val feedback = when {
                permissionDenied -> "已拒绝位置权限，请在系统设置中选择“使用应用时允许”"
                locationServicesOff -> "系统定位服务未开启"
                else -> uiState.locateMessage
            }
            val approximateOnly = feedback?.contains("系统仅授予大致位置") == true
            feedback?.let { message ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(ZhishengCard).padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        if (isPhosphorVista) message else "> $message",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.startsWith("已精确") || message.startsWith("已定位")) ZhishengMint else ZhishengOrange,
                    )
                    if (permissionDenied || locationServicesOff || approximateOnly) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                locationServicesOff -> "[ 开启手机定位服务 ]"
                                approximateOnly -> "[ 提升为精确位置 ]"
                                else -> "[ 去应用权限设置 ]"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = ZhishengCyan,
                            modifier = Modifier.clickable(role = Role.Button) {
                                val intent = if (!locationServicesOff) {
                                    Intent(
                                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                } else {
                                    Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)
                                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }.padding(vertical = 3.dp),
                        )
                    }
                }
            }
            if (isPhosphorVista) {
                androidx.compose.material3.TextButton(onClick = requestPreciseLocation, enabled = !uiState.locating,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                    PhosphorIcon(R.drawable.ph_crosshair, null, Modifier.size(17.dp), ZhishengTextSecondary)
                    Spacer(Modifier.width(7.dp))
                    Text(if (uiState.locating) "定位中…" else "定位当前位置", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                }
            } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zhishengCompactPanel(
                        containerColor = ZhishengCard,
                        borderColor = ZhishengCyan.copy(alpha = 0.55f),
                    )
                    .clickable(enabled = !uiState.locating, onClickLabel = "精确定位当前位置") {
                        requestPreciseLocation()
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhosphorIcon(R.drawable.ph_crosshair, null, Modifier.size(21.dp), ZhishengCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (uiState.locating) "定位中 ..." else "定位当前位置",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (uiState.locating) ZhishengTextTertiary else ZhishengCyan,
                    letterSpacing = 1.sp,
                )
            }
            }
            if (!isPhosphorVista) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zhishengCompactPanel(
                        containerColor = ZhishengCard,
                        borderColor = ZhishengMint.copy(alpha = 0.5f),
                    )
                    .clickable(onClickLabel = "添加城市") { onAddCity() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPhosphorVista) PhosphorIcon(R.drawable.ph_plus, null, Modifier.size(19.dp), ZhishengMint)
                else Icon(Icons.Filled.Add, contentDescription = null, tint = ZhishengMint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加城市", style = MaterialTheme.typography.titleSmall, color = ZhishengMint, letterSpacing = 1.sp)
            }
            }
        }
    }
}

internal fun formatAlertTime(s: String): String = try {
    s.substring(0, minOf(16, s.length)).replace("T", " ")
} catch (_: Exception) {
    s
}
