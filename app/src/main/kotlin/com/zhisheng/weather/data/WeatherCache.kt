package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.WeatherData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.weatherCacheStore: DataStore<Preferences> by preferencesDataStore(name = "weather_cache")

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
        val entry = CachedWeather(data = data, savedAtMillis = System.currentTimeMillis())
        // 两个 DataStore 分开读取，避免在 weather_cache 的 edit 事务里悬挂读取城市 DataStore。
        // 当前键始终保留，兼容“刚选中城市、城市流尚未完成刷新”的极短竞态窗口。
        val validCacheKeys = CityRepository.savedCities()
            .map { key(it.locationKey).name }
            .toSet() + key(locationKey).name
        context.applicationContext.weatherCacheStore.edit { prefs ->
            val previous = prefs[key(locationKey)]?.let { raw ->
                runCatching { json.decodeFromString(CachedWeather.serializer(), raw) }.getOrNull()
            }
            // A widget worker and a foreground request may finish out of order.
            if ((previous?.data?.fetchedAt ?: 0L) > (data.fetchedAt ?: 0L)) return@edit
            prefs[key(locationKey)] = json.encodeToString(CachedWeather.serializer(), entry)
            prefs.asMap().keys
                .filter { it.name.startsWith("cached_") && it.name !in validCacheKeys }
                .forEach { prefs.remove(it) }
        }
    }

    suspend fun load(context: Context, locationKey: String): CachedWeather? = withContext(Dispatchers.IO) {
        val raw = context.applicationContext.weatherCacheStore.data.first()[key(locationKey)] ?: return@withContext null
        try {
            json.decodeFromString(CachedWeather.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
