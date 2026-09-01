package com.zhisheng.weather.ui

import java.io.File
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
    fun radarUsesTiandituBasemapWithChineseAnnotation() {
        assertTrue(source.contains("weatherMapBaseStyle"))
        assertTrue(source.contains("TIANDITU_LABEL_LAYER"))
        assertTrue(style.contains("tianditu.gov.cn/DataServer"))
        assertTrue(style.contains("cia"))
        assertTrue(style.contains("cva"))
        assertTrue(style.contains("TDT_TOKEN"))
        assertTrue(style.contains("审图号"))
        assertTrue(File("build.gradle.kts").readText().contains("TDT_TOKEN"))
        assertFalse(source.contains("MAP_CITIES"))
        assertFalse(source.contains("projectMapLabels"))
        assertTrue(source.contains("clearRadarOverlays"))
    }
}
