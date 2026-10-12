package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.*
import java.time.format.DateTimeFormatter

data class SeniverseRuntimeCreds(val apiKey: String = "") { val ready: Boolean get() = apiKey.isNotBlank() }
data class MojiRuntimeCreds(
    val appCode: String = "", val currentEndpoint: String = "", val currentToken: String = "",
    val dailyEndpoint: String = "", val dailyToken: String = "",
) {
    val ready: Boolean get() = appCode.isNotBlank() && currentToken.isNotBlank() &&
        validMojiEndpoint(currentEndpoint, false) &&
        ((dailyEndpoint.isBlank() && dailyToken.isBlank()) ||
            (dailyToken.isNotBlank() && validMojiEndpoint(dailyEndpoint, true)))
}

/** Copy the HTTPS coordinate endpoint from the user's purchased product; never guess an HTTP-to-HTTPS host. */
internal fun validMojiEndpoint(raw: String, daily: Boolean): Boolean {
    val url = raw.trim().toHttpUrlOrNull() ?: return false
    // Marketplace assigns the HTTPS gateway per subscribed product. Copy it from
    // that product's example; never synthesize a gateway from its HTTP hostname.
    val trustedHost = url.host.endsWith(".data.moji.com") || url.host.endsWith(".market.alicloudapi.com")
    val paths = if (daily) setOf("forecast3days", "briefforecast3days", "forecast6days", "forecast15days")
        else setOf("condition", "briefcondition")
    return trustedHost && url.scheme == "https" && url.port == 443 && url.username.isEmpty() &&
        url.password.isEmpty() && url.query == null && url.fragment == null &&
        url.encodedPath in paths.map { "/whapi/json/aliweather/$it" }
}

internal fun seniverseRequest(city: City, key: String, kind: String): Request? {
    if (key.isBlank() || !validSearchCoordinates(city.latitude, city.longitude)) return null
    val path = when (kind) { "now", "daily" -> "weather/$kind"; "indices" -> "life/suggestion"; else -> return null }
    val url = "https://api.seniverse.com/v3/$path.json".toHttpUrlOrNull()!!.newBuilder()
        .addQueryParameter("key", key.trim())
        .addQueryParameter("location", "${city.latitude}:${city.longitude}")
        .addQueryParameter("language", "zh-Hans").addQueryParameter("unit", "c")
        .apply { if (kind == "daily") { addQueryParameter("start", "0"); addQueryParameter("days", "3") } }.build()
    return Request.Builder().url(url).get().build()
}

internal fun mojiRequest(city: City, creds: MojiRuntimeCreds, daily: Boolean = false): Request? {
    if (!creds.ready || !validSearchCoordinates(city.latitude, city.longitude)) return null
    val endpoint = if (daily) creds.dailyEndpoint else creds.currentEndpoint
    val token = if (daily) creds.dailyToken else creds.currentToken
    if (token.isBlank() || !validMojiEndpoint(endpoint, daily)) return null
    // The Alibaba Moji coordinate protocol explicitly requires Amap/GCJ-02 coordinates.
    val point = wgs84ToGcj02(city.latitude, city.longitude)
    return Request.Builder().url(endpoint.trim()).header("Authorization", "APPCODE ${creds.appCode.trim()}")
        .post(FormBody.Builder().add("lat", point.latitude.toString()).add("lon", point.longitude.toString())
            .add("token", token.trim()).build()).build()
}

