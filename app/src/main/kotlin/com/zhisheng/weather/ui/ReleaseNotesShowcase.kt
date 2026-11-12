package com.zhisheng.weather.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.ui.home.OutfitFigure
import com.zhisheng.weather.ui.home.OutfitGarment
import com.zhisheng.weather.ui.home.outfitColors
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMono
import com.zhisheng.weather.ui.theme.isPhosphorVista
import kotlin.math.roundToInt

internal enum class ReleaseHighlight(val label: String, val title: String, val detail: String) {
    OUTFIT("穿搭", "出门前，搭一套", "看体感、风雨和未来几小时的冷暖，搭好上身、外搭、下装与鞋履。配色、款式和冷暖偏好，都能自己选。"),
    BADGES("徽章", "把初光，放进口袋", "收藏有编号的三维徽章：在列表拖动，轻触放大，转过来细看。模型还在准备时，也能先收下。"),
    TYPHOON("台风", "沿着路径，看清动向", "从首页台风卡片出发，查看历史路径、强度和风圈，也能逐点回放。这里的轨迹仅作功能示意。"),
    HISTORY("回看", "那一天，也能查天气", "除了往年同日，还能选择某一天、一段日期或整个月。多日预报也能横着看、竖着看。"),
}

/** Finite, local illustrations. No weather fetch, badge model, entitlement or navigation side effect. */
@Composable
internal fun ReleaseNotesShowcase(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    initialHighlight: ReleaseHighlight = ReleaseHighlight.OUTFIT,
    previewProgress: Float? = null,
) {
    val palette = LocalZhishengPalette.current
    val motion = rememberShowcaseMotion()
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    var highlight by rememberSaveable { mutableStateOf(initialHighlight) }
    var replay by remember { mutableIntStateOf(0) }
    val clock = remember(highlight, replay) { Animatable(0f) }
    val animateScene = animate && motion.allowed && previewProgress == null
    // Re-entering the viewport resumes the same finite run, rather than replaying it on every scroll.
    LaunchedEffect(clock, animateScene, visible, motion.foreground, previewProgress) {
        if (!animateScene) clock.snapTo(1f)
        else if (visible && motion.foreground && clock.value < 1f) {
            clock.animateTo(1f, tween((1850 * (1f - clock.value)).roundToInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }
    val progress = remember(clock, previewProgress, animateScene) {
        { previewProgress?.coerceIn(0f, 1f) ?: if (animateScene) clock.value else 1f }
    }
    val shape = RoundedCornerShape(if (isPhosphorVista) 23.dp else 12.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(palette.card)
        .border(1.dp, palette.cardBorder, shape)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ReleaseHighlight.entries.forEach { item ->
                val selected = item == highlight
                Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (selected) palette.mint.copy(alpha = .10f) else Color.Transparent)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { if (highlight != item) highlight = item }
                    .padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(item.label, color = if (selected) palette.mint else palette.textSecondary,
                        fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.SansSerif,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        val sceneHeight = if (LocalDensity.current.fontScale > 1.3f) 220.dp else 208.dp
        Box(Modifier.fillMaxWidth().height(sceneHeight)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val root = view.rootView
                visible = bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f &&
                    bounds.left < root.width && bounds.top < root.height
            }
            .clearAndSetSemantics { contentDescription = "${highlight.label}功能示意" }) {
            when (highlight) {
                ReleaseHighlight.OUTFIT -> OutfitReleaseScene(progress)
                ReleaseHighlight.BADGES -> BadgeReleaseScene(progress)
                ReleaseHighlight.TYPHOON -> TyphoonReleaseScene(progress)
                ReleaseHighlight.HISTORY -> HistoryReleaseScene(progress)
            }
        }
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 17.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(highlight.title, color = palette.text, fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() })
            Text(highlight.detail, color = palette.textSecondary, fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp, lineHeight = 21.sp)
            if (animate && motion.allowed && previewProgress == null) {
                Text("重播演示", color = palette.mint, fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp, lineHeight = 19.sp,
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "重播${highlight.label}演示") { replay++ }
                        .padding(horizontal = 8.dp, vertical = 14.dp))
            }
        }
    }
}

