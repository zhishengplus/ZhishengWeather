package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherCnForecastTest {
    private val now = Instant.parse("2026-12-31T04:00:00Z").toEpochMilli()
    private fun html(stamp: String = "2026123108", area: String = "101010100") = """
        <ul id="someDayNav"><a href="/weather/$area.shtml">七天</a></ul>
        <input value="$stamp" id="fc_24h_internal_update_time" />
        <input id="fc_3h_internal_update_time" value="2026-12-31 08:00" />
        <ul class="t clearfix">
          <li><h1>31日（今天）</h1><big class="png40 d00"></big><big class="png40 n14"></big><p class="wea">晴转小雪</p><p class="tem"><span>2℃</span><i>-4℃</i></p></li>
          <li><h1>1日（明天）</h1><p class="wea">晴</p><p class="tem"><i>-3℃</i></p></li>
        </ul>
        <script>var hour3data={"1d":["31日14时,d00,晴,2℃,北风,3级,0","1日02时,n14,小雪,-3℃,北风,3级,0","1日05时,n14,小雪,9999℃,北风,3级,0"]};</script>
    """.trimIndent()

    @Test fun datesCrossNewYearAndNightOnlyDoesNotInventHigh() {
        val data = parseWeatherCnForecast(html(), "101010100", now)
        assertEquals(2, data.daily.size)
        assertEquals("2027-01-01", cityDate(data.daily[1].dateMillis, 28800).toString())
        assertNull(data.daily[1].high)
        assertEquals(-3.0, data.daily[1].low!!, 0.0)
        assertEquals(WeatherCondition.SNOW, data.daily[0].condition)
        assertEquals(2, data.hourly.size)
        assertEquals(Instant.parse("2026-12-31T18:00:00Z").toEpochMilli(), data.hourly[1].timeMillis)
        assertTrue(data.hourly.all { it.windSpeed == null && it.precipMm == null })
    }

    @Test fun wrongCityOrMissingPublicationStampCannotBecomeForecast() {
        assertTrue(parseWeatherCnForecast(html(area = "101280101"), "101010100", now).daily.isEmpty())
        assertTrue(parseWeatherCnForecast(html(stamp = ""), "101010100", now).daily.isEmpty())
    }

    @Test fun oldPageIsNotRedatedAndDuplicateHoursAreNotInterpolated() {
        val later = now + 10 * 24 * 3600_000L
        val parsed = parseWeatherCnForecast(html(), "101010100", later)
        val checked = WeatherQuality.prepare(WeatherData(hourly = parsed.hourly, daily = parsed.daily, utcOffsetSeconds = 28800), later)
        assertTrue(checked.daily.isEmpty())
        assertTrue(checked.hourly.isEmpty())
    }

    @Test fun sevenDayPageNeverRedatesStaleSingleDayFallback() {
        val bundle = WeatherComCnApi.WcnBundle("101010100", null,
            WeatherComCnApi.WcnToday(20.0, 10.0, "晴", "晴", "0", "0", null, null, "2026123008"),
            emptyList(), emptyList(), forecast = WeatherCnForecast(publishedAt = now))
        assertNull(wcnToWeatherData(bundle, now))
    }
}
