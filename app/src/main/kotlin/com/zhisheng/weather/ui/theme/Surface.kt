package com.zhisheng.weather.ui.theme

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import android.os.Build

/** Shared by the Vista home and settings surfaces. */
val LocalHomeSurfaceStyle = compositionLocalOf { HomeSurfaceStyle.CURRENT }

/** Material dialogs and sheets cannot sample the page layer; use the same neutral glass body. */
@Composable
fun zhishengOverlayColor(): Color {
    val palette = LocalZhishengPalette.current
    if (!isPhosphorVista || LocalHomeSurfaceStyle.current != HomeSurfaceStyle.FRAGRANCE_GLASS) return palette.surface
    return if (palette.isLight) Color(0xFFE8F5FB).copy(alpha = .94f)
        else Color(0xFF1C2A38).copy(alpha = .96f)
}

private val VistaGlowPearl = Color(0xFFE5CFC2)
private val VistaGlowIris = Color(0xFFD0D9EA)
private val VistaGlowMist = Color(0xFFB5CADF)

/** Shared material; a padded backdrop pass keeps blur and lensing continuous at the rim. */
@Composable
private fun Modifier.homeGlass(shape: Shape, light: Boolean, compact: Boolean, selected: Boolean): Modifier {
    // Dialogs have a different coordinate space and must not replay the host window's layer.
    val view = androidx.compose.ui.platform.LocalView.current
    val backdrop = LocalHomeBackdrop.current?.takeIf { it.owner === view }
    val capture = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val screen = LocalConfiguration.current
    val screenSize = with(LocalDensity.current) {
        androidx.compose.ui.geometry.Size(screen.screenWidthDp.dp.toPx(), screen.screenHeightDp.dp.toPx())
    }
    val optics = remember { if (Build.VERSION.SDK_INT >= 33)
        runCatching { HomeGlassOptics() }.onFailure { android.util.Log.w("HomeGlass", "Optics unavailable", it) }.getOrNull()
        else null }
    val touch = remember { mutableStateOf(Offset(.5f, .5f)) }
    val pressed = remember { mutableStateOf(false) }
    val energy = androidx.compose.animation.core.animateFloatAsState(
        if (pressed.value) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 420f), label = "glass illumination")
    return onGloballyPositioned { origin.value = it.positionInRoot() }
        .shadow(if (compact) 5.dp else 9.dp, shape, clip = false,
            ambientColor = Color(0xFF304459).copy(alpha = if (light) .12f else .20f),
            spotColor = Color(0xFF26384A).copy(alpha = if (light) .16f else .24f))
        .clip(shape)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch.value = Offset(down.position.x / size.width.coerceAtLeast(1), down.position.y / size.height.coerceAtLeast(1))
                pressed.value = android.animation.ValueAnimator.areAnimatorsEnabled()
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Observe only. Scrolling, clickable semantics and child sliders retain input ownership.
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    } while (event.changes.any { it.pressed })
                } finally { pressed.value = false }
            }
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val radius = (outline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val blurRadius = (if (compact) 5.dp else 8.dp).toPx()
            val apron = kotlin.math.ceil(blurRadius * 3f).toInt()
            val padding = apron.toFloat()
            val captureSize = androidx.compose.ui.unit.IntSize(
                kotlin.math.ceil(size.width).toInt() + apron * 2,
                kotlin.math.ceil(size.height).toInt() + apron * 2)
            val effect = if (Build.VERSION.SDK_INT >= 33) optics?.effect(size.width, size.height,
                minOf((if (compact) 10.dp else 15.dp).toPx(), size.minDimension * .24f),
                radius, light, density, padding, blurRadius) else null
            val compatibleBlur = if (effect == null && Build.VERSION.SDK_INT >= 31)
                android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius,
                    android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
            val fallbackRim = Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (light) .75f else .36f),
                Color.White.copy(alpha = .08f),
                Color.White.copy(alpha = if (light) .50f else .24f)),
                start = Offset.Zero, end = Offset(size.width, size.height))
            onDrawWithContent {
                val offset = origin.value - (backdrop?.origin ?: Offset.Zero)
                capture.record(size = captureSize) {
                    translate(padding - offset.x, padding - offset.y) {
                        if (backdrop != null) drawLayer(backdrop.layer)
                        else drawGlassPageField(light, screenSize)
                    }
                }
                capture.renderEffect = effect ?: compatibleBlur
                translate(-padding, -padding) { drawLayer(capture) }
                if (effect == null) {
                    drawRect(if (light) Color.White.copy(alpha = .17f) else Color(0xFF13202C).copy(alpha = .23f))
                    drawOutline(outline, fallbackRim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                }
                if (selected) drawRect(Color.White.copy(alpha = if (light) .08f else .045f))
                if (energy.value > .001f) {
                    val at = Offset(touch.value.x * size.width, touch.value.y * size.height)
                    drawRect(Brush.radialGradient(listOf(
                        Color.White.copy(alpha = energy.value * if (light) .16f else .10f), Color.Transparent),
                        center = at, radius = size.minDimension.coerceAtLeast(1f) * .95f))
                }
                drawContent()
            }
        }
}