private data class ShowcaseMotion(val allowed: Boolean, val foreground: Boolean)

/** Shared with the dialog entrance so read-aloud navigation and system motion settings agree. */
@Composable
internal fun rememberReleaseNotesMotionEnabled(): Boolean = rememberShowcaseMotion().allowed

/** No animation polling; changes in system animation settings, read-aloud navigation or lifecycle are observed. */
@Composable
private fun rememberShowcaseMotion(): ShowcaseMotion {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    fun current() = ShowcaseMotion(
        allowed = !preview && runCatching { ValueAnimator.areAnimatorsEnabled() &&
            accessibility?.isTouchExplorationEnabled != true }.getOrDefault(false),
        foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
    )
    var state by remember(context, lifecycle, preview) { mutableStateOf(current()) }
    if (!preview) DisposableEffect(context, lifecycle, accessibility) {
        fun refresh() { state = current() }
        val setting = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val touch = AccessibilityManager.TouchExplorationStateChangeListener { refresh() }
        val life = LifecycleEventObserver { _, _ -> refresh() }
        val registered = runCatching { context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, setting) }.isSuccess
        accessibility?.addTouchExplorationStateChangeListener(touch)
        lifecycle.addObserver(life)
        refresh()
        onDispose {
            lifecycle.removeObserver(life)
            accessibility?.removeTouchExplorationStateChangeListener(touch)
            if (registered) context.contentResolver.unregisterContentObserver(setting)
        }
    }
    return state
}

private fun reveal(progress: Float, start: Float, length: Float = .28f): Float =
    ((progress - start) / length).coerceIn(0f, 1f)

@Composable
private fun OutfitReleaseScene(progress: () -> Float) {
    val palette = LocalZhishengPalette.current
    val measurer = rememberTextMeasurer()
    val gear = remember { listOf(OutfitGarment.LONG_TEE, OutfitGarment.SHELL, OutfitGarment.PANTS, OutfitGarment.SNEAKERS) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        OutfitFigure(gear, outfitColors[0], null, emptyList(), palette.isLight,
            Modifier.width(126.dp).fillMaxHeight().padding(vertical = 1.dp))
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val labels = listOf("上身", "外搭", "下装", "鞋履").map {
                measurer.measure(it, TextStyle(color = palette.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.SansSerif))
            }
            val gold = outfitColors[0].accent
            val points = listOf(
                Offset(size.width * .45f, size.height * .30f), Offset(size.width * .57f, size.height * .40f),
                Offset(size.width * .46f, size.height * .65f), Offset(size.width * .55f, size.height * .88f))
            onDrawBehind {
                labels.forEachIndexed { index, label ->
                    val alpha = reveal(progress(), .05f + index * .16f)
                    val left = index % 2 == 0
                    val labelX = if (left) 14.dp.toPx() else size.width - label.size.width - 14.dp.toPx()
                    val y = if (index < 2) size.height * .31f else size.height * .76f
                    val start = Offset(if (left) labelX + label.size.width + 6.dp.toPx() else labelX - 6.dp.toPx(), y)
                    val end = points[index]
                    drawLine(gold.copy(alpha = .7f * alpha), start, start + (end - start) * alpha, 1.dp.toPx())
                    drawCircle(gold.copy(alpha = alpha), 2.dp.toPx(), end)
                    drawText(label, topLeft = Offset(labelX, y - label.size.height / 2f), alpha = alpha)
                }
            }
        })
    }
}

