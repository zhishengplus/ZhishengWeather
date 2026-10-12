package com.zhisheng.weather.ui

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherDiagnosticsTest {
    private val at = Instant.parse("2026-10-10T03:00:00Z").toEpochMilli()
    private val weather = WeatherData(current = CurrentWeather(temperature = 15.0), fetchedAt = at,
        updateTime = at, dataSource = "CAIYUN", blockSources = mapOf("daily" to "CAIYUN"),
        daily = listOf(DailyWeather(at, 21.0, 9.0, WeatherCondition.CLEAR)))
    private fun report(compatible: Boolean = true, cached: CachedWeather? = CachedWeather(weather, at)) =
        WeatherDiagnosticsReport(listOf("枳生天气 测试版", "Android 14 / API 34"),
            "SensitiveMaker SensitiveModel", "SensitiveTown · SensitiveStreet", SourcePref.CAIYUN, cached, compatible)

    @Test fun incompatibleHistoricalCacheCannotAppearAsCurrentSourceOrFieldCoverage() {
        val text = report(compatible = false).text()
        assertTrue(text.contains("当前配置暂无可用天气数据"))
        assertTrue(text.contains("历史缓存"))
        assertTrue(text.contains("2026-10-10T03:00:00Z"))
        assertFalse(text.contains("实际返回 CAIYUN"))
        assertFalse(text.contains("字段覆盖"))
        assertFalse(text.contains("daily=CAIYUN"))
    }

    @Test fun absentCacheExplicitlyReportsNoUsableData() {
        val text = report(cached = null, compatible = false).text()
        assertTrue(text.contains("当前配置暂无可用天气数据"))
        assertFalse(text.contains("字段覆盖"))
    }

    @Test fun compatibleCacheRetainsActualCoverageAndDatedAcquisition() {
        val text = report().text()
        assertTrue(text.contains("实际返回 CAIYUN"))
        assertTrue(text.contains("2026-10-10T03:00:00Z"))
        assertTrue(text.contains("实况 true · 小时 0 · 每日 1 · 短时降水 0"))
    }

    @Test fun defaultCopyHidesLocationAndDeviceWhileExplicitOptInCanIncludeThem() {
        val report = report()
        assertFalse(report.text().contains("SensitiveTown"))
        assertFalse(report.text().contains("SensitiveStreet"))
        assertFalse(report.text().contains("SensitiveMaker"))
        assertFalse(report.text().contains("SensitiveModel"))
        assertTrue(report.text().contains("Android 14"))
        assertTrue(report.text(includeLocationAndDevice = true).contains("SensitiveTown"))
        assertTrue(report.text(includeLocationAndDevice = true).contains("SensitiveModel"))
    }

    @Test fun reportNeverExportsRawProviderMessagesCredentialsOrCoordinates() {
        val unsafe = weather.copy(sourceDiagnostics = mapOf("forecast" to "token=private-token&lat=39.91&lon=116.41"),
            configurationFingerprint = "private-fingerprint",
            moduleEvidence = mapOf("forecast" to WeatherModuleEvidence("CAIYUN", at, at, 39.91, 116.41)))
        val text = report(cached = CachedWeather(unsafe, at)).text(includeLocationAndDevice = true)
        assertFalse(text.contains("private-token"))
        assertFalse(text.contains("private-fingerprint"))
        assertFalse(text.contains("39.91"))
        assertFalse(text.contains("116.41"))
    }

    @Test fun cancellingDiagnosticReadPropagatesCancellation() = runBlocking {
        try {
            readWeatherDiagnostics { throw CancellationException("dialog closed") }
            fail("Cancellation must not become a failed diagnostic report")
        } catch (_: CancellationException) { }
    }

    @Test fun ordinaryReadFailureBecomesRetryableFailureInsteadOfAReadyReport() = runBlocking {
        val state = readWeatherDiagnostics { error("unavailable store") }
        assertEquals(WeatherDiagnosticsState.Failed, state)
    }
}
