package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConsistencyTest {

    @Test fun previousForecastSurvivesTheExactHourCleanupBoundary() {
        val now = java.time.Instant.parse("2026-09-07T08:00:00Z").toEpochMilli()
        val previous = HourlyWeather(now - 3_600_000L, 21.0)
        val data = WeatherData(hourly = listOf(HourlyWeather(now - 7_200_000L, 20.0), previous,
            HourlyWeather(now, 22.0), HourlyWeather(now + 3_600_000L, 24.0)))
        assertEquals(previous, WeatherConsistency.dropPastHourly(data, now).hourly.first())
        assertEquals(now, WeatherConsistency.dropPastHourly(data, now + 59 * 60_000L).hourly.first().timeMillis)
    }

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
    fun futureOnlySeriesRemainsNativeWithoutInsertedObservation() {
        val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = listOf(HourlyWeather(t0 + 51 * 60_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 111 * 60_000L, 15.0, WeatherCondition.CLEAR)))
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.current, result.current)
        assertEquals(raw.hourly, result.hourly)
    }

    @Test
    fun currentObservationCannotOverwriteRealContainingHour() {
        val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE),
            hourly = listOf(HourlyWeather(t0, 14.0, WeatherCondition.OVERCAST), HourlyWeather(t0 + 3_600_000L, 15.0)))
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.current, result.current)
        assertEquals(raw.hourly, result.hourly)
    }

    @Test
    fun futureSlotIsNotMislabelledAsNow() {
        for (minutes in listOf(5L, 20L, 51L)) {
            val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.CLEAR),
                hourly = listOf(HourlyWeather(t0 + minutes * 60_000, 12.0, WeatherCondition.RAIN)))
            assertEquals(raw.hourly, WeatherConsistency.align(raw, t0).hourly)
        }
    }

    @Test
    fun snowPredictionDoesNotReplaceCurrentObservation() {
        val raw = WeatherData(current = CurrentWeather(condition = WeatherCondition.OVERCAST),
            rainMinutes = Nowcast.minuteSeries(List(12) { .4f }, t0, phase = PrecipitationPhase.SNOW))
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.current, result.current)
        assertEquals(raw.rainMinutes, result.rainMinutes)
        assertTrue(Nowcast.briefingLine(result, "c", t0)!!.contains("短时预报提示当前有降雪"))
    }

    @Test
    fun wetPredictionIsQualifiedAndDoesNotManufactureCurrentPrecipitation() {
        val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.CLEAR, weatherText = "晴"),
            rainMinutes = Nowcast.minuteSeries(List(20) { .04f }, t0))
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.current, result.current)
        assertNull(result.current!!.precipMm)
        assertTrue(Nowcast.briefingLine(result, "c", t0)!!.contains("短时预报提示当前有降水"))
    }

    @Test
    fun presentationPrefersObservedRainWithoutMutatingSourceText() {
        val raw = WeatherData(current = CurrentWeather(condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            rainNowcast = "未来两小时不会下雨，您可以放心出门")
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.rainNowcast, result.rainNowcast)
        assertEquals("正在下雨", Nowcast.briefingLine(result, "c", t0))
    }

    @Test
    fun observationAndForecastRemainDistinctForDifferentConditions() {
        val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = listOf(HourlyWeather(t0 + 51 * 60_000L, 14.0, WeatherCondition.OVERCAST),
                HourlyWeather(t0 + 111 * 60_000L, 15.0, WeatherCondition.CLEAR)))
        val result = WeatherConsistency.align(raw, t0)
        assertEquals(raw.current, result.current)
        assertEquals(raw.hourly, result.hourly)
        assertTrue(Nowcast.briefingLine(result, "c", t0)!!.contains("雨"))
    }

    @Test
    fun fiftyMinutesPastHourKeepsContainingAndFutureForecastUnchanged() {
        val hours = listOf(HourlyWeather(t0, 14.0, WeatherCondition.OVERCAST),
            HourlyWeather(t0 + 3_600_000L, 15.0, WeatherCondition.RAIN))
        val raw = WeatherData(current = CurrentWeather(temperature = 16.0, condition = WeatherCondition.DRIZZLE), hourly = hours)
        val now = t0 + 50 * 60_000L
        assertEquals(0, WeatherConsistency.currentHourIndex(hours, now))
        assertEquals(hours, WeatherConsistency.align(raw, now).hourly)
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
