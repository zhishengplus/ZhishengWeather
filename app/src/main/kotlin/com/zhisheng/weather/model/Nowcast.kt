package com.zhisheng.weather.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

// 短时降水与主屏一句话（v0.0.8）：现在是否在下，只看「此刻」而不是序列里最早的一场雨。
data class RainTiming(
    val rainingNow: Boolean,
    val minutesUntilStart: Int?,
    val minutesUntilEnd: Int? = null,
) {
    val hasRain: Boolean get() = rainingNow || minutesUntilStart != null
}

enum class BriefingKind {
    ALERT,
    PRECIPITATION,
    TEMPERATURE,
    WIND,
    AIR_QUALITY,
    VISIBILITY,
    UV,
    FORECAST,
    AMBIENT,
}

enum class BriefingEmote {
    SUNNY,
    CLOUDY,
    RAIN,
    HOT,
    COLD,
    WIND,
    NIGHT,
    ALERT,
}

data class HeroBriefing(
    val text: String,
    val kind: BriefingKind,
    val emote: BriefingEmote,
    val alertLevel: AlertLevel? = null,
)

object Nowcast {
    const val WET_THRESHOLD = 0.02f
    const val NOW_WINDOW_MS = 2 * 60_000L
    const val MINUTE_MS = 60_000L
    private const val STOP_DRY_CONFIRM_MS = 8 * MINUTE_MS

    fun accumulatedMmToRate(valueMm: Float, periodMinutes: Int): Float {
        if (!valueMm.isFinite() || valueMm <= 0f || periodMinutes <= 0) return 0f
        return valueMm * (60f / periodMinutes)
    }

    fun minuteSeries(
        values: List<Float>,
        startMillis: Long,
        stepMs: Long = MINUTE_MS,
        phase: PrecipitationPhase = PrecipitationPhase.RAIN,
    ): List<MinutePrecip> {
        if (values.isEmpty() || stepMs <= 0L) return emptyList()
        return values.mapIndexed { i, v ->
            MinutePrecip(startMillis + i * stepMs, v.coerceAtLeast(0f), phase)
        }
    }

    // 把 15 分钟粒度（Open-Meteo）等稀采样拉成逐分钟柱，和风/小米的 120 点图视觉一致。
    // 超出最后一个采样点的分钟沿用该点（区间数据：15 分钟桶代表随后一刻钟）。
    fun densifyToMinutes(
        points: List<MinutePrecip>,
        horizonMin: Int = 120,
    ): List<MinutePrecip> {
        if (points.size < 2 || horizonMin <= 0) return points
        val sorted = points.sortedBy { it.timeMillis }
        val start = sorted.first().timeMillis
        val out = ArrayList<MinutePrecip>(horizonMin)
        var i = 0
        for (m in 0 until horizonMin) {
            val t = start + m * MINUTE_MS
            while (i + 1 < sorted.size && sorted[i + 1].timeMillis <= t) i++
            out.add(MinutePrecip(t, sorted[i].precip.coerceAtLeast(0f), sorted[i].phase))
        }
        return out
    }

    fun horizonLabel(points: List<MinutePrecip>): String {
        if (points.size < 2) return "120 分钟"
        val mins = ((points.last().timeMillis - points.first().timeMillis) / MINUTE_MS).toInt()
            .coerceAtLeast(1)
        return if (mins >= 120) "2 小时" else "$mins 分钟"
    }

    fun intensityLabel(rateMmPerHour: Float): String = when {
        rateMmPerHour < WET_THRESHOLD -> "无降水"
        rateMmPerHour < 2.5f -> "小雨"
        rateMmPerHour < 8f -> "中雨"
        rateMmPerHour < 16f -> "大雨"
        else -> "强降水"
    }

    fun sourceLabel(source: String?): String = when (source?.uppercase()) {
        "QWEATHER" -> "和风"
        "CAIYUN" -> "彩云"
        "XIAOMI" -> "小米"
        "OPEN-METEO" -> "公共源"
        "SIMULATION" -> "模拟"
        else -> source?.takeIf { it.isNotBlank() } ?: "天气源"
    }

