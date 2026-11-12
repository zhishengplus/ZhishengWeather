package com.zhisheng.weather.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        get() = listOf(northEastKm, southEastKm, southWestKm, northWestKm).any { it != null && it.isFinite() && it > 0.0 }
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
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
)

private val typhoonJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
private val typhoonZone = ZoneId.of("Asia/Shanghai")
private val typhoonIdPattern = Regex("(?:[0-9]{6}|[0-9]{8})")
internal const val FIRST_TYPHOON_YEAR = 1949
internal fun currentTyphoonYear(): Int = java.time.ZonedDateTime.now(typhoonZone).year

object TyphoonRepository {
    private const val BASE = "https://typhoon.slt.zj.gov.cn/Api"
    private const val REFERER = "https://typhoon.slt.zj.gov.cn/"
    private const val CATALOG_PREFIX = "typhoon_catalog_v2_"
    private const val DETAIL_PREFIX = "typhoon_detail_v2_"
    private const val CATALOG_FALLBACK_AGE = 6L * 60L * 60L * 1_000L
    private const val DETAIL_FALLBACK_AGE = 90L * 60L * 1_000L

    private const val HISTORY_FALLBACK_AGE = 30L * 24L * 60L * 60L * 1_000L
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(16, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun loadCatalog(
        context: Context,
        force: Boolean = false,
        year: Int = currentTyphoonYear(),
    ): TyphoonLoad<List<TyphoonStorm>> =
        withContext(Dispatchers.IO) {
            if (!ReleaseFeatures.typhoon) return@withContext TyphoonLoad(error = "功能暂未开放")
            if (year !in FIRST_TYPHOON_YEAR..currentTyphoonYear()) {
                return@withContext TyphoonLoad(error = "该年份暂无可查询的台风资料")
            }
            val cache = File(context.cacheDir, "$CATALOG_PREFIX$year.json")
            if (!force) readCache<List<TyphoonStorm>>(cache, 10 * 60_000L)
                ?.takeIf { load -> load.value?.all { it.id.take(4).toIntOrNull() == year } == true }
                ?.let { return@withContext it }
            try {
                val payload = get("$BASE/TyphoonList/$year")
                val parsed = parseCatalogPayload(payload)
                require(parsed.all { it.id.take(4).toIntOrNull() == year }) { "台风列表年份与请求不符" }
                coroutineContext.ensureActive()
                writeCache(cache, parsed)
                TyphoonLoad(parsed, fetchedAt = System.currentTimeMillis())
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                val fallbackAge = if (year < currentTyphoonYear()) HISTORY_FALLBACK_AGE else CATALOG_FALLBACK_AGE
                readCache<List<TyphoonStorm>>(cache, fallbackAge, staleFallback = true)
                    ?.takeIf { load -> load.value?.all { it.id.take(4).toIntOrNull() == year } == true }?.copy(
                    error = "台风列表刷新失败，正在显示最近缓存",
                ) ?: TyphoonLoad(error = "暂时无法连接台风资料源，请稍后重试")
            }
        }

    suspend fun loadDetail(context: Context, storm: TyphoonStorm, force: Boolean = false): TyphoonLoad<TyphoonDetail> =
        withContext(Dispatchers.IO) {
            if (!ReleaseFeatures.typhoon) return@withContext TyphoonLoad(error = "功能暂未开放")
            val safeId = storm.id.takeIf(typhoonIdPattern::matches)
                ?: return@withContext TyphoonLoad(error = "台风编号无效，请刷新列表")
            val cache = File(context.cacheDir, "$DETAIL_PREFIX$safeId.json")
            if (!force) readCache<TyphoonDetail>(cache, 10 * 60_000L)
                ?.takeIf { load -> load.value?.let { cachedTyphoonDetailIsCurrent(it, storm) } == true }
                ?.let { return@withContext it }
            try {
                val payload = get("$BASE/TyphoonInfo/$safeId")
                val parsed = parseDetailPayload(payload, storm, System.currentTimeMillis())
                require(parsed.observed.isNotEmpty()) { "官方详情暂时没有返回路径点" }
                coroutineContext.ensureActive()
                writeCache(cache, parsed)
                TyphoonLoad(parsed, fetchedAt = parsed.fetchedAt)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                val fallbackAge = if (storm.active) DETAIL_FALLBACK_AGE else HISTORY_FALLBACK_AGE
                readCache<TyphoonDetail>(cache, fallbackAge, staleFallback = true)
                    ?.takeIf { load ->
                        val cached = load.value
                        cached != null && cached.storm.id == storm.id && cached.observed.isNotEmpty() &&
                            (!cached.storm.active || (load.cacheAgeMillis ?: Long.MAX_VALUE) <= DETAIL_FALLBACK_AGE)
                    }?.copy(
                    error = "台风路径刷新失败，正在显示最近缓存",
                ) ?: TyphoonLoad(error = "暂时无法取得这场台风的路径，请稍后重试")
            }
        }

    private suspend fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Referer", REFERER)
            .header("User-Agent", "ZhishengWeather/${com.zhisheng.weather.BuildConfig.VERSION_NAME} Android")
            .get()
            .build()
        return typhoonAwaitPayload(client.newCall(request))
    }