/** A floating action island uses the same sampled optics as the page, with a continuous capsule rim. */
@Composable
internal fun Modifier.zhishengGlassActionBar(light: Boolean): Modifier =
    homeGlass(RoundedCornerShape(percent = 50), light, compact = true, selected = false)

/** The same window-sized field is also the optical source for secondary pages without a sky. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassPageField(
    light: Boolean, fieldSize: androidx.compose.ui.geometry.Size,
) {
    val field = if (light) listOf(Color(0xFFC9DFED), Color(0xFFE2F0F7), Color(0xFFD3E6F1))
        else listOf(Color(0xFF172838), Color(0xFF141F2B), Color(0xFF0F1B29))
    drawRect(Brush.verticalGradient(field, endY = fieldSize.height), size = fieldSize)
}

/** One window-anchored field. Scrolling cards sample the same light as their surroundings. */
@Composable
internal fun Modifier.vistaSoftGlow(panel: Boolean = false): Modifier {
    if (!isPhosphorVista || !LocalVistaSoftGlow.current) return this
    val palette = LocalZhishengPalette.current
    val phase = LocalVistaGlowPhase.current
    val level = LocalVistaGlowLevel.current
    val natural = com.zhisheng.weather.ui.components.LocalNaturalLight.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val width = with(density) { config.screenWidthDp.dp.toPx() }.coerceAtLeast(1f)
    val height = with(density) { config.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    val strength = (if (panel) 0.32f else 1f) * (if (palette.isLight) 0.65f else 0.45f) * level.strength.coerceAtMost(1.15f)
    // Separate the changing light and the static content into display lists. A clock tick
    // must not rerecord all text, weather icons and charts beneath this modifier.
    return onGloballyPositioned { origin.value = it.positionInWindow() }.graphicsLayer().drawWithCache {
        val radius = maxOf(width, height * 0.68f)
        val brushes = listOf(
            Brush.radialGradient(listOf((natural?.top ?: VistaGlowMist).copy(alpha = 0.20f * strength), Color.Transparent),
                center = Offset(width * 0.15f, height * 0.18f), radius = radius),
            Brush.radialGradient(listOf((natural?.light ?: VistaGlowPearl).copy(alpha = 0.17f * strength), Color.Transparent),
                center = Offset(width * 0.83f, height * 0.62f), radius = radius * 0.86f),
            Brush.radialGradient(listOf((natural?.middle ?: VistaGlowIris).copy(alpha = 0.12f * strength), Color.Transparent),
                center = Offset(width * 0.25f, height * 0.92f), radius = radius * 0.8f),
        )
        onDrawBehind {
            val position = origin.value
            brushes.forEachIndexed { index, brush ->
                val drift = vistaGlowDrift(phase.value, index) * level.drift
                val shift = Offset(drift.x * width - position.x, drift.y * height - position.y)
                translate(shift.x, shift.y) {
                    drawRect(brush, topLeft = -shift, size = size)
                }
            }
        }
    }.graphicsLayer()
}

/** Page background shared by portrait, landscape, settings and feature pages. */
@Composable
fun Modifier.zhishengScreen(): Modifier {
    val palette = LocalZhishengPalette.current
    return if (isPhosphorVista) {
        if (LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS) {
            val field = if (palette.isLight) listOf(
                Color(0xFFC9DFED), Color(0xFFE2F0F7), Color(0xFFD3E6F1))
            else listOf(Color(0xFF172838), Color(0xFF141F2B), Color(0xFF0F1B29))
            background(Brush.verticalGradient(field)).vistaSoftGlow()
        } else background(palette.bg).vistaSoftGlow()
    } else {
        background(palette.bg)
    }
}

/** A style-aware panel that leaves classic beta6 square and gives Vista calmer depth. */
@Composable
fun Modifier.zhishengPanel(
    selected: Boolean = false,
    containerColor: Color? = null,
    borderColor: Color = LocalZhishengPalette.current.cardBorder,
): Modifier {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    val fill = containerColor ?: if (selected && isPhosphorVista) palette.card.copy(alpha = 0.96f) else palette.surface
    if (isPhosphorVista && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS) {
        return homeGlass(chrome.panelShape, palette.isLight, compact = false, selected = selected)
    }
    return if (isPhosphorVista) {
        shadow(
            elevation = 0.dp,
            shape = chrome.panelShape,
            clip = false,
            ambientColor = palette.bg.copy(alpha = 0.34f),
            spotColor = palette.bg.copy(alpha = 0.46f),
        )
            .clip(chrome.panelShape)
            .background(Brush.verticalGradient(listOf(
                androidx.compose.ui.graphics.lerp(fill, palette.card, 0.22f).copy(alpha = 0.88f),
                fill.copy(alpha = if (palette.isLight) 0.64f else 0.72f))), chrome.panelShape)
            .vistaSoftGlow(panel = true)
            .then(if (selected) Modifier.border(chrome.borderWidth, borderColor.copy(alpha = 0.45f), chrome.panelShape) else Modifier)
    } else {
        background(fill, RectangleShape)
            .border(chrome.borderWidth, borderColor, RectangleShape)
    }
}

@Composable
fun Modifier.zhishengCompactPanel(
    selected: Boolean = false,
    containerColor: Color? = null,
    borderColor: Color = LocalZhishengPalette.current.cardBorder,
): Modifier {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    val fill = containerColor ?: if (selected && isPhosphorVista) palette.card else palette.surface
    if (isPhosphorVista && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS) {
        return homeGlass(chrome.compactShape, palette.isLight, compact = true, selected = selected)
    }
    return if (isPhosphorVista) {
        clip(chrome.compactShape)
            .background(fill, chrome.compactShape)
            .vistaSoftGlow(panel = true)
            .border(chrome.borderWidth, borderColor.copy(alpha = borderColor.alpha * 0.5f), chrome.compactShape)
    } else {
        background(fill, RectangleShape)
            .border(chrome.borderWidth, borderColor, RectangleShape)
    }
}

@Composable
fun Modifier.zhishengDialogPanel(
    containerColor: Color? = null,
    borderColor: Color = LocalZhishengPalette.current.cardBorder,
): Modifier {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    val fill = containerColor ?: palette.surface
    if (isPhosphorVista && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS) {
        return homeGlass(chrome.dialogShape, palette.isLight, compact = false, selected = false)
    }
    return if (isPhosphorVista) {
        clip(chrome.dialogShape)
            .background(fill, chrome.dialogShape)
            .vistaSoftGlow(panel = true)
            .border(chrome.borderWidth, borderColor, chrome.dialogShape)
    } else {
        background(fill, RectangleShape)
            .border(chrome.borderWidth, borderColor, RectangleShape)
    }
}
