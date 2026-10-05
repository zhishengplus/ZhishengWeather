package com.zhisheng.weather.data

import com.zhisheng.weather.model.GlowForecast
import com.zhisheng.weather.model.GlowKind
import com.zhisheng.weather.model.MarinePoint
import com.zhisheng.weather.model.StarForecast
import com.zhisheng.weather.model.TideTrend
import com.zhisheng.weather.model.TideTurningPoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class SceneHour(
    val timeMillis: Long,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val dewPoint: Double? = null,
    val precipitation: Double? = null,
    val precipitationProbability: Double? = null,
    val visibilityKm: Double? = null,
    val cloud: Double? = null,
    val lowCloud: Double? = null,
    val midCloud: Double? = null,
    val highCloud: Double? = null,
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
) {
    // 不用默认晴朗代替缺测。核心字段缺失时不出分、不出积极结论。
    val hasCore: Boolean get() = listOf(cloud, lowCloud, midCloud, highCloud).all {
        it != null && it.isFinite() && it in 0.0..100.0
    } && visibilityKm != null && visibilityKm.isFinite() && visibilityKm >= 0 &&
        precipitation != null && precipitation.isFinite() && precipitation >= 0
}

internal object SceneWeatherCalculator {
    private const val HOUR = 3_600_000L

    fun glow(hours: List<SceneHour>, events: List<Pair<Long, GlowKind>>, nowMillis: Long): GlowForecast? {
        val event = events.filter { it.first >= nowMillis - 30 * 60_000L && it.first <= nowMillis + 36 * HOUR }
            .minByOrNull { it.first } ?: return null
        val window = hours.filter { abs(it.timeMillis - event.first) <= HOUR }
        if (window.isEmpty() || window.any { !it.hasCore }) return null
        val low = window.maxOf { it.lowCloud!! }
        val mid = window.map { it.midCloud!! }.average()
        val high = window.map { it.highCloud!! }.average()
        val canvas = max(mid, high)
        val visibility = window.minOf { it.visibilityKm!! }
        val rain = window.maxOf { it.precipitation!! }
        val probability = window.mapNotNull { it.precipitationProbability }.maxOrNull() ?: 0.0
        val blocked = low >= 85 || visibility < 3 || rain >= 0.3 || probability >= 55
        val grade = when {
            blocked || canvas < 12 || window.all { it.cloud!! >= 96 } -> "不建议专程去"
            canvas in 25.0..75.0 && low <= 30 && visibility >= 15 -> "很值得等"
            canvas in 15.0..88.0 && low < 60 && visibility >= 8 -> "可以碰碰运气"
            else -> "条件一般"
        }
        val reasons = listOf(
            when {
                canvas < 12 -> "中高云偏少，大片霞云机会有限"
                canvas <= 75 -> "有中高云可被染色，云量组合尚可"
                else -> "中高云偏厚，霞光层次可能受影响"
            },
            if (low <= 30) "本地点低云较少；远处地平线仍需现场观察" else "本地点低云偏多，可能遮挡阳光",
            when {
                rain >= 0.3 || probability >= 55 -> "窗口附近有降水风险，条件下调"
                visibility < 8 -> "能见度偏低，色彩和层次可能发灰"
                else -> "本地点能见度尚可"
            },
        )
        return GlowForecast(
            event.second, grade, event.first - 40 * 60_000L, event.first + 30 * 60_000L, reasons,
            quality = when {
                blocked -> "观赏质量受限；不代表不会出现短暂霞光"
                canvas < 12 -> "可能有地平线暖色，但不宜期待大片火烧云"
                else -> "有染色云层条件；颜色、面积取决于远处光路，不能保证火烧云"
            },
        )
    }

