package com.zhisheng.weather.ui

import com.zhisheng.weather.model.RadarCoverageState
import com.zhisheng.weather.model.RadarSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarMapStyleTest {

    private val source by lazy {
        File("src/main/kotlin/com/zhisheng/weather/ui/RadarScreen.kt").readText()
    }
    private val style by lazy {
        File("src/main/kotlin/com/zhisheng/weather/ui/TiandituStyle.kt").readText()
    }

    @Test
    fun radarUsesOpenStreetMapBasemap() {
        assertTrue(source.contains("weatherMapBaseStyle"))
        assertTrue(source.contains("mapLabelAnchorId"))
        assertTrue(style.contains("tiles.openfreemap.org"))
        assertTrue(style.contains("OPEN_MAP_STYLE_LIGHT"))
        assertFalse(source.contains("MAP_CITIES"))
        assertFalse(source.contains("projectMapLabels"))
        assertFalse(source.contains("china_boundaries.geojson"))
        assertTrue(source.contains("clearRadarOverlays"))
        assertTrue(source.contains("RADAR_MAX_ZOOM = 20.0"))
    }

    @Test
    fun vistaRadarCopySpeaksInEverydayChinese() {
        assertEquals("这附近看得到降雨", vistaRadarCoverageLine(RadarSource.RAINVIEWER, RadarCoverageState.AVAILABLE))
        assertEquals("这附近暂时没有雷达", vistaRadarCoverageLine(RadarSource.RAINVIEWER, RadarCoverageState.OUTSIDE))
        assertEquals("全国降雨图", vistaRadarCoverageLine(RadarSource.CAIYUN, RadarCoverageState.OUTSIDE))
        assertEquals("刚才到现在的实况", vistaRadarKindLine(false))
        assertEquals("未来一两小时的估计", vistaRadarKindLine(true))
        assertEquals("现在只能回看过去两小时", vistaRadarLimitLine(RadarSource.RAINVIEWER, false))
        assertEquals("正在打开地图", vistaRadarMapStatus(false, staleMetadata = false, tileError = false, radarTilesReady = false, mapReady = false))
        assertEquals("网不太稳，先看着上次的图", vistaRadarMapStatus(true, staleMetadata = true, tileError = false, radarTilesReady = true, mapReady = true))
        assertEquals("彩云还没接上，先用公开降雨图", vistaRadarFallbackNotice("未配置彩云 Token · 详情"))
        assertEquals("暂时连不上降雨图", vistaRadarErrorTitle("雷达数据连接失败"))
    }
}
