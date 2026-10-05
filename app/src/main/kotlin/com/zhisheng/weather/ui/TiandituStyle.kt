package com.zhisheng.weather.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer

internal const val OPEN_MAP_STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
internal const val OPEN_MAP_STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
internal const val OPEN_MAP_ATTRIBUTION = "© OpenStreetMap · OpenFreeMap"

internal fun weatherMapBaseStyle(palette: ZhishengPalette): Style.Builder =
    Style.Builder().fromUri(if (palette.isLight) OPEN_MAP_STYLE_LIGHT else OPEN_MAP_STYLE_DARK)

/** Put radar / typhoon overlays under place and road names. */
internal fun mapLabelAnchorId(style: Style): String? =
    style.layers.firstOrNull { it is SymbolLayer }?.id

@Composable
internal fun TiandituAttribution(modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    Text(
        if (isPhosphorVista) "地图来自 OpenStreetMap" else OPEN_MAP_ATTRIBUTION,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = palette.textTertiary,
        maxLines = 1,
    )
}