    fun rainTiming(
        minutes: List<MinutePrecip>,
        nowMillis: Long,
        wet: Float = WET_THRESHOLD,
        currentPrecip: Boolean = false,
    ): RainTiming {
        val sorted = minutes.sortedBy { it.timeMillis }
        val seriesNow = seriesWetAt(sorted, nowMillis, wet)
        val rainingNow = currentPrecip || seriesNow
        if (rainingNow) {
            val end = if (seriesNow) minutesUntilDry(sorted, nowMillis, wet) else null
            return RainTiming(true, 0, end)
        }
        val start = minutesUntilWet(sorted, nowMillis, wet)
        return RainTiming(false, start, null)
    }

    fun rainTimingLabel(timing: RainTiming): String? = when {
        timing.rainingNow && timing.minutesUntilEnd != null ->
            "${timing.minutesUntilEnd} 分钟后雨会停"
        timing.rainingNow -> "正在下雨"
        timing.minutesUntilStart != null -> "${timing.minutesUntilStart} 分钟后开始下雨"
        else -> null
    }

    fun briefing(data: WeatherData, unit: String, nowMillis: Long): HeroBriefing? {
        highestAlert(data.alerts)?.let { alert ->
            return HeroBriefing(
                text = alertActionLine(alert),
                kind = BriefingKind.ALERT,
                emote = BriefingEmote.ALERT,
                alertLevel = alert.severity,
            )
        }

        val precipNow = data.current.let { cur ->
            cur != null && (cur.condition?.isPrecipitation == true || (cur.precipMm ?: 0.0) > 0.05)
        }
        val timing = rainTiming(data.rainMinutes, nowMillis, currentPrecip = precipNow)
        val api = data.rainNowcast?.trim()?.takeIf { it.isNotEmpty() }?.let { tidyCopy(it) }

        if (timing.rainingNow) {
            val snowing = data.current?.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET)
            val text = when {
                timing.minutesUntilEnd != null && snowing -> "${timing.minutesUntilEnd} 分钟后雪会停"
                timing.minutesUntilEnd != null -> rainTimingLabel(timing)!!
                snowing -> "正在下雪"
                api != null && !isDryNowcast(api) -> api
                else -> rainTimingLabel(timing)!!
            }
            return HeroBriefing(text, BriefingKind.PRECIPITATION, BriefingEmote.RAIN)
        }
        if (timing.minutesUntilStart != null) {
            return HeroBriefing(
                text = rainTimingLabel(timing)!!,
                kind = BriefingKind.PRECIPITATION,
                emote = BriefingEmote.RAIN,
            )
        }
        if (api != null && looksLikeIncomingRain(api)) {
            return HeroBriefing(api, BriefingKind.PRECIPITATION, BriefingEmote.RAIN)
        }

        temperatureDeltaBriefing(data, unit, nowMillis)?.let { return it }
        currentConditionBriefing(data)?.let { return it }

        data.forecastSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { summary ->
            return HeroBriefing(
                text = "未来24小时：${tidyCopy(summary)}",
                kind = BriefingKind.FORECAST,
                emote = emoteFor(data.current?.condition, localHour(data, nowMillis)),
            )
        }

