package com.zhisheng.weather.ui

import com.zhisheng.weather.model.cityZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// 显示格式工具：温度/风速/气压单位换算在此统一生效
object Fmt {

    private val hourFormatter = DateTimeFormatter.ofPattern("H时")
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    private val dayFormatter = DateTimeFormatter.ofPattern("d日", Locale.CHINA)
    private val monthFormatter = DateTimeFormatter.ofPattern("M月", Locale.CHINA)

    fun zoneId(utcOffsetSeconds: Int?): ZoneId = cityZone(utcOffsetSeconds)

    private fun zone(utcOffsetSeconds: Int?): ZoneId = zoneId(utcOffsetSeconds)

    fun hour(epochMillis: Long, utcOffsetSeconds: Int?): String =
        hourFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun dailyDayLabel(
        epochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        utcOffsetSeconds: Int?,
    ): String {
        val target = Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds))
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone(utcOffsetSeconds)).toLocalDate()
        if (target.toLocalDate() == today) return "今天"
        return when (target.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    // 兼容旧调用；“今天”不再由列表下标决定。
    fun weekday(epochMillis: Long, index: Int, utcOffsetSeconds: Int?): String =
        dailyDayLabel(epochMillis, System.currentTimeMillis(), utcOffsetSeconds)

    fun dayOfMonth(epochMillis: Long, utcOffsetSeconds: Int?): String =
        dayFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun month(epochMillis: Long, utcOffsetSeconds: Int?): String =
        monthFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun isDifferentMonth(firstMillis: Long, secondMillis: Long, utcOffsetSeconds: Int?): Boolean {
        val first = Instant.ofEpochMilli(firstMillis).atZone(zone(utcOffsetSeconds))
        val second = Instant.ofEpochMilli(secondMillis).atZone(zone(utcOffsetSeconds))
        return first.year != second.year || first.monthValue != second.monthValue
    }

    fun date(epochMillis: Long, utcOffsetSeconds: Int?): String =
        dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun clock(epochMillis: Long, utcOffsetSeconds: Int?): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun stamp(epochMillis: Long, utcOffsetSeconds: Int?): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun coordinates(latitude: Double, longitude: Double): String {
        val latitudeDirection = if (latitude < 0.0) "S" else "N"
        val longitudeDirection = if (longitude < 0.0) "W" else "E"
        return String.format(
            Locale.US,
            "%.2f%s  %.2f%s",
            abs(latitude),
            latitudeDirection,
            abs(longitude),
            longitudeDirection,
        )
    }

    fun temp(celsius: Double?, unit: String): String? = celsius?.let {
        (if (unit == "f") it * 9.0 / 5.0 + 32.0 else it).roundToInt().toString()
    }

    fun unitSuffix(unit: String): String = if (unit == "f") "°F" else "°C"

    // 百分比只接受模型约定的 0..100；异常源值不进入 UI，避免 6000% 撑坏布局。
    fun probability(value: Int?): String? = value
        ?.takeIf { it in 1..100 }
        ?.let { "$it%" }

    // 内部一律以 km/h 存储，展示时换算
    fun wind(kmh: Double?, unit: String): String? = kmh?.let {
        when (unit) {
            "ms" -> String.format(Locale.US, "%.1f m/s", it / 3.6)
            "bft" -> "${beaufort(it)} 级"
            else -> "${it.roundToInt()} km/h"
        }
    }

    // 只要数值，用于逐时那种空间紧张的地方
    fun windValue(kmh: Double?, unit: String): String? = kmh?.let {
        when (unit) {
            "ms" -> String.format(Locale.US, "%.1f", it / 3.6)
            "bft" -> beaufort(it).toString()
            else -> it.roundToInt().toString()
        }
    }

    fun windUnitLabel(unit: String): String = when (unit) {
        "ms" -> "风速 m/s"
        "bft" -> "风力 级"
        else -> "风速 km/h"
    }

    /** 逐时窄列使用蒲福风级，读数完整且不会被 km/h 长单位挤压。 */
    fun windForce(kmh: Double?): String? = kmh?.let { "${beaufort(it)}级" }

    // 蒲福风级（按 km/h 上界切分）
    private fun beaufort(kmh: Double): Int = when {
        kmh < 1.0 -> 0
        kmh < 6.0 -> 1
        kmh < 12.0 -> 2
        kmh < 20.0 -> 3
        kmh < 29.0 -> 4
        kmh < 39.0 -> 5
        kmh < 50.0 -> 6
        kmh < 62.0 -> 7
        kmh < 75.0 -> 8
        kmh < 89.0 -> 9
        kmh < 103.0 -> 10
        kmh < 118.0 -> 11
        else -> 12
    }

    // 内部以 hPa 存储
    fun pressure(hpa: Double?, unit: String): String? = hpa?.let {
        when (unit) {
            "mmhg" -> String.format(Locale.US, "%.0f mmHg", it * 0.750062)
            "inhg" -> String.format(Locale.US, "%.2f inHg", it * 0.02953)
            else -> "${it.roundToInt()} hPa"
        }
    }

    // 和风月相 ID → 中文（该字段不随 lang 参数翻译，需本地映射）
    fun moonPhaseZh(en: String?): String? = en?.let {
        when (it.lowercase().replace(' ', '-')) {
            "new-moon" -> "新月"
            "waxing-crescent" -> "娥眉月"
            "first-quarter" -> "上弦月"
            "waxing-gibbous" -> "盈凸月"
            "full-moon" -> "满月"
            "waning-gibbous" -> "亏凸月"
            "last-quarter" -> "下弦月"
            "waning-crescent" -> "残月"
            else -> en
        }
    }
}
