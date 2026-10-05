package com.zhisheng.weather.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * nmc 用空字符串表示“无数据”（air=""、precipitation=""、温度差 "" 等散落各字段）。
 * 宽松数值解码：任意 JSON 标量取文本后转数值，空串/非法一律 null——
 * 否则单个空串字段会让整个 /rest/weather 响应解码失败（全国性事故）。
 */
internal object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NmcLenientDouble", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Double? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val text = (input.decodeJsonElement() as? JsonPrimitive)?.contentOrNull
        return text?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    }
    override fun serialize(encoder: Encoder, value: Double?) = encoder.encodeDouble(value ?: 0.0)
}

internal object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NmcLenientInt", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Int? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val text = (input.decodeJsonElement() as? JsonPrimitive)?.contentOrNull
        return text?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.toInt()
    }
    override fun serialize(encoder: Encoder, value: Int?) = encoder.encodeInt(value ?: 0)
}

/**
 * 中央气象台（中国气象局 nmc.cn）免密钥接口。
 *
 * 这些端点是 nmc.cn 网页自己使用的数据通道：公开、免 Key、全程 HTTPS，
 * 数据口径为中央气象台官方预报。不是官方对外承诺的 API——无 SLA、结构可能
 * 变化，因此只作为自动优选链与可锁定源中的一员，失败由 SourceHealth 熔断
 * 并降级到其他源。转载官方公开发布信息需显著标注“数据来源：中央气象台”。
 */
object NmcApi {

    private const val BASE = "https://www.nmc.cn"

    // 部分端点对无 UA 请求返回 403；携带常规浏览器 UA。
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class NmcProvince(val code: String = "", val name: String = "", val url: String = "")

    @Serializable
    data class NmcCityEntry(val code: String = "", val province: String = "", val city: String = "", val url: String = "")

    @Serializable
    data class NmcStation(val code: String = "", val province: String = "", val city: String = "")

    @Serializable
    data class NmcWeather(
        @Serializable(with = LenientDoubleSerializer::class) val temperature: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val airpressure: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val humidity: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val rain: Double? = null,
        @Serializable(with = LenientIntSerializer::class) val rcomfort: Int? = null,
        @Serializable(with = LenientIntSerializer::class) val icomfort: Int? = null,
        val info: String? = null,
        val img: String? = null,
        @Serializable(with = LenientDoubleSerializer::class) val feelst: Double? = null,
    )

    @Serializable
    data class NmcWind(
        val direct: String? = null,
        @Serializable(with = LenientDoubleSerializer::class) val degree: Double? = null,
        val power: String? = null,
        @Serializable(with = LenientDoubleSerializer::class) val speed: Double? = null,
    )

    @Serializable
    data class NmcSun(val sunrise: String? = null, val sunset: String? = null)

    @Serializable
    data class NmcReal(
        val station: NmcStation? = null,
        val publish_time: String? = null,
        val weather: NmcWeather? = null,
        val wind: NmcWind? = null,
        val sunriseSunset: NmcSun? = null,
    )

    /** /rest/weather 的昼夜半段：现象文本 + 国标码 + 温度（字符串，可能为空）。 */
    @Serializable
    data class NmcHalfWeather(val info: String? = null, val img: String? = null, val temperature: String? = null)

    @Serializable
    data class NmcHalfWind(val direct: String? = null, val power: String? = null)

    @Serializable
    data class NmcHalf(val weather: NmcHalfWeather? = null, val wind: NmcHalfWind? = null)

    @Serializable
    data class NmcPredictDay(
        val date: String? = null,
        val day: NmcHalf? = null,
        val night: NmcHalf? = null,
        // 缺数据时为 ""（如万宁第 6 天），必须宽松解码
        @Serializable(with = LenientDoubleSerializer::class) val precipitation: Double? = null,
    )

    @Serializable
    data class NmcPredict(
        val station: NmcStation? = null,
        val publish_time: String? = null,
        val detail: List<NmcPredictDay> = emptyList(),
    )

    /**
     * 日高低温曲线：前 7 天为实况（当天一行是"截至当前的当日最高/最低"），
     * 之后为预报。晚间 nmc 会把今天预报的昼段整个置 9999，今天的高低温
     * 就需要从这里兜底（否则出现 "今天 —°"）。
     */
    @Serializable
    data class NmcTemp(
        val time: String? = null,
        @Serializable(with = LenientDoubleSerializer::class) val max_temp: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val min_temp: Double? = null,
    )

