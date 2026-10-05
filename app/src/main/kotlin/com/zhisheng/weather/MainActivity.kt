@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.zhisheng.weather

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import com.zhisheng.weather.ui.components.WeatherContinuity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import com.zhisheng.weather.data.UsageStatistics
import com.zhisheng.weather.data.UpdateNotices
import com.zhisheng.weather.ui.AppUpdateDialog
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.AppLanguage
import com.zhisheng.weather.data.AppIconManager
import com.zhisheng.weather.data.AppIconStyle
import com.zhisheng.weather.data.AppUpdate
import com.zhisheng.weather.data.AppUpdateCheck
import com.zhisheng.weather.data.AppUpdateInfo
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.ReleaseFeatures
import com.zhisheng.weather.ui.isStartupIconEntry
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.AppScreen
import com.zhisheng.weather.ui.StandbyTiltGate
import com.zhisheng.weather.ui.SearchScreen
import com.zhisheng.weather.ui.WeatherViewModel
import com.zhisheng.weather.ui.ProvideWeatherPresentationClock
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.home.HomeScreen
import com.zhisheng.weather.ui.SettingsScreen
import com.zhisheng.weather.ui.AtmosphereLabScreen
import com.zhisheng.weather.ui.overlayEnter
import com.zhisheng.weather.ui.overlayExit
import com.zhisheng.weather.ui.screenTransition
import com.zhisheng.weather.ui.LandscapeStandbyScreen
import com.zhisheng.weather.ui.HistoryScreen
import com.zhisheng.weather.ui.DailyForecastScreen
import com.zhisheng.weather.ui.RadarScreen
import com.zhisheng.weather.ui.TyphoonScreen
import com.zhisheng.weather.ui.PortraitSessionNotice
import com.zhisheng.weather.ui.WhatsNewDialog
import com.zhisheng.weather.ui.WhatsNewPreferenceFile
import com.zhisheng.weather.ui.WhatsNewSeenKey
import com.zhisheng.weather.ui.WhatsNewVersion
import com.zhisheng.weather.ui.InterfaceStyleChoiceDialog
import com.zhisheng.weather.ui.InterfaceStyleChoicePreferenceFile
import com.zhisheng.weather.ui.InterfaceStyleChoiceSeenKey
import com.zhisheng.weather.ui.InterfaceStyleChoiceVersion
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.i18n.ProvideAppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class ShortcutCommand(val action: String? = null, val sequence: Long = 0L)

