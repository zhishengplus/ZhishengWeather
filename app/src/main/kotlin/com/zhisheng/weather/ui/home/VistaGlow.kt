package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.GlowForecast
import com.zhisheng.weather.model.GlowKind
import com.zhisheng.weather.model.SceneWeatherData
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.weatherPresentationTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun vistaGlowClock(time: Long, zone: String): String = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    .format(Instant.ofEpochMilli(time).atZone(vistaGlowZone(zone)))

internal fun vistaGlowWindowLabel(start: Long, end: Long, zone: String): String {
    val tz = vistaGlowZone(zone)
    val from = Instant.ofEpochMilli(start).atZone(tz)
    val to = Instant.ofEpochMilli(end).atZone(tz)
    val startClock = vistaGlowClock(start, zone)
    val endClock = vistaGlowClock(end, zone)
    return if (from.toLocalDate() == to.toLocalDate()) "$startClock–$endClock"
    else "${from.monthValue}/${from.dayOfMonth} $startClock–${to.monthValue}/${to.dayOfMonth} $endClock"
}

internal fun vistaGlowZone(zone: String): ZoneId =
    runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))

internal fun vistaGlowDayPhrase(now: Long, glow: GlowForecast, zone: String): String {
    val tz = vistaGlowZone(zone)
    val nowDate = Instant.ofEpochMilli(now).atZone(tz).toLocalDate()
    val eventDate = Instant.ofEpochMilli((glow.startMillis + glow.endMillis) / 2).atZone(tz).toLocalDate()
    val days = ChronoUnit.DAYS.between(nowDate, eventDate)
    return when (glow.kind) {
        GlowKind.DAWN -> when (days) { 0L -> "今早"; 1L -> "明早"; else -> "朝霞" }
        GlowKind.DUSK -> when (days) { 0L -> "今晚"; 1L -> "明晚"; else -> "晚霞" }
    }
}

internal fun vistaGlowHeadline(glow: GlowForecast, now: Long, zone: String): String {
    val day = vistaGlowDayPhrase(now, glow, zone)
    return if (glow.grade == "不建议专程去") "${day}不值得专程出门"
    else "${day}${glow.kind.label} · ${glow.grade}"
}

internal fun vistaGlowWhenLabel(glow: GlowForecast, zone: String): String {
    val range = vistaGlowWindowLabel(glow.startMillis, glow.endMillis, zone)
    return if (glow.kind == GlowKind.DUSK) "太阳落下前后 $range" else "太阳升起前后 $range"
}

internal fun vistaGlowNowStatus(now: Long, glow: GlowForecast): String = when {
    now < glow.startMillis -> "现在还早"
    now > glow.endMillis -> "已经过了"
    else -> "就在这会儿"
}

internal fun vistaGlowNowInWindow(now: Long, glow: GlowForecast): Boolean =
    now in glow.startMillis..glow.endMillis

internal fun vistaGlowBriefReason(glow: GlowForecast): String? =
    glow.reasons.firstOrNull(String::isNotBlank)

