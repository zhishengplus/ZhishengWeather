package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// 数据源偏好：AUTO 默认小米→公共源；和风只在开发者模式下可锁定。
// 装不上和风的用户锁 OPEN_METEO 即可拿到完整体验（实况/逐时/逐日/空气质量全免 key）。
enum class SourcePref(val key: String, val cn: String, val en: String, val desc: String) {
    AUTO("auto", "自动优选", "AUTO", "按功能优选·失败降级"),
    QWEATHER("qweather", "和风天气", "QWEATHER", "开发者·需凭据"),
    CAIYUN("caiyun", "彩云天气", "CAIYUN", "开发者·需 Token"),
    XIAOMI("xiaomi", "小米公开接口", "XIAOMI", "免配置·国内"),
    OPEN_METEO("openmeteo", "Open-Meteo", "OPEN-METEO", "免配置·全球");

    companion object {
        fun from(v: String?): SourcePref = entries.firstOrNull { it.key == v } ?: AUTO

        fun visible(developerMode: Boolean): List<SourcePref> = buildList {
            add(AUTO)
            add(XIAOMI)
            add(OPEN_METEO)
            if (developerMode) {
                add(CAIYUN)
                add(QWEATHER)
            }
        }
    }

    fun effective(developerMode: Boolean): SourcePref =
        if ((this == QWEATHER || this == CAIYUN) && !developerMode) AUTO else this

    fun matches(dataSource: String?): Boolean = when (this) {
        // AUTO 的真实链路只有小米与公共源；不能把上次手动选择的付费源缓存冒充自动结果。
        AUTO -> dataSource == "XIAOMI" || dataSource == "OPEN-METEO"
        QWEATHER -> dataSource == "QWEATHER"
        CAIYUN -> dataSource == "CAIYUN"
        XIAOMI -> dataSource == "XIAOMI"
        OPEN_METEO -> dataSource == "OPEN-METEO"
    }
}

// 氛围层强度：0.0.9 起默认“明显”，新用户首次打开即可看到完整天气表达。
enum class AmbienceLevel(val key: String, val cn: String, val factor: Float) {
    OFF("off", "关闭", 0f),
    SUBTLE("subtle", "克制", 1f),
    VIVID("vivid", "明显", 3.1f),
    INTENSE("intense", "强烈", 4.8f);

    val vivid: Boolean get() = this == VIVID || this == INTENSE
    val motionScale: Float get() = when (this) {
        INTENSE -> 1.62f
        VIVID -> 1.32f
        else -> 1f
    }

    companion object {
        fun from(v: String?): AmbienceLevel = entries.firstOrNull { it.key == v } ?: VIVID
    }
}

enum class TelemetryMetric(val key: String, val cn: String, val en: String) {
    HUMIDITY("humidity", "湿度", "HUMIDITY"),
    WIND("wind", "风向风速", "WIND"),
    PRESSURE("pressure", "气压", "PRESS"),
    UV("uv", "紫外线", "UV"),
    VISIBILITY("visibility", "能见度", "VIS"),
    DEW_POINT("dew_point", "露点", "DEW"),
    CLOUD_COVER("cloud_cover", "云量", "CLOUD"),
    WIND_GUST("wind_gust", "阵风", "GUST"),
    PRECIPITATION("precipitation", "1时降水", "PRECIP"),
    LUMINARY("luminary", "日月", "LUMINARY");

    companion object {
        private const val NONE = "__none__"
        val defaultSelection: Set<TelemetryMetric> = entries.toSet()

        fun selectionFrom(raw: String?): Set<TelemetryMetric> = when {
            raw == null -> defaultSelection
            raw == NONE -> emptySet()
            else -> raw.split(',')
                .mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }
                .toSet()
        }

        fun selectionKey(selection: Set<TelemetryMetric>): String =
            if (selection.isEmpty()) NONE else entries.filter(selection::contains).joinToString(",") { it.key }
    }
}

