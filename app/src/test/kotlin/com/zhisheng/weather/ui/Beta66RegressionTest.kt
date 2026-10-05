package com.zhisheng.weather.ui

import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Beta66RegressionTest {
    @Test fun ethnicPrefectureAliasesUseExactNormalizedNames() {
        val entry = BoundaryRepository.Entry(422800, "恩施土家族苗族自治州", 109.49, 30.27, 1, 1)
        val matcher = BoundaryMatcher(mapOf(entry.name to listOf(entry)), mapOf(entry.adcode to entry), emptyMap(), emptyMap())
        assertEquals(entry, matcher.resolve("恩施土家族苗族", null, null, null))
        assertEquals(entry, matcher.resolve("恩施土家族苗族州", null, null, null))
        assertNull(matcher.resolve("不存在的州", null, null, null))
    }
    @Test fun locatedAddressPrecedesFavoritesWithoutReorderingOtherCities() {
        fun city(key: String, favorite: Boolean = false) = City(key, "", 30.0, 110.0, key, isFavorite = favorite)
        val cities = listOf(city("a"), city("favorite", true), city("gps"), city("b"))
        assertEquals(listOf("gps", "favorite", "a", "b"), favoriteCitiesFirst(cities, "gps").map { it.locationKey })
        assertEquals(listOf("favorite", "a", "gps", "b"), favoriteCitiesFirst(cities, "removed").map { it.locationKey })
    }

    @Test fun vistaHonorsSavedOrderWhileKeepingWarningsAboveModules() {
        val order = HomeModule.entries.reversed()
        val blocks = vistaHomeBlocks(VistaHomeBlock.entries.toSet(), order)
        assertEquals(listOf(VistaHomeBlock.METADATA, VistaHomeBlock.HERO, VistaHomeBlock.ALERTS), blocks.take(3))
        assertEquals(VistaHomeBlock.TYPHOON, blocks[3])
        assertEquals(VistaHomeBlock.HOURLY, blocks.last())
        assertEquals(blocks.size, blocks.distinct().size)
        val withoutAir = vistaHomeBlocks(VistaHomeBlock.entries.toSet() - VistaHomeBlock.AQI, order)
        assertEquals(blocks.filterNot { it == VistaHomeBlock.AQI }, withoutAir)
    }

    @Test fun everyManualSourceRejectsMixedCacheAndSupplementRequests() {
        // 枳生天气源是唯一例外：它的身份就是"融合+完整性"，blockSources 记录参与源、允许补缺
        SourcePref.entries.filterNot { it == SourcePref.AUTO || it == SourcePref.ZHISHENG }.forEach { pref ->
            assertFalse(WeatherRepository.shouldSupplementWithOpenMeteo(pref))
            assertFalse(WeatherRepository.shouldFillMissingHourlyPrecip(pref))
            val name = when (pref) { SourcePref.OPEN_METEO -> "OPEN-METEO"; else -> pref.name }
            val pure = WeatherData(dataSource = name, blockSources = mapOf("hourly" to name))
            assertTrue(pref.matches(pure))
            assertFalse(pref.matches(pure.copy(blockSources = mapOf("extra" to "OTHER"))))
        }
    }

    @Test fun zhishengSourceAllowsSupplementsAndFusedBlockSources() {
        assertTrue(WeatherRepository.shouldSupplementWithOpenMeteo(SourcePref.ZHISHENG))
        assertTrue(WeatherRepository.shouldFillMissingHourlyPrecip(SourcePref.ZHISHENG))
        // 融合输出：dataSource=ZHISHENG、blockSources 是参与源名 → 仍算"与所选源相符"
        val fused = WeatherData(
            dataSource = "ZHISHENG",
            blockSources = mapOf("current" to "XIAOMI", "hourly" to "NMC", "daily" to "OPEN-METEO"),
        )
        assertTrue(SourcePref.ZHISHENG.matches(fused))
        // 身份只认 dataSource：别的源顶不了 ZHISHENG 的名
        assertFalse(SourcePref.ZHISHENG.matches(fused.copy(dataSource = "XIAOMI")))
    }

    @Test fun verificationExceptionIsDisplayedAndCannotSaveCredentials() = runBlocking {
        var saved = false
        val result = verifyThenPersist("candidate", verify = { throw IllegalStateException("test") },
            persist = { _, _ -> saved = true })
        assertFalse(result.ok)
        assertFalse(saved)
        assertEquals("验证未完成", result.title)
    }

    @Test fun cancellingVerificationNeverSavesOrConvertsCancellationToAnError() = runBlocking {
        var saved = false
        try {
            verifyThenPersist("candidate", verify = { throw CancellationException() }, persist = { _, _ -> saved = true })
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
        assertFalse(saved)
    }

    @Test fun flowingGlowHasGreaterStrengthAndDrift() {
        assertTrue(SoftGlowLevel.FLOWING.strength > SoftGlowLevel.SUBTLE.strength)
        assertTrue(SoftGlowLevel.FLOWING.drift > SoftGlowLevel.SUBTLE.drift)
    }
}
