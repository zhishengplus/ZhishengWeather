package com.zhisheng.weather

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.AppScreen
import com.zhisheng.weather.ui.SearchScreen
import com.zhisheng.weather.ui.WeatherViewModel
import com.zhisheng.weather.ui.home.HomeScreen
import com.zhisheng.weather.ui.SettingsScreen
import com.zhisheng.weather.ui.AtmosphereLabScreen
import com.zhisheng.weather.ui.overlayEnter
import com.zhisheng.weather.ui.overlayExit
import com.zhisheng.weather.ui.screenTransition
import com.zhisheng.weather.ui.LandscapeStandbyScreen
import com.zhisheng.weather.ui.HistoryScreen
import com.zhisheng.weather.ui.RadarScreen
import com.zhisheng.weather.ui.WhatsNewDialog
import com.zhisheng.weather.ui.WhatsNewPreferenceFile
import com.zhisheng.weather.ui.WhatsNewSeenKey
import com.zhisheng.weather.ui.WhatsNewVersion
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import kotlinx.coroutines.flow.MutableStateFlow

private data class ShortcutCommand(val action: String? = null, val sequence: Long = 0L)

class MainActivity : ComponentActivity() {
    private val shortcutCommand = MutableStateFlow(ShortcutCommand())
    private var shortcutSequence = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchShortcut(intent)
        applySplashBackground()
        // 部分厂商系统在新接口下仍依赖旧窗口标志；两者并用可兼容 Android 8.0 和定制 ROM。
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        enableEdgeToEdge()
        applyEdgeToEdgeSystemBars()
        setContent {
            // 主题模式（v0.0.5）：深色 / 浅色 / 跟随系统三档，切换立即生效
            val themeMode by SettingsRepository.themeMode.collectAsState(initial = ThemeMode.DARK)
            val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
            val systemDark = isSystemInDarkTheme()
            val isLight = when (themeMode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                // 跟随系统：系统深色→深色板（此前直接取 systemDark，方向反了，跟随系统会显示相反主题）
                ThemeMode.SYSTEM -> !systemDark
            }
            ZhishengWeatherTheme(isLight = isLight, accentTone = accentTone) {
                val vm: WeatherViewModel = viewModel()
                // 方向变化已由 configChanges 原地处理，不需要跨进程保存临时页面。
                // 三星 / realme 会比小米更积极恢复任务状态；若保存 SEARCH，横向冷启动会误回城市选择页。
                // 冷启动统一回主页（横放时由 standbyActive 展示气象时钟）；快捷方式仍由 command 明确跳转。
                var screen by remember { mutableStateOf(AppScreen.HOME) }
                var showWhatsNew by rememberSaveable { mutableStateOf(shouldShowWhatsNew()) }
                val uiState by vm.uiState.collectAsState()
                val command by shortcutCommand.collectAsState()
                val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = true)
                val configuration = LocalConfiguration.current
                val standbyActive = landscapeStandby && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                // 关闭横屏待机后立即回到竖屏并锁定；开启后由传感器决定竖/横屏。
                LaunchedEffect(landscapeStandby) {
                    requestedOrientation = if (landscapeStandby) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                LaunchedEffect(isLight) {
                    persistSplashBackground(isLight)
                }

                DisposableEffect(standbyActive) {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    if (standbyActive) {
                        bars.systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } else {
                        bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose { bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
                }

                LaunchedEffect(command.sequence) {
                    when (command.action) {
                        ACTION_SEARCH -> screen = AppScreen.SEARCH
                        ACTION_SETTINGS -> screen = AppScreen.SETTINGS
                        ACTION_REFRESH -> {
                            screen = AppScreen.HOME
                            vm.refresh(force = true)
                        }
                    }
                }

                // 常亮屏幕（设置项）
                val keepOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
                DisposableEffect(keepOn, standbyActive) {
                    view.keepScreenOn = keepOn || standbyActive
                    onDispose { view.keepScreenOn = false }
                }

                // 系统返回键：搜索/设置页退回主屏，而不是直接退出 App（v0.0.2）
                BackHandler(enabled = screen != AppScreen.HOME) {
                    screen = if (screen == AppScreen.ATMOSPHERE_LAB) AppScreen.SETTINGS else AppScreen.HOME
                }

                // 每次打开 / 回到前台都拉最新天气（10 分钟内同城不重复拉）
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.refresh(force = false)
                    vm.autoLocateIfEnabled()
                }

                if (standbyActive) {
                    LandscapeStandbyScreen(uiState = uiState, onRefresh = { vm.refresh() })
                } else {
                    // 主屏始终留在下层。进出设置/搜索只盖一层，避免拆掉 WeatherContent
                    // 后温度、图标再播一遍交错入场。
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(
                            viewModel = vm,
                            onSearchClick = { screen = AppScreen.SEARCH },
                            onSettingsClick = { screen = AppScreen.SETTINGS },
                            onHistoryClick = { screen = AppScreen.HISTORY },
                            onRadarClick = { screen = AppScreen.RADAR },
                        )
                        val overlayVisible = screen != AppScreen.HOME
                        var overlayScreen by remember { mutableStateOf(AppScreen.SETTINGS) }
                        if (overlayVisible) overlayScreen = screen
                        AnimatedVisibility(
                            visible = overlayVisible,
                            enter = overlayEnter(overlayScreen),
                            exit = overlayExit(overlayScreen),
                            modifier = Modifier.fillMaxSize(),
                            label = "overlay",
                        ) {
                            AnimatedContent(
                                targetState = overlayScreen,
                                transitionSpec = { screenTransition(initialState, targetState) },
                                label = "overlay-stack",
                            ) { dest ->
                                when (dest) {
                                    AppScreen.HOME -> Box(Modifier.fillMaxSize())
                                    AppScreen.SETTINGS -> SettingsScreen(
                                        onBack = { screen = AppScreen.HOME },
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
                                        onAtmosphereLab = { screen = AppScreen.ATMOSPHERE_LAB },
                                        onShowWhatsNew = { showWhatsNew = true },
                                    )
                                    AppScreen.ATMOSPHERE_LAB -> AtmosphereLabScreen(
                                        initialLevel = uiState.prefs.ambience,
                                        onBack = { screen = AppScreen.SETTINGS },
                                    )
                                    AppScreen.HISTORY -> HistoryScreen(
                                        city = uiState.selectedCity,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.RADAR -> RadarScreen(
                                        city = uiState.selectedCity,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                    AppScreen.SEARCH -> SearchScreen(
                                        onCityPicked = { city: City ->
                                            vm.addCityAndSelect(city)
                                            screen = AppScreen.HOME
                                        },
                                        onBack = { screen = AppScreen.HOME },
                                    )
                                }
                            }
                        }
                    }
                }
                if (showWhatsNew && !standbyActive) {
                    WhatsNewDialog(
                        onClose = {
                            markWhatsNewSeen()
                            showWhatsNew = false
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchShortcut(intent)
    }

    private fun dispatchShortcut(intent: Intent?) {
        val action = intent?.action
        if (action == ACTION_SEARCH || action == ACTION_SETTINGS || action == ACTION_REFRESH) {
            shortcutCommand.value = ShortcutCommand(action, ++shortcutSequence)
        }
    }

    private fun applySplashBackground() {
        val light = getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE).getBoolean(KEY_SPLASH_LIGHT, false)
        persistSplashBackground(light)
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

    private companion object {
        const val ACTION_REFRESH = "com.zhisheng.weather.action.REFRESH"
        const val ACTION_SEARCH = "com.zhisheng.weather.action.SEARCH"
        const val ACTION_SETTINGS = "com.zhisheng.weather.action.SETTINGS"
        const val PREFS_SPLASH = "zhisheng_splash"
        const val KEY_SPLASH_LIGHT = "light"
    }
}
