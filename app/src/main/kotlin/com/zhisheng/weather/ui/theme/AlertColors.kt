package com.zhisheng.weather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.model.AlertLevel

/** All alert surfaces share the same national blue/yellow/orange/red color mapping. */
@Composable
fun alertLevelColor(level: AlertLevel): Color = when (level) {
    AlertLevel.RED -> ZhishengRed
    AlertLevel.ORANGE -> ZhishengOrange
    AlertLevel.YELLOW -> ZhishengWarning
    AlertLevel.BLUE -> ZhishengCyan
    AlertLevel.UNKNOWN -> ZhishengRed
}
