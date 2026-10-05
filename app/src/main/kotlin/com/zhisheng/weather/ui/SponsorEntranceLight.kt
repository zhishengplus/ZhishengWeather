package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A layered 2.2s ceremonial burst when the board opens; decorative and pointer transparent. */
@Composable
internal fun SponsorEntranceLight(progress: Float, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    Canvas(modifier.clearAndSetSemantics { }) {
        val p = progress.coerceIn(0f, 1f)
        val fade = (1f - ((p - .48f) / .52f).coerceIn(0f, 1f))
        val bloom = (p / .16f).coerceIn(0f, 1f) * fade
        val origin = Offset(size.width / 2, size.height * .26f)
        // Radiating short light trails celebrate the board without covering sponsor names.
        repeat(28) { i ->
            val angle = (i / 28.0 * 2 * PI).toFloat()
            val travel = (0.22f + p * .85f) * size.maxDimension
            val length = (8f + (i % 4) * 5f) * density * (1f - p * .6f)
            val direction = Offset(cos(angle), sin(angle))
            val head = origin + direction * travel
            val tail = head - direction * length
            // Keep the central reading column clear once the panel is settling.
            if (head.x < size.width * .12f || head.x > size.width * .88f || head.y < size.height * .12f) {
                drawLine((if (i % 3 == 0) palette.cyan else palette.orange).copy(alpha = bloom * .92f), tail, head, (1.5f + i % 3) * density)
            }
        }
        // Layered orbital rings give the opening a richer, celebratory burst while fading quickly.
        repeat(3) { ring ->
            val radius = (size.minDimension * (0.22f + ring * 0.11f) + p * size.maxDimension * 0.22f)
            rotate((p * 180f + ring * 34f) * if (ring % 2 == 0) 1f else -1f, origin) {
                drawCircle(
                    color = if (ring == 1) palette.orange.copy(alpha = bloom * 0.58f) else palette.cyan.copy(alpha = bloom * 0.46f),
                    radius = radius,
                    center = origin,
                    style = Stroke(width = (1.5f + ring) * density),
                )
            }
        }
        // Counter-rotating broken halos add depth without flashing the reading surface.
        repeat(4) { arc ->
            val radius = size.minDimension * (0.17f + arc * 0.075f) + p * size.minDimension * 0.12f
            val topLeft = origin - Offset(radius, radius)
            rotate((p * 320f + arc * 51f) * if (arc % 2 == 0) 1f else -1f, origin) {
                drawArc(
                    color = (if (arc % 2 == 0) palette.orange else palette.cyan).copy(alpha = bloom * (0.62f - arc * 0.08f)),
                    startAngle = arc * 37f,
                    sweepAngle = 54f + arc * 13f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke((1.6f + arc * 0.45f) * density),
                )
            }
        }
        repeat(18) { i ->
            val angle = (i / 18.0 * 2 * PI + p * 3.2).toFloat()
            val radius = size.minDimension * (0.18f + (i % 5) * 0.035f) + p * size.maxDimension * 0.28f
            val point = origin + Offset(cos(angle), sin(angle)) * radius
            drawCircle(
                color = if (i % 2 == 0) palette.orange.copy(alpha = bloom * 0.9f) else palette.cyan.copy(alpha = bloom * 0.9f),
                radius = (1.5f + (i % 3)) * density,
                center = point,
            )
        }
        val inset = (8f + 14f * p) * density
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        val height = (size.height - inset * 2).coerceAtLeast(0f)
        drawRect(palette.orange.copy(alpha = bloom * .62f), Offset(inset, inset), Size(width, height), style = Stroke(2f * density))
        // Four travelling corner brackets; all motion ceases when progress reaches 1.
        val corner = 32f * density * (1f + p)
        listOf(Offset(inset, inset), Offset(size.width-inset, inset), Offset(inset,size.height-inset), Offset(size.width-inset,size.height-inset)).forEachIndexed { i, c ->
            val dx = if (i % 2 == 0) 1 else -1
            val dy = if (i < 2) 1 else -1
            drawLine(palette.cyan.copy(alpha = bloom), c, c + Offset(dx*corner, 0f), 3f*density)
            drawLine(palette.cyan.copy(alpha = bloom), c, c + Offset(0f, dy*corner), 3f*density)
        }
        val scanY = size.height * (-0.08f + p * 1.16f)
        drawLine(
            palette.cyan.copy(alpha = bloom * 0.45f),
            Offset(size.width * 0.08f, scanY),
            Offset(size.width * 0.92f, scanY),
            1.5f * density,
        )
    }
}
