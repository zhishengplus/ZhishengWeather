package com.zhisheng.weather.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Serializable
data class TyphoonStorm(
    val id: String,
    val name: String,
    val englishName: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val active: Boolean = false,
    val warningLevel: String? = null,
)

@Serializable
data class TyphoonWindRadii(
    val northEastKm: Double? = null,
    val southEastKm: Double? = null,
    val southWestKm: Double? = null,
    val northWestKm: Double? = null,
) {
    val available: Boolean
        get() = listOf(northEastKm, southEastKm, southWestKm, northWestKm).any { it != null && it > 0.0 }
}

@Serializable
data class TyphoonTrackPoint(
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val windLevel: Int? = null,
    val windSpeedMs: Double? = null,
    val pressureHpa: Int? = null,
    val intensity: String? = null,
    val moveDirection: String? = null,
    val moveSpeedKmh: Double? = null,
    val radius7: TyphoonWindRadii? = null,
    val radius10: TyphoonWindRadii? = null,
    val radius12: TyphoonWindRadii? = null,
)

@Serializable
data class TyphoonForecastTrack(
    val agency: String,
    val points: List<TyphoonTrackPoint>,
)

@Serializable
data class TyphoonDetail(
    val storm: TyphoonStorm,
    val observed: List<TyphoonTrackPoint>,
    val forecasts: List<TyphoonForecastTrack>,
    val fetchedAt: Long,
    val source: String = "浙江省水利厅台风路径实时发布系统",
)

data class TyphoonLoad<T>(
    val value: T? = null,
    val fromCache: Boolean = false,
    val cacheAgeMillis: Long? = null,
    val error: String? = null,
)

private val typhoonJson = Json { ignoreUnknownKeys = true; isLenient = true }

object TyphoonRepository {
    private const val BASE = "https://typhoon.slt.zj.gov.cn/Api"
    private const val REFERER = "https://typhoon.slt.zj.gov.cn/"
    private const val CATALOG_CACHE = "typhoon_catalog_v1.json"
    private const val DETAIL_PREFIX = "typhoon_detail_v1_"
    private const val CATALOG_FALLBACK_AGE = 6L * 60L * 60L * 1_000L
    private const val DETAIL_FALLBACK_AGE = 90L * 60L * 1_000L

    private val zone = ZoneId.of("Asia/Shanghai")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun loadCatalog(context: Context, force: Boolean = false): TyphoonLoad<List<TyphoonStorm>> =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, CATALOG_CACHE)
            if (!force) readCache<List<TyphoonStorm>>(cache, 10 * 60_000L)?.let { return@withContext it }
            runCatching {
                val year = java.time.ZonedDateTime.now(zone).year
                val payload = get("$BASE/TyphoonList/$year")
                val parsed = parseCatalogPayload(payload)
                require(parsed.isNotEmpty()) { "官方列表暂时没有返回台风资料" }
                writeCache(cache, parsed)
                TyphoonLoad(parsed)
            }.getOrElse { cause ->
                readCache<List<TyphoonStorm>>(cache, CATALOG_FALLBACK_AGE, staleFallback = true)?.copy(
                    error = "实时资料连接失败，正在显示最近缓存：${cause.message.orEmpty()}",
                ) ?: TyphoonLoad(error = "暂时无法连接台风资料源，请稍后重试")
            }
        }

    suspend fun loadDetail(context: Context, storm: TyphoonStorm, force: Boolean = false): TyphoonLoad<TyphoonDetail> =
        withContext(Dispatchers.IO) {
            val safeId = storm.id.filter(Char::isLetterOrDigit)
            val cache = File(context.cacheDir, "$DETAIL_PREFIX$safeId.json")
            if (!force) readCache<TyphoonDetail>(cache, 10 * 60_000L)?.let { return@withContext it }
            runCatching {
                val payload = get("$BASE/TyphoonInfo/$safeId")
                val parsed = parseDetailPayload(payload, storm, System.currentTimeMillis())
                require(parsed.observed.isNotEmpty()) { "官方详情暂时没有返回路径点" }
                writeCache(cache, parsed)
                TyphoonLoad(parsed)
            }.getOrElse { cause ->
                readCache<TyphoonDetail>(cache, DETAIL_FALLBACK_AGE, staleFallback = true)?.copy(
                    error = "实时路径连接失败，正在显示最近缓存：${cause.message.orEmpty()}",
                ) ?: TyphoonLoad(error = "暂时无法取得这场台风的路径，请稍后重试")
            }
        }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", REFERER)
            .header("User-Agent", "ZhishengWeather/${com.zhisheng.weather.BuildConfig.VERSION_NAME} Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string()?.takeIf(String::isNotBlank) ?: error("空响应")
        }
    }

    private inline fun <reified T> readCache(
        file: File,
        maxAge: Long,
        staleFallback: Boolean = false,
    ): TyphoonLoad<T>? = runCatching {
        if (!file.isFile) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age !in 0..maxAge) return null
        TyphoonLoad(
            typhoonJson.decodeFromString<T>(file.readText()),
            fromCache = staleFallback,
            cacheAgeMillis = age,
        )
    }.getOrNull()

    private inline fun <reified T> writeCache(file: File, value: T) {
        runCatching { file.writeText(typhoonJson.encodeToString(value)) }
    }
}

