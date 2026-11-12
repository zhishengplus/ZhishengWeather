package com.zhisheng.weather.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import java.util.WeakHashMap
import kotlin.math.abs

/** Public window/View preferences only; the OS retains user, thermal and power policy. */
internal object UiRefreshRate {
    private val voted = WeakHashMap<View, Boolean>()
    private val requestedFrameRate by lazy {
        if (Build.VERSION.SDK_INT >= 35) runCatching {
            View::class.java.getMethod("setRequestedFrameRate", Float::class.javaPrimitiveType)
        }.getOrNull() else null
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = apply(activity)
            override fun onActivityResumed(activity: Activity) = apply(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
            private fun apply(activity: Activity) {
                applyWindow(activity.window)
                activity.window.decorView.post {
                    if (!activity.isDestroyed) applyViewTree(activity.window.decorView)
                }
            }
        })
    }

    fun applyWindow(window: Window) {
        if (window.decorView.isInEditMode) return
        val display = window.decorView.display ?: window.windowManager.defaultDisplay
        val rate = refreshRate(display)
        if (window.attributes.preferredRefreshRate != rate) {
            window.attributes = window.attributes.apply { preferredRefreshRate = rate }
        }
        applyViewTree(window.decorView)
    }

    private fun refreshRate(display: Display): Float {
        val mode = display.mode
        val rates = display.supportedModes.filter {
            it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight
        }.map { it.refreshRate }
        return motionRefreshRate(Build.VERSION.SDK_INT, rates)
    }

    /** Compose DropdownMenu owns a separate sub-panel root, without a DialogWindowProvider. */
    fun applyPopupRoot(view: View) {
        if (view.isInEditMode) return
        val root = view.rootView
        val attributes = root.layoutParams as? WindowManager.LayoutParams
        if (attributes == null || !isHighRefreshPopupRoot(root.isAttachedToWindow, attributes.type)) {
            applyViewTree(view)
            return
        }
        // A popup can detach between the check and the update. Never let that interrupt dismissal.
        runCatching {
            val display = root.display ?: return@runCatching
            val manager = root.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@runCatching
            updatePopupRefreshRate(attributes, refreshRate(display)) { manager.updateViewLayout(root, it) }
        }
        applyViewTree(root)
    }

    fun applyViewTree(view: View) {
        if (view.isInEditMode) return
        // Android 15 ViewGroup frame-rate preferences do not propagate to children.
        // The method is public API 35; reflection keeps compile/target SDK and old devices unchanged.
        val setter = requestedFrameRate ?: return
        if (voted[view] != true) runCatching { setter.invoke(view, 120f) }.onSuccess { voted[view] = true }
        if (view is ViewGroup) for (index in 0 until view.childCount) applyViewTree(view.getChildAt(index))
    }
}

/** Call inside independent dialog or popup content, without changing its size, bars or styling. */
@Composable
internal fun HighRefreshDialogContent() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        fun apply() {
            if (window != null) UiRefreshRate.applyWindow(window) else UiRefreshRate.applyPopupRoot(view)
        }
        apply()
        val afterContent = Runnable { apply() }
        view.post(afterContent)
        onDispose { view.removeCallbacks(afterContent) }
    }
}

internal fun isHighRefreshPopupRoot(attached: Boolean, windowType: Int): Boolean =
    attached && windowType == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL

/** Preserve the very same params object used by Compose for later popup positioning updates. */
internal fun updatePopupRefreshRate(attributes: WindowManager.LayoutParams, rate: Float,
    updateWindow: (WindowManager.LayoutParams) -> Unit): Boolean {
    val previous = attributes.preferredRefreshRate
    if (previous == rate) return true
    attributes.preferredRefreshRate = rate
    return try {
        updateWindow(attributes)
        true
    } catch (_: Exception) {
        attributes.preferredRefreshRate = previous
        false
    }
}

internal fun motionRefreshRate(sdk: Int, supportedRates: List<Float>): Float {
    if (sdk >= 34) return 120f
    // Before API 34 the preference must be an actually supported rate.
    return supportedRates.filter { it.isFinite() && it > 0f }
        .minWithOrNull(compareBy<Float> { abs(it - 120f) }.thenByDescending { it }) ?: 0f
}
