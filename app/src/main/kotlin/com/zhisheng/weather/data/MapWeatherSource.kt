package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Existing user Web-service credentials only. No signup, paid coordinate-weather request,
 * guessed city ID, synthetic hourly forecast or conversion of Beaufort grades into km/h.
 * Official protocols: lbs.amap.com/api/webservice/guide/api/weatherinfo and
 * lbsyun.baidu.com/docs/webapi?title=weatherinquiry%2Fweather%2Fbase
 */
internal object MapWeatherSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()

    suspend fun fetchAmap(city: City): WeatherData {
        if (!SettingsRepository.amapUnlocked()) return WeatherData(error = "高德天气需要用户已配置的 Web 服务 Key")
        val key = SecretStore.currentAmap().webServiceKey
        val district = AmapApi.reverseStreetFromWgs84(key, city.latitude, city.longitude, city.name).adcode
            ?.takeIf { it.matches(Regex("\\d{6}")) }
            ?: return WeatherData(error = "高德未返回该地点的行政区编码或权限不足")
        return coroutineScope {
            val now = async { request("https://restapi.amap.com/v3/weather/weatherInfo", mapOf("key" to key, "city" to district, "extensions" to "base")) }
            val forecast = async { request("https://restapi.amap.com/v3/weather/weatherInfo", mapOf("key" to key, "city" to district, "extensions" to "all")) }
            parseAmapWeather(now.await(), forecast.await(), city, district, System.currentTimeMillis())
        }
    }

    suspend fun fetchBaidu(city: City): WeatherData {
        if (!SettingsRepository.baiduUnlocked()) return WeatherData(error = "百度天气需要用户已配置且具备天气权限的服务端 AK")
        val ak = SecretStore.currentBaidu().webServiceAk
        val district = BaiduApi.reverseStreetFromWgs84(ak, city.latitude, city.longitude).adcode
            ?.takeIf { it.matches(Regex("\\d{6}")) }
            ?: return WeatherData(error = "百度未返回该地点的行政区编码或权限不足")
        val response = request("https://api.map.baidu.com/weather/v1/", mapOf(
            "ak" to ak, "district_id" to district, "data_type" to "all", "output" to "json",
        ))
        return parseBaiduWeather(response, city, district, System.currentTimeMillis())
    }

    private suspend fun request(endpoint: String, params: Map<String, String>): JsonObject? = suspendCancellableCoroutine { continuation ->
        val url = endpoint.toHttpUrl().newBuilder().apply { params.forEach(::addQueryParameter) }.build()
        val call = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resume(null) }
            override fun onResponse(call: Call, response: Response) {
                val root = response.use { res -> if (!res.isSuccessful) null else runCatching {
                    json.parseToJsonElement(res.body?.string().orEmpty()) as? JsonObject
                }.getOrNull() }
                if (continuation.isActive) continuation.resume(root)
            }
        })
    }
}

private val chinaOffset = ZoneOffset.ofHours(8)
private fun JsonObject?.text(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    ?.trim()?.takeUnless { it.isBlank() || it == "暂无" || it == "null" || it == "999999" }
private fun JsonObject?.number(key: String, range: ClosedFloatingPointRange<Double>): Double? =
    text(key)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
private fun JsonObject?.objects(key: String): List<JsonObject> = (this?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun weatherTime(raw: String?): Long? = raw?.let { value ->
    listOf("yyyyMMddHHmmss", "yyyy-MM-dd HH:mm:ss").firstNotNullOfOrNull { pattern ->
        runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern)).toInstant(chinaOffset).toEpochMilli() }.getOrNull()
    }
}
private fun weatherDay(raw: String?): Long? = raw?.let {
    runCatching { LocalDate.parse(it).atStartOfDay().toInstant(chinaOffset).toEpochMilli() }.getOrNull()
}
internal fun weatherCondition(text: String?): WeatherCondition? = text?.let { name ->
    when (name) {
        "晴" -> WeatherCondition.CLEAR
        "多云" -> WeatherCondition.PARTLY_CLOUDY
        "阴" -> WeatherCondition.OVERCAST
        "小雨", "毛毛雨" -> WeatherCondition.DRIZZLE
        "中雨", "大雨", "暴雨", "大暴雨", "特大暴雨", "阵雨", "雨" -> WeatherCondition.RAIN
        "雷阵雨", "雷雨" -> WeatherCondition.THUNDERSTORM
        "雷阵雨伴有冰雹", "冰雹" -> WeatherCondition.HAIL
        "冻雨" -> WeatherCondition.FREEZING_RAIN
        "雨夹雪" -> WeatherCondition.SLEET
        "小雪", "中雪", "大雪", "暴雪", "阵雪", "雪" -> WeatherCondition.SNOW
        "雾", "大雾", "浓雾" -> WeatherCondition.FOG
        "霾", "中度霾", "重度霾", "严重霾" -> WeatherCondition.HAZE
        "浮尘", "扬沙", "沙尘暴", "强沙尘暴" -> WeatherCondition.SAND
        else -> null
    }
}
private fun mapEvidence(source: String, time: Long?, fetchedAt: Long, city: City, district: String) =
    WeatherModuleEvidence(source, time, fetchedAt, city.latitude, city.longitude, "行政区 $district（区县天气，非坐标点实测）")

