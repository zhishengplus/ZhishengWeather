package com.zhisheng.weather.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.LuminaryStyle
import com.zhisheng.weather.data.MoonCalc
import com.zhisheng.weather.data.ReleaseFeatures
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.rememberReducedMotion
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.weatherPresentationTime
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// 日月卡：波形（原版）与同心弧（新版）可切换，切换按钮是卡片右上角的一枚嵌入凹槽。
// ReleaseFeatures.luminaryArc = false 时整体退回原版，不显示切换按钮。

/** 上次读到的画法，只用于重建时的初值，免得卡片重进时先画成波形再跳成弧线。 */
private var lastKnownLuminaryStyle = LuminaryStyle.WAVE

/** 升起后走过的比例；不在天上返回 null。月升跨零点也成立。 */
internal fun vistaArcProgress(now: Int, rise: Int, set: Int): Float? {
    val up = vistaAstroUpDuration(rise, set) ?: return null
    val elapsed = Math.floorMod(now - rise, VISTA_DAY_MINUTES)
    return if (elapsed < up) elapsed.toFloat() / up.toFloat() else null
}

internal fun vistaDurationZh(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}分钟"
        m == 0 -> "${h}小时"
        else -> "${h}小时${m}分"
    }
}

/** 标题右侧的一句话：白天倒数日落，夜里倒数日出。 */
internal fun vistaSunStatus(now: Int, rise: Int?, set: Int?): String? {
    if (rise == null || set == null || set <= rise) return null
    return when {
        now < rise -> "日出还有 ${vistaDurationZh(rise - now)}"
        now < set -> "日落还有 ${vistaDurationZh(set - now)}"
        else -> "日出还有 ${vistaDurationZh(rise + VISTA_DAY_MINUTES - now)}"
    }
}

/**
 * 首页两处调用点统一走这里。持久化选择与动画内容分开，滚回卡片时直接显示已选形态。
 */
@Composable
internal fun VistaLuminarySlot(today: DailyWeather, utcOffsetSeconds: Int?) {
    if (!ReleaseFeatures.luminaryArc) {
        VistaLuminary(today, utcOffsetSeconds)
        return
    }
    val style by SettingsRepository.luminaryStyle.collectAsState(initial = lastKnownLuminaryStyle)
    // 首页在 LazyColumn 里，滚出再滚回会重建；缓存住上次读到的值，避免每次回到卡片都闪一下。
    SideEffect { lastKnownLuminaryStyle = style }
    val scope = rememberCoroutineScope()
    VistaLuminaryContent(today, utcOffsetSeconds, style) { next ->
        if (next != style) scope.launch { SettingsRepository.setLuminaryStyle(next) }
    }
}

@Composable
internal fun VistaLuminaryContent(
    today: DailyWeather,
    utcOffsetSeconds: Int?,
    style: LuminaryStyle,
    reducedMotion: Boolean = rememberReducedMotion(),
    onSelect: (LuminaryStyle) -> Unit,
) {
    // 一份可中途反向的进度同时驱动轨迹、标记、时刻和滑钮，首帧直接停在已选形态。
    val morph = animateFloatAsState(
        if (style == LuminaryStyle.ARC) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 420, easing = FastOutSlowInEasing),
        label = "luminary-morph",
    )
    val phase = Fmt.moonPhaseZh(today.moonPhase)
    val illumination = MoonCalc.illuminationFraction(weatherPresentationTime()).coerceIn(0.0, 1.0)
    val palette = LocalZhishengPalette.current

    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = buildString {
                append("太阳和月亮，日出 ${today.sunrise ?: "暂缺"}，日落 ${today.sunset ?: "暂缺"}，")
                append("月升 ${today.moonrise ?: "暂缺"}，月落 ${today.moonset ?: "暂缺"}")
                phase?.let { append("，$it") }
            }
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("太阳和月亮", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = palette.text, fontWeight = FontWeight.Medium)
            if (phase != null) {
                VistaMoonDisc(illumination, Modifier.size(18.dp))
                Text(phase, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1)
            }
            LuminaryStyleToggle(
                style = style,
                progress = { morph.value },
                onSelect = onSelect,
            )
        }
        VistaLuminaryMotionBody(today, utcOffsetSeconds) { morph.value }
    }
}

