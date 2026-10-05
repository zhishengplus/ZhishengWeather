package com.zhisheng.weather.ui

import com.zhisheng.weather.model.DailyWeather
import org.junit.Assert.*
import org.junit.Test

class VistaForecastFactsTest {
    @Test fun legitimateZeroValuesRemainVisible() {
        val facts = vistaForecastFacts(DailyWeather(0, precipMm = 0.0, precipProbability = 0, humidity = 0.0, uvIndex = 0), "kmh").toMap()
        assertEquals("0.0 mm", facts["降水量"])
        assertEquals("0%", facts["降水概率"])
        assertEquals("0%", facts["湿度"])
        assertEquals("0", facts["紫外线指数"])
    }
    @Test fun missingOrInvalidFieldsAreNotShownAsReadings() {
        assertTrue(vistaForecastFacts(DailyWeather(0), "kmh").isEmpty())
        assertTrue(vistaForecastFacts(DailyWeather(0, precipMm = Double.NaN, humidity = -1.0, aqi = -1), "kmh").isEmpty())
    }
    @Test fun sunAndMoonDetailsArePreserved() {
        val facts = vistaForecastFacts(DailyWeather(0, sunrise = "06:30", sunset = "19:30", moonrise = "20:00", moonset = "05:00"), "kmh").toMap()
        assertEquals("19:30", facts["日落"])
        assertEquals("20:00", facts["月出"])
        assertEquals("05:00", facts["月落"])
    }
}
