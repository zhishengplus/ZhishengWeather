package com.zhisheng.weather.data

import com.zhisheng.weather.model.DailyWeather
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.tan

// 本地月相计算（Meeus《天文算法》49 章，分秒级精度）——数据源缺 moonPhase 字段时的兜底。
// 计算朔/上弦/望/下弦的 UTC 时刻，标注约定与和风一致：
// 事件发生当日取事件名，其余日取最近已过事件的"时期名"，保证与和风源同日显示一致。
// 输出 key 与和风 moonPhase 相同，直接复用 Fmt.moonPhaseZh 翻译。
object MoonCalc {

    data class MoonTimes(val rise: String?, val set: String?)

    fun enrich(day: DailyWeather, latitude: Double, longitude: Double): DailyWeather {
        if (day.moonPhase != null && day.moonrise != null && day.moonset != null) return day
        val times = riseSet(day.dateMillis, latitude, longitude)
        var rise = day.moonrise ?: times.rise
        var set = day.moonset ?: times.set
        // 当日只升不落（如 15:01 升、次日凌晨落）是月亮的常态而非缺数据。
        // 与和风"下一次事件"语义对齐：向相邻日窗口补齐配对事件，否则月亮波浪图永远画不出来。
        if (rise != null && set == null) {
            set = (1L..2L).asSequence()
                .map { offset -> riseSet(day.dateMillis + offset * DAY_MS, latitude, longitude).set }
                .firstOrNull { it != null }
        }
        if (set != null && rise == null) {
            rise = (1L..2L).asSequence()
                .map { offset -> riseSet(day.dateMillis - offset * DAY_MS, latitude, longitude).rise }
                .firstOrNull { it != null }
        }
        return day.copy(
            moonPhase = day.moonPhase ?: phaseKeyForDayStart(day.dateMillis),
            moonrise = day.moonrise ?: rise,
            moonset = day.moonset ?: set,
        )
    }

    fun phaseKey(dateMillis: Long): String {
        // 日界按本地时区算：原来用本地日期却配 UTC 零点，窗口整体偏了一个时区偏移，
        // 东八区会把事件判到前一天（v0.0.2）
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return phaseKeyForWindow(dayStart, dayEnd)
    }

    // dateMillis 已是目标城市当天 00:00 的真实 epoch 时使用，避免海外城市被手机本地时区带偏。
    fun phaseKeyForDayStart(dateMillis: Long): String =
        phaseKeyForWindow(dateMillis, dateMillis + DAY_MS)

    private fun phaseKeyForWindow(dayStart: Long, dayEnd: Long): String {
        val events = eventsAround(dayStart)
        events.firstOrNull { it.first in dayStart until dayEnd }?.let { return it.second }
        val last = events.lastOrNull { it.first < dayStart } ?: return "waning-crescent"
        return when (last.second) {
            "new-moon" -> "waxing-crescent"
            "first-quarter" -> "waxing-gibbous"
            "full-moon" -> "waning-gibbous"
            else -> "waning-crescent"
        }
    }

    /**
     * 计算目标城市本地日内的月出/月落。算法取自常用的月球赤经/赤纬近似模型，
     * 以两小时为步长做二次插值；误差通常在数分钟量级，极区无升落事件时返回 null。
     * [dayStartMillis] 必须对应目标城市的本地 00:00。
     */
    fun riseSet(dayStartMillis: Long, latitude: Double, longitude: Double): MoonTimes {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0) {
            return MoonTimes(null, null)
        }

        val hc = 0.133 * RAD
        var h0 = moonAltitude(dayStartMillis, latitude, longitude) - hc
        var riseHour: Double? = null
        var setHour: Double? = null

        for (hour in 1..23 step 2) {
            val h1 = moonAltitude(dayStartMillis + hour * HOUR_MS, latitude, longitude) - hc
            val h2 = moonAltitude(dayStartMillis + (hour + 1) * HOUR_MS, latitude, longitude) - hc
            val a = (h0 + h2) / 2.0 - h1
            val b = (h2 - h0) / 2.0

            if (abs(a) > 1e-12) {
                val xe = -b / (2.0 * a)
                val ye = (a * xe + b) * xe + h1
                val discriminant = b * b - 4.0 * a * h1
                if (discriminant >= 0.0) {
                    val dx = sqrt(discriminant) / (2.0 * abs(a))
                    val x1 = xe - dx
                    val x2 = xe + dx
                    val roots = listOf(x1, x2).filter { abs(it) <= 1.0 }.sorted()
                    when (roots.size) {
                        1 -> {
                            val eventHour = hour + roots[0]
                            if (h0 < 0.0) riseHour = eventHour else setHour = eventHour
                        }
                        2 -> {
                            val first = hour + roots[0]
                            val second = hour + roots[1]
                            if (ye < 0.0) {
                                riseHour = second
                                setHour = first
                            } else {
                                riseHour = first
                                setHour = second
                            }
                        }
                    }
                }
            }

            if (riseHour != null && setHour != null) break
            h0 = h2
        }

