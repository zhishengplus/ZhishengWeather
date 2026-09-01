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
        assertTrue(style.contains("tianditu.gov.cn/DataServer"))
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
