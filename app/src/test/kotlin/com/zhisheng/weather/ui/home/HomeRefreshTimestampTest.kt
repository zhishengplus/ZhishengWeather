package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HomeRefreshTimestampTest {
    private val published = Instant.parse("2026-10-01T01:00:00Z").toEpochMilli()
    private val fetched = Instant.parse("2026-10-01T02:35:00Z").toEpochMilli()
    private val weather = WeatherData(current = CurrentWeather(temperature = 18.0), updateTime = published)

    @Test fun successfulRefreshShowsCompletionTimeInsteadOfUnchangedSourcePublication() {
        val result = WeatherRepository.markSuccessfulFetch(weather, fetched)
        val stamp = requireNotNull(homeWeatherUpdateStamp(result))
        assertEquals(fetched, stamp.timeMillis)
        assertEquals("更新于", stamp.label)
        assertEquals("UPD", stamp.code)
        assertEquals("10:35", vistaUpdateTime(stamp.timeMillis, fetched, 28800))
        assertEquals(published, result.updateTime)
    }

    @Test fun anotherSuccessfulPullMovesTimeEvenWhenWeatherAndSourceTimeAreIdentical() {
        val first = WeatherRepository.markSuccessfulFetch(weather, fetched)
        val second = WeatherRepository.markSuccessfulFetch(first, fetched + 120_000L)
        assertNotEquals(homeWeatherUpdateStamp(first)?.timeMillis, homeWeatherUpdateStamp(second)?.timeMillis)
        assertEquals(fetched + 120_000L, homeWeatherUpdateStamp(second)?.timeMillis)
        assertEquals(first.updateTime, second.updateTime)
    }

    @Test fun providerWithoutPublicationTimeStillReportsSuccessfulRefresh() {
        val result = WeatherRepository.markSuccessfulFetch(weather.copy(updateTime = null), fetched)
        val stamp = requireNotNull(homeWeatherUpdateStamp(result))
        assertEquals(fetched, stamp.timeMillis)
        assertEquals("更新于", stamp.label)
    }

    @Test fun legacyCacheWithoutFetchTimestampClearlyReportsSourcePublication() {
        val stamp = requireNotNull(homeWeatherUpdateStamp(weather))
        assertEquals(published, stamp.timeMillis)
        assertEquals("数据发布于", stamp.label)
        assertEquals("DATA", stamp.code)
    }

    @Test fun failedRefreshDoesNotAdvancePreviouslySuccessfulTimestamp() {
        val cached = weather.copy(fetchedAt = fetched)
        val failed = cached.copy(error = "请求超时")
        val result = WeatherRepository.markSuccessfulFetch(failed, fetched + 120_000L)
        assertEquals(fetched, homeWeatherUpdateStamp(result)?.timeMillis)
        assertEquals(published, result.updateTime)
    }

    @Test fun absentTimestampsStayUnknownInsteadOfUsingTheCurrentClock() {
        assertNull(homeWeatherUpdateStamp(WeatherData()))
        assertNull(homeWeatherUpdateStamp(null))
        val failed = WeatherRepository.markSuccessfulFetch(WeatherData(error = "请求超时"), fetched)
        assertNull(homeWeatherUpdateStamp(failed))
    }

    @Test fun sourceClockAheadDoesNotReplaceTheActualRefreshCompletionTime() {
        val data = weather.copy(updateTime = fetched + 120_000L, fetchedAt = fetched)
        assertEquals(fetched, homeWeatherUpdateStamp(data)?.timeMillis)
    }
}