    fun stars(
        hours: List<SceneHour>, latitude: Double, longitude: Double, nowMillis: Long,
        zoneId: String = "Asia/Shanghai",
    ): StarForecast? {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val remainingNight = local.hour < 12 && Astronomy.sunAltitudeDegrees(nowMillis, latitude, longitude) < -12
        val nightDate = if (remainingNight) local.toLocalDate().minusDays(1) else local.toLocalDate()
        val end = nightDate.plusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        // 三个逐时端点覆盖完整两小时；检查中间每15分钟，不能把破晓后的一小时算入黑夜。
        val windows = hours.sortedBy { it.timeMillis }.distinctBy { it.timeMillis }.windowed(3)
            .filter { w ->
                w.first().timeMillis >= nowMillis && w.last().timeMillis <= end &&
                    w.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis == HOUR } &&
                    w.all { it.hasCore } &&
                    (0..8).all { step ->
                        Astronomy.sunAltitudeDegrees(w.first().timeMillis + step * 15 * 60_000L, latitude, longitude) <= -18
                    }
            }
        val best = windows.maxByOrNull { w -> w.minOf { starHourScore(it, latitude, longitude) } } ?: return null
        // 取窗口最弱时刻，避免一小时大雨被另一小时晴朗“平均掉”。
        val score = best.minOf { starHourScore(it, latitude, longitude) }.roundToInt().coerceIn(0, 100)
        val cloud = best.maxOf { max(it.cloud!!, max(it.highCloud!!, max(it.midCloud!!, it.lowCloud!!))) }
        val rain = best.any { it.precipitation!! > 0 || (it.precipitationProbability ?: 0.0) >= 35 }
        val visibility = best.minOf { it.visibilityKm!! }
        val moonStrength = best.maxOf { moonPenalty(it.timeMillis, latitude, longitude) }
        val illumination = MoonCalc.illuminationFraction(best[1].timeMillis)
        val spreads = best.mapNotNull { h -> h.temperature?.let { t -> h.dewPoint?.let { t - it } } }
        val winds = best.mapNotNull { it.gustKmh ?: it.windKmh }
        val reasons = listOf(
            if (rain) "窗口内有降水风险，不宜露天架机" else "最大云量约 ${cloud.roundToInt()}%，已计入高云遮挡",
            if (moonStrength > 15) "月亮较亮且在地平线上，银河对比度受影响" else "窗口内月光干扰较弱",
            "能见度最低约 ${visibility.roundToInt()}km；这不是专业大气透明度测量",
            when {
                spreads.size != best.size -> "露点数据不完整，结露风险未知"
                spreads.min() < 2.5 -> "气温接近露点，建议给镜头防露"
                else -> "温度与露点有余量，结露风险较低"
            },
            when {
                winds.size != best.size -> "风速数据不完整，现场确认三脚架稳定性"
                winds.max() > 35 -> "阵风较强，三脚架稳定性受影响"
                else -> "风力对架机影响相对较小"
            },
        )
        val recommendation = when {
            rain || score < 45 -> "不建议专程架机；先看现场云雨变化"
            moonStrength > 15 -> "可尝试月亮、行星或星轨；目标是否可见仍需确认"
            score >= 75 -> "天气适合尝试星空；银河还需暗空环境与合适方位"
            else -> "可尝试明亮星座或星轨，现场条件为准"
        }
        return StarForecast(
            score, when (score) { in 80..100 -> "优秀"; in 65..79 -> "不错"; in 45..64 -> "一般"; else -> "较差" },
            best.first().timeMillis, best.last().timeMillis, reasons, recommendation, illumination,
        )
    }

    fun tide(points: List<MarinePoint>, nowMillis: Long): Triple<TideTrend, TideTurningPoint?, TideTurningPoint?> {
        val sorted = points.sortedBy { it.timeMillis }.distinctBy { it.timeMillis }
        fun contiguous(a: MarinePoint, b: MarinePoint) = b.timeMillis - a.timeMillis in 1..HOUR &&
            a.seaLevelM?.isFinite() == true && b.seaLevelM?.isFinite() == true
        val current = sorted.zipWithNext().firstOrNull { (a, b) ->
            a.timeMillis <= nowMillis && nowMillis < b.timeMillis && contiguous(a, b)
        }
        val trend = current?.let { (a, b) ->
            val delta = b.seaLevelM!! - a.seaLevelM!!
            when { abs(delta) < 0.01 -> TideTrend.STEADY; delta > 0 -> TideTrend.RISING; else -> TideTrend.FALLING }
        } ?: TideTrend.UNKNOWN
        // 保留 null 和时间空洞，不跨缺测拼拐点；平台极值需两侧都证实转向。
        val turns = mutableListOf<Pair<Boolean, TideTurningPoint>>()
        var index = 1
        while (index < sorted.lastIndex) {
            val before = sorted[index - 1]
            val middle = sorted[index]
            if (!contiguous(before, middle)) { index++; continue }
            var plateauEnd = index
            while (plateauEnd < sorted.lastIndex && contiguous(sorted[plateauEnd], sorted[plateauEnd + 1]) &&
                sorted[plateauEnd + 1].seaLevelM == middle.seaLevelM) plateauEnd++
            val after = sorted.getOrNull(plateauEnd + 1)
            if (after != null && contiguous(sorted[plateauEnd], after)) {
                val value = middle.seaLevelM!!
                val high = value > before.seaLevelM!! && value > after.seaLevelM!!
                val low = value < before.seaLevelM!! && value < after.seaLevelM!!
                val time = middle.timeMillis + (sorted[plateauEnd].timeMillis - middle.timeMillis) / 2
                if ((high || low) && time > nowMillis) turns += high to TideTurningPoint(time, value)
            }
            index = plateauEnd + 1
        }
        return Triple(trend, turns.firstOrNull { it.first }?.second, turns.firstOrNull { !it.first }?.second)
    }

    internal fun moonPenalty(time: Long, latitude: Double, longitude: Double): Double =
        if (MoonCalc.moonAltitudeDegrees(time, latitude, longitude) > 0) MoonCalc.illuminationFraction(time) * 28 else 0.0

    private fun starHourScore(h: SceneHour, latitude: Double, longitude: Double): Double {
        val cloud = max(h.cloud!!, max(h.lowCloud!!, max(h.midCloud!!, h.highCloud!!)))
        var score = 100.0 - cloud * 0.7 - moonPenalty(h.timeMillis, latitude, longitude)
        if (h.visibilityKm!! < 3) score -= 25 else if (h.visibilityKm < 8) score -= 12
        val spread = h.temperature?.let { t -> h.dewPoint?.let { t - it } }
        if (spread == null) score -= 8 else if (spread < 2.5) score -= 15
        val wind = h.gustKmh ?: h.windKmh
        if (wind == null) score -= 8 else if (wind > 35) score -= 15
        if (spread == null || wind == null) score = score.coerceAtMost(60.0)
        if (cloud >= 80 || h.precipitation!! > 0 || (h.precipitationProbability ?: 0.0) >= 35) score = score.coerceAtMost(25.0)
        return score.coerceIn(0.0, 100.0)
    }
}
