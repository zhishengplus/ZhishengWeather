package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.*

private val Context.sceneWeatherStore by preferencesDataStore(name = "scene_weather_cache")

/** 场景链独立于普通 AUTO；精确坐标只发给明确署名的数据源，不反写普通实况。 */
object SceneWeatherRepository {
    internal const val MAX_COAST_GRID_DISTANCE_KM = 30.0
    private val cacheKey = stringPreferencesKey("v1")
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    private var engine: SceneWeatherEngine? = null

    @Synchronized
    fun init(context: Context) {
        if (engine != null) return
        val store = context.applicationContext.sceneWeatherStore
        engine = SceneWeatherEngine(
            request = ::request,
            load = {
                val raw = store.data.first()[cacheKey]
                if (raw == null || raw.length > 4_000_000) emptyMap()
                else json.decodeFromString<Map<String, SceneCacheEntry>>(raw)
            },
            save = { entries -> store.edit { it[cacheKey] = json.encodeToString(entries) } },
        )
    }

    suspend fun fetch(city: City, wantSky: Boolean, wantCoast: Boolean, force: Boolean = false): SceneWeatherData =
        withContext(Dispatchers.Default) {
            // JSON 解析、逐时天文计算和缓存整理不占用 Compose 主线程。
            requireNotNull(engine) { "SceneWeatherRepository.init required" }.fetch(city, wantSky, wantCoast && ReleaseFeatures.coastalWeather, force)
        }

    private suspend fun request(url: String): String? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { r ->
                        if (!r.isSuccessful) null
                        else try {
                            // 有限响应体，异常页面/供应商错误不能塞满缓存。
                            val source = r.body?.source()
                            if (source == null || source.request(512_001)) null else source.readUtf8()
                        } catch (_: IOException) { null }
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dl / 2).pow(2)
        return 12742.0176 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

internal const val SCENE_SKY_TTL = 45 * 60_000L
internal const val SCENE_MARINE_TTL = 2 * 60 * 60_000L
internal const val SCENE_MAX_STALE = 12 * 60 * 60_000L
private const val RETRY_INTERVAL = 2 * 60_000L
private const val MANUAL_INTERVAL = 60_000L

@Serializable
internal data class SceneCacheEntry(
    val sky: String? = null,
    val skyAt: Long = 0,
    val skyAttempt: Long = 0,
    val marine: String? = null,
    val marineAt: Long = 0,
    val marineAttempt: Long = 0,
)

/** 可注入网络/时间/持久层，单测无需 Android、密钥或真实网络。Mutex 覆盖合并写盘，避免并发倒灌。 */
internal class SceneWeatherEngine(
    private val request: suspend (String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val load: suspend () -> Map<String, SceneCacheEntry> = { emptyMap() },
    private val save: suspend (Map<String, SceneCacheEntry>) -> Unit = {},
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, SceneCacheEntry>(16, 0.75f, true)
    private var loaded = false

    suspend fun fetch(city: City, wantSky: Boolean, wantCoast: Boolean, force: Boolean): SceneWeatherData = mutex.withLock {
        if (!wantSky && !wantCoast) return@withLock SceneWeatherData()
        if (!validSceneCoordinates(city.latitude, city.longitude)) return@withLock SceneWeatherData(
            skyError = "地点坐标无效", coastError = "地点坐标无效",
        )
        val time = now()
        if (!loaded) {
            attempt { load() }?.entries?.toList()?.takeLast(12)?.forEach { (key, entry) -> cache[key] = entry }
            loaded = true
        }
        cache.entries.removeAll { (_, v) ->
            !usable(time, max(v.skyAt, v.skyAttempt)) && !usable(time, max(v.marineAt, v.marineAttempt))
        }
        // 完整坐标+选格策略，不把相近但不同的精确地点合并。
        val key = "${city.latitude},${city.longitude},${city.isPreciseLocation}"
        val old = cache[key] ?: SceneCacheEntry()
        val skyDue = wantSky && due(time, old.skyAt, old.skyAttempt, SCENE_SKY_TTL, force)
        val marineDue = wantCoast && due(time, old.marineAt, old.marineAttempt, SCENE_MARINE_TTL, force)
        val responses = coroutineScope {
            val sky = async {
                if (skyDue) attempt { request(SceneResponseParser.skyUrl(city)) }
                    ?.takeIf { SceneResponseParser.sky(it, city, time, time, includeStars = ReleaseFeatures.starPhotography) != null } else null
            }
            val marine = async {
                if (marineDue) attempt { request(SceneResponseParser.marineUrl(city)) }
                    ?.takeIf { SceneResponseParser.marine(it, city, time, time) != null } else null
            }
            sky.await() to marine.await()
        }
        val entry = old.copy(
            sky = responses.first ?: old.sky,
            skyAt = if (responses.first != null) time else old.skyAt,
            skyAttempt = if (skyDue) time else old.skyAttempt,
            marine = responses.second ?: old.marine,
            marineAt = if (responses.second != null) time else old.marineAt,
            marineAttempt = if (marineDue) time else old.marineAttempt,
        )
        cache[key] = entry
        while (cache.size > 12 || cache.values.sumOf { (it.sky?.length ?: 0) + (it.marine?.length ?: 0) } > 3_500_000) {
            cache.remove(cache.keys.first())
        }
        if (entry != old) attempt { save(cache.toMap()) } // 写盘失败仍返回已成功数据
        val sky = if (wantSky && usable(time, entry.skyAt)) entry.sky?.let {
            SceneResponseParser.sky(it, city, entry.skyAt, time, includeStars = ReleaseFeatures.starPhotography)
        } else null
        val marine = if (wantCoast && usable(time, entry.marineAt)) entry.marine?.let {
            SceneResponseParser.marine(it, city, entry.marineAt, time)
        } else null
        val skyStale = time - entry.skyAt >= SCENE_SKY_TTL || entry.skyAttempt > entry.skyAt
        val marineStale = time - entry.marineAt >= SCENE_MARINE_TTL || entry.marineAttempt > entry.marineAt
        SceneWeatherData(
            sky = sky?.copy(stale = skyStale),
            coast = marine?.forecast?.copy(stale = marineStale),
            fetchedAtMillis = max(entry.skyAt, entry.marineAt),
            stale = (sky != null && skyStale) || (marine?.forecast != null && marineStale),
            skyError = if (wantSky && sky == null) "天空摄影暂不可用，下拉可重试" else null,
            coastError = if (wantCoast && marine == null) "海岸数据暂不可用，下拉可重试" else null,
        )
    }

    private fun due(time: Long, saved: Long, attempt: Long, ttl: Long, force: Boolean): Boolean {
        val age = time - attempt
        if (attempt > 0 && age >= 0 && age < if (force) MANUAL_INTERVAL else RETRY_INTERVAL) return false
        return force || saved <= 0 || time - saved !in 0 until ttl
    }

    private fun usable(time: Long, saved: Long) = saved > 0 && time - saved in 0..SCENE_MAX_STALE

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (ce: CancellationException) { throw ce } catch (_: Exception) { null }
}

internal fun validSceneCoordinates(lat: Double, lon: Double): Boolean =
    lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
