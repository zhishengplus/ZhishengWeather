package com.zhisheng.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsModuleOrderPersistenceTest {
    @Test fun userCanSaveTheFormerDefaultOrder() = assertSavedOrder(
        "hourly,precip,daily,spacetime,telemetry,aqi,indices,yesterday,typhoon",
    )

    @Test fun userCanSaveTheEnumDeclarationOrder() = assertSavedOrder(
        "hourly,precip,spacetime,daily,telemetry,aqi,indices,yesterday,typhoon",
    )

    private fun assertSavedOrder(order: String) = runBlocking {
        // Only the storage boundary is replaced; exercise the real edit/encoding path.
        val store = object : DataStore<Preferences> {
            var saved: Preferences = emptyPreferences()
            override val data: Flow<Preferences> get() = flowOf(saved)
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(saved).also { saved = it }
        }
        val field = SettingsRepository::class.java.getDeclaredField("store").apply { isAccessible = true }
        val previous = field.get(SettingsRepository)
        try {
            field.set(SettingsRepository, store)
            val modules = order.split(',').map { key -> HomeModule.entries.first { it.key == key } }
            SettingsRepository.setModuleOrder(modules)
            val persisted = store.saved[stringPreferencesKey("home_module_order")]
            assertEquals("v2:$order", persisted)
            assertEquals(modules, HomeModule.orderFrom(persisted))
        } finally {
            field.set(SettingsRepository, previous)
        }
    }
}