    /** AQI：大城市站点才有（如北京 54511），小站为空串，故全部按字符串接。 */
    @Serializable
    data class NmcAir(
        val forecasttime: String? = null,
        val aqi: String? = null,
        val text: String? = null,
    )

    /** 过去 24 小时逐时实况；9999 为无效哨兵值（如无雨站 rain24h）。 */
    @Serializable
    data class NmcPassed(
        val time: String? = null,
        @Serializable(with = LenientDoubleSerializer::class) val temperature: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val humidity: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val pressure: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val windDirection: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val windSpeed: Double? = null,
        @Serializable(with = LenientDoubleSerializer::class) val rain1h: Double? = null,
    )

    @Serializable
    data class NmcFull(
        val real: NmcReal? = null,
        val predict: NmcPredict? = null,
        // air 在部分站点是对象、在更多站点是空串 ""——必须按 JsonElement 接，
        // 否则一个空串字段让整个 /rest/weather 响应解码失败（全国性事故）。
        val air: kotlinx.serialization.json.JsonElement? = null,
        val passedchart: List<NmcPassed> = emptyList(),
        val tempchart: List<NmcTemp> = emptyList(),
    )

    /** /rest/weather 响应外层：{msg, code, data:{real, predict, air, passedchart}}。 */
    @Serializable
    data class NmcFullEnvelope(val code: Int = 0, val data: NmcFull? = null)

    /** air 字段安全解码：仅当它是对象且结构合法时返回，其余一律 null。 */
    fun decodeAir(element: kotlinx.serialization.json.JsonElement?): NmcAir? {
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
        return runCatching { json.decodeFromJsonElement(NmcAir.serializer(), obj) }.getOrNull()
    }

    @Serializable
    data class NmcAlarmPage(val count: Int = 0, val list: List<NmcAlarm> = emptyList())

    @Serializable
    data class NmcAlarm(
        val title: String = "",
        val issuetime: String = "",
        val url: String = "",
    )

    @Serializable
    data class NmcAlarmData(val page: NmcAlarmPage? = null)

    @Serializable
    data class NmcAlarmEnvelope(val code: Int = 0, val data: NmcAlarmData? = null)

    /** 省列表进程级缓存：nmc 全国省列表基本不变，避免每次刷新重复请求。 */
    @Volatile
    private var provinceCache: List<NmcProvince>? = null

    private val cityCache = java.util.concurrent.ConcurrentHashMap<String, List<NmcCityEntry>>()

    suspend fun provinces(): List<NmcProvince>? {
        provinceCache?.let { return it }
        val result = getJson<List<NmcProvince>>("$BASE/f/rest/province") ?: return null
        if (result.isNotEmpty()) provinceCache = result
        return provinceCache
    }

    suspend fun cities(pcode: String): List<NmcCityEntry>? {
        cityCache[pcode]?.let { return it }
        val result = getJson<List<NmcCityEntry>>("$BASE/f/rest/province/$pcode") ?: return null
        if (result.isNotEmpty()) cityCache[pcode] = result
        return cityCache[pcode]
    }

    suspend fun real(scode: String): NmcReal? = getJson("$BASE/f/rest/real/$scode")

    /**
     * nmc 网页的主数据端点：一次返回实况（real）、结构化 7 天预报（predict.detail，
     * 昼/夜现象+温度+日降水量）、空气质量（air，部分站点）、过去 24 小时逐时实况
     * （passedchart）。等价于“实况接口 + 预报页 HTML”的总和且更干净。
     */
    suspend fun weather(scode: String): NmcFull? =
        getJson<NmcFullEnvelope>("$BASE/rest/weather?stationid=$scode")?.data

    suspend fun alarms(pageSize: Int = 50): List<NmcAlarm> =
        getJson<NmcAlarmEnvelope>("$BASE/rest/findAlarm?pageNo=1&pageSize=$pageSize")
            ?.data?.page?.list.orEmpty()

    /** 7 天/逐 3 小时数据内嵌在城市预报页 HTML 中，返回原始文本交给 NmcSource 解析。 */
    suspend fun forecastHtml(pageUrl: String): String? = withContext(Dispatchers.IO) { getText(BASE + pageUrl) }

    private suspend inline fun <reified T> getJson(url: String): T? = withContext(Dispatchers.IO) {
        val body = getText(url) ?: return@withContext null
        try {
            json.decodeFromString<T>(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun getText(url: String): String? = try {
        okHttp.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/html, */*")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (_: Exception) {
        null
    }
}
