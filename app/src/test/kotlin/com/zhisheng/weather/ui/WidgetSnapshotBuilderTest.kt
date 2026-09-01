package com.zhisheng.weather.ui

import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotBuilderTest {

    @Test
    fun pendingSnapshotKeepsOnlyTheNewCityIdentity() {
        val pending = com.zhisheng.weather.data.WidgetSnapshot(city = "上海·浦东", updateMillis = 0L)
        assertEquals("上海·浦东", pending.city)
        assertEquals(null, pending.temp)
        assertTrue(pending.hours.isEmpty())
        assertTrue(pending.days.isEmpty())
    }

    @Test
    fun lifeTipsPreferUsefulRealValuesAndStayCompact() {
        val data = WeatherData(
            current = CurrentWeather(uvIndex = 6),
            sportsOk = true,
            extraIndices = listOf(
                LifeIndexExtra("穿衣", "DRESS", "天气较热，建议穿短袖"),
                LifeIndexExtra("舒适度", "COMFORT", "较舒适"),
            ),
        )

        val tips = widgetLifeTips(data, LifeIndexMetric.defaultSelection, enabled = true)

        assertEquals(listOf("紫外线", "穿衣", "运动"), tips.map { it.label })
        assertEquals("较强", tips[0].value)
        assertEquals("天气较热", tips[1].value)
        assertEquals("适宜", tips[2].value)
        assertTrue(tips.all { it.value.length <= 8 })
    }

    @Test
    fun lifeTipsRespectModuleAndItemSettings() {
        val data = WeatherData(
            current = CurrentWeather(uvIndex = 3),
            sportsOk = true,
        )

        assertTrue(widgetLifeTips(data, LifeIndexMetric.defaultSelection, enabled = false).isEmpty())
        assertEquals(
            listOf("运动"),
            widgetLifeTips(data, setOf(LifeIndexMetric.SPORTS), enabled = true).map { it.label },
        )
    }
}
