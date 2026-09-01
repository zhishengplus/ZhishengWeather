package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyOutlookTest {
    private val now = 1_800_000_000_000L

    @Test
    fun currentRainTakesPriorityOverForecastProbability() {
        val text = hourlyOutlookText(
            current = CurrentWeather(condition = WeatherCondition.RAIN),
            hourly = listOf(HourlyWeather(now, precipProb = 0)),
            nowMillis = now,
        )

        assertEquals("当前有降水，注意路面湿滑", text)
    }

    @Test
    fun meaningfulProbabilityProducesCautiousRainMessage() {
        val text = hourlyOutlookText(
            current = CurrentWeather(condition = WeatherCondition.CLEAR),
            hourly = listOf(HourlyWeather(now + 2 * 60 * 60 * 1000L, precipProb = 30)),
            nowMillis = now,
        )

        assertEquals("未来短时内可能有降水", text)
    }

    @Test
    fun dryHoursAvoidAnAbsoluteNoRainClaim() {
        val text = hourlyOutlookText(
            current = CurrentWeather(condition = WeatherCondition.CLEAR),
            hourly = (0 until 12).map {
                HourlyWeather(now + it * 60 * 60 * 1000L, precipProb = 5)
            },
            nowMillis = now,
        )

        assertEquals("未来短时内暂无明显降水", text)
    }

    @Test
    fun currentObservationIsInsertedAfterTheCurrentHourForecast() {
        val currentHour = now - 20 * 60 * 1000L
        val items = hourlyDisplayItems(
            current = CurrentWeather(temperature = 20.0, condition = WeatherCondition.CLEAR),
            hourly = listOf(
                HourlyWeather(currentHour, temperature = 19.0, condition = WeatherCondition.CLEAR),
                HourlyWeather(currentHour + 60 * 60 * 1000L, temperature = 21.0, condition = WeatherCondition.CLEAR),
            ),
            nowMillis = now,
        )

        assertEquals(3, items.size)
        assertFalse(items[0].isNow)
        assertEquals(19.0, items[0].weather.temperature ?: -1.0, 0.001)
        assertTrue(items[1].isNow)
        assertEquals(20.0, items[1].weather.temperature ?: -1.0, 0.001)
        assertFalse(items[2].isNow)
        assertEquals(21.0, items[2].weather.temperature ?: -1.0, 0.001)
    }
}
