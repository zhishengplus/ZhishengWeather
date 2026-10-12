package com.zhisheng.weather.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.PlaceRegion
import com.zhisheng.weather.model.filterPlaceRegion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zhisheng")

// 城市搜索 + 持久化（DataStore）
object CityRepository {

    fun searchAdvice(query: String): String = citySearchAdvice(query)

    const val MAX_FAVORITES = 6

    private lateinit var store: DataStore<Preferences>
    private lateinit var appContext: Context

    private val KEY_CITIES = InitialLocationStoragePolicy.citiesKey
    private val KEY_CITIES_BACKUP = InitialLocationStoragePolicy.backupKey
    private val KEY_SELECTED = InitialLocationStoragePolicy.selectedKey
    private val KEY_LOCATED = stringPreferencesKey("located_city_key")

    private val json = Json { ignoreUnknownKeys = true }
    private val cityListSerializer = ListSerializer(City.serializer())

    fun init(context: Context) {
        appContext = context.applicationContext
        store = appContext.dataStore
    }

    // List/identity repairs, location and favorites can alter the selected city's
    // meaning. Conservatively invalidate preparation around every city edit.
    private suspend fun editCityContext(transform: suspend (MutablePreferences) -> Unit): Preferences =
        reminderDeliveryGate.changeContext { store.edit(transform) }

    // 首装显式保存空城市表，等待用户选择位置；旧用户删光城市后也不会重种。
    // 主值 JSON 损坏时仍先恢复备份；主备都坏才沿用既有北京自愈兜底。
    suspend fun ensureDefaultCity() {
        // 存储读取本身也可能抛（文件级损坏/IO 异常）——这里挂了会连初始化和自愈一起跳过，
        // 必须兜住并留日志，绝不能让异常悄悄打死初始化流程。
        val prefs = try {
            store.data.first()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ZhishengWeather", "城市存储读取失败，跳过初始化与自愈检查", e)
            return
        }
        val state = prefs.decodeCities()
        when {
            state.corrupted -> {
                android.util.Log.e("ZhishengWeather", "城市数据主备均损坏且无法抢救，重新播种默认城市")
                addCity(defaultCity())
            }
            state.repairedFromBackup -> {
                // 主值损坏、备份可用：把备份回写主值，完成自愈
                android.util.Log.w("ZhishengWeather", "城市主数据损坏，已从备份恢复")
                editCityContext { prefs ->
                    prefs[KEY_CITIES_BACKUP]?.let { prefs[KEY_CITIES] = it }
                }
            }
            !prefs.contains(KEY_CITIES) -> editCityContext {
                InitialLocationStoragePolicy.initializeIfAbsent(it)
            }
        }
        // Older versions allowed the moving location slot itself to be favorited.
        // Preserve that saved address and give location following its own identity.
        editCityContext {
            separateLocatedFavorite(it)
            deduplicateSavedCities(it)
        }
    }

    private fun defaultCity() = City(
        name = "北京",
        affiliation = "北京",
        latitude = 39.90,
        longitude = 116.41,
        locationKey = "101010100",
    )

