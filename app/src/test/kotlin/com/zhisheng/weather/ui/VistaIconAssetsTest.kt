package com.zhisheng.weather.ui

import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.conditionIconRes
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class VistaIconAssetsTest {
    private val makinFiles = listOf("clear_day", "clear_night", "cloudy", "cloudy_2_day", "cloudy_2_night", "dust", "fog",
        "hail", "haze", "rain_and_sleet_mix", "rain_and_snow_mix", "rainy_1", "rainy_2", "snowy_2", "thunderstorms", "wind")

    @Test fun everyConditionMapsToShippedMakinThingsIcon() {
        WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }.forEach { c ->
            assertNotNull("$c has no icon", conditionIconRes(c))
        }
        assertNull(conditionIconRes(WeatherCondition.UNKNOWN))
        assertNull(conditionIconRes(null))
        assertEquals(R.drawable.mk_clear_day, conditionIconRes(WeatherCondition.CLEAR))
        assertEquals(R.drawable.mk_clear_night, conditionIconRes(WeatherCondition.CLEAR_NIGHT))
        assertEquals(R.drawable.mk_cloudy_2_night, conditionIconRes(WeatherCondition.PARTLY_CLOUDY_NIGHT))
        assertEquals(R.drawable.mk_rainy_1, conditionIconRes(WeatherCondition.DRIZZLE))
        assertEquals(R.drawable.mk_rainy_2, conditionIconRes(WeatherCondition.RAIN))
    }

    @Test fun makinThingsRastersAre512LosslessWebpWithAlpha() {
        makinFiles.forEach { n ->
            val f = File("src/main/res/drawable-nodpi/mk_$n.webp")
            assertTrue("Missing $f", f.isFile)
            val b = f.readBytes()
            assertEquals("RIFF", String(b, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(b, 8, 4, Charsets.US_ASCII))
            // Lossless VP8L chunk: 14-bit width/height minus one, then alpha hint bit.
            assertEquals("VP8L", String(b, 12, 4, Charsets.US_ASCII))
            val bits = (b[21].toInt() and 0xff) or ((b[22].toInt() and 0xff) shl 8) or
                ((b[23].toInt() and 0xff) shl 16) or ((b[24].toInt() and 0xff) shl 24)
            assertEquals(512, (bits and 0x3fff) + 1)
            assertEquals(512, ((bits shr 14) and 0x3fff) + 1)
            assertTrue("$f must keep transparency", (bits shr 28) and 1 == 1)
        }
    }

    @Test fun replacedWeatherRastersNoLongerShip() {
        val res = File("src/main/res/drawable-nodpi").list().orEmpty()
        val leftover = res.filter { (it.startsWith("weather_") || it.startsWith("vista_day_") ||
            it.startsWith("vista_night_") || it.startsWith("classic_")) && it.endsWith(".png") &&
            !it.startsWith("weather_girl_") && it != "weather_cloud_sun.png" }
        assertTrue("Replaced weather rasters still shipped: $leftover", leftover.isEmpty())
        // Themed launcher monochrome layer still uses the Meteocons cloud-sun silhouette.
        assertTrue(File("src/main/assets/licenses/meteocons.txt").readText().contains("MIT License"))
        val makin = File("src/main/assets/licenses/makin-things-weather-icons.txt").readText()
        assertTrue(makin.contains("MIT License") && makin.contains("amCharts") && makin.contains("CC BY 4.0"))
    }
}