@Composable
internal fun VistaGlowSummary(glow: GlowForecast?, zone: String) {
    if (glow == null) {
        Text("暂无可用的朝晚霞判断", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val now = weatherPresentationTime()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(vistaGlowHeadline(glow, now, zone), color = if (isPhosphorVista) ZhishengText else ZhishengOrange,
            style = if (isPhosphorVista) MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading)
                else MaterialTheme.typography.titleLarge)
        Text(vistaGlowWhenLabel(glow, zone), color = if (isPhosphorVista) ZhishengTextSecondary else ZhishengText,
            style = if (isPhosphorVista) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
        vistaGlowBriefReason(glow)?.let {
            Text(it, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun VistaGlowDetail(scene: SceneWeatherData?, city: City?) {
    val sky = scene?.sky
    val glow = sky?.glow
    if (glow == null) {
        Text(scene?.skyError ?: sky?.unavailableReason ?: "暂时没有可用霞光预报",
            color = ZhishengText, style = MaterialTheme.typography.titleLarge)
        AtlasCaption("返回首页刷新后重试；没有数据时不推断火烧云。")
        return
    }
    val zone = sky.zoneId
    val now = weatherPresentationTime()
    Text(vistaGlowHeadline(glow, now, zone), color = ZhishengOrange, style = MaterialTheme.typography.headlineMedium)
    Text(vistaGlowWhenLabel(glow, zone), color = ZhishengText, style = MaterialTheme.typography.titleMedium)
    AtlasStale(sky.stale)
    Column(Modifier.fillMaxWidth().vistaGlowSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VistaGlowHorizon(glow, now)
        AtlasCaption(
            if (glow.kind == GlowKind.DUSK) "太阳贴近地平线落下前后，中高云更容易被染成晚霞。"
            else "太阳贴近地平线升起前后，中高云更容易被染成朝霞。",
        )
        AtlasCaption("这不是火烧云概率，只说明这段时间值不值得出门看一眼。")
    }
    glow.quality.takeIf(String::isNotBlank)?.let { AtlasCaption(it) }
    glow.reasons.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { reasons ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("为什么这样说", color = ZhishengText, style = MaterialTheme.typography.titleMedium)
            reasons.forEach { AtlasCaption(it) }
        }
    }
    AtlasCaption((if (isPhosphorVista) "数据源 " else "") + "${sky.source} · 获取于 ${sceneStamp(sky.updatedAtMillis, zone)}")
    city?.let {
        AtlasCaption("地点 ${it.displayName}。天气与现场遮挡仍会影响能否拍到。")
    }
}

@Composable
private fun VistaGlowHorizon(glow: GlowForecast, now: Long) {
    val palette = LocalZhishengPalette.current
    val status = vistaGlowNowStatus(now, glow)
    val dusk = glow.kind == GlowKind.DUSK
    val mute = when (glow.grade) {
        "不建议专程去" -> 0.58f
        "条件一般" -> 0.32f
        "可以碰碰运气" -> 0.14f
        else -> 0.04f
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "${glow.kind.label}地平线示意，$status" },
        ) {
            val horizon = size.height * 0.78f
            val gray = palette.card
            fun wash(color: Color) = lerp(color, gray, mute)
            val top = wash(if (dusk) {
                if (palette.isLight) Color(0xFF5A6D88) else Color(0xFF152238)
            } else {
                if (palette.isLight) Color(0xFF7EA9C8) else Color(0xFF2A4E72)
            })
            val mid = wash(if (dusk) Color(0xFFD2653A) else Color(0xFFE48A98))
            val rim = wash(if (dusk) palette.orange else Color(0xFFF0C070))
            val core = wash(Color(0xFFFFF1C4))
            drawRect(Brush.verticalGradient(listOf(top, mid, rim), endY = size.height))
            val sunX = size.width * if (dusk) 0.72f else 0.28f
            val sunY = horizon + when (status) {
                "现在还早" -> if (dusk) -22.dp.toPx() else 18.dp.toPx()
                "已经过了" -> if (dusk) 18.dp.toPx() else -22.dp.toPx()
                else -> 0f
            }
            val sun = Offset(sunX, sunY)
            val halo = 52.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(core.copy(alpha = 0.95f), rim.copy(alpha = 0.42f), Color.Transparent), sun, halo),
                halo,
                sun,
            )
            drawCircle(core, 11.dp.toPx(), sun)
            val earth = lerp(rim, if (palette.isLight) Color(0xFF8D6B52) else Color(0xFF2A1C14), 0.28f)
            drawRect(
                Brush.verticalGradient(listOf(lerp(rim, earth, 0.4f), earth), startY = horizon, endY = size.height),
                Offset(0f, horizon),
                Size(size.width, size.height - horizon),
            )
            drawLine(rim.copy(alpha = 0.55f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
        }
        Text(
            status,
            color = ZhishengOrange,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Modifier.vistaGlowSurface(): Modifier = zhishengPanel(containerColor = ZhishengCard)
