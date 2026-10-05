package com.zhisheng.weather.ui

import com.zhisheng.weather.ui.home.VistaHomeBlock
import com.zhisheng.weather.ui.home.vistaHomeBlocks
import com.zhisheng.weather.ui.home.vistaScenePages
import com.zhisheng.weather.ui.home.homeWeatherUpdateStamp
import com.zhisheng.weather.model.WeatherData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearSkyLayoutRegressionTest {
    @Test fun fiveDayForecastCanBeSavedFirstOrLast() {
        val modules = com.zhisheng.weather.data.HomeModule
        val first = listOf(com.zhisheng.weather.data.HomeModule.DAILY) +
            com.zhisheng.weather.data.HomeModule.defaultOrder.filterNot { it == com.zhisheng.weather.data.HomeModule.DAILY }
        for (order in listOf(first, first.drop(1) + first.first())) {
            val saved = com.zhisheng.weather.data.HomeModule.orderFrom(order.joinToString(",") { it.key })
            val blocks = vistaHomeBlocks(VistaHomeBlock.entries.toSet(), saved).drop(3)
            if (order.first() == com.zhisheng.weather.data.HomeModule.DAILY) assertEquals(VistaHomeBlock.DAILY, blocks.first())
            else assertEquals(VistaHomeBlock.DAILY, blocks.last())
        }
    }
    private val root = File(requireNotNull(System.getProperty("user.dir")), "src/main/kotlin/com/zhisheng/weather/ui")

    @Test fun vistaHomeBlocksKeepTheirRelativeOrderWhenOnlySomeDataIsAvailable() {
        assertEquals(
            listOf(VistaHomeBlock.METADATA, VistaHomeBlock.HERO, VistaHomeBlock.TYPHOON),
            vistaHomeBlocks(setOf(VistaHomeBlock.HERO, VistaHomeBlock.TYPHOON, VistaHomeBlock.METADATA)),
        )
    }

    @Test fun vistaHomeBlocksFollowTheSafetyAndFrequencyReadingOrder() {
        assertEquals(
            listOf(
                VistaHomeBlock.METADATA,
                VistaHomeBlock.HERO,
                VistaHomeBlock.ALERTS,
                VistaHomeBlock.HOURLY,
                VistaHomeBlock.DAILY,
                VistaHomeBlock.PRECIPITATION,
                VistaHomeBlock.TELEMETRY,
                VistaHomeBlock.AQI,
                VistaHomeBlock.ATLAS,
                VistaHomeBlock.TYPHOON,
                VistaHomeBlock.INDICES,
                VistaHomeBlock.YESTERDAY,
            ),
            vistaHomeBlocks(VistaHomeBlock.entries.toSet()),
        )
    }

    @Test fun sourceTimeAndCoordinatesRemainAvailable() {
        val home = File(root, "home/HomeScreen.kt").readText()
        assertTrue(home.contains("GPS 精确定位"))
        assertTrue(home.contains("经纬度"))
        assertTrue(home.contains("数据发布于"))
        assertEquals("更新于", homeWeatherUpdateStamp(WeatherData(fetchedAt = 1L))?.label)
        assertTrue(home.contains("supplementShortLabel(data)"))
        assertTrue(home.contains(" 分钟前保存"))
    }

    @Test fun atlasDoesNotOfferUnavailableSkyData() {
        val detail = File(root, "home/AtlasDetails.kt").readText()
        // Surface placement is covered by native previews. Missing measurements must
        // still produce no scene entries even when the user enabled these modules.
        assertTrue(vistaScenePages(null, DisplayPrefs(showSkyPhotography = true, showCoastWeather = true)).isEmpty())
        assertTrue(detail.contains("数据源：尚未接入 · 更新时间：暂无"))
        assertTrue(detail.contains("不能用今天的天气评分代替"))
    }
}