    /** No network and no Key required, including when the system geocoder is absent. */
    suspend fun searchLocal(query: String): List<City> {
        if (query.isBlank()) return emptyList()
        val term = normalizePlaceTerm(query)
        val persisted = try { cities.first() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        val saved = persisted.filter {
            normalizePlaceTerm(it.displayName + it.affiliation).contains(term)
        }
        return mergePlaceSearchResults(saved, LocalPlaces.search(appContext, query))
    }

    suspend fun search(query: String): List<City> {
        val outcome = searchOnline(query, precise = false)
        outcome.failure?.let { if (outcome.cities.isEmpty()) throw PlaceServiceException(it) }
        return outcome.cities
    }

    internal suspend fun searchOnline(query: String, precise: Boolean, region: PlaceRegion = PlaceRegion.DOMESTIC): PlaceSearchOutcome {
        if (query.isBlank()) return PlaceSearchOutcome(emptyList())
        val snapshot = currentPlaceCredentials()
        val providers = if (precise) preciseProviders(query, snapshot, region) else cityProviders(query, snapshot, region)
        val scoped = providers.map { provider -> suspend { filterPlaceRegion(provider(), region, includeUnclassified = precise) } }
        return coordinatePlaceSearch(scoped, isCurrent = { currentPlaceCredentials() == snapshot })
    }

    private fun cityProviders(query: String, snapshot: PlaceCredentials, region: PlaceRegion): List<suspend () -> List<City>> {
        if (region == PlaceRegion.OVERSEAS) return listOf({ OpenMeteoSource.searchCity(query) })
        val providers = mutableListOf<suspend () -> List<City>>(
            { coroutineScope {
                val results = listOf("CN", "HK", "MO", "TW").map { code -> async {
                    try { OpenMeteoSource.searchCity(query, code) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } }.awaitAll()
                if (results.all { it == null }) throw java.io.IOException("Domestic city lookup unavailable")
                results.filterNotNull().flatten().filter { validSearchCoordinates(it.latitude, it.longitude) }
            } },
            {
                XiaomiApi.instance.searchCity(query).filter { it.status == 0 }.mapNotNull {
                    val lat = it.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = it.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                    if (!validSearchCoordinates(lat, lon)) return@mapNotNull null
                    val key = it.locationKey ?: return@mapNotNull null
                    if (it.name.isNullOrBlank()) return@mapNotNull null
                    City(it.name.orEmpty(), it.affiliation.orEmpty(), lat, lon, key,
                        countryCode = "CN".takeIf { key.matches(Regex("101[0-9]{6}")) })
                }
            },
        )
        if (snapshot.qweatherEnabled) providers += {
            val response = QWeatherApi.service.cityLookup(query, range = "cn")
            if (response.code != "200" && response.code != "404") throw java.io.IOException("City lookup unavailable")
            response.location.mapNotNull { loc ->
                val lat = loc.lat?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = loc.lon?.toDoubleOrNull() ?: return@mapNotNull null
                if (!validSearchCoordinates(lat, lon) || loc.name.isNullOrBlank()) return@mapNotNull null
                City(loc.name.orEmpty(), listOf(loc.adm1, loc.adm2).filter { !it.isNullOrBlank() }.distinct().joinToString("·"),
                    lat, lon, loc.id ?: "$lon,$lat", countryCode = "CN", countryName = loc.country)
            }
        }
        return providers
    }

    /**
     * 搜索任意地点而不是城市中心。Android Geocoder 由手机系统提供，普通用户无需配置 Key；
     * 返回的完整坐标直接进入各天气源，locationKey 只负责稳定保存与去重。
     */
    suspend fun searchPreciseAddress(query: String): List<City> {
        val outcome = searchOnline(query, precise = true)
        outcome.failure?.let { if (outcome.cities.isEmpty()) throw PlaceServiceException(it) }
        return outcome.cities
    }

    private fun preciseProviders(query: String, snapshot: PlaceCredentials, region: PlaceRegion): List<suspend () -> List<City>> {
        val keyword = query.trim()
        if (keyword.length < 2) return emptyList()
        val providers = mutableListOf<suspend () -> List<City>>()
        if (region == PlaceRegion.DOMESTIC && snapshot.amap.isNotBlank()) providers += {
            AmapApi.searchPlaces(snapshot.amap, keyword).mapNotNull { place ->
                preciseAddressCity(place.district, place.city, place.province, place.name, place.address, null,
                    place.latitude, place.longitude, source = "amap", sourceId = place.sourceId)
            }
        }
        providers += {
            if (!Geocoder.isPresent()) throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
            forwardGeocode(keyword, maxResults = 8).mapNotNull { address ->
            preciseAddressCity(
                district = address.subLocality ?: address.subAdminArea,
                city = address.locality,
                province = address.adminArea,
                featureName = address.featureName,
                thoroughfare = address.thoroughfare,
                premises = address.premises,
                latitude = address.latitude,
                longitude = address.longitude,
                source = "system",
            )?.copy(countryCode = address.countryCode, countryName = address.countryName)
            }
        }
        if (region == PlaceRegion.DOMESTIC && snapshot.baidu.isNotBlank()) providers += {
            BaiduApi.searchPlaces(snapshot.baidu, keyword).mapNotNull { place ->
                preciseAddressCity(place.district, place.city, place.province, place.name, place.address, null,
                    place.latitude, place.longitude, source = "baidu")
            }
        }
        return providers
    }

    private suspend fun forwardGeocode(query: String, maxResults: Int): List<Address> {
        val geocoder = Geocoder(appContext, java.util.Locale.SIMPLIFIED_CHINESE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, maxResults, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resumeWithException(PlaceServiceException(PlaceFailure.UNAVAILABLE))
                    }
                })
            }
        } else {
            geocoderCall(strict = true) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, maxResults)
            }.orEmpty()
        }
    }

    // 已保存城市
    val initialLocationSetupRequired: Flow<Boolean>
        get() = store.data.map { InitialLocationStoragePolicy.setupRequired(it) }

    /** Choosing a manual route or skipping the guide only records that choice. */
    suspend fun completeInitialLocationSetup() {
        store.edit { it[InitialLocationStoragePolicy.completedKey] = true }
    }

    val locatedCityKey: Flow<String?> by lazy { store.data.map { it[KEY_LOCATED] } }

    val cities: Flow<List<City>> by lazy {
        store.data.map { prefs -> favoriteCitiesFirst(prefs.cities(), prefs[KEY_LOCATED]) }
    }

    val selectedCity: Flow<City?> by lazy {
        store.data.map { prefs ->
            val sel = prefs[KEY_SELECTED] ?: return@map null
            prefs.cities().firstOrNull { it.locationKey == sel }
        }
    }

    /** 给离线缓存做键淘汰；返回原始顺序，不改变城市列表。 */
    internal suspend fun savedCities(): List<City> = store.data.first().cities()

    suspend fun addCity(city: City): City {
        var saved = city
        editCityContext { prefs ->
            val list = prefs.cities().toMutableList()
            // Search providers can give the same place different IDs. Reuse the
            // saved record, including its favorite and fixed coordinates.
            saved = list.firstOrNull { it.locationKey == city.locationKey }
                ?: list.firstOrNull {
                    it.locationKey != prefs[KEY_LOCATED] && sameSavedPlace(it, city)
                } ?: city
            if (list.none { it.locationKey == saved.locationKey }) {
                list.add(city)
            }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded // 双写备份（v0.0.4）
            prefs[KEY_SELECTED] = saved.locationKey
            prefs[InitialLocationStoragePolicy.completedKey] = true
        }
        return saved
    }

    suspend fun addOrUpdateLocatedCity(city: City, expectedSelectedKey: String?, expectedPlaceRevision: Long? = null, beforePublish: (City) -> Unit = {}): City? {
        var applied: City? = null
        editCityContext { prefs ->
            // Check within the transaction: a queued user selection wins over a late fix.
            if (!shouldApplyLocation(expectedSelectedKey, prefs[KEY_SELECTED]) ||
                (expectedPlaceRevision != null && PlaceConfigurationRevision.current() != expectedPlaceRevision)) return@editCityContext
            val list = replaceLocatedCity(prefs.cities(), prefs[KEY_LOCATED], city)
            val located = list.first()
            prefs[KEY_LOCATED] = located.locationKey
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded
            prefs[KEY_SELECTED] = located.locationKey
            prefs[InitialLocationStoragePolicy.completedKey] = true
            beforePublish(located)
            applied = located
        }
        return applied
    }

    /** Enrichment updates the same sample only; weather coordinates and favorites remain unchanged. */
    internal suspend fun updateLocatedLabel(enriched: City, expectedPlaceRevision: Long? = null): City? {
        var applied: City? = null
        store.edit { prefs ->
            if (prefs[KEY_SELECTED] != enriched.locationKey || prefs[KEY_LOCATED] != enriched.locationKey ||
                (expectedPlaceRevision != null && PlaceConfigurationRevision.current() != expectedPlaceRevision)) return@edit
            val list = prefs.cities().map { saved ->
                if (saved.locationKey == enriched.locationKey) updateMeasuredLabel(saved, enriched)?.also { applied = it } ?: saved
                else saved
            }
            if (applied != null) {
                val encoded = json.encodeToString(cityListSerializer, list)
                prefs[KEY_CITIES] = encoded
                prefs[KEY_CITIES_BACKUP] = encoded
            }
        }
        return applied
    }

    suspend fun removeCity(locationKey: String) {
        editCityContext { prefs ->
            val list = prefs.cities().toMutableList()
            list.removeAll { it.locationKey == locationKey }
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded // 双写备份（v0.0.4）
            if (prefs[KEY_SELECTED] == locationKey) {
                prefs[KEY_SELECTED] = favoriteCitiesFirst(list, prefs[KEY_LOCATED]).firstOrNull()?.locationKey.orEmpty()
            }
        }
    }

    suspend fun toggleFavorite(locationKey: String): FavoriteToggleResult {
        var result = FavoriteToggleResult.NOT_FOUND
        editCityContext { prefs ->
            val toggled = toggleFavoriteIn(prefs.cities(), locationKey)
            result = toggled.result
            val list = toggled.cities
            val encoded = json.encodeToString(cityListSerializer, list)
            prefs[KEY_CITIES] = encoded
            prefs[KEY_CITIES_BACKUP] = encoded
            if (result == FavoriteToggleResult.FAVORITED) separateLocatedFavorite(prefs)
        }
        return result
    }

    private fun separateLocatedFavorite(prefs: MutablePreferences) {
        val locatedKey = prefs[KEY_LOCATED] ?: return
        val cities = prefs.cities()
        val located = cities.firstOrNull { it.locationKey == locatedKey && it.isFavorite } ?: return
        val separated = replaceLocatedCity(cities, locatedKey, located.copy(isFavorite = false))
        prefs[KEY_LOCATED] = separated.first().locationKey
        val encoded = json.encodeToString(cityListSerializer, separated)
        prefs[KEY_CITIES] = encoded
        prefs[KEY_CITIES_BACKUP] = encoded
        // KEY_SELECTED stays on the saved favorite; favoriting must not switch cities.
    }

    private fun deduplicateSavedCities(prefs: MutablePreferences) {
        val original = prefs.cities()
        val deduplicated = deduplicateCityRecords(original, prefs[KEY_LOCATED], prefs[KEY_SELECTED])
        if (deduplicated.cities == original) return
        val encoded = json.encodeToString(cityListSerializer, deduplicated.cities)
        prefs[KEY_CITIES] = encoded
        prefs[KEY_CITIES_BACKUP] = encoded
        prefs[KEY_SELECTED]?.let { selected ->
            deduplicated.canonicalKeys[selected]?.let { prefs[KEY_SELECTED] = it }
        }
    }

    suspend fun selectCity(locationKey: String) {
        editCityContext { prefs ->
            prefs[KEY_SELECTED] = locationKey
        }
    }

    /** Restore the saved location without requesting GPS or replacing any saved city. */
    suspend fun restoreLocatedCity(expectedSelectedKey: String?, expectedPlaceRevision: Long? = null, beforePublish: (City) -> Unit = {}): City? {
        var restored: City? = null
        editCityContext { prefs ->
            val cities = prefs.cities()
            val selectedKey = cities.firstOrNull { it.locationKey == prefs[KEY_SELECTED] }?.locationKey
            // A user selection queued during startup takes precedence over restoration.
            if (!shouldApplyLocation(expectedSelectedKey, selectedKey)) return@editCityContext
            val located = cities.firstOrNull { it.locationKey == prefs[KEY_LOCATED] } ?: return@editCityContext
            prefs[KEY_SELECTED] = located.locationKey
            restored = located
        }
        return restored
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
        } catch (e: Exception) {
            // 只记长度与异常，不打印内容（坐标属隐私）
            android.util.Log.w("ZhishengWeather", "城市主数据解码失败（len=${raw.length}）：${e.message}")
            null
        }
        if (decoded != null) return CitiesState(decoded, false, false)
        val backup = this[KEY_CITIES_BACKUP]
        if (backup != null) {
            val backupDecoded = try {
                json.decodeFromString(cityListSerializer, backup)
            } catch (e: Exception) {
                android.util.Log.w("ZhishengWeather", "城市备份数据解码失败（len=${backup.length}）：${e.message}")
                null
            }
            if (backupDecoded != null) return CitiesState(backupDecoded, true, false)
        }
        // 主备严格解码双败：尝试抢救解码（应对序列化结构漂移），救回核心字段就不让用户重选。
        // 抢救结果按正常列表返回（corrupted=false）：后续任意一次城市写入会自然回写主备两键完成自愈。
        val salvaged = salvageCities(raw) ?: backup?.let { salvageCities(it) }
        if (!salvaged.isNullOrEmpty()) {
            android.util.Log.w("ZhishengWeather", "城市数据严格解码双败，抢救解码恢复 ${salvaged.size} 座城市")
            return CitiesState(salvaged, false, false)
        }
        return CitiesState(emptyList(), false, true)
    }

    // 抢救解码：严格解码双败时按字段逐一容错抽取，只取渲染与定位必需的核心字段，救不出的城市跳过。
    private fun salvageCities(raw: String): List<City>? {
        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return null
        val salvaged = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String): String? = (obj[key] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotBlank)
            val name = text("name") ?: return@mapNotNull null
            val locationKey = text("locationKey") ?: return@mapNotNull null
            val latitude = text("latitude")?.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = text("longitude")?.toDoubleOrNull() ?: return@mapNotNull null
            City(
                name = name,
                affiliation = text("affiliation").orEmpty(),
                latitude = latitude,
                longitude = longitude,
                locationKey = locationKey,
                street = text("street"),
                isFavorite = text("isFavorite") == "true",
                weatherLocationKey = text("weatherLocationKey"),
            )
        }
        return salvaged.ifEmpty { null }
    }

    private fun Preferences.cities(): List<City> = decodeCities().list

}

