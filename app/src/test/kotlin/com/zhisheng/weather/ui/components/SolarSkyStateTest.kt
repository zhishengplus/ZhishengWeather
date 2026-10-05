package com.zhisheng.weather.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.PhosphorVistaLightPalette
import com.zhisheng.weather.ui.theme.PhosphorVistaDarkPalette
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class SolarSkyStateTest {
    private val now = Instant.parse("2026-03-20T12:00:00Z").toEpochMilli()
    private fun state(condition: WeatherCondition, altitude: Float, rising: Boolean = false): NaturalLightState {
        val solar = SolarSkyState(altitude, if (rising) 95f else 265f, rising)
        return naturalLightState(WeatherData(current = CurrentWeather(condition = condition)), false, 720, now, solar)
    }

    @Test fun sameInstantDoesNotChangeWhenPhoneTimezoneChanges() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val chinesePhone = solarSkyState(40.7, -74.0, now)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(chinesePhone, solarSkyState(40.7, -74.0, now))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun solarNoonAndMidnightUseCoordinatesRatherThanFixedLocalHours() {
        assertTrue(solarSkyState(0.0, 0.0, now)!!.altitude > 85f)
        assertTrue(solarSkyState(0.0, 180.0, now)!!.altitude < -85f)
        assertTrue(solarSkyState(39.9, 116.4, now)!!.altitude < 0f)
    }

    @Test fun polarDayAndNightNeedNoInventedSunriseOrSunset() {
        val summer = solarSkyState(78.0, 15.0, Instant.parse("2026-06-21T00:00:00Z").toEpochMilli())!!
        val winter = solarSkyState(78.0, 15.0, Instant.parse("2026-12-21T12:00:00Z").toEpochMilli())!!
        assertTrue(summer.altitude > 0f)
        assertEquals(0f, winter.daylight, .001f)
    }

    @Test fun badCoordinatesFallBackInsteadOfPretendingToKnowTheSky() {
        for ((lat, lon) in listOf(null to 116.4, 39.9 to null, Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY,
            91.0 to 0.0, 0.0 to 181.0)) assertNull(solarSkyState(lat, lon, now))
        assertNotNull(solarSkyState(0.0, 0.0, now))
    }

    @Test fun omittedSolarStateRetainsTheOriginalLightAndPalette() {
        val data = WeatherData(current = CurrentWeather(condition = WeatherCondition.RAIN), daily = listOf(
            DailyWeather(dateMillis = now, sunrise = "06:30", sunset = "19:00")))
        val old = naturalLightState(data, false, 1140, now)
        assertNull(old.solar)
        assertEquals(old, naturalLightState(data, false, 1140, now, null))
        for (light in listOf(true, false)) assertEquals(naturalLightPalette(old, light),
            livingSkyPalette(old, light, naturalLightPalette(old, light)))
    }

    @Test fun weatherSuppressesIllustratedSunsetAndFogRemainsDistinct() {
        assertTrue(solarColourVisibility(state(WeatherCondition.RAIN, -2f)) < .2f)
        assertTrue(solarColourVisibility(state(WeatherCondition.OVERCAST, -2f)) < .3f)
        assertTrue(solarColourVisibility(state(WeatherCondition.FOG, -2f)) < .2f)
        assertEquals(0f, solarColourVisibility(state(WeatherCondition.UNKNOWN, -2f)), 0f)
        assertNotEquals(naturalLightPalette(state(WeatherCondition.RAIN, -2f), true),
            naturalLightPalette(state(WeatherCondition.FOG, -2f), true))
    }

    @Test fun dawnGoldenSunsetBlueAndNightHaveDistinctNativeColours() {
        val moments = listOf(-2f to true, 35f to false, 5f to false, -1.5f to false, -7f to false, -24f to false)
        for (light in listOf(true, false)) assertEquals(6, moments.map { (a, rising) ->
            naturalLightPalette(state(WeatherCondition.PARTLY_CLOUDY, a, rising), light)
        }.distinct().size)
    }

    @Test fun everyMinuteKeepsContinuousColourInsteadOfSwitchingAtClockHours() {
        fun largestDifference(a: Color, b: Color) = maxOf(kotlin.math.abs(a.red-b.red), kotlin.math.abs(a.green-b.green), kotlin.math.abs(a.blue-b.blue))
        for (light in listOf(true, false)) for (rising in listOf(true, false)) {
            val skies = (-240..400).map { naturalLightPalette(state(WeatherCondition.CLEAR, it / 10f, rising), light) }
            skies.zipWithNext().forEach { (a, b) ->
                assertTrue(largestDifference(a.top, b.top) < .015f)
                assertTrue(largestDifference(a.middle, b.middle) < .02f)
            }
        }
    }

    @Test fun allSolarPhasesKeepSmallReadingTextAboveFourPointFiveContrast() {
        for (condition in WeatherCondition.entries) for (light in listOf(true, false)) {
            val ink = if (light) PhosphorVistaLightPalette else PhosphorVistaDarkPalette
            for (altitude in -24..60) {
                val sky = naturalLightPalette(state(condition, altitude.toFloat()), light)
                for (bg in listOf(sky.top, sky.middle, sky.base))
                for (fg in listOf(ink.text, ink.textSecondary, ink.textTertiary, ink.mint, ink.cyan)) {
                    val ratio = (maxOf(bg.luminance(), fg.luminance()) + .05f) / (minOf(bg.luminance(), fg.luminance()) + .05f)
                    assertTrue("$condition light=$light altitude=$altitude contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }

    @Test fun azimuthBlendsAcrossNorthWithoutTakingALongTurn() {
        val halfway = SolarSkyState(1f, 359f, false).mix(SolarSkyState(3f, 1f, false), .5f)
        assertEquals(0f, halfway.azimuth, .001f)
        assertEquals(2f, halfway.altitude, .001f)
    }

    @Test fun volumetricCloudEdgesAndFogVeilsAlsoStayWithinTheReadingContrastEnvelope() {
        for (condition in WeatherCondition.entries.filterNot { it == WeatherCondition.UNKNOWN })
        for (light in listOf(true, false)) for (altitude in -24..60) {
            val sky = naturalLightPalette(state(condition, altitude.toFloat()), light)
            val ink = if (light) PhosphorVistaLightPalette else PhosphorVistaDarkPalette
            for (background in listOf(sky.cloudShadow, sky.cloudLit, sky.cloud))
            for (foreground in listOf(ink.text, ink.textSecondary, ink.textTertiary, ink.mint, ink.cyan)) {
                val ratio = (maxOf(background.luminance(), foreground.luminance()) + .05f) /
                    (minOf(background.luminance(), foreground.luminance()) + .05f)
                assertTrue("cloud $condition light=$light altitude=$altitude contrast=$ratio", ratio >= 4.5f)
            }
        }
    }

    @Test fun rainyAndStormyCloudEdgesLoseTheirSunsetWarmthAlongWithTheBackground() {
        fun warmth(condition: WeatherCondition): Float {
            val cloud = naturalLightPalette(state(condition, -1f), true).cloudLit
            return cloud.red - cloud.blue
        }
        assertTrue(warmth(WeatherCondition.CLEAR) > warmth(WeatherCondition.RAIN) * 3f)
        assertTrue(warmth(WeatherCondition.THUNDERSTORM) < warmth(WeatherCondition.RAIN))
        assertTrue(warmth(WeatherCondition.FOG) < warmth(WeatherCondition.RAIN))
    }
}