@Composable
private fun BadgeReleaseScene(progress: () -> Float) {
    val palette = LocalZhishengPalette.current
    val measurer = rememberTextMeasurer()
    val largeText = LocalDensity.current.fontScale > 1.3f
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val title = measurer.measure("我的收藏", TextStyle(color = palette.text, fontSize = 12.sp, fontFamily = FontFamily.SansSerif))
        val number = measurer.measure("NO. 000001", TextStyle(color = palette.textSecondary, fontSize = 10.sp, fontFamily = ZhishengMono))
        val note = measurer.measure("功能示意", TextStyle(color = palette.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.SansSerif))
        val cx = size.width * if (largeText) .5f else .33f
        val cy = size.height * if (largeText) .30f else .46f
        val radius = minOf(size.width * .22f, size.height * if (largeText) .24f else .31f)
        val gold = Brush.linearGradient(listOf(Color(0xFFF4E4B6), Color(0xFFB59A62), Color(0xFFF3DC9E), Color(0xFF8A7248)),
            Offset(cx - radius, cy - radius), Offset(cx + radius, cy + radius))
        val face = Brush.radialGradient(listOf(Color(0xFF416875), Color(0xFF233C4C)), Offset(cx, cy - radius * .4f), radius * 1.5f)
        val labelX = size.width * .60f
        onDrawBehind {
            val p = progress()
            val arrival = reveal(p, .15f, .5f)
            val tilt = kotlin.math.sin(p * Math.PI).toFloat() * -11f
            withTransform({ rotate(tilt, Offset(cx, cy)); scale(.86f + .14f * reveal(p, 0f, .7f), 1f, Offset(cx, cy)) }) {
                drawCircle(palette.text.copy(alpha = .05f), radius + 11.dp.toPx(), Offset(cx, cy))
                drawCircle(gold, radius, Offset(cx, cy))
                drawCircle(face, radius * .88f, Offset(cx, cy))
                drawCircle(Color(0xFFEDD89D).copy(alpha = .65f), radius * .79f, Offset(cx, cy), style = Stroke(.7.dp.toPx()))
                val sun = Offset(cx + radius * .19f, cy - radius * .17f)
                drawCircle(Color(0xFFF3D79A), radius * .20f, sun)
                val cloud = Color(0xFFEDF2EB)
                drawCircle(cloud, radius * .19f, Offset(cx - radius * .22f, cy + radius * .10f))
                drawCircle(cloud, radius * .25f, Offset(cx, cy))
                drawCircle(cloud, radius * .18f, Offset(cx + radius * .24f, cy + radius * .11f))
                drawRoundRect(cloud, Offset(cx - radius * .38f, cy + radius * .08f), Size(radius * .78f, radius * .22f), CornerRadius(radius * .11f))
                repeat(3) { i -> drawLine(Color(0xFF8EC9C2), Offset(cx - radius * .20f + i * radius * .20f, cy + radius * .42f),
                    Offset(cx - radius * .23f + i * radius * .20f, cy + radius * .54f), 2.dp.toPx(), StrokeCap.Round) }
            }
            if (largeText) {
                drawText(title, topLeft = Offset((size.width - title.size.width) / 2, size.height * .57f), alpha = arrival)
                drawText(number, topLeft = Offset((size.width - number.size.width) / 2, size.height * .73f), alpha = arrival)
            } else {
                drawLine(palette.mint.copy(alpha = arrival * .45f), Offset(cx + radius + 7.dp.toPx(), cy), Offset(labelX - 7.dp.toPx(), cy), 1.dp.toPx())
                drawText(title, topLeft = Offset(labelX, cy - title.size.height - 4.dp.toPx()), alpha = arrival)
                drawText(number, topLeft = Offset(labelX, cy + 6.dp.toPx()), alpha = arrival)
            }
            drawText(note, topLeft = Offset((size.width - note.size.width) / 2f, size.height - note.size.height - 9.dp.toPx()))
        }
    })
}

