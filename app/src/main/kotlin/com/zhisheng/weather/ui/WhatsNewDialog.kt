package com.zhisheng.weather.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista
import kotlinx.coroutines.launch

internal const val WhatsNewVersion = "0.1.5-beta10.3-public"
internal const val WhatsNewPreferenceFile = "zhisheng_whats_new"
internal const val WhatsNewSeenKey = "last_seen_version"

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WhatsNewDialogContent(onClose)
    }
}

/** Same content for the real modal and native layout previews. */
@Composable
internal fun WhatsNewDialogContent(
    onClose: () -> Unit,
    initialCategory: ReleaseNotesCategory = ReleaseNotesCategory.ALL,
    animateEntrance: Boolean = true,
) {
    val p = LocalZhishengPalette.current
    val shape = RoundedCornerShape(if (isPhosphorVista) 30.dp else 18.dp)
    val motion = remember { runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true) }
    var appeared by remember { mutableStateOf(!animateEntrance || !motion) }
    LaunchedEffect(Unit) { appeared = true }
    val entrance by animateFloatAsState(if (appeared) 1f else 0f,
        tween(if (motion && animateEntrance) 240 else 0), label = "releaseNotesEntrance")
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Box(Modifier.fillMaxSize().pointerInput(onClose) { detectTapGestures(onTap = { onClose() }) }) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center) {
        val compactHeader = largeText || maxHeight < 420.dp
        val shortWindow = maxHeight < 420.dp
        Column(Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = maxHeight)
            .graphicsLayer {
                alpha = entrance
                scaleX = .98f + .02f * entrance; scaleY = scaleX
                translationY = (1f - entrance) * 10.dp.toPx()
            }
            .clip(shape).background(p.surface)
            .border(1.dp, p.cardBorder.copy(alpha = .7f), shape)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!largeText) {
                    Text("枳生天气", color = p.textSecondary, fontSize = 13.sp, fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
                Text("BETA 10.3", color = p.cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.cyan.copy(alpha = .07f))
                        .padding(horizontal = 9.dp, vertical = 6.dp))
                if (largeText) Spacer(Modifier.weight(1f))
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                    PhosphorIcon(R.drawable.ph_x, "关闭更新说明", Modifier.size(21.dp), p.textSecondary)
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(
                    listOf(p.surface, p.cyan.copy(alpha = .035f))))) {
                    Canvas(Modifier.matchParentSize()) {
                        repeat(3) { i ->
                            val path = Path().apply {
                                moveTo(size.width * .66f + i * 22.dp.toPx(), -20.dp.toPx())
                                cubicTo(size.width * .42f, size.height * .7f,
                                    size.width * 1.03f, size.height * .36f, size.width * 1.08f, size.height * 1.3f)
                            }
                            drawPath(path, p.cyan.copy(alpha = .065f), style = Stroke(1.dp.toPx()))
                        }
                    }
                    if (shortWindow) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("更新说明", color = p.text, fontSize = 22.sp, lineHeight = 30.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.weight(1f).semantics { heading() })
                            Text("10.1 → 10.3", color = p.cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 16.dp, bottom = 22.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("更新说明", color = p.text, fontSize = 28.sp, lineHeight = 36.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(10.dp))
                            Text("10.1  →  10.3", color = p.cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            if (!compactHeader) {
                                Spacer(Modifier.height(6.dp))
                                Text("最近的变化，都在这里。", color = p.textSecondary, fontSize = 13.sp,
                                    lineHeight = 20.sp, fontFamily = FontFamily.SansSerif)
                            }
                        }
                        if (!compactHeader) {
                            Spacer(Modifier.width(10.dp))
                            WeatherIcon(WeatherCondition.PARTLY_CLOUDY, Modifier.size(76.dp).clearAndSetSemantics {})
                        }
                    }
                    }
                }
                if (category == ReleaseNotesCategory.ALL && !compactHeader) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(p.cyan.copy(alpha = if (p.isLight) .055f else .09f))
                            .padding(17.dp)) {
                            Text("这次，先看看这些", color = p.textSecondary, fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif)
                            Spacer(Modifier.height(10.dp))
                            Text("桌面小组件，更清楚了", color = p.text, fontSize = 17.sp, lineHeight = 25.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
                            Spacer(Modifier.height(5.dp))
                            Text("比例与字号一起调整。\n摘要能换行，点击更顺手。", color = p.textSecondary,
                                fontSize = 13.sp, lineHeight = 21.sp, fontFamily = FontFamily.SansSerif)
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = p.cyan.copy(alpha = .13f))
                            Spacer(Modifier.height(12.dp))
                            Text("收藏地点更稳 · 天气读数更一致", color = p.cyan, fontSize = 12.sp,
                                lineHeight = 19.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = if (shortWindow) 10.dp else 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReleaseNotesCategory.entries.forEach { item ->
                        val active = category == item
                        Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                                .background(if (active) p.text else p.cardBorder.copy(alpha = .22f))
                                .semantics { selected = active }
                                .clickable(role = Role.Tab) {
                                    category = item
                                    scope.launch { scroll.scrollTo(0) }
                                }.padding(horizontal = 13.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(item.label, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = FontFamily.SansSerif, color = if (active) p.surface else p.textSecondary)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    releaseNotesFor(category).forEach { section -> ReleaseSection(section) }
                    Text("谢谢你把每一个小问题告诉我们。", color = p.textTertiary, fontSize = 12.sp,
                        lineHeight = 19.sp, fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(bottom = 20.dp))
                }
            }
            HorizontalDivider(color = p.cardBorder.copy(alpha = .65f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = if (shortWindow) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onClose, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.text, contentColor = p.surface),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
                    Text(if (compactHeader) "知道了" else "知道了，开始使用", fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif, color = p.surface, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(20.dp), p.surface)
                }
                if (!shortWindow) {
                    Spacer(Modifier.height(10.dp))
                    Text(if (scroll.canScrollForward) (if (compactHeader) "向上滑动看更新" else "向上滑动，看看完整更新")
                        else "设置里可再次查看", fontSize = 11.sp, lineHeight = 17.sp,
                        fontFamily = FontFamily.SansSerif, color = p.textTertiary)
                }
            }
        }
    }
    }
}

@Composable
private fun ReleaseSection(section: ReleaseNotesSection) {
    val p = LocalZhishengPalette.current
    val icon = when (section.id) {
        "widgets" -> R.drawable.ph_cloud
        "cities" -> R.drawable.ph_map_pin
        "weather" -> R.drawable.ph_cloud_rain
        "atmosphere" -> R.drawable.ph_sun
        "daily" -> R.drawable.ph_clock
        else -> R.drawable.ph_arrow_right
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhosphorIcon(icon, null, Modifier.size(20.dp), p.cyan)
            Spacer(Modifier.width(9.dp))
            Text(section.title, color = p.text, fontSize = 16.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            section.notes.forEach { note ->
                Column {
                    Text(note.title, color = p.text, fontSize = 14.sp, lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(4.dp))
                    Text(note.detail, color = p.textSecondary, fontSize = 13.sp, lineHeight = 22.sp,
                        fontFamily = FontFamily.SansSerif)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = p.cardBorder.copy(alpha = .6f))
    }
}
