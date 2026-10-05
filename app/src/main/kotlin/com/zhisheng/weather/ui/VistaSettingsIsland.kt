package com.zhisheng.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** One physical gesture owner: touch drives the lens directly, release commits the page. */
@Composable
internal fun VistaSettingsIsland(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null, backdropOrigin: Offset = Offset.Zero) {
    val labels = listOf("数据", "首页", "外观", "关于")
    val icons = listOf(R.drawable.ph_database, R.drawable.ph_sun, R.drawable.ph_sparkle, R.drawable.ph_info)
    val haptic = LocalHapticFeedback.current
    val selection by rememberUpdatedState(selected)
    val commit by rememberUpdatedState(onSelected)
    val palette = LocalZhishengPalette.current
    val materialLayer = rememberGraphicsLayer()
    var glassPosition by remember { mutableStateOf(Offset.Zero) }
    val optics = remember { if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { RefractiveGlass() }.onFailure { android.util.Log.w("RefractiveGlass", "Optics unavailable", it) }.getOrNull() else null }
    val blur = remember { if (android.os.Build.VERSION.SDK_INT >= 31)
        android.graphics.RenderEffect.createBlurEffect(12f, 12f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val itemWidth = maxWidth / labels.size
        val itemPixels = with(LocalDensity.current) { itemWidth.toPx() }.coerceAtLeast(1f)
        var touchPosition by remember(itemPixels) { mutableStateOf<Float?>(null) }
        var touchLift by remember { mutableFloatStateOf(0f) }
        var stretch by remember { mutableFloatStateOf(0f) }
        val settlingPosition = animateFloatAsState(touchPosition ?: selection.toFloat(),
            if (touchPosition != null) snap() else spring(dampingRatio = 0.76f, stiffness = 520f),
            label = "玻璃吸附")
        // Direct state while touching avoids even a one-frame animation lag.
        val position = { touchPosition ?: settlingPosition.value }
        val pressure = animateFloatAsState(if (touchPosition != null) 1f else 0f,
            spring(dampingRatio = 0.7f, stiffness = 650f), label = "玻璃按压")
        val elasticStretch = animateFloatAsState(stretch,
            if (touchPosition != null) snap() else spring(dampingRatio = 0.65f, stiffness = 480f),
            label = "玻璃形变复位")
        val lift = animateFloatAsState(touchLift,
            if (touchPosition != null) snap() else spring(dampingRatio = 0.7f, stiffness = 480f),
            label = "玻璃垂直牵引")
        val previewIndex by remember(itemPixels) { derivedStateOf { (touchPosition ?: settlingPosition.value).roundToInt().coerceIn(labels.indices) } }
        val shape = RoundedCornerShape(31.dp)
        Box(Modifier.fillMaxWidth().height(62.dp)
            .onGloballyPositioned { glassPosition = it.positionInRoot() }
            .shadow(6.dp, shape, ambientColor = Color(0xFF607A91).copy(alpha = .10f),
                spotColor = Color(0xFF425C70).copy(alpha = .08f))
            .clip(shape)
            .then(if (optics == null) Modifier.border(0.8.dp, Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (palette.isLight) 0.92f else 0.34f),
                Color.White.copy(alpha = if (palette.isLight) 0.29f else 0.09f),
                Color(0xFF91AEC3).copy(alpha = if (palette.isLight) 0.42f else 0.18f))), shape) else Modifier)
            .pointerInput(itemPixels) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var raw = down.position.x / itemPixels - 0.5f
                    var lastStop = raw.roundToInt().coerceIn(labels.indices)
                    touchPosition = resistedTabPosition(raw, labels.lastIndex)
                    touchLift = ((down.position.y - size.height / 2f) * 0.12f).coerceIn(-4.dp.toPx(), 4.dp.toPx())
                    stretch = 0.025f
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.id != down.id && it.pressed }) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            raw = change.position.x / itemPixels - 0.5f
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            touchPosition = resistedTabPosition(raw, labels.lastIndex)
                            touchLift = ((change.position.y - size.height / 2f) * 0.12f).coerceIn(-4.dp.toPx(), 4.dp.toPx())
                            stretch = (abs(tracker.calculateVelocity().x) / itemPixels * 0.015f).coerceIn(0.025f, 0.16f)
                            val stop = raw.roundToInt().coerceIn(labels.indices)
                            if (stop != lastStop) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastStop = stop
                            }
                        }
                        if (released) {
                            // Settle underneath the finger; a quick swipe must not jump an extra page.
                            val target = raw.roundToInt().coerceIn(labels.indices)
                            if (target != selection) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            commit(target)
                        }
                    } finally {
                        touchPosition = null
                        touchLift = 0f
                        stretch = 0f
                    }
                }
            }) {
            if (backdrop != null) Box(Modifier.matchParentSize().drawWithCache {
                val apron = kotlin.math.ceil(18.dp.toPx()).toInt()
                val padding = apron.toFloat()
                val layerSize = androidx.compose.ui.unit.IntSize(
                    kotlin.math.ceil(size.width).toInt() + 2 * apron,
                    kotlin.math.ceil(size.height).toInt() + 2 * apron)
                onDrawBehind {
                    val relative = glassPosition - backdropOrigin
                    materialLayer.record(size = layerSize) {
                        translate(padding-relative.x, padding-relative.y) { drawLayer(backdrop) }
                    }
                    materialLayer.renderEffect = if (android.os.Build.VERSION.SDK_INT >= 33 && optics != null)
                        optics.effect(size.width, size.height, (position()+.5f)*itemPixels, itemPixels,
                            pressure.value, elasticStretch.value, lift.value, palette.isLight, density, padding) else blur
                    translate(-padding, -padding) { drawLayer(materialLayer) }
                }
            })
            if (backdrop == null || optics == null) Box(Modifier.matchParentSize().background(
                if (palette.isLight) Color(0xFFEAF5FB).copy(alpha = .35f) else Color(0xFF18232E).copy(alpha = .65f)))
            LiquidTabLens(Modifier.width(itemWidth).fillMaxHeight().graphicsLayer {
                translationX = position() * itemPixels
                translationY = lift.value
                scaleX = 1f + pressure.value * 0.035f + elasticStretch.value
                scaleY = 1f + pressure.value * 0.025f - elasticStretch.value * 0.32f
            }.padding(horizontal = 5.dp, vertical = 5.dp), { pressure.value })
            Row(Modifier.fillMaxSize().selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    val isPreview = previewIndex == index
                    val labelColor by animateColorAsState(
                        if (isPreview) palette.text else palette.textSecondary,
                        tween(90), label = "设置分类文字$index")
                    Box(Modifier.weight(1f).fillMaxHeight()
                        // Accessibility/keyboard action remains available. Physical input is owned above.
                        .selectable(selected = selection == index, role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { commit(index) }), contentAlignment = Alignment.Center) {
                        Column(Modifier.graphicsLayer {
                            val magnification = (1f - abs(position() - index)).coerceIn(0f, 1f) * pressure.value
                            scaleX = 1f + magnification * 0.045f
                            scaleY = scaleX
                        }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            PhosphorIcon(icons[index], null, Modifier.size(20.dp), labelColor)
                            Text(label, color = labelColor, fontSize = 11.sp,
                                fontWeight = if (isPreview) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Translucent body, inset refractive rim and a broad specular highlight; no cast shadow. */
@Composable
internal fun LiquidTabLens(modifier: Modifier = Modifier, pressure: () -> Float = { 0f }) {
    val palette = LocalZhishengPalette.current
    Box(modifier.clip(RoundedCornerShape(30.dp)).drawWithCache {
        val light = palette.isLight
        val body = Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (light) 0.15f else 0.09f),
            Color.White.copy(alpha = if (light) 0.045f else 0.025f),
            Color.White.copy(alpha = if (light) 0.10f else 0.04f)),
            start = Offset(size.width * 0.15f, 0f), end = Offset(size.width * 0.9f, size.height))
        val rim = Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (light) 1f else 0.68f),
            palette.textSecondary.copy(alpha = if (light) 0.18f else 0.16f),
            Color.White.copy(alpha = if (light) 0.76f else 0.22f)),
            start = Offset(size.width * 0.25f, 0f), end = Offset(size.width * 0.75f, size.height))
        val glint = Brush.radialGradient(listOf(Color.White.copy(alpha = if (light) 0.18f else 0.07f), Color.Transparent),
            center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.65f)
        onDrawBehind {
            drawRoundRect(body, cornerRadius = CornerRadius(size.height / 2f))
            val shift = size.width * pressure() * 0.1f
            translate(left = shift) { drawRect(glint, topLeft = Offset(-shift, 0f), size = size) }
            val inset = 0.6.dp.toPx()
            drawRoundRect(rim, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(size.height / 2f), style = Stroke(0.65.dp.toPx()))
        }
    })
}
