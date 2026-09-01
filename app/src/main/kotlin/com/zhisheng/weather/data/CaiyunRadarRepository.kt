package com.zhisheng.weather.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.zhisheng.weather.model.RadarFeed
import com.zhisheng.weather.model.RadarFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

// 彩云雷达图接口失败原因：UI 据此给出不同的终端提示
enum class CaiyunRadarReason { NOT_CONFIGURED, NO_PERMISSION, SERVICE_UNAVAILABLE, EMPTY_FRAMES }

class CaiyunRadarException(val reason: CaiyunRadarReason, message: String) : Exception(message)

/**
 * 彩云雷达图（官方 v1 增值接口）：
 * - 实况：GET /v1/radar/images        → 过去约 20 帧拼图，5 分钟一帧
 * - 预报：GET /v1/radar/forecast_images → 最新实况 + 未来约 2 小时外推（25 帧）
 * level=2 拼图按 EPSG:3857 / Web Mercator 生成，四角边界为 WGS84，可直接贴 MapLibre。
 * 图片 URL 带时效 auth_key，只能短缓存；接口需企业套餐开通雷达权限。
 */
object CaiyunRadarRepository {
    private const val IMAGES_URL = "https://api.caiyunapp.com/v1/radar/images"
    private const val FORECAST_URL = "https://api.caiyunapp.com/v1/radar/forecast_images"
    private const val IMAGE_CACHE_TTL_MS = 5 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun loadFeed(lon: Double, lat: Double, token: String): RadarFeed = withContext(Dispatchers.IO) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            throw CaiyunRadarException(CaiyunRadarReason.NOT_CONFIGURED, "未配置彩云 Token")
        }
        val (past, forecast) = coroutineScope {
            val realtime = async { fetch(IMAGES_URL, lon, lat, trimmed) }
            val future = async { fetch(FORECAST_URL, lon, lat, trimmed) }
            realtime.await() to future.await()
        }
        // 预报接口首帧是「最新实况」：只保留晚于过去末帧的外推帧
        val pastMax = past.maxOfOrNull(RadarFrame::timeMillis)
        val futureOnly = forecast
            .filter { f -> pastMax == null || f.timeMillis > pastMax }
            .distinctBy(RadarFrame::timeMillis)
            .sortedBy(RadarFrame::timeMillis)
        if (past.isEmpty() && futureOnly.isEmpty()) {
            throw CaiyunRadarException(CaiyunRadarReason.EMPTY_FRAMES, "彩云未返回可用雷达帧")
        }
        RadarFeed(past, futureOnly)
    }

    private fun fetch(base: String, lon: Double, lat: Double, token: String): List<RadarFrame> {
        val url = String.format(
            Locale.US,
            "%s?lon=%.4f&lat=%.4f&level=2&world_map=true&token=%s",
            base, lon, lat, token,
        )
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 401 || response.code == 403 -> throw CaiyunRadarException(
                        CaiyunRadarReason.NO_PERMISSION,
                        "彩云 Token 未开通雷达图权限（企业套餐，联系 api@caiyunapp.com 开通）",
                    )
                    !response.isSuccessful -> throw CaiyunRadarException(
                        CaiyunRadarReason.SERVICE_UNAVAILABLE,
                        "彩云雷达服务返回 HTTP ${response.code}",
                    )
                    else -> parseCaiyunFrames(raw) ?: throw CaiyunRadarException(
                        CaiyunRadarReason.NO_PERMISSION,
                        "彩云雷达接口未授权（status=${caiyunStatusOf(raw)}）",
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: CaiyunRadarException) {
            throw e
        } catch (_: Exception) {
            throw CaiyunRadarException(CaiyunRadarReason.SERVICE_UNAVAILABLE, "无法连接彩云雷达服务")
        }
    }

    /**
     * 解析接口返回的帧列表；status 非 ok 或没有 images 字段时返回 null（未授权/空数据）。
     */
    internal fun parseCaiyunFrames(payload: String): List<RadarFrame>? {
        val text = payload.trim()
        if (text.isEmpty()) return null
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            if (root["status"]?.jsonPrimitive?.contentOrNull != "ok") return null
            val images = root["images"]?.jsonArray ?: return null
            images.mapNotNull { el ->
                val arr = el.jsonArray
                if (arr.size < 3) return@mapNotNull null
                val url = arr[0].jsonPrimitive.contentOrNull
                val timeSeconds = arr[1].jsonPrimitive.longOrNull
                val b = arr[2].jsonArray
                if (url.isNullOrBlank() || timeSeconds == null || b.size < 4) return@mapNotNull null
                val imageUrl = when {
                    url.startsWith("http://") || url.startsWith("https://") -> url
                    url.startsWith("/") -> "https://cdn.caiyunapp.com$url"
                    else -> return@mapNotNull null
                }
                RadarFrame(
                    timeMillis = timeSeconds * 1000L,
                    imageUrl = imageUrl,
                    southLat = b[0].jsonPrimitive.doubleOrNull,
                    westLng = b[1].jsonPrimitive.doubleOrNull,
                    northLat = b[2].jsonPrimitive.doubleOrNull,
                    eastLng = b[3].jsonPrimitive.doubleOrNull,
                )
            }
        }.getOrNull()
    }

    internal fun caiyunStatusOf(payload: String): String =
        runCatching {
            json.parseToJsonElement(payload.trim()).jsonObject["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }.getOrDefault("")

    /**
     * 下载并解码一帧图片。磁盘短缓存 5 分钟（auth_key 过期后重新拉取）；
     * 缓存键只用 URL path，避免签名参数变化导致缓存永远不命中。
     */
    suspend fun loadBitmap(context: Context, frame: RadarFrame): Bitmap? = withContext(Dispatchers.IO) {
        val url = frame.imageUrl ?: return@withContext null
        val file = imageFile(context, url)
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < IMAGE_CACHE_TTL_MS) {
            return@withContext BitmapFactory.decodeFile(file.absolutePath)
        }
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val bytes = response.body?.bytes()
                if (response.isSuccessful && bytes != null && bytes.isNotEmpty()) {
                    file.writeBytes(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun imageFile(context: Context, url: String): File {
        val path = url.substringAfter("//").substringAfter("/")
        val dir = File(context.cacheDir, "radar-caiyun").apply { mkdirs() }
        return File(dir, sha256(path) + ".png")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { String.format("%02x", it) }
}
