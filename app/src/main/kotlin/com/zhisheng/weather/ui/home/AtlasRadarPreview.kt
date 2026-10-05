package com.zhisheng.weather.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/** A single observed RainViewer tile, never synthetic echoes or a second paid Caiyun query. */
internal data class AtlasRadarTile(val bitmap: Bitmap?, val sample: RadarTileSample?, val time: Long?, val message: String)
private object AtlasRadarTiles {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, Pair<Long, AtlasRadarTile>>(4, .75f, true)
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun load(city: City): AtlasRadarTile = mutex.withLock {
        val key = "${city.latitude},${city.longitude}"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.first in 0..5 * 60_000L }?.let { return@withLock it.second }
        val result = withContext(Dispatchers.IO) {
            try {
                val timeline = RadarRepository.loadTimeline(city)
                currentCoroutineContext().ensureActive()
                if (timeline.coverage != RadarCoverageState.AVAILABLE) return@withContext AtlasRadarTile(null, null, null,
                    if (timeline.coverage == RadarCoverageState.OUTSIDE) "本地暂不在覆盖区" else "覆盖状态待确认")
                val frame = timeline.latest ?: return@withContext AtlasRadarTile(null, null, null, "回波暂不可用")
                val sample = radarTileSample(city.latitude, city.longitude, 6)
                val url = timeline.tileTemplate(frame).replace("{z}", "6")
                    .replace("{x}", sample.tileX.toString()).replace("{y}", sample.tileY.toString())
                check(isAtlasRadarTileUrl(url))
                val bitmap = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val body = checkNotNull(response.body)
                    // Bound both decoding and transfer; thumbnails must never hold a giant response.
                    val stream = body.source()
                    stream.request(1_048_577)
                    val bytes = stream.buffer.readByteArray()
                    check(bytes.size <= 1_048_576)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    check(bounds.outWidth == 256 && bounds.outHeight == 256)
                    checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                }
                currentCoroutineContext().ensureActive()
                AtlasRadarTile(bitmap, sample, frame.timeMillis, if (timeline.staleMetadata) "缓存回波" else "最新可用帧")
            } catch (ce: CancellationException) { throw ce }
            catch (_: Exception) { AtlasRadarTile(null, null, null, "预览暂不可用") }
        }
        val previous = cache[key]?.second
        val retained = if (result.bitmap == null && previous?.bitmap != null) previous.copy(message = "上次更新") else result
        cache[key] = System.currentTimeMillis() to retained
        while (cache.size > 4) cache.remove(cache.keys.first())
        retained
    }
}

internal fun isAtlasRadarTileUrl(url: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps && parsed.port == 443 && parsed.username.isEmpty() && parsed.password.isEmpty() &&
        (parsed.host == "tilecache.rainviewer.com" || parsed.host.endsWith(".rainviewer.com"))
}

internal data class AtlasRadarPreviewState(val source: RadarSource?, val tile: AtlasRadarTile?) {
    val available: Boolean get() = tile?.bitmap != null || source == RadarSource.CAIYUN
}

@Composable
internal fun rememberAtlasRadarPreview(city: City?, refreshKey: Long?): AtlasRadarPreviewState {
    var source by remember { mutableStateOf<RadarSource?>(null) }
    LaunchedEffect(Unit) { SettingsRepository.radarSource.collect { source = it } }
    var tile by remember(city?.latitude, city?.longitude, source) { mutableStateOf<AtlasRadarTile?>(null) }
    LaunchedEffect(city?.latitude, city?.longitude, source, refreshKey) {
        if (source == RadarSource.RAINVIEWER && city != null) tile = AtlasRadarTiles.load(city)
    }
    return AtlasRadarPreviewState(source, tile)
}

@Composable
internal fun AtlasRadarPreview(city: City?, refreshKey: Long? = null, zoneId: String = "UTC", preview: AtlasRadarPreviewState? = null) {
    val state = preview ?: rememberAtlasRadarPreview(city, refreshKey)
    val source = state.source
    val tile = state.tile
    if (isPhosphorVista && !state.available) return
    if (isPhosphorVista && source == RadarSource.CAIYUN) {
        AtlasCaption("查看降雨变化")
        return
    }
    val palette = LocalZhishengPalette.current
    val bitmap = tile?.bitmap?.asImageBitmap()
    val sample = tile?.sample
    val message = when {
        city == null -> "尚未选择地点"
        source == RadarSource.CAIYUN -> "打开后加载彩云回波"
        source == null -> "正在读取雷达设置"
        else -> tile?.message ?: "正在加载回波"
    }
    Canvas(Modifier.fillMaxWidth().height(92.dp).background(palette.surface).semantics {
        contentDescription = "雷达缩略图：$message。十字为地点，无底图，打开可查看完整地图。"
    }) {
        // Render the entire tile without cropping; the location marker uses its actual tile pixel.
        val side = size.minDimension
        val left = (size.width - side) / 2
        if (bitmap != null) drawImage(bitmap, dstOffset = IntOffset(left.toInt(), 0), dstSize = IntSize(side.toInt(), side.toInt()))
        for (i in 1..3) {
            val x = left + side * i / 4
            val y = side * i / 4
            drawLine(palette.cardBorder, Offset(x, 0f), Offset(x, side), 1.dp.toPx())
            drawLine(palette.cardBorder, Offset(left, y), Offset(left + side, y), 1.dp.toPx())
        }
        if (bitmap != null && sample != null) {
            val x = left + side * sample.pixelX / 256; val y = side * sample.pixelY / 256
            drawCircle(palette.card, 5.dp.toPx(), Offset(x, y))
            drawLine(palette.text, Offset(x - 4.dp.toPx(), y), Offset(x + 4.dp.toPx(), y), 2.dp.toPx())
            drawLine(palette.text, Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 2.dp.toPx())
        }
    }
    AtlasCaption(message)
    tile?.time?.let { AtlasCaption("RainViewer · ${sceneStamp(it, zoneId)}") }
    if (bitmap == null) AtlasCaption("打开地图 →", ZhishengCyan)
}
