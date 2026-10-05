package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeUvConsistencyTest {
    private val uvSelection = setOf(LifeIndexMetric.UV)

    @Test fun currentLowUvOverridesHighSupplierDailyGradeDuringRain() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.RAIN, uvIndex = 1),
            extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "很强")),
        )
        val items = lifeIndexItems(data, uvSelection)
        assertEquals(1, items.size)
        assertEquals("1 弱", items.single().value)
        assertEquals("当前", items.single().period)
    }

    @Test fun rainDoesNotFabricateLowUvWhenTheActualReadingIsHigh() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.RAIN, uvIndex = 8),
            extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "弱")),
        )
        assertEquals("8 很强", lifeIndexItems(data, uvSelection).single().value)
    }

    @Test fun zeroCurrentUvDoesNotFallBackToDaytimeGrade() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.CLEAR_NIGHT, uvIndex = 0),
            extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "极强")),
        )
        assertEquals("0 弱", lifeIndexItems(data, uvSelection).single().value)
    }

    @Test fun currentUvDoesNotRequireSeparateSupplierLifeIndices() {
        val data = WeatherData(current = CurrentWeather(uvIndex = 4))
        assertEquals("4 中等", lifeIndexItems(data, uvSelection).single().value)
    }

    @Test fun currentUvUsesTheSameGradeBoundariesAsTheHomeReading() {
        val expected = mapOf(2 to "弱", 3 to "中等", 5 to "中等", 6 to "强",
            7 to "强", 8 to "很强", 10 to "很强", 11 to "极强")
        expected.forEach { (uv, grade) ->
            val data = WeatherData(current = CurrentWeather(uvIndex = uv))
            assertEquals("$uv $grade", lifeIndexItems(data, uvSelection).single().value)
        }
    }

    @Test fun userCanHideUvEvenWhenCurrentReadingExists() {
        val data = WeatherData(current = CurrentWeather(uvIndex = 6))
        assertTrue(lifeIndexItems(data, emptySet()).isEmpty())
    }

    @Test fun unrelatedAndUnknownSupplierIndicesKeepTheirValues() {
        val data = WeatherData(
            current = CurrentWeather(uvIndex = 1),
            extraIndices = listOf(LifeIndexExtra("穿衣", "DRESS", "较冷"),
                LifeIndexExtra("路况", "INDEX 21", "较好")),
        )
        val items = lifeIndexItems(data, setOf(LifeIndexMetric.DRESS))
        assertEquals(listOf("较冷", "较好"), items.map { it.value })
    }

    @Test fun missingCurrentUvKeepsSupplierGradeClearlyMarkedAsDailyForecast() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.RAIN),
            extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "强")),
        )
        val item = lifeIndexItems(data, uvSelection).single()
        assertEquals("强", item.value)
        assertEquals("今日预报", item.period)
    }

    @Test fun invalidCurrentUvDoesNotBecomeAValidCurrentGrade() {
        for (uv in listOf(-1, 51)) {
            val data = WeatherData(current = CurrentWeather(uvIndex = uv),
                extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "弱")))
            val item = lifeIndexItems(data, uvSelection).single()
            assertEquals("弱", item.value)
            assertEquals("今日预报", item.period)
        }
    }
}
