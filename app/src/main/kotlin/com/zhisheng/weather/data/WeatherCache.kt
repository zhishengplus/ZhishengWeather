package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.City
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

internal fun weatherCacheCorruptionHandler() = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

private val Context.weatherCacheStore: DataStore<Preferences> by preferencesDataStore(
    name = "weather_cache", corruptionHandler = weatherCacheCorruptionHandler())

// 离线缓存：每城最近一次抓取成功的 WeatherData。
// 断网 / 全部数据源失败 / 全局超时时的兜底展示源（v0.0.4）。
// 缓存完整数据、按城市分键。
@Serializable
data class CachedWeather(
    val data: WeatherData,
    val savedAtMillis: Long,
)

internal const val MAX_OFFLINE_WEATHER_AGE_MS = 24 * 60 * 60_000L

internal fun CachedWeather.isUsableOfflineAt(nowMillis: Long): Boolean {
    val savedAge = nowMillis - savedAtMillis
    if (savedAge !in 0..MAX_OFFLINE_WEATHER_AGE_MS) return false
    val providerAge = data.updateTime?.let { nowMillis - it } ?: savedAge
    return providerAge in -5 * 60_000L..MAX_OFFLINE_WEATHER_AGE_MS
}

object WeatherCache {

    private val json = Json { ignoreUnknownKeys = true }

    private fun key(locationKey: String) = stringPreferencesKey("cached_$locationKey")

    suspend fun save(context: Context, locationKey: String, data: WeatherData) {
        if (data.current == null || data.error != null) return
        // 两个 DataStore 分开读取，避免在 weather_cache 的 edit 事务里悬挂读取城市 DataStore。
        // 当前键始终保留，兼容“刚选中城市、城市流尚未完成刷新”的极短竞态窗口。
        val savedCities = CityRepository.savedCities()
        savedCities.firstOrNull { it.locationKey == locationKey }?.let {
            if (!weatherCoordinatesMatch(it, data)) return
        }
        val validCacheKeys = savedCities
            .map { key(it.locationKey).name }
            .toSet() + key(locationKey).name
        saveToStore(context.applicationContext.weatherCacheStore, locationKey, data, validCacheKeys)
    }

    internal suspend fun saveToStore(store: DataStore<Preferences>, locationKey: String, data: WeatherData,
        validCacheKeys: Set<String> = setOf(key(locationKey).name), nowMillis: Long? = null) {
        if (data.current == null || data.error != null) return
        store.edit { prefs ->
            // Read the clock inside the transaction: correction may occur while this write waits.
            val committedAt = nowMillis ?: System.currentTimeMillis()
            val previous = prefs[key(locationKey)]?.let { raw ->
                runCatching { json.decodeFromString(CachedWeather.serializer(), raw) }.getOrNull()
            }
            // A widget worker and a foreground request may finish out of order.
            if (!shouldReplaceWeatherCache(previous?.data, data, committedAt)) return@edit
            prefs[key(locationKey)] = json.encodeToString(CachedWeather.serializer(), CachedWeather(data, committedAt))
            prefs.asMap().keys
                .filter { it.name.startsWith("cached_") && it.name !in validCacheKeys }
                .forEach { prefs.remove(it) }
        }
    }

    suspend fun load(context: Context, locationKey: String): CachedWeather? =
        loadFromStore(context.applicationContext.weatherCacheStore, locationKey)

    /** Cache reads are optional: a storage failure must not prevent a fresh network request. */
    internal suspend fun loadFromStore(store: DataStore<Preferences>, locationKey: String): CachedWeather? = withContext(Dispatchers.IO) {
        val raw = try {
            store.data.first()[key(locationKey)]
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } ?: return@withContext null
        try {
            json.decodeFromString(CachedWeather.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun load(context: Context, city: City): CachedWeather? =
        load(context, city.locationKey)?.takeIf { weatherCoordinatesMatch(city, it.data) }
}
