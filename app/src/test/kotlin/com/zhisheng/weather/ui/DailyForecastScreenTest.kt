package com.zhisheng.weather.ui

import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.BriefingEmote
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.YesterdayInfo
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyForecastScreenTest {
    @Test
    fun `day and night text keeps both forecast phases`() {
        val visual = forecastDayNightVisual(
            DailyWeather(
                dateMillis = 0L,
                condition = WeatherCondition.CLEAR,
                weatherText = "晴转雷阵雨",
            ),
        )

        assertEquals("晴", visual.dayLabel)
        assertEquals("雷阵雨", visual.nightLabel)
        assertEquals(WeatherCondition.CLEAR, visual.dayCondition)
        assertEquals(WeatherCondition.THUNDERSTORM, visual.nightCondition)
    }

    @Test
    fun `night fallback uses a night icon without changing the label`() {
        val visual = forecastDayNightVisual(
            DailyWeather(dateMillis = 0L, condition = WeatherCondition.CLEAR, weatherText = "晴"),
        )

        assertEquals("晴", visual.nightLabel)
        assertEquals(WeatherCondition.CLEAR_NIGHT, visual.nightCondition)
    }

    @Test
    fun `date label follows the city timezone`() {
        val instant = Instant.parse("2026-08-30T23:30:00Z").toEpochMilli()
        assertEquals("8/31", forecastDateLabel(instant, 8 * 3600))
        assertEquals("8/30", forecastDateLabel(instant, -4 * 3600))
    }

    @Test
    fun `yesterday is prepended as a real previous local date only on forecast track`() {
        val now = Instant.parse("2026-08-30T16:30:00Z").toEpochMilli() // 8/31 00:30 in China
        val day = yesterdayForecastDay(
            YesterdayInfo(high = 30.0, low = 21.0, condition = WeatherCondition.CLEAR),
            utcOffsetSeconds = 8 * 3600,
            nowMillis = now,
        )

        requireNotNull(day)
        assertEquals("8/30", forecastDateLabel(day.dateMillis, 8 * 3600))
        assertEquals("昨天", forecastTemporalLabel(day.dateMillis, 8 * 3600, now))
        assertEquals(30.0, day.high)
        assertEquals(21.0, day.low)
    }

    @Test
    fun `forecast details hide missing rows and label available values`() {
        val empty = DailyWeather(dateMillis = 0L)
        val available = DailyWeather(
            dateMillis = 0L,
            precipProbability = 35,
            windSpeed = 18.0,
            sunrise = "06:38",
        )

        assertTrue(forecastDetailLabels(empty, "kmh").isEmpty())
        assertEquals(
            listOf("降水 35%", "风 18 km/h", "日出 06:38"),
            forecastDetailLabels(available, "kmh"),
        )
    }

    @Test
    fun `temperature range makes the space between high and low informative`() {
        val day = DailyWeather(dateMillis = 0L, high = 28.0, low = 16.0)

        assertEquals("温差 12°", forecastTemperatureRangeLabel(day, "c"))
        assertEquals("温差 22°", forecastTemperatureRangeLabel(day, "f"))
        assertEquals(null, forecastTemperatureRangeLabel(DailyWeather(dateMillis = 0L), "c"))
    }

    @Test
    fun `forecast digest derives trend extremes rain and temperature range from real days`() {
        val days = listOf(
            DailyWeather(0L, high = 30.0, low = 10.0, condition = WeatherCondition.RAIN),
            DailyWeather(86_400_000L, high = 31.0, low = 12.0, condition = WeatherCondition.CLEAR),
            DailyWeather(172_800_000L, high = 28.0, low = 13.0, condition = WeatherCondition.CLEAR),
            DailyWeather(259_200_000L, high = 24.0, low = 8.0, condition = WeatherCondition.CLEAR),
        )

        val digest = buildForecastDigest(days, "c", 0)
        val fahrenheit = buildForecastDigest(days, "f", 0)

        assertTrue(digest.headline.contains("凉"))
        assertEquals(BriefingEmote.COLD, digest.emote)
        assertEquals("31°", digest.highValue)
        assertEquals("8°", digest.lowValue)
        assertEquals("1 日", digest.rainValue)
        assertEquals("20°", digest.rangeValue)
        assertEquals("36°", fahrenheit.rangeValue)
        assertTrue(digest.overview.contains("有 1 天可能下雨"))
    }

    @Test
    fun `rain and freezing rain never produce a snow claim`() {
        val days = listOf(
            DailyWeather(0L, high = 8.0, low = 1.0, condition = WeatherCondition.RAIN, weatherText = "中雨"),
            DailyWeather(86_400_000L, high = 7.0, low = 0.0, condition = WeatherCondition.FREEZING_RAIN, weatherText = "冻雨"),
            DailyWeather(172_800_000L, high = 9.0, low = 2.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
        )

        val digest = buildForecastDigest(days, "c", 0, seedKey = "rain-only")

        assertFalse(digest.headline.contains("雪"))
        assertFalse(digest.overview.contains("雪"))
        assertEquals("3 日", digest.rainValue)
    }

    @Test
    fun `home exposes one five-day route and settings no longer exposes layout choices`() {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        val home = File(root, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()
        val settings = File(root, "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()
        val forecast = File(root, "src/main/kotlin/com/zhisheng/weather/ui/DailyForecastScreen.kt").readText()

        assertTrue(home.contains("val visibleDays = daily.take(5)"))
        assertTrue(home.contains("查看近15日天气"))
        assertTrue(home.contains(".height(38.dp)"))
        assertTrue(home.contains("d.condition?.label"))
        assertTrue(home.contains("降水概率 \$probability"))
        assertFalse(settings.contains("三天转上下"))
        assertFalse(settings.contains("完整上下"))
        assertFalse(settings.contains("经典横排"))
        assertTrue(forecast.contains("枳生天气娘 · 未来\${digest.dayCount}日"))
        assertTrue(forecast.contains(".align(Alignment.CenterStart)"))
        assertTrue(forecast.contains(".size(128.dp)"))
        assertTrue(forecast.contains(".alpha(if (isYesterday) 0.58f else 1f)"))
        assertTrue(forecast.contains("Column(verticalArrangement = Arrangement.spacedBy(8.dp))"))
        assertTrue(forecast.contains("maxLines = 2"))
        assertTrue(forecast.contains("yesterdayForecastDay"))
        assertFalse(forecast.contains("SIGNAL TRACK"))
        assertFalse(forecast.contains("FORECAST SYNTHESIS"))
    }
}
