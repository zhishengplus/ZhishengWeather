package com.zhisheng.weather.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.zhisheng.weather.data.BoundaryRepository
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min

/** 位图缓存键：城市 + 主题 + 密度 + 尺寸。 */
private data class BitmapKey(val adcode: Int, val isLight: Boolean, val dpi: Int, val w: Int, val h: Int)

private val bitmapCache = object : LinkedHashMap<BitmapKey, ImageBitmap>(4, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BitmapKey, ImageBitmap>): Boolean = size > 6
}

/**
 * 城市行政区划轮廓地图。
 *
 * 图层（自底向上）：
 * 1. 淡色填充（均匀低透明度）
 * 2. 城市外轮廓线
 * 3. 区县内部边界线
 * 4. 政府中心定位标
 *
 * 城市外轮廓由区县拓扑 merge 生成，与区县边界严格共边；
 * 几何按城市惰性解码，位图在后台线程渲染并按城市+主题+密度+尺寸缓存。
 */
@Composable
fun CityOutlineMap(
    cityName: String,
    cityAffiliation: String?,
    cityLat: Double?,
    cityLon: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalZhishengPalette.current
    val density = LocalDensity.current

    var geometry by remember { mutableStateOf<BoundaryRepository.CityGeometry?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(cityName, cityAffiliation, cityLat, cityLon) {
        geometry = null // 先清掉上一个城市的几何，避免切换期间残留旧图
        BoundaryRepository.ensureLoaded(context)
        val entry = BoundaryRepository.resolve(cityName, cityAffiliation, cityLat, cityLon)
            ?: return@LaunchedEffect
        geometry = BoundaryRepository.geometry(entry)
    }

    val dpi = (density.density * 160).toInt()
    var bitmap by remember(geometry, palette.isLight, dpi, size) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(geometry, palette.isLight, dpi, size) {
        val g = geometry ?: return@LaunchedEffect
        if (size.width < 16 || size.height < 16) return@LaunchedEffect
        val key = BitmapKey(g.adcode, palette.isLight, dpi, size.width, size.height)
        bitmap = bitmapCache[key] ?: withContext(Dispatchers.Default) {
            renderCityMap(g, palette.isLight, palette.cyan, palette.red, size.width, size.height)
                .asImageBitmap()
                .also { bitmapCache[key] = it }
        }
    }

    Box(modifier.onSizeChanged { size = it }) {
        val bmp = bitmap
        if (bmp != null) {
            Canvas(Modifier) { drawImage(bmp) }
        }
    }
}

private fun renderCityMap(
    g: BoundaryRepository.CityGeometry,
    isLight: Boolean,
    cyan: Color,
    red: Color,
    wPx: Int,
    hPx: Int,
): Bitmap {
    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    for (ring in g.outerRings) {
        var i = 0
        while (i < ring.size) {
            val lon = ring[i].toDouble(); val lat = ring[i + 1].toDouble()
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            i += 2
        }
    }
    if (minLon > maxLon) return bmp

    // 轮廓以约 93% 宽度充满卡片，四周仅留极窄边距，与生成脚本的容差定标保持一致
    val span = maxOf(maxLon - minLon, maxLat - minLat)
    val expand = span * 0.04
    minLon -= expand; maxLon += expand; minLat -= expand; maxLat += expand

    val lonMid = (minLon + maxLon) / 2.0
    val latMid = (minLat + maxLat) / 2.0
    val k = cos(Math.toRadians(latMid)) // 纬度校正：经度方向按 1° 实际长度压缩
    val lonSpan = ((maxLon - minLon) * k).coerceAtLeast(1e-6)
    val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
    val pad = 10f
    val scale = min((wPx - pad * 2) / lonSpan, (hPx - pad * 2) / latSpan).toFloat()

    val x: (Float) -> Float = { (wPx / 2f + ((it - lonMid) * k * scale).toFloat()) }
    val y: (Float) -> Float = { (hPx / 2f - ((it - latMid) * scale).toFloat()) }
    val ds = (wPx / 300f).coerceIn(0.8f, 2.5f) // 线宽/标记随卡片尺寸缩放

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    val cityPath = buildRingPath(g.outerRings, x, y)

    // ── 第 1 层：淡色填充（均匀低透明度，与轮廓同色系） ──
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = argb(cyan, if (isLight) 0.04f else 0.06f)
    canvas.drawPath(cityPath, paint)

    // ── 第 2 层：城市外轮廓线 ──
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.0f * ds
    paint.color = argb(cyan, if (isLight) 0.36f else 0.44f)
    canvas.drawPath(cityPath, paint)

    // ── 第 3 层：区县内部线 ──
    if (g.innerLines.isNotEmpty()) {
        paint.strokeWidth = 0.6f * ds
        paint.color = argb(cyan, if (isLight) 0.24f else 0.30f)
        for (line in g.innerLines) canvas.drawPath(buildLinePath(line, x, y), paint)
    }

    // ── 第 4 层：政府中心定位标 ──
    val cx = x(g.centerLon.toFloat())
    val cy = y(g.centerLat.toFloat())
    val pinR = 4.5f * ds
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = argb(red, 1f)
    canvas.drawCircle(cx, cy - pinR * 0.6f, pinR * 1.4f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy - pinR * 0.6f, pinR * 0.55f, paint)
    val tail = android.graphics.Path().apply {
        moveTo(cx - pinR * 0.6f, cy - pinR * 0.1f)
        lineTo(cx, cy + pinR * 1.6f)
        lineTo(cx + pinR * 0.6f, cy - pinR * 0.1f)
        close()
    }
    paint.color = argb(red, 1f)
    canvas.drawPath(tail, paint)

    return bmp
}

private fun argb(c: Color, alpha: Float): Int = android.graphics.Color.argb(
    (alpha.coerceIn(0f, 1f) * 255).toInt(),
    (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
)

private fun buildRingPath(
    rings: List<FloatArray>,
    x: (Float) -> Float, y: (Float) -> Float,
): android.graphics.Path {
    val path = android.graphics.Path()
    path.fillType = android.graphics.Path.FillType.EVEN_ODD
    for (ring in rings) addSegment(ring, x, y, path, close = true)
    return path
}

private fun buildLinePath(
    line: FloatArray,
    x: (Float) -> Float, y: (Float) -> Float,
): android.graphics.Path {
    val path = android.graphics.Path()
    addSegment(line, x, y, path, close = false)
    return path
}

private fun addSegment(
    pts: FloatArray,
    x: (Float) -> Float, y: (Float) -> Float,
    path: android.graphics.Path,
    close: Boolean,
) {
    if (pts.size < 4) return
    path.moveTo(x(pts[0]), y(pts[1]))
    for (i in 2 until pts.size step 2) path.lineTo(x(pts[i]), y(pts[i + 1]))
    if (close) path.close()
}