internal fun validSearchCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun citySearchAdvice(query: String): String =
    if ('颖' in query || '颍' in query) "未找到「${query.trim()}」。颖与颍是不同的字，请核对地名并补充省、市或区县；也可切换地点搜索。"
    else "未找到「${query.trim()}」。请补充省、市或区县名，或切换地点搜索。"

/** 最近定位地址置顶，其余收藏和普通城市各自保持原有次序。 */
internal fun favoriteCitiesFirst(cities: List<City>, locatedKey: String? = null): List<City> =
    cities.sortedBy { city ->
        when {
            city.locationKey == locatedKey -> 0
            city.isFavorite -> 1
            else -> 2
        }
    }

internal fun mergeLocatedCity(existing: City, located: City): City =
    located.copy(isFavorite = existing.isFavorite)

/** Match saved places conservatively; a city name alone is never an identity. */
internal fun sameSavedPlace(a: City, b: City): Boolean {
    if (a.locationKey == b.locationKey) return true
    if (a.isPreciseLocation != b.isPreciseLocation || a.name.isBlank() || a.name.trim() != b.name.trim()) return false
    fun region(city: City) = city.affiliation.split('·', ',', '，')
        .map(String::trim).filter { it.isNotBlank() && it != "中国" && it != city.name.trim() }.toSet()
    if (region(a) != region(b)) return false
    if (a.street.orEmpty().trim() != b.street.orEmpty().trim()) return false
    val distance = distanceKm(a.latitude, a.longitude, b.latitude, b.longitude)
    fun hasCoordinateIdentity(city: City) = city.isPreciseLocation ||
        city.locationKey.startsWith("geo-city:") || city.locationKey.startsWith("located:")
    // A favorite is a pinned coordinate even with only approximate permission.
    // Keep distinct saved coordinates; only ordinary city-center searches use 1 km.
    return if (a.isFavorite || b.isFavorite || hasCoordinateIdentity(a) || hasCoordinateIdentity(b)) {
        distance <= 0.001
    } else {
        distance <= 1.0
    }
}

