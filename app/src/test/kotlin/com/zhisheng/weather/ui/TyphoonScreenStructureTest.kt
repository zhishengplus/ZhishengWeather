package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TyphoonScreenStructureTest {
    private val projectDir = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun typhoonUsesIndependentOfficialDataAndTiandituMap() {
        val screen = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/TyphoonScreen.kt").readText()
        val repository = File(projectDir, "src/main/kotlin/com/zhisheng/weather/data/TyphoonRepository.kt").readText()
        val style = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/TiandituStyle.kt").readText()

        assertTrue(repository.contains("typhoon.slt.zj.gov.cn/Api"))
        assertTrue(repository.contains("TyphoonList"))
        assertTrue(repository.contains("TyphoonInfo"))
        assertTrue(screen.contains("MapView"))
        assertTrue(screen.contains("weatherMapBaseStyle"))
        assertTrue(screen.contains("installTyphoonOverlayScaffold"))
        assertTrue(screen.contains("applyTyphoonTracks"))
        assertTrue(screen.contains("applyTyphoonSelection"))
        assertTrue(screen.contains("keepSelectedInView"))
        assertTrue(screen.contains("typhoonPolyline"))
        assertTrue(screen.contains("windCircleRing"))
        assertTrue(screen.contains("ty-observed-casing"))
        assertTrue(screen.contains("LINE_CAP_BUTT"))
        assertTrue(screen.contains("中央气象台预报"))
        assertTrue(screen.contains("白色预警"))
        assertTrue(repository.contains("isCmaForecastAgency"))
        assertTrue(style.contains("tiles.openfreemap.org"))
        assertEquals("黄色预警 · 资料有点旧", vistaTyphoonStatus("黄色预警", stale = true, active = true, switching = false))
        assertEquals("正在追踪", vistaTyphoonStatus(null, stale = false, active = true, switching = false))
        assertEquals("蝴蝶", vistaStormLabel("202525", "蝴蝶"))
        assertTrue(screen.contains("这些线是什么"))
        assertTrue(screen.contains("现在没有台风"))
        assertTrue(screen.contains("再试一次"))
        assertTrue(screen.contains("TYPHOON LINK STANDBY"))
        assertTrue(screen.contains("[ 重新连接 ]"))
        assertFalse(screen.contains("TYPHOON_MAP_LABELS"))
        assertFalse(screen.contains("setStyle(typhoonStyle"))
        assertFalse(screen.contains("detail = null"))
        assertFalse(screen.contains("steps ="))
        assertFalse(screen.contains("预报机构"))
        assertFalse(screen.contains("TY_FORECAST_SLOTS"))
        assertFalse(screen.contains("closestForecastPoint"))
    }

    @Test
    fun warningLevelKeepsWhiteSeparateFromActiveState() {
        assertEquals("white", normalizedTyphoonWarningLevel("white"))
        assertEquals("white", normalizedTyphoonWarningLevel("1"))
        assertEquals("blue", normalizedTyphoonWarningLevel("2"))
        assertEquals("red", normalizedTyphoonWarningLevel("5"))
        assertNull(normalizedTyphoonWarningLevel("0"))
    }
}
