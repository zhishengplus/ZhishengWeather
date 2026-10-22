package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherModuleEvidence
import java.security.MessageDigest

enum class WeatherModule(val key: String, val cn: String, val blocks: Set<String>) {
    CURRENT("current", "实况", setOf("current", "current-supplement")),
    FORECAST("forecast", "逐时与逐日预报", setOf("hourly", "daily", "hourly-precip-supplement", "daily-supplement")),
    MINUTELY("minutely", "短时降水", setOf("minutely")),
    ALERTS("alerts", "气象预警", setOf("alerts")),
    INDICES("indices", "生活指数", setOf("indices")),
    AIR("air", "空气质量", setOf("air", "aqi"));
}

data class ModuleSourcePolicy(val selections: Map<WeatherModule, SourcePref> = emptyMap()) {
    operator fun get(module: WeatherModule): SourcePref? = selections[module]
    val followsPrimary: Boolean get() = selections.isEmpty()
    fun with(module: WeatherModule, source: SourcePref?): ModuleSourcePolicy =
        ModuleSourcePolicy(if (source == null) selections - module else selections + (module to source))
    fun encode(): String = WeatherModule.entries.mapNotNull { module ->
        selections[module]?.let { "${module.key}=${it.key}" }
    }.joinToString(";")
    companion object {
        fun decode(raw: String?): ModuleSourcePolicy = ModuleSourcePolicy(buildMap {
            raw.orEmpty().split(';').forEach { part ->
                val pair = part.split('=', limit = 2)
                val module = WeatherModule.entries.firstOrNull { it.key == pair.firstOrNull() }
                val source = SourcePref.entries.firstOrNull { it.key == pair.getOrNull(1) }
                if (module != null && source != null) put(module, source)
            }
        })
    }
}

data class SourceCapabilities(val modules: Set<WeatherModule>, val hourly: Boolean = true, val note: String = "")

object WeatherSourceCapabilities {
    private val full = WeatherModule.entries.toSet()
    val map: Map<SourcePref, SourceCapabilities> = mapOf(
        SourcePref.AUTO to SourceCapabilities(full),
        SourcePref.ZHISHENG to SourceCapabilities(full),
        SourcePref.XIAOMI to SourceCapabilities(full),
        SourcePref.QWEATHER to SourceCapabilities(full, note = "字段取决于用户服务授权与覆盖"),
        SourcePref.CAIYUN to SourceCapabilities(full, note = "字段取决于用户服务授权与覆盖"),
        SourcePref.NMC to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.ALERTS, WeatherModule.AIR)),
        SourcePref.OPEN_METEO to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.MINUTELY, WeatherModule.AIR), note = "短时数据为15分钟模型桶；没有官方预警和生活指数"),
        SourcePref.AMAP to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST), hourly = false, note = "区县级实况与日预报；不提供逐小时、短时降水、预警、指数、空气"),
        SourcePref.BAIDU to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.ALERTS, WeatherModule.INDICES, WeatherModule.AIR), note = "区县级；逐小时和空气等字段仅在用户已有授权且响应提供时使用"),
        SourcePref.TENCENT to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.INDICES), hourly = false, note = "网站城市天气通道，无官方 API 稳定性保证；只有日预报，不是街道级天气"),
        SourcePref.WEATHERCN to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.ALERTS, WeatherModule.INDICES, WeatherModule.AIR), hourly = false, note = "网站城市天气通道；预报仅当日，不提供逐小时与短时降水"),
        SourcePref.SENIVERSE to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST, WeatherModule.INDICES), hourly = false, note = "个人免费版：主要城市实况、3天日预报和基础指数；不提供街道级天气"),
        SourcePref.MOJI to SourceCapabilities(setOf(WeatherModule.CURRENT, WeatherModule.FORECAST), hourly = false, note = "个人阿里云市场订阅，仅接实况与日预报；非永久免费，字段与时效依权限"),
    )
    fun supports(source: SourcePref, module: WeatherModule): Boolean = module in map.getValue(source).modules
}

