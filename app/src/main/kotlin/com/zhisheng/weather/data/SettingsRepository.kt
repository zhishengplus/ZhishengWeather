package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.zhisheng.weather.model.RadarSource

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// 数据源偏好：AUTO 默认小米→Open-Meteo（0.1.5-beta6 起百度/高德天气下线，两把 AK 只服务定位地理）。
// 需凭据的数据源只在开发者模式下可锁定；手动锁定时保持纯源，便于判断真实表现。
enum class SourcePref(val key: String, val cn: String, val en: String, val desc: String) {
    AUTO("auto", "自动优选", "AUTO", "按功能优选·失败降级"),
    // 自有品牌融合源：并发抓取小米/中央气象台/腾讯/天气网/Open-Meteo 多模型（有 key 时加和风/彩云），
    // 端侧加权共识 + 实况纠偏 + 完整性补齐。它是唯一允许补缺的手动源——"融合+完整"就是它的identity。
    ZHISHENG("zhisheng", "枳生天气源", "ZHISHENG", "多源共识·免配置"),
    NMC("nmc", "中央气象台", "NMC", "免配置·官方"),
    QWEATHER("qweather", "和风天气", "QWEATHER", "开发者·需凭据"),
    CAIYUN("caiyun", "彩云天气", "CAIYUN", "开发者·需 Token"),
    XIAOMI("xiaomi", "小米公开接口", "XIAOMI", "免配置·国内"),
    OPEN_METEO("openmeteo", "Open-Meteo", "OPEN-METEO", "免配置·全球");

    companion object {
        // 旧版本持久化的 "amap"/"baidu" 因枚举已无对应项，经 from() 自动回落 AUTO
        fun from(v: String?): SourcePref = entries.firstOrNull { it.key == v } ?: AUTO

        fun visible(developerMode: Boolean): List<SourcePref> = buildList {
            add(AUTO)
            add(ZHISHENG)
            add(XIAOMI)
            add(NMC)
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
        // AUTO 只读自身降级链（小米/中央气象台/Open-Meteo）的缓存；仍不接受仅手动锁定的和风/彩云缓存，
        // 也不把融合源缓存算作自身（AUTO 与枳生源切换时重拉一次，保持设置页语义严格一致）
        AUTO -> dataSource in setOf("XIAOMI", "NMC", "OPEN-METEO")
        ZHISHENG -> dataSource == "ZHISHENG"
        NMC -> dataSource == "NMC"
        QWEATHER -> dataSource == "QWEATHER"
        CAIYUN -> dataSource == "CAIYUN"
        XIAOMI -> dataSource == "XIAOMI"
        OPEN_METEO -> dataSource == "OPEN-METEO"
    }

    fun matches(data: com.zhisheng.weather.model.WeatherData): Boolean =
        matches(data.dataSource) &&
            // 枳生源的 blockSources 记录的是参与源（小米/中央气象台/…），身份只由 dataSource 唯一确定
            (this == AUTO || this == ZHISHENG || data.blockSources.values.all { it == data.dataSource })
}

// 氛围层强度：0.0.9 起默认“明显”，新用户首次打开即可看到完整天气表达。
enum class SoftGlowLevel(val key: String, val cn: String, val strength: Float, val drift: Float, val cycleSeconds: Double) {
    SUBTLE("subtle", "柔和", 1f, 1f, 72.0),
    FLOWING("flowing", "流光", 1.7f, 2.1f, 42.0);

    companion object {
        fun from(value: String?): SoftGlowLevel = entries.firstOrNull { it.key == value } ?: SUBTLE
    }
}

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
    PRECIPITATION("precipitation", "当前雨强", "PRECIP"),
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
        fun from(v: String?): ThemeMode = entries.firstOrNull { it.key == v } ?: LIGHT
    }
}

/**
 * 整套界面语言。经典终端保留 beta6 的原始视觉；澄空终端是 beta6-3 的默认新界面。
 * 只改变 Compose 表现层，不参与天气数据、定位、缓存或页面路由判断。
 */
enum class InterfaceStyle(val key: String, val cn: String, val en: String) {
    PHOSPHOR_VISTA("phosphor_vista", "澄空终端", "CLEAR SKY TERMINAL"),
    CLASSIC_TERMINAL("classic_terminal", "经典终端", "CLASSIC TERMINAL");

    companion object {
        fun from(v: String?): InterfaceStyle =
            entries.firstOrNull { it.key == v } ?: PHOSPHOR_VISTA
    }
}