internal fun parseAmapWeather(liveRoot: JsonObject?, forecastRoot: JsonObject?, city: City, district: String, fetchedAt: Long): WeatherData {
    val live = liveRoot?.takeIf { it.text("status") == "1" }?.objects("lives")?.firstOrNull { it.text("adcode") == district }
    val forecast = forecastRoot?.takeIf { it.text("status") == "1" }?.objects("forecasts")?.firstOrNull { it.text("adcode") == district }
    val temperature = live.number("temperature_float", -90.0..60.0) ?: live.number("temperature", -90.0..60.0)
    val current = live?.takeIf { temperature != null }?.let {
        CurrentWeather(temperature = temperature, humidity = it.number("humidity_float", 0.0..100.0) ?: it.number("humidity", 0.0..100.0),
            weatherText = it.text("weather"), condition = weatherCondition(it.text("weather")))
    }
    val daily = forecast.objects("casts").mapNotNull { day ->
        val date = weatherDay(day.text("date")) ?: return@mapNotNull null
        val high = day.number("daytemp_float", -90.0..60.0) ?: day.number("daytemp", -90.0..60.0)
        val low = day.number("nighttemp_float", -90.0..60.0) ?: day.number("nighttemp", -90.0..60.0)
        if (high == null && low == null) return@mapNotNull null
        val dayText = day.text("dayweather")
        val nightText = day.text("nightweather")
        DailyWeather(dateMillis = date, high = high, low = low,
            condition = listOfNotNull(weatherCondition(dayText), weatherCondition(nightText)).maxByOrNull { it.significanceRank },
            weatherText = joinDayNightText(dayText, nightText))
    }
    if (current == null && daily.isEmpty()) return WeatherData(error = "高德天气无有效数据（Key权限、覆盖或网络问题）")
    val observed = weatherTime(live.text("reporttime"))
    return WeatherData(current = current, daily = daily, dataSource = "AMAP", updateTime = observed,
        fetchedAt = fetchedAt, utcOffsetSeconds = 28800,
        blockSources = buildMap { if (current != null) put("current", "AMAP"); if (daily.isNotEmpty()) put("daily", "AMAP") },
        moduleEvidence = buildMap {
            if (current != null) put("current", mapEvidence("AMAP", observed, fetchedAt, city, district))
            if (daily.isNotEmpty()) put("forecast", mapEvidence("AMAP", weatherTime(forecast.text("reporttime")), fetchedAt, city, district))
        }, sourceDiagnostics = mapOf("capabilities" to "高德只返回区县实况与日预报；风力为等级，没有km/h风速或小时预报"))
}

