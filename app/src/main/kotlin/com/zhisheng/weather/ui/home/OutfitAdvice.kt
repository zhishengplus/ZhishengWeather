package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData

internal enum class OutfitWindow(val label: String) { NOW("现在出门"), SIX_HOURS("接下来6小时") }
internal enum class OutfitFeeling(val label: String) { COLD("我怕冷"), NORMAL("刚刚好"), WARM("我怕热") }
internal enum class OutfitActivity(val label: String) { DAILY("日常"), COMMUTE("通勤"), WALK("走动多") }
internal enum class OutfitSlot(val label: String) { BASE("上身"), OUTER("外搭"), BOTTOM("下装"), SHOES("鞋履") }
internal enum class OutfitGarment(val label: String) {
    TEE("透气短袖"), POLO("轻薄POLO"), SHIRT("长袖衬衫"), LONG_TEE("长袖打底"),
    KNIT("针织衫"), SWEATSHIRT("卫衣"), WARM_BASE("保暖打底＋毛衣"),
    NO_COAT("不必加外套"), OVERSHIRT("薄衬衫外搭"), CARDIGAN("薄开衫"),
    SHELL("防风夹克"), JACKET("夹棉外套"), COAT("毛呢外套"), PUFFER("羽绒外套"), DOWN("厚羽绒服"),
    SHORTS("轻薄短裤"), LIGHT_PANTS("轻薄长裤"), PANTS("直筒长裤"), WARM_PANTS("加厚长裤"),
    THERMAL_PANTS("保暖内层＋长裤"), SNEAKERS("透气运动鞋"), CLOSED_SHOES("舒适包脚鞋"),
    RAIN_SHOES("防水防滑鞋"), BOOTS("保暖防滑靴")
}
internal enum class OutfitNeed(val label: String) { WARMTH("保暖"), BREATHABLE("透气"), WIND("挡风"), RAIN("防雨"), SNOW("防雨雪"), SUN("遮阳"), GRIP("防滑") }
internal data class OutfitGear(val slot: OutfitSlot, val choices: List<OutfitGarment>, val reason: String)
internal data class OutfitExtra(val label: String, val kind: String)
internal data class OutfitPlan(
    val temperature: Double, val low: Double, val high: Double, val usesFeelsLike: Boolean,
    val mixedTemperatureBasis: Boolean, val level: Int, val title: String, val summary: String,
    val gear: List<OutfitGear>, val extras: List<OutfitExtra>, val needs: List<OutfitNeed>,
    val notes: List<String>, val windKmh: Double?, val rainProbability: Int?, val uv: Int?,
    val forecastUntil: Long?, val forecastCount: Int, val freshnessNote: String?,
    val window: OutfitWindow, val caution: String?, val gustKmh: Double? = null,
)
internal data class OutfitAdvice(val plan: OutfitPlan? = null, val unavailable: String? = null)

/** Reference bands: Shenzhen Meteorological Bureau health-weather service, accessed 2026-11-02.
 * Clothing combinations are examples, not a supplier index or a personal heat-balance calculation.
 * Canonical model units are Celsius and km/h. Never apply wind chill twice to provider feelsLike. */
internal fun outfitTemperatureLevel(celsius: Double): Int = when {
    celsius >= 29 -> 0; celsius >= 24 -> 1; celsius >= 21 -> 2; celsius >= 18 -> 3
    celsius >= 15 -> 4; celsius >= 11 -> 5; celsius >= 6 -> 6; else -> 7
}

private data class OutfitSample(val time: Long, val temp: Double, val feels: Boolean,
    val condition: WeatherCondition?, val wind: Double?, val gust: Double?, val mm: Double?, val probability: Int?, val uv: Int?)
private fun Double?.outfitTemperature(): Double? = this?.takeIf { it.isFinite() && it in -90.0..65.0 }
private fun Double?.outfitFeels(): Double? = this?.takeIf { it.isFinite() && it in -100.0..80.0 }
private fun Double?.outfitWind(): Double? = this?.takeIf { it.isFinite() && it in 0.0..350.0 }
private fun Double?.outfitRain(): Double? = this?.takeIf { it.isFinite() && it in 0.0..1000.0 }