/** 澄空首页模块的材质独立于界面主题；旧外观保留为可选项。 */
enum class HomeSurfaceStyle(val key: String, val cn: String) {
    CURRENT("current", "无界香氛"),
    FRAGRANCE_GLASS("fragrance_glass", "流光玻璃");

    companion object {
        fun from(value: String?): HomeSurfaceStyle = entries.firstOrNull { it.key == value } ?: FRAGRANCE_GLASS
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
    DARK("dark", "深色"),
    LIGHT("light", "浅色");

    companion object {
        fun from(v: String?): AppIconStyle = when (v) {
            // beta5 及更早版本使用 classic；无感迁移为同一枚深色图标。
            "classic" -> DARK
            else -> entries.firstOrNull { it.key == v } ?: CHARACTER
        }
    }
}

/** 主页首屏播报样式：默认保留天气娘，也为偏好纯天气工具的用户提供无人物模式。 */
enum class HomeBriefingStyle(val key: String, val cn: String) {
    WEATHER_GIRL("weather_girl", "天气娘"),
    TIPS("tips", "文字提示"),
    OFF("off", "关闭");

    companion object {
        fun from(v: String?): HomeBriefingStyle =
            entries.firstOrNull { it.key == v } ?: TIPS
    }
}

/** 横屏待机界面保留经典版，同时允许用户切换到新的沉浸式气象中枢。 */
enum class LandscapeStandbyStyle(val key: String, val cn: String) {
    CLASSIC("classic", "经典终端"),
    WEATHER_CORE("weather_core", "气象中枢");

    companion object {
        fun from(v: String?): LandscapeStandbyStyle =
            entries.firstOrNull { it.key == v } ?: WEATHER_CORE
    }
}

enum class AppLanguage(val key: String, val cn: String) {
    CHINESE("zh", "简体中文"),
    JAPANESE("ja", "日本語");

    companion object {
        fun from(value: String?): AppLanguage = entries.firstOrNull { it.key == value } ?: CHINESE
    }
}

enum class HomeModule(val key: String, val cn: String, val en: String) {
    HOURLY("hourly", "逐时预报", "HOURLY"),
    PRECIP("precip", "短时降水", "NOWCAST"),
    SPACETIME("spacetime", "气象视界", "WEATHER ATLAS"),
    DAILY("daily", "五天天气", "5-DAY FORECAST"),
    TELEMETRY("telemetry", "遥测数据", "TELEMETRY"),
    AQI("aqi", "空气质量", "AIR QUALITY"),
    INDICES("indices", "生活指数", "INDICES"),
    YESTERDAY("yesterday", "昨日复盘", "RETRO"),
    TYPHOON("typhoon", "台风关注", "TYPHOON");

    companion object {
        // 默认阅读顺序先回答用户最常看的三件事：接下来几小时、是否马上下雨、
        // 未来几天怎样；回看与雷达属于主动查看工具，放在核心预报之后。
        // 用户已经保存的自定义排序仍按原顺序读取，不会被默认值覆盖。
        val defaultOrder: List<HomeModule> = listOf(
            HOURLY,
            PRECIP,
            DAILY,
            TELEMETRY,
            AQI,
            SPACETIME,
            INDICES,
            YESTERDAY,
            TYPHOON,
        )

        // v2 标记 = 用户在本机主动保存过的排序。旧格式串（无标记）才走
        // 旧版迁移清洗；否则用户恰好拖成与旧默认/声明序一致的顺序时，
        // 读写两端都会把它当历史遗留静默重置，怎么拖都存不上。
        const val ORDER_MARKER = "v2:"

        fun orderFrom(raw: String?): List<HomeModule> {
            if (raw.isNullOrBlank()) return defaultOrder
            val userSaved = raw.startsWith(ORDER_MARKER)
            val source = if (userSaved) raw.substring(ORDER_MARKER.length) else raw
            val selected = source.split(',')
                .mapNotNull { key ->
                    when (key.trim()) {
                        // 0.1.4 早期体验版曾拆成两个序号；升级后无损并回一个模块。
                        "history", "radar", "weather_tools", "sky", "coast" -> SPACETIME
                        else -> entries.firstOrNull { it.key == key.trim() }
                    }
                }
                .distinct()
                .toMutableList()
            if (!userSaved) {
                // 旧版本曾把完整默认序列写入偏好；这不代表用户主动排序。
                // 仅当它仍与旧默认完全一致时迁移到新默认，任何真实改动过的顺序都原样保留。
                val previousDefault = listOf(HOURLY, PRECIP, DAILY, SPACETIME, TELEMETRY, AQI, INDICES, YESTERDAY, TYPHOON)
                if (selected == entries.toList() || selected == previousDefault) return defaultOrder
                // 旧版自定义顺序缺少专题时，以空气质量为锚；其余模块的相对顺序不变。
                if (SPACETIME !in selected) {
                    val anchor = selected.indexOf(AQI).takeIf { it >= 0 } ?: selected.indexOf(TELEMETRY)
                    val insertion = if (anchor >= 0) anchor + 1 else selected.size
                    selected.add(insertion, SPACETIME)
                }
            }
            selected += defaultOrder.filterNot(selected::contains)
            return selected
        }
    }
}

// 设置仓储
object SettingsRepository {
    private val KEY_USAGE_STATISTICS = booleanPreferencesKey("device_version_statistics")
    val usageStatistics: Flow<Boolean> by lazy { store.data.map { it[KEY_USAGE_STATISTICS] ?: true }.distinctUntilChanged() }
    suspend fun setUsageStatistics(enabled: Boolean) = store.edit { it[KEY_USAGE_STATISTICS] = enabled }

