package com.zhisheng.weather.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** A fixed title bar that softly absorbs the scrolling content underneath it. */
@Composable
internal fun Modifier.glassScrollHeader(
    backdrop: GraphicsLayer,
    backdropOrigin: Offset,
    underlay: GraphicsLayer? = null,
    underlayOrigin: Offset = Offset.Zero,
    strength: () -> Float = { 1f },
): Modifier {
    val light = LocalZhishengPalette.current.isLight
    val sample = rememberGraphicsLayer()
    val clearBackdrop = rememberGraphicsLayer()
    var headerOrigin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { headerOrigin = it.positionInRoot() }
        .drawWithCache {
            val padding = ceil(15.dp.toPx() * 3f).toInt()
            val sampleSize = IntSize(ceil(size.width).toInt() + padding * 2,
                ceil(size.height).toInt() + padding * 2)
            val blur = if (Build.VERSION.SDK_INT >= 31) {
                android.graphics.RenderEffect.createBlurEffect(15.dp.toPx(), 15.dp.toPx(),
                    android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
            } else null
            val fade = Brush.verticalGradient(
                0f to Color.White,
                .48f to Color.White.copy(alpha = .96f),
                .78f to Color.White.copy(alpha = .60f),
                1f to Color.Transparent,
            )
            val coverFade = Brush.verticalGradient(
                0f to Color.White,
                .82f to Color.White,
                .92f to Color.White.copy(alpha = .74f),
                1f to Color.Transparent,
            )
            val tint = if (light) Color(0xFFDDECF5) else Color(0xFF182836)
            val wash = Brush.verticalGradient(
                0f to tint.copy(alpha = if (underlay == null) .92f else .16f),
                .48f to tint.copy(alpha = if (underlay == null) .80f else .12f),
                .82f to tint.copy(alpha = if (underlay == null) .35f else .06f),
                1f to Color.Transparent,
            )
            val blendPaint = Paint()
            onDrawWithContent {
                val amount = strength().coerceIn(0f, 1f)
                if (blur != null && amount > 0f) {
                    val relative = headerOrigin - backdropOrigin
                    sample.record(size = sampleSize) {
                        if (underlay != null) {
                            val underlayRelative = headerOrigin - underlayOrigin
                            translate(padding - underlayRelative.x, padding - underlayRelative.y) {
                                drawLayer(underlay)
                            }
                        }
                        translate(padding - relative.x, padding - relative.y) {
                            drawLayer(backdrop)
                        }
                    }
                    sample.renderEffect = blur
                    blendPaint.alpha = amount
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height),
                        blendPaint)
                    translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sample) }
                    drawRect(fade, blendMode = BlendMode.DstIn)
                    drawContext.canvas.restore()
                }
                if (underlay != null && amount > 0f) {
                    val underlayRelative = headerOrigin - underlayOrigin
                    clearBackdrop.record(size = sampleSize) {
                        drawRect(if (light) Color(0xFFDCEBF5) else Color(0xFF152331))
                        translate(padding - underlayRelative.x, padding - underlayRelative.y) {
                            drawLayer(underlay)
                        }
                    }
                    blendPaint.alpha = amount
                    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), blendPaint)
                    translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(clearBackdrop) }
                    drawRect(coverFade, blendMode = BlendMode.DstIn)
                    drawContext.canvas.restore()
                }
                if (amount > 0f) drawRect(wash, alpha = amount)
                drawContent()
            }
        }
}
