package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.widgetStore: DataStore<Preferences> by preferencesDataStore(name = "widget_cache")

// 小组件快照：主 App 每次成功抓取后写一份，小组件直接读。
// 这样小组件刷新不必自己跑完整数据链路，离线时也有内容可显示。
@Serializable
data class WidgetSnapshot(
    val city: String = "",
    val temp: Int? = null,
    val high: Int? = null,
    val low: Int? = null,
    val feelsLike: Int? = null,
    val humidity: Int? = null,
    val windText: String = "",
    val rainChance: Int? = null,
    val text: String = "",
    val conditionName: String = "",
    val aqi: Int? = null,
    val aqiLevel: String = "",
    val aqiStandard: String = "",
    val updateMillis: Long = 0L,
    val source: String = "",
    val utcOffsetSeconds: Int? = null,
    val hours: List<WidgetHour> = emptyList(),
    val lifeTips: List<WidgetLifeTip> = emptyList(),
    val days: List<WidgetDay> = emptyList(),
)

@Serializable
data class WidgetHour(val label: String, val temp: Int?, val conditionName: String)

@Serializable
data class WidgetLifeTip(val label: String, val value: String)

@Serializable
data class WidgetDay(val label: String, val high: Int?, val low: Int?, val conditionName: String)

object WidgetCache {

    private val KEY = stringPreferencesKey("snapshot")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(context: Context, snap: WidgetSnapshot) {
        context.applicationContext.widgetStore.edit {
            it[KEY] = json.encodeToString(WidgetSnapshot.serializer(), snap)
        }
    }

    suspend fun load(context: Context): WidgetSnapshot? {
        val raw = context.applicationContext.widgetStore.data.first()[KEY] ?: return null
        return try {
            json.decodeFromString(WidgetSnapshot.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