internal fun parseCatalogPayload(payload: String): List<TyphoonStorm> {
    val root = typhoonJson.parseToJsonElement(payload)
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject -> root.array("data", "list", "typhoons") ?: JsonArray(listOf(root))
        else -> JsonArray(emptyList())
    }
    return rows.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("tfid", "id", "code")?.trim().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        TyphoonStorm(
            id = id,
            name = obj.string("name", "cname", "typhoonCname")?.ifBlank { "热带低压" } ?: "热带低压",
            englishName = obj.string("enname", "ename", "typhoonEname").orEmpty(),
            startTime = obj.string("starttime", "startTime"),
            endTime = obj.string("endtime", "endTime"),
            active = obj.string("isactive", "active")?.let { it == "1" || it.equals("true", true) } ?: false,
            warningLevel = obj.string("warnlevel", "warningLevel")?.takeIf(String::isNotBlank),
        )
    }.sortedWith(
        compareByDescending<TyphoonStorm> { it.active }
            .thenByDescending { parseTyphoonTime(it.startTime) ?: Long.MIN_VALUE },
    ).take(12)
}

internal fun parseDetailPayload(payload: String, fallback: TyphoonStorm, fetchedAt: Long): TyphoonDetail {
    val rootElement = typhoonJson.parseToJsonElement(payload)
    val root = when (rootElement) {
        is JsonObject -> rootElement.obj("data", "typhoon") ?: rootElement
        is JsonArray -> rootElement.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
        else -> JsonObject(emptyMap())
    }
    val storm = fallback.copy(
        id = root.string("tfid", "id", "code") ?: fallback.id,
        name = root.string("name", "cname")?.ifBlank { fallback.name } ?: fallback.name,
        englishName = root.string("enname", "ename") ?: fallback.englishName,
        active = root.string("isactive", "active")?.let { it == "1" || it.equals("true", true) } ?: fallback.active,
        warningLevel = root.string("warnlevel", "warningLevel") ?: fallback.warningLevel,
    )
    val observed = (root.array("points", "track", "path") ?: JsonArray(emptyList()))
        .mapNotNull(::parseTrackPoint)
        .distinctBy { "${it.time}/${it.latitude}/${it.longitude}" }
        .sortedBy { parseTyphoonTime(it.time) ?: Long.MIN_VALUE }
    val latestRaw = (root.array("points", "track", "path") ?: JsonArray(emptyList()))
        .mapNotNull { it as? JsonObject }
        .maxByOrNull { parseTyphoonTime(it.string("time", "datetime")) ?: Long.MIN_VALUE }
    val forecasts = (latestRaw?.array("forecast", "forecasts") ?: JsonArray(emptyList()))
        .mapNotNull { forecastElement ->
            val forecast = forecastElement as? JsonObject ?: return@mapNotNull null
            val agency = forecast.string("tm", "agency", "name")?.ifBlank { "预报机构" } ?: "预报机构"
            val points = (forecast.array("forecastpoints", "points", "path") ?: JsonArray(emptyList()))
                .mapNotNull(::parseTrackPoint)
                .filterNot { point -> observed.lastOrNull()?.let { samePoint(it, point) } == true }
                .sortedBy { parseTyphoonTime(it.time) ?: Long.MIN_VALUE }
            TyphoonForecastTrack(agency, points).takeIf { points.isNotEmpty() && isCmaForecastAgency(agency) }
        }
    return TyphoonDetail(storm, observed, forecasts, fetchedAt)
}

