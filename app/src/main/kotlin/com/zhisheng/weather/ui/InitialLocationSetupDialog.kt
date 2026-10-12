package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel

internal enum class InitialLocationSetupStage {
    INTRO, REQUESTING_PERMISSION, LOCATING, PERMISSION_DENIED, SERVICES_OFF, FAILED
}

@Composable
internal fun InitialLocationSetupDialog(
    stage: InitialLocationSetupStage,
    failureMessage: String? = null,
    permissionNeedsSettings: Boolean = false,
    onUseLocation: () -> Unit,
    onChooseCity: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    WeatherOverlayDialog(onDismiss = onDismiss) { modifier ->
        InitialLocationSetupPanel(stage, failureMessage, permissionNeedsSettings,
            onUseLocation, onChooseCity, onOpenAppSettings, onOpenLocationSettings, onDismiss, modifier)
    }
}

/** Presentation only: the host owns permission requests, location work and city selection. */
@Composable
internal fun InitialLocationSetupPanel(
    stage: InitialLocationSetupStage,
    failureMessage: String? = null,
    permissionNeedsSettings: Boolean = false,
    onUseLocation: () -> Unit,
    onChooseCity: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    val busy = stage == InitialLocationSetupStage.REQUESTING_PERMISSION || stage == InitialLocationSetupStage.LOCATING
    val title = when (stage) {
        InitialLocationSetupStage.INTRO -> "先选一个天气地点"
        InitialLocationSetupStage.REQUESTING_PERMISSION -> "等待位置授权"
        InitialLocationSetupStage.LOCATING -> "正在获取当前位置"
        InitialLocationSetupStage.PERMISSION_DENIED -> "尚未获得位置权限"
        InitialLocationSetupStage.SERVICES_OFF -> "系统定位尚未开启"
        InitialLocationSetupStage.FAILED -> "暂时无法定位"
    }
    val explanation = when (stage) {
        InitialLocationSetupStage.INTRO -> "定位只用于查看当前城市天气，大致位置即可。也可以手动选择城市。"
        InitialLocationSetupStage.REQUESTING_PERMISSION -> "请在系统弹窗中选择是否允许位置访问，也可以手动选择城市。"
        InitialLocationSetupStage.LOCATING -> "正在查找附近的天气地点，也可以直接手动选择城市。"
        InitialLocationSetupStage.PERMISSION_DENIED -> if (permissionNeedsSettings)
            "可在应用设置中允许位置访问，也可以直接手动选择城市。"
            else "可以再次允许定位，也可以直接手动选择城市。"
        InitialLocationSetupStage.SERVICES_OFF -> "开启系统定位后再试，也可以直接手动选择城市。"
        InitialLocationSetupStage.FAILED -> failureMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: "暂时未能获取当前位置，请重试或选择城市"
    }
    val actionLabel = when (stage) {
        InitialLocationSetupStage.INTRO -> "定位当前位置"
        InitialLocationSetupStage.REQUESTING_PERMISSION -> "正在请求权限"
        InitialLocationSetupStage.LOCATING -> "正在定位…"
        InitialLocationSetupStage.PERMISSION_DENIED -> if (permissionNeedsSettings) "打开应用设置" else "再次允许定位"
        InitialLocationSetupStage.SERVICES_OFF -> "打开定位设置"
        InitialLocationSetupStage.FAILED -> "重新定位"
    }
    val action = when {
        stage == InitialLocationSetupStage.PERMISSION_DENIED && permissionNeedsSettings -> onOpenAppSettings
        stage == InitialLocationSetupStage.SERVICES_OFF -> onOpenLocationSettings
        else -> onUseLocation
    }
    BoxWithConstraints(modifier.heightIn(max = 480.dp).zhishengDialogPanel(glass = true)) {
        val compact = maxHeight < 240.dp
        val fontScale = LocalDensity.current.fontScale
        // The footer is measured before the weighted explanation. On short screens,
        // two complete scaled button labels fit alongside each other without scrolling.
        val sideBySide = maxWidth >= (240 * fontScale + 48).dp
        Column(Modifier.fillMaxWidth()) {
            key(stage, permissionNeedsSettings, failureMessage) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, color = palette.text,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp),
                            fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "稍后选择", tint = palette.textSecondary)
                        }
                    }
                    Text(explanation, color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = palette.mint, strokeWidth = 2.dp)
                }
            }
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
            if (sideBySide) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InitialLocationAction(actionLabel, action, !busy, primary = true, modifier = Modifier.weight(1f))
                    InitialLocationAction("手动选择城市", onChooseCity, primary = false, modifier = Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InitialLocationAction(actionLabel, action, !busy, primary = true, modifier = Modifier.fillMaxWidth())
                    InitialLocationAction("手动选择城市", onChooseCity, primary = false, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun InitialLocationAction(label: String, onClick: () -> Unit, enabled: Boolean = true,
    primary: Boolean, modifier: Modifier) {
    val palette = LocalZhishengPalette.current
    val shape = LocalZhishengChrome.current.compactShape
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.mint, contentColor = palette.bg,
                disabledContainerColor = palette.mint.copy(alpha = .15f), disabledContentColor = palette.textSecondary)) {
            Text(label, Modifier.fillMaxWidth(), style = labelStyle, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)) {
            Text(label, Modifier.fillMaxWidth(), style = labelStyle, textAlign = TextAlign.Center)
        }
    }
}
