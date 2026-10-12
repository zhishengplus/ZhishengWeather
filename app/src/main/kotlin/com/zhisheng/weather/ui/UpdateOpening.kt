package com.zhisheng.weather.ui

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMono
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val UpdateOpeningDurationMs = 5000f
internal const val UpdateOpeningPreferenceFile = "zhisheng_update_opening"
internal const val UpdateOpeningHandledKey = "handled_version_code"

internal fun systemAnimationsEnabled(): Boolean =
    runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

internal fun shouldAutoPlayUpdateOpening(versionCode: Int, handledVersionCode: Int,
                                         whatsNewPending: Boolean, motionEnabled: Boolean): Boolean =
    whatsNewPending && motionEnabled && versionCode > handledVersionCode

/** "0.1.5-beta10.3-public" -> "10.3"; other names fall back to their first numeric version. */
internal fun openingVersionLabel(versionName: String?): String? {
    if (versionName.isNullOrBlank()) return null
    Regex("""beta\s*(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE).find(versionName)
        ?.let { return it.groupValues[1] }
    return Regex("""\d+(?:\.\d+)*""").find(versionName)?.value
}

internal data class UpdateOpeningSpec(
    val version: String,
    /** Set only for a genuine upgrade; the changed digits roll from this value. */
    val previousVersion: String?,
    val channel: String,
    val sectionCount: Int,
    val noteCount: Int,
)

internal fun updateOpeningSpec(versionName: String, previousNotesVersion: String?, upgrade: Boolean,
                               sections: List<ReleaseNotesSection> = WhatsNewSections): UpdateOpeningSpec {
    val version = openingVersionLabel(versionName) ?: versionName
    val previous = if (upgrade) openingVersionLabel(previousNotesVersion)?.takeIf { it != version } else null
    return UpdateOpeningSpec(
        version = version,
        previousVersion = previous,
        channel = if (versionName.contains("beta", ignoreCase = true)) "BETA" else "",
        sectionCount = sections.size,
        noteCount = sections.sumOf { it.notes.size },
    )
}

// Timeline, in milliseconds. Every visual is a pure function of time.
internal const val OpeningImpactMs = 2250f
internal const val OpeningExitMs = 4200f
internal const val OpeningHandoffMs = 4550f

internal fun openingSegment(t: Float, start: Float, end: Float): Float =
    ((t - start) / (end - start)).coerceIn(0f, 1f)

internal fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)
internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
internal fun easeOutBack(x: Float, overshoot: Float = 1.45f): Float {
    val c3 = overshoot + 1f
    return 1f + c3 * (x - 1f).pow(3) + overshoot * (x - 1f).pow(2)
}

internal fun openingExit(t: Float): Float = easeInOutCubic(openingSegment(t, OpeningExitMs, 4850f))
internal fun openingBackdropAlpha(t: Float): Float = 1f - easeInOutCubic(openingSegment(t, 4450f, UpdateOpeningDurationMs))

private class OpeningColors(
    val backdropTop: Color, val backdropBottom: Color, val glow: Color, val ring: Color,
    val particles: List<Color>, val rim: Color,
    val glassInner: Color, val glassMid: Color, val glassOuter: Color,
    val sunCore: Color, val sunEdge: Color, val halo: Color,
    val text: Color, val textSoft: Color, val accent: Color,
)

private fun openingColors(p: ZhishengPalette, vista: Boolean): OpeningColors = when {
    vista && p.isLight -> OpeningColors(
        backdropTop = Color(0xFFEAF2F6), backdropBottom = Color(0xFFF8FAFB), glow = Color(0xFFBFE0EA),
        ring = Color(0xFF7FB3C4), particles = listOf(Color(0xFF4F97AD), Color(0xFF86B6D6), Color(0xFFD49A52)),
        rim = Color(0xFF3B8497), glassInner = Color(0xFF3B879B), glassMid = Color(0xFF78B4C3),
        glassOuter = Color(0xFFD5EDF2), sunCore = Color(0xFFFFF4D6), sunEdge = Color(0xFFF1C477),
        halo = Color(0xFFF5D7A0), text = p.text, textSoft = p.textSecondary, accent = Color(0xFFB4712A),
    )
    vista -> OpeningColors(
        backdropTop = Color(0xFF0A0E15), backdropBottom = Color(0xFF0F1722), glow = Color(0xFF1D4B5B),
        ring = Color(0xFF6FA8B9), particles = listOf(Color(0xFF7CC2D2), Color(0xFF9CC3EA), Color(0xFFE8B767)),
        rim = Color(0xFFA6D7E1), glassInner = Color(0xFF2C6C7E), glassMid = Color(0xFF5396A7),
        glassOuter = Color(0xFFA4D3DC), sunCore = Color(0xFFFFECC4), sunEdge = Color(0xFFE6B263),
        halo = Color(0xFF8C6A38), text = p.text, textSoft = p.textSecondary, accent = Color(0xFFF2B56B),
    )
    else -> OpeningColors(
        backdropTop = p.bg, backdropBottom = p.bg, glow = Color.Transparent,
        ring = p.cyan, particles = listOf(p.mint, p.cyan, p.orange), rim = p.cyan,
        glassInner = p.cyan, glassMid = p.cyan, glassOuter = p.cyan,
        sunCore = p.orange, sunEdge = p.orange, halo = Color.Transparent,
        text = p.text, textSoft = p.textSecondary, accent = p.orange,
    )
}

private class OpeningParticle(
    val angle: Float, val distance: Float, val arrive: Float, val travel: Float,
    val swirl: Float, val tone: Int, val width: Float,
)

// Deterministic, so every run and every rendered review frame is identical.
private val OpeningParticles: List<OpeningParticle> = run {
    var seed = 0x2F6E2B1L
    fun next(): Float {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (seed ushr 40).toFloat() / (1L shl 24).toFloat()
    }
    List(64) { index ->
        val travel = 650f + next() * 450f
        OpeningParticle(
            angle = (index / 64f) * 2f * PI.toFloat() + next() * 0.35f,
            distance = 0.62f + next() * 0.30f,
            arrive = 800f + next() * 800f,
            travel = travel,
            swirl = 0.7f + next() * 0.9f,
            tone = when { next() < 0.18f -> 2; next() < 0.5f -> 1; else -> 0 },
            width = 1.1f + next() * 1.2f,
        )
    }
}

/** Glyph box height in units of its width. */
internal const val OpeningGlyphAspect = 0.80f

private class OpeningGlyph(val cloud: Path, val sunCenter: Offset, val sunRadius: Float, val width: Float)

private fun openingGlyph(u: Float): OpeningGlyph {
    val body = Path().apply {
        addRoundRect(RoundRect(0.04f * u, 0.42f * u, 0.96f * u, 0.76f * u, CornerRadius(0.17f * u)))
    }
    fun circle(x: Float, y: Float, r: Float) = Path().apply { addOval(Rect(Offset(x * u, y * u), r * u)) }
    var cloud = body
    for (bump in listOf(circle(0.40f, 0.42f, 0.25f), circle(0.68f, 0.48f, 0.17f), circle(0.21f, 0.53f, 0.13f))) {
        cloud = Path().apply { op(cloud, bump, PathOperation.Union) }
    }
    return OpeningGlyph(cloud, Offset(0.735f * u, 0.25f * u), 0.165f * u, u)
}

private class OpeningTrace(glyph: OpeningGlyph) {
    val measure = PathMeasure().apply { setPath(glyph.cloud, false) }
    val length = measure.length
    val segment = Path()
}

private fun DrawScope.drawOpeningScene(
    t: Float, glyphOrigin: Offset, glyph: OpeningGlyph, trace: OpeningTrace,
    c: OpeningColors, vista: Boolean,
) {
    val u = glyph.width
    val dp = density
    val center = glyphOrigin + Offset(0.5f * u, 0.50f * u)
    val exit = openingExit(t)
    val fade = 1f - exit

    drawRect(Brush.verticalGradient(listOf(c.backdropTop, c.backdropBottom)), alpha = openingBackdropAlpha(t))

    if (vista) {
        val build = easeOutCubic(openingSegment(t, 300f, 1500f)) * 0.55f
        val pulse = if (t > OpeningImpactMs) 0.45f * exp(-(t - OpeningImpactMs) / 380f) else 0f
        val glowAlpha = ((build + pulse) * fade).coerceIn(0f, 1f)
        if (glowAlpha > 0.001f) drawCircle(
            Brush.radialGradient(listOf(c.glow.copy(alpha = glowAlpha), Color.Transparent), center,
                u * (0.95f + 0.4f * pulse)), radius = u * (0.95f + 0.4f * pulse), center = center)
    } else {
        val gridAlpha = 0.07f * easeOutCubic(openingSegment(t, 0f, 700f)) * fade
        val step = 22f * dp
        var x = (center.x % step)
        while (x < size.width) { drawLine(c.ring.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, size.height)); x += step }
        var y = (center.y % step)
        while (y < size.height) { drawLine(c.ring.copy(alpha = gridAlpha), Offset(0f, y), Offset(size.width, y)); y += step }
        val scan = openingSegment(t, 0f, 2200f)
        if (scan in 0.001f..0.999f) drawLine(c.ring.copy(alpha = 0.22f * fade), Offset(0f, size.height * scan),
            Offset(size.width, size.height * scan), strokeWidth = 1.5f * dp)
    }

    // A seed of light, then the converging data streams.
    val seed = easeOutCubic(openingSegment(t, 0f, 520f)) * (1f - openingSegment(t, 1250f, 1750f))
    if (seed > 0.001f) {
        if (vista) drawCircle(Brush.radialGradient(listOf(c.sunCore.copy(alpha = seed), c.sunEdge.copy(alpha = 0f)),
            center, 14f * dp), radius = 14f * dp, center = center)
        else drawRect(c.accent.copy(alpha = seed), center - Offset(2.5f * dp, 2.5f * dp), Size(5f * dp, 5f * dp))
    }

    val ringRadius = 0.64f * u
    val reach = hypot(size.width, size.height)
    var arrived = 0
    for (p in OpeningParticles) {
        val x = openingSegment(t, p.arrive - p.travel, p.arrive)
        if (x >= 1f) arrived++
        if (x <= 0f) continue
        val color = c.particles[p.tone]
        fun at(progress: Float): Offset {
            val e = easeOutCubic(progress)
            val r = ringRadius + (p.distance * reach - ringRadius) * (1f - e)
            val a = p.angle + p.swirl * (1f - e)
            return center + Offset(cos(a) * r, sin(a) * r)
        }
        if (x < 1f) {
            val head = at(x)
            if (vista) {
                var prev = head
                for (k in 1..4) {
                    val tail = at((x - 0.035f * k).coerceAtLeast(0f))
                    drawLine(color.copy(alpha = (0.9f - k * 0.2f) * fade), prev, tail,
                        strokeWidth = p.width * dp, cap = StrokeCap.Round)
                    prev = tail
                }
            } else {
                val s = p.width * 1.6f * dp
                drawRect(color.copy(alpha = 0.9f * fade), head - Offset(s / 2f, s / 2f), Size(s, s))
            }
        } else {
            val settle = 1f - openingSegment(t, p.arrive, p.arrive + 320f)
            if (settle > 0f) drawCircle(color.copy(alpha = settle * 0.8f * fade), radius = p.width * 1.4f * dp,
                center = at(1f))
        }
    }

    val ringFade = 1f - openingSegment(t, 1550f, 2150f)
    val ringAlpha = (arrived / OpeningParticles.size.toFloat()) * ringFade * 0.75f * fade
    if (ringAlpha > 0.001f) {
        val r = ringRadius * (1f - 0.12f * openingSegment(t, 1550f, 2150f))
        drawCircle(c.ring.copy(alpha = ringAlpha), radius = r, center = center, style = Stroke(1f * dp))
        val spin = t * 0.07f
        for (k in 0 until 3) drawArc(c.ring.copy(alpha = ringAlpha), spin + k * 120f, 22f, false,
            topLeft = center - Offset(r + 6f * dp, r + 6f * dp), size = Size((r + 6f * dp) * 2f, (r + 6f * dp) * 2f),
            style = Stroke(2f * dp, cap = StrokeCap.Round))
    }

    // Ripple from the moment the mark is complete.
    for ((delay, strength) in listOf(0f to 0.38f, 150f to 0.2f)) {
        val x = openingSegment(t, OpeningImpactMs + delay, OpeningImpactMs + delay + 1100f)
        if (x <= 0f || x >= 1f) continue
        val r = 0.55f * u + (max(size.width, size.height) * 0.72f - 0.55f * u) * easeOutCubic(x)
        drawCircle(c.ring.copy(alpha = strength * (1f - x) * fade), radius = r, center = center,
            style = Stroke((2f - 1.5f * x) * dp))
    }

    withTransform({
        scale(1f + 0.035f * exit, 1f + 0.035f * exit, Offset(size.width / 2f, size.height / 2f))
        translate(glyphOrigin.x, glyphOrigin.y)
    }) { drawOpeningGlyph(t, glyph, trace, c, vista, fade) }
}

private fun DrawScope.drawOpeningGlyph(
    t: Float, glyph: OpeningGlyph, trace: OpeningTrace, c: OpeningColors, vista: Boolean, fade: Float,
) {
    val u = glyph.width
    val dp = density

    val sunIn = openingSegment(t, 1550f, 2350f)
    if (sunIn > 0f) {
        val r = glyph.sunRadius * (0.6f + 0.4f * easeOutBack(sunIn))
        val a = easeOutCubic(sunIn) * fade
        if (vista) {
            drawCircle(Brush.radialGradient(listOf(c.halo.copy(alpha = 0.55f * a), Color.Transparent),
                glyph.sunCenter, r * 2.4f), radius = r * 2.4f, center = glyph.sunCenter)
            drawCircle(Brush.radialGradient(listOf(c.sunCore, c.sunEdge),
                glyph.sunCenter - Offset(r * 0.3f, r * 0.35f), r * 1.35f),
                radius = r, center = glyph.sunCenter, alpha = a)
        } else {
            drawCircle(c.sunCore.copy(alpha = a), radius = r, center = glyph.sunCenter, style = Stroke(1.6f * dp))
            for (k in 0 until 8) {
                val angle = k * PI.toFloat() / 4f
                val inner = r + 4f * dp
                val outer = inner + 5f * dp * easeOutCubic(openingSegment(t, 1900f + k * 40f, 2400f + k * 40f))
                drawLine(c.sunCore.copy(alpha = a), glyph.sunCenter + Offset(cos(angle) * inner, sin(angle) * inner),
                    glyph.sunCenter + Offset(cos(angle) * outer, sin(angle) * outer), strokeWidth = 1.4f * dp)
            }
        }
    }

    val fill = easeOutCubic(openingSegment(t, 2050f, 2750f))
    if (fill > 0f) {
        if (vista) {
            drawPath(glyph.cloud, Brush.radialGradient(listOf(c.glassInner, c.glassMid, c.glassOuter),
                Offset(0.48f * u, 0.60f * u), 0.62f * u), alpha = 0.94f * fill * fade)
            drawPath(glyph.cloud, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                0.17f * u, 0.70f * u), alpha = fill * fade, style = Stroke(1.6f * dp))
            clipPath(glyph.cloud) {
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                    0.47f * u, 0.57f * u), Offset(0.12f * u, 0.47f * u), Size(0.26f * u, 0.09f * u),
                    alpha = fill * fade)
                drawPath(glyph.cloud, Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f)),
                    0.62f * u, 0.78f * u), alpha = fill * fade)
            }
        } else {
            clipPath(glyph.cloud) {
                val step = 3f * dp
                var y = 0.16f * u
                while (y < 0.78f * u) {
                    drawLine(c.rim.copy(alpha = 0.28f * fill * fade), Offset(0f, y), Offset(u, y))
                    y += step
                }
            }
        }
    }

    val traced = easeInOutCubic(openingSegment(t, 1350f, 2250f))
    if (traced > 0f) {
        trace.segment.reset()
        trace.measure.getSegment(0f, trace.length * traced, trace.segment, true)
        drawPath(trace.segment, c.rim.copy(alpha = (1f - 0.55f * fill) * fade),
            style = Stroke(1.8f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (traced < 1f) {
            val head = trace.measure.getPosition(trace.length * traced)
            drawCircle(c.sunCore.copy(alpha = fade), radius = 2.6f * dp, center = head)
            if (vista) drawCircle(Brush.radialGradient(listOf(c.sunEdge.copy(alpha = 0.6f * fade), Color.Transparent),
                head, 12f * dp), radius = 12f * dp, center = head)
        }
    }

    // A single pass of light across the finished mark.
    val sweep = openingSegment(t, 3550f, 4250f)
    if (sweep > 0f && sweep < 1f) clipPath(glyph.cloud) {
        val x = -0.4f * u + 1.8f * u * easeInOutCubic(sweep)
        val band = if (vista) Color.White.copy(alpha = 0.5f * fade) else c.rim.copy(alpha = 0.6f * fade)
        drawRect(Brush.linearGradient(listOf(Color.Transparent, band, Color.Transparent),
            Offset(x - 0.18f * u, 0.1f * u), Offset(x + 0.18f * u, 0.8f * u)))
    }
}

/** One frame of the opening at [time]; the live overlay and the review renderer share it. */
@Composable
internal fun UpdateOpeningStage(spec: UpdateOpeningSpec, time: () -> Float, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val vista = isPhosphorVista
    val colors = remember(palette, vista) { openingColors(palette, vista) }
    val density = LocalDensity.current
    var glyphOrigin by remember { mutableStateOf(Offset.Unspecified) }
    // Opening type is drawn at fixed physical sizes: the release notes that follow honour font scale.
    fun fixed(value: Float) = with(density) { value.dp.toSp() }
    val serif = if (vista) FontFamily.SansSerif else ZhishengMono
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        val gap = if (compact) 0.55f else 1f
        val glyphWidth = minOf(maxWidth * 0.46f, maxHeight * (if (compact) 0.24f else 0.28f) / OpeningGlyphAspect, 232.dp)
        val glyphPx = with(density) { glyphWidth.toPx() }
        val glyph = remember(glyphPx) { openingGlyph(glyphPx) }
        val trace = remember(glyph) { OpeningTrace(glyph) }
        Canvas(Modifier.fillMaxSize()) {
            if (glyphOrigin.isSpecified) drawOpeningScene(time(), glyphOrigin, glyph, trace, colors, vista)
            else drawRect(Brush.verticalGradient(listOf(colors.backdropTop, colors.backdropBottom)))
        }
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                val exit = openingExit(time())
                alpha = 1f - exit
                scaleX = 1f + 0.035f * exit
                scaleY = scaleX
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.size(glyphWidth, glyphWidth * OpeningGlyphAspect)
                .onGloballyPositioned { glyphOrigin = it.positionInParent() })
            Spacer(Modifier.height(28.dp * gap))
            Row(horizontalArrangement = Arrangement.spacedBy(if (vista) 7.dp else 5.dp)) {
                "枳生天气".forEachIndexed { index, char ->
                    Text(char.toString(), color = colors.text, fontSize = fixed(30f), lineHeight = fixed(38f),
                        fontFamily = serif, fontWeight = if (vista) FontWeight.Medium else FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            val start = 2350f + index * 85f
                            val e = easeOutCubic(openingSegment(time(), start, start + 650f))
                            alpha = e
                            translationY = (1f - e) * 16.dp.toPx()
                            translationX = (index - 1.5f) * 14.dp.toPx() * (1f - e)
                        })
                }
            }
            Spacer(Modifier.height(18.dp * gap))
            Box(Modifier.width(36.dp).height(1.dp).graphicsLayer {
                val e = easeOutCubic(openingSegment(time(), 2850f, 3450f))
                scaleX = e
                alpha = e
            }.background(colors.textSoft.copy(alpha = 0.5f)))
            Spacer(Modifier.height(18.dp * gap))
            Text(if (spec.previousVersion != null) "已更新至" else "当前版本", color = colors.textSoft,
                fontSize = fixed(12f), letterSpacing = fixed(3f), fontFamily = serif,
                modifier = Modifier.graphicsLayer { alpha = easeOutCubic(openingSegment(time(), 2850f, 3300f)) })
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom,
                modifier = Modifier.graphicsLayer { alpha = easeOutCubic(openingSegment(time(), 2900f, 3250f)) }) {
                if (spec.channel.isNotEmpty()) {
                    Text(spec.channel, color = colors.accent, fontSize = fixed(13f), letterSpacing = fixed(2.5f),
                        fontFamily = serif, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 9.dp, end = 10.dp))
                }
                OpeningOdometer(spec.version, spec.previousVersion, time, colors, vista, fixed(48f))
            }
            Spacer(Modifier.height(10.dp * gap))
            val sections by remember(spec) { derivedStateOf {
                (spec.sectionCount * easeOutCubic(openingSegment(time(), 3350f, 4050f))).roundToInt() } }
            val notes by remember(spec) { derivedStateOf {
                (spec.noteCount * easeOutCubic(openingSegment(time(), 3350f, 4050f))).roundToInt() } }
            Text("$sections 个方面 · $notes 项更新", color = colors.textSoft, fontSize = fixed(13f),
                letterSpacing = fixed(1f), fontFamily = serif,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.graphicsLayer { alpha = easeOutCubic(openingSegment(time(), 3300f, 3650f)) })
        }
    }
}

@Composable
private fun OpeningOdometer(target: String, previous: String?, time: () -> Float, colors: OpeningColors,
                            vista: Boolean, fontSize: TextUnit) {
    val density = LocalDensity.current
    val cell = with(density) { (fontSize.toDp() * 1.18f) }
    val family = if (vista) FontFamily.SansSerif else ZhishengMono
    val weight = if (vista) FontWeight.Light else FontWeight.Medium
    Row {
        target.forEachIndexed { index, char ->
            if (!char.isDigit()) {
                Box(Modifier.height(cell), contentAlignment = Alignment.Center) {
                    Text(char.toString(), color = colors.text, fontSize = fontSize, fontFamily = family, fontWeight = weight)
                }
                return@forEachIndexed
            }
            val to = char.digitToInt()
            val from = previous?.getOrNull(previous.length - target.length + index)?.takeIf { it.isDigit() }?.digitToInt()
            val upgrade = previous != null
            val steps = when {
                !upgrade -> 10
                from == null -> 10
                from == to -> 0
                else -> (to - from + 10) % 10 + 10
            }
            val first = if (!upgrade || from == null) (to + 10 - steps % 10) % 10 else from
            val changed = upgrade && steps > 0
            val start = 2950f + index * 80f
            Box(Modifier.height(cell).clipToBounds()) {
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer {
                    val e = easeOutCubic(openingSegment(time(), start, start + 850f))
                    translationY = -e * steps * cell.toPx()
                }) {
                    for (k in 0..steps) {
                        Box(Modifier.height(cell), contentAlignment = Alignment.Center) {
                            Text(((first + k) % 10).toString(), color = if (changed) colors.accent else colors.text,
                                fontSize = fontSize, fontFamily = family, fontWeight = weight)
                        }
                    }
                }
            }
        }
    }
}

private class OpeningRunState {
    var skip = false
    var handedOff = false
    var finished = false
}

/**
 * Full-screen opening over the current screen. [onHandoff] fires while the backdrop is still
 * fading so the release notes can enter underneath; [onFinished] removes the overlay.
 * Tap or Back skips to the exit; leaving the foreground ends it immediately.
 */
@Composable
internal fun UpdateOpeningOverlay(spec: UpdateOpeningSpec, onHandoff: () -> Unit, onFinished: () -> Unit) {
    val clock = remember { mutableFloatStateOf(0f) }
    val run = remember { OpeningRunState() }
    val handoff by rememberUpdatedState(onHandoff)
    val finish by rememberUpdatedState(onFinished)
    val view = LocalView.current
    fun complete() {
        if (!run.handedOff) { run.handedOff = true; handoff() }
        if (!run.finished) { run.finished = true; finish() }
    }
    LaunchedEffect(Unit) {
        if (!systemAnimationsEnabled()) { complete(); return@LaunchedEffect }
        var start = -1L
        var offset = 0f
        var ticked = false
        while (!run.finished) {
            val frame = withFrameMillis { it }
            if (start < 0L) start = frame
            var t = (frame - start) + offset
            if (run.skip && t < OpeningExitMs) {
                offset += OpeningExitMs - t
                t = OpeningExitMs
                ticked = true
            }
            clock.floatValue = t
            if (!ticked && t >= OpeningImpactMs) {
                ticked = true
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            if (!run.handedOff && t >= OpeningHandoffMs) { run.handedOff = true; handoff() }
            if (t >= UpdateOpeningDurationMs) complete()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { complete() }
    BackHandler { run.skip = true }
    UpdateOpeningStage(
        spec = spec,
        time = { clock.floatValue },
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures { run.skip = true } }
            .clearAndSetSemantics {
                contentDescription = "更新开场动画，轻点跳过"
                onClick("跳过") { run.skip = true; true }
            },
    )
}