enum class LifeIndexMetric(val key: String, val cn: String, val en: String) {
    CAR_WASH("car_wash", "洗车", "CAR WASH"),
    SPORTS("sports", "运动", "SPORTS"),
    DRESS("dress", "穿衣", "DRESS"),
    FISHING("fishing", "钓鱼", "FISHING"),
    UV("uv", "紫外线", "UV"),
    TRAVEL("travel", "旅游", "TRAVEL"),
    ALLERGY("allergy", "过敏", "ALLERGY"),
    COMFORT("comfort", "舒适度", "COMFORT"),
    COLD("cold", "感冒", "COLD"),
    AIR_POLLUTION("air_pollution", "空气扩散", "AIR"),
    AIR_CONDITIONER("air_conditioner", "空调", "A/C"),
    SUNGLASSES("sunglasses", "太阳镜", "GLASSES"),
    MAKEUP("makeup", "化妆", "MAKEUP"),
    DRYING("drying", "晾晒", "DRYING"),
    TRAFFIC("traffic", "交通", "TRAFFIC"),
    SUNSCREEN("sunscreen", "防晒", "SPF");

    companion object {
        private const val NONE = "__none__"
        val defaultSelection: Set<LifeIndexMetric> = entries.toSet()

        fun selectionFrom(raw: String?): Set<LifeIndexMetric> = when {
            raw == null -> defaultSelection
            raw == NONE -> emptySet()
            else -> raw.split(',')
                .mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }
                .toSet()
        }

        fun selectionKey(selection: Set<LifeIndexMetric>): String =
            if (selection.isEmpty()) NONE else entries.filter(selection::contains).joinToString(",") { it.key }

        fun fromEnglish(raw: String): LifeIndexMetric? = when (raw.trim().uppercase()) {
            "CAR WASH" -> CAR_WASH
            "SPORTS" -> SPORTS
            "DRESS" -> DRESS
            "FISHING" -> FISHING
            "UV" -> UV
            "TRAVEL" -> TRAVEL
            "ALLERGY" -> ALLERGY
            "COMFORT" -> COMFORT
            "COLD" -> COLD
            "AIR POLLUTION", "AIR" -> AIR_POLLUTION
            "A/C" -> AIR_CONDITIONER
            "SUNGLASSES", "GLASSES" -> SUNGLASSES
            "MAKEUP" -> MAKEUP
            "DRYING" -> DRYING
            "TRAFFIC" -> TRAFFIC
            "SPF" -> SUNSCREEN
            else -> null
        }
    }
}

// 主题模式（v0.0.5）：默认深色保持磷光终端品牌，可切纸面浅色或跟随系统
enum class ThemeMode(val key: String, val cn: String) {
    DARK("dark", "深色"),
    LIGHT("light", "浅色"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun from(v: String?): ThemeMode = entries.firstOrNull { it.key == v } ?: DARK
    }
}

// 强调色亮度只调整数据绿与线框蓝，橙/红等语义色保持稳定。
enum class AccentTone(val key: String, val cn: String) {
    STANDARD("standard", "标准"),
    SOFT("soft", "柔和");

    companion object {
        fun from(v: String?): AccentTone = entries.firstOrNull { it.key == v } ?: STANDARD
    }
}

enum class AppIconStyle(val key: String, val cn: String) {
    CHARACTER("character", "天气娘"),
    CLASSIC("classic", "经典");

    companion object {
        fun from(v: String?): AppIconStyle =
            entries.firstOrNull { it.key == v } ?: CHARACTER
    }
}

enum class HomeModule(val key: String, val cn: String, val en: String) {
    HOURLY("hourly", "逐时预报", "HOURLY"),
    PRECIP("precip", "短时降水", "NOWCAST"),
    SPACETIME("spacetime", "时空观测", "TIME / RADAR"),
    DAILY("daily", "逐日预报", "FORECAST"),
    TELEMETRY("telemetry", "遥测数据", "TELEMETRY"),
    AQI("aqi", "空气质量", "AIR QUALITY"),
    INDICES("indices", "生活指数", "INDICES"),
    YESTERDAY("yesterday", "昨日复盘", "RETRO"),
    TYPHOON("typhoon", "台风关注", "TYPHOON");

