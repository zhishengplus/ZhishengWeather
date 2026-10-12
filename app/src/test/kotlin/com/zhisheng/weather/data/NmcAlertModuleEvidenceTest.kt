package com.zhisheng.weather.data

import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherModuleEvidence
import org.junit.Assert.*
import org.junit.Test

class NmcAlertModuleEvidenceTest {
    private val city = City("颍上县", "安徽省·阜阳市", 32.637, 116.264, "original")
    private val ref = NmcSource.NmcCityRef("安徽省", "58419", "/publish/forecast/AAH/yingshang.html", "安徽省", "颍上县")
    private val primary = WeatherData(dataSource = "XIAOMI", alerts = listOf(AlertInfo("主源颍上县暴雨预警")),
        blockSources = mapOf("alerts" to "XIAOMI"))

    private fun oldNmcPayload(parsed: List<AlertInfo>, cachedEvidence: Boolean = false) = WeatherData(
        dataSource = "NMC", alerts = parsed, blockSources = mapOf("alerts" to "NMC"),
        moduleEvidence = if (cachedEvidence) mapOf("alerts" to WeatherModuleEvidence("NMC")) else emptyMap())

    @Test fun emptyNmcFirstPageCannotConfirmNoLocalAlertsOrClearPrimary() {
        val parsed = NmcSource.parseNmcAlarms(emptyList(), ref)
        // This is the actual payload shape emitted before the fix: network errors and
        // empty first pages both arrived as empty lists, incorrectly marked as confirmed.
        val donor = stampModules(oldNmcPayload(parsed), city)
        assertFalse(modulePresent(donor, WeatherModule.ALERTS))
        assertEquals(primary.alerts, replaceModule(primary, donor, WeatherModule.ALERTS).alerts)
    }

    @Test fun cachedErroneousEmptyEvidenceAlsoCannotClearPrimary() {
        val donor = oldNmcPayload(emptyList(), cachedEvidence = true)
        assertFalse(modulePresent(donor, WeatherModule.ALERTS))
        assertEquals(primary, replaceModule(primary, donor, WeatherModule.ALERTS))
    }

    @Test fun unmatchedProvinceFirstPageDoesNotConfirmNoLocalAlert() {
        val parsed = NmcSource.parseNmcAlarms(listOf(NmcApi.NmcAlarm(title = "广东省广州市气象台发布暴雨橙色预警信号")), ref)
        assertTrue(parsed.isEmpty())
        val donor = stampModules(oldNmcPayload(parsed), city)
        assertFalse(modulePresent(donor, WeatherModule.ALERTS))
        assertEquals(primary.alerts, replaceModule(primary, donor, WeatherModule.ALERTS).alerts)
    }

    @Test fun actualMatchedPositiveNmcAlertCanReplacePrimary() {
        val parsed = NmcSource.parseNmcAlarms(listOf(NmcApi.NmcAlarm(title = "安徽省颍上县气象台发布暴雨橙色预警信号")), ref)
        assertEquals(1, parsed.size)
        val donor = stampModules(oldNmcPayload(parsed), city)
        assertTrue(modulePresent(donor, WeatherModule.ALERTS))
        val result = replaceModule(primary, donor, WeatherModule.ALERTS)
        assertEquals(parsed, result.alerts)
        assertEquals("NMC", result.moduleEvidence.getValue("alerts").source)
    }

    @Test fun acknowledgedEmptyFromOtherProvidersStillClearsPrimary() {
        val donor = stampModules(WeatherData(dataSource = "QWEATHER", blockSources = mapOf("alerts" to "QWEATHER")), city)
        assertTrue(modulePresent(donor, WeatherModule.ALERTS))
        assertTrue(replaceModule(primary, donor, WeatherModule.ALERTS).alerts.isEmpty())
    }
}
