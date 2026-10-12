package com.zhisheng.weather.widget

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.RainMeta

class WidgetFeedbackPolicyTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun clockUsesSystemEntryBeforeAlarmAndDoesNotTryAlarmAfterSuccess() {
        val launches = mutableListOf<WidgetClockDestination>()
        assertTrue(launchWidgetClock { launches += it; it == WidgetClockDestination.CLOCK_ENTRY })
        assertEquals(listOf(WidgetClockDestination.CLOCK_ENTRY), launches)
        launches.clear()
        assertTrue(launchWidgetClock { launches += it; it == WidgetClockDestination.ALARMS })
        assertEquals(listOf(WidgetClockDestination.CLOCK_ENTRY, WidgetClockDestination.ALARMS), launches)
    }

    @Test fun absentClockDoesNotClaimLaunchSuccess() {
        val launches = mutableListOf<WidgetClockDestination>()
        assertFalse(launchWidgetClock { launches += it; false })
        assertEquals(listOf(WidgetClockDestination.CLOCK_ENTRY, WidgetClockDestination.ALARMS), launches)
    }

    @Test fun minutelyChartKeepsRealDryAndWetSamplesAndRejectsBadOrExpiredData() {
        val now = 1_000_000L
        val weather = WeatherData(fetchedAt = now, rainMeta = RainMeta("QWEATHER", 5, now), rainMinutes = listOf(
            MinutePrecip(now + 60_000, 0f), MinutePrecip(now + 5 * 60_000, .2f),
            MinutePrecip(now + 10 * 60_000, Float.NaN), MinutePrecip(now + 20 * 60_000, -1f),
            MinutePrecip(now - 6 * 60_000, 2f), MinutePrecip(now + 130 * 60_000, 2f)))
        assertEquals(listOf(0f, .2f), widgetMinuteSeries(weather, now).map { it.precip })
        assertTrue(widgetMinuteSeries(weather.copy(rainMeta = weather.rainMeta?.copy(updateTime = now - 2 * 3_600_000)), now).isEmpty())
        assertTrue(widgetMinuteSeries(weather.copy(rainMinutes = emptyList()), now).isEmpty())
    }

    @Test fun readabilityPresetsRetainCityAndActionsAndTransparentReallyMeansZero() {
        val config = WidgetConfig(cityKey = "fixed", clickTime = "none", textScale = 1.25f, showForecast = false)
        val readable = widgetReadablePreset(config)
        assertEquals("fixed", readable.cityKey)
        assertEquals("none", readable.clickTime)
        assertFalse(readable.showForecast)
        assertTrue(readable.boldText)
        assertTrue(readable.detailScale >= 1f)
        assertEquals(0, widgetTransparentPreset(config).opacity)
    }

    @Test fun panoramaCanShrinkWhileKeepingFourByFourInitialSize() {
        for (style in WidgetStyle.entries) {
            val path = "src/main/res/xml/widget_${style.key}_scene.xml"
            val file = File(path).takeIf { it.exists() } ?: File("app/$path")
            val provider = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            assertEquals("4", provider.getAttribute("android:targetCellHeight"))
            assertEquals("180dp", provider.getAttribute("android:minResizeHeight"))
            assertEquals("180dp", provider.getAttribute("android:minResizeWidth"))
        }
    }

    @Test fun userCanRequestDoubleSizeWithoutChangingDefault() {
        assertEquals(2f, WidgetConfig(textScale = 2f).normalized().textScale)
        assertEquals(1f, WidgetConfig().normalized().textScale)
        assertEquals(2f, WidgetConfig(textScale = 100f).normalized().textScale)
    }

    @Test fun hiddenClockAndForecastSurviveConfigRoundTrip() {
        val config = json.decodeFromString<WidgetConfig>("""{"showClock":false,"showForecast":false,"detailScale":1.3,"boldText":true,"opacity":0,"clickTime":"settings"}""")
        val saved = json.parseToJsonElement(json.encodeToString(WidgetConfig.serializer(), config.normalized())).jsonObject
        assertEquals("false", saved["showClock"]?.jsonPrimitive?.content)
        assertEquals("false", saved["showForecast"]?.jsonPrimitive?.content)
        assertEquals("1.3", saved["detailScale"]?.jsonPrimitive?.content)
        assertEquals("true", saved["boldText"]?.jsonPrimitive?.content)
        assertEquals("0", saved["opacity"]?.jsonPrimitive?.content)
        assertEquals("settings", saved["clickTime"]?.jsonPrimitive?.content)
    }

    @Test fun oldConfigKeepsClockForecastAndOriginalNumberScale() {
        val config = json.decodeFromString<WidgetConfig>("""{"textScale":1.25,"opacity":0,"cityKey":"my-city"}""")
        val saved = json.parseToJsonElement(json.encodeToString(WidgetConfig.serializer(), config.normalized())).jsonObject
        assertEquals("true", saved["showClock"]?.jsonPrimitive?.content)
        assertEquals("true", saved["showForecast"]?.jsonPrimitive?.content)
        assertEquals("1.25", saved["textScale"]?.jsonPrimitive?.content)
        assertEquals("my-city", saved["cityKey"]?.jsonPrimitive?.content)
    }
}
