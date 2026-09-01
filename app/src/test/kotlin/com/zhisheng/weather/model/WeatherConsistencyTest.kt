package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConsistencyTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun dropPastHourlyRemovesHoursOlderThanGraceWindow() {
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE),
            hourly = listOf(
                HourlyWeather(t0 - 3 * 3_600_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 3_600_000L, 16.0, WeatherCondition.DRIZZLE),
            ),
        )
        val trimmed = WeatherConsistency.dropPastHourly(data, t0)
        assertEquals(1, trimmed.hourly.size)
        assertEquals(t0 + 3_600_000L, trimmed.hourly[0].timeMillis)
    }

    @Test
    fun prependsNowSlotWhenHourlyStartsInTheFuture() {
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = listOf(
                HourlyWeather(t0 + 51 * 60_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 111 * 60_000L, 14.0, WeatherCondition.OVERCAST),
            ),
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals(WeatherCondition.DRIZZLE, aligned.hourly.first().condition)
        assertEquals(16.0, aligned.hourly.first().temperature)
        assertEquals(WeatherCondition.OVERCAST, aligned.hourly[1].condition)
    }

    @Test
    fun overlaysCurrentConditionOntoTheNowHour() {
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = listOf(
                HourlyWeather(t0, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 3_600_000L, 14.0, WeatherCondition.OVERCAST),
            ),
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals(WeatherCondition.DRIZZLE, aligned.hourly[0].condition)
        assertEquals(16.0, aligned.hourly[0].temperature)
        assertEquals(WeatherCondition.OVERCAST, aligned.hourly[1].condition)
    }

    @Test
    fun futureSlotIsNotMislabelledAsNow() {
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.CLEAR),
            hourly = listOf(HourlyWeather(t0 + 20 * 60_000L, 12.0, WeatherCondition.RAIN)),
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals(t0, aligned.hourly.first().timeMillis)
        assertEquals(WeatherCondition.CLEAR, aligned.hourly.first().condition)
        assertEquals(WeatherCondition.RAIN, aligned.hourly[1].condition)
    }

    @Test
    fun snowNowcastDoesNotTurnIntoRain() {
        val minutes = Nowcast.minuteSeries(
            List(12) { 0.4f },
            t0,
            phase = PrecipitationPhase.SNOW,
        )
        val aligned = WeatherConsistency.align(
            WeatherData(current = CurrentWeather(condition = WeatherCondition.OVERCAST), rainMinutes = minutes),
            t0,
        )
        assertEquals(WeatherCondition.SNOW, aligned.current?.condition)
    }

    @Test
    fun upgradesClearCurrentWhenMinuteSeriesIsWetNow() {
        val minutes = Nowcast.minuteSeries(List(20) { 0.04f }, t0)
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.OVERCAST, weatherText = "阴"),
            rainMinutes = minutes,
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals(WeatherCondition.DRIZZLE, aligned.current?.condition)
        assertEquals("小雨", aligned.current?.weatherText)
        assertEquals(0.04, aligned.current?.precipMm!!, 0.0001)
    }

    @Test
    fun dropsContradictingDryNowcastWhenRaining() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            rainNowcast = "未来两小时不会下雨，您可以放心出门",
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertNull(aligned.rainNowcast)
        assertEquals("正在下雨", Nowcast.briefingLine(aligned, "c", t0))
    }

    @Test
    fun jinchangStyleHeroAndHourlyAgree() {
        val minutes = Nowcast.minuteSeries(List(23) { 0.033f } + List(97) { 0f }, t0)
        val data = WeatherData(
            current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = listOf(
                HourlyWeather(t0 + 51 * 60_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 111 * 60_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 171 * 60_000L, 14.0, WeatherCondition.DRIZZLE),
            ),
            rainNowcast = "未来两小时不会下雨",
            rainMinutes = minutes,
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals("小雨", aligned.current?.weatherText)
        assertEquals(WeatherCondition.DRIZZLE, aligned.hourly.first().condition)
        assertTrue(Nowcast.briefingLine(aligned, "c", t0)!!.contains("雨"))
        assertTrue(!Nowcast.briefingLine(aligned, "c", t0)!!.contains("不会下雨"))
    }

    @Test
    fun fiftyMinutesPastTheHourOverlaysTheContainingHourNotTheNext() {
        val hour10 = t0
        val hour11 = t0 + 3_600_000L
        val now = t0 + 50 * 60_000L
        val hourly = listOf(
            HourlyWeather(hour10, 14.0, WeatherCondition.OVERCAST),
            HourlyWeather(hour11, 14.0, WeatherCondition.RAIN),
        )
        assertEquals(0, WeatherConsistency.currentHourIndex(hourly, now))
        val aligned = WeatherConsistency.align(
            WeatherData(
                current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
                hourly = hourly,
            ),
            now,
        )
        assertEquals(WeatherCondition.DRIZZLE, aligned.hourly[0].condition)
        assertEquals(16.0, aligned.hourly[0].temperature)
        assertEquals(WeatherCondition.RAIN, aligned.hourly[1].condition)
    }

    @Test
    fun alignStillDropsPastHoursWhenCurrentIsMissing() {
        val data = WeatherData(
            hourly = listOf(
                HourlyWeather(t0 - 3 * 3_600_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 3_600_000L, 16.0, WeatherCondition.DRIZZLE),
            ),
        )
        val aligned = WeatherConsistency.align(data, t0)
        assertEquals(1, aligned.hourly.size)
        assertEquals(t0 + 3_600_000L, aligned.hourly[0].timeMillis)
    }

    @Test
    fun widgetKeepsFirstFutureHourWhenThereIsNoCurrentSlot() {
        val now = t0
        val hourly = listOf(
            HourlyWeather(t0 + 2 * 3_600_000L, 16.0, WeatherCondition.OVERCAST),
            HourlyWeather(t0 + 3 * 3_600_000L, 17.0, WeatherCondition.CLEAR),
        )

        assertEquals(-1, WeatherConsistency.currentHourIndex(hourly, now))
        assertEquals(0, WeatherConsistency.upcomingHourStartIndex(hourly, now))
    }

    @Test
    fun widgetSkipsOnlyTheActualCurrentHour() {
        val hourly = listOf(
            HourlyWeather(t0, 15.0, WeatherCondition.OVERCAST),
            HourlyWeather(t0 + 3_600_000L, 16.0, WeatherCondition.CLEAR),
        )

        assertEquals(1, WeatherConsistency.upcomingHourStartIndex(hourly, t0 + 20 * 60_000L))
    }

    @Test
    fun sanitizerRejectsImpossibleValuesAndOrdersTimeSeries() {
        val sane = WeatherConsistency.sanitize(
            WeatherData(
                current = CurrentWeather(
                    temperature = Double.NaN,
                    humidity = 130.0,
                    windDirectionDeg = -10.0,
                    pressure = 1010.0,
                ),
                hourly = listOf(
                    HourlyWeather(t0 + 3_600_000L, 20.0, precipProb = 130),
                    HourlyWeather(t0, 18.0, windDirectionDeg = 725.0),
                    HourlyWeather(t0, 99.0),
                    HourlyWeather(-1L, 10.0),
                ),
                daily = listOf(
                    DailyWeather(t0 + 86_400_000L, high = 10.0, low = 20.0),
                    DailyWeather(t0, high = 30.0, low = 15.0, humidity = -1.0),
                ),
                rainMinutes = listOf(
                    MinutePrecip(t0 + 60_000L, -1f),
                    MinutePrecip(t0, 0.2f),
                ),
            ),
        )

        assertNull(sane.current?.temperature)
        assertNull(sane.current?.humidity)
        assertEquals(350.0, sane.current?.windDirectionDeg ?: -1.0, 0.0001)
        assertEquals(listOf(t0, t0 + 3_600_000L), sane.hourly.map { it.timeMillis })
        assertEquals(5.0, sane.hourly.first().windDirectionDeg ?: -1.0, 0.0001)
        assertNull(sane.hourly.last().precipProb)
        assertEquals(20.0, sane.daily.last().high ?: -1.0, 0.0001)
        assertEquals(10.0, sane.daily.last().low ?: -1.0, 0.0001)
        assertEquals(listOf(t0), sane.rainMinutes.map { it.timeMillis })
    }

    @Test
    fun sanitizerDoesNotPretendAnEmptyInvalidCurrentIsUsable() {
        val sane = WeatherConsistency.sanitize(
            WeatherData(
                current = CurrentWeather(
                    temperature = 999.0,
                    humidity = -2.0,
                    condition = WeatherCondition.UNKNOWN,
                    weatherText = "未知",
                ),
            ),
        )

        assertNull(sane.current)
    }

    @Test
    fun sanitizerRejectsFutureFreshnessAndNonNumericPollutants() {
        val sane = WeatherConsistency.sanitize(
            WeatherData(
                current = CurrentWeather(temperature = 20.0),
                updateTime = t0 + 10 * 60_000L,
                aqi = AqiInfo(value = 40, pm25 = "NaN", pm10 = "18.5", co = "-1"),
            ),
            nowMillis = t0,
        )

        assertNull(sane.updateTime)
        assertNull(sane.aqi?.pm25)
        assertEquals("18.5", sane.aqi?.pm10)
        assertNull(sane.aqi?.co)
    }
}
