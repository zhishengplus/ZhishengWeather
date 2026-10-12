package com.zhisheng.weather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zhisheng.weather.model.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class InitialLocationStorageTest {
    private val cities = stringPreferencesKey("cities")
    private val backup = stringPreferencesKey("cities_backup")
    private val selected = stringPreferencesKey("selected_key")
    private val located = stringPreferencesKey("located_city_key")
    private val completed = booleanPreferencesKey("initial_location_setup_complete")
    private val locationEnabled = booleanPreferencesKey("location_enabled")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(City.serializer())
    private val beijing = City("北京", "北京", 39.9, 116.41, "101010100")
    private val chosen = City("金昌", "甘肃", 38.52, 102.19, "chosen", isFavorite = true)

    @Test fun newStorageStartsWithExplicitEmptyCitiesAndNoSelection() = runBlocking {
        withStore(emptyPreferences()) { store ->
            CityRepository.ensureDefaultCity()

            assertEquals("[]", store.saved[cities])
            assertEquals("[]", store.saved[backup])
            assertNull(store.saved[selected])
            assertEquals(false, store.saved[completed])
            assertNull(CityRepository.selectedCity.first())
        }
    }

    @Test fun repeatedInitializationDoesNotCreateAPlaceholderOrOverwriteCompletion() = runBlocking {
        withStore(emptyPreferences()) { store ->
            CityRepository.ensureDefaultCity()
            store.saved = store.saved.toMutablePreferences().apply { this[completed] = true }
            CityRepository.ensureDefaultCity()

            assertEquals("[]", store.saved[cities])
            assertNull(store.saved[selected])
            assertEquals(true, store.saved[completed])
        }
    }

    @Test fun oldBeijingAndManuallyChosenFavoritesRemainByteIdentical() = runBlocking {
        for (city in listOf(beijing, chosen)) {
            val encoded = json.encodeToString(serializer, listOf(city))
            val original = mutablePreferencesOf(cities to encoded, backup to encoded,
                selected to city.locationKey, locationEnabled to false)
            withStore(original) { store ->
                CityRepository.ensureDefaultCity()

                assertEquals(original.asMap(), store.saved.asMap())
            }
        }
    }

    @Test fun oldEmptyListRemainsEmptyWithoutIntroducingAnOnboardingMarker() = runBlocking {
        val original = mutablePreferencesOf(cities to "[]", backup to "[]", selected to "")
        withStore(original) { store ->
            CityRepository.ensureDefaultCity()

            assertEquals(original.asMap(), store.saved.asMap())
        }
    }

    @Test fun initializationAfterPreviouslyHandledSetupKeepsCompletionAndSettings() = runBlocking {
        withStore(mutablePreferencesOf(completed to true, selected to "stale", locationEnabled to false)) { store ->
            CityRepository.ensureDefaultCity()

            assertEquals("[]", store.saved[cities])
            assertEquals("[]", store.saved[backup])
            assertNull(store.saved[selected])
            assertEquals(true, store.saved[completed])
            assertEquals(false, store.saved[locationEnabled])
        }
    }

    @Test fun cityAddedAfterInitialReadWinsOverInitialization() = runBlocking {
        withStore(emptyPreferences()) { store ->
            store.beforeNextWrite = { CityRepository.addCity(chosen) }
            CityRepository.ensureDefaultCity()

            assertEquals(listOf(chosen), json.decodeFromString(serializer, store.saved[cities]!!))
            assertEquals(store.saved[cities], store.saved[backup])
            assertEquals(chosen.locationKey, store.saved[selected])
            assertEquals(true, store.saved[completed])
        }
    }

    @Test fun locatedCityAddedAfterInitialReadWinsOverInitialization() = runBlocking {
        withStore(emptyPreferences()) { store ->
            store.beforeNextWrite = { CityRepository.addOrUpdateLocatedCity(beijing, expectedSelectedKey = null) }
            CityRepository.ensureDefaultCity()

            assertEquals(listOf(beijing), json.decodeFromString(serializer, store.saved[cities]!!))
            assertEquals(beijing.locationKey, store.saved[selected])
            assertEquals(beijing.locationKey, store.saved[located])
            assertEquals(true, store.saved[completed])
        }
    }

    @Test fun newAndExplicitlyPendingStorageRequireSetupWhileLegacyTablesDoNot() {
        val cases = listOf(
            emptyPreferences() to true,
            mutablePreferencesOf(cities to "[]") to false,
            mutablePreferencesOf(cities to json.encodeToString(serializer, listOf(beijing))) to false,
            mutablePreferencesOf(cities to json.encodeToString(serializer, listOf(chosen))) to false,
            mutablePreferencesOf(cities to "[]", completed to false) to true,
            mutablePreferencesOf(completed to false) to true,
            mutablePreferencesOf(completed to true) to false,
        )
        for ((preferences, required) in cases) {
            assertEquals(required, InitialLocationStoragePolicy.setupRequired(preferences))
        }
    }

    @Test fun setupFlowStaysPendingThroughInitializationAndStopsAfterExplicitCompletion() = runBlocking {
        withStore(emptyPreferences()) { store ->
            val required = CityRepository.initialLocationSetupRequired
            assertTrue(required.first())

            CityRepository.ensureDefaultCity()
            assertTrue(required.first())
            CityRepository.completeInitialLocationSetup()

            assertFalse(required.first())
            assertEquals("[]", store.saved[cities])
            assertNull(store.saved[selected])
        }
    }

    @Test fun completionOnlyChangesTheGuideMarkerAndPreservesCitiesAndSettings() = runBlocking {
        val encoded = json.encodeToString(serializer, listOf(chosen, beijing))
        val original = mutablePreferencesOf(cities to encoded, backup to encoded,
            selected to chosen.locationKey, located to beijing.locationKey,
            locationEnabled to false, completed to false)
        withStore(original) { store ->
            CityRepository.completeInitialLocationSetup()

            assertEquals(original.asMap().filterKeys { it != completed },
                store.saved.asMap().filterKeys { it != completed })
            assertEquals(true, store.saved[completed])
            assertFalse(CityRepository.initialLocationSetupRequired.first())
        }
    }

    @Test fun skipBeforeInitializationDoesNotAddCitiesOrLoseTheDecision() = runBlocking {
        withStore(emptyPreferences()) { store ->
            CityRepository.completeInitialLocationSetup()
            assertFalse(store.saved.contains(cities))
            assertFalse(store.saved.contains(backup))
            assertNull(store.saved[selected])

            CityRepository.ensureDefaultCity()

            assertEquals("[]", store.saved[cities])
            assertEquals(true, store.saved[completed])
            assertFalse(CityRepository.initialLocationSetupRequired.first())
        }
    }

    @Test fun completionQueuedBeforeInitializationIsNotOverwrittenByPendingMarker() = runBlocking {
        withStore(emptyPreferences()) { store ->
            store.beforeNextWrite = { CityRepository.completeInitialLocationSetup() }
            CityRepository.ensureDefaultCity()

            assertEquals("[]", store.saved[cities])
            assertEquals(true, store.saved[completed])
            assertFalse(CityRepository.initialLocationSetupRequired.first())
        }
    }

    @Test fun manuallyAddingACityCompletesSetupInTheSameWrite() = runBlocking {
        withStore(mutablePreferencesOf(cities to "[]", completed to false)) { store ->
            CityRepository.addCity(chosen)

            assertEquals(1, store.writes.size)
            val written = store.writes.single()
            assertEquals(listOf(chosen), json.decodeFromString(serializer, written[cities]!!))
            assertEquals(chosen.locationKey, written[selected])
            assertEquals(true, written[completed])
        }
    }

    @Test fun acceptingALocationWithNoSelectionCompletesSetupInTheSameWrite() = runBlocking {
        withStore(mutablePreferencesOf(cities to "[]", completed to false)) { store ->
            assertEquals(beijing, CityRepository.addOrUpdateLocatedCity(beijing, expectedSelectedKey = null))

            assertEquals(1, store.writes.size)
            val written = store.writes.single()
            assertEquals(beijing.locationKey, written[selected])
            assertEquals(beijing.locationKey, written[located])
            assertEquals(true, written[completed])
        }
    }

    @Test fun lateLocationRejectedAfterManualSelectionDoesNotCompletePendingSetup() = runBlocking {
        val encoded = json.encodeToString(serializer, listOf(chosen))
        val original = mutablePreferencesOf(cities to encoded, backup to encoded,
            selected to chosen.locationKey, completed to false)
        withStore(original) { store ->
            assertNull(CityRepository.addOrUpdateLocatedCity(beijing, expectedSelectedKey = null))

            assertEquals(original.asMap(), store.saved.asMap())
            assertTrue(CityRepository.initialLocationSetupRequired.first())
        }
    }

    @Test fun primaryCityCorruptionStillRestoresTheBackupWithoutFirstInstallChanges() = runBlocking {
        val encoded = json.encodeToString(serializer, listOf(chosen))
        withStore(mutablePreferencesOf(cities to "broken", backup to encoded,
            selected to chosen.locationKey)) { store ->
            CityRepository.ensureDefaultCity()

            assertEquals(encoded, store.saved[cities])
            assertEquals(encoded, store.saved[backup])
            assertEquals(chosen.locationKey, store.saved[selected])
            assertNull(store.saved[completed])
            assertFalse(CityRepository.initialLocationSetupRequired.first())
        }
    }

    @Test fun unrecoverableExistingCityTablesKeepThePreviousDefaultCityRecovery() = runBlocking {
        withStore(mutablePreferencesOf(cities to "broken", backup to "broken")) { store ->
            CityRepository.ensureDefaultCity()

            assertEquals(listOf(beijing), json.decodeFromString(serializer, store.saved[cities]!!))
            assertEquals(beijing.locationKey, store.saved[selected])
            assertEquals(true, store.saved[completed])
        }
    }

    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        var saved: Preferences = initial
        val writes = mutableListOf<Preferences>()
        var beforeNextWrite: (suspend () -> Unit)? = null
        override val data: Flow<Preferences> = flow { emit(saved) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val beforeWrite = beforeNextWrite
            beforeNextWrite = null
            beforeWrite?.invoke()
            return transform(saved).also { saved = it; writes.add(it) }
        }
    }

    private suspend fun withStore(initial: Preferences, action: suspend (MemoryStore) -> Unit) {
        val store = MemoryStore(initial)
        val field = CityRepository::class.java.getDeclaredField("store").apply { isAccessible = true }
        val previous = field.get(CityRepository)
        try {
            field.set(CityRepository, store)
            action(store)
        } finally {
            field.set(CityRepository, previous)
        }
    }
}
