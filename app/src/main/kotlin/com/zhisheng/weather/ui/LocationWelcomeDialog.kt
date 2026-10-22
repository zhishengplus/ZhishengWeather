package com.zhisheng.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.components.WeatherIcon
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel

/** One calm entrance after the opening/notes, with no permission request until a choice. */
@Composable
internal fun LocationWelcomeDialog(onSharedLocation: () -> Unit,
    onConfigure: (ProviderWizardKind) -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    WeatherOverlayDialog(onDismiss, maxWidth = 480.dp) { modifier ->
        LocationWelcomePanel(onSharedLocation, onConfigure, onManual, modifier)
    }
}

@Composable
internal fun LocationWelcomePanel(onSharedLocation: () -> Unit,
    onConfigure: (ProviderWizardKind) -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier,
    busy: Boolean = false) {
    val p = LocalZhishengPalette.current
    val reduced = rememberReducedMotion() || androidx.compose.ui.platform.LocalInspectionMode.current
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 800
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(if (reduced) 0 else 620)) }
    Column(modifier.heightIn(max = 720.dp).zhishengDialogPanel(glass = true)
        .graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * 18.dp.toPx() }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
        Row(Modifier.fillMaxWidth().graphicsLayer {
            alpha = reveal.value
            translationY = (1f - reveal.value) * 16.dp.toPx()
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("天气，从这里开始", Modifier.weight(1f), color = p.text,
                fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
            WeatherIcon(WeatherCondition.PARTLY_CLOUDY,
                Modifier.size(if (compact) 56.dp else 72.dp).clearAndSetSemantics {})
        }
        Text("选一种适合你的定位方式，之后也能在设置中更改。", color = p.textSecondary,
            fontSize = 14.sp, lineHeight = 21.sp)
        WelcomeChoice("枳生天气源", if (busy) "正在定位，请稍候…" else "无需配置 Key",
            "使用系统定位，自动解析所在地点。", true, onSharedLocation, enabled = !busy, busy = busy)
        WelcomeChoice("高德 Key 自己配置", "查看教程并填写 Web 服务 Key", "", false,
            { onConfigure(ProviderWizardKind.AMAP) }, enabled = !busy)
        WelcomeChoice("百度 Key 自己配置", "查看教程并填写 Web 服务 AK", "", false,
            { onConfigure(ProviderWizardKind.BAIDU) }, enabled = !busy)
        }
        HorizontalDivider(color = p.cardBorder.copy(alpha = .35f))
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp)) {
            TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("不使用定位，手动添加地点", color = p.textSecondary)
            }
            Text("位置服务说明可在设置中查看", color = p.textTertiary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WelcomeChoice(title: String, hint: String, description: String, primary: Boolean,
    onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false) {
    val p = LocalZhishengPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().background(if (primary) p.mint.copy(alpha = .08f) else p.card.copy(alpha = .35f), shape)
        .border(1.dp, if (primary) p.mint.copy(alpha = .24f) else p.cardBorder.copy(alpha = .4f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f), color = if (primary) p.mint else p.text,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp)
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = p.mint, strokeWidth = 2.dp)
            else if (primary) Text("推荐", Modifier.background(p.mint.copy(alpha = .10f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp), color = p.mint, fontSize = 11.sp, lineHeight = 16.sp)
            else Text("›", color = p.textTertiary, fontSize = 22.sp, lineHeight = 23.sp)
        }
        Text(hint, color = p.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        if (description.isNotEmpty()) Text(description, color = p.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