    companion object {
        val defaultOrder: List<HomeModule> = entries.toList()

        fun orderFrom(raw: String?): List<HomeModule> {
            if (raw.isNullOrBlank()) return defaultOrder
            val selected = raw.orEmpty().split(',')
                .mapNotNull { key ->
                    when (key.trim()) {
                        // 0.1.4 早期体验版曾拆成两个序号；升级后无损并回一个模块。
                        "history", "radar", "weather_tools" -> SPACETIME
                        else -> entries.firstOrNull { it.key == key.trim() }
                    }
                }
                .distinct()
                .toMutableList()
            // 旧版自定义顺序中没有新模块：就近插在短时降水后，
            // 既不打乱用户已排好的其他模块，也不把新入口丢到页尾。
            if (SPACETIME !in selected) {
                val anchor = selected.indexOf(PRECIP)
                val insertion = if (anchor >= 0) anchor + 1 else selected.size
                selected.add(insertion, SPACETIME)
            }
            selected += defaultOrder.filterNot(selected::contains)
            return selected
        }
    }
}

// 设置仓储
object SettingsRepository {

    private lateinit var store: DataStore<Preferences>

    private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
    private val KEY_SHOW_TYPHOON = booleanPreferencesKey("show_typhoon")
    private val KEY_SOURCE = stringPreferencesKey("source_pref")
    private val KEY_AMBIENCE = stringPreferencesKey("ambience")
    private val KEY_SCANLINES = booleanPreferencesKey("scanlines")
    private val KEY_LOCATION_ENABLED = booleanPreferencesKey("location_enabled")
    private val KEY_PRECISE_LOCATION = booleanPreferencesKey("precise_location")
    private val KEY_PRECISE_LOCATION_ASKED = booleanPreferencesKey("precise_location_asked")
    private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
    private val KEY_PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
    private val KEY_SHOW_AQI = booleanPreferencesKey("show_aqi")
    private val KEY_SHOW_INDICES = booleanPreferencesKey("show_indices")
    private val KEY_SHOW_YESTERDAY = booleanPreferencesKey("show_yesterday")
    private val KEY_SHOW_PRECIP = booleanPreferencesKey("show_precip")
    private val KEY_SHOW_TELEMETRY = booleanPreferencesKey("show_telemetry")
    private val KEY_SHOW_WEATHER_TOOLS = booleanPreferencesKey("show_weather_tools")
    private val KEY_SHOW_HISTORY = booleanPreferencesKey("show_history")
    private val KEY_SHOW_RADAR = booleanPreferencesKey("show_radar")
    private val KEY_SHOW_SPACETIME = booleanPreferencesKey("show_spacetime")
    private val KEY_BOOT_ANIM = booleanPreferencesKey("boot_anim")
    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT_TONE = stringPreferencesKey("accent_tone")
    private val KEY_APP_ICON = stringPreferencesKey("app_icon")
    private val KEY_MODULE_ORDER = stringPreferencesKey("home_module_order")
    private val KEY_DEVELOPER = booleanPreferencesKey("developer_mode")
    private val KEY_LANDSCAPE_STANDBY = booleanPreferencesKey("landscape_standby")
    private val KEY_TELEMETRY_METRICS = stringPreferencesKey("telemetry_metrics")
    private val KEY_LIFE_INDEX_METRICS = stringPreferencesKey("life_index_metrics")

    fun init(context: Context) {
        store = context.applicationContext.settingsStore
    }

