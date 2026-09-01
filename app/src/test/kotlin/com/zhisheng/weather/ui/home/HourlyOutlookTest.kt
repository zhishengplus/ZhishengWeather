package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyOutlookTest {
    private val now = 1_800_000_000_000L

    @Test
    fun risingHoursProduceAUsefulTemperatureSummary() {
        val text = hourlyTrendText(
            hourly = listOf(
                HourlyWeather(now, temperature = 20.0),
                HourlyWeather(now + 60 * 60 * 1000L, temperature = 22.0),
                HourlyWeather(now + 2 * 60 * 60 * 1000L, temperature = 25.0),
            ),
            nowMillis = now,
            unit = "c",
        )

        assertEquals("接下来几小时逐渐升温 · 20° → 25°", text)
    }

    @Test
    fun interiorPeakIsDescribedAsRiseThenFall() {
        val text = hourlyTrendText(
            hourly = listOf(
                HourlyWeather(now, temperature = 20.0),
                HourlyWeather(now + 60 * 60 * 1000L, temperature = 24.0),
                HourlyWeather(now + 2 * 60 * 60 * 1000L, temperature = 21.0),
            ),
            nowMillis = now,
            unit = "c",
        )

        assertEquals("接下来几小时先升后降 · 20° → 24° → 21°", text)
    }

    @Test
    fun stableHoursDoNotRepeatTheNowcastConclusion() {
        val text = hourlyTrendText(
            hourly = (0 until 6).map {
                HourlyWeather(
                    timeMillis = now + it * 60 * 60 * 1000L,
                    temperature = 20.0 + it * 0.2,
                    precipProb = 90,
                    condition = WeatherCondition.RAIN,
                )
            },
            nowMillis = now,
            unit = "c",
        )

        assertEquals("接下来几小时气温平稳 · 20°–21°", text)
    }

    @Test
    fun aPastHourlyAnchorCannotBecomeTheTrendStartingPoint() {
        val text = hourlyTrendText(
            hourly = listOf(
                HourlyWeather(now - 50 * 60 * 1000L, temperature = 32.0),
                HourlyWeather(now, temperature = 24.0),
                HourlyWeather(now + 60 * 60 * 1000L, temperature = 22.0),
            ),
            nowMillis = now,
            unit = "c",
        )

        assertEquals("接下来几小时逐渐降温 · 24° → 22°", text)
    }

    @Test
    fun currentObservationIsInsertedAfterTheCurrentHourForecast() {
        val currentHour = now - 20 * 60 * 1000L
        val items = hourlyDisplayItems(
            current = com.zhisheng.weather.model.CurrentWeather(temperature = 20.0, condition = WeatherCondition.CLEAR),
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

    @Test
    fun longProviderPayloadIsCappedOnTheHomeScreenWithoutLosingNow() {
        val currentHour = now - 20 * 60 * 1000L
        val items = hourlyDisplayItems(
            current = com.zhisheng.weather.model.CurrentWeather(
                temperature = 20.0,
                condition = WeatherCondition.CLEAR,
            ),
            hourly = (0 until 240).map { index ->
                HourlyWeather(
                    timeMillis = currentHour + index * 60 * 60 * 1000L,
                    temperature = 19.0 + index,
                    condition = WeatherCondition.CLEAR,
                )
            },
            nowMillis = now,
        )

        assertEquals(HOME_HOURLY_ITEM_LIMIT, items.size)
        assertFalse(items.first().isNow)
        assertTrue(items[1].isNow)
    }

    @Test
    fun longPayloadWithoutCurrentObservationStillShowsOnlyOneDay() {
        val hourly = (0 until 240).map { index ->
            HourlyWeather(
                timeMillis = now + index * 60 * 60 * 1000L,
                temperature = 20.0,
            )
        }

        val items = hourlyDisplayItems(current = null, hourly = hourly, nowMillis = now)

        assertEquals(HOME_HOURLY_ITEM_LIMIT, items.size)
        assertEquals(hourly.take(HOME_HOURLY_ITEM_LIMIT), items.map { it.weather })
    }
}
