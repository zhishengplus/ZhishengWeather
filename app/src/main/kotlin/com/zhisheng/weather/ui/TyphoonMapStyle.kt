package com.zhisheng.weather.ui

import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.ui.theme.ZhishengPalette
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.maplibre.android.maps.Style
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal const val TYPHOON_TDT_LABEL_LAYER = "tdt-labels"
// Verified against the national portal's mapdrawingApprovalNumber on 2026-10-26.
internal const val TYPHOON_MAP_APPROVAL = "GS（2026）4921号"
internal const val TYPHOON_MAP_ATTRIBUTION = "© 天地图 · $TYPHOON_MAP_APPROVAL"

internal fun validTyphoonMapToken(token: String): Boolean = token.trim().matches(Regex("[A-Za-z0-9_-]{16,128}"))

internal fun typhoonMapBaseStyle(palette: ZhishengPalette, satellite: Boolean = false): Style.Builder =
    Style.Builder().fromJson(typhoonMapStyleJson(BuildConfig.TDT_TOKEN, palette.isLight, satellite))

/** Both themes retain the official geometry, labels and colors. No foreign-map fallback. */
internal fun typhoonMapStyleJson(token: String, isLight: Boolean, satellite: Boolean = false): String = buildJsonObject {
    put("version", 8)
    put("name", "枳生台风 · 天地图")
    val configured = validTyphoonMapToken(token)
    val base = if (satellite) "img" else "vec"
    val labels = if (satellite) "cia" else "cva"
    put("sources", buildJsonObject {
        if (configured) for (layer in listOf(base, labels)) {
            put("tdt-$layer", buildJsonObject {
                put("type", "raster")
                put("tileSize", 256)
                put("minzoom", 1)
                put("maxzoom", 18)
                put("attribution", TYPHOON_MAP_ATTRIBUTION)
                put("tiles", buildJsonArray {
                    add(JsonPrimitive(typhoonTileUrl(layer, token)))
                })
            })
        }
    })
    put("layers", buildJsonArray {
        add(buildJsonObject {
            put("id", "background"); put("type", "background")
            put("paint", buildJsonObject { put("background-color", if (isLight) "#EDF3F7" else "#111C27") })
        })
        if (configured) for ((layer, id) in listOf(base to "tdt-base", labels to TYPHOON_TDT_LABEL_LAYER)) {
            add(buildJsonObject {
                put("id", id); put("type", "raster"); put("source", "tdt-$layer")
                put("paint", buildJsonObject { put("raster-fade-duration", 0) })
            })
        }
    })
}.toString()

private fun typhoonTileUrl(layer: String, token: String): String =
    "https://t0.tianditu.gov.cn/${layer}_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=$layer&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${token.trim()}"

private val typhoonMapProbeClient by lazy {
    OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
}

/** Two fixed public-region tiles, once per entry/style change; never probes the user's location. */
internal suspend fun verifyTyphoonMapService(satellite: Boolean): Boolean = coroutineScope {
    (if (satellite) listOf("img", "cia") else listOf("vec", "cva")).map { layer ->
        async {
            val url = typhoonTileUrl(layer, BuildConfig.TDT_TOKEN)
                .replace("{z}", "6").replace("{x}", "53").replace("{y}", "27")
            suspendCancellableCoroutine { continuation ->
                val call = typhoonMapProbeClient.newCall(Request.Builder().url(url).build())
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val valid = response.use {
                            try {
                                it.isSuccessful && typhoonTileHeaderValid(it.header("Content-Type"), it.peekBody(16).bytes())
                            } catch (_: IOException) { false }
                        }
                        if (continuation.isActive) continuation.resume(valid)
                    }
                })
            }
        }
    }.awaitAll().all { it }
}

internal fun typhoonTileHeaderValid(contentType: String?, bytes: ByteArray): Boolean {
    if (contentType?.substringBefore(';')?.trim()?.startsWith("image/", ignoreCase = true) != true) return false
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    return (bytes.size >= png.size && bytes.take(png.size) == png.toList()) ||
        (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte())
}
