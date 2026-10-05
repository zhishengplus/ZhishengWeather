package com.zhisheng.weather.model

import kotlin.math.abs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Hero 高低温跟时刻走（v0.0.9）：国内源白天 10:00–20:00。
// 10 点前看昨夜最低和今天最高；白天看今天高/低；20 点后看今夜最低和明天最高。
data class HeroRange(
    val leftLabel: String,
    val left: Double?,
    val rightLabel: String,
    val right: Double?,
) {
    val hasAny: Boolean get() = left != null || right != null
}

object HeroTemps {
    const val DAY_START_HOUR = 10
    const val NIGHT_START_HOUR = 20
    const val FEELS_GAP_C = 1.5

    fun range(
        daily: List<DailyWeather>,
        yesterday: YesterdayInfo?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        hourly: List<HourlyWeather> = emptyList(),
    ): HeroRange {
        val zonedNow = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val hour = zonedNow.hour
        val todayDate = zonedNow.toLocalDate()
        fun dayOf(d: DailyWeather): LocalDate =
            Instant.ofEpochMilli(d.dateMillis).atZone(zone).toLocalDate()
        val today = daily.firstOrNull { dayOf(it) == todayDate }
        val tomorrow = daily.firstOrNull { dayOf(it) == todayDate.plusDays(1) }
        val todayHigh = today?.high
        val todayLow = today?.low
        // A daily minimum covers a calendar day, not tonight. Only use the
        // tonight label when hourly forecasts cover now through early morning.
        val start = zonedNow.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val end = todayDate.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val night = hourly.filter { it.timeMillis in start..end && it.temperature?.isFinite() == true }
            .distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
        val gap = 3 * 60 * 60_000L
        val coversNight = night.isNotEmpty() && night.first().timeMillis - start <= gap &&
            end - night.last().timeMillis <= 2 * 60 * 60_000L &&
            night.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis <= gap }
        val tonightLow = if (coversNight) night.mapNotNull { it.temperature }.minOrNull() else null
        return when {
            hour < DAY_START_HOUR -> HeroRange(
                leftLabel = "昨晚最低",
                left = yesterday?.low,
                rightLabel = "今天最高",
                right = todayHigh,
            )
            hour >= NIGHT_START_HOUR -> HeroRange(
                leftLabel = if (tonightLow != null) "今夜最低" else if (tomorrow?.low != null) "明日最低" else "今日最低",
                left = tonightLow ?: tomorrow?.low ?: todayLow,
                rightLabel = if (tomorrow?.high != null) "明天最高" else "今天最高",
                right = tomorrow?.high ?: todayHigh,
            )
            else -> HeroRange(
                leftLabel = "高",
                left = todayHigh,
                rightLabel = "低",
                right = todayLow,
            )
        }
    }

    fun showFeelsLike(temperature: Double?, feelsLike: Double?): Boolean {
        if (feelsLike == null) return false
        if (temperature == null) return true
        return abs(feelsLike - temperature) >= FEELS_GAP_C
    }
}
