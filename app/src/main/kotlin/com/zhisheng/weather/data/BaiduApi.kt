package com.zhisheng.weather.data

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class BaiduLookupResult(
    val ok: Boolean,
    val street: String? = null,
    val formattedAddress: String? = null,
    val district: String? = null,
    val city: String? = null,
    val province: String? = null,
    val adcode: String? = null,
    val info: String? = null,
)

internal data class BaiduPlaceResult(
    val name: String,
    val address: String?,
    val district: String?,
    val city: String?,
    val province: String?,
    val latitude: Double,
    val longitude: Double,
)

/** 百度 Web API：统一使用 AK，不引入地图/定位 SDK，也不接管系统 GPS。 */
internal object BaiduApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun reverseStreetFromWgs84(ak: String, latitude: Double, longitude: Double): BaiduLookupResult {
        val root = request(
            path = listOf("reverse_geocoding", "v3", ""),
            query = mapOf(
                "ak" to ak.trim(),
                "output" to "json",
                "coordtype" to "wgs84ll",
                "ret_coordtype" to "gcj02ll",
                "latest_admin" to "1",
                "extensions_poi" to "1",
                "entire_poi" to "1",
                "sort_strategy" to "distance",
                "location" to coordinate(latitude, longitude),
            ),
        ) ?: return BaiduLookupResult(false, info = "百度逆地理服务暂不可用")
        val result = root.objectValue("result") ?: return BaiduLookupResult(false, info = root.string("message"))
        val component = result.objectValue("addressComponent")
        val nearestPoi = (result["pois"] as? JsonArray)?.firstOrNull()?.asObject()?.string("name")
        val street = listOf(
            component?.string("town"),
            component?.string("street"),
            component?.string("street_number"),
            nearestPoi,
        ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct().take(2).joinToString("·").ifBlank { null }
        return BaiduLookupResult(
            ok = true,
            street = street,
            formattedAddress = result.string("formatted_address_poi") ?: result.string("formatted_address"),
            district = component?.string("district"),
            city = component?.string("city"),
            province = component?.string("province"),
            adcode = component?.string("adcode"),
        )
    }

    suspend fun searchPlaces(ak: String, keyword: String): List<BaiduPlaceResult> {
        if (ak.isBlank() || keyword.trim().length < 2) return emptyList()
        val root = request(
            path = listOf("place", "v2", "search"),
            query = mapOf(
                "ak" to ak.trim(),
                "query" to keyword.trim(),
                "region" to "全国",
                "city_limit" to "false",
                "scope" to "1",
                "page_size" to "10",
                "output" to "json",
            ),
        ) ?: throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
        return (root["results"] as? JsonArray ?: throw PlaceServiceException(PlaceFailure.UNAVAILABLE)).mapNotNull { item ->
            val poi = item.asObject() ?: return@mapNotNull null
            val location = poi.objectValue("location") ?: return@mapNotNull null
            val bdLat = location.double("lat") ?: return@mapNotNull null
            val bdLon = location.double("lng") ?: return@mapNotNull null
            val wgs = bd09ToWgs84(bdLat, bdLon)
            BaiduPlaceResult(
                name = poi.string("name") ?: return@mapNotNull null,
                address = poi.string("address"),
                district = poi.string("area"),
                city = poi.string("city"),
                province = poi.string("province"),
                latitude = wgs.latitude,
                longitude = wgs.longitude,
            )
        }
    }

    // 验证 AK：只验逆地理链路（0.1.5-beta6 起百度天气接口已下线，AK 仅服务定位/街道/搜索）
    suspend fun verifyKey(ak: String): BaiduLookupResult {
        if (ak.isBlank()) return BaiduLookupResult(false, info = "请填写百度地图服务端 AK")
        PlaceServiceGate.clear(ak.trim(), "baidu:reverse")
        PlaceServiceGate.clear(ak.trim(), "baidu:search")
        return try { reverseStreetFromWgs84(ak, 39.9042, 116.4074) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: PlaceServiceException) { BaiduLookupResult(false, info = when (error.failure) {
            PlaceFailure.AUTH -> "百度 AK 未通过授权校验"
            PlaceFailure.QUOTA -> "百度服务配额暂不可用"
            else -> "百度服务暂不可用"
        }) }
    }

    private suspend fun request(path: List<String>, query: Map<String, String>): JsonObject? =
        withContext(Dispatchers.IO) {
            val credential = query["ak"].orEmpty()
            if (credential.isBlank()) return@withContext null
            val capability = if (path.firstOrNull() == "place") "baidu:search" else "baidu:reverse"
            PlaceServiceGate.check(credential, capability)
            try {
                val builder = "https://api.map.baidu.com/".toHttpUrl().newBuilder()
                path.forEach { if (it.isNotEmpty()) builder.addPathSegment(it) }
                query.forEach(builder::addQueryParameter)
                val response = radarRequestBytes(client, Request.Builder().url(builder.build()).get().build(), 1_000_000)
                if (!response.successful) throw PlaceServiceException(
                    if (response.code == 429) PlaceFailure.QUOTA else PlaceFailure.UNAVAILABLE,
                    placeRetryAfterMillis(response.retryAfter))
                val root = json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).asObject()
                    ?: return@withContext null
                val status = root.string("status")?.toIntOrNull() ?: root.int("status")
                if (status != 0) throw PlaceServiceException(baiduFailure(status))
                PlaceServiceGate.clear(credential, capability)
                root
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: PlaceServiceException) {
                PlaceServiceGate.record(credential, capability, error.failure, error.retryAfterMillis)
                throw error
            } catch (_: Exception) {
                PlaceServiceGate.record(credential, capability, PlaceFailure.UNAVAILABLE)
                null
            }
        }

    private fun coordinate(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonObject.objectValue(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.string(name: String): String? =
    runCatching { get(name)?.jsonPrimitive?.contentOrNull?.trim() }.getOrNull()?.takeIf(String::isNotBlank)
private fun JsonObject.double(name: String): Double? = string(name)?.toDoubleOrNull()
private fun JsonObject.int(name: String): Int? = string(name)?.toIntOrNull()