        return MoonTimes(formatLocalHour(riseHour), formatLocalHour(setHour))
    }

    fun moonAltitudeDegrees(millis: Long, latitude: Double, longitude: Double): Double =
        moonAltitude(millis, latitude, longitude) / RAD

    fun illuminationFraction(millis: Long): Double {
        // 用同一时刻日月赤经/赤纬求距角，不拿固定朔望月锚点当当天月相。
        val days = millis / DAY_MS.toDouble() - 0.5 + J1970 - J2000
        val anomaly = (357.5291 + 0.98560028 * days) * RAD
        val center = (1.9148 * sin(anomaly) + 0.02 * sin(2 * anomaly) + 0.0003 * sin(3 * anomaly)) * RAD
        val sunLongitude = anomaly + center + 102.9372 * RAD + PI
        val sunRa = rightAscension(sunLongitude, 0.0)
        val sunDec = declination(sunLongitude, 0.0)
        val moon = moonCoords(days)
        val cosElongation = sin(sunDec) * sin(moon.second) + cos(sunDec) * cos(moon.second) * cos(sunRa - moon.first)
        return ((1.0 - cosElongation) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun moonAltitude(millis: Long, latitude: Double, longitude: Double): Double {
        val lw = -longitude * RAD
        val phi = latitude * RAD
        val days = millis / DAY_MS.toDouble() - 0.5 + J1970 - J2000
        val coords = moonCoords(days)
        val hourAngle = siderealTime(days, lw) - coords.first
        val altitude = asin(sin(phi) * sin(coords.second) + cos(phi) * cos(coords.second) * cos(hourAngle))
        // 折射公式自身在地平线以下按 0 计算，但必须保留原始负高度，否则整天都不会出现穿越事件。
        val refractionAltitude = altitude.coerceAtLeast(0.0)
        return altitude + 0.0002967 /
            tan(refractionAltitude + 0.00312536 / (refractionAltitude + 0.08901179))
    }

    private fun moonCoords(days: Double): Pair<Double, Double> {
        val meanLongitude = (218.316 + 13.176396 * days) * RAD
        val meanAnomaly = (134.963 + 13.064993 * days) * RAD
        val meanDistance = (93.272 + 13.229350 * days) * RAD
        val longitude = meanLongitude + 6.289 * RAD * sin(meanAnomaly)
        val latitude = 5.128 * RAD * sin(meanDistance)
        return rightAscension(longitude, latitude) to declination(longitude, latitude)
    }

    private fun rightAscension(longitude: Double, latitude: Double): Double =
        atan2(sin(longitude) * cos(OBLIQUITY) - tan(latitude) * sin(OBLIQUITY), cos(longitude))

    private fun declination(longitude: Double, latitude: Double): Double =
        asin(sin(latitude) * cos(OBLIQUITY) + cos(latitude) * sin(OBLIQUITY) * sin(longitude))

    private fun siderealTime(days: Double, lw: Double): Double =
        (280.16 + 360.9856235 * days) * RAD - lw

    private fun formatLocalHour(hour: Double?): String? {
        if (hour == null || !hour.isFinite()) return null
        val minutes = (hour * 60.0).roundToInt()
        if (minutes !in 0 until 24 * 60) return null
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }

    // 朔望月长度（毫秒）与 k=0 基准锚点。
    // 注意三处数值并不完全一致：K0_EPOCH_MS 为 2000-01-06T12:14Z，phaseMillis 多项式基值
    // 2451550.09766 约对应 2000-01-05T14:20Z，真实新月约在 2000-01-06T18:14Z（v0.0.4 勘正旧注释）。
    // 数小时的差除以朔望月仅 ~0.01，k0 经 round 取整不受影响，事件窗口判定由 2024-08 锚点测试锁定。
    private const val SYNODIC_MS = 2_551_442_877L
    private const val K0_EPOCH_MS = 946_728_000_000L + 476_040_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val RAD = PI / 180.0
    private const val OBLIQUITY = 23.4397 * RAD

    // 目标日前后共 3 个朔望月的 12 个相位事件，升序（epochMillis → 事件名）
    private fun eventsAround(millis: Long): List<Pair<Long, String>> {
        // k 必须从 phaseMillis 的同一基准（2000 年那次新月）起算。
        // 原式先把 epoch 当作「1970 年起的儒略年数」再乘每年朔望月数，基准差了 30 年，
        // 算出的 k0≈700 对应 2056 年，12 个事件全部落在目标日之后 →
        // phaseKey 两个分支都落空，永远返回兜底的 "waning-crescent"（月相恒显示"残月"）。
        val k0 = Math.round((millis - K0_EPOCH_MS).toDouble() / SYNODIC_MS).toInt()
        val out = ArrayList<Pair<Long, String>>(20)
        for (k in k0 - 2..k0 + 2) {
            out.add(phaseMillis(k, 0.00) to "new-moon")
            out.add(phaseMillis(k, 0.25) to "first-quarter")
            out.add(phaseMillis(k, 0.50) to "full-moon")
            out.add(phaseMillis(k, 0.75) to "last-quarter")
        }
        return out.sortedBy { it.first }
    }

    // k=朔望月序号（2000-01 新月为 0），kind=相位偏移（0 朔 / .25 上弦 / .5 望 / .75 下弦）
    private fun phaseMillis(kInt: Int, kind: Double): Long {
        val k = kInt + kind
        val t = k / 1236.85
        var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t * t -
            0.000000150 * t * t * t + 0.00000000073 * t * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t * t)
        val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t)
        val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t)
        val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t)
        val corr = when (kind) {
            0.00 -> -0.40720 * sin(mp) + 0.17241 * e * sin(m) + 0.01608 * sin(2 * mp) +
                0.01039 * sin(2 * f) + 0.00739 * e * sin(m - mp) - 0.00514 * e * sin(m + mp) +
                0.00208 * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) +
                0.00056 * e * sin(2 * m + mp) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * mp) +
                0.00038 * e * sin(m - 2 * mp) - 0.00024 * e * sin(2 * m - mp) - 0.00017 * sin(om) -
                0.00007 * sin(mp + 2 * m)
            0.50 -> -0.40614 * sin(mp) + 0.17303 * e * sin(m) + 0.01614 * sin(2 * mp) +
                0.01043 * sin(2 * f) + 0.00734 * e * sin(m - mp) - 0.00515 * e * sin(m + mp) +
                0.00209 * e * e * sin(2 * m) - 0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) +
                0.00056 * e * sin(2 * m + mp) - 0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * mp) +
                0.00038 * e * sin(m - 2 * mp) - 0.00024 * e * sin(2 * m - mp) - 0.00017 * sin(om) -
                0.00007 * sin(mp + 2 * m)
            else -> {
                val sgn = if (kind == 0.25) 1.0 else -1.0
                sgn * 0.00306 - 0.62801 * sin(mp) + 0.17172 * e * sin(m) -
                    0.01183 * e * sin(mp + m) + 0.00862 * sin(2 * mp) + 0.00804 * sin(2 * f) +
                    0.00454 * e * sin(m - mp) + 0.00204 * e * e * sin(2 * m) -
                    0.00180 * sin(mp - 2 * f) - 0.00070 * sin(mp + 2 * f) - 0.00040 * sin(3 * mp) -
                    0.00034 * e * sin(2 * mp - m) + 0.00032 * e * sin(m + 2 * mp) +
                    0.00032 * e * sin(m - 2 * mp) - 0.00028 * e * e * sin(mp + 2 * m) +
                    0.00027 * e * sin(2 * m - mp) - 0.00017 * sin(om)
            }
        }
        jde += corr
        // JDE → UTC epoch（ΔT≈70s 忽略，不影响日界判定）；J2000.0 = 2000-01-01T12:00Z
        return ((jde - 2451545.0) * 86_400_000.0).toLong() + 946_728_000_000L
    }

    private fun rad(deg: Double): Double = deg * Math.PI / 180.0
}
