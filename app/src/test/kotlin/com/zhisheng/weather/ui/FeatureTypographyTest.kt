package com.zhisheng.weather.ui

import com.zhisheng.weather.model.HistoricalDay
import com.zhisheng.weather.model.RecentWeatherWeek
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTypographyTest {
    @Test
    fun `recent range keeps dates compact and moves year into context`() {
        val sameYear = RecentWeatherWeek(
            listOf(HistoricalDay("2026-08-23"), HistoricalDay("2026-08-29")),
        )
        val crossYear = RecentWeatherWeek(
            listOf(HistoricalDay("2025-12-29"), HistoricalDay("2026-01-04")),
        )

        assertEquals(RecentRangeDisplay("08.23—08.29", "2026"), recentRangeDisplay(sameYear))
        assertEquals(RecentRangeDisplay("12.29—01.04", "2025—2026"), recentRangeDisplay(crossYear))
    }

    @Test
    fun `secondary headers and history dates avoid display scale typography`() {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        val chrome = File(root, "src/main/kotlin/com/zhisheng/weather/ui/FeatureScreenChrome.kt").readText()
        val history = File(root, "src/main/kotlin/com/zhisheng/weather/ui/HistoryScreen.kt").readText()
        val radar = File(root, "src/main/kotlin/com/zhisheng/weather/ui/RadarScreen.kt").readText()

        assertTrue(chrome.contains("fontSize = 18.sp"))
        assertTrue(history.contains("fontSize = 24.sp"))
        assertFalse(history.contains("MaterialTheme.typography.displaySmall"))
        assertTrue(radar.contains("Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding())"))
    }
}
