package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.City
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zhisheng")

// 城市搜索 + 持久化（DataStore）
object CityRepository {

    private lateinit var store: DataStore<Preferences>

    private val KEY_CITIES = stringPreferencesKey("cities")
    private val KEY_CITIES_BACKUP = stringPreferencesKey("cities_backup")
    private val KEY_SELECTED = stringPreferencesKey("selected_key")

    private val json = Json { ignoreUnknownKeys = true }
    private val cityListSerializer = ListSerializer(City.serializer())

    fun init(context: Context) {
        store = context.applicationContext.dataStore
    }

    // 首装种子默认城市（零配置体验：装好即有天气，无需手动加城市）；
    // 以 KEY_CITIES 是否存在判定“首装”，用户删光城市后不会重种。
    // v0.0.4：主值 JSON 损坏时先尝试备份值；主备都坏则重种北京（此前会永久空列表且不重种）。
    suspend fun ensureDefaultCity() {
        val prefs = store.data.first()
        val state = prefs.decodeCities()
        when {
            state.corrupted -> {
                android.util.Log.e("ZhishengWeather", "城市数据主备均损坏，重新播种默认城市")
                addCity(defaultCity())
            }
            state.repairedFromBackup -> {
                // 主值损坏、备份可用：把备份回写主值，完成自愈
                android.util.Log.w("ZhishengWeather", "城市主数据损坏，已从备份恢复")
                store.edit { prefs ->
                    prefs[KEY_CITIES_BACKUP]?.let { prefs[KEY_CITIES] = it }
                }
            }
            !prefs.contains(KEY_CITIES) -> addCity(defaultCity())
        }
    }

    private fun defaultCity() = City(
        name = "北京",
        affiliation = "北京",
        latitude = 39.90,
        longitude = 116.41,
        locationKey = "101010100",
    )

    // 搜索城市：默认小米 → Open-Meteo；和风 Geo 仅开发者模式且前两源都空时才打。
    suspend fun search(query: String): List<City> {
        if (query.isBlank()) return emptyList()
        val xiaomi = try {
            XiaomiApi.instance.searchCity(query)
                .filter { it.status == 0 }
                .mapNotNull {
                    val lat = it.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = it.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                    val key = it.locationKey ?: return@mapNotNull null
                    City(
                        name = it.name ?: "",
                        affiliation = it.affiliation ?: "",
                        latitude = lat,
                        longitude = lon,
                        locationKey = key,
                    )
                }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
        if (xiaomi.isNotEmpty()) return xiaomi
        val om = OpenMeteoSource.searchCity(query)
        if (om.isNotEmpty()) return om
        if (!SettingsRepository.qweatherUnlocked()) return emptyList()
        val qw = try {
            QWeatherApi.service.cityLookup(query)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
        return qw?.location?.mapNotNull { loc ->
            val lat = loc.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = loc.lon?.toDoubleOrNull() ?: return@mapNotNull null
            City(
                name = loc.name ?: "",
                affiliation = listOf(loc.adm1, loc.adm2)
                    .filter { !it.isNullOrBlank() }.distinct().joinToString("·"),
                latitude = lat,
                longitude = lon,
                locationKey = loc.id ?: "$lon,$lat",
            )
        }.orEmpty()
    }

    // 已保存城市
    val cities: Flow<List<City>> by lazy {
        store.data.map { prefs -> favoriteCitiesFirst(prefs.cities()) }
    }

    val selectedCity: Flow<City?> by lazy {
        store.data.map { prefs ->
            val sel = prefs[KEY_SELECTED] ?: return@map null
            prefs.cities().firstOrNull { it.locationKey == sel }
        }
    }

    suspend fun addCity(city: City) {
        store.edit { prefs ->
            val list = prefs.cities().toMutableList()
            if (list.none { it.locationKey == city.locationKey }) {
                list.add(city)
            }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded // 双写备份（v0.0.4）
            prefs[KEY_SELECTED] = city.locationKey
        }
    }

    suspend fun addOrUpdateLocatedCity(city: City) {
        store.edit { prefs ->
            val list = prefs.cities().toMutableList()
            val existing = list.indexOfFirst { it.locationKey == city.locationKey }
            if (existing >= 0) {
                // 自动定位会刷新街道和坐标，但不能顺手清掉用户已经点亮的收藏。
                list[existing] = mergeLocatedCity(list[existing], city)
            } else {
                list.add(city)
            }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded
            prefs[KEY_SELECTED] = city.locationKey
        }
    }

    suspend fun removeCity(locationKey: String) {
        store.edit { prefs ->
            val list = prefs.cities().toMutableList()
            list.removeAll { it.locationKey == locationKey }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded // 双写备份（v0.0.4）
            if (prefs[KEY_SELECTED] == locationKey) {
                prefs[KEY_SELECTED] = favoriteCitiesFirst(list).firstOrNull()?.locationKey.orEmpty()
            }
        }
    }

    suspend fun toggleFavorite(locationKey: String) {
        store.edit { prefs ->
            val list = prefs.cities().map { city ->
                if (city.locationKey == locationKey) city.copy(isFavorite = !city.isFavorite) else city
            }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded
        }
    }

    suspend fun selectCity(locationKey: String) {
        store.edit { prefs ->
            prefs[KEY_SELECTED] = locationKey
        }
    }

    // 解码状态：list 为可用城市；repairedFromBackup 表示主值损坏、已从备份读出；
    // corrupted 表示主备均损坏（或不存在备份且主值损坏）
    private data class CitiesState(
        val list: List<City>,
        val repairedFromBackup: Boolean,
        val corrupted: Boolean,
    )

    private fun Preferences.decodeCities(): CitiesState {
        val raw = this[KEY_CITIES] ?: return CitiesState(emptyList(), false, false)
        val decoded = try {
            json.decodeFromString(cityListSerializer, raw)
        } catch (_: Exception) {
            null
        }
        if (decoded != null) return CitiesState(decoded, false, false)
        val backup = this[KEY_CITIES_BACKUP]
        if (backup != null) {
            return try {
                CitiesState(json.decodeFromString(cityListSerializer, backup), true, false)
            } catch (_: Exception) {
                CitiesState(emptyList(), false, true)
            }
        }
        return CitiesState(emptyList(), false, true)
    }

    private fun Preferences.cities(): List<City> = decodeCities().list
}

/** 收藏城市固定在前；两个分组内部保持用户原有次序，不暗中重排。 */
internal fun favoriteCitiesFirst(cities: List<City>): List<City> =
    cities.filter(City::isFavorite) + cities.filterNot(City::isFavorite)

internal fun mergeLocatedCity(existing: City, located: City): City =
    located.copy(isFavorite = existing.isFavorite)
