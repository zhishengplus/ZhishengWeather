package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.alertLevelOf
import com.zhisheng.weather.model.cityDate

private val Context.wcnStore: DataStore<Preferences> by preferencesDataStore(name = "weather_com_cn")

/**
 * 中国天气网（d1.weather.com.cn，中国气象局公共气象服务中心）免密钥通道。
 * 网页自用数据通道，与 NMC 同级定位：转载公开信息、解析失败按缺数据处理。
 * 实测（2026-09-14）：sk_2d=实况（含能见度/AQI——其他免费源没有的字段）；
 * weather_index=今日昼夜（cityDZ）+ 预警（alarmDZ）+ 约 30 项生活指数（dataZS）。
 * 区县定位用官方 city.js 省市区县码表（382KB，DataStore 缓存 7 天）。
 */
object WeatherComCnApi {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val REFERER = "http://www.weather.com.cn/"
    private const val CITY_JS_URL = "https://j.i8tq.com/weather2020/search/city.js"
    private const val CITY_JS_TTL_MS = 7 * 24 * 60 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val keyCityJs = stringPreferencesKey("city_js")
    private val keyCityJsAt = longPreferencesKey("city_js_at")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var zonesCache: List<WcnZone>? = null

    internal fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal data class WcnZone(val province: String, val city: String, val district: String, val areaId: String)

    internal data class WcnCurrent(
        val temperature: Double?,
        val humidity: Double?,
        val pressure: Double?,
        /** 公里（"30km" → 30.0）。 */
        val visibilityKm: Double?,
        val precipMm: Double?,
        val rain24hMm: Double?,
        val aqi: Int?,
        val weatherText: String?,
        val weatherCode: String?,
        val windDirection: String?,
        /** "8km/h" → 8.0（内部 km/h）。 */
        val windSpeedKmh: Double?,
        val time: String?,
    )

    internal data class WcnToday(
        val high: Double?,
        val low: Double?,
        val dayText: String?,
        val nightText: String?,
        val dayCode: String?,
        val nightCode: String?,
        val windText: String?,
        val windPower: String?,
    )

    internal data class WcnIndex(val name: String, val en: String, val level: String?)

    internal data class WcnAlarm(
        val title: String,
        val detail: String?,
        val type: String?,
        val level: String?,
        val pubTime: String?,
    )

    internal data class WcnBundle(
        val areaId: String,
        val current: WcnCurrent?,
        val today: WcnToday?,
        val indices: List<WcnIndex>,
        val alarms: List<WcnAlarm>,
    )

