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
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume

internal val domesticWeatherClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build()

/** Cancellation closes the socket; HTTP errors and oversized payloads never become weather. No credentials are logged. */
internal suspend fun weatherAwaitText(client: OkHttpClient, request: Request): String? =
    weatherAwaitTextWithStatus(client, request).text

internal enum class DomesticWeatherTransportFailure { TLS_IDENTITY, TLS_CERTIFICATE, NETWORK }
internal data class DomesticWeatherHttpResponse(
    val text: String?, val status: Int?, val failure: DomesticWeatherTransportFailure? = null,
)

/**
 * Like [weatherAwaitText] but preserves the HTTP status code so callers can
 * distinguish an expired AppCode (401), a wrong token (403), quota exhaustion
 * (429) or a gateway timeout from a plain network failure.
 */
internal suspend fun weatherAwaitTextWithStatus(client: OkHttpClient, request: Request): DomesticWeatherHttpResponse = suspendCancellableCoroutine { continuation ->
    val call = client.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            val failure = when (e) {
                is SSLPeerUnverifiedException -> DomesticWeatherTransportFailure.TLS_IDENTITY
                is SSLHandshakeException -> DomesticWeatherTransportFailure.TLS_CERTIFICATE
                else -> DomesticWeatherTransportFailure.NETWORK
            }
            if (continuation.isActive) continuation.resume(DomesticWeatherHttpResponse(null, null, failure))
        }
        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                val text = if (!res.isSuccessful) null else runCatching {
                    val body = res.body ?: return@runCatching null
                    if (body.contentLength() > 2_000_000L) return@runCatching null
                    val source = body.source()
                    source.request(2_000_001L)
                    if (source.buffer.size > 2_000_000L) null else source.readUtf8()
                }.getOrNull()
                if (continuation.isActive) continuation.resume(DomesticWeatherHttpResponse(text, res.code))
            }
        }
    })
}

private val domesticJson = Json { ignoreUnknownKeys = true }
internal suspend fun weatherAwaitJson(request: Request, client: OkHttpClient = domesticWeatherClient): JsonObject? = weatherAwaitText(client, request)
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

    internal suspend fun moji(city: City, creds: MojiRuntimeCreds, verifyOnly: Boolean = false,
        client: OkHttpClient = domesticWeatherClient): WeatherData {
        val request = mojiRequest(city, creds) ?: return WeatherData(error = "墨迹配置不完整，请填写所订阅坐标产品的 HTTPS 地址与凭据")
        val nowResponse = weatherAwaitTextWithStatus(client, request)
        val now = nowResponse.text?.let { runCatching { domesticJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        if (now == null) {
            if (nowResponse.failure in setOf(DomesticWeatherTransportFailure.TLS_IDENTITY, DomesticWeatherTransportFailure.TLS_CERTIFICATE)) {
                return WeatherData(error = "墨迹 HTTPS 证书校验未通过：请核对所购产品的 HTTPS 域名，不能只把 http 改成 https")
            }
            val status = nowResponse.status
            return WeatherData(error = when (status) {
                401 -> "墨迹认证失败（HTTP 401）：AppCode 无效或已过期，请在阿里云控制台核对"
                403 -> "墨迹权限被拒（HTTP 403）：Token 与接口不匹配，或该接口未订阅"
                404 -> "墨迹接口不存在（HTTP 404）：请核对 HTTPS 地址路径是否与所购产品一致"
                429 -> "墨迹额度已用完（HTTP 429）：请检查阿里云市场订阅余量"
                502, 503, 504 -> "墨迹网关暂不可用（HTTP $status）：请稍后重试"
                null -> "墨迹请求超时或无响应：请检查网络连接"
                200 -> "墨迹返回内容无法解析（HTTP 200）：请核对接口地址或稍后重试"
                else -> "墨迹请求失败（HTTP $status）：请核对 AppCode、Token 与 HTTPS 地址"
            })
        }
        if (now.domesticText("code") != "0") {
            val mojiMsg = now.domesticText("msg") ?: now.domesticText("message")
            return WeatherData(error = "墨迹返回错误：" + (mojiMsg ?: ("code=" + now.domesticText("code"))))
        }
        val daily = if (verifyOnly || creds.dailyEndpoint.isBlank()) null else mojiRequest(city, creds, true)?.let { weatherAwaitJson(it, client) }
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
