package com.zhisheng.weather.ui.components

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista

/** Full-page details own their system bars rather than exposing the dimmed page behind. */
@Composable
internal fun WeatherDialogWindow() {
    val view = LocalView.current
    val palette = LocalZhishengPalette.current
    val light = palette.isLight
    val background = palette.bg.toArgb()
    val navigationBackground = if (isPhosphorVista &&
        LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    ) {
        (if (light) Color(0xFFD3E6F1) else Color(0xFF0F1B29)).toArgb()
    } else background
    DisposableEffect(view, light, background, navigationBackground) {
        val provider = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()
        val window = provider?.window
        if (window != null) {
            fun configureWindow() {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.attributes = window.attributes.apply {
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(ColorDrawable(navigationBackground))
            window.statusBarColor = background
            window.navigationBarColor = navigationBackground
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
            }
            configureWindow()
            // Run after Dialog's initial parameter update, which can restore inset fitting.
            val configure = Runnable { configureWindow() }
            view.post(configure)
            // Older Compose DialogLayout writes measured content dimensions to the Window
            // during layout. Keep the window full-size even when content excludes system bars.
            val observer = view.viewTreeObserver
            val keepFullScreen = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val attributes = window.attributes
                if (attributes.width != WindowManager.LayoutParams.MATCH_PARENT ||
                    attributes.height != WindowManager.LayoutParams.MATCH_PARENT) {
                    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                }
            }
            observer.addOnGlobalLayoutListener(keepFullScreen)
            onDispose {
                view.removeCallbacks(configure)
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(keepFullScreen)
            }
        } else {
            onDispose { }
        }
    }
}
