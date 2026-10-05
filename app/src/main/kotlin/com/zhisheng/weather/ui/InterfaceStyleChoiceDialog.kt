package com.zhisheng.weather.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary

internal const val InterfaceStyleChoicePreferenceFile = "zhisheng_interface_style_choice"
internal const val InterfaceStyleChoiceSeenKey = "last_seen_choice_version"
internal const val InterfaceStyleChoiceVersion = "0.1.5-beta6-4"

/**
 * 更新说明后的单次选择页。它只写界面风格，不触碰城市、来源、缓存或任何天气设置。
 */
@Composable
fun InterfaceStyleChoiceDialog(
    currentStyle: InterfaceStyle,
    onConfirm: (InterfaceStyle) -> Unit,
) {
    var selectedStyle by rememberSaveable(currentStyle) { mutableStateOf(currentStyle) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ZhishengBg, ZhishengSurface.copy(alpha = 0.98f)),
                    ),
                )
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "选择你喜欢的天气界面",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ZhishengText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "两种风格使用同一套天气数据和全部功能。选择后仍可在设置中切换。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(2.dp))
                StyleChoiceCard(
                    style = InterfaceStyle.PHOSPHOR_VISTA,
                    selected = selectedStyle == InterfaceStyle.PHOSPHOR_VISTA,
                    title = "澄空终端",
                    summary = "天气感更强，信息顺序更直观，适合日常快速查看",
                    onSelect = { selectedStyle = InterfaceStyle.PHOSPHOR_VISTA },
                )
                StyleChoiceCard(
                    style = InterfaceStyle.CLASSIC_TERMINAL,
                    selected = selectedStyle == InterfaceStyle.CLASSIC_TERMINAL,
                    title = "经典终端",
                    summary = "保留原有布局、磷光字符和熟悉的操作方式",
                    onSelect = { selectedStyle = InterfaceStyle.CLASSIC_TERMINAL },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(ZhishengMint, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onConfirm(selectedStyle) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "使用${selectedStyle.cn}",
                        style = MaterialTheme.typography.titleSmall,
                        color = ZhishengBg,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleChoiceCard(
    style: InterfaceStyle,
    selected: Boolean,
    title: String,
    summary: String,
    onSelect: () -> Unit,
) {
    val accent = if (style == InterfaceStyle.PHOSPHOR_VISTA) ZhishengMint else ZhishengOrange
    val shape = if (style == InterfaceStyle.PHOSPHOR_VISTA) RoundedCornerShape(16.dp) else RoundedCornerShape(2.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .background(if (selected) ZhishengCard else ZhishengSurface, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else ZhishengCardBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics { this.selected = selected }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalDensity.current.fontScale <= 1.2f && LocalConfiguration.current.screenWidthDp >= 360) {
            StylePreview(style = style, accent = accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) accent else ZhishengText,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(18.dp)
                        .border(1.dp, if (selected) accent else ZhishengTextTertiary, RoundedCornerShape(9.dp))
                        .padding(4.dp),
                ) {
                    if (selected) Box(Modifier.fillMaxSize().background(accent, RoundedCornerShape(5.dp)))
                }
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = ZhishengTextSecondary,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun StylePreview(style: InterfaceStyle, accent: Color) {
    val shape = if (style == InterfaceStyle.PHOSPHOR_VISTA) RoundedCornerShape(12.dp) else RoundedCornerShape(1.dp)
    Column(
        modifier = Modifier
            .width(94.dp)
            .height(96.dp)
            .background(ZhishengBg, shape)
            .border(1.dp, accent.copy(alpha = 0.58f), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (style == InterfaceStyle.PHOSPHOR_VISTA) "此刻" else "NOW//",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            letterSpacing = if (style == InterfaceStyle.PHOSPHOR_VISTA) 0.sp else 1.sp,
        )
        Text(
            "—°",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            color = ZhishengText,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Start,
        )
        HorizontalDivider(color = accent.copy(alpha = 0.45f), thickness = 1.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (index == 1) 8.dp else 4.dp)
                        .background(if (index == 1) accent else ZhishengCardBorder),
                )
            }
        }
    }
}
