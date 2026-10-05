package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// 常驻地点缓存（1B，省高德/百度配额）：定位坐标命中已知地点 200 米内时，
// 街道/区县/adcode 直接复用，零付费调用。本缓存包含约 110 米格点坐标与最近命中时间，
// 因此按敏感位置数据管理：7 天过期、最多 100 条，并被 backup_rules / data_extraction_rules
// 显式排除，不进系统云备份与设备迁移。

@Serializable
data class KnownPlace(
    val latGrid: Double,
    val lonGrid: Double,
    val street: String? = null,
    val district: String? = null,
    val amapAdcode: String? = null,
    val baiduAdcode: String? = null,
    val lastHitMillis: Long = 0L,
)

private val Context.knownPlacesStore: DataStore<Preferences> by preferencesDataStore(name = "known_places")

object KnownPlaceStore {

    private const val TAG = "ZhishengWeather"
    private val KEY = stringPreferencesKey("places")
    private const val CAP = 100
    private const val MATCH_RADIUS_KM = 0.2          // 200 米
    private const val EXPIRE_MILLIS = 7L * 24 * 3_600_000L
    private const val HIT_PERSIST_INTERVAL_MILLIS = 6L * 3_600_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    // 进程内快查表 + 命中续期：命中立即更新内存，落盘最多每 6 小时一次（写盘节流）。
    @Volatile
    private var memory: List<KnownPlace>? = null
    private var lastPersistMillis: Long = 0L

    private fun grid(value: Double): Double = Math.round(value * 1_000.0) / 1_000.0

    suspend fun find(lat: Double, lon: Double): KnownPlace? = mutex.withLock {
        val places = memory ?: load().also { memory = it }
        val now = System.currentTimeMillis()
        val alive = places.filter { now - it.lastHitMillis < EXPIRE_MILLIS }
        val hit = alive
            .map { it to distanceKm(lat, lon, it.latGrid, it.lonGrid) }
            .filter { it.second <= MATCH_RADIUS_KM }
            .minByOrNull { it.second }
            ?.first
        if (hit == null) {
            memory = alive
            if (alive.size != places.size) persistLocked(alive, now)
            return@withLock null
        }
        val refreshed = hit.copy(lastHitMillis = now)
        val updated = alive.map { if (it == hit) refreshed else it }
        memory = updated
        // 命中续期最多每 6 小时落盘一次；进程重启后首次命中会立即落盘。
        if (lastPersistMillis == 0L || now - lastPersistMillis !in 0 until HIT_PERSIST_INTERVAL_MILLIS) {
            persistLocked(updated, now)
        }
        refreshed
    }

    suspend fun upsert(place: KnownPlace): KnownPlace = mutex.withLock {
        val rounded = place.copy(latGrid = grid(place.latGrid), lonGrid = grid(place.lonGrid))
        val places = memory ?: load().also { memory = it }
        val now = System.currentTimeMillis()
        val alive = places.filter { now - it.lastHitMillis < EXPIRE_MILLIS }
        val stamped = rounded.copy(lastHitMillis = now)
        val nearest = alive
            .map { it to distanceKm(rounded.latGrid, rounded.lonGrid, it.latGrid, it.lonGrid) }
            .filter { it.second <= MATCH_RADIUS_KM }
            .minByOrNull { it.second }
        val merged = when {
            nearest == null -> alive + stamped
            // 自愈：新鲜查询的文字或区划码任一变化都覆盖旧条目。
            nearest.first.district != stamped.district || nearest.first.street != stamped.street ||
                nearest.first.amapAdcode != stamped.amapAdcode || nearest.first.baiduAdcode != stamped.baiduAdcode ->
                alive.map { if (it == nearest.first) stamped else it }
            else -> alive.map { if (it == nearest.first) nearest.first.copy(lastHitMillis = now) else it }
        }
        val trimmed = merged
            .sortedByDescending { it.lastHitMillis }
            .take(CAP)
        memory = trimmed
        persistLocked(trimmed, now)
        stamped
    }

    private suspend fun load(): List<KnownPlace> {
        val context = appContextSafe ?: return emptyList()
        val raw = context.knownPlacesStore.data.first()[KEY] ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(KnownPlace.serializer()), raw)
                .filter { it.latGrid.isFinite() && it.lonGrid.isFinite() && it.latGrid in -90.0..90.0 && it.lonGrid in -180.0..180.0 }
                .sortedByDescending { it.lastHitMillis }
                .take(CAP)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "常驻地点缓存解码失败（len=${raw.length}），按空处理：${e.message}")
            context.knownPlacesStore.edit { it.remove(KEY) }
            emptyList()
        }
    }

    private suspend fun persistLocked(places: List<KnownPlace>, now: Long) {
        val context = appContextSafe ?: return
        context.knownPlacesStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(ListSerializer(KnownPlace.serializer()), places)
        }
        lastPersistMillis = now
    }

    // init 由 ZhishengApplication 注入（与其他 Store 同一模式），避免在无 Application 的上下文里触雷
    @Volatile
    private var appContextSafe: Context? = null

    fun init(context: Context) {
        appContextSafe = context.applicationContext
    }
}
