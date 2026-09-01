package com.zhisheng.weather.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.zhisheng.weather.model.WeatherCondition

class QWeatherV1MappingTest {
    @Test
    fun decimalPrecipitationProbabilityParsesAndNormalizes() {
        val parsed = Json.decodeFromString<QwHourly>(
            """{"hours":[{"forecastTime":"2026-08-27T00:00+08:00","precipitation":{"probability":0.31}}]}""",
        )

        assertEquals(0.31, parsed.hours.single().precipitation?.probability ?: -1.0, 0.0001)
        assertEquals(31, WeatherRepository.normalizeQwProbability(0.31))
    }

    @Test
    fun paidHourlyFieldsKeepTheirOwnUnitsAndMeanings() {
        val hour = Json.decodeFromString<QwHourly>(
            """{
              "hours":[{
                "forecastTime":"2026-08-27T00:00+08:00",
                "feelsLike":{"value":31.2,"unit":"celsius"},
                "humidity":0.68,
                "windGust":{"value":42.0,"unit":"km/h"},
                "pressure":{"value":1007.0,"unit":"hPa"},
                "visibility":{"value":8.5,"unit":"km"},
                "dewPoint":{"value":23.0,"unit":"celsius"},
                "cloudCover":0.72,
                "uvIndex":6
              }]
            }""".trimIndent(),
        ).hours.single()

        assertEquals(31.2, hour.feelsLike?.value ?: -1.0, 0.0001)
        assertEquals(0.68, hour.humidity ?: -1.0, 0.0001)
        assertEquals(42.0, hour.windGust?.value ?: -1.0, 0.0001)
        assertEquals(1007.0, hour.pressure?.value ?: -1.0, 0.0001)
        assertEquals(8.5, hour.visibility?.value ?: -1.0, 0.0001)
        assertEquals(23.0, hour.dewPoint?.value ?: -1.0, 0.0001)
        assertEquals(0.72, hour.cloudCover ?: -1.0, 0.0001)
        assertEquals(6, hour.uvIndex)
    }

    @Test
    fun probabilityNormalizationAlsoAcceptsPercentShapedFallbacks() {
        assertEquals(31, WeatherRepository.normalizeQwProbability(31.0))
        assertNull(WeatherRepository.normalizeQwProbability(120.0))
        assertNull(WeatherRepository.normalizeQwProbability(-0.2))
    }

    @Test
    fun currentPrecipitationUsesIntensityInsteadOfHourlyAmount() {
        val parsed = Json.decodeFromString<QwCurrent>(
            """{"precipitation":{"amount":{"value":1.2,"unit":"mm"},"intensity":{"value":0.4,"unit":"mm/h"},"type":"rain"}}""",
        )

        assertEquals(0.4, WeatherRepository.qweatherCurrentPrecipRate(parsed.precipitation)!!, 0.0001)
    }

    @Test
    fun currentPrecipitationOmitsRateWhenOnlyAccumulatedAmountExists() {
        val parsed = Json.decodeFromString<QwCurrent>(
            """{"precipitation":{"amount":{"value":1.2,"unit":"mm"},"type":"rain"}}""",
        )

        assertNull(WeatherRepository.qweatherCurrentPrecipRate(parsed.precipitation))
    }

    @Test
    fun dailyForecastCombinesDayAndNightPrecipitation() {
        val parsed = Json.decodeFromString<QwDaily>(
            """{
                "days":[{
                  "forecastStartTime":"2026-08-27T00:00+08:00",
                  "daytime":{"condition":{"code":"100","text":"晴"},"precipitation":{"amount":{"value":0.2,"unit":"mm"},"probability":0.2}},
                  "nighttime":{"condition":{"code":"305","text":"小雨"},"precipitation":{"amount":{"value":1.3,"unit":"mm"},"probability":0.8}}
                }]
            }""".trimIndent(),
        ).days.single()

        assertEquals(1.5, WeatherRepository.qweatherDailyPrecipMm(parsed)!!, 0.0001)
        assertEquals(80, WeatherRepository.qweatherDailyProbability(parsed))
        assertEquals(WeatherCondition.DRIZZLE, WeatherRepository.qweatherDailyCondition(parsed))
        assertEquals("晴转小雨", WeatherRepository.qweatherDailyText(parsed))
    }

    @Test
    fun decimalQaqiParsesAndLocalStandardWins() {
        val air = Json.decodeFromString<QwAir>(
            """{"indexes":[{"code":"qaqi","aqi":1.4,"aqiDisplay":"1.4"},{"code":"us-epa","aqi":46,"aqiDisplay":"46"}]}""",
        )

        assertEquals(1.4, air.indexes.first().aqi!!, 0.0001)
        assertEquals("us-epa", WeatherRepository.preferredAirIndex(air.indexes)?.code)
    }

    @Test
    fun qweatherUnitsAreNormalizedWithoutGuessing() {
        assertEquals(36.0, WeatherRepository.speedKmh(QwVal(10.0, "m/s")) ?: -1.0, 0.0001)
        assertEquals(16.09344, WeatherRepository.speedKmh(QwVal(10.0, "mph")) ?: -1.0, 0.0001)
        assertEquals(1013.25, WeatherRepository.pressureHpa(QwVal(101_325.0, "Pa")) ?: -1.0, 0.0001)
        assertEquals(1013.25, WeatherRepository.pressureHpa(QwVal(101.325, "kPa")) ?: -1.0, 0.0001)
        assertNull(WeatherRepository.pressureHpa(QwVal(1013.0, "mystery")))
    }

    @Test
    fun airQualityKeepsTheSelectedStandardAndEachPollutantUnit() {
        val air = Json.decodeFromString<QwAir>(
            """{"pollutants":[
                {"code":"pm2p5","concentration":{"value":18,"unit":"ug/m3"}},
                {"code":"o3","concentration":{"value":31,"unit":"ppb"}},
                {"code":"co","concentration":{"value":0.4,"unit":"mg/m3"}}
            ]}""",
        )

        assertEquals("中国", WeatherRepository.qweatherAqiStandard("cn-mee"))
        assertEquals("美国", WeatherRepository.qweatherAqiStandard("us-epa"))
        assertEquals("μg/m³", WeatherRepository.qweatherPollutantUnits(air)["pm2p5"])
        assertEquals("ppb", WeatherRepository.qweatherPollutantUnits(air)["o3"])
        assertEquals("mg/m³", WeatherRepository.qweatherPollutantUnits(air)["co"])
    }
}
