package com.zhisheng.weather.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 枳生天气 · UI 数据模型

@Serializable
data class City(
    val name: String,
    val affiliation: String,
    val latitude: Double,
    val longitude: Double,
    val locationKey: String,
    val street: String? = null,
    val isFavorite: Boolean = false,
) {
    val displayName: String
        get() = listOf(name, street.orEmpty())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("·")

    val contextLabel: String
        get() = listOf(affiliation, street.orEmpty())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
}

// 以下模型全部 @Serializable：离线缓存（WeatherCache）按城市持久化最近一次 WeatherData（v0.0.4）
@Serializable
data class CurrentWeather(
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val condition: WeatherCondition? = null,
    val weatherText: String? = null,
    val humidity: Double? = null,
    val windSpeed: Double? = null,
    val windDirectionDeg: Double? = null,
    val pressure: Double? = null,
    val uvIndex: Int? = null,
    val visibility: Double? = null,
    val dewPoint: Double? = null,
    val cloudCover: Double? = null,
    val windGust: Double? = null,
    val precipMm: Double? = null,
    val profile: WeatherProfile? = null,
)

@Serializable
data class HourlyWeather(
    val timeMillis: Long,
    val temperature: Double? = null,
    val condition: WeatherCondition? = null,
    val windSpeed: Double? = null,
    val precipProb: Int? = null,
    val aqi: Int? = null,
    val profile: WeatherProfile? = null,
    val feelsLike: Double? = null,
    val windDirectionDeg: Double? = null,
    val windGust: Double? = null,
    val precipMm: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val visibility: Double? = null,
    val dewPoint: Double? = null,
    val cloudCover: Double? = null,
    val uvIndex: Int? = null,
)

@Serializable
data class MinutePrecip(
    val timeMillis: Long,
    val precip: Float,
    val phase: PrecipitationPhase = PrecipitationPhase.RAIN,
)

@Serializable
data class RainMeta(
    val source: String,
    val intervalMinutes: Int,
    val updateTime: Long? = null,
    val horizonMinutes: Int = 120,
)

@Serializable
data class YesterdayInfo(
    val high: Double? = null,
    val low: Double? = null,
    val aqi: Int? = null,
    val condition: WeatherCondition? = null,
)

@Serializable
data class TyphoonInfo(
    val name: String? = null,
    val ename: String? = null,
    val type: String? = null,
    val windSpeed: Double? = null,
    val id: String? = null,
    val active: Boolean = true,
    val source: String? = null,
)

@Serializable
data class DailyWeather(
    val dateMillis: Long,
    val high: Double? = null,
    val low: Double? = null,
    val condition: WeatherCondition? = null,
    val windSpeed: Double? = null,
    val precipProbability: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val moonrise: String? = null,
    val moonset: String? = null,
    val moonPhase: String? = null,
    val precipMm: Double? = null,
    // 白天转夜间文案（「晴转雷阵雨」）；仅有一种现象时与 condition.label 相同
    val weatherText: String? = null,
    val profile: WeatherProfile? = null,
    val average: Double? = null,
    val windDirectionDeg: Double? = null,
    val windGust: Double? = null,
    val humidity: Double? = null,
    val cloudCover: Double? = null,
    val uvIndex: Int? = null,
)

@Serializable
data class AqiInfo(
    val value: Int? = null,
    val level: String? = null,
    // AQI 不是全球统一口径。中国、美标、欧洲及供应商自有指数的数值不可直接横比，
    // 因此把实际采用的标准与污染物单位一起带到展示层。
    val standard: String? = null,
    val primary: String? = null,
    val pm25: String? = null,
    val pm10: String? = null,
    val o3: String? = null,
    val no2: String? = null,
    val so2: String? = null,
    val co: String? = null,
    val pollutantUnits: Map<String, String> = emptyMap(),
    // 健康建议文案（小米 suggest，v0.0.4 接入；其余源为空）
    val suggest: String? = null,
)

@Serializable
data class LifeIndexExtra(
    val name: String,
    val en: String,
    val category: String,
)

@Serializable
data class AlertInfo(
    val title: String,
    val detail: String? = null,
    val level: String? = null,
    val pubTime: String? = null,
    // 三源等级归一（和风 severity 英文枚举 / 小米 level 中文），UI 按档着色（v0.0.4）
    val severity: AlertLevel = AlertLevel.UNKNOWN,
)