    private lateinit var store: DataStore<Preferences>

    private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
    private val KEY_SHOW_TYPHOON = booleanPreferencesKey("show_typhoon")
    private val KEY_SOURCE = stringPreferencesKey("source_pref")
    private val KEY_AMBIENCE = stringPreferencesKey("ambience")
    private val KEY_LIVING_SKY = booleanPreferencesKey("living_sky")
    private val KEY_SCANLINES = booleanPreferencesKey("scanlines")
    private val KEY_RELEASE_APPEARANCE = booleanPreferencesKey("release_appearance_20260907")
    private val KEY_RELEASE_BRIEFING = booleanPreferencesKey("release_briefing_20260907")
    private val KEY_SOFT_GLOW = booleanPreferencesKey("vista_soft_glow")
    private val KEY_SOFT_GLOW_LEVEL = stringPreferencesKey("vista_soft_glow_level")
    private val KEY_STYLE_RESTORED = booleanPreferencesKey("style_choice_beta6_6")
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
    private val KEY_SHOW_SKY = booleanPreferencesKey("show_sky_photography")
    private val KEY_SHOW_COAST = booleanPreferencesKey("show_coast_weather")
    private val KEY_BOOT_ANIM = booleanPreferencesKey("boot_anim")
    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_STANDBY_BURN_IN_PROTECTION = booleanPreferencesKey("standby_burn_in_protection")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_INTERFACE_STYLE = stringPreferencesKey("interface_style")
    private val KEY_ACCENT_TONE = stringPreferencesKey("accent_tone")
    private val KEY_APP_ICON = stringPreferencesKey("app_icon")
    private val KEY_HOME_BRIEFING_STYLE = stringPreferencesKey("home_briefing_style")
    private val KEY_HOME_SURFACE_STYLE = stringPreferencesKey("home_surface_style")
    private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    private val KEY_LAST_AUTO_LOCATE = longPreferencesKey("last_auto_locate_at")
    private val KEY_MODULE_ORDER = stringPreferencesKey("home_module_order")
    private val KEY_RADAR_SOURCE = stringPreferencesKey("radar_source")
    private val KEY_DEVELOPER = booleanPreferencesKey("developer_mode")
    private val KEY_LANDSCAPE_STANDBY = booleanPreferencesKey("landscape_standby")
    private val KEY_LANDSCAPE_STANDBY_STYLE = stringPreferencesKey("landscape_standby_style")
    private val KEY_TELEMETRY_METRICS = stringPreferencesKey("telemetry_metrics")
    private val KEY_LIFE_INDEX_METRICS = stringPreferencesKey("life_index_metrics")

    fun init(context: Context) {
        store = context.applicationContext.settingsStore
    }

