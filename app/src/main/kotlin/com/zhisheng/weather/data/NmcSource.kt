package com.zhisheng.weather.data

import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.AlertLevel
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherLocationMatch
import com.zhisheng.weather.model.alertLevelOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 中央气象台（nmc.cn）免密钥数据源。实况/7 天/日高低温曲线/过去 24 小时与预警
 * 来自 /rest/weather 等 JSON 端点；逐 3 小时内嵌在城市预报页 HTML 中，用结构化
 * 正则提取（页面结构多年稳定，但不是 API 契约，解析失败按缺数据处理，不让整源
 * 失败）。
 *
 * 覆盖范围：中国省/市/县三级行政区（无街道级）；无分钟级降水、无生活指数、
 * 紫外线/能见度/露点/云量/阵风——对应模块按“缺数据即隐藏”的规则处理；
 * AQI 仅大城市站有；月相/月升月落由 MoonCalc 本地补齐；AUTO 链中小米失败时
 * 作为国内第二跳，也可在设置里直接锁定本源。
 */
object NmcSource {

    internal const val SOURCE_ID = "NMC"
    private const val CHINA_ZONE = "GMT+8"

    /** 省级简称 → nmc 全称的归一（nmc 用“内蒙古自治区”这类全称）。 */
    private val provinceAliases = mapOf(
        "内蒙古" to "内蒙古自治区",
        "广西" to "广西壮族自治区",
        "西藏" to "西藏自治区",
        "宁夏" to "宁夏回族自治区",
        "新疆" to "新疆维吾尔自治区",
        "香港" to "香港特别行政区",
        "澳门" to "澳门特别行政区",
    )

    private fun provinceNameVariants(name: String): List<String> {
        val short = provinceAliases.entries.firstOrNull { it.value == name }?.key
            ?: name.removeSuffix("省").removeSuffix("市")
                .removeSuffix("自治区").removeSuffix("特别行政区")
        return listOf(name, short).distinct().filter { it.length >= 2 }
    }

    private val alarmProvincePrefix =
        Regex("^(.+?(?:省|自治区|特别行政区)|(?:北京|天津|上海|重庆)市?)")

    /** 一个已解析的 nmc 城市指向：省级码 + 站点码 + 预报页路径。 */
    internal data class NmcCityRef(
        val pcode: String,
        val scode: String,
        val pageUrl: String,
        val provinceName: String,
        val cityName: String,
    )