    // c=摄氏度 f=华氏度
    val tempUnit: Flow<String> by lazy { store.data.map { it[KEY_TEMP_UNIT] ?: "c" } }
    val showTyphoon: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_TYPHOON] ?: true } }
    val developerMode: Flow<Boolean> by lazy {
        store.data.map { it[KEY_DEVELOPER] ?: false }.distinctUntilChanged()
    }
    val sourcePref: Flow<SourcePref> by lazy {
        store.data.map { prefs ->
            SourcePref.from(prefs[KEY_SOURCE]).effective(prefs[KEY_DEVELOPER] ?: false)
        }.distinctUntilChanged()
    }
    val ambience: Flow<AmbienceLevel> by lazy { store.data.map { AmbienceLevel.from(it[KEY_AMBIENCE]) } }
    val scanlines: Flow<Boolean> by lazy { store.data.map { it[KEY_SCANLINES] ?: true } }
    // 定位总开关：关闭时 App 完全不碰位置权限（默认关，兑现「不主动获取权限」）
    val locationEnabled: Flow<Boolean> by lazy { store.data.map { it[KEY_LOCATION_ENABLED] ?: false } }
    // 街道级定位为单独的可选能力；关闭时仍只使用粗略位置。
    val preciseLocationEnabled: Flow<Boolean> by lazy {
        store.data.map { it[KEY_PRECISE_LOCATION] ?: false }.distinctUntilChanged()
    }
    val preciseLocationPermissionAsked: Flow<Boolean> by lazy {
        store.data.map { it[KEY_PRECISE_LOCATION_ASKED] ?: false }.distinctUntilChanged()
    }
    // kmh / ms / bft
    val windUnit: Flow<String> by lazy { store.data.map { it[KEY_WIND_UNIT] ?: "kmh" } }
    // hpa / mmhg / inhg
    val pressureUnit: Flow<String> by lazy { store.data.map { it[KEY_PRESSURE_UNIT] ?: "hpa" } }
    val showAqi: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_AQI] ?: true } }
    val showIndices: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_INDICES] ?: true } }
    val showYesterday: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_YESTERDAY] ?: true } }
    val showPrecip: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_PRECIP] ?: true } }
    val showTelemetry: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_TELEMETRY] ?: true } }
    val showSpacetime: Flow<Boolean> by lazy {
        store.data.map {
            it[KEY_SHOW_SPACETIME]
                ?: it[KEY_SHOW_WEATHER_TOOLS]
                ?: ((it[KEY_SHOW_HISTORY] ?: true) || (it[KEY_SHOW_RADAR] ?: true))
        }
    }
    val bootAnim: Flow<Boolean> by lazy { store.data.map { it[KEY_BOOT_ANIM] ?: true } }
    val keepScreenOn: Flow<Boolean> by lazy { store.data.map { it[KEY_KEEP_SCREEN_ON] ?: false } }
    val landscapeStandby: Flow<Boolean> by lazy {
        store.data.map { it[KEY_LANDSCAPE_STANDBY] ?: true }.distinctUntilChanged()
    }
    val telemetryMetrics: Flow<Set<TelemetryMetric>> by lazy {
        store.data.map { TelemetryMetric.selectionFrom(it[KEY_TELEMETRY_METRICS]) }.distinctUntilChanged()
    }
    val lifeIndexMetrics: Flow<Set<LifeIndexMetric>> by lazy {
        store.data.map { LifeIndexMetric.selectionFrom(it[KEY_LIFE_INDEX_METRICS]) }.distinctUntilChanged()
    }
    // 主题模式（v0.0.5）：默认深色
    val themeMode: Flow<ThemeMode> by lazy {
        store.data.map { ThemeMode.from(it[KEY_THEME_MODE]) }.distinctUntilChanged()
    }
    val accentTone: Flow<AccentTone> by lazy {
        store.data.map { AccentTone.from(it[KEY_ACCENT_TONE]) }.distinctUntilChanged()
    }
    val appIconStyle: Flow<AppIconStyle> by lazy {
        store.data.map { AppIconStyle.from(it[KEY_APP_ICON]) }.distinctUntilChanged()
    }
    val moduleOrder: Flow<List<HomeModule>> by lazy {
        store.data.map { HomeModule.orderFrom(it[KEY_MODULE_ORDER]) }.distinctUntilChanged()
    }

    suspend fun setTempUnit(unit: String) = store.edit { it[KEY_TEMP_UNIT] = unit }
    suspend fun setShowTyphoon(show: Boolean) = store.edit { it[KEY_SHOW_TYPHOON] = show }
    suspend fun setSourcePref(p: SourcePref) = store.edit { it[KEY_SOURCE] = p.key }
    suspend fun setDeveloperMode(v: Boolean) = store.edit { it[KEY_DEVELOPER] = v }
    suspend fun qweatherUnlocked(): Boolean {
        SecretStore.currentQw()
        return QWeatherApi.enabled && developerMode.first()
    }

    suspend fun caiyunUnlocked(): Boolean {
        SecretStore.currentCaiyun()
        return SecretStore.caiyunReady && developerMode.first()
    }
    suspend fun purgeRetiredProviderData() = store.edit { prefs ->
        listOf("caiyun_app_key", "caiyun_app_secret", "caiyun_credential")
            .map(::stringPreferencesKey)
            .forEach(prefs::remove)
    }
    suspend fun setAmbience(a: AmbienceLevel) = store.edit { it[KEY_AMBIENCE] = a.key }
    suspend fun setScanlines(v: Boolean) = store.edit { it[KEY_SCANLINES] = v }
    suspend fun setLocationEnabled(v: Boolean) = store.edit { it[KEY_LOCATION_ENABLED] = v }
    suspend fun setPreciseLocationEnabled(v: Boolean) = store.edit {
        it[KEY_PRECISE_LOCATION] = v
        it[KEY_PRECISE_LOCATION_ASKED] = false
    }
    suspend fun setPreciseLocationPermissionAsked() = store.edit { it[KEY_PRECISE_LOCATION_ASKED] = true }
    suspend fun setWindUnit(v: String) = store.edit { it[KEY_WIND_UNIT] = v }
    suspend fun setPressureUnit(v: String) = store.edit { it[KEY_PRESSURE_UNIT] = v }
    suspend fun setShowAqi(v: Boolean) = store.edit { it[KEY_SHOW_AQI] = v }
    suspend fun setShowIndices(v: Boolean) = store.edit { it[KEY_SHOW_INDICES] = v }
    suspend fun setShowYesterday(v: Boolean) = store.edit { it[KEY_SHOW_YESTERDAY] = v }
    suspend fun setShowPrecip(v: Boolean) = store.edit { it[KEY_SHOW_PRECIP] = v }
    suspend fun setShowTelemetry(v: Boolean) = store.edit { it[KEY_SHOW_TELEMETRY] = v }
    suspend fun setShowSpacetime(v: Boolean) = store.edit { it[KEY_SHOW_SPACETIME] = v }
    suspend fun setBootAnim(v: Boolean) = store.edit { it[KEY_BOOT_ANIM] = v }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[KEY_KEEP_SCREEN_ON] = v }
    suspend fun setLandscapeStandby(v: Boolean) = store.edit { it[KEY_LANDSCAPE_STANDBY] = v }
    suspend fun setTelemetryMetrics(selection: Set<TelemetryMetric>) = store.edit {
        it[KEY_TELEMETRY_METRICS] = TelemetryMetric.selectionKey(selection)
    }
    suspend fun setLifeIndexMetrics(selection: Set<LifeIndexMetric>) = store.edit {
        it[KEY_LIFE_INDEX_METRICS] = LifeIndexMetric.selectionKey(selection)
    }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME_MODE] = mode.key }
    suspend fun setAccentTone(tone: AccentTone) = store.edit { it[KEY_ACCENT_TONE] = tone.key }
    suspend fun setAppIconStyle(style: AppIconStyle) = store.edit { it[KEY_APP_ICON] = style.key }
    suspend fun setModuleOrder(order: List<HomeModule>) = store.edit {
        it[KEY_MODULE_ORDER] = HomeModule.orderFrom(order.joinToString(",") { module -> module.key })
            .joinToString(",") { module -> module.key }
    }
}