// 预警四档（国标蓝/黄/橙/红）
@Serializable
enum class AlertLevel {
    BLUE, YELLOW, ORANGE, RED, UNKNOWN
}

// 三源等级归一：中文色名（小米 level「黄色预警」）/ 英文色名（和风 color.code）/
// 英文严重度（和风 severity）/ 彩云 4 位预警码（如 "0902" = 雷电黄色，后两位为级别）。
fun alertLevelOf(raw: String?): AlertLevel {
    val r = raw?.trim().orEmpty()
    if (r.isEmpty()) return AlertLevel.UNKNOWN
    // 彩云 v2.6 alert.content[].code：4 位字符串，前 2 位类型、后 2 位级别
    //（00 白 / 01 蓝 / 02 黄 / 03 橙 / 04 红）。原实现只认色名/英文枚举，
    // 彩云全部预警都落 UNKNOWN、被当成红色渲染（0.0.9-debug 修复）。
    if (r.length == 4 && r.all { it.isDigit() }) {
        return when (r.substring(2)) {
            "00" -> AlertLevel.UNKNOWN // 白色预警（v2.6 新增档），国标四档外不上色
            "01" -> AlertLevel.BLUE
            "02" -> AlertLevel.YELLOW
            "03" -> AlertLevel.ORANGE
            "04" -> AlertLevel.RED
            else -> AlertLevel.UNKNOWN
        }
    }
    return when {
        r.contains("红") || r.equals("red", ignoreCase = true) -> AlertLevel.RED
        r.contains("橙") || r.equals("orange", ignoreCase = true) -> AlertLevel.ORANGE
        r.contains("黄") || r.equals("yellow", ignoreCase = true) -> AlertLevel.YELLOW
        r.contains("蓝") || r.equals("blue", ignoreCase = true) -> AlertLevel.BLUE
        // 和风 severity 英文枚举（无 color.code 时兜底）：国内 minor→蓝 / moderate→黄 / severe→橙 / extreme→红；
        // major/standard 为澳洲等地的补充档，就近归入橙/蓝（0.0.4 修复：此前英文枚举不匹配、全落 UNKNOWN）
        r.equals("extreme", ignoreCase = true) -> AlertLevel.RED
        r.equals("severe", ignoreCase = true) || r.equals("major", ignoreCase = true) -> AlertLevel.ORANGE
        r.equals("moderate", ignoreCase = true) -> AlertLevel.YELLOW
        r.equals("minor", ignoreCase = true) || r.equals("standard", ignoreCase = true) -> AlertLevel.BLUE
        else -> AlertLevel.UNKNOWN
    }
}

@Serializable
data class WeatherData(
    val current: CurrentWeather? = null,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val aqi: AqiInfo? = null,
    val alerts: List<AlertInfo> = emptyList(),
    val updateTime: Long? = null,
    val rainNowcast: String? = null,
    // 未来 24 小时变化摘要，与短时降水文案严格分开，避免放在实况下方时看似自相矛盾。
    val forecastSummary: String? = null,
    val rainMinutes: List<MinutePrecip> = emptyList(),
    // 保留各源真实时间粒度；UI 不再把 5/15 分钟桶伪装成逐分钟数据。
    val rainMeta: RainMeta? = null,
    val carWashOk: Boolean? = null,
    val sportsOk: Boolean? = null,
    val extraIndices: List<LifeIndexExtra> = emptyList(),
    val yesterday: YesterdayInfo? = null,
    val typhoons: List<TyphoonInfo> = emptyList(),
    // 雨区距离（km）：小米分钟降水 kmNum，仅该源返回时非空（v0.0.4）
    val rainDistanceKm: Double? = null,
    val dataSource: String? = null,
    val blockSources: Map<String, String> = emptyMap(),
    val utcOffsetSeconds: Int? = null,
    val error: String? = null,
) {
    fun todayDaily(nowMillis: Long = System.currentTimeMillis()): DailyWeather? {
        val today = cityDate(nowMillis, utcOffsetSeconds)
        return daily.firstOrNull { cityDate(it.dateMillis, utcOffsetSeconds) == today }
    }

    fun tomorrowDaily(nowMillis: Long = System.currentTimeMillis()): DailyWeather? {
        val tomorrow = cityDate(nowMillis, utcOffsetSeconds).plusDays(1)
        return daily.firstOrNull { cityDate(it.dateMillis, utcOffsetSeconds) == tomorrow }
    }

    fun currentAndFutureDaily(nowMillis: Long = System.currentTimeMillis()): List<DailyWeather> {
        val today = cityDate(nowMillis, utcOffsetSeconds)
        return daily.filter { !cityDate(it.dateMillis, utcOffsetSeconds).isBefore(today) }
    }
}

