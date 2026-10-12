package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal val domesticWeatherClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()

/** Cancellation closes the socket; HTTP errors and oversized payloads never become weather. No credentials are logged. */
internal suspend fun weatherAwaitText(client: OkHttpClient, request: Request): String? = suspendCancellableCoroutine { continuation ->
    val call = client.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resume(null) }
        override fun onResponse(call: Call, response: Response) {
            val text = response.use { res ->
                if (!res.isSuccessful) null else runCatching {
                    val body = res.body ?: return@runCatching null
                    if (body.contentLength() > 2_000_000L) return@runCatching null
                    val source = body.source()
                    source.request(2_000_001L)
                    if (source.buffer.size > 2_000_000L) null else source.readUtf8()
                }.getOrNull()
            }
            if (continuation.isActive) continuation.resume(text)
        }
    })
}

private val domesticJson = Json { ignoreUnknownKeys = true }
internal suspend fun weatherAwaitJson(request: Request): JsonObject? = weatherAwaitText(domesticWeatherClient, request)
    ?.let { runCatching { domesticJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }

/** Free city website channels and explicit personal subscriptions, with no shared account or backend. */
internal object DomesticWeatherSource {
    private val seniverseGate = Mutex()
    private var lastSeniverseStartNanos = 0L
    private suspend fun seniverseCall(request: Request): JsonObject? = seniverseGate.withLock {
        val elapsed = (System.nanoTime() - lastSeniverseStartNanos) / 1_000_000L
        if (lastSeniverseStartNanos != 0L && elapsed < 1_100L) delay(1_100L - elapsed)
        lastSeniverseStartNanos = System.nanoTime()
        weatherAwaitJson(request)
    }

    suspend fun fetchSeniverse(city: City): WeatherData {
        if (!SettingsRepository.developerMode.first()) return WeatherData(error = "请先开启自有服务接入")
        return seniverse(city, SecretStore.currentSeniverse().apiKey)
    }

    internal suspend fun seniverse(city: City, key: String, verifyOnly: Boolean = false): WeatherData {
        val nowRequest = seniverseRequest(city, key, "now") ?: return WeatherData(error = "心知 Key 或坐标无效")
        val now = seniverseCall(nowRequest)
        // Do not spend another two calls when the key is rejected or the location is not covered.
        if ((now?.get("results") as? JsonArray)?.singleOrNull() !is JsonObject) return WeatherData(error = "心知请求失败，请核对 Key、免费版覆盖及网络")
        val daily = if (verifyOnly) null else seniverseCall(seniverseRequest(city, key, "daily")!!)
        val indices = if (verifyOnly) null else seniverseCall(seniverseRequest(city, key, "indices")!!)
        return parseSeniverseWeather(now, daily, indices, city, System.currentTimeMillis())
    }

    suspend fun fetchMoji(city: City): WeatherData {
        if (!SettingsRepository.developerMode.first()) return WeatherData(error = "请先开启自有服务接入")
        return moji(city, SecretStore.currentMoji())
    }

    internal suspend fun moji(city: City, creds: MojiRuntimeCreds, verifyOnly: Boolean = false): WeatherData {
        val request = mojiRequest(city, creds) ?: return WeatherData(error = "墨迹配置不完整，请填写所订阅坐标产品的 HTTPS 地址与凭据")
        val now = weatherAwaitJson(request)
        if (now == null || now.domesticText("code") != "0") return WeatherData(error = "墨迹请求失败，请核对 AppCode、对应 Token、订阅额度与 HTTPS 地址")
        val daily = if (verifyOnly || creds.dailyEndpoint.isBlank()) null else mojiRequest(city, creds, true)?.let { weatherAwaitJson(it) }
        return parseMojiWeather(now, daily, city, System.currentTimeMillis())
    }

    suspend fun fetchTencent(city: City): WeatherData {
        val area = tencentPublicArea(city) ?: return WeatherData(error = "腾讯网站通道需要明确的省市名称；可手选城市或使用支持坐标的来源")
        val bundle = TencentWeatherApi.fetch(area.first, area.second)
            ?: return WeatherData(error = "腾讯网站天气通道暂不可用，请检查城市覆盖与网络")
        val data = tencentToWeatherData(bundle.copy(alarms = emptyList()), System.currentTimeMillis())
            ?: return WeatherData(error = "腾讯未返回有效天气")
        val update = bundle.observe?.updateTime?.let { runCatching {
            java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        }.getOrNull() }?.takeIf { it <= System.currentTimeMillis() + 300_000L }
        return data.copy(current = data.current?.copy(windSpeed = null), updateTime = update,
            dataSource = "TENCENT", blockSources = buildMap {
                if (data.current != null) put("current", "TENCENT")
                if (data.daily.isNotEmpty()) put("daily", "TENCENT")
                if (data.extraIndices.isNotEmpty()) put("indices", "TENCENT")
            }, sourceDiagnostics = mapOf("location" to "按${area.second}查询城市天气；未向此网站通道发送设备坐标"))
    }

    suspend fun fetchWeatherCn(city: City): WeatherData {
        val bundle = WeatherComCnApi.fetch(city) ?: return WeatherData(error = "中国天气网未匹配到该城市或网站通道暂不可用")
        val data = wcnToWeatherData(bundle, System.currentTimeMillis()) ?: return WeatherData(error = "中国天气网未返回有效天气")
        return data.copy(daily = data.daily.map { it.copy(windSpeed = null) }, dataSource = "WEATHERCN",
            blockSources = buildMap {
                if (data.current != null) put("current", "WEATHERCN")
                if (data.daily.isNotEmpty()) put("daily", "WEATHERCN")
                if (data.alerts.isNotEmpty()) put("alerts", "WEATHERCN")
                if (data.extraIndices.isNotEmpty()) put("indices", "WEATHERCN")
                if (data.aqi != null) put("air", "WEATHERCN")
            }, moduleEvidence = data.moduleEvidence.mapValues { (_, evidence) -> evidence.copy(
                requestedLatitude = city.latitude, requestedLongitude = city.longitude) },
            sourceDiagnostics = mapOf("location" to "按网站城市编号查询，预报仅当日；不是街道级天气"))
    }
}

internal fun tencentPublicArea(city: City): Pair<String, String>? {
    val municipalities = mapOf("北京" to "北京市", "上海" to "上海市", "天津" to "天津市", "重庆" to "重庆市")
    val parts = city.affiliation.split('·', ',', '，').map(String::trim).filter(String::isNotBlank)
    val compact = parts.joinToString("").removePrefix("中国")
    val joinedProvince = Regex("^(.*?(?:省|自治区|特别行政区)|北京市|上海市|天津市|重庆市)")
        .find(compact)?.value
    val province = parts.firstOrNull { it.endsWith("省") || it.endsWith("自治区") || it.endsWith("特别行政区") || it in municipalities.values }
        ?: municipalities[parts.firstOrNull()] ?: municipalities[city.name] ?: joinedProvince ?: return null
    val cityName = parts.map { it.removePrefix(province) }.firstOrNull {
        it.isNotBlank() && (it.endsWith("市") || it.endsWith("自治州") || it.endsWith("盟") || it.endsWith("地区")) }
        ?: province.takeIf { it in municipalities.values }
        ?: joinedProvince?.let { Regex("^(.+?(?:自治州|地区|市|盟))").find(compact.removePrefix(it))?.value }
        ?: city.name.takeIf { it.endsWith("市") } ?: return null
    return province to cityName
}
