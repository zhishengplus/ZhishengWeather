package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.radarTileSample
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.math.*

/** World Mercator coordinates in tile pixels at the same zoom as the existing radar thumbnail. */
internal data class PreviewWorldPoint(val x: Double, val y: Double)
internal fun radarPreviewWorldPoint(latitude: Double, longitude: Double): PreviewWorldPoint {
    val lat = latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180
    val lon = ((longitude + 180) % 360 + 360) % 360 - 180
    return PreviewWorldPoint((lon + 180) / 360 * 16384,
        (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * 16384)
}

internal fun radarPreviewRelativeX(x: Double, centerX: Double): Double =
    ((x - centerX + 8192) % 16384 + 16384) % 16384 - 8192

/** Clip before handing a path to the renderer; global outlines must never create huge offscreen strokes. */
internal fun clipRadarPreviewSegment(a: PreviewWorldPoint, b: PreviewWorldPoint): Pair<PreviewWorldPoint,PreviewWorldPoint>? {
    if (abs(b.x-a.x)>8192) return null
    val dx=b.x-a.x;val dy=b.y-a.y
    var lo=0.0;var hi=1.0
    val p=doubleArrayOf(-dx,dx,-dy,dy);val q=doubleArrayOf(a.x+128,128-a.x,a.y+256,256-a.y)
    for(i in p.indices) {
        if(p[i]==0.0) { if(q[i]<0) return null } else {
            val t=q[i]/p[i]
            if(p[i]<0) lo=max(lo,t) else hi=min(hi,t)
            if(lo>hi) return null
        }
    }
    return PreviewWorldPoint(a.x+lo*dx,a.y+lo*dy) to PreviewWorldPoint(a.x+hi*dx,a.y+hi*dy)
}

internal object RadarPreviewGeography {
    private var cached: List<List<PreviewWorldPoint>>? = null
    suspend fun load(context: android.content.Context): List<List<PreviewWorldPoint>> = withContext(Dispatchers.IO) {
        synchronized(this@RadarPreviewGeography) {
            cached ?: buildList {
                for (asset in listOf("world_coastline.geojson", "world_borders.geojson", "china_boundaries.geojson")) {
                    val json = context.assets.open("geo/$asset").bufferedReader().use { Json.parseToJsonElement(it.readText()) }
                    fun collect(value: JsonElement) {
                        val array = value as? JsonArray ?: return
                        if (array.firstOrNull() is JsonArray && (array.first() as JsonArray).firstOrNull() is JsonPrimitive) {
                            val line = array.mapNotNull { item ->
                                val point = item as? JsonArray ?: return@mapNotNull null
                                val lon = (point.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
                                val lat = (point.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
                                if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) radarPreviewWorldPoint(lat, lon) else null
                            }
                            if (line.size > 1) add(line)
                        } else array.forEach(::collect)
                    }
                    json.jsonObject["features"]?.jsonArray?.forEach { collect(it.jsonObject["geometry"]!!.jsonObject["coordinates"]!!) }
                }
            }.also { cached = it }
        }
    }
}

@Composable
internal fun HomeRadarMap(city: City, tile: AtlasRadarTile, geography: List<List<PreviewWorldPoint>>) {
    val palette = LocalZhishengPalette.current
    val sample = tile.sample ?: radarTileSample(city.latitude, city.longitude, 6)
    val center = radarPreviewWorldPoint(city.latitude, city.longitude)
    val paths = remember(city.latitude, city.longitude, geography) {
        geography.mapNotNull { line ->
            val points = line.map { PreviewWorldPoint(radarPreviewRelativeX(it.x, center.x),it.y-center.y) }
            val path=Path();var segments=0
            points.zipWithNext().forEach { (a,b) ->
                clipRadarPreviewSegment(a,b)?.let { (first,last) ->
                    path.moveTo(first.x.toFloat(),first.y.toFloat());path.lineTo(last.x.toFloat(),last.y.toFloat());segments++
                }
            }
            path.takeIf { segments>0 }
        }
    }
    val image = remember(tile.bitmap) { tile.bitmap?.asImageBitmap() }
    Box(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(18.dp))
        .background(palette.cyan.copy(alpha = if (palette.isLight) .035f else .055f))) {
        Canvas(Modifier.matchParentSize().semantics {
            contentDescription = "当前地点附近的历史回波单帧预览；未加载区域不表示无雨，点击查看完整地图"
        }) {
            val scale = size.width / 256f
            val origin = Offset(size.width / 2, size.height / 2)
            clipRect {
                if (image != null) drawImage(image,
                    dstOffset = IntOffset((origin.x-sample.pixelX*scale).roundToInt(), (origin.y-sample.pixelY*scale).roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(),size.width.roundToInt()))
                withTransform({ translate(origin.x,origin.y);scale(scale,scale,Offset.Zero) }) {
                    paths.forEach { drawPath(it, palette.textSecondary.copy(alpha = .24f), style = Stroke(1.dp.toPx()/scale)) }
                }
                drawCircle(palette.card.copy(alpha = .94f), 8.dp.toPx(), origin)
                drawCircle(palette.cyan, 4.dp.toPx(), origin)
                drawCircle(palette.cyan.copy(alpha = .24f), 12.dp.toPx(), origin, style = Stroke(1.dp.toPx()))
            }
        }
        Text("北 ↑", Modifier.align(Alignment.TopEnd).padding(12.dp),
            color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        Text("当前地点", Modifier.align(Alignment.Center).offset(y = 25.dp)
            .clip(RoundedCornerShape(8.dp)).background(palette.card.copy(alpha = .88f)).padding(horizontal = 8.dp,vertical = 3.dp),
            color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