fun cityZone(utcOffsetSeconds: Int?): ZoneId =
    utcOffsetSeconds
        ?.takeIf { it in -18 * 3_600..18 * 3_600 }
        ?.let(ZoneOffset::ofTotalSeconds)
        ?: ZoneId.systemDefault()

fun cityDate(epochMillis: Long, utcOffsetSeconds: Int?): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(cityZone(utcOffsetSeconds)).toLocalDate()

@Serializable
enum class WeatherCondition(val label: String) {
    UNKNOWN("未知"),
    CLEAR("晴"),
    CLEAR_NIGHT("晴"),
    PARTLY_CLOUDY("多云"),
    PARTLY_CLOUDY_NIGHT("多云"),
    CLOUDY("阴"),
    OVERCAST("阴"),
    RAIN("雨"),
    DRIZZLE("小雨"),
    THUNDERSTORM("雷阵雨"),
    HAIL("冰雹"),
    FREEZING_RAIN("冻雨"),
    FREEZING_DRIZZLE("冻毛毛雨"),
    SNOW("雪"),
    SLEET("雨夹雪"),
    FOG("雾"),
    HAZE("霾"),
    SAND("沙尘"),
    WIND("大风");

    val isPrecipitation: Boolean
        get() = this == RAIN || this == DRIZZLE || this == THUNDERSTORM || this == HAIL ||
            this == FREEZING_RAIN || this == FREEZING_DRIZZLE || this == SNOW || this == SLEET

    val significanceRank: Int get() = rank(this)

