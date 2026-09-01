package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowcastTest {
    @Test
    fun accumulatedPrecipitationNormalizesToMillimetresPerHour() {
        assertEquals(1.2f, Nowcast.accumulatedMmToRate(0.1f, 5), 0.0001f)
        assertEquals(0.4f, Nowcast.accumulatedMmToRate(0.1f, 15), 0.0001f)
        assertEquals(0f, Nowcast.accumulatedMmToRate(-1f, 5), 0.0001f)
    }

    private val t0 = 1_700_000_000_000L

    @Test
    fun minuteSeriesAlignsIndexToTimestamp() {
        val series = Nowcast.minuteSeries(listOf(0f, 0.2f, 0.4f), t0)
        assertEquals(3, series.size)
        assertEquals(t0, series[0].timeMillis)
        assertEquals(t0 + 60_000L, series[1].timeMillis)
        assertEquals(0.4f, series[2].precip)
    }

    @Test
    fun rainTimingDetectsRainNow() {
        val minutes = Nowcast.minuteSeries(listOf(0.2f, 0.3f), t0)
        val timing = Nowcast.rainTiming(minutes, t0)
        assertTrue(timing.rainingNow)
        assertEquals(0, timing.minutesUntilStart)
        assertEquals("正在下雨", Nowcast.rainTimingLabel(timing))
    }

    @Test
    fun rainTimingReportsMinutesUntilStart() {
        val values = MutableList(40) { 0f }
        values[35] = 0.2f
        val minutes = Nowcast.minuteSeries(values, t0)
        val timing = Nowcast.rainTiming(minutes, t0)
        assertFalse(timing.rainingNow)
        assertEquals(35, timing.minutesUntilStart)
        assertEquals("35 分钟后开始下雨", Nowcast.rainTimingLabel(timing))
    }

    @Test
    fun rainTimingIgnoresDrySeries() {
        val minutes = Nowcast.minuteSeries(List(120) { 0f }, t0)
        val timing = Nowcast.rainTiming(minutes, t0)
        assertFalse(timing.hasRain)
        assertNull(Nowcast.rainTimingLabel(timing))
    }

    @Test
    fun briefingPrefersComputedTimingWhenSeriesHasRain() {
        val values = MutableList(20) { 0f }
        values[10] = 0.5f
        val data = WeatherData(
            rainNowcast = "距离最近的降雨约在38公里以外~",
            rainMinutes = Nowcast.minuteSeries(values, t0),
        )
        assertEquals("10 分钟后开始下雨", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun briefingPrefersRainOverTemperature() {
        val values = MutableList(20) { 0f }
        values[10] = 0.5f
        val data = WeatherData(
            rainMinutes = Nowcast.minuteSeries(values, t0),
            daily = listOf(
                DailyWeather(dateMillis = t0, high = 30.0, low = 20.0),
                DailyWeather(dateMillis = t0 + 86_400_000L, high = 20.0, low = 12.0),
            ),
        )
        assertEquals("10 分钟后开始下雨", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun briefingUsesProviderNowcastWhenMinuteSeriesIsDry() {
        val data = WeatherData(
            rainNowcast = "未来两小时近处有雨约38公里外",
            rainMinutes = Nowcast.minuteSeries(List(120) { 0f }, t0),
        )
        assertEquals("未来两小时近处有雨约38公里外", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun briefingSkipsDryNowcastFromApi() {
        val data = WeatherData(rainNowcast = "未来两小时不会下雨，放心出门吧")
        assertNull(Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun precipCardHidesDrySeriesAndDryCopy() {
        val dry = WeatherData(
            rainNowcast = "未来两小时不会下雨",
            rainMinutes = Nowcast.minuteSeries(List(120) { 0f }, t0),
        )
        assertFalse(Nowcast.shouldShowPrecipCard(dry, t0))
        val wet = WeatherData(
            rainMinutes = Nowcast.minuteSeries(List(120) { if (it == 20) 0.3f else 0f }, t0),
        )
        assertTrue(Nowcast.shouldShowPrecipCard(wet, t0))
    }

    @Test
    fun caiyunMinuteModuleStaysAvailableWhenForecastIsDry() {
        val caiyunDry = WeatherData(
            dataSource = "CAIYUN",
            rainMinutes = Nowcast.minuteSeries(List(120) { 0f }, t0),
        )
        assertTrue(Nowcast.shouldShowPrecipModule(caiyunDry, t0))

        val noPaidMinuteBlock = WeatherData(dataSource = "CAIYUN")
        assertFalse(Nowcast.shouldShowPrecipModule(noPaidMinuteBlock, t0))
    }

    @Test
    fun precipChartScaleKeepsLightRainReadable() {
        assertEquals(0f, Nowcast.precipChartCeiling(emptyList()))
        assertEquals(
            0.05f,
            Nowcast.precipChartCeiling(Nowcast.minuteSeries(listOf(0.01f, 0.04f), t0)),
        )
        assertEquals(
            0.25f,
            Nowcast.precipChartCeiling(Nowcast.minuteSeries(listOf(0.08f, 0.21f), t0)),
        )
        assertEquals(
            2f,
            Nowcast.precipChartCeiling(Nowcast.minuteSeries(listOf(0.8f, 1.4f), t0)),
        )
    }

    @Test
    fun briefingReportsTomorrowColder() {
        val data = WeatherData(
            daily = listOf(
                DailyWeather(dateMillis = t0, high = 28.0, low = 18.0),
                DailyWeather(dateMillis = t0 + 86_400_000L, high = 22.0, low = 14.0),
            ),
        )
        val briefing = Nowcast.briefing(data, "c", t0)!!
        assertEquals(BriefingKind.TEMPERATURE, briefing.kind)
        assertEquals(BriefingEmote.COLD, briefing.emote)
        assertTrue(briefing.text.contains("6°"))
    }

    @Test
    fun briefingComparesCityCalendarTodayAndTomorrowNotListIndex() {
        val offset = 8 * 3_600
        val now = java.time.Instant.parse("2026-08-21T06:00:00Z").toEpochMilli()
        val data = WeatherData(
            daily = listOf(
                DailyWeather(dateMillis = java.time.Instant.parse("2026-08-20T14:00:00Z").toEpochMilli(), high = 30.0, low = 18.0),
                DailyWeather(dateMillis = java.time.Instant.parse("2026-08-20T16:00:00Z").toEpochMilli(), high = 28.0, low = 16.0),
                DailyWeather(dateMillis = java.time.Instant.parse("2026-08-21T16:00:00Z").toEpochMilli(), high = 22.0, low = 12.0),
            ),
            utcOffsetSeconds = offset,
        )
        val briefing = Nowcast.briefing(data, "c", now)!!
        assertEquals(BriefingKind.TEMPERATURE, briefing.kind)
        assertEquals(BriefingEmote.COLD, briefing.emote)
        assertTrue(briefing.text.contains("6°"))
    }

    @Test
    fun briefingIgnoresSmallTemperatureSwing() {
        val data = WeatherData(
            daily = listOf(
                DailyWeather(dateMillis = t0, high = 20.0, low = 12.0),
                DailyWeather(dateMillis = t0 + 86_400_000L, high = 21.0, low = 12.0),
            ),
        )
        assertNull(Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun briefingPrefersRedAlertOverMildAlert() {
        val data = WeatherData(
            alerts = listOf(
                AlertInfo(title = "大风蓝色预警", severity = AlertLevel.BLUE),
                AlertInfo(title = "暴雨红色预警", severity = AlertLevel.RED),
            ),
        )
        assertEquals("红色预警生效中，避开低洼路段和积水区域，驾车不要贸然涉水。", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun yellowAlertBecomesActionableBriefingInsteadOfDuplicateTitle() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.CLEAR),
            alerts = listOf(
                AlertInfo(
                    title = "金川发布地质灾害气象风险黄色预警",
                    severity = AlertLevel.YELLOW,
                ),
            ),
        )

        val briefing = Nowcast.briefing(data, "c", t0)!!

        assertEquals("黄色预警生效中，尽量远离山区沟谷和陡坡，注意落石、滑坡等风险。", briefing.text)
        assertEquals(BriefingKind.ALERT, briefing.kind)
        assertEquals(BriefingEmote.ALERT, briefing.emote)
        assertEquals(AlertLevel.YELLOW, briefing.alertLevel)
    }

    @Test
    fun calmWeatherLineStaysStableForTheSameLocalDay() {
        val data = WeatherData(current = CurrentWeather(condition = WeatherCondition.CLEAR))

        val first = Nowcast.briefing(data, "c", t0)!!
        val second = Nowcast.briefing(data, "c", t0 + 10 * 60_000L)!!

        assertEquals(BriefingKind.AMBIENT, first.kind)
        assertEquals(first.text, second.text)
        assertEquals(BriefingEmote.NIGHT, first.emote)
    }

    @Test
    fun hotWeatherCopyIsNaturalAndDoesNotUseTheReportedForcedMetaphor() {
        val now = java.time.Instant.parse("2026-08-31T08:28:00Z").toEpochMilli()
        val data = WeatherData(
            current = CurrentWeather(temperature = 31.0, condition = WeatherCondition.CLEAR),
            utcOffsetSeconds = 8 * 3_600,
        )

        val text = Nowcast.briefing(data, "c", now)!!.text

        assertTrue(listOf("热", "气温", "补水", "轻装", "透气").any(text::contains))
        assertFalse(text.contains("发烫"))
        assertFalse(text.contains("走慢一点"))
        assertFalse(text.contains("正在营业"))
    }

    @Test
    fun tidyCopyStripsTrailingWaveDash() {
        assertEquals(
            "未来两小时不会下雨，您可以放心出门",
            Nowcast.tidyCopy("未来两小时不会下雨，您可以放心出门~"),
        )
    }

    @Test
    fun looksLikeIncomingRainRejectsNegativesAndFarAway() {
        assertFalse(Nowcast.looksLikeIncomingRain("未来两小时无降水"))
        assertFalse(Nowcast.looksLikeIncomingRain("距离最近的降雨约在38公里以外"))
        assertTrue(Nowcast.looksLikeIncomingRain("35分钟后有雨"))
        assertTrue(Nowcast.looksLikeIncomingRain("正在下雨"))
    }

    @Test
    fun rainTimingIgnoresRainThatAlreadyEnded() {
        val values = MutableList(40) { if (it < 10) 0.2f else 0f }
        val timing = Nowcast.rainTiming(Nowcast.minuteSeries(values, t0), t0 + 25 * 60_000L)
        assertFalse(timing.rainingNow)
        assertNull(timing.minutesUntilStart)
        assertNull(Nowcast.rainTimingLabel(timing))
    }

    @Test
    fun briefingKeepsRainingWhenApiSaysNoRain() {
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            rainNowcast = "未来两小时不会下雨，放心出门吧",
            rainMinutes = Nowcast.minuteSeries(List(120) { 0f }, t0),
        )
        assertEquals("正在下雨", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun rainTimingReportsMinutesUntilEnd() {
        val values = MutableList(40) { if (it < 23) 0.03f else 0f }
        val timing = Nowcast.rainTiming(Nowcast.minuteSeries(values, t0), t0)
        assertTrue(timing.rainingNow)
        assertEquals(23, timing.minutesUntilEnd)
        assertEquals("23 分钟后雨会停", Nowcast.rainTimingLabel(timing))
    }

    @Test
    fun briefingAndPrecipCardShareComputedStopTime() {
        val values = MutableList(40) { if (it < 29) 0.03f else 0f }
        val data = WeatherData(
            current = CurrentWeather(condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            rainNowcast = "半小时后雨渐停",
            rainMinutes = Nowcast.minuteSeries(values, t0),
        )
        assertEquals("29 分钟后雨会停", Nowcast.briefingLine(data, "c", t0))
    }

    @Test
    fun densifyTurnsFifteenMinuteBucketsIntoMinuteBars() {
        val coarse = Nowcast.minuteSeries(listOf(0f, 0f, 0.4f, 0.4f, 0f, 0f, 0f, 0f), t0, 15 * 60_000L)
        val dense = Nowcast.densifyToMinutes(coarse)
        assertEquals(120, dense.size)
        assertEquals(t0, dense[0].timeMillis)
        assertEquals(0f, dense[29].precip)
        assertEquals(0.4f, dense[30].precip)
        assertEquals(0.4f, dense[44].precip)
        assertEquals("119 分钟", Nowcast.horizonLabel(dense))
    }

    @Test
    fun densifyKeepsAlreadyMinuteSeriesShape() {
        val minutes = Nowcast.minuteSeries(List(120) { if (it == 10) 0.5f else 0f }, t0)
        val dense = Nowcast.densifyToMinutes(minutes)
        assertEquals(120, dense.size)
        assertEquals(0.5f, dense[10].precip)
        assertEquals(0f, dense[11].precip)
    }

    @Test
    fun nativeProviderLabelsRemainReadable() {
        assertEquals("和风", Nowcast.sourceLabel("QWEATHER"))
        assertEquals("彩云", Nowcast.sourceLabel("CAIYUN"))
        assertEquals("小雨", Nowcast.intensityLabel(0.8f))
        assertEquals("大雨", Nowcast.intensityLabel(10f))
    }

    @Test
    fun rainingWithEmptySeriesIsNotDrawnAsClearWindow() {
        assertFalse(Nowcast.precipCardClearWindow(emptyList(), t0, precipNow = true))
        assertTrue(
            Nowcast.precipCardClearWindow(
                Nowcast.minuteSeries(List(120) { 0f }, t0),
                t0,
                precipNow = false,
            ),
        )
    }
}