internal data class DeduplicatedCities(val cities: List<City>, val canonicalKeys: Map<String, String>)

internal fun deduplicateCityRecords(cities: List<City>, locatedKey: String?, selectedKey: String?): DeduplicatedCities {
    val groups = mutableListOf<MutableList<City>>()
    cities.forEach { city ->
        val group = groups.firstOrNull { members ->
            // Require every member to agree, so a chain of nearby records cannot
            // accidentally collapse two genuinely different places.
            members.all { saved ->
                saved.locationKey == city.locationKey ||
                    (saved.locationKey != locatedKey && city.locationKey != locatedKey && sameSavedPlace(saved, city))
            }
        }
        if (group == null) groups.add(mutableListOf(city)) else group.add(city)
    }
    val canonicalKeys = mutableMapOf<String, String>()
    val result = groups.map { members ->
        val saved = members.firstOrNull(City::isFavorite)
            ?: members.firstOrNull { it.locationKey == selectedKey }
            ?: members.first()
        members.forEach { canonicalKeys[it.locationKey] = saved.locationKey }
        saved
    }
    return DeduplicatedCities(result, canonicalKeys)
}

enum class FavoriteToggleResult {
    FAVORITED,
    UNFAVORITED,
    LIMIT_REACHED,
    NOT_FOUND,
}