internal fun isCmaForecastAgency(agency: String): Boolean {
    val name = agency.trim()
    if (name.contains("香港") || name.contains("台湾")) return false
    return name == "中国" || name.contains("中央气象台")
}

private fun parseTrackPoint(element: JsonElement): TyphoonTrackPoint? {
    val obj = element as? JsonObject ?: return null
    val lat = obj.double("lat", "latitude") ?: return null
    val lon = obj.double("lng", "lon", "longitude") ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return TyphoonTrackPoint(
        time = obj.string("time", "datetime", "forecasttime").orEmpty(),
        latitude = lat,
        longitude = lon,
        windLevel = obj.int("power", "windlevel", "windLevel"),
        windSpeedMs = obj.double("speed", "windspeed", "windSpeed"),
        pressureHpa = obj.int("pressure", "pres"),
        intensity = obj.string("strong", "type", "intensity"),
        moveDirection = obj.string("movedirection", "moveDirection"),
        moveSpeedKmh = obj.double("movespeed", "moveSpeed"),
        radius7 = obj.windRadii("radius7"),
        radius10 = obj.windRadii("radius10"),
        radius12 = obj.windRadii("radius12"),
    )
}

private fun samePoint(a: TyphoonTrackPoint, b: TyphoonTrackPoint): Boolean =
    kotlin.math.abs(a.latitude - b.latitude) < 0.001 && kotlin.math.abs(a.longitude - b.longitude) < 0.001

internal fun parseTyphoonTime(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    val clean = text.trim().replace('T', ' ').removeSuffix("Z")
    val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/M/d H:mm:ss", "yyyy/M/d H:mm")
    for (pattern in patterns) {
        runCatching {
            return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern(pattern))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        }
    }
    return null
}

private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

private fun JsonObject.double(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()
}

private fun JsonObject.int(vararg keys: String): Int? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonPrimitive?.intOrNull ?: this[key]?.jsonPrimitive?.doubleOrNull?.toInt() }.getOrNull()
}

private fun JsonObject.array(vararg keys: String): JsonArray? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonArray }.getOrNull()
}

private fun JsonObject.obj(vararg keys: String): JsonObject? = keys.firstNotNullOfOrNull { key ->
    runCatching { this[key]?.jsonObject }.getOrNull()
}

private fun JsonObject.windRadii(key: String): TyphoonWindRadii? {
    fun positive(value: Double?): Double? = value?.takeIf { it > 0.0 }
    val quadrant = obj("${key}_quad", "${key}Quad") ?: obj(key)
    if (quadrant != null) {
        return TyphoonWindRadii(
            northEastKm = positive(quadrant.double("ne", "northeast", "northEast")),
            southEastKm = positive(quadrant.double("se", "southeast", "southEast")),
            southWestKm = positive(quadrant.double("sw", "southwest", "southWest")),
            northWestKm = positive(quadrant.double("nw", "northwest", "northWest")),
        ).takeIf(TyphoonWindRadii::available)
    }

    val parts = string(key)?.split('|', ',', ';')
        ?.map { positive(it.trim().toDoubleOrNull()) }
        .orEmpty()
    if (parts.size >= 4) {
        // 浙江台风接口的字符串顺序为东北、东南、西南、西北。
        return TyphoonWindRadii(parts[0], parts[1], parts[2], parts[3])
            .takeIf(TyphoonWindRadii::available)
    }
    val uniform = parts.firstOrNull() ?: double(key)?.let(::positive)
    return uniform?.let { TyphoonWindRadii(it, it, it, it) }
}
