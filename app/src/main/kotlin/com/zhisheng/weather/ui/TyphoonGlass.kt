package com.zhisheng.weather.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista
import kotlin.math.ceil

/** Map glass samples only the supplied same-window map layer; foreground content stays unfiltered. */
@Composable
internal fun Modifier.typhoonGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    compact: Boolean = false,
): Modifier {
    val palette = LocalZhishengPalette.current
    if (!isPhosphorVista) {
        return clip(RectangleShape).background(palette.surface)
            .border(1.dp, palette.cardBorder, RectangleShape)
    }
    val light = palette.isLight
    val owner = LocalView.current
    // The origin supplied by the typhoon page is also positionInRoot(). Never sample another window.
    val backdrop = LocalHomeBackdrop.current?.takeIf { it.owner === owner }
    val capture = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val optics = remember(light, compact) {
        if (Build.VERSION.SDK_INT >= 33) runCatching { TyphoonGlassOptics() }.getOrNull() else null
    }
    val readingTint = if (light) Color(0xFFF8FBFF) else Color(0xFF0A1726)
    return onGloballyPositioned { origin.value = it.positionInRoot() }
        .shadow(if (compact) 3.dp else 7.dp, shape, clip = false,
            ambientColor = Color(0xFF152B40).copy(alpha = if (light) .08f else .18f),
            spotColor = Color(0xFF10253C).copy(alpha = if (light) .11f else .22f))
        .clip(shape)
        .graphicsLayer()
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val radius = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val blurRadius = (if (compact) 4.dp else 6.dp).toPx()
            val edge = minOf((if (compact) 5.dp else 8.dp).toPx(), size.minDimension * .24f)
            // The source extends beyond the panel so blur and the refracted rim do not smear a crop edge.
            val apron = ceil(blurRadius * 3f + edge * .5f).toInt()
            val padding = apron.toFloat()
            val captureSize = IntSize(ceil(size.width).toInt().coerceAtLeast(1) + apron * 2,
                ceil(size.height).toInt().coerceAtLeast(1) + apron * 2)
            val opticalEffect = if (backdrop != null && Build.VERSION.SDK_INT >= 33) {
                runCatching { optics?.effect(size.width, size.height, edge, radius, light, density, padding, blurRadius) }.getOrNull()
            } else null
            val compatibleBlur = if (backdrop != null && opticalEffect == null && Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP).asComposeRenderEffect()
                }.getOrNull()
            } else null
            val filtered = opticalEffect ?: compatibleBlur
            val fallbackBody = Brush.verticalGradient(
                0f to readingTint.copy(alpha = if (filtered != null) .36f else .88f),
                .18f to readingTint.copy(alpha = if (filtered != null) .76f else .92f),
                .82f to readingTint.copy(alpha = if (filtered != null) .76f else .92f),
                1f to readingTint.copy(alpha = if (filtered != null) .40f else .90f),
                endY = size.height.coerceAtLeast(1f),
            )
            val fallbackRim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (light) .76f else .42f),
                    Color.White.copy(alpha = .08f), Color.White.copy(alpha = if (light) .44f else .24f)),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            onDrawWithContent {
                if (backdrop != null && filtered != null) {
                    val offset = origin.value - backdrop.origin
                    capture.record(size = captureSize) {
                        translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop.layer) }
                    }
                    capture.renderEffect = filtered
                    // A not-yet-recorded source must not expose unreadable text over the raw sea.
                    drawRect(readingTint.copy(alpha = .90f))
                    translate(-padding, -padding) { drawLayer(capture) }
                }
                if (opticalEffect == null) {
                    drawRect(fallbackBody)
                    drawOutline(outline, fallbackRim, style = Stroke(1.dp.toPx()))
                }
                // This is outside the filtered capture: type, icons and child controls remain sharp.
                drawContent()
            }
        }
}

/** One remembered GPU effect per panel; no clock, CPU image readback, or animated uniforms. */
@RequiresApi(33)
private class TyphoonGlassOptics {
    private val shader = RuntimeShader(TYPHOON_GLASS_OPTICS)

    fun effect(width: Float, height: Float, edge: Float, radius: Float, light: Boolean,
        density: Float, apron: Float, blur: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
        shader.setFloatUniform("edge", edge.coerceAtLeast(1f))
        shader.setFloatUniform("cornerRadius", radius.coerceIn(0f, minOf(width, height).coerceAtLeast(0f) * .5f))
        shader.setFloatUniform("lightMode", if (light) 1f else 0f)
        shader.setFloatUniform("pixel", density.coerceAtLeast(.1f))
        shader.setFloatUniform("apron", apron)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "scene"),
            RenderEffect.createBlurEffect(blur.coerceAtLeast(.1f), blur.coerceAtLeast(.1f), Shader.TileMode.CLAMP),
        ).asComposeRenderEffect()
    }
}

private const val TYPHOON_GLASS_OPTICS = """
uniform shader scene;
uniform float2 resolution;
uniform float edge;
uniform float cornerRadius;
uniform float lightMode;
uniform float pixel;
uniform float apron;

float glassEdgeDistance(float2 p) {
    float2 q = abs(p - resolution * .5) - (resolution * .5 - cornerRadius);
    return length(max(q, float2(0))) + min(max(q.x, q.y), 0.0) - cornerRadius;
}

half4 main(float2 xy) {
    float2 p = xy - apron;
    float depth = max(-glassEdgeDistance(p), 0.0);
    float2 g = float2(
        glassEdgeDistance(p + float2(pixel, 0)) - glassEdgeDistance(p - float2(pixel, 0)),
        glassEdgeDistance(p + float2(0, pixel)) - glassEdgeDistance(p - float2(0, pixel)));
    float2 normal = g / max(length(g), .001);
    float lip = 1.0 - smoothstep(0.0, edge, depth);
    float bend = edge * .48 * lip * lip;
    half4 sampled = scene.eval(xy - normal * bend);
    float alpha = float(sampled.a);
    float3 color = float3(sampled.rgb) / max(alpha, .001);
    float luminance = dot(color, float3(.2126, .7152, .0722));
    // The centre protects reading; the narrow rim keeps the actual map colours and refraction.
    float centre = smoothstep(edge * .22, edge, depth);
    float protection = mix(.55, .58, lightMode);
    float adaptation = mix(.24 * smoothstep(.45, .95, luminance),
        .10 * (1.0 - smoothstep(.05, .40, luminance)), lightMode);
    float3 tint = mix(float3(.025, .045, .070), float3(.975, .985, 1.0), lightMode);
    color = mix(color, tint, mix(mix(.40, .46, lightMode), protection + adaptation, centre));
    float facing = dot(normal, normalize(float2(-.58, -.82)));
    float key = pow(max(facing, 0.0), 5.0);
    float bounce = pow(max(-facing, 0.0), 6.0);
    // One hairline highlight and its soft shoulder, rather than a double painted border.
    float rim = exp(-pow((depth - .48 * pixel) / (.56 * pixel), 2.0));
    float shoulder = exp(-pow((depth - 2.0 * pixel) / (1.6 * pixel), 2.0));
    float glint = rim * (.05 + .28 * key + .12 * bounce);
    color = mix(color, float3(1.0), glint * mix(.52, .65, lightMode));
    color += float3(.86, .94, 1.0) * shoulder * key * .025;
    color *= 1.0 - shoulder * (1.0 - abs(facing)) * .018;
    // Replacing the sampled scene preserves the bent pixels; only the material edge is clearer.
    return half4(half3(clamp(color, 0.0, 1.0) * alpha), half(alpha));
}
"""
