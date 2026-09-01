package com.zhisheng.weather.model

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

/** 雷达数据源：RainViewer（全球、免费）与彩云拼图（全国、企业套餐）。 */
enum class RadarSource(val key: String, val cn: String) {
    RAINVIEWER("rainviewer", "RainViewer"),
    CAIYUN("caiyun", "彩云拼图");

    companion object {
        fun fromKey(raw: String?): RadarSource =
            entries.firstOrNull { it.key == raw } ?: RAINVIEWER
    }
}

data class RadarFrame(
    val timeMillis: Long,
    // RainViewer 瓦片帧：相对路径，拼进 tileTemplate
    val path: String = "",
    // 彩云图片帧：单张 PNG 的 URL（带时效签名）与 WGS84 四角边界 [南纬, 西经, 北纬, 东经]
    val imageUrl: String? = null,
    val southLat: Double? = null,
    val westLng: Double? = null,
    val northLat: Double? = null,
    val eastLng: Double? = null,
) {
    val isImageFrame: Boolean get() = !imageUrl.isNullOrBlank()
    val frameKey: String get() = path + "|" + (imageUrl.orEmpty())
}

/**
 * 一页雷达的数据集：过去段 + 未来段。
 * RainViewer 与彩云共用同一播放逻辑，帧实现不同（瓦片 vs 贴图）。
 */
data class RadarFeed(
    val past: List<RadarFrame>,
    val future: List<RadarFrame>,
    // RainViewer 瓦片主机；彩云图片帧不需要（空串）
    val host: String = "",
) {
    val playbackFrames: List<RadarFrame>
        get() = orderRadarFrames(past + future) { it.timeMillis }

    /** 播放序列中「过去 → 未来」的分界下标（最后一帧过去实测的位置）。 */
    val nowBoundaryIndex: Int
        get() = (past.size - 1).coerceIn(-1, playbackFrames.lastIndex)
}

enum class RadarCoverageState {
    AVAILABLE,
    OUTSIDE,
    UNKNOWN,
}

data class RadarTimeline(
    val host: String,
    val frames: List<RadarFrame>,
    val staleMetadata: Boolean,
    val coverage: RadarCoverageState = RadarCoverageState.UNKNOWN,
    // 保留统一时间线形状；RainViewer 当前公开接口只提供过去两小时，通常为空。
    val futureFrames: List<RadarFrame> = emptyList(),
) {
    val expectedFrames: Int get() = frames.size
    val loadedFrames: Int get() = frames.size
    val latest: RadarFrame? get() = frames.maxByOrNull(RadarFrame::timeMillis)

    /** 播放序列：过去实测回波 + 未来外推，按时间排序去重。 */
    val playbackFrames: List<RadarFrame>
        get() = orderRadarFrames(frames + futureFrames) { it.timeMillis }

    /** 播放序列中「过去 → 未来」的分界下标（即最后一帧实测回波的序列位置）。 */
    val nowBoundaryIndex: Int
        get() {
            val pastCount = frames.size
            return (pastCount - 1).coerceIn(-1, playbackFrames.lastIndex)
        }

    fun tileTemplate(frame: RadarFrame, tileSize: Int = 256): String =
        host.trimEnd('/') + frame.path + "/$tileSize/{z}/{x}/{y}/2/1_1.png"
}

fun classifyRadarCoveragePixels(pixels: IntArray): RadarCoverageState {
    if (pixels.isEmpty()) return RadarCoverageState.UNKNOWN
    var transparent = 0
    var opaqueBlack = 0
    pixels.forEach { pixel ->
        val alpha = pixel ushr 24 and 0xff
        val red = pixel ushr 16 and 0xff
        val green = pixel ushr 8 and 0xff
        val blue = pixel and 0xff
        if (alpha <= 48) transparent++
        if (alpha >= 192 && red <= 32 && green <= 32 && blue <= 32) opaqueBlack++
    }
    val threshold = ((pixels.size * 3 + 4) / 5).coerceAtLeast(1)
    return when {
        transparent >= threshold -> RadarCoverageState.AVAILABLE
        opaqueBlack >= threshold -> RadarCoverageState.OUTSIDE
        else -> RadarCoverageState.UNKNOWN
    }
}

data class RadarTileSample(
    val tileX: Int,
    val tileY: Int,
    val pixelX: Int,
    val pixelY: Int,
)

/**
 * 经纬度转 Web Mercator XYZ 瓦片及瓦片内像素。RainViewer coverage 接口接收的是
 * {z}/{x}/{y}，不是经纬度；采样点也必须落在城市真实像素而不是固定取瓦片中心。
 */
fun radarTileSample(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    tileSize: Int = 256,
): RadarTileSample {
    val safeZoom = zoom.coerceIn(0, 22)
    val safeSize = tileSize.coerceAtLeast(1)
    val n = (1 shl safeZoom).toDouble()
    val lon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    val lat = latitude.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(lat)
    val worldX = (lon + 180.0) / 360.0 * n
    val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
    val tileX = floor(worldX).toInt().coerceIn(0, n.toInt() - 1)
    val tileY = floor(worldY).toInt().coerceIn(0, n.toInt() - 1)
    return RadarTileSample(
        tileX = tileX,
        tileY = tileY,
        pixelX = ((worldX - tileX) * safeSize).roundToInt().coerceIn(0, safeSize - 1),
        pixelY = ((worldY - tileY) * safeSize).roundToInt().coerceIn(0, safeSize - 1),
    )
}

fun radarViewportRadiusKm(latitude: Double, zoom: Int = 7): Int {
    val worldWidthKm = 40_075.016686 * cos(Math.toRadians(latitude))
    return (worldWidthKm / (1 shl zoom) / 2.0).toInt().coerceAtLeast(1)
}

fun <T> orderRadarFrames(items: List<T>, timeOf: (T) -> Long): List<T> =
    items.distinctBy(timeOf).sortedBy(timeOf)
