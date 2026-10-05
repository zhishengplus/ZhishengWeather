package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.sceneStamp
import com.zhisheng.weather.ui.home.tideSegments
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SceneWeatherReliabilityTest {
    @Test fun sceneCachesAreExcludedFromBothBackupModes() {
        val legacy = java.io.File("src/main/res/xml/backup_rules.xml").readText()
        val modern = java.io.File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains("datastore/scene_weather_cache.preferences_pb"))
        assertEquals(2, Regex("datastore/scene_weather_cache.preferences_pb").findAll(modern).count())
    }

    @Test fun removedWeatherProvidersAreNotPromisedInSetupCopy() {
        val source = java.io.File("src/main/kotlin/com/zhisheng/weather/ui/ProviderSetupDialog.kt").readText()
        assertFalse(source.contains("AMAP GEO+WX"))
        assertFalse(source.contains("BAIDU GEO+WX"))
        assertFalse(source.contains("逆地理编码和天气。两项都成功"))
        assertTrue(source.contains("不测试天气接口"))
    }

    private val hour = 3_600_000L
    private val time = Instant.parse("2026-09-04T16:00:00Z").toEpochMilli()
    private val city = City("海边", "", 22.55, 114.10, "geo:22.55,114.10")
    private fun h(at: Long) = SceneHour(at, 20.0, 60.0, 10.0, 0.0, 5.0, 20.0, 5.0, 3.0, 3.0, 3.0, 5.0, 10.0)

    @Test fun missingCloudsOrRainNeverProducePositiveScore() {
        val hours = (0..4).map { h(time + it * hour).copy(highCloud = null, precipitation = null) }
        assertNull(SceneWeatherCalculator.stars(hours, 22.55, 114.1, time))
        assertNull(SceneWeatherCalculator.glow(hours, listOf(time to GlowKind.DUSK), time))
    }

    @Test fun singleWetHourCannotBeAveragedIntoGoodPhotography() {
        val hours = (0..2).map { h(time + it * hour) }.toMutableList()
        hours[1] = hours[1].copy(precipitation = 2.0)
        val result = SceneWeatherCalculator.stars(hours, 22.55, 114.1, time)!!
        assertTrue(result.score <= 25)
        assertTrue(result.recommendation.startsWith("不建议"))
    }

    @Test fun missingDewOrWindCannotBecomeAnExcellentRating() {
        val result = SceneWeatherCalculator.stars(
            (0..2).map { h(time + it * hour).copy(dewPoint = null, windKmh = null, gustKmh = null) },
            22.55, 114.1, time,
        )!!
        assertTrue(result.score <= 60)
        assertTrue(result.reasons.any { "未知" in it })
    }

    @Test fun opaqueHighCloudCapsScoreEvenIfTotalCloudIsSmall() {
        val result = SceneWeatherCalculator.stars((0..2).map { h(time + it * hour).copy(highCloud = 100.0) },
            22.55, 114.1, time)!!
        assertTrue(result.score <= 25)
    }

    @Test fun twoEndpointsDoNotInventAFullTwoHourWindow() {
        assertNull(SceneWeatherCalculator.stars(listOf(h(time), h(time + hour)), 22.55, 114.1, time))
        assertNull(SceneWeatherCalculator.stars(listOf(h(time), h(time + hour), h(time + 3 * hour)), 22.55, 114.1, time))
    }

    @Test fun neverSelectsTomorrowNightAsTonight() {
        val future = (24..28).map { h(time + it * hour) }
        assertNull(SceneWeatherCalculator.stars(future, 22.55, 114.1, time))
    }

    @Test fun belowHorizonMoonHasNoPenalty() {
        val below = (0..23).map { time + it * hour }.first { MoonCalc.moonAltitudeDegrees(it, 22.55, 114.1) < -5 }
        assertEquals(0.0, SceneWeatherCalculator.moonPenalty(below, 22.55, 114.1), 0.0)
    }

    @Test fun tideDoesNotBridgeMissingSamplesOrTimeGaps() {
        val missing = listOf(MarinePoint(time, 0.0), MarinePoint(time + hour, null), MarinePoint(time + 2 * hour, 1.0))
        val result = SceneWeatherCalculator.tide(missing, time + 1)
        assertEquals(TideTrend.UNKNOWN, result.first)
        assertNull(result.second)
        assertTrue(tideSegments(missing).isEmpty())
        assertTrue(tideSegments(listOf(missing.first(), missing.last())).isEmpty())
    }

    @Test fun plateauNeedsRealReversalAndUsesPlateauMidpoint() {
        val rise = listOf(0.0, 1.0, 1.0, 2.0).mapIndexed { i, v -> MarinePoint(time + i * hour, v) }
        assertNull(SceneWeatherCalculator.tide(rise, time).second)
        val high = listOf(0.0, 1.0, 1.0, 0.0).mapIndexed { i, v -> MarinePoint(time + i * hour, v) }
        assertEquals(time + hour + hour / 2, SceneWeatherCalculator.tide(high, time).second?.timeMillis)
    }

    @Test fun parseRetainsNegativeTideAndTemperatureButRejectsSentinels() {
        val raw = marine(temperatures = "-2,-999999,-2,-2,-2,-2")
        val result = SceneResponseParser.marine(raw, city, time, time)!!.forecast!!
        assertEquals(-2.0, result.seaTemperatureC!!, 0.0)
        assertEquals(-0.5, result.points.first().seaLevelM!!, 0.0)
        assertNull(SceneResponseParser.marine(raw, city, time, time + hour)!!.forecast!!.seaTemperatureC)
    }

    @Test fun malformedTimestampCannotShiftParallelArrays() {
        val raw = marine().replace("${time / 1000 + 3600}", "null")
        val result = SceneResponseParser.marine(raw, city, time, time + 2 * hour)!!.forecast!!
        assertEquals(1.0, result.points.first { it.timeMillis == time + 2 * hour }.seaLevelM!!, 0.0)
        assertEquals(TideTrend.FALLING, result.trend)
    }

    @Test fun rejectWrongUnitsAndUnsupportedCoordinate() {
        assertNull(SceneResponseParser.marine(marine().replace("\"wave_height\":\"m\"", "\"wave_height\":\"ft\""), city, time, time))
        val inland = city.copy(latitude = 39.9, longitude = 116.4)
        assertNull(SceneResponseParser.marine(marine(), inland, time, time)!!.forecast)
        assertFalse(validSceneCoordinates(Double.NaN, 114.0))
    }

    @Test fun currentIsPrecedingHourNotNearestFutureHour() {
        val result = SceneResponseParser.marine(marine(), city, time, time + 50 * 60_000L)!!.forecast!!
        assertEquals(time, result.sampleTimeMillis)
        assertEquals(TideTrend.RISING, result.trend)
    }

    @Test fun dateAndDstAreRenderedInSceneTimezone() {
        val before = Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()
        val after = Instant.parse("2026-11-01T06:30:00Z").toEpochMilli()
        assertEquals("11-01 01:30", sceneStamp(before, "America/New_York"))
        assertEquals("11-01 01:30", sceneStamp(after, "America/New_York"))
        assertEquals("09-05 00:00", sceneStamp(time, "Asia/Shanghai"))
    }

    @Test fun manualParallelRefreshSharesRequestsAndCacheRecalculatesTime() = runBlocking {
        var clock = time
        var calls = 0
        val engine = SceneWeatherEngine(request = { calls++; delay(5); if ("marine?" in it) marine() else sky() }, now = { clock })
        coroutineScope { (1..3).map { async { engine.fetch(city, true, true, true) } }.awaitAll() }
        assertEquals(2, calls)
        clock += 30 * 60_000L
        val cached = engine.fetch(city, true, true, false)
        assertEquals(2, calls)
        assertEquals(time, cached.coast!!.updatedAtMillis)
        assertEquals(time, cached.coast!!.sampleTimeMillis)
        clock += hour
        assertEquals(time + hour, engine.fetch(city, false, true, false).coast!!.sampleTimeMillis)
        assertEquals(2, calls)
    }

    @Test fun perSourceExpiryNeverGetsResetByRefreshingOtherSource() = runBlocking {
        var clock = time
        var marineCalls = 0
        var skyCalls = 0
        val engine = SceneWeatherEngine(request = { if ("marine?" in it) { marineCalls++; marine() } else { skyCalls++; sky() } }, now = { clock })
        engine.fetch(city, true, true, false)
        clock += 46 * 60_000L
        engine.fetch(city, true, true, false)
        assertEquals(2, skyCalls)
        assertEquals(1, marineCalls)
        clock += 80 * 60_000L
        engine.fetch(city, true, true, false)
        assertEquals(2, marineCalls)
    }

    @Test fun failureUsesDatedPersistedCacheAndNeverCallsItFresh() = runBlocking {
        var disk = emptyMap<String, SceneCacheEntry>()
        val engine = SceneWeatherEngine(request = { if ("marine?" in it) marine() else sky() }, now = { time }, save = { disk = it })
        engine.fetch(city, true, true, true)
        val restored = SceneWeatherEngine(request = { null }, now = { time + 3 * hour }, load = { disk })
        val result = restored.fetch(city, true, true, false)
        assertTrue(result.sky!!.stale)
        assertTrue(result.coast!!.stale)
        assertEquals(time, result.coast!!.updatedAtMillis)
        val expired = SceneWeatherEngine(request = { null }, now = { time + 13 * hour }, load = { disk })
            .fetch(city, true, true, false)
        assertNull(expired.sky)
        assertNull(expired.coast)
    }

    @Test fun noKeyDisabledMeansNoRequestsAndFailuresAreThrottled() = runBlocking {
        var calls = 0
        val engine = SceneWeatherEngine(request = { calls++; null }, now = { time })
        engine.fetch(city, false, false, true)
        assertEquals(0, calls)
        engine.fetch(city, true, true, false)
        engine.fetch(city, true, true, false)
        assertEquals(2, calls)
    }

    @Test fun cancellationPropagatesWithoutCommittingFailedCache() = runBlocking {
        var writes = 0
        val engine = SceneWeatherEngine(request = { throw CancellationException() }, now = { time }, save = { writes++ })
        try { engine.fetch(city, true, true, true); fail("must cancel") } catch (_: CancellationException) { }
        assertEquals(0, writes)
    }

    @Test fun lruBoundCorruptCacheAndInvalidCoordinatesRemainSafe() = runBlocking {
        var disk = emptyMap<String, SceneCacheEntry>()
        val engine = SceneWeatherEngine(request = { marine() }, now = { time }, load = { error("corrupt") }, save = { disk = it })
        repeat(14) { engine.fetch(city.copy(longitude = 114.10 + it / 1000.0), false, true, false) }
        assertEquals(12, disk.size)
        val result = engine.fetch(city.copy(latitude = 100.0), true, true, true)
        assertNull(result.sky)
        assertNotNull(result.skyError)
    }

    @Test fun apiUsesExactCoordinatesAndKeepsMarineSeparate() {
        val precise = city.copy(latitude = 22.12345678, longitude = 114.12345678)
        assertTrue(SceneResponseParser.skyUrl(precise).contains("latitude=22.12345678"))
        assertTrue(SceneResponseParser.skyUrl(precise).contains("timeformat=unixtime"))
        assertTrue(SceneResponseParser.marineUrl(precise).contains("cell_selection=sea"))
    }

    private fun times() = (0..5).joinToString(",") { (time / 1000 + it * 3600).toString() }
    private fun repeated(v: Double) = (0..5).joinToString(",") { v.toString() }
    private fun sky(): String = """{"timezone":"Asia/Shanghai","utc_offset_seconds":28800,
        "hourly":{"time":[${times()}],"temperature_2m":[${repeated(20.0)}],
        "dew_point_2m":[${repeated(10.0)}],"precipitation":[${repeated(0.0)}],
        "visibility":[${repeated(20000.0)}],"cloud_cover":[${repeated(5.0)}],
        "cloud_cover_low":[${repeated(3.0)}],"cloud_cover_mid":[${repeated(3.0)}],
        "cloud_cover_high":[${repeated(3.0)}]} }"""
    private fun marine(temperatures: String = repeated(20.0)): String = """{
        "latitude":22.5,"longitude":114.1,"timezone":"Asia/Shanghai",
        "hourly_units":{"wave_height":"m","sea_level_height_msl":"m"},
        "hourly":{"time":[${times()}],"wave_height":[0.2,0.3,0.4,0.5,0.6,0.7],
        "sea_surface_temperature":[$temperatures],"sea_level_height_msl":[-0.5,0,1,0.5,-0.5,0]}}"""
}
