package com.zhisheng.weather.widget

import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** Production editor: both styles, existing-widget and pin flows, short screens and large type. */
class WidgetStudioFooterTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun glassFooterAtNormalAndShortHeights() {
        for (style in WidgetStyle.entries) for (editing in listOf(false, true)) for (short in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                screenWidth = if (short) 1600 else 1080, screenHeight = if (short) 960 else 1920,
                orientation = if (short) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
            snapshot(style, editing, "footer_${style.key}_${editing}_${short}")
        }
    }

    @Test fun largeTypeAndPendingFeedbackRemainUsable() {
        for (style in WidgetStyle.entries) for (pending in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 1920,
                fontScale = 2f, orientation = ScreenOrientation.PORTRAIT))
            snapshot(style, false, "footer_large_${style.key}_${pending}", if (pending) WidgetPinState.WAITING else WidgetPinState.IDLE)
        }
    }

    private fun snapshot(style: WidgetStyle, editing: Boolean, name: String, state: WidgetPinState = WidgetPinState.IDLE) {
        val sample = widgetPreviewSample()
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                WidgetStudio(style, if (editing) 1 else null, WidgetKind.WEEK, WidgetConfig.defaults(style),
                    listOf(sample.city), sample.city, null, NotificationSettings(), {}, {}, { _, _ -> }, sample, state)
            } }
        }
        paparazzi.snapshot(host, name)
    }
}