class MainActivity : ComponentActivity() {
    private var hostResumed by mutableStateOf(false)
    private var startupPromptsSuppressed by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        hostResumed = true
    }

    override fun onPause() {
        hostResumed = false
        super.onPause()
    }

    private var lastInteraction = SystemClock.elapsedRealtime()
    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteraction = SystemClock.elapsedRealtime()
    }
    private val shortcutCommand = MutableStateFlow(ShortcutCommand())
    private var shortcutSequence = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preserve the real entry before dispatchShortcut normalizes its action.
        val iconEntry = isStartupIconEntry(intent?.action,
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true, isTaskRoot,
            savedInstanceState != null,
            (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0)
        startupPromptsSuppressed = !iconEntry
        // 首帧与持久化设置读取期间一律竖屏。不能用 true 占位开启传感器。
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (savedInstanceState == null) dispatchShortcut(intent)
        applySplashBackground()
        enableEdgeToEdge()
        applyEdgeToEdgeSystemBars()
        setContent {
            ProvideWeatherPresentationClock {
            // 主题模式（v0.0.5）：深色 / 浅色 / 跟随系统三档，切换立即生效
            val themeMode by SettingsRepository.themeMode.collectAsState(initial = initialThemeMode())
            val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
            val softGlow by SettingsRepository.softGlow.collectAsState(initial = false)
            val softGlowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = com.zhisheng.weather.data.SoftGlowLevel.SUBTLE)
            val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(
                initial = InterfaceStyle.PHOSPHOR_VISTA,
            )
            val homeSurfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(
                initial = com.zhisheng.weather.data.HomeSurfaceStyle.FRAGRANCE_GLASS,
            )
            val appLanguage by SettingsRepository.appLanguage.collectAsState(initial = AppLanguage.CHINESE)
            val appConfiguration = LocalConfiguration.current
            val systemDark = isSystemInDarkTheme()
            val isLight = when (themeMode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                // 跟随系统：系统深色→深色板（此前直接取 systemDark，方向反了，跟随系统会显示相反主题）
                ThemeMode.SYSTEM -> !systemDark
            }
            ZhishengWeatherTheme(
                isLight = isLight,
                accentTone = accentTone,
                interfaceStyle = interfaceStyle,
                homeSurfaceStyle = homeSurfaceStyle,
                softGlow = softGlow,
                softGlowLevel = softGlowLevel,
            ) {
                ProvideAppLanguage(appLanguage) {
                val vm: WeatherViewModel = viewModel()
                val scope = rememberCoroutineScope()
                // 方向变化已由 configChanges 原地处理，不需要跨进程保存临时页面。
                // 三星 / realme 会比小米更积极恢复任务状态；若保存 SEARCH，横向冷启动会误回城市选择页。
                // 冷启动统一回主页（横放时由 standbyActive 展示气象时钟）；快捷方式仍由 command 明确跳转。
                var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
                var cityPickedRequest by rememberSaveable { mutableIntStateOf(0) }
                val initialWhatsNew = remember { shouldShowWhatsNew() }
                var showWhatsNew by rememberSaveable { mutableStateOf(initialWhatsNew) }
                var showStyleChoiceAfterWhatsNew by rememberSaveable {
                    mutableStateOf(initialWhatsNew && shouldShowInterfaceStyleChoice())
                }
                var showInterfaceStyleChoice by rememberSaveable {
                    mutableStateOf(!initialWhatsNew && shouldShowInterfaceStyleChoice())
                }
                var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var automaticUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
                val notices = remember { UpdateNotices(this@MainActivity) }
                val statistics = remember { UsageStatistics(this@MainActivity) }
                val usageEnabled by SettingsRepository.usageStatistics.collectAsState(initial = false)
                LaunchedEffect(usageEnabled, showWhatsNew) {
                    if (usageEnabled && !showWhatsNew) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            delay(15_000)
                            statistics.register(true)
                            awaitCancellation()
                        }
                    }
                }
                val uiState by vm.uiState.collectAsState()
                val command by shortcutCommand.collectAsState()
                val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = false)
                val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
                    initial = LandscapeStandbyStyle.WEATHER_CORE,
                )
                // 横屏页点“竖屏”后，当前 Activity 会话保持竖向；重新开启横屏待机或
                // 下次冷启动后再恢复传感器判断，避免用户躺着时手机立刻又转回横屏。
                var portraitSession by remember { mutableStateOf(false) }
                val configuration = LocalConfiguration.current
                val standbyActive = landscapeStandby && !portraitSession &&
                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                // 横屏只在主页展示待机时钟；从时钟进入设置后仍保持横向，
                // 但恢复系统栏并展示完整设置，返回主页再回到待机界面。
                val standbyVisible = standbyActive && screen == AppScreen.HOME
                LaunchedEffect(availableUpdate, screen, standbyVisible, showWhatsNew, startupPromptsSuppressed, showInterfaceStyleChoice) {
                    val info = availableUpdate
                    if (info != null && screen == AppScreen.HOME && !standbyVisible && !showWhatsNew && !startupPromptsSuppressed && !showInterfaceStyleChoice) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            delay(3_000)
                            while (SystemClock.elapsedRealtime() - lastInteraction < 3_000) delay(500)
                            if (withContext(Dispatchers.IO) { notices.offer(info) }) automaticUpdate = info
                            awaitCancellation()
                        }
                    }
                }

                // 关闭横屏待机后立即回到竖屏并锁定；开启后方向决策收回 App——
                // 系统 SENSOR 阈值低（约 45–60° 就翻面），"稍微一歪就横屏"。
                // 6.1：默认锁竖屏，倾角 ≥65° 稳定 500ms 才切 SENSOR_LANDSCAPE，
                // 回竖用更宽阈值（<40°）迟滞防抖；平放（UNKNOWN）与迟滞区间保持现状；
                // 无传感器能力的老设备回退现行"直接交系统"行为。
                LaunchedEffect(landscapeStandby) {
                    if (!landscapeStandby) portraitSession = false
                }
                var landscapeTilt by remember { mutableStateOf(false) }
                val tiltGate = remember {
                    val handler = Handler(Looper.getMainLooper())
                    var pendingEntry: Runnable? = null
                    StandbyTiltGate(
                        scheduleEntry = { delayMs, action ->
                            val callback = Runnable { pendingEntry = null; action() }
                            pendingEntry = callback
                            handler.postDelayed(callback, delayMs)
                        },
                        cancelEntry = {
                            pendingEntry?.let { handler.removeCallbacks(it) }
                            pendingEntry = null
                        },
                        onLandscapeChanged = { landscapeTilt = it },
                    )
                }
                val tiltListener = remember {
                    object : OrientationEventListener(baseContext, SensorManager.SENSOR_DELAY_UI) {
                        override fun onOrientationChanged(orientation: Int) {
                            tiltGate.updateAngle(orientation)
                        }
                    }
                }
                DisposableEffect(landscapeStandby, portraitSession) {
                    val sensorCapable = tiltListener.canDetectOrientation()
                    if (landscapeStandby && !portraitSession && sensorCapable) {
                        tiltListener.enable()
                    } else {
                        tiltListener.disable()
                        tiltGate.reset()
                        landscapeTilt = false
                    }
                    onDispose {
                        tiltListener.disable()
                        tiltGate.reset()
                    }
                }
                LaunchedEffect(landscapeStandby, portraitSession, landscapeTilt) {
                    requestedOrientation = when {
                        !landscapeStandby || portraitSession -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else -> if (landscapeTilt || !tiltListener.canDetectOrientation()) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }

                // 状态栏/导航栏图标颜色随主题切换（浅色主题 → 深色图标）
                val view = LocalView.current
                SideEffect {
                    applyEdgeToEdgeSystemBars()
                    androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = isLight
                        isAppearanceLightNavigationBars = isLight
                    }
                }
                LaunchedEffect(isLight, themeMode) {
                    getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE).edit().putString("theme_mode", themeMode.key).apply()
                    persistSplashBackground(isLight)
                    // 天气娘是用户明确选择的独立图标，不参与主题联动。
                    // 深/浅图标则在 App 主题变化（含跟随系统）时同步切换。
                    val selectedIcon = SettingsRepository.appIconStyle.first()
                    if (selectedIcon != AppIconStyle.CHARACTER) {
                        val themedIcon = if (isLight) AppIconStyle.LIGHT else AppIconStyle.DARK
                        if (AppIconManager.apply(this@MainActivity, themedIcon) && selectedIcon != themedIcon) {
                            SettingsRepository.setAppIconStyle(themedIcon)
                        }
                    }
                }

                DisposableEffect(standbyVisible) {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    // 仅横屏待机需要锁屏上显示/点亮屏幕；普通竖屏天气页不能长期持有
                    // SHOW_WHEN_LOCKED，否则部分三星、realme 在任务恢复时会越过锁屏露出页面。
                    @Suppress("DEPRECATION")
                    if (standbyVisible) {
                        window.addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                    } else {
                        window.clearFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        setShowWhenLocked(standbyVisible)
                        setTurnScreenOn(standbyVisible)
                    }
                    if (standbyVisible) {
                        bars.systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } else {
                        bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose {
                        bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        @Suppress("DEPRECATION")
                        window.clearFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(false)
                            setTurnScreenOn(false)
                        }
                    }
                }

                LaunchedEffect(command.sequence) {
                    when (command.action) {
                        Intent.ACTION_MAIN -> {
                            screen = AppScreen.HOME
                            // onNewIntent can reuse an already resumed Activity, so
                            // a launcher re-entry must not rely solely on ON_RESUME.
                            vm.autoLocateIfEnabled()
                        }
                        ACTION_WIDGET_WEATHER -> {
                            screen = AppScreen.HOME
                            vm.preserveWidgetCityOnEntry()
                        }
                        ACTION_SEARCH -> screen = AppScreen.SEARCH
                        ACTION_SETTINGS -> screen = AppScreen.SETTINGS
                        ACTION_TYPHOON -> screen = if (ReleaseFeatures.typhoon) AppScreen.TYPHOON else AppScreen.HOME
                        ACTION_REFRESH -> {
                            screen = AppScreen.HOME
                            vm.refreshFromUser()
                        }
                    }
                }

                // 静默检测更新；提示由上方主页空闲和版本提醒策略决定。
                // 检测不自动下载。
                LaunchedEffect(Unit) {
                    for (attempt in 0..2) {
                        val result = if (attempt == 0) AppUpdate.check() else AppUpdate.check(refresh = true)
                        when (result) {
                            is AppUpdateCheck.Available -> {
                                availableUpdate = result.info
                                break
                            }
                            AppUpdateCheck.UpToDate -> {
                                availableUpdate = null
                                break
                            }
                            is AppUpdateCheck.Failed -> {
                                if (attempt < 2) delay(10_000)
                            }
                        }
                    }
                }

                // 常亮屏幕（设置项）
                val keepOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
                DisposableEffect(keepOn, standbyVisible) {
                    view.keepScreenOn = keepOn || standbyVisible
                    onDispose { view.keepScreenOn = false }
                }

                // 系统返回键：搜索/设置页退回主屏，而不是直接退出 App（v0.0.2）
                BackHandler(enabled = screen != AppScreen.HOME) {
                    screen = if (screen == AppScreen.ATMOSPHERE_LAB) AppScreen.SETTINGS else AppScreen.HOME
                }

                // 每次打开 / 回到前台都拉最新天气（10 分钟内同城不重复拉）
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.refresh(force = false)
                    // A fixed-city widget tap is an explicit city choice. Ordinary
                    // app entry still follows the saved location when enabled.
                    if (command.action != ACTION_WIDGET_WEATHER) vm.autoLocateIfEnabled()
                }

                val orientationState = rememberSaveableStateHolder()
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = standbyVisible,
                        transitionSpec = {
                            if (screen == AppScreen.HOME) fadeIn(tween(300)) togetherWith fadeOut(tween(180))
                            else androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        },
                        label = "weather-orientation",
                    ) { landscape ->
                        CompositionLocalProvider(LocalWeatherContinuity provides
                            if (screen == AppScreen.HOME) WeatherContinuity(this@SharedTransitionLayout, this) else null) {
                            orientationState.SaveableStateProvider(if (landscape) "landscape" else "portrait") {
                if (landscape) {
                    LandscapeStandbyScreen(
                        uiState = uiState,
                        style = landscapeStandbyStyle,
                        onRefresh = { vm.refreshFromUser() },
                        onExitLandscape = {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            portraitSession = true
                        },
                        onSettings = { screen = AppScreen.SETTINGS },
                    )
                } else {
                    // 主屏始终留在下层。进出设置/搜索只盖一层，避免拆掉 WeatherContent
                    // 后温度、图标再播一遍交错入场。
                    var lastNavigationOrigin by remember { mutableStateOf(TransformOrigin.Center) }
                    val overlayState = rememberSaveableStateHolder()
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { down ->
                                    lastNavigationOrigin = TransformOrigin(
                                        (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                        (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }) {
                        val liveGlow = com.zhisheng.weather.ui.theme.LocalVistaGlowPhase.current
                        val frozenGlow = remember { mutableStateOf(0.0) }
                        CompositionLocalProvider(com.zhisheng.weather.ui.theme.LocalVistaGlowPhase provides
                            if (screen == AppScreen.HOME) liveGlow else frozenGlow) {
                        HomeScreen(
                            viewModel = vm,
                            ambienceActive = screen == AppScreen.HOME,
                            dismissCitiesRequest = cityPickedRequest,
                            onSearchClick = { screen = AppScreen.SEARCH },
                            onSettingsClick = { screen = AppScreen.SETTINGS },
                            onHistoryClick = { screen = AppScreen.HISTORY },
                            onRadarClick = { if (ReleaseFeatures.radar) screen = AppScreen.RADAR },
                            onDailyForecastClick = { screen = AppScreen.DAILY_FORECAST },
                            onPrecipitationClick = { screen = AppScreen.PRECIPITATION },
                            onHourlyClick = { screen = AppScreen.HOURLY_DETAIL },
                            onTyphoonClick = { if (ReleaseFeatures.typhoon) screen = AppScreen.TYPHOON },
                        )
                        }
                        AnimatedVisibility(
                            visible = portraitSession && screen == AppScreen.HOME,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            label = "portrait-session-notice",
                        ) {
                            PortraitSessionNotice(onRestore = { portraitSession = false })
                        }
                        val overlayVisible = screen != AppScreen.HOME
                        var overlayScreen by remember { mutableStateOf(AppScreen.SETTINGS) }
                        var overlayWasVisible by remember { mutableStateOf(false) }
                        var overlayOrigin by remember { mutableStateOf(TransformOrigin.Center) }
                        if (overlayVisible != overlayWasVisible) {
                            if (overlayVisible) overlayOrigin = lastNavigationOrigin
                            overlayWasVisible = overlayVisible
                        }
                        // 组合期回写需带相等性护栏：状态已一致就不再写，杜绝潜在的无限重组；
                        // 不能改成 LaunchedEffect——那会让转场首帧读到旧目标，转场方向错一帧
                        if (overlayVisible && overlayScreen != screen) overlayScreen = screen
                        AnimatedVisibility(
                            visible = overlayVisible,
                            enter = if (standbyActive) androidx.compose.animation.EnterTransition.None else overlayEnter(overlayScreen, overlayOrigin),
                            exit = overlayExit(overlayScreen, overlayOrigin),
                            modifier = Modifier.fillMaxSize(),
                            label = "overlay",
                        ) {
                            AnimatedContent(
                                targetState = if (overlayScreen == AppScreen.ATMOSPHERE_LAB) AppScreen.SETTINGS else overlayScreen,
                                transitionSpec = { screenTransition(initialState, targetState) },
                                label = "overlay-stack",
                            ) { dest ->
                                overlayState.SaveableStateProvider(dest.name) {
                                when (dest) {
                                    AppScreen.HOME -> Box(Modifier.fillMaxSize())
                                    AppScreen.SETTINGS -> Box(Modifier.fillMaxSize()) {
                                    SettingsScreen(
                                        onBack = { screen = AppScreen.HOME },
                                        landscapePortraitLocked = portraitSession,
                                        onRestoreLandscapeAuto = { portraitSession = false },
                                        onLocate = { vm.locateCurrentCity() },
                                        locating = uiState.locating,
                                        locateMessage = uiState.locateMessage,
                                        onClearLocateMessage = { vm.clearLocateMessage() },
                                        activeSource = uiState.weather?.dataSource,
                                        activeSupplementSources = uiState.weather?.let { weather ->
                                            weather.blockSources.values
                                                .filter { it.isNotBlank() && it != weather.dataSource }
                                                .distinct()
                                        }.orEmpty(),
                                        activeCityName = uiState.selectedCity?.name,
                                        sourceLoading = uiState.loading,
                                        onAtmosphereLab = { if (ReleaseFeatures.weatherAtmosphere) screen = AppScreen.ATMOSPHERE_LAB },
                                        onShowWhatsNew = showNotes@ {
                                            if (!hostResumed) return@showNotes
                                            // 设置页手动重看更新说明时不重复打断用户选择界面风格。
                                            showStyleChoiceAfterWhatsNew = false
                                            startupPromptsSuppressed = false
                                            showWhatsNew = true
                                        },
                                        availableUpdate = availableUpdate,
                                    )
                                    AnimatedVisibility(
                                        visible = overlayScreen == AppScreen.ATMOSPHERE_LAB,
                                        enter = overlayEnter(AppScreen.ATMOSPHERE_LAB),
                                        exit = overlayExit(AppScreen.ATMOSPHERE_LAB),
                                    ) {
                                        if (ReleaseFeatures.weatherAtmosphere) AtmosphereLabScreen(
                                            initialLevel = uiState.prefs.ambience,
                                            onBack = { screen = AppScreen.SETTINGS },
                                        )
                                    }
                                    }
                                    AppScreen.ATMOSPHERE_LAB -> Unit
                                    AppScreen.DAILY_FORECAST -> DailyForecastScreen(
                                        city = uiState.selectedCity,
                                        days = uiState.weather?.currentAndFutureDaily(weatherPresentationTime()).orEmpty(),
                                        yesterday = uiState.weather?.yesterday,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.PRECIPITATION -> com.zhisheng.weather.ui.PrecipitationDetailScreen(
                                        data = uiState.weather ?: com.zhisheng.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.HOURLY_DETAIL -> com.zhisheng.weather.ui.HourlyDetailScreen(
                                        data = uiState.weather ?: com.zhisheng.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.HISTORY -> HistoryScreen(
                                        city = uiState.selectedCity,
                                        weather = uiState.weather,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.RADAR -> if (ReleaseFeatures.radar) RadarScreen(
                                        city = uiState.selectedCity,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.TYPHOON -> if (ReleaseFeatures.typhoon) TyphoonScreen(
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.SEARCH -> SearchScreen(
                                        onCityPicked = { city: City ->
                                            vm.addCityAndSelect(city)
                                            cityPickedRequest++
                                            screen = AppScreen.HOME
                                        },
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                }
                                }
                            }
                        }
                    }
                }
                            }
                        }
                    }
                }
                automaticUpdate?.takeIf { hostResumed && !startupPromptsSuppressed }?.let { info ->
                    AppUpdateDialog(initialInfo = info,
                        onDeclineVersion = {
                            automaticUpdate = null
                            scope.launch(Dispatchers.IO) { notices.dismiss(info) }
                        },
                        onClose = { automaticUpdate = null })
                }
                if (showWhatsNew && hostResumed && !startupPromptsSuppressed) {
                    WhatsNewDialog(
                        onClose = {
                            markWhatsNewSeen()
                            showWhatsNew = false
                            if (showStyleChoiceAfterWhatsNew) {
                                showStyleChoiceAfterWhatsNew = false
                                showInterfaceStyleChoice = true
                            }
                        },
                    )
                }
                if (ReleaseFeatures.classicTheme && showInterfaceStyleChoice && !showWhatsNew && !standbyVisible && hostResumed && !startupPromptsSuppressed) {
                    InterfaceStyleChoiceDialog(
                        currentStyle = interfaceStyle,
                        onConfirm = { style ->
                            scope.launch {
                                SettingsRepository.setInterfaceStyle(style)
                                markInterfaceStyleChoiceSeen()
                                showInterfaceStyleChoice = false
                            }
                        },
                    )
                }
                }
            }
        }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startupPromptsSuppressed = !isStartupIconEntry(intent.action,
            intent.hasCategory(Intent.CATEGORY_LAUNCHER), isTaskRoot, false,
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0)
        setIntent(intent)
        dispatchShortcut(intent)
    }

    private fun dispatchShortcut(intent: Intent?) {
        // 最近任务恢复不能重放曾经点击过的“搜索城市”快捷命令。
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val action = intent.action
        if (action == Intent.ACTION_MAIN || action == ACTION_SEARCH || action == ACTION_SETTINGS || action == ACTION_REFRESH || action == ACTION_TYPHOON || action == ACTION_WIDGET_WEATHER) {
            shortcutCommand.value = ShortcutCommand(action, ++shortcutSequence)
            setIntent(Intent(intent).setAction(Intent.ACTION_MAIN))
        }
    }

    private fun initialThemeMode(): ThemeMode {
        val prefs = getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE)
        return prefs.getString("theme_mode", null)?.let(ThemeMode::from)
            ?: if (prefs.getBoolean(KEY_SPLASH_LIGHT, true)) ThemeMode.LIGHT else ThemeMode.DARK
    }
    private fun applySplashBackground() {
        val light = when (initialThemeMode()) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawableResource(if (light) R.color.splash_light else R.color.splash_dark)
    }

    private fun persistSplashBackground(light: Boolean) {
        getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPLASH_LIGHT, light)
            .apply()
        val color = if (light) R.color.splash_light else R.color.splash_dark
        window.setBackgroundDrawableResource(color)
    }

    @Suppress("DEPRECATION")
    private fun applyEdgeToEdgeSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 禁止系统为手势区强加不透明对比底色，让天气背景与氛围层连续延伸到底部。
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun shouldShowWhatsNew(): Boolean =
        getSharedPreferences(WhatsNewPreferenceFile, MODE_PRIVATE)
            .getString(WhatsNewSeenKey, null) != WhatsNewVersion

    private fun markWhatsNewSeen() {
        getSharedPreferences(WhatsNewPreferenceFile, MODE_PRIVATE)
            .edit()
            .putString(WhatsNewSeenKey, WhatsNewVersion)
            .apply()
    }

    private fun shouldShowInterfaceStyleChoice(): Boolean =
        ReleaseFeatures.themeChoiceOnStartup &&
        getSharedPreferences(InterfaceStyleChoicePreferenceFile, MODE_PRIVATE)
            .getString(InterfaceStyleChoiceSeenKey, null) != InterfaceStyleChoiceVersion

    private fun markInterfaceStyleChoiceSeen() {
        getSharedPreferences(InterfaceStyleChoicePreferenceFile, MODE_PRIVATE)
            .edit()
            .putString(InterfaceStyleChoiceSeenKey, InterfaceStyleChoiceVersion)
            .apply()
    }

    private companion object {
        const val ACTION_WIDGET_WEATHER = "com.zhisheng.weather.action.WIDGET_WEATHER"
        const val ACTION_REFRESH = "com.zhisheng.weather.action.REFRESH"
        const val ACTION_SEARCH = "com.zhisheng.weather.action.SEARCH"
        const val ACTION_SETTINGS = "com.zhisheng.weather.action.SETTINGS"
        const val ACTION_TYPHOON = "com.zhisheng.weather.action.TYPHOON"
        const val PREFS_SPLASH = "zhisheng_splash"
        const val KEY_SPLASH_LIGHT = "light"
    }
}
// 6.1 横屏灵敏度参数：进入阈值比系统 SENSOR 高、回竖阈值更低形成迟滞，平放不触发
