package com.zhisheng.weather.ui.badge

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.AppLanguage
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SoftGlowLevel
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.data.badge.BadgeViewModel
import com.zhisheng.weather.i18n.ProvideAppLanguage
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opaque application Activity, never an OEM floating Dialog. Uses the same app/UID wallet. */
class BadgeActivity : ComponentActivity() {
    private var requestedAwardId by mutableStateOf<String?>(null)
    private var lightAppearance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedAwardId = awardId(intent)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(6, 18, 24)))
        var appearanceReady = false
        val firstDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (appearanceReady) window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return appearanceReady
            }
        }
        window.decorView.viewTreeObserver.addOnPreDrawListener(firstDraw)
        fillDisplay()
        lifecycleScope.launch {
            val initial = try {
                withContext(Dispatchers.IO) { BadgeAppearance.read() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { BadgeAppearance() }
            if (isFinishing || isDestroyed) return@launch
            val initialLight = initial.themeMode == ThemeMode.LIGHT || (initial.themeMode == ThemeMode.SYSTEM &&
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
            window.setBackgroundDrawable(ColorDrawable(when {
                initial.interfaceStyle == InterfaceStyle.CLASSIC_TERMINAL -> Color.rgb(5, 8, 7)
                initialLight -> Color.rgb(232, 243, 238)
                else -> Color.rgb(16, 31, 27)
            }))
            setContent {
                val themeMode by SettingsRepository.themeMode.collectAsState(initial = initial.themeMode)
                val accentTone by SettingsRepository.accentTone.collectAsState(initial = initial.accentTone)
                val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(initial = initial.interfaceStyle)
                val surfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(initial = initial.surfaceStyle)
                val softGlow by SettingsRepository.softGlow.collectAsState(initial = initial.softGlow)
                val glowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = initial.glowLevel)
                val language by SettingsRepository.appLanguage.collectAsState(initial = initial.language)
                val systemDark = isSystemInDarkTheme()
                val isLight = when (themeMode) {
                    ThemeMode.LIGHT -> true
                    ThemeMode.DARK -> false
                    ThemeMode.SYSTEM -> !systemDark
                }
                SideEffect { lightAppearance = isLight; fillDisplay() }
                ZhishengWeatherTheme(isLight = isLight, accentTone = accentTone, interfaceStyle = interfaceStyle,
                    homeSurfaceStyle = surfaceStyle, softGlow = softGlow, softGlowLevel = glowLevel) {
                    ProvideAppLanguage(language) {
                        val vm: BadgeViewModel = viewModel()
                        MyBadgesContent(onBack = { finish() }, vm = vm, celebrationGrantId = requestedAwardId,
                            onAwardPresented = { grantId ->
                                // The Intent only selects presentation. The grant itself must have
                                // been loaded and signature-verified by this Activity's wallet VM.
                                if (grantId == requestedAwardId && vm.state.value.collection.any { it.grantId == grantId }) {
                                    // The wallet VM persists acceptance; model downloads remain independent.
                                    setResult(RESULT_OK)
                                }
                            })
                    }
                }
            }
            appearanceReady = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAwardId = awardId(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fillDisplay()
    }

    override fun onResume() {
        super.onResume()
        fillDisplay()
        window.decorView.post { if (!isFinishing && !isDestroyed) fillDisplay() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            fillDisplay()
            window.decorView.post { if (!isFinishing && !isDestroyed) fillDisplay() }
        }
    }

    @Suppress("DEPRECATION")
    private fun fillDisplay() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                setFitInsetsSides(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightAppearance
            isAppearanceLightNavigationBars = lightAppearance
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun awardId(intent: Intent?): String? = intent?.getStringExtra(EXTRA_AWARD_ID)
        ?.takeIf { it.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")) }

    companion object {
        private const val EXTRA_AWARD_ID = "badge_present_verified_grant"
        internal fun intent(context: Context, celebrationGrantId: String? = null): Intent =
            Intent(context, BadgeActivity::class.java).apply {
                celebrationGrantId?.let { putExtra(EXTRA_AWARD_ID, it) }
            }
    }
}

private data class BadgeAppearance(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentTone: AccentTone = AccentTone.STANDARD,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
    val surfaceStyle: HomeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS,
    val softGlow: Boolean = true,
    val glowLevel: SoftGlowLevel = SoftGlowLevel.FLOWING,
    val language: AppLanguage = AppLanguage.CHINESE,
) {
    companion object {
        suspend fun read() = BadgeAppearance(SettingsRepository.themeMode.first(), SettingsRepository.accentTone.first(),
            SettingsRepository.interfaceStyle.first(), SettingsRepository.homeSurfaceStyle.first(), SettingsRepository.softGlow.first(),
            SettingsRepository.softGlowLevel.first(), SettingsRepository.appLanguage.first())
    }
}
