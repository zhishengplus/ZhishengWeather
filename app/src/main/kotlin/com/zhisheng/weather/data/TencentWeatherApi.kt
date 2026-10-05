package com.zhisheng.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.model.alertLevelOf
import com.zhisheng.weather.model.cityDate
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 腾讯天气（wis.qq.com）免密钥接口——网页自用数据通道，与 NMC 同级定位：
 * 转载公开信息、解析失败按缺数据处理、失败由调用方降级。实测（2026-09-14）：
 * observe=实况；forecast_24h=昨日+未来 6 天逐日（含昼夜现象与高低温，注意不是逐小时）；
 * index=23 项生活指数；alarm=全国预警（含分级字段）。
 */
object TencentWeatherApi {

    private const val BASE = "https://wis.qq.com/weather/common"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    internal data class TencentObserve(
        val temperature: Double?,
        val humidity: Double?,
        val pressure: Double?,
        val precipMm: Double?,
        val weatherText: String?,
        /** 腾讯编码与国标码同空间（"00"=晴、"01"=多云），消费端去前导零后走 WeatherCondition.fromCode。 */
        val weatherCode: String?,
        val windDirectionName: String?,
        /** "4-5"（蒲福风级区间）。 */
        val windPowerLevel: String?,
        val updateTime: String?,
    )

    /** 一条逐日（含昨日实况）。day/night 分别带现象码。 */
    internal data class TencentDaily(
        val date: String,
        val high: Double?,
        val low: Double?,
        val dayText: String?,
        val dayCode: String?,
        val nightText: String?,
        val nightCode: String?,
        val aqi: Int?,
    )

    internal data class TencentIndex(val name: String, val en: String, val category: String?)

    internal data class TencentAlarm(
        val province: String?,
        val city: String?,
        val county: String?,
        val typeName: String?,
        val levelName: String?,
        val detail: String?,
        val pubTime: String?,
    )

    internal data class TencentBundle(
        val observe: TencentObserve?,
        val daily: List<TencentDaily>,
        val indices: List<TencentIndex>,
        val alarms: List<TencentAlarm>,
    )

