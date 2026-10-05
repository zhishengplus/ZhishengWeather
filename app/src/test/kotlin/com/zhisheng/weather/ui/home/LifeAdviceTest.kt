package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.DailyWeather
import org.junit.Assert.*
import org.junit.Test

class LifeAdviceTest {
    @Test fun missingForecastDoesNotBecomeGoodWeather() {
        assertEquals("暂无预报", forecastLifeAdvice("DRESS", null))
        assertEquals("缺少降水预报", forecastLifeAdvice("CAR WASH", DailyWeather(0)))
        assertEquals("缺少紫外线预报", forecastLifeAdvice("SPF", DailyWeather(0)))
    }
    @Test fun actualMetricKeysUseForecastEvidence() {
        assertEquals("预计有降水，建议缓洗", forecastLifeAdvice("CAR WASH", DailyWeather(0, precipProbability = 80)))
        assertEquals("加强遮阳，避开午间曝晒", forecastLifeAdvice("GLASSES", DailyWeather(0, uvIndex = 8)))
        assertEquals("长袖配薄外套", forecastLifeAdvice("DRESS", DailyWeather(0, high = 18.0)))
    }
    @Test fun healthIndicesAreNotInventedFromTemperature() {
        assertEquals("数据源暂未提供该日建议", forecastLifeAdvice("ALLERGY", DailyWeather(0, high = 25.0)))
        assertEquals("数据源暂未提供该日建议", forecastLifeAdvice("COLD", DailyWeather(0, high = 5.0)))
    }
}
