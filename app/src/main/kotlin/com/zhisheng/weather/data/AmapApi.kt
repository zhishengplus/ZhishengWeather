package com.zhisheng.weather.data

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class AmapLookupResult(
    val ok: Boolean,
    val street: String? = null,
    val formattedAddress: String? = null,
    val info: String? = null,
    val infocode: String? = null,
)

private data class AmapRawResult(val result: AmapLookupResult, val root: JsonObject? = null)

/**
 * 高德 Web 服务只作为开发者可选的街道名称增强，不接管系统定位。
 * Android LocationManager 给出的坐标按 GPS/WGS84 处理，先走官方坐标转换，
 * 再逆地理编码；任一步失败都由调用方无感回退到系统 Geocoder。
 */
internal object AmapApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun reverseStreetFromWgs84(
        key: String,
        latitude: Double,
        longitude: Double,
        cityName: String?,
    ): AmapLookupResult {
        val candidate = key.trim()
        if (candidate.isEmpty()) return AmapLookupResult(false, info = "EMPTY_KEY")
        return try {
            val converted = request(
                path = listOf("v3", "assistant", "coordinate", "convert"),
                query = mapOf(
                    "key" to candidate,
                    "locations" to coordinate(longitude, latitude),
                    "coordsys" to "gps",
                ),
            )
            if (!converted.result.ok) return converted.result
            val location = converted.result.formattedAddress?.takeIf(::validCoordinatePair)
                ?: return AmapLookupResult(false, info = "坐标转换未返回有效结果")
            reverseGcj(candidate, location, cityName)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            AmapLookupResult(false, info = "高德街道服务暂不可用")
        }
    }

    /** 保存前真实验证 Web 服务 Key；北京测试点已是 GCJ-02，因此只消耗一次逆地理请求。 */
    suspend fun verifyKey(key: String): AmapLookupResult {
        val candidate = key.trim()
        if (candidate.isEmpty()) return AmapLookupResult(false, info = "请填写 Web 服务 API Key")
        return try {
            reverseGcj(candidate, "116.397428,39.909230", "北京")
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            AmapLookupResult(false, info = "无法连接高德 Web 服务")
        }
    }

    private suspend fun reverseGcj(key: String, location: String, cityName: String?): AmapLookupResult {
        val raw = request(
            path = listOf("v3", "geocode", "regeo"),
            query = mapOf(
                "key" to key,
                "location" to location,
                "extensions" to "base",
                "radius" to "1000",
                "roadlevel" to "0",
            ),
        )
        if (!raw.result.ok) return raw.result
        val root = raw.root ?: return AmapLookupResult(false, info = "高德响应无法解析")
        val regeocode = root["regeocode"]?.asObject()
            ?: return AmapLookupResult(false, info = "高德未返回逆地理结果")
        val component = regeocode["addressComponent"]?.asObject()
        val township = component?.string("township")
        val street = component?.get("streetNumber")?.asObject()?.string("street")
        val formatted = regeocode.string("formatted_address")
        return AmapLookupResult(
            ok = true,
            street = amapStreetLabel(township, street, cityName),
            formattedAddress = formatted,
            info = raw.result.info,
            infocode = raw.result.infocode,
        )
    }

    // 返回值只沿当前调用链传递；对象不记录 URL/Key，也不写日志。
    private suspend fun request(path: List<String>, query: Map<String, String>): AmapRawResult =
        withContext(Dispatchers.IO) {
            val builder = "https://restapi.amap.com/".toHttpUrl().newBuilder()
            path.forEach(builder::addPathSegment)
            query.forEach(builder::addQueryParameter)
            val response = client.newCall(Request.Builder().url(builder.build()).get().build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    return@withContext AmapRawResult(
                        AmapLookupResult(false, info = "高德服务返回 HTTP ${it.code}"),
                    )
                }
                val root = runCatching {
                    json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
                }.getOrNull()
                val status = root?.string("status")
                val info = root?.string("info")
                val infocode = root?.string("infocode")
                if (status != "1") {
                    return@withContext AmapRawResult(
                        AmapLookupResult(false, info = info ?: "高德鉴权未通过", infocode = infocode),
                        root,
                    )
                }
                val locations = root?.string("locations")
                AmapRawResult(
                    AmapLookupResult(true, formattedAddress = locations, info = info, infocode = infocode),
                    root,
                )
            }
        }

    private fun coordinate(longitude: Double, latitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", longitude, latitude)

    private fun validCoordinatePair(value: String): Boolean {
        val parts = value.split(',')
        return parts.size == 2 && parts.all { it.trim().toDoubleOrNull() != null }
    }
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonObject.string(name: String): String? =
    runCatching { get(name)?.jsonPrimitive?.contentOrNull?.trim() }
        .getOrNull()?.takeIf(String::isNotBlank)

internal fun amapStreetLabel(township: String?, street: String?, cityName: String?): String? =
    listOf(township, street)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .filterNot { it == cityName?.trim() }
        .distinct()
        .take(2)
        .joinToString("·")
        .ifBlank { null }