internal data class FavoriteToggle(
    val cities: List<City>,
    val result: FavoriteToggleResult,
)

internal fun toggleFavoriteIn(cities: List<City>, locationKey: String): FavoriteToggle {
    val target = cities.firstOrNull { it.locationKey == locationKey }
        ?: return FavoriteToggle(cities, FavoriteToggleResult.NOT_FOUND)
    if (!target.isFavorite && cities.count(City::isFavorite) >= CityRepository.MAX_FAVORITES) {
        return FavoriteToggle(cities, FavoriteToggleResult.LIMIT_REACHED)
    }
    return FavoriteToggle(
        cities = cities.map { city ->
            if (city.locationKey == locationKey) city.copy(isFavorite = !city.isFavorite) else city
        },
        result = if (target.isFavorite) FavoriteToggleResult.UNFAVORITED else FavoriteToggleResult.FAVORITED,
    )
}

internal fun shouldRefreshSameLocatedAddress(existing: City, located: City): Boolean =
    existing.isPreciseLocation && located.isPreciseLocation && existing.name == located.name &&
        distanceKm(existing.latitude, existing.longitude, located.latitude, located.longitude) <= 0.25

internal fun nearestLocatedCityIndex(cities: List<City>, located: City): Int = cities.indices
    .filter { shouldRefreshSameLocatedAddress(cities[it], located) }
    .minByOrNull { index ->
        val existing = cities[index]
        distanceKm(existing.latitude, existing.longitude, located.latitude, located.longitude)
    } ?: -1