internal fun JsonObject?.domesticText(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    ?.trim()?.takeUnless { it.isBlank() || it == "null" || it == "暂无" || it == "999999" }
private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
private fun JsonObject?.rows(key: String): List<JsonObject> = (this?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject?.num(key: String, range: ClosedFloatingPointRange<Double>): Double? =
    domesticText(key)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
private fun zoneOf(location: JsonObject?): ZoneId? =
    listOf("timezone", "ianatimezone").firstNotNullOfOrNull { field ->
        location.domesticText(field)?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    } ?: location.domesticText("timezone_offset")?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
private fun published(raw: String?, zone: ZoneId?, fetchedAt: Long): Long? = raw?.let { value ->
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: zone?.let { z -> listOf("yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmm").firstNotNullOfOrNull { pattern ->
            runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern)).atZone(z).toInstant().toEpochMilli() }.getOrNull()
        } }
}?.takeIf { it > 0L && it <= fetchedAt + 300_000L }
private fun dayMillis(raw: String?, zone: ZoneId?): Long? =
    if (raw == null || zone == null) null else runCatching { LocalDate.parse(raw).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
private fun sameId(a: JsonObject?, b: JsonObject?, field: String): Boolean =
    a.domesticText(field)?.let { it == b.domesticText(field) } == true

private fun domesticCondition(text: String?): WeatherCondition? = weatherCondition(text) ?: when (text) {
    "晴间多云", "大部多云", "少云", "大部晴朗" -> WeatherCondition.PARTLY_CLOUDY
    "局部阵雨", "强阵雨" -> WeatherCondition.RAIN
    "局部雷阵雨", "强雷阵雨" -> WeatherCondition.THUNDERSTORM
    "风", "大风", "飓风", "热带风暴", "龙卷风" -> WeatherCondition.WIND
    else -> null
}

private fun dailyValues(rows: List<JsonObject>, zone: ZoneId?, fetchedAt: Long, moji: Boolean): List<DailyWeather> {
    val today = zone?.let { Instant.ofEpochMilli(fetchedAt).atZone(it).toLocalDate().atStartOfDay(it).toInstant().toEpochMilli() }
        ?: return emptyList()
    return rows.mapNotNull { row ->
        val date = dayMillis(row.domesticText(if (moji) "predictDate" else "date"), zone) ?: return@mapNotNull null
        if (date < today) return@mapNotNull null
        val high = row.num(if (moji) "tempDay" else "high", -90.0..60.0)
        val low = row.num(if (moji) "tempNight" else "low", -90.0..60.0)
        val day = row.domesticText(if (moji) "conditionDay" else "text_day")
        val night = row.domesticText(if (moji) "conditionNight" else "text_night")
        if ((high == null && low == null && day == null && night == null) || (high != null && low != null && high < low)) return@mapNotNull null
        DailyWeather(date, high, low, listOfNotNull(domesticCondition(day), domesticCondition(night))
            .reduceOrNull { a, b -> WeatherCondition.moreSignificant(a, b) },
            weatherText = joinDayNightText(day, night),
            windSpeed = if (moji) null else row.num("wind_speed", 0.0..500.0),
            humidity = row.num("humidity", 0.0..100.0),
            precipProbability = if (moji) null else row.num("precip", 0.0..100.0)?.toInt(),
            precipMm = if (moji) null else row.num("rainfall", 0.0..3000.0))
    }.distinctBy { it.dateMillis }.sortedBy { it.dateMillis }.take(if (moji) 15 else 3)
}

private fun assembleDomesticWeather(source: String, city: City, provider: GeoPoint, current: CurrentWeather?,
    daily: List<DailyWeather>, indices: List<LifeIndexExtra>, currentTime: Long?, dailyTime: Long?, indexTime: Long?,
    zone: ZoneId?, fetchedAt: Long, matchedName: String?, diagnostics: Map<String, String> = emptyMap()): WeatherData {
    if (current == null && daily.isEmpty()) return WeatherData(error = "未返回有效实况或日预报，请核对凭据、订阅权限与网络", dataSource = source)
    val blocks = buildMap { if (current != null) put("current", source); if (daily.isNotEmpty()) put("daily", source); if (indices.isNotEmpty()) put("indices", source) }
    val evidence = buildMap {
        fun putModule(key: String, time: Long?) { put(key, WeatherModuleEvidence(source, time, fetchedAt, city.latitude, city.longitude,
            locationScope = "按坐标匹配城市或区县${matchedName?.let { " · $it" }.orEmpty()}")) }
        if (current != null) putModule("current", currentTime)
        if (daily.isNotEmpty()) putModule("forecast", dailyTime)
        if (indices.isNotEmpty()) putModule("indices", indexTime)
    }
    return WeatherData(current = current, daily = daily, extraIndices = indices, updateTime = currentTime,
        fetchedAt = fetchedAt, dataSource = source, blockSources = blocks, moduleEvidence = evidence,
        sourceDiagnostics = diagnostics, utcOffsetSeconds = zone?.rules?.getOffset(Instant.ofEpochMilli(fetchedAt))?.totalSeconds,
        locationMatch = WeatherLocationMatch(city.latitude, city.longitude, provider.latitude, provider.longitude,
            preciseGps = city.hasMeasuredPrecision, dataResolution = "城市或区县天气",
            matchedRegionName = matchedName, providerCoordinateSystem = if (source == "MOJI") "GCJ-02" else "WGS84"))
}

internal fun parseSeniverseWeather(now: JsonObject?, daily: JsonObject?, indices: JsonObject?, city: City, fetchedAt: Long): WeatherData {
    val n = now.rows("results").singleOrNull()?.takeIf { it.obj("location").domesticText("id") != null }
    val d = daily.rows("results").singleOrNull()?.takeIf { it.obj("location").domesticText("id") != null }
    val i = indices.rows("results").singleOrNull()?.takeIf { it.obj("location").domesticText("id") != null }
    val location = (n ?: d)?.obj("location")
    val zone = zoneOf(location)
    val values = n.obj("now")
    val temperature = values.num("temperature", -90.0..60.0)
    val text = values.domesticText("text")
    val current = if (temperature == null && text == null) null else CurrentWeather(temperature = temperature,
        weatherText = text, condition = domesticCondition(text), humidity = values.num("humidity", 0.0..100.0),
        feelsLike = values.num("feels_like", -100.0..80.0), pressure = values.num("pressure", 250.0..1100.0),
        windSpeed = values.num("wind_speed", 0.0..500.0), windDirectionDeg = values.num("wind_direction_degree", 0.0..360.0),
        visibility = values.num("visibility", 0.0..500.0))
    val acceptedDaily = d?.takeIf { n == null || sameId(location, it.obj("location"), "id") }
    val acceptedIndices = i?.takeIf { sameId(location, it.obj("location"), "id") }
    val names = mapOf("car_washing" to ("洗车" to "CAR WASH"), "sport" to ("运动" to "SPORTS"),
        "dressing" to ("穿衣" to "DRESS"), "uv" to ("紫外线" to "UV"), "travel" to ("旅游" to "TRAVEL"), "flu" to ("感冒" to "COLD"))
    val suggestions = names.mapNotNull { (key, name) -> acceptedIndices.obj("suggestion").obj(key).domesticText("brief")
        ?.let { LifeIndexExtra(name.first, name.second, it) } }
    return assembleDomesticWeather("SENIVERSE", city, GeoPoint(city.latitude, city.longitude), current,
        dailyValues(acceptedDaily.rows("daily"), zone, fetchedAt, false), suggestions,
        published(n.domesticText("last_update"), zone, fetchedAt), published(acceptedDaily.domesticText("last_update"), zone, fetchedAt),
        published(acceptedIndices.domesticText("last_update"), zone, fetchedAt), zone, fetchedAt, location.domesticText("name"),
        buildMap { if (d != null && acceptedDaily == null) put("forecast", "返回城市不一致，已丢弃该预报") })
}

internal fun parseMojiWeather(now: JsonObject?, daily: JsonObject?, city: City, fetchedAt: Long): WeatherData {
    val n = now?.takeIf { it.domesticText("code") == "0" }?.obj("data")
    val d = daily?.takeIf { it.domesticText("code") == "0" }?.obj("data")
    val location = (n ?: d)?.obj("city")
    // The older domestic coordinate product omits timezone. Its mainland scope
    // permits this fallback; foreign/unknown locations must not acquire UTC+8.
    val zone = zoneOf(location) ?: ZoneId.of("Asia/Shanghai").takeIf {
        location.domesticText("counname") == "中国" && city.latitude in 18.0..54.0 && city.longitude in 73.0..135.0
    }
    val values = n.obj("condition")
    val temperature = values.num("temp", -90.0..60.0)
    val text = values.domesticText("condition")
    val current = if (temperature == null && text == null) null else CurrentWeather(temperature = temperature,
        weatherText = text, condition = domesticCondition(text), humidity = values.num("humidity", 0.0..100.0),
        feelsLike = values.num("realFeel", -100.0..80.0), pressure = values.num("pressure", 250.0..1100.0))
    val acceptedDaily = d?.takeIf { n == null || sameId(location, it.obj("city"), "cityId") }
    val rows = acceptedDaily.rows("forecast")
    return assembleDomesticWeather("MOJI", city, wgs84ToGcj02(city.latitude, city.longitude), current,
        dailyValues(rows, zone, fetchedAt, true), emptyList(), published(values.domesticText("updatetime"), zone, fetchedAt),
        rows.mapNotNull { published(it.domesticText("updatetime"), zone, fetchedAt) }.maxOrNull(), null, zone, fetchedAt,
        location.domesticText("name"), buildMap { if (d != null && acceptedDaily == null) put("forecast", "返回城市不一致，已丢弃该预报") })
}
