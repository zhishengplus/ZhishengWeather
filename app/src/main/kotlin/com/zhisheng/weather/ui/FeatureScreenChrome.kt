package com.zhisheng.weather.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.i18n.uiText
import kotlinx.coroutines.delay

@Composable
internal fun FeaturePageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = if (isPhosphorVista) 8.dp else 4.dp,
            vertical = if (isPhosphorVista) 10.dp else 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回"), tint = ZhishengText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isPhosphorVista) ZhishengText else ZhishengOrange,
                maxLines = 1,
            )
            if (!isPhosphorVista) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                    color = ZhishengTextSecondary,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun FeatureSectionTitle(index: Int, title: String, en: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val showCaption = maxWidth >= 420.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isPhosphorVista) {
            Text(
                "%02d//".format(index),
                style = MaterialTheme.typography.titleSmall,
                color = if (isPhosphorVista) ZhishengText else ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (isPhosphorVista) title else "  $title  ",
            style = if (isPhosphorVista) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = ZhishengText,
            fontWeight = if (isPhosphorVista) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (!isPhosphorVista && showCaption) {
            Text(
                en,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextSecondary,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(.65f),
            )
        }
    }
    }
}

@Composable
internal fun TerminalPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .zhishengPanel(),
    ) { content() }
}

/** 辅助功能页的短促自检动画；真实加载结束就立即离场，不人为延长等待。 */
@Composable
internal fun FeatureBootLoader(
    channel: String,
    lines: List<String>,
    status: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    val animate = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    // 历史加载进度会持续改写最后一行；不能因此从第一行重播整套序列。
    var visibleLines by remember(channel, animate) { mutableIntStateOf(if (animate) 1 else lines.size) }
    var cursorVisible by remember { mutableStateOf(true) }
    var probe by remember { mutableIntStateOf(0) }

    LaunchedEffect(channel, animate) {
        if (!animate) return@LaunchedEffect
        for (count in 2..lines.size) {
            delay(190)
            visibleLines = count
        }
    }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(140)
            probe = (probe + 1) % 13
            if (probe % 3 == 0) cursorVisible = !cursorVisible
        }
    }

    if (isPhosphorVista) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("枳生天气", style = MaterialTheme.typography.titleMedium, color = palette.orange, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(13) { index ->
                    val completed = progress?.let { index < (it.coerceIn(0f, 1f) * 13).toInt() }
                    val color = when {
                        completed == true -> palette.mint
                        progress == null && index == probe -> palette.orange
                        else -> palette.cardBorder
                    }
                    Canvas(Modifier.weight(1f).height(5.dp)) { drawRect(color) }
                }
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "ZHISHENG // $channel",
            style = MaterialTheme.typography.labelMedium,
            color = palette.orange,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(14.dp))
        lines.take(visibleLines).forEachIndexed { index, line ->
            Text(
                "> $line",
                style = MaterialTheme.typography.bodySmall,
                color = if (index == visibleLines - 1) palette.mint else palette.textTertiary,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(7.dp))
        }
        Text(
            if (cursorVisible || !animate) "█" else " ",
            style = MaterialTheme.typography.bodySmall,
            color = palette.mint,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(13) { index ->
                val completed = progress?.let { index < (it.coerceIn(0f, 1f) * 13).toInt() }
                val color = when {
                    completed == true -> palette.mint
                    progress == null && index == probe -> palette.orange
                    else -> palette.cardBorder
                }
                Canvas(Modifier.weight(1f).height(5.dp)) { drawRect(color) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    }
}
