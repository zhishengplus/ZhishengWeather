package com.zhisheng.weather.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

// 短时降水与主屏一句话（v0.0.8）：现在是否在下，只看「此刻」而不是序列里最早的一场雨。
// 天气娘提示分级链（v0.1.5）：预警 > 此刻降水 > 明日温差 > 当前温度 > 大风 > 空气 > 能见度 > 紫外线 > 湿度 > 预报摘要 > 时段闲聊。
// 每一级只用自己的文案池，句子互不复用；温度阈值按摄氏度判断，数值随显示单位输出。
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
        currentConditionBriefing(data, nowMillis)?.let { return it }

        data.forecastSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { summary ->
            val cleaned = tidyCopy(summary)
            val text = if (cleaned.contains("未来")) cleaned else "未来24小时：$cleaned"
            return HeroBriefing(
                text = text,
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
            title.contains("台风") -> "台风影响期间尽量留在室内，远离海边、河岸和临时建筑。"
            title.contains("地质灾害") -> "尽量远离山区沟谷和陡坡，注意落石、滑坡等风险。"
            title.contains("暴雨") || title.contains("强降水") -> "避开低洼路段和积水区域，驾车不要贸然涉水。"
            title.contains("暴雪") -> "雪天路滑，驾车减速，步行远离结冰坡面。"
            title.contains("冰雹") -> "冰雹来临时留在室内，车辆尽量停进遮蔽处。"
            title.contains("雷电") || title.contains("雷暴") -> "雷电来临时尽快进入室内，并远离高处和孤立树木。"
            title.contains("大风") -> "收好阳台和窗边的轻物，远离广告牌和临时搭建物。"
            title.contains("高温") -> "午后减少长时间暴晒，记得及时补水。"
            title.contains("寒潮") || title.contains("低温") -> "气温会明显下降，外出多穿一层，注意保暖。"
            title.contains("大雾") || title.contains("浓雾") -> "能见度可能很低，驾车请开灯、减速并拉开车距。"
            title.contains("道路结冰") -> "路面可能结冰，步行和驾车都要减速防滑。"
            title.contains("沙尘") || title.contains("霾") -> "外出建议戴好口罩，敏感人群尽量缩短户外停留时间。"
            else -> "请留意当地气象部门的最新提示。"
        }
        return "$lead，$action"
    }

    private fun temperatureDeltaBriefing(data: WeatherData, unit: String, nowMillis: Long): HeroBriefing? {
        val today = data.todayDaily(nowMillis)?.high ?: return null
        val tomorrow = data.tomorrowDaily(nowMillis)?.high ?: return null
        val delta = displayTemp(tomorrow, unit) - displayTemp(today, unit)
        if (abs(delta) < 3) return null
        val magnitude = abs(delta)
        val lines = if (delta > 0) when {
            magnitude >= 8 -> listOf(
                "明天最高温会比今天高 ${magnitude}°，中午前后注意防晒补水。",
                "明天升温 ${magnitude}°，白天会更热，衣服可以穿得轻薄些。",
                "明天最高温预计上升 ${magnitude}°，外出尽量避开午后最热的时段。",
                "一夜之间要热 ${magnitude}°，明天记得把防晒和水分都备足。",
                "明天比今天热不少，最高温会高出 ${magnitude}°。",
                "明天热得很明显，最高温预计升 ${magnitude}°，注意防暑。",
            )
            magnitude >= 5 -> listOf(
                "明天最高温会升 ${magnitude}°，中午会比今天热一些。",
                "明天比今天高 ${magnitude}°，可以提前准备轻薄一点的衣服。",
                "明天会暖不少，最高温预计上升 ${magnitude}°。",
                "明天升温 ${magnitude}°，出门记得带水。",
                "明天最高温比今天高 ${magnitude}°，午后记得补水。",
                "明天体感会更暖，最高温约升 ${magnitude}°。",
            )
            else -> listOf(
                "明天最高温会升 ${magnitude}°，白天能感觉到更暖。",
                "明天比今天高 ${magnitude}°，中午可能会热一点。",
                "明天会暖 ${magnitude}°，穿衣可以稍微轻一点。",
                "明天最高温小幅上升 ${magnitude}°，出门不用穿太厚。",
                "明天回暖 ${magnitude}°，早晚还是照常保暖。",
            )
        } else when {
            magnitude >= 8 -> listOf(
                "明天最高温会骤降 ${magnitude}°，厚外套提前准备好。",
                "明天会比今天冷很多，最高温预计下降 ${magnitude}°。",
                "明天降温 ${magnitude}°，早晚出门要多穿一些。",
                "明天冷得明显，最高温比今天低 ${magnitude}°，把厚衣服翻出来。",
                "明天最高温下降 ${magnitude}°，出门多带一层更稳妥。",
                "明天要冷一大截，最高温比今天低 ${magnitude}°。",
            )
            magnitude >= 5 -> listOf(
                "明天会比今天低 ${magnitude}°，今晚把外套备好。",
                "明天会降温 ${magnitude}°，早晚别穿得太单薄。",
                "明天最高温回落 ${magnitude}°，体感会明显转凉。",
                "明天最高温比今天低 ${magnitude}°，出门记得多带一层。",
                "明天转凉，最高温约降 ${magnitude}°，早晚注意保暖。",
                "明天会冷一点，最高温比今天低 ${magnitude}°。",
            )
            else -> listOf(
                "明天会凉 ${magnitude}°，可以带上一件薄外套。",
                "明天最高温小幅回落 ${magnitude}°，早出晚归多带一层。",
                "明天比今天低 ${magnitude}°，体感会稍微凉一些。",
                "明天降温 ${magnitude}°，薄外套就能应付。",
                "明天比今天凉一点，最高温低 ${magnitude}°。",
            )
        }
        return HeroBriefing(
            pickLine(lines, data, nowMillis, salt = 17),
            BriefingKind.TEMPERATURE,
            if (delta > 0) BriefingEmote.HOT else BriefingEmote.COLD,
        )
    }

    private fun currentConditionBriefing(data: WeatherData, nowMillis: Long): HeroBriefing? {
        val current = data.current ?: return null
        val temperature = current.temperature
        val thermalLines = when {
            temperature != null && temperature >= 40.0 -> listOf(
                "现在已是极端高温，非必要不要在午后长时间外出。",
                "外面热得非常厉害，尽量待在凉爽处，及时补水。",
                "正午前后的暴晒很危险，户外活动能免则免。",
                "气温到了极端水平，老人和孩子更要注意防暑。",
                "这种天气里出汗快，水要随身带着。",
            )
            temperature != null && temperature >= 37.0 -> listOf(
                "现在很热，外出要认真防暑并及时补水。",
                "气温已经很高，尽量缩短在户外停留的时间。",
                "午后体感会很热，出门尽量走阴凉处。",
                "长时间暴晒容易不舒服，防晒和饮水都别忘。",
                "今天热得厉害，活动尽量安排在早晚。",
                "外面像蒸笼，找有空调的地方待着更舒服。",
            )
            temperature != null && temperature >= 35.0 -> listOf(
                "现在气温偏高，午后尽量避开长时间暴晒。",
                "今天比较热，出门尽量选择有阴凉的路线。",
                "高温天气里，帽子、防晒和饮用水都很有用。",
                "午后容易晒得不舒服，能错开最热时段就更好。",
                "暑气已经上来了，出门记得把水带上。",
            )
            temperature != null && temperature >= 32.0 -> listOf(
                "现在比较热，活动久了容易出汗，记得及时补水。",
                "今天偏热，轻薄衣服会舒服许多。",
                "气温不低，出门记得带水。",
                "户外体感偏热，晒久了就找个阴凉处休息。",
                "天热起来以后，别等到口渴再喝水。",
            )
            temperature != null && temperature >= 30.0 -> listOf(
                "现在有点热，活动久了容易出汗，记得及时补水。",
                "今天适合轻装出门，也别忘了补水。",
                "体感有些热，在户外待久了记得歇一会儿。",
                "气温刚开始偏热，穿透气一点的衣服更舒服。",
                "天刚热起来，出门带杯水就够。",
            )
            temperature != null && temperature <= -15.0 -> listOf(
                "现在非常寒冷，皮肤不要长时间暴露在外。",
                "气温极低，围巾、帽子和手套都要戴好。",
                "严寒天气容易冻伤，外出时间尽量短一些。",
                "外面冷得刺骨，能不出门就尽量别出门。",
                "这种低温下，耳套和厚袜子都别省。",
            )
            temperature != null && temperature <= -5.0 -> listOf(
                "现在很冷，帽子和手套会派上用场。",
                "低温天气里，出门记得护好领口和手脚。",
                "今天气温很低，多穿一层会更舒服。",
                "手和耳朵容易受冻，外出时记得遮好。",
                "外面冻得结实，出门前把保暖再检查一遍。",
            )
            temperature != null && temperature <= 0.0 -> listOf(
                "气温接近冰点，路面可能结冰，出行多留神脚下。",
                "气温在冰点附近徘徊，步行和驾车都要防滑。",
                "现在很冷，厚外套可以穿上了。",
                "天冷到结冰的份上，早晚出门要穿暖一些。",
                "气温接近冰点，出门小心路滑。",
            )
            temperature != null && temperature <= 5.0 -> listOf(
                "空气里有明显寒意，外出别忘了添一层。",
                "现在比较冷，出门记得把外套穿好。",
                "只穿薄衣服可能会冷，最好再加一件外套。",
                "早晚体感更冷，围巾也可以顺手带上。",
                "气温不算高，出门多带一件总没错。",
                "现在有点凉，注意别感冒。",
            )
            else -> null
        }
        if (thermalLines != null) {
            return HeroBriefing(
                pickLine(thermalLines, data, nowMillis, salt = 29),
                BriefingKind.TEMPERATURE,
                if ((temperature ?: 0.0) >= 30.0) BriefingEmote.HOT else BriefingEmote.COLD,
            )
        }

        val wind = maxOf(current.windSpeed ?: 0.0, current.windGust ?: 0.0)
        when {
            wind >= 62.0 -> return HeroBriefing(
                pickLine(listOf(
                    "风力很强，尽量减少户外停留，远离广告牌和临时搭建物。",
                    "现在风很大，别在高楼、广告牌和树下久留。",
                    "风力已经到了要留神的程度，出门注意高空坠物。",
                    "强风天骑车尤其危险，改乘其他方式更稳妥。",
                    "外面风声不小，门窗和阳台的轻物先收好。",
                ), data, nowMillis, 37),
                BriefingKind.WIND,
                BriefingEmote.WIND,
            )
            wind >= 39.0 -> return HeroBriefing(
                pickLine(listOf(
                    "风力较强，外出留意高空坠物和易被吹动的物品。",
                    "今天风比较大，经过高楼和围挡附近时多留意。",
                    "侧风可能影响骑车和驾车，路上注意控制速度。",
                    "阳台和窗边的轻物记得收好，避免被风吹落。",
                    "风把尘土都吹起来了，敏感的人少在户外久待。",
                ), data, nowMillis, 41),
                BriefingKind.WIND,
                BriefingEmote.WIND,
            )
            wind >= 20.0 || current.condition == WeatherCondition.WIND -> return HeroBriefing(
                pickLine(listOf(
                    "外面有点风，骑车时会比走路更有感觉。",
                    "今天的风不算大，轻的东西别随手放窗边。",
                    "风一阵一阵的，帽子记得戴稳。",
                    "有风但不碍事，出门照常就行。",
                    "起风了，晾在外面的衣服留意一下。",
                ), data, nowMillis, 43),
                BriefingKind.WIND,
                BriefingEmote.WIND,
            )
        }

        val aqi = data.aqi?.value
        when {
            aqi != null && aqi >= 201 -> return HeroBriefing(
                pickLine(listOf(
                    "空气质量较差，今天尽量把运动留在室内。",
                    "空气不好，外出做好防护，回家记得洗脸洗手。",
                    "今天少在户外久留，尤其要照顾好老人和孩子。",
                    "空气差的日子里，出门戴好口罩，回家先换下外套。",
                    "这种空气下跑步得不偿失，运动先停一停。",
                ), data, nowMillis, 47),
                BriefingKind.AIR_QUALITY,
                BriefingEmote.CLOUDY,
            )
            aqi != null && aqi >= 151 -> return HeroBriefing(
                pickLine(listOf(
                    "空气质量不太理想，长时间户外活动可以缓一缓。",
                    "空气有些脏，跑步和散步的时间可以短一些。",
                    "敏感人群今天尽量少在户外久留。",
                    "窗外看着平静，空气质量却不算好，运动先放一放。",
                    "空气不太干净，开窗通风可以等一等。",
                ), data, nowMillis, 49),
                BriefingKind.AIR_QUALITY,
                BriefingEmote.CLOUDY,
            )
            aqi != null && aqi >= 101 -> return HeroBriefing(
                pickLine(listOf(
                    "空气质量一般，敏感人群外出可以少停留一会儿。",
                    "空气略有下降，敏感人群尽量缩短户外活动时间。",
                    "今天空气质量一般，开窗前可以先看一眼空气指数。",
                    "呼吸道比较敏感的话，今天少做长时间户外运动。",
                    "空气刚过良好线，剧烈运动可以缓一缓。",
                ), data, nowMillis, 53),
                BriefingKind.AIR_QUALITY,
                BriefingEmote.CLOUDY,
            )
        }

        current.visibility?.takeIf { it <= 1.0 }?.let {
            val foggy = current.condition in setOf(WeatherCondition.FOG, WeatherCondition.HAZE)
            val lines = if (foggy) listOf(
                "现在雾很大，驾车请开灯、减速、拉开车距。",
                "雾浓的时候看不清路口，慢一点总没错。",
                "大雾天开车别抢行，行人也多留意来车。",
                "雾很重，出门前给路上多留一点时间。",
            ) else listOf(
                "能见度很低，驾车请开灯、降速、拉开车距。",
                "远处可能看不清，开车务必减速并打开车灯。",
                "能见度很低，经过路口和高速路段时更要留神。",
                "视野差的时候，跟车距离要再放大一些。",
            )
            return HeroBriefing(
                pickLine(lines, data, nowMillis, 59),
                BriefingKind.VISIBILITY,
                BriefingEmote.CLOUDY,
            )
        }
        current.visibility?.takeIf { it <= 3.0 }?.let {
            val foggy = current.condition in setOf(WeatherCondition.FOG, WeatherCondition.HAZE)
            val lines = if (foggy) listOf(
                "现在有雾，开车记得开灯慢行。",
                "雾天视线不好，跟车多留一段距离。",
                "有雾的早晨出门别急，给自己多留点时间。",
                "雾还没散，骑车过路口时多按两下铃。",
            ) else listOf(
                "能见度偏低，驾车请把速度放慢一些。",
                "远处有些看不清，开车记得多留一段距离。",
                "视野不够清楚，经过路口时多观察一会儿。",
                "视线有些模糊，变道前多确认几眼。",
            )
            return HeroBriefing(
                pickLine(lines, data, nowMillis, 61),
                BriefingKind.VISIBILITY,
                BriefingEmote.CLOUDY,
            )
        }
        current.uvIndex?.takeIf { it >= 6 }?.let { uv ->
            val text = when {
                uv >= 11 -> pickLine(listOf(
                    "紫外线极强，遮阳和防晒都要认真做好。",
                    "正午前后紫外线很强，尽量减少长时间暴晒。",
                    "紫外线已到极强级别，长时间户外要及时补涂防晒。",
                    "今天的太阳晒不起，帽子、墨镜、防晒都备上。",
                    "这种强度的紫外线，露出来的皮肤都会遭殃。",
                ), data, nowMillis, 67)
                uv >= 8 -> pickLine(listOf(
                    "紫外线很强，午后出门别忘了防晒。",
                    "日晒较强，帽子和防晒霜都可以准备好。",
                    "今天晒得快，长时间户外记得补涂防晒。",
                    "紫外线指数较高，不要低估短时间暴晒的影响。",
                    "阳光有点厉害，露在外面的皮肤都要护好。",
                ), data, nowMillis, 71)
                else -> pickLine(listOf(
                    "紫外线偏强，长时间户外记得防晒。",
                    "在户外待久了容易晒伤，记得做好防护。",
                    "今天可以带上帽子，减少阳光直晒。",
                    "户外活动时间较长时，记得及时补涂防晒。",
                    "太阳开始晒人了，中午出门抹点防晒。",
                ), data, nowMillis, 73)
            }
            return HeroBriefing(text, BriefingKind.UV, BriefingEmote.SUNNY)
        }

        current.humidity?.takeIf { it >= 85.0 && (temperature ?: 0.0) >= 25.0 }?.let {
            return HeroBriefing(
                pickLine(listOf(
                    "空气又潮又热，活动后可能更容易觉得闷。",
                    "今天湿度高，穿轻薄透气的衣服会舒服些。",
                    "湿度偏高，室内也记得适当通风。",
                    "闷热天里出汗多，水要喝得勤一点。",
                    "又湿又热的时候，运动强度别拉太高。",
                    "身上黏糊糊的感觉，就是湿度在作怪。",
                ), data, nowMillis, 79),
                BriefingKind.TEMPERATURE,
                BriefingEmote.HOT,
            )
        }
        current.humidity?.takeIf { it <= 25.0 }?.let {
            return HeroBriefing(
                pickLine(listOf(
                    "空气有些干，水杯和润唇膏都别忘。",
                    "今天空气偏干，记得多喝水，也照顾好嗓子。",
                    "湿度不高，多喝几口水会舒服很多。",
                    "空气比较干燥，皮肤和鼻子都会先有感觉。",
                    "天干物燥，加湿器和保湿都可以用上。",
                    "嘴唇容易起皮的日子，润唇膏放口袋里。",
                ), data, nowMillis, 83),
                BriefingKind.AMBIENT,
                BriefingEmote.SUNNY,
            )
        }
        return null
    }

    private fun ambientBriefing(data: WeatherData, nowMillis: Long): HeroBriefing? {
        val condition = data.current?.condition ?: return null
        val hour = localHour(data, nowMillis)
        val lines = when {
            hour >= 22 || hour < 5 -> listOf(
                "夜深了，天气暂时没有明显变化。",
                "今晚天气比较平稳，可以安心休息。",
                "夜里没有特别要提醒的天气，回家路上注意安全。",
                "现在天气平稳，忙完早点休息。",
                "我还在看着天气，今晚暂时没有特殊提醒。",
                "夜间天气没有明显变化，明早出门前再来看我一眼。",
                "今天辛苦了，今晚的天气没有特别提醒。",
                "夜里天气平稳，愿你一路平安到家。",
            )
            hour < 7 -> listOf(
                "早上好，今天的天气我已经替你看过了。",
                "清晨天气平稳，出门不用着急。",
                "新的一天开始了，出门前记得看看温度。",
                "天气暂时平稳，今天也请照顾好自己。",
                "天刚亮，路上注意安全。",
                "早晨没有特别的天气提醒，可以照常出发。",
                "早上好，今天的出门天气我已经准备好了。",
                "清晨没什么风浪，按自己的节奏开始一天吧。",
            )
            condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) && hour < 10 -> listOf(
                "一早就是晴天，出行可以照常安排。",
                "早上天气晴朗，阳光已经出来了。",
                "今天一早天气不错，户外待久了记得防晒。",
                "现在是晴天，可以按计划出门。",
                "早晨阳光充足，出门记得带好随身物品。",
                "天气晴朗，祝你今天一切顺利。",
            )
            condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) && hour >= 17 -> listOf(
                "傍晚还是晴天，回家的路上视野不错。",
                "现在天气晴朗，傍晚出行可以照常安排。",
                "天色正在变暗，回家路上注意安全。",
                "傍晚天气不错，适合在外面走一走。",
                "现在没有特别的天气提醒，可以慢慢回家。",
                "今天傍晚天气平稳，晚些时候再留意温度变化。",
            )
            condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) -> listOf(
                "现在天气晴朗，出行可以照常安排。",
                "今天是晴天，长时间待在户外记得防晒。",
                "天气不错，户外活动可以按计划进行。",
                "现在是晴天，没有特别需要提醒的天气。",
                "阳光比较充足，出门记得带水。",
                "晴天适合洗晒，外出也比较方便。",
                "目前天气平稳，可以放心安排今天的行程。",
                "天气晴朗，祝你今天顺顺利利。",
            )
            condition in setOf(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT, WeatherCondition.CLOUDY, WeatherCondition.OVERCAST) -> listOf(
                "现在云比较多，光线会柔和一些。",
                "天空云量较多，天气暂时比较平稳。",
                "今天是多云或阴天，出行可以照常安排。",
                "云层比较厚，这会儿天色偏暗也正常。",
                "现在没有明显天气变化，可以按计划出门。",
                "阳光被云挡住了一些，体感可能会比晴天凉。",
                "天气目前平稳，出门前再看一眼降水预报就好。",
                "今天云比较多，我暂时没有特别提醒。",
            )
            condition == WeatherCondition.FOG -> listOf(
                "现在有雾，远处可能看不清，驾车请放慢速度。",
                "雾会影响视线，经过路口时多观察。",
                "能见度可能受雾影响，开车记得打开车灯。",
                "有雾时别急着赶路，拉开车距会更安全。",
            )
            condition == WeatherCondition.HAZE -> listOf(
                "现在有霾，长时间户外活动可以适当减少。",
                "空气有些浑浊，呼吸道敏感的人外出要多注意。",
                "有霾时可以戴好口罩，回家后记得清洁口鼻。",
                "今天有霾，开窗和户外运动前可以先看空气指数。",
            )
            condition == WeatherCondition.SAND -> listOf(
                "风里有沙尘，外出戴好口罩会更舒服。",
                "天空有些灰黄，回家记得清洁口鼻。",
                "有沙尘时尽量关好窗户，减少室内进灰。",
                "空气不够清爽，户外停留时间尽量短一些。",
            )
            condition in setOf(WeatherCondition.RAIN, WeatherCondition.DRIZZLE) -> listOf(
                "雨刚停，路面还湿，出门留意积水。",
                "雨暂时歇了，带把伞有备无患。",
                "现在雨停了，但地滑，走路骑车都慢一点。",
                "雨停之后空气很清新，可以开窗透透气。",
                "这场雨告一段落，出门前再看一眼降水预报。",
                "雨已经停了，晾在外面的东西可以去收回来了。",
            )
            condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE) -> listOf(
                "雪刚停，路面可能结冰，出门留意脚下。",
                "雪暂时停了，融雪的时候会更冷，多穿一点。",
                "雪停了但路滑，老人孩子出门多扶着点。",
                "雪后的空气很干净，保暖还是要做好。",
                "这场雪告一段落，出门前留意路面结冰。",
                "雪刚停，屋檐和树枝上的积雪也要留意。",
            )
            condition in setOf(WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL) -> listOf(
                "附近有雷雨活动，听到雷声就尽快回到室内。",
                "雷雨在附近徘徊，别在高处和树下停留。",
                "雷声一响就收工，安全第一。",
                "天边在打雷，户外的活动先放一放。",
                "雷雨天气里，远离水边和空旷地带。",
            )
            else -> listOf(
                "天气平稳，今天可以照常安排。",
                "没有特别需要提醒的天气，这是个好消息。",
                "目前天气没有明显变化，可以按自己的节奏出发。",
                "天气暂时稳定，今天的计划不用调整。",
                "窗外没有明显变化，出行可以照常。",
                "现在没有特殊天气提醒，放心去忙自己的事吧。",
                "我这边一切正常，天气有变化会第一时间写在这里。",
                "今天的天气很平常，日常安排照旧就好。",
            )
        }
        return HeroBriefing(pickLine(lines, data, nowMillis, salt = 97), BriefingKind.AMBIENT, emoteFor(condition, hour))
    }

    /**
     * 同一小时窗口内保持稳定，避免重组时跳句；跨小时、日期、天气现象和城市会自然轮换。
     * 这里只负责文案轮换，不参与任何天气判断，避免“随机句子”污染气象结论。
     */
    internal fun pickLine(lines: List<String>, data: WeatherData, nowMillis: Long, salt: Int = 0): String {
        require(lines.isNotEmpty())
        val local = Instant.ofEpochMilli(nowMillis)
            .atOffset(ZoneOffset.ofTotalSeconds(data.utcOffsetSeconds ?: 0))
        val seed = local.toLocalDate().toEpochDay() * 31L + local.hour * 7L +
            (data.current?.condition?.ordinal ?: 0) * 13L + salt
        return lines[Math.floorMod(seed, lines.size.toLong()).toInt()]
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