@Composable
private fun TyphoonReleaseScene(progress: () -> Float) {
    val palette = LocalZhishengPalette.current
    val measurer = rememberTextMeasurer()
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val inset = 24.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * .15f, size.height * .80f)
            cubicTo(size.width * .34f, size.height * .75f, size.width * .72f, size.height * .55f, size.width * .66f, size.height * .20f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val segment = Path()
        val note = measurer.measure("路径 · 强度 · 风圈", TextStyle(color = palette.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.SansSerif))
        onDrawBehind {
            for (i in 0..6) {
                val x = inset + (size.width - inset * 2) * i / 6
                drawLine(palette.cardBorder.copy(alpha = .65f), Offset(x, 20.dp.toPx()), Offset(x, size.height - 29.dp.toPx()), .6.dp.toPx())
            }
            for (i in 0..4) {
                val y = 20.dp.toPx() + (size.height - 49.dp.toPx()) * i / 4
                drawLine(palette.cardBorder.copy(alpha = .65f), Offset(inset, y), Offset(size.width - inset, y), .6.dp.toPx())
            }
            drawPath(path, palette.mint.copy(alpha = .15f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            val fraction = .06f + .94f * reveal(progress(), 0f, .90f)
            segment.reset()
            measure.getSegment(0f, measure.length * fraction, segment, true)
            drawPath(segment, palette.mint, style = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round))
            val eye = measure.getPosition(measure.length * fraction)
            for (i in 3 downTo 1) drawCircle(palette.cyan.copy(alpha = .10f), i * 10.dp.toPx(), eye, style = Stroke(1.dp.toPx()))
            drawCircle(palette.cyan.copy(alpha = .09f), 30.dp.toPx(), eye)
            drawCircle(palette.mint, 5.dp.toPx(), eye)
            drawCircle(palette.card, 2.dp.toPx(), eye)
            for (i in 0..4) if (i / 5f < fraction) drawCircle(palette.mint, 2.5.dp.toPx(), measure.getPosition(measure.length * i / 5f))
            drawText(note, topLeft = Offset((size.width - note.size.width) / 2f, size.height - note.size.height - 6.dp.toPx()))
        }
    })
}

@Composable
private fun HistoryReleaseScene(progress: () -> Float) {
    val palette = LocalZhishengPalette.current
    val measurer = rememberTextMeasurer()
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val margin = 22.dp.toPx()
        val cell = (size.width - margin * 2) / 7
        val row = (size.height - 67.dp.toPx()) / 4
        val top = 55.dp.toPx()
        val days = (1..28).map { measurer.measure(it.toString(), TextStyle(color = palette.text,
            fontSize = 11.sp, fontFamily = ZhishengMono)) }
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日").map { measurer.measure(it,
            TextStyle(color = palette.textTertiary, fontSize = 10.sp, fontFamily = FontFamily.SansSerif)) }
        val label = measurer.measure("选择日期", TextStyle(color = palette.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.SansSerif))
        onDrawBehind {
            drawText(label, topLeft = Offset(margin, 4.dp.toPx()))
            weekdays.forEachIndexed { index, text -> drawText(text,
                topLeft = Offset(margin + cell * (index + .5f) - text.size.width / 2, 30.dp.toPx())) }
            val span = reveal(progress(), .28f, .52f)
            val y = top + row
            drawRoundRect(palette.mint.copy(alpha = .12f), Offset(margin + cell, y), Size(cell * (1 + 3 * span), row - 3.dp.toPx()), CornerRadius(8.dp.toPx()))
            drawRoundRect(palette.mint.copy(alpha = .22f), Offset(margin + cell, y), Size(cell, row - 3.dp.toPx()), CornerRadius(8.dp.toPx()))
            days.forEachIndexed { index, text ->
                drawText(text, topLeft = Offset(margin + cell * (index % 7 + .5f) - text.size.width / 2,
                    top + row * (index / 7 + .5f) - text.size.height / 2 - 1.dp.toPx()))
            }
            drawRoundRect(palette.mint.copy(alpha = .6f), Offset(margin + cell, y), Size(cell * (1 + 3 * span), row - 3.dp.toPx()), CornerRadius(8.dp.toPx()), style = Stroke(.8.dp.toPx()))
        }
    })
}