/** Empty arrays are retained as actual zero-alert results only when the API acknowledged that block. */
internal fun modulePresent(data: WeatherData, module: WeatherModule): Boolean = when (module) {
    WeatherModule.CURRENT -> data.current != null
    WeatherModule.FORECAST -> data.hourly.isNotEmpty() || data.daily.isNotEmpty()
    WeatherModule.MINUTELY -> data.rainMinutes.isNotEmpty() || !data.rainNowcast.isNullOrBlank()
    WeatherModule.ALERTS -> {
        val source = data.moduleEvidence[module.key]?.source ?: data.blockSources["alerts"] ?: data.dataSource
        // Older NMC payloads incorrectly stamped an empty first page as a confirmed zero.
        // Their evidence must not erase a primary provider's existing local warnings.
        data.alerts.isNotEmpty() || (source != "NMC" &&
            (data.moduleEvidence[module.key] != null || data.blockSources["alerts"] != null))
    }
    WeatherModule.INDICES -> data.extraIndices.isNotEmpty() || data.carWashOk != null || data.sportsOk != null
    WeatherModule.AIR -> data.aqi != null
}

internal fun stampModules(data: WeatherData, city: City): WeatherData {
    val evidence = data.moduleEvidence.toMutableMap()
    WeatherModule.entries.filter { modulePresent(data, it) }.forEach { module ->
        if (module.key !in evidence) {
            val sources = module.blocks.mapNotNull(data.blockSources::get).distinct()
            evidence[module.key] = WeatherModuleEvidence(
                source = sources.joinToString(" + ").ifBlank { data.dataSource ?: "未知" },
                observationTime = when (module) {
                    WeatherModule.CURRENT -> data.updateTime
                    WeatherModule.MINUTELY -> data.rainMeta?.updateTime
                    else -> null // a current-observation timestamp is not a forecast/index publish time
                },
                fetchedAt = data.fetchedAt,
                requestedLatitude = city.latitude, requestedLongitude = city.longitude,
            )
        }
    }
    return data.copy(moduleEvidence = evidence)
}

internal fun replaceModule(base: WeatherData, donor: WeatherData, module: WeatherModule): WeatherData {
    if (donor.error != null || !modulePresent(donor, module)) return base
    var result = when (module) {
        WeatherModule.CURRENT -> base.copy(current = donor.current, updateTime = donor.updateTime,
            locationMatch = donor.locationMatch, error = null, dataSource = base.dataSource ?: donor.dataSource)
        WeatherModule.FORECAST -> base.copy(hourly = donor.hourly, daily = donor.daily,
            forecastSummary = donor.forecastSummary, yesterday = donor.yesterday, utcOffsetSeconds = donor.utcOffsetSeconds ?: base.utcOffsetSeconds)
        WeatherModule.MINUTELY -> base.copy(rainMinutes = donor.rainMinutes, rainNowcast = donor.rainNowcast,
            rainMeta = donor.rainMeta, rainDistanceKm = donor.rainDistanceKm)
        WeatherModule.ALERTS -> base.copy(alerts = donor.alerts)
        WeatherModule.INDICES -> base.copy(extraIndices = donor.extraIndices, carWashOk = donor.carWashOk, sportsOk = donor.sportsOk)
        WeatherModule.AIR -> base.copy(aqi = donor.aqi)
    }
    val donorSources = donor.blockSources.filterKeys(module.blocks::contains)
    result = result.copy(
        blockSources = base.blockSources.filterKeys { it !in module.blocks } + donorSources.ifEmpty {
            mapOf(module.blocks.first() to (donor.dataSource ?: "未知"))
        },
        moduleEvidence = base.moduleEvidence - module.key + donor.moduleEvidence.filterKeys { it == module.key },
    )
    return result
}

internal fun weatherConfigurationFingerprint(primary: SourcePref, policy: ModuleSourcePolicy, developer: Boolean, credentials: List<String>): String {
    // Length-prefix each field to avoid ambiguous boundaries; only the digest leaves this function.
    // 10.6 changes observation/forecast alignment and AQI provenance. Older processed weather
    // must be fetched again; this changes only weather snapshots, never cities or preferences.
    val values = listOf("module-v2-accuracy10.6", primary.key, policy.encode(), developer.toString()) + credentials
    val bytes = values.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

internal fun cacheMatchesConfiguration(data: WeatherData, primary: SourcePref, policy: ModuleSourcePolicy, fingerprint: String, credentialsEmpty: Boolean): Boolean =
    data.configurationFingerprint != null && data.configurationFingerprint == fingerprint