    private inline fun <reified T> readCache(
        file: File,
        maxAge: Long,
        staleFallback: Boolean = false,
    ): TyphoonLoad<T>? = runCatching {
        if (!file.isFile) return null
        if (file.length() > 8L * 1024 * 1024) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (age !in 0..maxAge) return null
        TyphoonLoad(
            typhoonJson.decodeFromString<T>(file.readText()),
            fromCache = true,
            cacheAgeMillis = age,
            fetchedAt = file.lastModified(),
            stale = staleFallback,
        )
    }.getOrNull()

    private inline fun <reified T> writeCache(file: File, value: T) {
        // A cancelled/failed download never replaces the previous complete response.
        runCatching {
            val temporary = File.createTempFile(file.name, ".tmp", file.parentFile)
            try {
                temporary.writeText(typhoonJson.encodeToString(value))
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }
}

/** A fresh catalog can invalidate a ten-minute detail cache before its normal TTL expires. */
internal fun cachedTyphoonDetailIsCurrent(detail: TyphoonDetail, selected: TyphoonStorm): Boolean {
    if (detail.storm.id != selected.id || detail.storm.active != selected.active) return false
    val latestTime = detail.observed.lastOrNull()?.let { parseTyphoonTime(it.time) } ?: return false
    val catalogTime = parseTyphoonTime(selected.endTime)
    return catalogTime == null || latestTime >= catalogTime
}

/** Disconnect the socket when a different year/storm is selected; bound data before JSON parsing. */
internal suspend fun typhoonAwaitPayload(call: Call, maxBytes: Long = 8L * 1024 * 1024): String =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val payload = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val source = it.body?.source() ?: throw IOException("空响应")
                        if (source.request(maxBytes + 1)) throw IOException("台风资料超出大小限制")
                        source.readUtf8().takeIf(String::isNotBlank) ?: throw IOException("空响应")
                    }
                    if (continuation.isActive) continuation.resume(payload)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

internal fun parseCatalogPayload(payload: String): List<TyphoonStorm> {
    val root = typhoonJson.parseToJsonElement(payload)
    val rows = when (root) {
        is JsonArray -> root
        is JsonObject -> root.array("data", "list", "typhoons")
            ?: if (root.string("tfid", "id", "code") != null) JsonArray(listOf(root))
            else error("台风列表格式不可识别")
        else -> error("台风列表格式不可识别")
    }
    val storms = rows.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("tfid", "id", "code")?.trim().orEmpty()
        if (!typhoonIdPattern.matches(id)) return@mapNotNull null
        TyphoonStorm(
            id = id,
            name = obj.string("name", "cname", "typhoonCname")?.ifBlank { "热带低压" } ?: "热带低压",
            englishName = obj.string("enname", "ename", "typhoonEname").orEmpty(),
            startTime = obj.string("starttime", "startTime"),
            endTime = obj.string("endtime", "endTime"),
            active = obj.string("isactive", "active")?.let { it == "1" || it.equals("true", true) } ?: false,
            warningLevel = obj.string("warnlevel", "warningLevel")?.takeIf(String::isNotBlank),
        )
    }.distinctBy(TyphoonStorm::id).sortedWith(
        compareByDescending<TyphoonStorm> { it.active }
            .thenByDescending { parseTyphoonTime(it.startTime) ?: Long.MIN_VALUE },
    )
    require(rows.isEmpty() || storms.isNotEmpty()) { "台风列表未包含有效编号" }
    return storms
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
        startTime = root.string("starttime", "startTime") ?: fallback.startTime,
        endTime = root.string("endtime", "endTime") ?: fallback.endTime,
    )
    require(storm.id == fallback.id) { "台风详情编号与请求不符" }
    val observed = (root.array("points", "track", "path") ?: JsonArray(emptyList()))
        .mapNotNull(::parseTrackPoint)
        .distinctBy { parseTyphoonTime(it.time) }
        .sortedBy { parseTyphoonTime(it.time) ?: Long.MIN_VALUE }
    val latestRaw = (root.array("points", "track", "path") ?: JsonArray(emptyList()))
        .mapNotNull { it as? JsonObject }
        .filter { parseTrackPoint(it) != null }
        .maxByOrNull { parseTyphoonTime(it.string("time", "datetime", "forecasttime")) ?: Long.MIN_VALUE }
    val forecasts = (latestRaw?.array("forecast", "forecasts") ?: JsonArray(emptyList()))
        .mapNotNull { forecastElement ->
            val forecast = forecastElement as? JsonObject ?: return@mapNotNull null
            val agency = forecast.string("tm", "agency", "name")?.ifBlank { "预报机构" } ?: "预报机构"
            val points = (forecast.array("forecastpoints", "points", "path") ?: JsonArray(emptyList()))
                .mapNotNull(::parseTrackPoint)
                .filterNot { point -> observed.lastOrNull()?.let { samePoint(it, point) } == true }
                .filter { point ->
                    val latestTime = observed.lastOrNull()?.let { parseTyphoonTime(it.time) }
                    latestTime == null || (parseTyphoonTime(point.time) ?: Long.MIN_VALUE) > latestTime
                }
                .distinctBy { parseTyphoonTime(it.time) }
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
    val time = obj.string("time", "datetime", "forecasttime") ?: return null
    if (parseTyphoonTime(time) == null) return null
    val moveSpeed = obj.double("movespeed", "moveSpeed")?.takeIf { it in 0.0..200.0 }
    val moveDirection = obj.string("movedirection", "moveDirection")?.trim()?.takeIf(String::isNotBlank)
    return TyphoonTrackPoint(
        time = time,
        latitude = lat,
        longitude = lon,
        windLevel = obj.int("power", "windlevel", "windLevel")?.takeIf { it in 1..25 },
        windSpeedMs = obj.double("speed", "windspeed", "windSpeed")?.takeIf { it > 0.0 && it <= 150.0 },
        pressureHpa = obj.int("pressure", "pres")?.takeIf { it in 800..1100 },
        intensity = obj.string("strong", "type", "intensity")?.trim()?.takeIf(String::isNotBlank),
        moveDirection = if (moveDirection == "0" && moveSpeed == 0.0) "原地少动" else moveDirection,
        moveSpeedKmh = moveSpeed,
        radius7 = obj.windRadii("radius7"),
        radius10 = obj.windRadii("radius10"),
        radius12 = obj.windRadii("radius12"),
    )
}