internal fun parseBaiduWeather(root: JsonObject?, city: City, district: String, fetchedAt: Long): WeatherData {
    if (root.text("status") !in setOf("0", "200")) return WeatherData(error = "百度天气鉴权、覆盖或网络不可用（未自动开通任何高级服务）")
    val result = root?.get("result") as? JsonObject ?: return WeatherData(error = "百度天气未返回结果")
    val location = (result["location"] ?: result["address"]) as? JsonObject
    if (location?.text("id") != null && location.text("id") != district) return WeatherData(error = "百度返回行政区与请求地点不一致")
    val now = result["now"] as? JsonObject
    val observed = weatherTime(now.text("uptime"))
    val current = now?.takeIf { it.number("temp", -90.0..60.0) != null }?.let {
        CurrentWeather(temperature = it.number("temp", -90.0..60.0), feelsLike = it.number("feels_like", -100.0..70.0),
            condition = weatherCondition(it.text("text")), weatherText = it.text("text"), humidity = it.number("rh", 0.0..100.0),
            windDirectionDeg = it.number("wind_angle", 0.0..360.0), pressure = it.number("pressure", 250.0..1100.0),
            visibility = it.number("vis", 0.0..200000.0)?.div(1000.0), precipMm = it.number("prec_1h", 0.0..1000.0),
            dewPoint = it.number("dpt", -100.0..70.0), cloudCover = it.number("clouds", 0.0..100.0), uvIndex = it.number("uvi", 0.0..30.0)?.toInt())
    }
    val daily = result.objects("forecasts").mapNotNull { day ->
        val date = weatherDay(day.text("date")) ?: return@mapNotNull null
        val high = day.number("high", -90.0..60.0); val low = day.number("low", -90.0..60.0)
        if (high == null && low == null) return@mapNotNull null
        val dayText = day.text("text_day"); val nightText = day.text("text_night")
        DailyWeather(dateMillis = date, high = high, low = low, weatherText = joinDayNightText(dayText, nightText),
            condition = listOfNotNull(weatherCondition(dayText), weatherCondition(nightText)).maxByOrNull { it.significanceRank })
    }
    val hourly = result.objects("forecast_hours").mapNotNull { hour ->
        val time = weatherTime(hour.text("data_time")) ?: return@mapNotNull null
        val temp = hour.number("temp_fc", -90.0..60.0) ?: return@mapNotNull null
        HourlyWeather(timeMillis = time, temperature = temp, condition = weatherCondition(hour.text("text")),
            precipMm = hour.number("prec_1h", 0.0..1000.0), precipProb = hour.number("pop", 0.0..100.0)?.toInt(),
            humidity = hour.number("rh", 0.0..100.0), pressure = hour.number("pressure", 250.0..1100.0),
            windDirectionDeg = hour.number("wind_angle", 0.0..360.0), uvIndex = hour.number("uvi", 0.0..30.0)?.toInt())
    }
    val alertsKey = if ("alerts" in result) "alerts" else "alert"
    val alerts = result.objects(alertsKey).mapNotNull { alert ->
        val title = alert.text("title") ?: return@mapNotNull null
        AlertInfo(title = title, detail = alert.text("desc"), level = alert.text("level"),
            severity = alertLevelOf(alert.text("level")), type = alert.text("type"))
    }
    val indices = result.objects("indexes").mapNotNull { index ->
        val name = index.text("name") ?: return@mapNotNull null
        val brief = index.text("brief") ?: return@mapNotNull null
        LifeIndexExtra(name, "BAIDU", listOfNotNull(brief, index.text("detail")).joinToString(" · "))
    }
    val aqi = now.number("aqi", 0.0..1000.0)?.toInt()?.let { value -> AqiInfo(value = value,
        standard = "中国 AQI（百度返回）", level = WeatherRepository.aqiLevel(value),
        pm25 = now.number("pm25", 0.0..5000.0)?.toString(), pm10 = now.number("pm10", 0.0..5000.0)?.toString(),
        o3 = now.number("o3", 0.0..5000.0)?.toString(), no2 = now.number("no2", 0.0..5000.0)?.toString(),
        so2 = now.number("so2", 0.0..5000.0)?.toString(), co = now.number("co", 0.0..150.0)?.toString(),
        pollutantUnits = WeatherRepository.CHINA_POLLUTANT_UNITS) }
    if (current == null && daily.isEmpty() && hourly.isEmpty()) return WeatherData(error = "百度天气无有效实况或预报")
    val evidence = buildMap {
        if (current != null) put("current", mapEvidence("BAIDU", observed, fetchedAt, city, district))
        if (daily.isNotEmpty() || hourly.isNotEmpty()) put("forecast", mapEvidence("BAIDU", null, fetchedAt, city, district))
        if (result[alertsKey] is JsonArray) put("alerts", mapEvidence("BAIDU", null, fetchedAt, city, district))
        if (indices.isNotEmpty()) put("indices", mapEvidence("BAIDU", null, fetchedAt, city, district))
        if (aqi != null) put("air", mapEvidence("BAIDU", observed, fetchedAt, city, district))
    }
    return WeatherData(current = current, daily = daily, hourly = hourly, alerts = alerts, extraIndices = indices, aqi = aqi,
        dataSource = "BAIDU", updateTime = observed, fetchedAt = fetchedAt, utcOffsetSeconds = 28800,
        blockSources = buildMap {
            evidence.keys.forEach { put(it, "BAIDU") }
            if (daily.isNotEmpty()) put("daily", "BAIDU")
            if (hourly.isNotEmpty()) put("hourly", "BAIDU")
        }, moduleEvidence = evidence,
        sourceDiagnostics = mapOf("capabilities" to "区县基础天气；未返回的小时、空气或高级字段保持缺失，未请求付费坐标天气"))
}