    companion object {
        // 中国天气现象编码（GB/T 天气现象）。小米 weathercn 源用这套，不是 AccuWeather 图标号。
        fun fromCode(code: String?): WeatherCondition = chinaProfile(code).condition

        fun chinaProfile(code: String?): WeatherProfile {
            val raw = norm(code)
            return when (raw) {
                "0" -> profile(CLEAR, raw, "CHINA")
                "1" -> profile(PARTLY_CLOUDY, raw, "CHINA")
                "2" -> profile(OVERCAST, raw, "CHINA")
                "3" -> profile(RAIN, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN, shower = true)
                "4" -> profile(THUNDERSTORM, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN, shower = true, thunder = true)
                "5" -> profile(HAIL, raw, "CHINA", WeatherIntensity.HEAVY, PrecipitationPhase.HAIL, shower = true, thunder = true)
                "6" -> profile(SLEET, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.MIXED)
                "7", "21" -> profile(DRIZZLE, raw, "CHINA", WeatherIntensity.LIGHT, PrecipitationPhase.RAIN)
                "8", "22" -> profile(RAIN, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN)
                "9", "23" -> profile(RAIN, raw, "CHINA", WeatherIntensity.HEAVY, PrecipitationPhase.RAIN)
                "10", "11", "12", "24", "25" -> profile(RAIN, raw, "CHINA", WeatherIntensity.EXTREME, PrecipitationPhase.RAIN)
                "13" -> profile(SNOW, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW, shower = true)
                "14", "26" -> profile(SNOW, raw, "CHINA", WeatherIntensity.LIGHT, PrecipitationPhase.SNOW)
                "15", "27" -> profile(SNOW, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW)
                "16", "28" -> profile(SNOW, raw, "CHINA", WeatherIntensity.HEAVY, PrecipitationPhase.SNOW)
                "17" -> profile(SNOW, raw, "CHINA", WeatherIntensity.EXTREME, PrecipitationPhase.SNOW)
                "19" -> profile(FREEZING_RAIN, raw, "CHINA", WeatherIntensity.MODERATE, PrecipitationPhase.FREEZING_RAIN, freezing = true)
                "18" -> profile(FOG, raw, "CHINA", WeatherIntensity.LIGHT)
                "32", "57" -> profile(FOG, raw, "CHINA", WeatherIntensity.HEAVY)
                "49", "58" -> profile(FOG, raw, "CHINA", WeatherIntensity.EXTREME)
                "29" -> profile(SAND, raw, "CHINA", WeatherIntensity.LIGHT)
                "30" -> profile(SAND, raw, "CHINA", WeatherIntensity.MODERATE)
                "20", "31" -> profile(SAND, raw, "CHINA", WeatherIntensity.HEAVY)
                "53" -> profile(HAZE, raw, "CHINA", WeatherIntensity.LIGHT)
                "54" -> profile(HAZE, raw, "CHINA", WeatherIntensity.MODERATE)
                "55" -> profile(HAZE, raw, "CHINA", WeatherIntensity.HEAVY)
                "56" -> profile(HAZE, raw, "CHINA", WeatherIntensity.EXTREME)
                else -> profile(UNKNOWN, raw, "CHINA")
            }
        }

        // 小米 locationKey：weathercn 用国标现象码；accu 用 AccuWeather 图标号。两套数字重叠（18=雾 vs 雨），必须按前缀分支。
        fun fromXiaomi(code: String?, locationKey: String?): WeatherCondition =
            xiaomiProfile(code, locationKey).condition

        fun xiaomiProfile(code: String?, locationKey: String?): WeatherProfile =
            if (locationKey.orEmpty().startsWith("accu:", ignoreCase = true)) accuProfile(code)
            else chinaProfile(code)

        fun fromAccu(code: String?): WeatherCondition = accuProfile(code).condition

        fun accuProfile(code: String?): WeatherProfile {
            val raw = norm(code)
            return when (raw) {
                "1", "2" -> profile(CLEAR, raw, "ACCU")
                "33", "34" -> profile(CLEAR_NIGHT, raw, "ACCU")
                "3", "4", "6" -> profile(PARTLY_CLOUDY, raw, "ACCU")
                "35", "36", "38" -> profile(PARTLY_CLOUDY_NIGHT, raw, "ACCU")
                "5", "37" -> profile(HAZE, raw, "ACCU", WeatherIntensity.LIGHT)
                "7", "8" -> profile(OVERCAST, raw, "ACCU")
                "11" -> profile(FOG, raw, "ACCU")
                "12" -> profile(DRIZZLE, raw, "ACCU", WeatherIntensity.LIGHT, PrecipitationPhase.RAIN)
                "13", "14", "39", "40" -> profile(RAIN, raw, "ACCU", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN, shower = true)
                "18" -> profile(RAIN, raw, "ACCU", WeatherIntensity.HEAVY, PrecipitationPhase.RAIN)
                "15", "16", "17", "41", "42" -> profile(THUNDERSTORM, raw, "ACCU", WeatherIntensity.HEAVY, PrecipitationPhase.RAIN, shower = true, thunder = true)
                "19", "20", "21", "22", "23", "43", "44" -> profile(SNOW, raw, "ACCU", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW, shower = raw in setOf("21", "23", "43", "44"))
                "24" -> profile(FREEZING_RAIN, raw, "ACCU", WeatherIntensity.LIGHT, PrecipitationPhase.FREEZING_RAIN, freezing = true)
                "25" -> profile(SLEET, raw, "ACCU", WeatherIntensity.MODERATE, PrecipitationPhase.MIXED)
                "26" -> profile(FREEZING_RAIN, raw, "ACCU", WeatherIntensity.MODERATE, PrecipitationPhase.FREEZING_RAIN, freezing = true)
                "29" -> profile(SLEET, raw, "ACCU", WeatherIntensity.MODERATE, PrecipitationPhase.MIXED)
                "30" -> profile(UNKNOWN, raw, "ACCU", thermal = ThermalModifier.HOT)
                "31" -> profile(UNKNOWN, raw, "ACCU", thermal = ThermalModifier.COLD)
                "32" -> profile(WIND, raw, "ACCU")
                else -> profile(UNKNOWN, raw, "ACCU")
            }
        }

        fun chinaLabel(code: String?): String {
            val n = norm(code) ?: return UNKNOWN.label
            return CHINA_LABELS[n] ?: UNKNOWN.label
        }

        fun xiaomiLabel(code: String?, locationKey: String?): String =
            if (locationKey.orEmpty().startsWith("accu:", ignoreCase = true)) accuLabel(code)
            else chinaLabel(code)

        private fun accuLabel(code: String?): String = when (norm(code)) {
            "5", "37" -> "薄雾"
            "24" -> "冰"
            "26" -> "冻雨"
            "30" -> "炎热"
            "31" -> "寒冷"
            else -> fromAccu(code).label
        }

        fun turnPhrase(fromCode: String?, toCode: String?, locationKey: String? = null): String {
            val a = xiaomiLabel(fromCode, locationKey)
            val b = xiaomiLabel(toCode, locationKey)
            return if (a == b) a else "${a}转${b}"
        }

        fun moreSignificant(a: WeatherCondition, b: WeatherCondition): WeatherCondition =
            if (rank(a) >= rank(b)) a else b

        private fun rank(c: WeatherCondition): Int = when (c) {
            HAIL -> 100
            THUNDERSTORM -> 90
            FREEZING_RAIN -> 85
            FREEZING_DRIZZLE -> 82
            SLEET -> 80
            SNOW -> 75
            RAIN -> 70
            DRIZZLE -> 60
            SAND -> 50
            FOG -> 40
            HAZE -> 35
            WIND -> 30
            OVERCAST -> 20
            CLOUDY -> 18
            PARTLY_CLOUDY, PARTLY_CLOUDY_NIGHT -> 10
            CLEAR, CLEAR_NIGHT -> 0
            UNKNOWN -> -1
        }

        internal fun norm(code: String?): String? {
            val t = code?.trim().orEmpty()
            if (t.isEmpty()) return null
            return t.toIntOrNull()?.toString() ?: t
        }

        private val CHINA_LABELS = mapOf(
            "0" to "晴", "1" to "多云", "2" to "阴",
            "3" to "阵雨", "4" to "雷阵雨", "5" to "雷阵雨伴冰雹",
            "6" to "雨夹雪", "7" to "小雨", "8" to "中雨", "9" to "大雨",
            "10" to "暴雨", "11" to "大暴雨", "12" to "特大暴雨",
            "13" to "阵雪", "14" to "小雪", "15" to "中雪", "16" to "大雪", "17" to "暴雪",
            "18" to "雾", "19" to "冻雨", "20" to "沙尘暴",
            "21" to "小到中雨", "22" to "中到大雨", "23" to "大到暴雨",
            "24" to "暴雨到大暴雨", "25" to "大暴雨到特大暴雨",
            "26" to "小到中雪", "27" to "中到大雪", "28" to "大到暴雪",
            "29" to "浮尘", "30" to "扬沙", "31" to "强沙尘暴",
            "32" to "浓雾", "49" to "强浓雾",
            "53" to "霾", "54" to "中度霾", "55" to "重度霾", "56" to "严重霾",
            "57" to "大雾", "58" to "特强浓雾",
        )

        // 和风 condition：icon 带昼夜变体（100 晴日 / 150 晴夜），code 恒为白天码。
        // 优先 icon，缺失时退回 code（v0.0.2：修复夜间显示太阳）
        fun fromQw(icon: String?, code: String?): WeatherCondition = qwProfile(icon, code).condition

        // 和风天气图标码 → 条件（1xx 白天 / 15x 夜间 / 3xx 雨 / 4xx 雪 / 5xx 视程）
        fun fromQwCode(code: String?): WeatherCondition = qwProfile(code, null).condition

        fun qwProfile(icon: String?, code: String?): WeatherProfile {
            val raw = norm(icon?.takeIf { it.isNotBlank() } ?: code)
            return when (raw) {
                "100" -> profile(CLEAR, raw, "QWEATHER")
                "150" -> profile(CLEAR_NIGHT, raw, "QWEATHER")
                "101", "102", "103" -> profile(PARTLY_CLOUDY, raw, "QWEATHER")
                "151", "152", "153" -> profile(PARTLY_CLOUDY_NIGHT, raw, "QWEATHER")
                "104" -> profile(OVERCAST, raw, "QWEATHER")
                "300", "301", "350", "351" -> profile(RAIN, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN, shower = true)
                "302", "303" -> profile(THUNDERSTORM, raw, "QWEATHER", WeatherIntensity.HEAVY, PrecipitationPhase.RAIN, shower = true, thunder = true)
                "304" -> profile(HAIL, raw, "QWEATHER", WeatherIntensity.HEAVY, PrecipitationPhase.HAIL, shower = true, thunder = true)
                "305", "309" -> profile(DRIZZLE, raw, "QWEATHER", WeatherIntensity.LIGHT, PrecipitationPhase.RAIN)
                "306", "314" -> profile(RAIN, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN)
                "307", "315" -> profile(RAIN, raw, "QWEATHER", WeatherIntensity.HEAVY, PrecipitationPhase.RAIN)
                "308", "310", "311", "312", "316", "317", "318" -> profile(RAIN, raw, "QWEATHER", WeatherIntensity.EXTREME, PrecipitationPhase.RAIN)
                "313" -> profile(FREEZING_RAIN, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.FREEZING_RAIN, freezing = true)
                "399" -> profile(RAIN, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.RAIN)
                "404", "405" -> profile(SLEET, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.MIXED)
                "406", "456" -> profile(SLEET, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.MIXED, shower = true)
                "400", "408" -> profile(SNOW, raw, "QWEATHER", WeatherIntensity.LIGHT, PrecipitationPhase.SNOW)
                "401", "409" -> profile(SNOW, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW)
                "402", "403", "410" -> profile(SNOW, raw, "QWEATHER", WeatherIntensity.HEAVY, PrecipitationPhase.SNOW)
                "407", "457" -> profile(SNOW, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW, shower = true)
                "499" -> profile(SNOW, raw, "QWEATHER", WeatherIntensity.MODERATE, PrecipitationPhase.SNOW)
                "500", "501" -> profile(FOG, raw, "QWEATHER", WeatherIntensity.LIGHT)
                "509", "510", "514", "515" -> profile(FOG, raw, "QWEATHER", if (raw in setOf("510", "515")) WeatherIntensity.EXTREME else WeatherIntensity.HEAVY)
                "502", "511" -> profile(HAZE, raw, "QWEATHER", WeatherIntensity.LIGHT)
                "512" -> profile(HAZE, raw, "QWEATHER", WeatherIntensity.MODERATE)
                "513" -> profile(HAZE, raw, "QWEATHER", WeatherIntensity.HEAVY)
                "503", "504" -> profile(SAND, raw, "QWEATHER", WeatherIntensity.MODERATE)
                "507", "508" -> profile(SAND, raw, "QWEATHER", WeatherIntensity.HEAVY)
                "900" -> profile(UNKNOWN, raw, "QWEATHER", thermal = ThermalModifier.HOT)
                "901" -> profile(UNKNOWN, raw, "QWEATHER", thermal = ThermalModifier.COLD)
                else -> profile(UNKNOWN, raw, "QWEATHER")
            }
        }

        private fun profile(
            condition: WeatherCondition,
            rawCode: String?,
            source: String,
            intensity: WeatherIntensity? = null,
            phase: PrecipitationPhase = PrecipitationPhase.NONE,
            shower: Boolean = false,
            thunder: Boolean = false,
            freezing: Boolean = false,
            thermal: ThermalModifier = ThermalModifier.NONE,
        ) = WeatherProfile(condition, intensity, phase, shower, thunder, freezing, thermal, source, rawCode)
    }
}

@Serializable
enum class WeatherIntensity { LIGHT, MODERATE, HEAVY, EXTREME }

@Serializable
enum class PrecipitationPhase { NONE, RAIN, SNOW, MIXED, FREEZING_RAIN, FREEZING_DRIZZLE, HAIL }

@Serializable
enum class ThermalModifier { NONE, HOT, COLD }

@Serializable
data class WeatherProfile(
    val condition: WeatherCondition,
    val intensity: WeatherIntensity? = null,
    val phase: PrecipitationPhase = PrecipitationPhase.NONE,
    val shower: Boolean = false,
    val thunder: Boolean = false,
    val freezing: Boolean = false,
    val thermal: ThermalModifier = ThermalModifier.NONE,
    val source: String? = null,
    val rawCode: String? = null,
)