    internal suspend fun fetch(city: City): WcnBundle? = try {
        val zones = loadZones() ?: return null
        val zone = matchZone(city, zones) ?: return null
        withContext(Dispatchers.IO) {
            // 实测 https 可用（免明文流量限制）；Referer 必须带，否则被拒
            val skText = getText("https://d1.weather.com.cn/sk_2d/${zone.areaId}.html")
            val idxText = getText("https://d1.weather.com.cn/weather_index/${zone.areaId}.html")
            if (skText == null && idxText == null) return@withContext null
            val parsed = idxText?.let { parseIndexText(it) }
            WcnBundle(
                areaId = zone.areaId,
                current = skText?.let { parseSk(it) },
                today = parsed?.first,
                alarms = parsed?.second.orEmpty(),
                indices = parsed?.third.orEmpty(),
            )
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        null
    }

    // —— city.js 码表：内存 → DataStore（7 天）→ 网络 ——

    private suspend fun loadZones(): List<WcnZone>? {
        zonesCache?.let { return it }
        val context = appContext
        // 先读持久化缓存（避免冷启动 382KB 流量）
        if (context != null) {
            val prefs = context.wcnStore.data.first()
            val savedText = prefs[keyCityJs]
            val savedAt = prefs[keyCityJsAt] ?: 0L
            if (savedText != null && System.currentTimeMillis() - savedAt < CITY_JS_TTL_MS) {
                parseCityJs(savedText)?.let { zonesCache = it; return it }
            }
        }
        val text = getText(CITY_JS_URL) ?: return null
        val zones = parseCityJs(text) ?: return null
        zonesCache = zones
        if (context != null) {
            runCatching {
                context.wcnStore.edit { prefs ->
                    prefs[keyCityJs] = text
                    prefs[keyCityJsAt] = System.currentTimeMillis()
                }
            }
        }
        return zones
    }

    internal fun parseCityJs(text: String): List<WcnZone>? = try {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else {
            val root = json.parseToJsonElement(text.substring(start, end + 1)) as JsonObject
            val out = mutableListOf<WcnZone>()
            root.forEach { (province, pv) ->
                (pv as? JsonObject)?.forEach { (cityName, cv) ->
                    (cv as? JsonObject)?.forEach { (districtName, dv) ->
                        val areaId = (dv as? JsonObject)?.primString("AREAID")
                        if (!areaId.isNullOrBlank()) {
                            out += WcnZone(province, cityName, districtName, areaId)
                        }
                    }
                }
            }
            out.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    /** 与 NMC 同套匹配：省份归一 → 区县精确 → 区县包含 → 父级地级市回退。 */
    internal fun matchZone(city: City, zones: List<WcnZone>): WcnZone? {
        val haystack = listOfNotNull(city.affiliation, city.name).joinToString(" ")
        val provinceZones = zones.filter { zone ->
            zone.province.length >= 2 && haystack.contains(zone.province)
        }
        if (provinceZones.isEmpty()) return null
        val wanted = NmcSource.stripDistrictSuffix(city.name)
        if (wanted.isNotEmpty()) {
            provinceZones.firstOrNull { NmcSource.stripDistrictSuffix(it.district) == wanted }?.let { return it }
            provinceZones.firstOrNull { zone ->
                val d = NmcSource.stripDistrictSuffix(zone.district)
                d.isNotEmpty() && d != zone.city && (wanted.contains(d) || d.contains(wanted))
            }?.let { return it }
        }
        // 父级地级市回退：affiliation 去省名后的串与市名前缀匹配（金川区 → 金昌）
        val rest = NmcSource.stripProvincePrefix(city.affiliation, fullProvinceName(provinceZones.first().province))
        if (rest.length >= 2) {
            provinceZones.firstOrNull { zone ->
                val c = NmcSource.stripDistrictSuffix(zone.city)
                c.length >= 2 && rest.startsWith(c) && zone.district == zone.city
            }?.let { return it }
        }
        return null
    }

    private fun fullProvinceName(short: String): String = when (short) {
        "北京", "上海", "天津", "重庆" -> "${short}市"
        "内蒙古", "广西", "西藏", "宁夏", "新疆" -> short // stripProvincePrefix 会按简称剥
        else -> if (short.endsWith("省") || short.endsWith("市")) short else "${short}省"
    }

    // —— sk_2d → 实况 ——

    internal fun parseSk(text: String): WcnCurrent? {
        val obj = extractVar(text, "dataSK") ?: return null
        val temp = obj.primString("temp")?.toDoubleOrNull()?.takeIf { it in -90.0..60.0 }
        return WcnCurrent(
            temperature = temp,
            humidity = obj.primString("SD")?.trimEnd('%')?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 },
            pressure = obj.primString("qy")?.toDoubleOrNull()?.takeIf { it in 250.0..1100.0 },
            visibilityKm = obj.primString("njd")?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull(),
            precipMm = obj.primString("rain")?.toDoubleOrNull()?.takeIf { it >= 0 },
            rain24hMm = obj.primString("rain24h")?.toDoubleOrNull()?.takeIf { it >= 0 },
            aqi = obj.primString("aqi")?.toDoubleOrNull()?.toInt()?.takeIf { it in 0..1000 },
            weatherText = obj.primString("weather")?.takeIf { it.isNotBlank() },
            weatherCode = normalizeCode(obj.primString("weathercode")),
            windDirection = obj.primString("WD")?.takeIf { it.isNotBlank() },
            windSpeedKmh = obj.primString("wse")?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull(),
            time = obj.primString("time"),
        )
    }

    // —— weather_index → 今日 / 预警 / 指数 ——

    internal fun parseIndexText(text: String): Triple<WcnToday?, List<WcnAlarm>, List<WcnIndex>> {
        val today = extractVar(text, "cityDZ")?.obj("weatherinfo")?.let { w ->
            WcnToday(
                high = parseTempLike(w.primString("temp")),
                low = parseTempLike(w.primString("tempn")),
                dayText = w.primString("weather")?.takeIf { it.isNotBlank() },
                nightText = w.primString("weathern")?.takeIf { it.isNotBlank() },
                dayCode = normalizeCode(w.primString("weathercode")),
                nightCode = normalizeCode(w.primString("weatherncode") ?: w.primString("weathercoden")),
                windText = w.primString("wd")?.takeIf { it.isNotBlank() },
                windPower = w.primString("ws")?.takeIf { it.isNotBlank() },
            )
        }
        val alarms = extractVar(text, "alarmDZ")?.get("w")
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { item ->
                val e = item as? JsonObject ?: return@mapNotNull null
                val title = e.primString("w13")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WcnAlarm(
                    title = title,
                    detail = e.primString("w9")?.takeIf { it.isNotBlank() },
                    type = e.primString("w5")?.takeIf { it.isNotBlank() },
                    level = e.primString("w7")?.takeIf { it.isNotBlank() },
                    pubTime = e.primString("w8")?.takeIf { it.isNotBlank() },
                )
            }.orEmpty()
        val indices = extractVar(text, "dataZS")?.obj("zs")?.let { zs ->
            zs.entries.mapNotNull { (key, value) ->
                if (!key.endsWith("_name")) return@mapNotNull null
                val prefix = key.removeSuffix("_name")
                val name = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                WcnIndex(
                    name = name,
                    en = prefix,
                    level = zs.primString("${prefix}_hint")?.takeIf { it.isNotBlank() },
                )
            }
        }.orEmpty()
        return Triple(today, alarms, indices)
    }

    /** "26℃"/"999"（缺省哨兵）→ Double?。 */
    private fun parseTempLike(raw: String?): Double? =
        raw?.filter { it.isDigit() || it == '.' || it == '-' }?.toDoubleOrNull()
            ?.takeIf { it in -90.0..60.0 }

    /** "d00"/"n7" → "0"/"7"（国标码空间）。 */
    internal fun normalizeCode(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() } ?: return null
        if (digits.isEmpty()) return null
        return digits.trimStart('0').ifEmpty { "0" }
    }

    // —— 通用：从 "var name = {...};" 中提取 JSON 对象（花括号配对扫描，抗嵌套） ——

    internal fun extractVar(text: String, name: String): JsonObject? {
        val anchor = text.indexOf("var $name")
        if (anchor < 0) return null
        val start = text.indexOf('{', anchor)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return try {
                                json.parseToJsonElement(text.substring(start, i + 1)) as? JsonObject
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun getText(url: String): String? = try {
        okHttp.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.primString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

// —— 枳生天气源：WcnBundle → 内部 WeatherData（纯转换） ——

internal fun wcnToWeatherData(bundle: WeatherComCnApi.WcnBundle, nowMillis: Long): WeatherData? {
    val offset = java.time.ZoneOffset.ofHours(8)
    val today = cityDate(nowMillis, 28800)
    val current = bundle.current?.let { c ->
        CurrentWeather(
            temperature = c.temperature,
            condition = WeatherCondition.fromCode(c.weatherCode),
            weatherText = c.weatherText,
            humidity = c.humidity,
            pressure = c.pressure,
            visibility = c.visibilityKm, // 免费源里唯一的实况能见度
            precipMm = c.precipMm,
            windSpeed = c.windSpeedKmh,
            windDirectionDeg = NmcSource.windDirToDegree(c.windDirection),
        )
    }
    val daily = bundle.today?.let { t ->
        listOf(
            DailyWeather(
                dateMillis = today.atStartOfDay(offset).toInstant().toEpochMilli(),
                high = t.high,
                low = t.low,
                condition = WeatherCondition.moreSignificant(
                    WeatherCondition.fromCode(t.dayCode), WeatherCondition.fromCode(t.nightCode),
                ),
                weatherText = joinDayNightText(t.dayText, t.nightText),
                windSpeed = NmcSource.windPowerToSpeed(t.windPower),
                windDirectionDeg = NmcSource.windDirToDegree(t.windText?.substringBefore("转")?.trim()),
            )
        )
    }.orEmpty()
    val aqi = bundle.current?.aqi?.let {
        AqiInfo(value = it, level = WeatherRepository.aqiLevel(it), standard = "中国")
    }
    val alerts = bundle.alarms.mapNotNull { a ->
        val levelName = a.level ?: return@mapNotNull null
        val level = "${levelName}预警"
        AlertInfo(
            title = a.title,
            detail = a.detail,
            level = level,
            pubTime = a.pubTime,
            severity = alertLevelOf(level),
            type = a.type,
        )
    }
    if (current == null && daily.isEmpty()) return null
    return WeatherData(
        current = current,
        daily = daily,
        aqi = aqi,
        alerts = alerts,
        extraIndices = bundle.indices.map { LifeIndexExtra(name = it.name, en = "wcn:${it.en}", category = it.level.orEmpty()) },
        carWashOk = hintToOk(bundle.indices.firstOrNull { it.en == "xc" }?.level),
        sportsOk = hintToOk(bundle.indices.firstOrNull { it.en == "yd" }?.level),
        utcOffsetSeconds = 28800,
    )
}