/**
 * 右上角切换件：一条嵌进面板的凹槽，两个小图形（波形 / 弧线），圆钮在中间滑过去。
 * 不抢眼：只用面板描边色和文字色，没有新色值，也没有阴影外扩。
 */
@Composable
private fun LuminaryStyleToggle(style: LuminaryStyle, progress: () -> Float, onSelect: (LuminaryStyle) -> Unit) {
    val palette = LocalZhishengPalette.current
    val groove = if (palette.isLight) lerp(palette.card, palette.cardBorder, 0.42f)
    else lerp(palette.surface, Color.Black, 0.34f)
    val thumb = if (palette.isLight) palette.surface else lerp(palette.surface, palette.cardBorder, 0.55f)
    val label = if (style == LuminaryStyle.ARC) "弧线" else "波形"
    Box(
        Modifier
            .size(width = 48.dp, height = 26.dp)
            .clickable(role = Role.Switch) {
                onSelect(if (style == LuminaryStyle.ARC) LuminaryStyle.WAVE else LuminaryStyle.ARC)
            }
            .semantics {
                contentDescription = "日月卡样式"
                stateDescription = label
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val slide = progress().coerceIn(0f, 1f)
            val r = size.height / 2f
            // 凹槽：底色比面板略深，顶部压一道内阴影，底部给一道极淡的反光。
            drawRoundRect(groove, cornerRadius = CornerRadius(r))
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = if (palette.isLight) 0.07f else 0.26f), Color.Transparent),
                    startY = 0f, endY = size.height * 0.62f,
                ),
                cornerRadius = CornerRadius(r),
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = if (palette.isLight) 0.5f else 0.06f)),
                    startY = size.height * 0.55f, endY = size.height,
                ),
                cornerRadius = CornerRadius(r),
            )
            drawRoundRect(palette.cardBorder.copy(alpha = 0.5f), cornerRadius = CornerRadius(r),
                style = Stroke(0.8.dp.toPx()))
            // 滑钮：外圈一道同色描边当作嵌入的边缘，不投阴影。
            val inset = 2.5.dp.toPx()
            val thumbW = size.width / 2f - inset
            val thumbH = size.height - inset * 2
            val x = inset + slide * thumbW
            drawRoundRect(thumb.copy(alpha = if (palette.isLight) 0.99f else 0.92f),
                topLeft = Offset(x, inset), size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbH / 2f))
            drawRoundRect(palette.cardBorder.copy(alpha = if (palette.isLight) 0.75f else 0.45f),
                topLeft = Offset(x, inset), size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbH / 2f), style = Stroke(0.8.dp.toPx()))
            // 两个小图形：波形在左，同心弧在右；随滑钮连续变亮/变暗。
            val cy = size.height / 2f
            val gw = 4.5.dp.toPx()
            val gh = 3.6.dp.toPx()
            val stroke = 1.15.dp.toPx()
            drawWaveGlyph(size.width * 0.25f, cy, gw, gh,
                lerp(palette.textTertiary, palette.text, 1f - slide), stroke)
            drawArcGlyph(size.width * 0.75f, cy, gw, gh,
                lerp(palette.textTertiary, palette.text, slide), stroke)
        }
    }
}

private fun DrawScope.drawWaveGlyph(cx: Float, cy: Float, halfW: Float, amp: Float, color: Color, stroke: Float) {
    val path = Path()
    val steps = 18
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = cx - halfW + halfW * 2f * t
        val y = cy - amp * sin(t * 2f * PI.toFloat()) * 0.9f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun DrawScope.drawArcGlyph(cx: Float, cy: Float, halfW: Float, amp: Float, color: Color, stroke: Float) {
    drawArc(color, 180f, 180f, useCenter = false,
        topLeft = Offset(cx - halfW, cy - amp), size = Size(halfW * 2f, amp * 2f),
        style = Stroke(stroke, cap = StrokeCap.Round))
}

/** Static arc endpoint used by native rendering checks; production always uses the shared motion body. */
@Composable
internal fun VistaLuminaryArcBody(today: DailyWeather, utcOffsetSeconds: Int?) {
    VistaLuminaryMotionBody(today, utcOffsetSeconds) { 1f }
}