    suspend fun fetch(city: City): WeatherData {
        return try {
            val ref = withContext(Dispatchers.IO) { resolveCity(city) }
                ?: return WeatherData(error = "中央气象台暂无「${city.displayName}」的区县级数据，可换用小米或 Open-Meteo 源")
            coroutineScope {
                val fullDeferred = async { NmcApi.weather(ref.scode) }
                val pageDeferred = async { NmcApi.forecastHtml(ref.pageUrl) }
                val alarmDeferred = async { NmcApi.alarms() }

                val full = fullDeferred.await()
                val pageHtml = pageDeferred.await()
                val real = full?.real
                val alerts = parseNmcAlarms(alarmDeferred.await(), ref)
                // 过去实况先解析：小站 real 气压常为 9999，需用同站最近整点的测站气压兜底。
                val hourlyPast = parseNmcPassed(full?.passedchart.orEmpty())
                val current = real?.let {
                    parseNmcReal(it, pressureFallbackHpa = hourlyPast.lastOrNull()?.pressure)
                }
                // 7 天用 /rest/weather 的结构化 JSON（含日降水量）；tempchart 兜底
                // 晚间 9999 的今天高低温；月相与月升月落由本地天文计算补齐（源不提供）。
                val daily = parseNmcPredict(full?.predict, real?.sunriseSunset, full?.tempchart.orEmpty())
                    .map { MoonCalc.enrich(it, city.latitude, city.longitude) }
                val hourlyFuture = pageHtml?.let { parseNmcHourly(it) }.orEmpty()
                val hourly = (hourlyPast + hourlyFuture).sortedBy { it.timeMillis }

                if (current == null && daily.isEmpty()) {
                    return@coroutineScope WeatherData(error = "中央气象台数据请求失败，稍后自动重试")
                }

                val publishMs = real?.publish_time?.let { parseBeijingMillis(it) }
                WeatherData(
                    current = current,
                    hourly = hourly,
                    daily = daily,
                    aqi = parseNmcAir(NmcApi.decodeAir(full?.air)),
                    alerts = alerts,
                    updateTime = publishMs,
                    dataSource = SOURCE_ID,
                    blockSources = mapOf(
                        "current" to SOURCE_ID,
                        "hourly" to SOURCE_ID,
                        "daily" to SOURCE_ID,
                    ),
                    locationMatch = WeatherLocationMatch(
                        requestedLatitude = city.latitude,
                        requestedLongitude = city.longitude,
                        providerLatitude = city.latitude,
                        providerLongitude = city.longitude,
                        preciseGps = false,
                        matchedLatitude = city.latitude,
                        matchedLongitude = city.longitude,
                    ),
                    // nmc 仅覆盖中国，全部按北京时间。
                    utcOffsetSeconds = 28800,
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            WeatherData(error = WeatherRepository.userFacingFetchError("中央气象台"))
        }
    }

    // —— 城市匹配：省（含简称）→ 市/区县（剥离行政后缀）——
    // nmc 只有部分省份（海南等直管县体制）提供区县级站点；普通省份只到地级市，
    // 因此区县匹配失败时回退父级地级市（金川区→金昌），不让精确定位整页失败。
    internal suspend fun resolveCitySync(
        city: City,
        provinces: List<NmcApi.NmcProvince>,
        cityLookup: suspend (String) -> List<NmcApi.NmcCityEntry>?,
    ): NmcCityRef? {
        val province = matchProvince(city.affiliation, city.name, provinces) ?: return null
        val entries = cityLookup(province.code) ?: return null
        val wanted = stripDistrictSuffix(city.name)
        if (wanted.isNotEmpty()) {
            entries.firstOrNull { stripDistrictSuffix(it.city) == wanted }?.let {
                return NmcCityRef(province.code, it.code, it.url, province.name, it.city)
            }
            entries.firstOrNull { entry ->
                val entryName = stripDistrictSuffix(entry.city)
                entryName.isNotEmpty() && (wanted.contains(entryName) || entryName.contains(wanted))
            }?.let {
                return NmcCityRef(province.code, it.code, it.url, province.name, it.city)
            }
        }
        // 父级地级市回退：affiliation 去省名后的串，与任一站点名做前缀匹配。
        val rest = stripProvincePrefix(city.affiliation, province.name)
        if (rest.length >= 2) {
            entries.firstOrNull { entry ->
                val entryName = stripDistrictSuffix(entry.city)
                entryName.length >= 2 && rest.startsWith(entryName)
            }?.let {
                return NmcCityRef(province.code, it.code, it.url, province.name, it.city)
            }
        }
        return null
    }

    /** 去掉 affiliation 开头的省名（全称或简称），返回剩余部分。 */
    internal fun stripProvincePrefix(affiliation: String?, provinceName: String): String {
        val aff = affiliation?.trim().orEmpty()
        if (aff.isEmpty()) return ""
        var rest = aff
        provinceNameVariants(provinceName).firstOrNull { rest.startsWith(it) }
            ?.let { rest = rest.substring(it.length) }
        return rest.trim().trimStart('·', ',', '，', '-', '/', ' ')
    }

    private suspend fun resolveCity(city: City): NmcCityRef? {
        val provinces = NmcApi.provinces() ?: return null
        return resolveCitySync(city, provinces) { NmcApi.cities(it) }
    }

    internal fun matchProvince(
        affiliation: String?,
        cityName: String?,
        provinces: List<NmcApi.NmcProvince>,
    ): NmcApi.NmcProvince? {
        val haystack = listOfNotNull(affiliation, cityName).joinToString(" ")
        provinces.firstOrNull { p -> haystack.contains(p.name) }?.let { return it }
        provinces.firstOrNull { p ->
            provinceNameVariants(p.name).any { haystack.contains(it) }
        }?.let { return it }
        // 直辖市：城市名即省名（“北京市”）
        provinces.firstOrNull { p -> p.name == cityName || provinceAliases[cityName?.trim()] == p.name }?.let { return it }
        return null
    }

    internal fun stripDistrictSuffix(name: String): String {
        var n = name.trim()
        listOf("特别行政区", "自治州", "自治县", "地区", "林区", "盟", "市", "区", "县", "旗").forEach { suffix ->
            if (n.length > suffix.length + 1 && n.endsWith(suffix)) {
                n = n.removeSuffix(suffix)
                return@forEach
            }
        }
        return n.trim()
    }

    // —— 实况 JSON → CurrentWeather ——
    // nmc 用 9999 表示"该字段无数据"（晚间预报把当天昼段整个置 9999；
    // 小站无气压/无 AQI 也用空串或 9999）。一律按缺失处理，绝不显示给用户。
    internal fun nmcText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && it != "9999" && it != "-" }

    internal fun parseNmcReal(real: NmcApi.NmcReal, pressureFallbackHpa: Double? = null): CurrentWeather? {
        val w = real.weather ?: return null
        val info = nmcText(w.info)?.takeUnless { it == WeatherCondition.UNKNOWN.label }
        val codedCondition = WeatherCondition.fromCode(nmcText(w.img))
        // Some stations have valid phenomenon text but no usable icon code. Match known
        // Chinese phenomenon names exactly; arbitrary text must never become sunny weather.
        val condition = codedCondition.takeUnless { it == WeatherCondition.UNKNOWN }
            ?: nmcConditionNames[info]
            ?: WeatherCondition.UNKNOWN
        val wind = real.wind
        val pressure = w.airpressure?.takeIf { it in 250.0..1100.0 }
            // 小站 real 气压常为 9999，但同一站的 passedchart 最近整点有测站气压
            //（金昌 852hPa）——同源同站，直接兜底，别让"气压"格整格消失。
            ?: pressureFallbackHpa
        return CurrentWeather(
            temperature = w.temperature?.takeIf { it in -90.0..60.0 },
            feelsLike = w.feelst?.takeIf { it in -100.0..70.0 }
                ?: w.temperature?.takeIf { it in -100.0..70.0 },
            condition = condition,
            weatherText = info ?: condition.takeUnless { it == WeatherCondition.UNKNOWN }?.label ?: "天气现象暂缺",
            humidity = w.humidity?.takeIf { it in 0.0..100.0 },
            // 实况风速是 m/s，App 内部一律 km/h。
            windSpeed = wind?.speed?.takeIf { it in 0.0..90.0 }?.times(3.6),
            windDirectionDeg = wind?.degree?.takeIf { it in 0.0..360.0 },
            pressure = pressure,
            precipMm = w.rain?.takeIf { it in 0.0..1000.0 },
        )
    }

    private val nmcConditionNames: Map<String, WeatherCondition> =
        ((0..58).map(Int::toString) + listOf("301", "302"))
            .mapNotNull { code ->
                val condition = WeatherCondition.fromCode(code)
                val label = WeatherCondition.chinaLabel(code)
                if (condition == WeatherCondition.UNKNOWN || label == WeatherCondition.UNKNOWN.label) null
                else label to condition
            }.toMap()

    // —— 预警：全国列表按省/市过滤 ——
    internal fun parseNmcAlarms(alarms: List<NmcApi.NmcAlarm>, ref: NmcCityRef): List<AlertInfo> {
        if (alarms.isEmpty()) return emptyList()
        val cityShort = stripDistrictSuffix(ref.cityName)
        val provinceNames = provinceNameVariants(ref.provinceName)
        return alarms.mapNotNull { alarm ->
            val parsed = parseAlarmTitle(alarm.title) ?: return@mapNotNull null
            val (area, level, kind) = parsed
            // 同名市/区县不能越过标题中明确的省级属地。
            val declaredProvince = alarmProvincePrefix.find(area)?.value
            if (declaredProvince != null && provinceNameVariants(declaredProvince).none { it in provinceNames }) {
                return@mapNotNull null
            }
            // area 是发布台属地（“海南省万宁”/“海南省”/“上海市宝山”）。判定只能用
            // 正向包含与省级等值：反向“城市名.contains(area)”会让省级标题剥出的
            // 空地名命中任何城市（真机事故：上海收到海南省级预警）。
            val hit = (cityShort.isNotEmpty() && area.contains(cityShort)) ||
                area in provinceNames || area.removeSuffix("省") in provinceNames
            if (!hit) return@mapNotNull null
            AlertInfo(
                title = alarm.title,
                level = "${level}预警",
                pubTime = alarm.issuetime,
                severity = alertLevelOf("${level}预警"),
                type = kind,
            )
        }.take(3)
    }

    internal fun parseAlarmTitle(title: String): Triple<String, String, String>? {
        // 例：“海南省万宁市气象台发布暴雨红色预警信号”“海南省气象台发布…”
        //（省级标题的 area=“海南省”，保留省名交由调用方按省等值判定，绝不剥成空串）。
        val m = Regex("^(.+?)气象台发布(.+?)([蓝黄橙红])色预警").find(title) ?: return null
        val area = m.groupValues[1].trim()
        if (area.isEmpty()) return null
        val kind = m.groupValues[2]
        val level = m.groupValues[3]
        return Triple(area, level, kind)
    }

    // —— 7 天预报（/rest/weather 的 predict.detail 结构化 JSON，昼/夜分段 + 日降水量） ——
    internal fun parseNmcPredict(
        predict: NmcApi.NmcPredict?,
        sun: NmcApi.NmcSun?,
        tempchart: List<NmcApi.NmcTemp> = emptyList(),
    ): List<DailyWeather> {
        val detail = predict?.detail ?: return emptyList()
        val temps = tempchart.mapNotNull { row ->
            val date = parseSlashDate(row.time) ?: return@mapNotNull null
            date to row
        }.toMap()
        return detail.mapIndexedNotNull { index, day ->
            val date = day.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapIndexedNotNull null
            // 晚间 nmc 把"今天"的昼段整个置 9999（白天已过），其余字段照常。
            // 9999 一律按缺失处理，否则整行显示"9999转晴"这类脏文本。
            val dayInfo = nmcText(day.day?.weather?.info)
            val nightInfo = nmcText(day.night?.weather?.info)
            val dayTemp = nmcTemperature(day.day?.weather?.temperature)
            val nightTemp = nmcTemperature(day.night?.weather?.temperature)
            // 今天昼段缺失时用日高低温曲线（实况口径，含当天已出现的最高/最低）兜底，
            // 至少不出现 "今天 —°"。
            val chart = temps[date]
            val dayCondition = WeatherCondition.fromCode(nmcText(day.day?.weather?.img))
            val nightCondition = WeatherCondition.fromCode(nmcText(day.night?.weather?.img))
            // 全天图标与生活建议必须覆盖夜间天气，不能让白天的晴遮住晚间雷雨。
            val condition = WeatherCondition.moreSignificant(dayCondition, nightCondition)
            DailyWeather(
                dateMillis = date.atStartOfDay(java.time.ZoneId.of(CHINA_ZONE)).toInstant().toEpochMilli(),
                high = (dayTemp ?: chart?.max_temp?.takeIf { it in -90.0..60.0 }),
                low = (nightTemp ?: chart?.min_temp?.takeIf { it in -90.0..60.0 }),
                condition = condition,
                weatherText = when {
                    dayInfo == null -> nightInfo
                    nightInfo == null || dayInfo == nightInfo -> dayInfo
                    else -> "${dayInfo}转${nightInfo}"
                },
                windSpeed = windPowerToSpeed(nmcText(day.day?.wind?.power)),
                windDirectionDeg = windDirToDegree(nmcText(day.day?.wind?.direct)),
                precipMm = day.precipitation?.takeIf { it >= 0.0 },
                // 首日带出官方日出日落（原格式 “2026-09-14 05:53”）。
                sunrise = if (index == 0) sun?.sunrise?.substringAfter(' ', missingDelimiterValue = sun.sunrise ?: "") else null,
                sunset = if (index == 0) sun?.sunset?.substringAfter(' ', missingDelimiterValue = sun.sunset ?: "") else null,
            )
        }
    }

    /** "2026/09/14" → LocalDate；tempchart 专用。 */
    private fun parseSlashDate(text: String?): LocalDate? = try {
        text?.let { LocalDate.parse(it.replace('/', '-')) }
    } catch (_: Exception) {
        null
    }

    /** 昼/夜温度是字符串且可能是 "" 或 "9999"。 */
    private fun nmcTemperature(raw: String?): Double? =
        raw?.trim()?.takeIf { it.isNotEmpty() && it != "9999" }?.toDoubleOrNull()
            ?.takeIf { it in -90.0..60.0 }

    /** 空气质量：仅部分站点提供（大城市有，小站为空串）。 */
    internal fun parseNmcAir(air: NmcApi.NmcAir?): com.zhisheng.weather.model.AqiInfo? {
        val value = air?.aqi?.trim()?.toIntOrNull()?.takeIf { it in 0..1000 } ?: return null
        return com.zhisheng.weather.model.AqiInfo(
            value = value,
            level = air?.text?.takeIf(String::isNotBlank) ?: WeatherRepository.aqiLevel(value),
            standard = "中国",
        )
    }

    /** 过去 24 小时逐时实况：2 小时窗内取最近两条（App 层对更早逐时按宽限窗裁剪）。 */
    internal fun parseNmcPassed(
        passed: List<NmcApi.NmcPassed>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<HourlyWeather> {
        val earliest = nowMillis - 2 * 3_600_000L
        return passed.mapNotNull { p ->
            val t = parseBeijingMillis(p.time ?: return@mapNotNull null) ?: return@mapNotNull null
            if (t > nowMillis || t < earliest) return@mapNotNull null
            HourlyWeather(
                timeMillis = t,
                // 9999 是无效哨兵值，按字段合理域过滤。
                temperature = p.temperature?.takeIf { kotlin.math.abs(it) < 90.0 },
                humidity = p.humidity?.takeIf { it in 0.0..100.0 },
                pressure = p.pressure?.takeIf { it in 300.0..1200.0 },
                windDirectionDeg = p.windDirection?.takeIf { it in 0.0..360.0 },
                windSpeed = p.windSpeed?.takeIf { it in 0.0..90.0 }?.times(3.6),
                precipMm = p.rain1h?.takeIf { it >= 0.0 },
            )
        }.sortedBy { it.timeMillis }.takeLast(2)
    }

    // —— 逐 3 小时（页面 id=hourValues 区块） ——
    // 页面把未来 7 天按"日容器"分组、每组 8 段；相邻容器有重叠（后一容器的前
    // 四段与前一容器尾部重复），跨零点首段带 "N日" 前缀。解析：①以 "N日HH:MM"
    // 为锚点、其余按单调递增推日期；②落不进（上一段, 下一锚点）窗口的块是重复
    // 段，跳过；③图标 URL 尾段即国标现象码（经晴/多云/阴/小中雨的时段交叉验证），
    // 逐段给出真实天气现象。全量保留 56 段。
    internal fun parseNmcHourly(html: String, nowMillis: Long = System.currentTimeMillis()): List<HourlyWeather> {
        val start = html.indexOf("id=hourValues")
        if (start < 0) return emptyList()
        val section = html.substring(start, minOf(html.length, start + 200_000))
        val matches = Regex("class=\"?hour3").findAll(section).toList()
        if (matches.isEmpty()) return emptyList()

        data class Raw(
            val dayNum: Int?, val hour: Int, val minute: Int,
            val temp: Double?, val speedKmh: Double?, val dir: String?,
            val pressure: Double?, val humidity: Double?, val prob: Int?,
            val condition: WeatherCondition?,
        )
        val raws = matches.mapIndexedNotNull { i, m ->
            val blockEnd = matches.getOrNull(i + 1)?.range?.first ?: section.length
            val block = section.substring(m.range.first, minOf(blockEnd, m.range.first + 1200))
            val label = Regex("<div>\\s*(?:(\\d{1,2})日)?(\\d{1,2}):(\\d{2})\\s*</div>").find(block)
                ?: return@mapIndexedNotNull null
            val values = Regex("<div[^>]*>\\s*([^<>]*?)\\s*</div>")
                .findAll(block)
                .map { it.groupValues[1] }
                .filter { it.isNotEmpty() && !it.contains("px") }
                .toList()
            if (values.isEmpty()) return@mapIndexedNotNull null
            val temp = values.firstOrNull { it.endsWith("°") || it.endsWith("℃") }
                ?.trimEnd('°', '℃')?.toDoubleOrNull()?.takeIf { it in -90.0..60.0 }
            val speed = values.firstOrNull { it.endsWith("m/s") }
                ?.removeSuffix("m/s")?.toDoubleOrNull()?.takeIf { it in 0.0..90.0 }?.times(3.6)
            val dir = values.firstOrNull { it.endsWith("风") && it != "微风" && it.length <= 4 }
            val pressure = values.firstOrNull { it.endsWith("hPa") }
                ?.removeSuffix("hPa")?.toDoubleOrNull()?.takeIf { it in 300.0..1200.0 }
            val percents = values.filter { it.endsWith("%") }
            val humidity = percents.getOrNull(0)?.removeSuffix("%")?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
            val prob = percents.getOrNull(1)?.removeSuffix("%")?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            // 尾段兼容不同前缀（实测 "40x40/3/0.png" 的 3 是模板号、0 是国标码）。
            val code = Regex("40x40/[^/\"]+/(\\d+)\\.png").find(block)?.groupValues?.get(1)
            Raw(
                dayNum = label.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull(),
                hour = label.groupValues[2].toInt(),
                minute = label.groupValues[3].toInt(),
                temp = temp, speedKmh = speed, dir = dir,
                pressure = pressure, humidity = humidity, prob = prob,
                condition = WeatherCondition.fromCode(code).takeIf { it != WeatherCondition.UNKNOWN },
            )
        }
        if (raws.isEmpty()) return emptyList()

        val zone = java.time.ZoneId.of(CHINA_ZONE)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // 显式 "N日" 锚点的绝对日期；日号回退即跨月（9/30 之后出现 1 日 → 10 月）。
        val anchorDates = HashMap<Int, LocalDate>()
        var year = today.year
        var month = today.monthValue
        var prevDayNum = 0
        raws.forEachIndexed { i, raw ->
            val n = raw.dayNum ?: return@forEachIndexed
            if (n < prevDayNum) {
                month += 1
                if (month > 12) { month = 1; year += 1 }
            }
            prevDayNum = n
            anchorDates[i] = LocalDate.of(year, month, n)
        }

        val out = mutableListOf<HourlyWeather>()
        var last: Long? = null
        raws.forEachIndexed { i, raw ->
            val explicitDate = anchorDates[i]
            var candidate = if (explicitDate != null) {
                explicitDate.atTime(raw.hour, raw.minute).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
            } else {
                val baseDate = last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
                var c = baseDate.atTime(raw.hour, raw.minute).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                // 首段若明显落在过去（页面从上一个整点边界起算等情况），归到未来同名时刻。
                if (last == null && c < nowMillis - 3_600_000L) c += 24 * 3_600_000L
                while (last != null && c <= last) c += 24 * 3_600_000L
                c
            }
            if (last != null && candidate <= last) return@forEachIndexed
            if (explicitDate == null) {
                val nextAnchor = raws.indices.firstOrNull { it > i && anchorDates.containsKey(it) }
                    ?.let {
                        anchorDates.getValue(it)
                            .atTime(raws[it].hour, raws[it].minute)
                            .toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                    }
                if (nextAnchor != null && candidate >= nextAnchor) return@forEachIndexed
            }
            last = candidate
            out += HourlyWeather(
                timeMillis = candidate,
                temperature = raw.temp,
                condition = raw.condition,
                windSpeed = raw.speedKmh,
                windDirectionDeg = windDirToDegree(raw.dir),
                pressure = raw.pressure,
                humidity = raw.humidity,
                precipProb = raw.prob,
            )
        }
        return out
    }

    private fun parseBeijingMillis(stamp: String): Long? = try {
        // “2026-09-14 10:00”
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .parse(stamp.take(16))
            .let { java.time.LocalDateTime.from(it) }
            .toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
    } catch (_: Exception) {
        null
    }

    // “微风/3-4级”等风力等级没有精确风速；按蒲福风级给保守估值（App 内部 km/h）。
    // 微风 ≈ 2 级 ≈ 6-11 km/h；n 级下限 ≈ n×5 km/h（4 级=20、6 级=30）。
    internal fun windPowerToSpeed(text: String?): Double? = when {
        text == null -> null
        text.contains("微风") -> 8.0
        else -> Regex("(\\d+)").findAll(text).map { it.groupValues[1].toDoubleOrNull() }
            .filterNotNull().toList().takeIf { it.isNotEmpty() }?.let { list ->
                (list.max() * 5.0).takeIf { it in 0.0..200.0 }
            }
    }

    // 中文方位 → 罗盘度数（8 方位 + 常见变体；实况 JSON 自带 degree，此映射兜底 7 天文本）。
    internal fun windDirToDegree(text: String?): Double? {
        val t = text?.trim() ?: return null
        if (t.isEmpty() || t == "-") return null
        val base = when {
            t.startsWith("北") && !t.contains("东") && !t.contains("西") -> 0.0
            t.startsWith("东北") -> 45.0
            t.startsWith("东南") -> 135.0
            t.startsWith("东") -> 90.0
            t.startsWith("南") -> 180.0
            t.startsWith("西南") -> 225.0
            t.startsWith("西") && !t.contains("北") && !t.contains("南") -> 270.0
            t.startsWith("西北") -> 315.0
            else -> return null
        }
        return when {
            t.contains("偏北") && base == 0.0 -> 337.5
            t.contains("偏东") -> base + 22.5
            t.contains("偏南") && base == 180.0 -> 168.75
            t.contains("偏西") && base == 270.0 -> 292.5
            else -> base
        }
    }

    // nmc 只有中国城市；城市搜索不做全网抓取（35 省×逐省请求不现实），
    // 由现有搜索渠道负责选址，本源在 fetch 时按省/市名匹配。
    suspend fun searchCity(query: String): List<City> = emptyList()
}