/** 把系统地址结果收敛成项目统一的精确地址模型；数据字段保持纯函数以便回归测试。 */
internal fun preciseAddressCity(
    district: String?,
    city: String?,
    province: String?,
    featureName: String?,
    thoroughfare: String?,
    premises: String?,
    latitude: Double,
    longitude: Double,
    source: String? = null,
    sourceId: String? = null,
): City? {
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }
    fun clean(value: String?): String? = value?.trim()?.takeIf(String::isNotBlank)
    val name = sequenceOf(district, city, province, featureName).mapNotNull(::clean).firstOrNull()
        ?: return null
    val affiliation = listOf(province, city)
        .mapNotNull(::clean)
        .filterNot { it == name }
        .distinct()
        .joinToString("·")
    val street = listOf(featureName, premises, thoroughfare)
        .mapNotNull(::clean)
        .filterNot { it == name || it == city || it == province || it == district }
        .distinct()
        .take(2)
        .joinToString("·")
        .ifBlank { null }
    return City(
        name = name,
        affiliation = affiliation,
        latitude = latitude,
        longitude = longitude,
        locationKey = if (source == null) preciseLocationKey(latitude, longitude)
            else placeIdentity(source, sourceId, name, street, latitude, longitude),
        street = street,
        coordinateKind = com.zhisheng.weather.model.CoordinateKind.PLACE,
        addressSource = source,
    )
}

internal fun placeIdentity(source: String, sourceId: String?, name: String, street: String?, latitude: Double, longitude: Double): String {
    val identity = sourceId?.takeIf(String::isNotBlank)
        ?: java.lang.String.format(java.util.Locale.US, "%s|%s|%.5f|%.5f", name, street.orEmpty(), latitude, longitude)
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
    return "geo:poi:$source:$digest"
}

/** One moving location slot; every favorite remains an immutable saved address. */
internal fun replaceLocatedCity(cities: List<City>, previousKey: String?, located: City): List<City> {
    val favoriteKeys = cities.filter(City::isFavorite).map(City::locationKey).toSet()
    var movingKey = located.locationKey
    var variant = 0
    // A city ID (or rounded GPS key) can be shared by different measured coordinates.
    // Do not overwrite the favorite even when a new fix has the same source identity.
    val prefix = if (located.isPreciseLocation) "geo:located:" else "located:"
    while (movingKey in favoriteKeys) movingKey = "$prefix${variant++}:${located.locationKey}"
    val moving = located.copy(
        locationKey = movingKey,
        isFavorite = false,
        // Internal slot IDs must never be sent to a provider as its city ID.
        weatherLocationKey = if (movingKey == located.locationKey) located.weatherLocationKey
            else located.weatherLocationKey ?: located.locationKey,
    )
    val result = cities.filterNot {
        !it.isFavorite && (it.locationKey == previousKey || it.locationKey == movingKey)
    }.toMutableList()
    result.add(0, moving)
    return result
}
