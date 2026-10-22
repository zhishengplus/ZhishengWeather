package com.zhisheng.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WeatherCacheStorageTest {
    private val now = 2_000_000_000_000L
    private fun weather(at: Long, temperature: Double) =
        WeatherData(current = CurrentWeather(temperature = temperature), fetchedAt = at)

    @Test fun actualCorruptPreferencesRecoverWithoutChangingUserStoresAndPersistFreshWeather() = runBlocking {
        val folder = Files.createTempDirectory("weather-cache-recovery").toFile()
        val file = File(folder, "weather_cache.preferences_pb")
        val settingsFile = File(folder, "settings.preferences_pb")
        var job = SupervisorJob()
        try {
            val settings = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { settingsFile })
            settings.edit { it[stringPreferencesKey("user_city_and_source")] = "selected-city|personal-source" }
            job.cancelAndJoin()
            val originalUserBytes = settingsFile.readBytes()
            // A truncated protobuf exercises the real Preferences serializer and corruption handler.
            file.writeBytes(byteArrayOf(0x0a, 0x7f))
            job = SupervisorJob()
            fun open() = PreferenceDataStoreFactory.create(corruptionHandler = weatherCacheCorruptionHandler(),
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val store = open()
            assertNull(WeatherCache.loadFromStore(store, "city"))
            assertTrue(store.data.first().asMap().isEmpty())
            val fresh = weather(now, 18.0)
            WeatherCache.saveToStore(store, "city", fresh, nowMillis = now)
            assertEquals(fresh, WeatherCache.loadFromStore(store, "city")?.data)
            job.cancelAndJoin()
            job = SupervisorJob()
            assertEquals(fresh, WeatherCache.loadFromStore(open(), "city")?.data)
            assertArrayEquals(originalUserBytes, settingsFile.readBytes())
        } finally {
            job.cancelAndJoin()
            file.delete()
            settingsFile.delete()
            folder.delete()
        }
    }

    @Test fun actualDataStoreReadIOExceptionIsOptionalAndDoesNotPreventNextNetworkResult() = runBlocking {
        val folder = Files.createTempDirectory("weather-cache-io").toFile()
        val file = File(folder, "weather-cache.data")
        val job = SupervisorJob()
        try {
            file.writeBytes(byteArrayOf(1))
            val store = DataStoreFactory.create(serializer = object : Serializer<Preferences> {
                override val defaultValue = emptyPreferences()
                override suspend fun readFrom(input: InputStream): Preferences = throw IOException("Injected cache read failure")
                override suspend fun writeTo(t: Preferences, output: OutputStream) = Unit
            }, scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            assertNull(WeatherCache.loadFromStore(store, "city"))
            val networkResult = weather(now, 22.0)
            var displayed: WeatherData? = null
            // The caller can proceed to its network result rather than fail at the optional read.
            if (WeatherCache.loadFromStore(store, "city") == null) displayed = networkResult
            assertEquals(networkResult, displayed)
        } finally {
            job.cancelAndJoin()
            file.delete()
            folder.delete()
        }
    }

    @Test fun actualDiskWritesKeepNormalOutOfOrderProtectionAndRecoverAfterClockRollback() = runBlocking {
        val folder = Files.createTempDirectory("weather-cache-order").toFile()
        val file = File(folder, "weather_cache.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(corruptionHandler = weatherCacheCorruptionHandler(),
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val initial = weather(now, 20.0)
            WeatherCache.saveToStore(store, "city", initial, nowMillis = now)
            WeatherCache.saveToStore(store, "city", weather(now - 30_000, 9.0), nowMillis = now)
            assertEquals(initial, WeatherCache.loadFromStore(store, "city")?.data)
            val correctedNow = now - 3_600_000L
            val corrected = weather(correctedNow, 10.0)
            WeatherCache.saveToStore(store, "city", corrected, nowMillis = correctedNow)
            assertEquals(corrected, WeatherCache.loadFromStore(store, "city")?.data)
            // A late result stamped before the clock correction cannot restore the old future data.
            WeatherCache.saveToStore(store, "city", initial, nowMillis = correctedNow + 1_000)
            assertEquals(corrected, WeatherCache.loadFromStore(store, "city")?.data)
        } finally {
            job.cancelAndJoin()
            file.delete()
            folder.delete()
        }
    }

    @Test fun cancellationFromAnOptionalCacheReadStillPropagates() = runBlocking {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw CancellationException("screen closed") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                error("Read-only test")
        }
        try {
            WeatherCache.loadFromStore(store, "city")
            fail("Cache recovery must never swallow cancellation")
        } catch (_: CancellationException) { }
    }
}
