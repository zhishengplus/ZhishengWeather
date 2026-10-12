package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WeatherModulePolicyTest {
    private val city = City("颍上县", "安徽省·阜阳市", 32.637, 116.264, "provider-original")
    private val primary = WeatherData(current = CurrentWeather(temperature = 20.0), dataSource = "XIAOMI",
        hourly = listOf(HourlyWeather(1000L, temperature = 21.0)), daily = listOf(DailyWeather(0, high = 22.0)),
        rainMinutes = listOf(MinutePrecip(1000, 1f)), alerts = listOf(AlertInfo("主源预警")),
        blockSources = mapOf("current" to "XIAOMI", "hourly" to "XIAOMI", "daily" to "XIAOMI"))

    @Test fun oldSettingsFollowPrimaryAndUnknownKeysDoNotCreateRequests() {
        assertTrue(ModuleSourcePolicy.decode(null).followsPrimary)
        assertTrue(ModuleSourcePolicy.decode("future=amap;current=retired").followsPrimary)
        val policy = ModuleSourcePolicy().with(WeatherModule.CURRENT, SourcePref.AMAP).with(WeatherModule.AIR, SourcePref.OPEN_METEO)
        assertEquals(policy, ModuleSourcePolicy.decode(policy.encode()))
        assertEquals(SourcePref.OPEN_METEO, policy.with(WeatherModule.CURRENT, null)[WeatherModule.AIR])
    }

    @Test fun capabilitiesDoNotPretendAmapHasHourlyOrOtherModules() {
        assertTrue(WeatherSourceCapabilities.supports(SourcePref.AMAP, WeatherModule.FORECAST))
        assertFalse(WeatherSourceCapabilities.map.getValue(SourcePref.AMAP).hourly)
        listOf(WeatherModule.AIR, WeatherModule.ALERTS, WeatherModule.MINUTELY, WeatherModule.INDICES).forEach {
            assertFalse(WeatherSourceCapabilities.supports(SourcePref.AMAP, it))
        }
        assertFalse(WeatherSourceCapabilities.supports(SourcePref.OPEN_METEO, WeatherModule.ALERTS))
        assertFalse(WeatherSourceCapabilities.supports(SourcePref.NMC, WeatherModule.MINUTELY))
    }

    @Test fun replacingDailyOnlyForecastClearsOldHourlyRatherThanRelabellingIt() {
        val base = stampModules(primary, city)
        val donor = stampModules(WeatherData(daily = listOf(DailyWeather(0, high = 31.0)), dataSource = "AMAP",
            blockSources = mapOf("daily" to "AMAP"), utcOffsetSeconds = 28800), city)
        val result = replaceModule(base, donor, WeatherModule.FORECAST)
        assertTrue(result.hourly.isEmpty())
        assertEquals(31.0, result.daily.single().high!!, 0.0)
        assertEquals(base.current, result.current)
        assertEquals(base.rainMinutes, result.rainMinutes)
        assertFalse(result.blockSources.containsKey("hourly"))
        assertEquals("AMAP", result.moduleEvidence.getValue("forecast").source)
        assertEquals(city.latitude, result.moduleEvidence.getValue("forecast").requestedLatitude!!, 0.0)
        assertEquals(28800, result.utcOffsetSeconds)
    }

    @Test fun failedOrMissingModuleKeepsPrimaryAndDoesNotChangeItsSource() {
        assertEquals(primary, replaceModule(primary, WeatherData(error = "no permission"), WeatherModule.CURRENT))
        assertEquals(primary, replaceModule(primary, WeatherData(dataSource = "AMAP"), WeatherModule.MINUTELY))
        assertEquals(primary, replaceModule(primary, WeatherData(dataSource = "BAIDU"), WeatherModule.ALERTS))
    }

    @Test fun acknowledgedEmptyAlertResponseClearsPreviousSourceAlerts() {
        val donor = stampModules(WeatherData(dataSource = "BAIDU", blockSources = mapOf("alerts" to "BAIDU")), city)
        val result = replaceModule(primary, donor, WeatherModule.ALERTS)
        assertTrue(result.alerts.isEmpty())
        assertEquals("BAIDU", result.moduleEvidence.getValue("alerts").source)
        assertEquals(primary.current, result.current)
    }

    @Test fun fingerprintChangesForEachRoutingOrCredentialChangeAndNeverContainsSecrets() {
        val policy = ModuleSourcePolicy()
        val old = weatherConfigurationFingerprint(SourcePref.XIAOMI, policy, true, listOf("secret-one"))
        assertEquals(64, old.length)
        assertFalse(old.contains("secret"))
        assertNotEquals(old, weatherConfigurationFingerprint(SourcePref.XIAOMI, policy, true, listOf("secret-two")))
        assertNotEquals(old, weatherConfigurationFingerprint(SourcePref.XIAOMI, policy.with(WeatherModule.ALERTS, SourcePref.NMC), true, listOf("secret-one")))
        assertNotEquals(old, weatherConfigurationFingerprint(SourcePref.XIAOMI, policy, false, listOf("secret-one")))
        assertNotEquals(old, weatherConfigurationFingerprint(SourcePref.AUTO, policy, true, listOf("secret-one")))
        assertNotEquals(weatherConfigurationFingerprint(SourcePref.AUTO, policy, true, listOf("a", "bc")),
            weatherConfigurationFingerprint(SourcePref.AUTO, policy, true, listOf("ab", "c")))
    }

    @Test fun configuredCacheAcceptsMixedModulesAndRejectsChangedConfiguration() {
        val policy = ModuleSourcePolicy().with(WeatherModule.FORECAST, SourcePref.AMAP)
        val data = primary.copy(configurationFingerprint = "request-snapshot", blockSources = mapOf("daily" to "AMAP"))
        assertTrue(cacheMatchesConfiguration(data, SourcePref.XIAOMI, policy, "request-snapshot", false))
        assertFalse(cacheMatchesConfiguration(data, SourcePref.XIAOMI, policy, "changed", false))
        assertFalse(cacheMatchesConfiguration(primary, SourcePref.XIAOMI, policy, "request-snapshot", true))
        assertFalse(cacheMatchesConfiguration(primary, SourcePref.XIAOMI, ModuleSourcePolicy(), "request-snapshot", false))
        assertTrue(cacheMatchesConfiguration(primary, SourcePref.XIAOMI, ModuleSourcePolicy(), "request-snapshot", true))
    }

    @Test fun oldCachedModelStillDecodesWithNewFieldsAbsent() {
        val restored = Json.decodeFromString(WeatherData.serializer(), """{"current":{"temperature":20.0},"dataSource":"XIAOMI"}""")
        assertNull(restored.configurationFingerprint)
        assertTrue(restored.moduleEvidence.isEmpty())
        assertEquals(20.0, restored.current!!.temperature!!, 0.0)
    }

    @Test fun publicationTimeNeverBorrowedFromCurrentObservationForOtherModules() {
        val result = stampModules(primary.copy(updateTime = 1000L, fetchedAt = 2000L), city)
        assertEquals(1000L, result.moduleEvidence.getValue("current").observationTime)
        assertNull(result.moduleEvidence.getValue("forecast").observationTime)
        assertEquals(2000L, result.moduleEvidence.getValue("forecast").fetchedAt)
    }

    @Test fun cityAdviceDoesNotRewriteDifferentCharactersOrIdentity() {
        assertTrue(citySearchAdvice("颖上").contains("颖与颍是不同的字"))
        assertTrue(citySearchAdvice("颖上").contains("颖上"))
        assertEquals("颍上县", city.name)
        assertEquals("provider-original", city.locationKey)
        assertFalse(validSearchCoordinates(Double.NaN, 116.0))
        assertFalse(validSearchCoordinates(91.0, 116.0))
        assertFalse(validSearchCoordinates(32.0, Double.POSITIVE_INFINITY))
        assertTrue(validSearchCoordinates(city.latitude, city.longitude))
    }
}
