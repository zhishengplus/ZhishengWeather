package com.zhisheng.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One window owns the scrim and system bars; content owns its material and scrolling. */
@Composable
internal fun WeatherOverlayDialog(onDismiss: () -> Unit, maxWidth: Dp = 560.dp,
    content: @Composable (Modifier) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = true, decorFitsSystemWindows = false)) {
        WeatherDialogWindow(overlay = true)
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .38f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onDismiss))
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                // Intercept blank-space taps without adding a fake button or stealing
                // child controls/scrolling. Otherwise plain Column panels hit the scrim.
                val panelHeight = (maxHeight * .94f).coerceAtMost(700.dp)
                Box(Modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
                    content(Modifier.widthIn(max = maxWidth).fillMaxWidth().heightIn(max = panelHeight))
                }
            }
        }
    }
}