private fun samePoint(a: TyphoonTrackPoint, b: TyphoonTrackPoint): Boolean {
    val sameTime = parseTyphoonTime(a.time)?.let { it == parseTyphoonTime(b.time) }
        ?: (a.time.isNotBlank() && a.time == b.time)
    return sameTime && kotlin.math.abs(a.latitude - b.latitude) < 0.001 &&
        kotlin.math.abs(a.longitude - b.longitude) < 0.001
}

internal fun parseTyphoonTime(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    val value = text.trim()
    // An explicit offset is authoritative. Offset-free Zhejiang observations use Beijing time.
    runCatching { return Instant.parse(value).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli() }
    val clean = value.replace('T', ' ')
    val patterns = listOf("uuuu-MM-dd HH:mm:ss", "uuuu-MM-dd HH:mm", "uuuu/M/d H:mm:ss", "uuuu/M/d H:mm")
    for (pattern in patterns) {
        runCatching {
            return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))
                .atZone(typhoonZone).toInstant().toEpochMilli()
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
    fun positive(value: Double?): Double? = value?.takeIf { it > 0.0 && it <= 2_000.0 }
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
    if (parts.size == 4) {
        // 浙江台风接口的字符串顺序为东北、东南、西南、西北。
        return TyphoonWindRadii(parts[0], parts[1], parts[2], parts[3])
            .takeIf(TyphoonWindRadii::available)
    }
    if (parts.size > 1) return null // Do not invent missing quadrants from an incomplete radius string.
    val uniform = parts.firstOrNull() ?: double(key)?.let(::positive)
    return uniform?.let { TyphoonWindRadii(it, it, it, it) }
}
