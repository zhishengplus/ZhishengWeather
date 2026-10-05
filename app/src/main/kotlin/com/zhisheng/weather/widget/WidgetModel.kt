package com.zhisheng.weather.widget

import android.content.Context
import com.zhisheng.weather.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

enum class WidgetKind(val title: String, val classicTitle: String, val description: String, val width: Int, val height: Int, val columns: Int, val rows: Int) {
    PULSE("时光气象", "时间信号", "时钟与天气，留给桌面更多空间", 344, 76, 4, 1),
    NOW("天气小窗", "微型观测站", "此刻温度、天气与今日温差", 164, 164, 2, 2),
    BAND("逐时天光", "出行监测", "未来六小时的温度与降水概率", 344, 174, 4, 2),
    WEEK("日历天气", "五日趋势", "澄空的时钟日历，经典的温差趋势", 344, 194, 4, 2),
    SCENE("天气全景", "气象终端", "时间、预报与由你排序的天气读数", 344, 300, 4, 4);
    fun name(style: WidgetStyle) = if (style == WidgetStyle.CLASSIC) classicTitle else title
}
enum class WidgetStyle(val key: String, val title: String) {
    VISTA("vista", "澄空"), CLASSIC("classic", "经典");
    companion object { fun from(value: String?) = entries.firstOrNull { it.key == value } ?: VISTA }
}
enum class WidgetIconSet(val key: String, val title: String, val subtitle: String) {
    WUJIE("wujie", "无界", "新版多色天气图标"),
    CLASSIC("classic", "经典", "清晰的彩色天气图标");
    companion object { fun from(value: String?) = entries.firstOrNull { it.key == value } ?: WUJIE }
}
internal const val WidgetMinimumNumberScale = .85f
internal const val WidgetMaximumNumberScale = 1.25f
internal fun normalizedWidgetNumberScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(WidgetMinimumNumberScale, WidgetMaximumNumberScale) else 1f

@Serializable
data class WidgetConfig(
    val cityKey: String = "",
    val locationMode: String = "selected",
    val fixedCity: City? = null,
    val theme: String = "light",
    val text: String = "auto",
    val opacity: Int = 88,
    val textScale: Float = 1f,
    val protectText: Boolean = true,
    val intervalMinutes: Int = 30,
    val showLunar: Boolean = true,
    val showAir: Boolean = true,
    val showWeatherGirl: Boolean = false,
    val clock24: Boolean = true,
    val tempUnit: String = "c",
    /** Independent from the visual theme. Beta9 defaults to the new 无界 set. */
    val iconSet: String = WidgetIconSet.WUJIE.key,
    val metrics: List<String> = listOf("feels", "humidity", "wind", "air", "rain", "pressure"),
    val clickTime: String = "clock",
    val clickDate: String = "calendar",
    // Weather areas default to the forecast; each widget may opt into its editor.
    val clickWeather: String = "app",
) {
    fun normalized() = copy(opacity = opacity.coerceIn(0, 100), textScale = normalizedWidgetNumberScale(textScale),
        intervalMinutes = intervalMinutes.coerceIn(30, 120),
        iconSet = WidgetIconSet.from(iconSet).key,
        // The removed alarm display left configs whose time tap pointed at "alarm";
        // keep that intent as the system clock action instead of resetting it.
        clickTime = if (clickTime == "alarm") "clock" else clickTime,
        clickWeather = clickWeather.takeIf { it in setOf("app", "settings", "none") } ?: "app",
        metrics = metrics.filter { it in metricLabels }.distinct().take(6))
    companion object {
        fun defaults(style: WidgetStyle) = WidgetConfig(theme = if (style == WidgetStyle.CLASSIC) "dark" else "light")
    }
}
val metricLabels = linkedMapOf("feels" to "体感", "humidity" to "湿度", "wind" to "风力", "air" to "空气", "pressure" to "气压", "uv" to "紫外线", "rain" to "降水概率")
internal fun migrateWeatherClick(config: WidgetConfig, updated: Boolean): WidgetConfig =
    config.normalized()
@Serializable data class WidgetSnapshot(val city: City, val weather: WeatherData)
@Serializable data class NotificationSettings(val enabled: Boolean = false, val style: String = "vista", val config: WidgetConfig = WidgetConfig(), val alertsEnabled: Boolean = false)
@Serializable private data class PendingWidget(val style: String, val kind: String, val config: WidgetConfig, val createdAt: Long,
    val completedId: Int? = null, val configApplied: Boolean = false)
object WidgetStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("widget_beta9", Context.MODE_PRIVATE)
    fun config(c: Context, id: Int, style: WidgetStyle = WidgetStyle.VISTA): WidgetConfig = runCatching {
        val p = prefs(c)
        p.getString("config_$id", null)?.let {
            val decoded = json.decodeFromString<WidgetConfig>(it).normalized()
            // Existing desktop widgets should follow the current weather-first tap
            // behavior too. The marker protects later explicit edits from migration.
            if (!p.getBoolean("weather_click_20260943_$id", false)) {
                val migrated = migrateWeatherClick(decoded, false)
                p.edit().putString("config_$id", json.encodeToString(WidgetConfig.serializer(), migrated))
                    .putBoolean("weather_click_20260943_$id", true).apply()
                migrated
            } else decoded
        }
    }.getOrNull() ?: WidgetConfig.defaults(style)
    fun save(c: Context, id: Int, config: WidgetConfig) { prefs(c).edit().putString("config_$id", json.encodeToString(WidgetConfig.serializer(), config.normalized())).putBoolean("weather_click_20260943_$id", true).apply() }
    fun delete(c: Context, id: Int) { prefs(c).edit().remove("config_$id").remove("action_migrated_$id").remove("weather_click_20260943_$id").apply() }
    fun hasConfig(c: Context, id: Int) = prefs(c).contains("config_$id")
    fun recentCities(c: Context): List<City> = runCatching {
        prefs(c).getString("recent_cities", null)?.let { json.decodeFromString<List<City>>(it) }
    }.getOrNull().orEmpty()
    fun rememberCity(c: Context, city: City) {
        val list = (listOf(city) + recentCities(c)).distinctBy { it.locationKey }.take(12)
        prefs(c).edit().putString("recent_cities", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(City.serializer()), list)).apply()
    }
    fun preparePin(c: Context, style: WidgetStyle, kind: WidgetKind, config: WidgetConfig): String {
        val token = UUID.randomUUID().toString()
        val p = prefs(c)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("pin_") }.forEach { key ->
            val entry = runCatching { json.decodeFromString<PendingWidget>(p.getString(key, "")!!) }.getOrNull()
            if (entry == null || System.currentTimeMillis() - entry.createdAt > 600_000) editor.remove(key)
        }
        editor.putString("pin_$token", json.encodeToString(PendingWidget.serializer(), PendingWidget(style.key, kind.name, config, System.currentTimeMillis()))).apply()
        return token
    }
    fun applyPin(c: Context, token: String, instance: WidgetInstance): Boolean {
        val raw = prefs(c).getString("pin_$token", null) ?: return false
        val entry = runCatching { json.decodeFromString<PendingWidget>(raw) }.getOrNull() ?: return false
        if (entry.kind != instance.kind.name || entry.style != instance.style.key) return false
        if (entry.completedId != null) return entry.completedId == instance.id && entry.configApplied
        val applied = System.currentTimeMillis() - entry.createdAt in 0..600_000L
        if (applied) save(c, instance.id, entry.config)
        // Durable acknowledgement: the editor may be paused or recreated while
        // the launcher confirms. Do not infer success from unrelated widget IDs.
        prefs(c).edit().putString("pin_$token", json.encodeToString(PendingWidget.serializer(),
            entry.copy(completedId = instance.id, configApplied = applied))).apply()
        return applied
    }
    internal fun pinCompletion(c: Context, token: String): Boolean? {
        val entry = runCatching { prefs(c).getString("pin_$token", null)?.let { json.decodeFromString<PendingWidget>(it) } }.getOrNull() ?: return null
        val id = entry.completedId ?: return null
        return entry.configApplied && WidgetRuntime.instance(c, id) != null
    }
    fun snapshot(c: Context, key: String): WidgetSnapshot? = runCatching {
        prefs(c).getString("snapshot_$key", null)?.let { json.decodeFromString<WidgetSnapshot>(it) }
    }.getOrNull()
    @Synchronized fun publish(c: Context, city: City, weather: WeatherData) {
        if (weather.current == null || weather.error != null || weather.fetchedAt == null) return
        if ((snapshot(c, city.locationKey)?.weather?.fetchedAt ?: 0L) > weather.fetchedAt) return
        prefs(c).edit().putString("snapshot_${city.locationKey}", json.encodeToString(WidgetSnapshot.serializer(), WidgetSnapshot(city, weather))).apply()
    }
    fun notification(c: Context): NotificationSettings = runCatching {
        prefs(c).getString("notification_v2", null)?.let { json.decodeFromString<NotificationSettings>(it) }
    }.getOrNull() ?: NotificationSettings()
    fun saveNotification(c: Context, value: NotificationSettings) {
        prefs(c).edit().putString("notification_v2", json.encodeToString(NotificationSettings.serializer(), value)).apply()
    }
}
internal fun resolveWidgetCity(config: WidgetConfig, selected: City?, cities: List<City>, locatedKey: String?): City? = when (config.locationMode) {
    "located" -> cities.firstOrNull { it.locationKey == locatedKey }
    "fixed" -> cities.firstOrNull { it.locationKey == config.cityKey } ?: config.fixedCity?.takeIf { it.locationKey == config.cityKey }
    else -> selected
}
internal fun widgetStale(data: WeatherData?, now: Long, interval: Int): Boolean {
    val fetched = data?.fetchedAt ?: return true
    return now - fetched !in -300_000..maxOf(interval * 120_000L, 3_600_000L) ||
        (data.updateTime?.let { now - it > 3 * 3_600_000L } == true)
}
