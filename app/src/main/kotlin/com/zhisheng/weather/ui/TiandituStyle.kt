package com.zhisheng.weather.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterBrightnessMax
import org.maplibre.android.style.layers.PropertyFactory.rasterBrightnessMin
import org.maplibre.android.style.layers.PropertyFactory.rasterContrast
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.rasterSaturation
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

internal const val TIANDITU_BASE_LAYER = "zhisheng-tdt-base"
internal const val TIANDITU_LABEL_LAYER = "zhisheng-tdt-label"
internal const val TIANDITU_ATTRIBUTION = "审图号 GS(2024)0650号 · 天地图"

internal fun tiandituToken(): String = BuildConfig.TDT_TOKEN.trim()

internal fun hasTiandituToken(): Boolean = tiandituToken().isNotEmpty()

internal data class WeatherMapFallbackGeo(
    val china: String,
    val coast: String,
    val worldBorders: String? = null,
)

internal fun weatherMapBaseStyle(
    palette: ZhishengPalette,
    fallback: WeatherMapFallbackGeo? = null,
): Style.Builder {
    val token = tiandituToken()
    return if (token.isNotEmpty()) tiandituBaseStyle(palette, token)
    else localVectorFallbackStyle(palette, fallback)
}

private fun tiandituBaseStyle(palette: ZhishengPalette, token: String): Style.Builder {
    val imagery = !palette.isLight
    val baseLayer = if (imagery) "img" else "vec"
    val labelLayer = if (imagery) "cia" else "cva"
    val builder = Style.Builder()
        .withLayer(
            BackgroundLayer("zhisheng-map-background").withProperties(backgroundColor(palette.bg.toArgb())),
        )
        .withSource(tiandituRasterSource("zhisheng-tdt-base-source", baseLayer, token))
        .withLayer(
            if (imagery) {
                RasterLayer(TIANDITU_BASE_LAYER, "zhisheng-tdt-base-source").withProperties(
                    rasterOpacity(0.94f),
                    rasterSaturation(-0.38f),
                    rasterContrast(0.28f),
                    rasterBrightnessMin(0.0f),
                    rasterBrightnessMax(0.68f),
                )
            } else {
                RasterLayer(TIANDITU_BASE_LAYER, "zhisheng-tdt-base-source").withProperties(
                    rasterOpacity(1f),
                    rasterSaturation(-0.42f),
                    rasterContrast(0.08f),
                    rasterBrightnessMin(0.06f),
                    rasterBrightnessMax(1f),
                )
            },
        )
        .withSource(tiandituRasterSource("zhisheng-tdt-label-source", labelLayer, token))
        .withLayer(
            RasterLayer(TIANDITU_LABEL_LAYER, "zhisheng-tdt-label-source").withProperties(
                rasterOpacity(if (imagery) 0.96f else 0.92f),
            ),
        )
    return builder
}

private fun tiandituRasterSource(id: String, layer: String, token: String): RasterSource {
    val urls = Array(8) { index ->
        "https://t$index.tianditu.gov.cn/DataServer?T=${layer}_w&x={x}&y={y}&l={z}&tk=$token"
    }
    return RasterSource(
        id,
        TileSet("2.1.0", *urls).apply {
            minZoom = 1f
            maxZoom = 18f
        },
        256,
    )
}

private fun localVectorFallbackStyle(
    palette: ZhishengPalette,
    fallback: WeatherMapFallbackGeo?,
): Style.Builder {
    val builder = Style.Builder().withLayer(
        BackgroundLayer("zhisheng-map-background").withProperties(backgroundColor(palette.bg.toArgb())),
    )
    val geo = fallback ?: return builder
    builder.withSource(GeoJsonSource("zhisheng-geo-coast", geo.coast))
        .withLayer(
            LineLayer("zhisheng-geo-coast-layer", "zhisheng-geo-coast").withProperties(
                lineColor(palette.textTertiary.toArgb()),
                lineWidth(0.8f),
                lineOpacity(0.45f),
            ),
        )
    geo.worldBorders?.let { borders ->
        builder.withSource(GeoJsonSource("zhisheng-geo-world-borders", borders))
            .withLayer(
                LineLayer("zhisheng-geo-world-borders-layer", "zhisheng-geo-world-borders").withProperties(
                    lineColor(palette.textTertiary.toArgb()),
                    lineWidth(0.65f),
                    lineOpacity(if (palette.isLight) 0.42f else 0.32f),
                ),
            )
    }
    builder.withSource(GeoJsonSource("zhisheng-geo-china", geo.china))
        .withLayer(
            FillLayer("zhisheng-geo-china-fill", "zhisheng-geo-china").withProperties(
                fillColor(palette.surface.toArgb()),
                fillOpacity(if (palette.isLight) 0.62f else 0.48f),
            ),
        )
        .withLayer(
            LineLayer("zhisheng-geo-china-layer", "zhisheng-geo-china").withProperties(
                lineColor(palette.cyan.toArgb()),
                lineWidth(1.1f),
                lineOpacity(0.6f),
            ),
        )
    return builder
}

@Composable
internal fun TiandituAttribution(modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    Text(
        if (hasTiandituToken()) TIANDITU_ATTRIBUTION else "本机矢量底图",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = palette.textTertiary,
        maxLines = 1,
    )
}
