package com.zhisheng.weather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.model.AlertLevel

internal enum class AlertVisualTone { RED, ORANGE, YELLOW, BLUE, NEUTRAL }

internal fun alertVisualTone(level: AlertLevel): AlertVisualTone = when (level) {
    AlertLevel.RED -> AlertVisualTone.RED
    AlertLevel.ORANGE -> AlertVisualTone.ORANGE
    AlertLevel.YELLOW -> AlertVisualTone.YELLOW
    AlertLevel.BLUE -> AlertVisualTone.BLUE
    AlertLevel.UNKNOWN -> AlertVisualTone.NEUTRAL
}

/** All alert surfaces share the same national blue/yellow/orange/red color mapping. */
@Composable
fun alertLevelColor(level: AlertLevel): Color = when (alertVisualTone(level)) {
    AlertVisualTone.RED -> ZhishengRed
    AlertVisualTone.ORANGE -> ZhishengOrange
    AlertVisualTone.YELLOW -> ZhishengWarning
    AlertVisualTone.BLUE -> ZhishengCyan
    // 没有可靠等级时保持中性。未知不等于红色，不能把供应商缺字段、海外补充档
    // 或白色预警误报成最高级别。
    AlertVisualTone.NEUTRAL -> ZhishengTextSecondary
}