        return ambientBriefing(data, nowMillis)
    }

    fun briefingLine(data: WeatherData, unit: String, nowMillis: Long): String? =
        briefing(data, unit, nowMillis)?.text

    // 分钟降水卡：只在接下来一段时间真有雨，或雨带近到值得看时出现。
    fun shouldShowPrecipCard(data: WeatherData, nowMillis: Long): Boolean {
        val precipNow = data.current.let { cur ->
            cur != null && (cur.condition?.isPrecipitation == true || (cur.precipMm ?: 0.0) > 0.05)
        }
        val timing = rainTiming(data.rainMinutes, nowMillis, currentPrecip = precipNow)
        if (timing.hasRain) return true
        val api = data.rainNowcast?.trim()?.takeIf { it.isNotEmpty() }
        if (api != null && looksLikeIncomingRain(api)) return true
        val km = data.rainDistanceKm ?: return false
        return km in 0.0..40.0
    }

    // 无柱且此刻也没雨才走晴窗；正在下雨时即使序列被裁空，也不能画成 CLEAR WINDOW。
    fun precipCardClearWindow(
        minutes: List<MinutePrecip>,
        nowMillis: Long,
        precipNow: Boolean,
    ): Boolean {
        val timing = rainTiming(minutes, nowMillis, currentPrecip = precipNow)
        return precipChartCeiling(minutes) <= 0f && !timing.hasRain
    }

    // 任何源只要确实返回当前/未来短时序列就展示；全 0 代表“有数据且未来无雨”，
    // 空列表才代表当前源没有这项能力或请求失败。
    fun shouldShowPrecipModule(data: WeatherData, nowMillis: Long): Boolean {
        val hasUsableSeries = data.rainMinutes.any { it.timeMillis >= nowMillis - NOW_WINDOW_MS }
        return hasUsableSeries || shouldShowPrecipCard(data, nowMillis)
    }

    // 分钟图按实际峰值选离散标尺，弱降水不会被固定 0.3 mm/h 的上限压成细线。
    fun precipChartCeiling(points: List<MinutePrecip>): Float {
        val max = points.maxOfOrNull { it.precip.coerceAtLeast(0f) } ?: 0f
        return when {
            max <= 0f -> 0f
            max <= 0.05f -> 0.05f
            max <= 0.1f -> 0.1f
            max <= 0.25f -> 0.25f
            max <= 0.5f -> 0.5f
            max <= 1f -> 1f
            max <= 2f -> 2f
            max <= 5f -> 5f
            else -> kotlin.math.ceil(max.toDouble()).toFloat()
        }
    }

    fun tidyCopy(text: String): String =
        text.trim().trimEnd('~', '～').trimEnd()

    internal fun isDryNowcast(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.contains("不会下雨") || text.contains("无降水") || text.contains("不会有雨") ||
            text.contains("无降雨") || text.contains("没有雨")
    }

    internal fun looksLikeIncomingRain(text: String): Boolean {
        if (text.isEmpty()) return false
        if (isDryNowcast(text)) return false
        if (text.contains("以外") || text.contains("远离")) return false
        return text.contains("雨") || text.contains("雪") || text.contains("降水")
    }

    internal fun seriesWetAt(
        minutes: List<MinutePrecip>,
        nowMillis: Long,
        wet: Float = WET_THRESHOLD,
    ): Boolean {
        if (minutes.isEmpty()) return false
        val window = minutes.filter { abs(it.timeMillis - nowMillis) <= NOW_WINDOW_MS }
        if (window.isNotEmpty()) return window.any { it.precip >= wet }
        val first = minutes.first()
        return first.timeMillis > nowMillis &&
            first.timeMillis - nowMillis <= NOW_WINDOW_MS &&
            first.precip >= wet
    }

    private fun minutesUntilWet(
        minutes: List<MinutePrecip>,
        nowMillis: Long,
        wet: Float,
    ): Int? {
        val firstWet = minutes.firstOrNull { it.timeMillis > nowMillis + NOW_WINDOW_MS && it.precip >= wet }
            ?: return null
        val mins = ((firstWet.timeMillis - nowMillis + 30_000L) / MINUTE_MS).toInt().coerceAtLeast(1)
        return mins
    }

    private fun minutesUntilDry(
        minutes: List<MinutePrecip>,
        nowMillis: Long,
        wet: Float,
    ): Int? {
        val after = minutes.filter { it.timeMillis >= nowMillis }
        if (after.isEmpty()) return null
        var dryStart: MinutePrecip? = null
        for (p in after) {
            if (p.precip < wet) {
                if (dryStart == null) dryStart = p
                if (p.timeMillis - dryStart.timeMillis >= STOP_DRY_CONFIRM_MS) {
                    return ((dryStart!!.timeMillis - nowMillis + 30_000L) / MINUTE_MS).toInt()
                        .coerceAtLeast(1)
                }
            } else {
                dryStart = null
            }
        }
        if (dryStart != null && after.last().precip < wet) {
            return ((dryStart!!.timeMillis - nowMillis + 30_000L) / MINUTE_MS).toInt()
                .coerceAtLeast(1)
        }
        return null
    }

    private fun highestAlert(alerts: List<AlertInfo>): AlertInfo? =
        alerts.firstOrNull { it.severity == AlertLevel.RED }
            ?: alerts.firstOrNull { it.severity == AlertLevel.ORANGE }
            ?: alerts.firstOrNull { it.severity == AlertLevel.YELLOW }
            ?: alerts.firstOrNull { it.severity == AlertLevel.BLUE }
            ?: alerts.firstOrNull()

    private fun alertActionLine(alert: AlertInfo): String {
        val lead = when (alert.severity) {
            AlertLevel.RED -> "红色预警生效中"
            AlertLevel.ORANGE -> "橙色预警生效中"
            AlertLevel.YELLOW -> "黄色预警生效中"
            AlertLevel.BLUE -> "蓝色预警生效中"
            AlertLevel.UNKNOWN -> "天气预警生效中"
        }
        val title = alert.title
        val action = when {
            title.contains("地质灾害") -> "山区、沟谷和陡坡附近请多留意。"
            title.contains("暴雨") || title.contains("强降水") -> "低洼路段和积水区域请谨慎通行。"
            title.contains("雷电") || title.contains("雷暴") -> "户外活动请留意雷电和短时大风。"
            title.contains("大风") || title.contains("台风") -> "请收好易被吹动的物品，远离临时搭建物。"
            title.contains("高温") -> "午后减少长时间暴晒，记得及时补水。"
            title.contains("寒潮") || title.contains("低温") -> "气温变化明显，外出请做好保暖。"
            title.contains("大雾") || title.contains("浓雾") -> "能见度可能偏低，驾车请放慢速度。"
            title.contains("道路结冰") -> "路面可能湿滑结冰，出行请注意防滑。"
            title.contains("沙尘") || title.contains("霾") -> "外出请做好防护，敏感人群减少久留。"
            else -> "请留意当地气象部门的最新提示。"
        }
        return "$lead，$action"
    }

    private fun temperatureDeltaBriefing(data: WeatherData, unit: String, nowMillis: Long): HeroBriefing? {
        val today = data.todayDaily(nowMillis)?.high ?: return null
        val tomorrow = data.tomorrowDaily(nowMillis)?.high ?: return null
        val delta = displayTemp(tomorrow, unit) - displayTemp(today, unit)
        if (abs(delta) < 3) return null
        return if (delta > 0) {
            HeroBriefing("明天会比今天高 ${delta}°，热意会更明显一些。", BriefingKind.TEMPERATURE, BriefingEmote.HOT)
        } else {
            HeroBriefing("明天会比今天低 ${-delta}°，今晚把外套备好。", BriefingKind.TEMPERATURE, BriefingEmote.COLD)
        }
    }

    private fun currentConditionBriefing(data: WeatherData): HeroBriefing? {
        val current = data.current ?: return null
        val temperature = current.temperature
        when {
            temperature != null && temperature >= 35.0 ->
                return HeroBriefing("热意很重，午后尽量避开长时间暴晒。", BriefingKind.TEMPERATURE, BriefingEmote.HOT)
            temperature != null && temperature >= 30.0 ->
                return HeroBriefing("今天会有些热，出门记得给自己留点阴凉。", BriefingKind.TEMPERATURE, BriefingEmote.HOT)
            temperature != null && temperature <= 0.0 ->
                return HeroBriefing("寒意很实在，外出前把保暖再检查一遍。", BriefingKind.TEMPERATURE, BriefingEmote.COLD)
            temperature != null && temperature <= 5.0 ->
                return HeroBriefing("空气里有明显寒意，外出别忘了添一层。", BriefingKind.TEMPERATURE, BriefingEmote.COLD)
        }

        val wind = maxOf(current.windSpeed ?: 0.0, current.windGust ?: 0.0)
        when {
            wind >= 39.0 -> return HeroBriefing(
                "风力较强，外出留意高空坠物和易被吹动的物品。",
                BriefingKind.WIND,
                BriefingEmote.WIND,
            )
            wind >= 20.0 || current.condition == WeatherCondition.WIND -> return HeroBriefing(
                "风会有些明显，帽子和轻便物品记得收好。",
                BriefingKind.WIND,
                BriefingEmote.WIND,
            )
        }

        val aqi = data.aqi?.value
        when {
            aqi != null && aqi >= 151 -> return HeroBriefing(
                "空气质量不太理想，长时间户外活动可以缓一缓。",
                BriefingKind.AIR_QUALITY,
                BriefingEmote.CLOUDY,
            )
            aqi != null && aqi >= 101 -> return HeroBriefing(
                "空气质量一般，敏感人群外出可以少停留一会儿。",
                BriefingKind.AIR_QUALITY,
                BriefingEmote.CLOUDY,
            )
        }

        current.visibility?.takeIf { it <= 3.0 }?.let {
            return HeroBriefing(
                "能见度偏低，驾车请把速度放慢一些。",
                BriefingKind.VISIBILITY,
                BriefingEmote.CLOUDY,
            )
        }
        current.uvIndex?.takeIf { it >= 6 }?.let { uv ->
            val text = if (uv >= 8) "紫外线很强，午后出门别忘了防晒。" else "阳光有些锋利，长时间户外记得防晒。"
            return HeroBriefing(text, BriefingKind.UV, BriefingEmote.SUNNY)
        }
        return null
    }

    private fun ambientBriefing(data: WeatherData, nowMillis: Long): HeroBriefing? {
        val condition = data.current?.condition ?: return null
        val hour = localHour(data, nowMillis)
        val lines = when {
            hour >= 22 || hour < 5 -> listOf(
                "夜色渐深，城市也安静了一些。",
                "夜已经深了，窗外暂时没有特别的变化。",
                "天气安静地守在窗外，今晚可以慢一点。",
            )
            condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) && hour < 10 -> listOf(
                "晨光已经铺开，今天从清朗里开始。",
                "天色正慢慢亮起来，天气没有特别的脾气。",
                "清晨的光很干净，今天可以从容出发。",
            )
            condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) -> listOf(
                "天色清朗，风景也显得格外利落。",
                "阳光把城市照得很清楚，天气正平稳。",
                "天空今天没什么脾气，按原计划出发吧。",
            )
            condition in setOf(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT, WeatherCondition.CLOUDY, WeatherCondition.OVERCAST) -> listOf(
                "云层收住了光线，天空显得安静一些。",
                "云在慢慢铺开，天气暂时没有明显变化。",
                "光线柔和下来，今天适合按自己的节奏走。",
            )
            else -> listOf(
                "天气平稳，今天可以照常安排。",
                "没有特别需要提醒的天气，这本身就是好消息。",
                "一切都在正常变化，按自己的节奏出发吧。",
            )
        }
        val localDate = Instant.ofEpochMilli(nowMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(data.utcOffsetSeconds ?: 0))
            .toLocalDate()
        val index = Math.floorMod(localDate.toEpochDay().toInt() + condition.ordinal, lines.size)
        return HeroBriefing(lines[index], BriefingKind.AMBIENT, emoteFor(condition, hour))
    }

    private fun localHour(data: WeatherData, nowMillis: Long): Int =
        Instant.ofEpochMilli(nowMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(data.utcOffsetSeconds ?: 0))
            .hour

    private fun emoteFor(condition: WeatherCondition?, hour: Int): BriefingEmote = when {
        hour >= 22 || hour < 5 -> BriefingEmote.NIGHT
        condition in setOf(WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL, WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.SNOW, WeatherCondition.SLEET) -> BriefingEmote.RAIN
        condition == WeatherCondition.WIND -> BriefingEmote.WIND
        condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) -> BriefingEmote.SUNNY
        else -> BriefingEmote.CLOUDY
    }

    private fun displayTemp(celsius: Double, unit: String): Int =
        if (unit == "f") (celsius * 9.0 / 5.0 + 32.0).roundToInt() else celsius.roundToInt()
}
