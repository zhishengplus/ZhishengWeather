package com.zhisheng.weather.widget

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun WidgetFeedbackTheme(style: WidgetStyle, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (style == WidgetStyle.CLASSIC) darkColorScheme(
        primary = Color(0xFFABD1BD), onPrimary = Color(0xFF18392D), surface = Color(0xFF1D2827), onSurface = Color(0xFFE5EDE7),
        onSurfaceVariant = Color(0xFF9EAFA7), outlineVariant = Color(0xFF344340), background = Color(0xFF141B1B))
    else lightColorScheme(primary = Color(0xFF315F6D), onPrimary = Color.White, surface = Color(0xFFF7FAFA), onSurface = Color(0xFF203A43),
        onSurfaceVariant = Color(0xFF647D85), outlineVariant = Color(0xFFDCE7E9)), content = content)
}

@Composable
private fun feedbackMotion(): Boolean = remember {
    runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
}

/** Shared appearance, gentle motion and a footer that remains reachable when content scrolls. */
@Composable
private fun FeedbackDialog(kind: WidgetKind, style: WidgetStyle, title: String, body: String, success: Boolean,
                           primary: String, onDismiss: () -> Unit, onHome: () -> Unit,
                           content: @Composable () -> Unit = {}) {
    val inspection = LocalInspectionMode.current
    val motion = feedbackMotion()
    val enterMs = if (motion) 240 else 0
    val exitMs = if (motion) 160 else 0
    var visible by remember { mutableStateOf(inspection) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { visible = true }
    fun close(action: () -> Unit) {
        if (!closing) {
            closing = true; visible = false
            scope.launch { delay(exitMs.toLong()); action() }
        }
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 80).coerceAtLeast(220).dp
    Dialog(onDismissRequest = { close(onDismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply {
            setDimAmount(0f)
            decorView.systemUiVisibility = if (style == WidgetStyle.VISTA)
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        } }
        val dim by animateFloatAsState(if (visible) .38f else 0f, tween(if (closing) exitMs else enterMs), label = "widgetDialogDim")
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close(onDismiss) }
            .padding(24.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible, enter = fadeIn(tween(enterMs)) +
                scaleIn(tween(enterMs, easing = FastOutSlowInEasing), initialScale = .96f),
                exit = fadeOut(tween(exitMs)) + scaleOut(tween(exitMs), targetScale = .98f)) {
                val c = MaterialTheme.colorScheme
                Surface(Modifier.widthIn(max = 360.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(if (style == WidgetStyle.CLASSIC) 18.dp else 30.dp),
                    color = c.surface, shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .7f))) {
                    Column(Modifier.heightIn(max = maxHeight).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(c.primary.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                                    PhosphorIcon(if (success) R.drawable.ph_check else R.drawable.ph_info, null, Modifier.size(24.dp), c.primary)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(title, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                                    Text("${kind.name(style)} · ${kind.columns} × ${kind.rows}", fontSize = 12.sp, color = c.onSurfaceVariant)
                                }
                            }
                            Text(body, fontSize = 14.sp, lineHeight = 22.sp, color = c.onSurfaceVariant)
                            content()
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { close(onDismiss) }, modifier = Modifier.weight(1f)) {
                                Text(if (success) "继续编辑" else "返回编辑", fontSize = 13.sp)
                            }
                            Button(onClick = { close(onHome) }, modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(16.dp)) {
                                Text(primary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetAddedDialog(kind: WidgetKind, style: WidgetStyle, onDismiss: () -> Unit, onHome: () -> Unit) {
    FeedbackDialog(kind, style, "添加好了", "天气已经放到桌面啦，回去看看吧。以后长按小组件，就能调整位置和大小。",
        true, "查看桌面", onDismiss, onHome)
}

/** Short answer first. Device-specific steps expand only when they are needed. */
@Composable
internal fun WidgetAddHelp(state: WidgetPinState, kind: WidgetKind, style: WidgetStyle,
                           onDismiss: () -> Unit, onHome: () -> Unit,
                           onHomeSettings: () -> Unit, onAppSettings: () -> Unit) {
    var guide by remember { mutableStateOf(widgetHomeGuide(Build.MANUFACTURER)) }
    var menu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var troubleshooting by remember { mutableStateOf(false) }
    val motion = feedbackMotion()
    val title = when (state) {
        WidgetPinState.UNSUPPORTED -> "试试从桌面添加"
        WidgetPinState.REJECTED -> "桌面暂时没接收"
        WidgetPinState.UNCONFIRMED -> "还没确认添加"
        WidgetPinState.ERROR -> "这次没有完成"
        WidgetPinState.BACKGROUND -> "回来再试一下"
        WidgetPinState.CONFIG_FAILED -> "再调整一下设置"
        else -> "把天气放到桌面"
    }
    val explanation = when (state) {
        WidgetPinState.UNSUPPORTED -> "这个桌面暂不支持在 App 里直接添加。点“添加方法”，跟着三步试试吧。"
        WidgetPinState.REJECTED -> "这次请求没有被桌面接受。可以换个方式，从桌面手动添加。"
        WidgetPinState.UNCONFIRMED -> "还没收到桌面的确认。先看看有没有加上，免得重复添加。"
        WidgetPinState.ERROR -> "添加时遇到了问题，刚才的选择还在。先看看桌面，没加上时可以再试一次。"
        WidgetPinState.BACKGROUND -> "刚才切到了其他页面，还没发出添加请求。返回编辑后再点一次就好。"
        WidgetPinState.CONFIG_FAILED -> "这次设置没能生效。如果组件还在桌面，长按它重新编辑一下。"
        else -> "长按桌面空白处，也能添加天气。选好款式，留个空位就可以试试啦。"
    }
    FeedbackDialog(kind, style, title, explanation, false, if (expanded) "去桌面添加" else "去桌面看看", onDismiss, onHome) {
        val c = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = .06f))
            .clickable { expanded = !expanded }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("添加方法", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.primary, modifier = Modifier.weight(1f))
            val angle by animateFloatAsState(if (expanded) 90f else 0f, tween(if (motion) 180 else 0), label = "widgetHelpArrow")
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(18.dp).rotate(angle), c.primary)
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(if (motion) 200 else 0)) + fadeIn(tween(if (motion) 180 else 0)),
            exit = shrinkVertically(tween(if (motion) 160 else 0)) + fadeOut(tween(if (motion) 120 else 0))) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) { Text("${guide.title}  ▾", fontSize = 13.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        WidgetHomeGuide.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.title) }, onClick = { guide = item; menu = false })
                        }
                    }
                }
                guide.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(22.dp).background(c.primary.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(step, fontSize = 14.sp, lineHeight = 22.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                    }
                }
                Text(guide.note, fontSize = 12.sp, lineHeight = 18.sp, color = c.onSurfaceVariant)
                Text("留出 ${kind.columns} × ${kind.rows} 的空位。手动添加后，地点和外观需要重新选一下。",
                    fontSize = 12.sp, lineHeight = 18.sp, color = c.onSurfaceVariant)
                TextButton(onClick = { troubleshooting = !troubleshooting }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (troubleshooting) "收起其他办法" else "还是加不上？", fontSize = 12.sp)
                }
                AnimatedVisibility(troubleshooting, enter = expandVertically(tween(if (motion) 200 else 0)) + fadeIn(tween(if (motion) 180 else 0)),
                    exit = shrinkVertically(tween(if (motion) 160 else 0)) + fadeOut(tween(if (motion) 120 else 0))) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("如果桌面布局锁住了，先去“桌面设置”解锁。只有手机提示权限限制时，才需要检查应用设置，不用把所有权限都打开。",
                            fontSize = 12.sp, lineHeight = 18.sp, color = c.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onHomeSettings, modifier = Modifier.weight(1f)) { Text("桌面设置", fontSize = 12.sp) }
                            TextButton(onClick = onAppSettings, modifier = Modifier.weight(1f)) { Text("应用设置", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}
