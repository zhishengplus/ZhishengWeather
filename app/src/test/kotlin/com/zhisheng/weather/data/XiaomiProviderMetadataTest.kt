package com.zhisheng.weather.data

import com.zhisheng.weather.model.WeatherCondition
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class XiaomiProviderMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixture = """{
      "sourceMaps":{"current":{"weather":"weatherbj(locationKey=101320101)"},
        "hourly":{"weather":"weatherbj(locationKey=101320101)"},
        "daily":{"weather":"weatherbj(locationKey=101320101)"}},
      "current":{"weather":"0","temperature":{"value":"30"},"humidity":{"unit":"%","value":"1"}},
      "forecastHourly":{"pubTime":"2026-11-02T17:00:00+08:00",
        "temperature":{"value":[30,29]},"weather":{"value":[0,1]}},
      "forecastDaily":{"pubTime":"2026-11-02T00:00:00+08:00",
        "temperature":{"value":[{"from":"30","to":"24"}]},"weather":{"value":[{"from":"0","to":"0"}]}},
      "yesterday":{"weatherStart":"0","weatherEnd":"0"}
    }"""

    @Test fun hkAndMacauAccuLocationKeysRespectWeatherbjFieldProvenance() {
        val response = json.decodeFromString<XiaomiForecastResult>(fixture)
        for (key in listOf("accu:1123655", "accu:60090")) {
            val actual = WeatherRepository.mapXiaomiToWeatherData(response, key)
            val expected = WeatherRepository.mapXiaomiToWeatherData(response.copy(sourceMaps = null), "weathercn:101320101")
            assertEquals(WeatherCondition.CLEAR, actual.current!!.condition)
            assertEquals(expected.current, actual.current)
            assertEquals(expected.hourly, actual.hourly)
            assertEquals(expected.daily, actual.daily)
            assertEquals(expected.yesterday, actual.yesterday)
        }
    }

    @Test fun eachBlockChoosesItsOwnCodeTableAndLegacyResponsesStillWork() {
        val response = json.decodeFromString<XiaomiForecastResult>(fixture.replace(
            "\"hourly\":{\"weather\":\"weatherbj(locationKey=101320101)\"}",
            "\"hourly\":{\"weather\":\"accu(locationKey=59209)\"}"))
        val mapped = WeatherRepository.mapXiaomiToWeatherData(response, "accu:1123655")
        assertEquals(WeatherCondition.CLEAR, mapped.current!!.condition)
        assertEquals(WeatherCondition.fromXiaomi("1", "accu:"), mapped.hourly[1].condition)
        assertEquals("accu:legacy", response.copy(sourceMaps = null).weatherCodeKey("current", "accu:legacy"))
        val malformed = json.decodeFromString<XiaomiForecastResult>("""{"sourceMaps":{"current":[]}}""")
        assertEquals("accu:legacy", malformed.weatherCodeKey("current", "accu:legacy"))
    }

    @Test fun explicitPercentHumidityPreservesZeroAndLowPercentages() {
        for (value in listOf("0", "0.5", "1", "62", "100")) {
            val data = WeatherRepository.mapXiaomiToWeatherData(XiaomiForecastResult(
                current = XiaomiCurrent(humidity = XiaomiUnitValue("%", value))))
            assertEquals(value.toDouble(), data.current!!.humidity!!, 0.0)
        }
    }
}