    // c=摄氏度 f=华氏度
    val tempUnit: Flow<String> by lazy { store.data.map { it[KEY_TEMP_UNIT] ?: "c" } }
    val showTyphoon: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.typhoon && (it[KEY_SHOW_TYPHOON] ?: true) } }
    val developerMode: Flow<Boolean> by lazy {
        store.data.map { it[KEY_DEVELOPER] ?: false }.distinctUntilChanged()
    }
    val sourcePref: Flow<SourcePref> by lazy {
        store.data.map { prefs ->
            SourcePref.from(prefs[KEY_SOURCE]).effective(prefs[KEY_DEVELOPER] ?: false)
        }.distinctUntilChanged()
    }
    val ambience: Flow<AmbienceLevel> by lazy { store.data.map { if (ReleaseFeatures.weatherAtmosphere) AmbienceLevel.from(it[KEY_AMBIENCE]) else AmbienceLevel.OFF } }
    val livingSky: Flow<Boolean> by lazy {
        store.data.map { ReleaseFeatures.weatherAtmosphere && (it[KEY_LIVING_SKY] ?: false) }.distinctUntilChanged()
    }
    val scanlines: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.weatherAtmosphere && (it[KEY_SCANLINES] ?: true) } }
    val softGlow: Flow<Boolean> by lazy { store.data.map { it[KEY_SOFT_GLOW] ?: true }.distinctUntilChanged() }
    val softGlowLevel: Flow<SoftGlowLevel> by lazy { store.data.map { SoftGlowLevel.from(it[KEY_SOFT_GLOW_LEVEL]) }.distinctUntilChanged() }
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
    val showSkyPhotography: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_SKY] ?: true } }
    val showCoastWeather: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.coastalWeather && (it[KEY_SHOW_COAST] ?: true) } }
    val bootAnim: Flow<Boolean> by lazy { store.data.map { it[KEY_BOOT_ANIM] ?: true } }
    val keepScreenOn: Flow<Boolean> by lazy { store.data.map { it[KEY_KEEP_SCREEN_ON] ?: false } }
    val standbyBurnInProtection: Flow<Boolean> by lazy {
        store.data.map { it[KEY_STANDBY_BURN_IN_PROTECTION] ?: true }.distinctUntilChanged()
    }
    val landscapeStandby: Flow<Boolean> by lazy {
        store.data.map { it[KEY_LANDSCAPE_STANDBY] ?: true }.distinctUntilChanged()
    }
    val landscapeStandbyStyle: Flow<LandscapeStandbyStyle> by lazy {
        store.data.map { if (ReleaseFeatures.classicTheme) LandscapeStandbyStyle.from(it[KEY_LANDSCAPE_STANDBY_STYLE]) else LandscapeStandbyStyle.WEATHER_CORE }.distinctUntilChanged()
    }
    val telemetryMetrics: Flow<Set<TelemetryMetric>> by lazy {
        store.data.map { TelemetryMetric.selectionFrom(it[KEY_TELEMETRY_METRICS]) }.distinctUntilChanged()
    }
    val lifeIndexMetrics: Flow<Set<LifeIndexMetric>> by lazy {
        store.data.map { LifeIndexMetric.selectionFrom(it[KEY_LIFE_INDEX_METRICS]) }.distinctUntilChanged()
    }
    // 主题模式（v0.0.5）：默认深色
    val themeMode: Flow<ThemeMode> by lazy {
        store.data.map { ReleaseFeatures.themeMode(it[KEY_THEME_MODE], it[KEY_RELEASE_APPEARANCE] == true) }.distinctUntilChanged()
    }
    val interfaceStyle: Flow<InterfaceStyle> by lazy {
        store.data.map {
            if (it[KEY_STYLE_RESTORED] == true) ReleaseFeatures.interfaceStyle(InterfaceStyle.from(it[KEY_INTERFACE_STYLE]))
            else InterfaceStyle.PHOSPHOR_VISTA
        }.distinctUntilChanged()
    }
    val accentTone: Flow<AccentTone> by lazy {
        store.data.map { AccentTone.from(it[KEY_ACCENT_TONE]) }.distinctUntilChanged()
    }
    val appIconStyle: Flow<AppIconStyle> by lazy {
        store.data.map { AppIconStyle.from(it[KEY_APP_ICON]) }.distinctUntilChanged()
    }
    val homeBriefingStyle: Flow<HomeBriefingStyle> by lazy {
        store.data.map { if (it[KEY_RELEASE_BRIEFING] == true) HomeBriefingStyle.from(it[KEY_HOME_BRIEFING_STYLE]) else HomeBriefingStyle.TIPS }.distinctUntilChanged()
    }
    val homeSurfaceStyle: Flow<HomeSurfaceStyle> by lazy {
        store.data.map { HomeSurfaceStyle.from(it[KEY_HOME_SURFACE_STYLE]) }.distinctUntilChanged()
    }
    val appLanguage: Flow<AppLanguage> by lazy {
        store.data.map { AppLanguage.from(it[KEY_APP_LANGUAGE]) }.distinctUntilChanged()
    }
    // 1B：自动定位闸门持久化——上次自动定位时间落盘，进程重启不归零
    val lastAutoLocateAt: Flow<Long> by lazy {
        store.data.map { it[KEY_LAST_AUTO_LOCATE] ?: 0L }
    }
    suspend fun setLastAutoLocateAt(millis: Long) = store.edit {
        it[KEY_LAST_AUTO_LOCATE] = millis
    }
    val moduleOrder: Flow<List<HomeModule>> by lazy {
        store.data.map { HomeModule.orderFrom(it[KEY_MODULE_ORDER]) }.distinctUntilChanged()
    }

    // 雷达页数据源：RainViewer / 彩云拼图，用户选择后持久化
    val radarSource: Flow<RadarSource> by lazy {
        store.data.map { RadarSource.fromKey(it[KEY_RADAR_SOURCE]) }.distinctUntilChanged()
    }

    suspend fun setRadarSource(source: RadarSource) = store.edit { it[KEY_RADAR_SOURCE] = source.key }

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
    suspend fun amapUnlocked(): Boolean {
        SecretStore.currentAmap()
        return SecretStore.amapReady && developerMode.first()
    }
    suspend fun baiduUnlocked(): Boolean {
        SecretStore.currentBaidu()
        return SecretStore.baiduReady && developerMode.first()
    }
    suspend fun purgeRetiredProviderData() = store.edit { prefs ->
        listOf("caiyun_app_key", "caiyun_app_secret", "caiyun_credential")
            .map(::stringPreferencesKey)
            .forEach(prefs::remove)
    }
    suspend fun setAmbience(a: AmbienceLevel) = store.edit { it[KEY_AMBIENCE] = a.key }
    suspend fun setLivingSky(enabled: Boolean) = store.edit { it[KEY_LIVING_SKY] = enabled }
    suspend fun setScanlines(v: Boolean) = store.edit { it[KEY_SCANLINES] = v }
    suspend fun setSoftGlow(v: Boolean) = store.edit { it[KEY_SOFT_GLOW] = v }
    suspend fun setSoftGlowLevel(v: SoftGlowLevel) = store.edit { it[KEY_SOFT_GLOW_LEVEL] = v.key }
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
    suspend fun setShowSkyPhotography(v: Boolean) = store.edit { it[KEY_SHOW_SKY] = v }
    suspend fun setShowCoastWeather(v: Boolean) = store.edit { it[KEY_SHOW_COAST] = v }
    suspend fun setBootAnim(v: Boolean) = store.edit { it[KEY_BOOT_ANIM] = v }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[KEY_KEEP_SCREEN_ON] = v }
    suspend fun setStandbyBurnInProtection(v: Boolean) = store.edit { it[KEY_STANDBY_BURN_IN_PROTECTION] = v }
    suspend fun setLandscapeStandby(v: Boolean) = store.edit { it[KEY_LANDSCAPE_STANDBY] = v }
    suspend fun setLandscapeStandbyStyle(style: LandscapeStandbyStyle) = store.edit {
        it[KEY_LANDSCAPE_STANDBY_STYLE] = style.key
    }
    suspend fun setTelemetryMetrics(selection: Set<TelemetryMetric>) = store.edit {
        it[KEY_TELEMETRY_METRICS] = TelemetryMetric.selectionKey(selection)
    }
    suspend fun setLifeIndexMetrics(selection: Set<LifeIndexMetric>) = store.edit {
        it[KEY_LIFE_INDEX_METRICS] = LifeIndexMetric.selectionKey(selection)
    }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME_MODE] = mode.key; it[KEY_RELEASE_APPEARANCE] = true }
    suspend fun setInterfaceStyle(style: InterfaceStyle) = store.edit {
        it[KEY_INTERFACE_STYLE] = style.key
        it[KEY_STYLE_RESTORED] = true
    }
    suspend fun setAccentTone(tone: AccentTone) = store.edit { it[KEY_ACCENT_TONE] = tone.key }
    suspend fun setAppIconStyle(style: AppIconStyle) = store.edit { it[KEY_APP_ICON] = style.key }
    suspend fun setHomeBriefingStyle(style: HomeBriefingStyle) = store.edit {
        it[KEY_HOME_BRIEFING_STYLE] = style.key
        it[KEY_RELEASE_BRIEFING] = true
    }
    suspend fun setHomeSurfaceStyle(style: HomeSurfaceStyle) = store.edit {
        it[KEY_HOME_SURFACE_STYLE] = style.key
    }
    suspend fun setAppLanguage(language: AppLanguage) = store.edit {
        it[KEY_APP_LANGUAGE] = language.key
    }
    suspend fun setModuleOrder(order: List<HomeModule>) = store.edit {
        it[KEY_MODULE_ORDER] = HomeModule.ORDER_MARKER + HomeModule.orderFrom(
            HomeModule.ORDER_MARKER + order.joinToString(",") { module -> module.key })
            .joinToString(",") { module -> module.key }
    }
}