    /**
     * @param province 省级名称（"北京市"/"海南省"），@param city 市级名称（"北京市"/"三亚市"）。
     * 四路并发，任一路失败该块为空（不拖垮整包）。
     */
    internal suspend fun fetch(province: String, city: String): TencentBundle? = coroutineScope {
        try {
            val area = "province=${encode(province)}&city=${encode(city)}"
            val observeDeferred = async { getJsonObject("$BASE?source=pc&weather_type=observe&$area") }
            val dailyDeferred = async { getJsonObject("$BASE?source=pc&weather_type=forecast_24h&$area") }
            val indexDeferred = async { getJsonObject("$BASE?source=pc&weather_type=index&$area") }
            val alarmDeferred = async { getJsonObject("$BASE?source=pc&weather_type=alarm&$area") }
            val observe = parseObserve(observeDeferred.await())
            val daily = parseDaily(dailyDeferred.await())
            val indices = parseIndex(indexDeferred.await())
            val alarms = parseAlarm(alarmDeferred.await())
            if (observe == null && daily.isEmpty()) return@coroutineScope null
            TencentBundle(observe, daily, indices, alarms)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseObserve(root: JsonObject?): TencentObserve? {
        val o = root?.dataObj()?.obj("observe") ?: return null
        val temp = o.primDouble("degree")
        if (temp == null && o.primString("weather").isNullOrBlank()) return null
        return TencentObserve(
            temperature = temp,
            humidity = o.primDouble("humidity"),
            pressure = o.primDouble("pressure"),
            precipMm = o.primDouble("precipitation"),
            weatherText = o.primString("weather")?.takeIf { it.isNotBlank() },
            weatherCode = o.primString("weather_code"),
            windDirectionName = o.primString("wind_direction_name")?.takeIf { it.isNotBlank() },
            windPowerLevel = o.primString("wind_power"),
            updateTime = o.primString("update_time"),
        )
    }

    internal fun parseDaily(root: JsonObject?): List<TencentDaily> {
        val container = root?.dataObj()?.obj("forecast_24h") ?: return emptyList()
        return container.entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { (_, v) ->
                val e = v as? JsonObject ?: return@mapNotNull null
                val date = e.primString("time")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TencentDaily(
                    date = date,
                    high = e.primDouble("max_degree"),
                    low = e.primDouble("min_degree"),
                    dayText = e.primString("day_weather")?.takeIf { it.isNotBlank() },
                    dayCode = e.primString("day_weather_code"),
                    nightText = e.primString("night_weather")?.takeIf { it.isNotBlank() },
                    nightCode = e.primString("night_weather_code"),
                    aqi = e.primDouble("aqi")?.toInt(),
                )
            }
    }

    internal fun parseIndex(root: JsonObject?): List<TencentIndex> {
        val container = root?.dataObj()?.obj("index") ?: return emptyList()
        return container.entries.mapNotNull { (en, v) ->
            if (en == "time") return@mapNotNull null
            val e = v as? JsonObject ?: return@mapNotNull null
            val name = e.primString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TencentIndex(
                name = name,
                en = en,
                category = e.primString("info")?.takeIf { it.isNotBlank() },
            )
        }
    }

    internal fun parseAlarm(root: JsonObject?): List<TencentAlarm> {
        val arr = root?.dataObj()?.get("alarm") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val e = item as? JsonObject ?: return@mapNotNull null
            TencentAlarm(
                province = e.primString("province")?.takeIf { it.isNotBlank() },
                city = e.primString("city")?.takeIf { it.isNotBlank() },
                county = e.primString("county")?.takeIf { it.isNotBlank() },
                typeName = e.primString("type_name")?.takeIf { it.isNotBlank() },
                levelName = e.primString("level_name")?.takeIf { it.isNotBlank() },
                detail = e.primString("detail")?.takeIf { it.isNotBlank() },
                pubTime = e.primString("update_time")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun JsonObject.dataObj(): JsonObject? = this["data"] as? JsonObject

    private suspend fun getJsonObject(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            okHttp.newCall(
                Request.Builder().url(url).header("User-Agent", UA).build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) null
                else json.parseToJsonElement(resp.body?.string() ?: return@use null) as? JsonObject
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.primString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.primDouble(key: String): Double? = primString(key)?.toDoubleOrNull()
}

// —— 枳生天气源：TencentBundle → 内部 WeatherData（纯转换） ——

internal fun tencentToWeatherData(bundle: TencentWeatherApi.TencentBundle, nowMillis: Long): WeatherData? {
    val offset = java.time.ZoneOffset.ofHours(8)
    val today = cityDate(nowMillis, 28800)
    fun parseDate(s: String): java.time.LocalDate? = try {
        java.time.LocalDate.parse(s)
    } catch (_: Exception) {
        null
    }

    val daily = bundle.daily.mapNotNull { d ->
        val date = parseDate(d.date) ?: return@mapNotNull null
        if (date.isBefore(today)) return@mapNotNull null // 昨日只供 yesterday 兜底
        DailyWeather(
            dateMillis = date.atStartOfDay(offset).toInstant().toEpochMilli(),
            high = d.high?.takeIf { it in -90.0..60.0 },
            low = d.low?.takeIf { it in -90.0..60.0 },
            condition = WeatherCondition.moreSignificant(
                WeatherCondition.fromCode(normalizeTencentCode(d.dayCode)),
                WeatherCondition.fromCode(normalizeTencentCode(d.nightCode)),
            ),
            weatherText = joinDayNightText(d.dayText, d.nightText),
            aqi = d.aqi?.takeIf { it in 0..1000 },
        )
    }
    val yesterdayEntry = bundle.daily.firstOrNull { d -> parseDate(d.date)?.isBefore(today) == true }
    val yesterday = yesterdayEntry?.let { d ->
        YesterdayInfo(
            high = d.high?.takeIf { it in -90.0..60.0 },
            low = d.low?.takeIf { it in -90.0..60.0 },
            condition = WeatherCondition.moreSignificant(
                WeatherCondition.fromCode(normalizeTencentCode(d.dayCode)),
                WeatherCondition.fromCode(normalizeTencentCode(d.nightCode)),
            ),
            aqi = d.aqi?.takeIf { it in 0..1000 },
            dateMillis = parseDate(d.date)?.atStartOfDay(offset)?.toInstant()?.toEpochMilli(),
        )
    }
    val current = bundle.observe?.let { o ->
        CurrentWeather(
            temperature = o.temperature?.takeIf { it in -90.0..60.0 },
            condition = WeatherCondition.fromCode(normalizeTencentCode(o.weatherCode)),
            weatherText = o.weatherText,
            humidity = o.humidity?.takeIf { it in 0.0..100.0 },
            pressure = o.pressure?.takeIf { it in 250.0..1100.0 },
            precipMm = o.precipMm?.takeIf { it >= 0.0 },
            windSpeed = NmcSource.windPowerToSpeed(o.windPowerLevel),
            windDirectionDeg = NmcSource.windDirToDegree(o.windDirectionName),
        )
    }
    val alerts = bundle.alarms.mapNotNull { a ->
        val levelName = a.levelName ?: return@mapNotNull null
        val level = "${levelName}预警"
        AlertInfo(
            title = "${a.province.orEmpty()}${a.city.orEmpty()}${a.county.orEmpty()}${a.typeName.orEmpty()}$level",
            detail = a.detail,
            level = level,
            pubTime = a.pubTime,
            severity = alertLevelOf(level),
            type = a.typeName,
        )
    }
    if (current == null && daily.isEmpty()) return null
    return WeatherData(
        current = current,
        daily = daily,
        alerts = alerts,
        extraIndices = bundle.indices.map { LifeIndexExtra(name = it.name, en = "tc:${it.en}", category = it.category.orEmpty()) },
        carWashOk = hintToOk(bundle.indices.firstOrNull { it.en == "carwash" }?.category),
        sportsOk = hintToOk(bundle.indices.firstOrNull { it.en == "sports" }?.category),
        yesterday = yesterday,
        utcOffsetSeconds = 28800,
    )
}

/** 腾讯编码与国标码同空间（"00"=晴、"01"=多云）。 */
internal fun normalizeTencentCode(raw: String?): String? {
    val digits = raw?.filter { it.isDigit() } ?: return null
    if (digits.isEmpty()) return null
    return digits.trimStart('0').ifEmpty { "0" }
}

internal fun joinDayNightText(day: String?, night: String?): String? = when {
    day == null -> night
    night == null || day == night -> day
    else -> day + "\u8f6c" + night
}

/** "适宜/较适宜"→true；"不…"→false；其余 null（不猜）。 */
internal fun hintToOk(hint: String?): Boolean? = when {
    hint == null -> null
    hint.contains("不") -> false
    hint.contains("适宜") -> true
    else -> null
}