internal fun outfitAdvice(data: WeatherData, now: Long, window: OutfitWindow = OutfitWindow.NOW,
    feeling: OutfitFeeling = OutfitFeeling.NORMAL, activity: OutfitActivity = OutfitActivity.DAILY): OutfitAdvice {
    val hour = 3_600_000L
    val stamp = data.moduleEvidence["current"]?.observationTime ?: data.updateTime ?: data.fetchedAt
    val validStamp = stamp?.takeIf { it <= now + 15 * 60_000L }
    val age = validStamp?.let { (now - it).coerceAtLeast(0) }
    if (age != null && age > 12 * hour) return OutfitAdvice(unavailable = "天气已过期，刷新首页天气后再搭配")
    val current = data.current?.takeIf { age == null || age <= 3 * hour }?.let { c ->
        val feels = c.feelsLike.outfitFeels(); val temperature = feels ?: c.temperature.outfitTemperature()
        temperature?.let { OutfitSample(now, it, feels != null, c.condition, c.windSpeed.outfitWind(),
            c.windGust.outfitWind(), c.precipMm.outfitRain(), null, c.uvIndex?.takeIf { uv -> uv in 0..50 }) }
    }
    val end = now + if (window == OutfitWindow.NOW) 90 * 60_000L else 6 * hour
    // Use actual forecast timestamps, never list positions or today's daily maximum/minimum.
    val future = data.hourly.filter { it.timeMillis in now..end }.groupBy { it.timeMillis }
        .mapNotNull { (_, sameHour) ->
            val temperatures = sameHour.mapNotNull { it.feelsLike.outfitFeels() ?: it.temperature.outfitTemperature() }.distinct()
            if (temperatures.size != 1) null else sameHour.firstOrNull { it.feelsLike.outfitFeels() != null || it.temperature.outfitTemperature() != null }
        }.sortedBy { it.timeMillis }.map { h ->
            val feels = h.feelsLike.outfitFeels()
            OutfitSample(h.timeMillis, feels ?: h.temperature.outfitTemperature()!!, feels != null, h.condition,
                h.windSpeed.outfitWind(), h.windGust.outfitWind(), h.precipMm.outfitRain(), h.precipProb?.takeIf { it in 0..100 }, h.uvIndex?.takeIf { it in 0..50 })
        }
    val samples = if (window == OutfitWindow.NOW) listOfNotNull(current ?: future.firstOrNull()) else listOfNotNull(current) + future
    if (samples.isEmpty()) return OutfitAdvice(unavailable = "还缺少温度数据，刷新首页天气后生成穿搭")
    val cold = samples.minOf { it.temp }; val warm = samples.maxOf { it.temp }
    val temperature = samples.first().temp
    val baseLevel = outfitTemperatureLevel(cold)
    var level = (baseLevel + when (feeling) { OutfitFeeling.COLD -> 1; OutfitFeeling.WARM -> -1; else -> 0 }).coerceIn(0, 7)
    if (activity == OutfitActivity.WALK && cold > 6) level = (level - 1).coerceAtLeast(0)
    // A preference is not a reason to recommend summer clothing in freezing weather.
    if (cold <= 0) level = 7
    val wind = samples.mapNotNull { it.wind }.maxOrNull()
    val gust = samples.mapNotNull { it.gust }.maxOrNull()
    val exposedWind = maxOf(wind ?: 0.0, gust ?: 0.0)
    val windy = exposedWind >= 20.0
    val strongWind = exposedWind >= 39.0
    val probability = samples.mapNotNull { it.probability }.maxOrNull()
    val raining = samples.any { it.condition?.isPrecipitation == true || (it.mm ?: 0.0) >= .1 }
    val snow = samples.any { it.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE) }
    val rain = raining || (probability ?: 0) >= 40
    val thunder = samples.any { it.condition in setOf(WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL) }
    val uv = samples.mapNotNull { sample -> sample.uv?.takeUnless {
        it > 0 && sample.condition in setOf(WeatherCondition.CLEAR_NIGHT, WeatherCondition.PARTLY_CLOUDY_NIGHT)
    } }.maxOrNull() // Never substitute a daily UV maximum for the current night.
    val sun = (uv ?: 0) >= 3
    val needs = buildList {
        if (level >= 5) add(OutfitNeed.WARMTH) else if (level <= 1) add(OutfitNeed.BREATHABLE)
        if (windy) add(OutfitNeed.WIND); if (rain) add(if(snow) OutfitNeed.SNOW else OutfitNeed.RAIN); if (sun) add(OutfitNeed.SUN)
        if (snow || rain && cold <= 3) add(OutfitNeed.GRIP)
    }
    val base = when (level) {
        0, 1 -> listOf(OutfitGarment.TEE, OutfitGarment.POLO)
        2, 3 -> listOf(OutfitGarment.SHIRT, OutfitGarment.LONG_TEE)
        4, 5 -> listOf(OutfitGarment.KNIT, OutfitGarment.SWEATSHIRT)
        else -> listOf(OutfitGarment.WARM_BASE)
    }
    val outer = when {
        level >= 7 -> if(cold <= -10) listOf(OutfitGarment.DOWN) else listOf(OutfitGarment.DOWN, OutfitGarment.PUFFER)
        level == 6 -> listOf(OutfitGarment.PUFFER, OutfitGarment.COAT)
        level == 5 -> listOf(OutfitGarment.JACKET, OutfitGarment.COAT)
        windy || rain -> listOf(OutfitGarment.SHELL)
        level == 4 -> listOf(OutfitGarment.SHELL, OutfitGarment.CARDIGAN)
        level == 3 -> listOf(OutfitGarment.CARDIGAN, OutfitGarment.OVERSHIRT)
        warm - cold >= 6 -> listOf(OutfitGarment.OVERSHIRT)
        else -> listOf(OutfitGarment.NO_COAT)
    }
    val bottom = when {
        level >= 7 -> listOf(OutfitGarment.THERMAL_PANTS)
        level >= 5 -> listOf(OutfitGarment.WARM_PANTS)
        level >= 2 -> listOf(OutfitGarment.PANTS)
        level == 0 && !sun && activity != OutfitActivity.COMMUTE -> listOf(OutfitGarment.SHORTS, OutfitGarment.LIGHT_PANTS)
        else -> listOf(OutfitGarment.LIGHT_PANTS)
    }
    val shoes = when {
        snow || level >= 7 -> listOf(OutfitGarment.BOOTS)
        rain -> listOf(OutfitGarment.RAIN_SHOES)
        level <= 1 -> listOf(OutfitGarment.SNEAKERS)
        else -> listOf(OutfitGarment.CLOSED_SHOES, OutfitGarment.SNEAKERS)
    }
    val gear = listOf(
        OutfitGear(OutfitSlot.BASE, base, if (level >= 5) "内层保暖，进入室内可减少外层" else "贴身层舒适透气，出汗后及时增减"),
        OutfitGear(OutfitSlot.OUTER, outer, when { rain && level >= 5 -> "保暖层遇雨雪还需防水罩层，别让衣物淋湿"; rain -> "选有防雨能力的外层，普通夹克未必防水"; windy -> "外层挡风，比一味加厚内层更合适"; warm-cold >= 6 -> "温差较大，外层方便随时穿脱"; outer.first() == OutfitGarment.NO_COAT -> "天气偏暖，外套按室内空调情况随身备"; else -> "用可穿脱的外层应对室内外温差" }),
        OutfitGear(OutfitSlot.BOTTOM, bottom, if (level >= 5) "腿部也要保暖，别只顾上半身" else if (sun) "轻薄长裤兼顾遮挡与透气" else "宽松舒适，按活动量选择厚薄"),
        OutfitGear(OutfitSlot.SHOES, shoes, if (snow || rain) "看鞋底纹路与抓地，积水和结冰处放慢脚步" else "优先合脚、透气，走路多时留意缓震"),
    )
    val extras = buildList {
        if (rain && !snow) add(OutfitExtra(if (strongWind) "带帽雨衣" else "随身雨具", if (strongWind) "raincoat" else "umbrella"))
        if (sun) { if(level<6) add(OutfitExtra("遮阳帽", "sunhat")); add(OutfitExtra("太阳镜", "glasses")) }
        if (level>=6) add(OutfitExtra("保暖帽", "warmhat"))
        if (level >= 7) { add(OutfitExtra("围巾与手套", "scarf")); add(OutfitExtra("保暖袜", "socks")) }
        else if (level >= 6) add(OutfitExtra("围巾", "scarf"))
    }
    val notes = buildList {
        if (warm-cold >= 6) add("这几小时冷暖变化明显，外层选方便穿脱的款式。")
        if (feeling != OutfitFeeling.NORMAL) add(if (feeling == OutfitFeeling.COLD) "已考虑怕冷偏好，保暖优先；温度读数没有改变。" else "已考虑怕热偏好，优先方便穿脱；寒冷时仍保留必要保暖。")
        if (activity == OutfitActivity.WALK) add("走动多会更容易出汗，停下后及时加回外层。")
        if (activity == OutfitActivity.COMMUTE) add("室内、车厢与户外温差大，尽量分层穿。")
        if (rain && strongWind) add("风较大，雨衣比撑伞更便于行动；强风中优先避险。")
        if (sun) add("户外配合衣物遮挡与遮阴，裸露部位注意防晒。")
        if (samples.none { it.feels }) add("未提供体感温度，暂按气温参考；风雨另行提示。")
    }
    val caution = when {
        thunder -> "有雷雨或冰雹影响，先到安全室内，装备不能代替避险。"
        exposedWind >= 62 -> "风力很强，减少空旷处停留，优先查看当地预警。"
        cold <= -10 -> "天气很冷，减少长时间暴露，护好手、耳朵和脚。"
        warm >= 35 -> "天气炎热，避开高温时段，及时休息补水。"
        data.alerts.isNotEmpty() -> "当地有天气预警，出门前先确认预警内容。"
        else -> null
    }
    val title = listOf("轻装，留点清凉", "短袖正合适", "轻薄长袖就好", "带一件薄外搭", "长袖配一层挡风", "叠穿，暖得刚好", "保暖外套上场", "今天认真保暖")[level]
    val summary = when { caution != null -> caution; snow -> "雨雪天，把保暖与防滑一起照顾到"; rain && windy -> "挡风防雨，鞋底也要稳"; rain -> "保暖之外，别忘了防雨".takeIf { level >= 5 } ?: "外层防雨，鞋履防滑"; warm-cold >= 6 -> "一套分层搭配，应对几小时的冷暖"; sun -> "轻薄透气，也留好遮阳的位置"; level >= 5 -> "上身、腿部和脚部一起照顾到"; else -> "按这套出门，再随冷暖微调" }
    val freshness = when { current == null && window == OutfitWindow.NOW -> "依据近期逐时预报"; age == null -> "缺少数据发布时间，搭配仅供参考"; age > hour -> "天气数据已超过1小时，出门前建议刷新"; else -> null }
    return OutfitAdvice(OutfitPlan(temperature, cold, warm, samples.any { it.feels }, samples.any { it.feels } && samples.any { !it.feels }, level,
        title, summary, gear, extras, needs, notes, wind, probability, uv, future.lastOrNull()?.time,
        if (window == OutfitWindow.SIX_HOURS) future.size else 0, freshness, window, caution, gustKmh=gust))
}

internal val outfitReferences = listOf(
    "体感与穿衣分级 · 深圳市气象局" to "https://weather.sz.gov.cn/qixiangfuwu/zhuanxiangfuwu/jiankangqixiangfuwu/",
    "分层穿衣 · 中国天气网" to "https://www.weather.com.cn/anhui/tqyw/04/2308379.shtml",
    "紫外线与遮阳 · 世界卫生组织" to "https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index/",
)
