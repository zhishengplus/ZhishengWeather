package com.zhisheng.weather.model

import kotlin.math.cos

data class RadarFrame(
    val timeMillis: Long,
    val path: String,
)

data class RadarTimeline(
    val host: String,
    val frames: List<RadarFrame>,
    val staleMetadata: Boolean,
) {
    val expectedFrames: Int get() = frames.size
    val loadedFrames: Int get() = frames.size
    val latest: RadarFrame? get() = frames.maxByOrNull(RadarFrame::timeMillis)

    fun tileTemplate(frame: RadarFrame, tileSize: Int = 256): String =
        host.trimEnd('/') + frame.path + "/$tileSize/{z}/{x}/{y}/2/1_1.png"
}

fun radarViewportRadiusKm(latitude: Double, zoom: Int = 7): Int {
    val worldWidthKm = 40_075.016686 * cos(Math.toRadians(latitude))
    return (worldWidthKm / (1 shl zoom) / 2.0).toInt().coerceAtLeast(1)
}

fun <T> orderRadarFrames(items: List<T>, timeOf: (T) -> Long): List<T> =
    items.distinctBy(timeOf).sortedBy(timeOf)
