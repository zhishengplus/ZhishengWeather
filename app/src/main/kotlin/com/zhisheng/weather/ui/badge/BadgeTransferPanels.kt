package com.zhisheng.weather.ui.badge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.zhisheng.weather.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.theme.ZhishengMono

@Composable
internal fun BadgeOperationProgress(label: String, active: Boolean, reducedMotion: Boolean) {
    val pulse = remember { Animatable(.45f) }
    LaunchedEffect(active, reducedMotion) {
        if (active && !reducedMotion) while (isActive) { pulse.animateTo(1f, tween(650)); pulse.animateTo(.35f, tween(650)) }
        else pulse.snapTo(.6f)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = pulse.value)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BadgeTransferForm(input: String, onInput: (String) -> Unit, registered: Boolean, onRegister: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("一枚徽章，一个独一无二的编号。", style = MaterialTheme.typography.titleMedium)
        Text("输入这枚徽章的恢复码，它将加入你的收藏，已有徽章都会保留。原设备下次联网同步后移除这一枚。")
        BadgeCodeField(input, onInput, label = "单枚恢复码 ZB1.…", modifier = Modifier.fillMaxWidth())
        Text("转入后旧码自动失效，请保存新的恢复码。", style = MaterialTheme.typography.bodySmall)
        if (!registered) BadgeAction(onClick = onRegister) { Text("先登记本机档案") }
    }
}

@Composable
internal fun BadgeInstanceCodeNotice(name: String, serial: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(serial, style = MaterialTheme.typography.titleMedium, fontFamily = ZhishengMono)
        Text("这份恢复码只对应这一枚。可用于换机，也可以交给你想赠予的人；对方转入后，你的其他徽章不会受到影响。")
        Text("持有码的人可以转走这枚徽章。请妥善保存，勿公开分享。每次转入成功后，旧码失效。", style = MaterialTheme.typography.bodySmall)
    }
}
