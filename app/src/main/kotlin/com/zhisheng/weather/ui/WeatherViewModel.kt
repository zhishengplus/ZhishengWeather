package com.zhisheng.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.FavoriteToggleResult
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SceneWeatherRepository
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.TelemetryMetric
import com.zhisheng.weather.data.WeatherCache
import com.zhisheng.weather.data.isUsableOfflineAt
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.SceneWeatherData
import com.zhisheng.weather.widget.WidgetRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import com.zhisheng.weather.ui.home.citySwitchNeedsRefresh
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DisplayPrefs(
    val showAqi: Boolean = true,
    val showIndices: Boolean = true,
    val showYesterday: Boolean = true,
    val showPrecip: Boolean = true,
    val showTelemetry: Boolean = true,
    val showSpacetime: Boolean = true,
    val showSkyPhotography: Boolean = true,
    val showCoastWeather: Boolean = com.zhisheng.weather.data.ReleaseFeatures.coastalWeather,
    val windUnit: String = "kmh",
    val pressureUnit: String = "hpa",
    val scanlines: Boolean = false,
    val ambience: AmbienceLevel = AmbienceLevel.OFF,
    val bootAnim: Boolean = true,
    val homeBriefingStyle: HomeBriefingStyle = HomeBriefingStyle.TIPS,
    val homeSurfaceStyle: HomeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS,
    val moduleOrder: List<HomeModule> = HomeModule.defaultOrder,
    val telemetryMetrics: Set<TelemetryMetric> = TelemetryMetric.defaultSelection,
    val lifeIndexMetrics: Set<LifeIndexMetric> = LifeIndexMetric.defaultSelection,
)

data class HomeUiState(
    val cities: List<City> = emptyList(),
    val selectedCity: City? = null,
    val weather: WeatherData? = null,
    val sceneWeather: SceneWeatherData? = null,
    val loading: Boolean = false,
    val tempUnit: String = "c",
    val showTyphoon: Boolean = com.zhisheng.weather.data.ReleaseFeatures.typhoon,
    val sourcePref: SourcePref = SourcePref.AUTO,
    val prefs: DisplayPrefs = DisplayPrefs(),
    val locating: Boolean = false,
    val locateMessage: String? = null,
    // 非空 = 当前展示的是离线缓存兜底数据，值为缓存年龄（毫秒）
    val staleAgeMillis: Long? = null,
    // false = cities 仍是 stateIn 占位空表，UI 不得据此渲染"未接入城市"空态（0.0.9-debug）
    val citiesLoaded: Boolean = false,
    // 每城最近一次可用天气快照（内存），供相邻页与刷新期间立即显示，避免翻页空白
    val citySnapshots: Map<String, CachedWeather> = emptyMap(),
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    // Non-secret generation: all city previews expire when the provider configuration changes.
    private val _previewRevision = MutableStateFlow(0L)
    val previewRevision: StateFlow<Long> = _previewRevision

    /** Disk-only neighbour preview. Does not select a city, refresh widgets or spend API quota. */
    suspend fun loadCityPreview(city: City): com.zhisheng.weather.data.CachedWeather? =
        kotlinx.coroutines.withTimeoutOrNull(1_500L) {
            try {
                val revision = _previewRevision.value
                val source = SettingsRepository.sourcePref.first()
                val cached = listOfNotNull(_citySnapshots.value[city.locationKey], WeatherCache.load(getApplication(), city))
                    .filter { it.isUsableOfflineAt(System.currentTimeMillis()) && com.zhisheng.weather.data.weatherCoordinatesMatch(city, it.data) }
                    .sortedByDescending { it.savedAtMillis }.firstOrNull { WeatherRepository.cacheCompatible(it.data, source) }
                    ?: return@withTimeoutOrNull null
                val compatible = WeatherRepository.cacheCompatible(cached.data, source)
                if (revision != _previewRevision.value || source != SettingsRepository.sourcePref.first())
                    return@withTimeoutOrNull null
                com.zhisheng.weather.ui.home.usableCityWeatherCache(cached, source,
                    System.currentTimeMillis(), configurationCompatible = compatible, city = city)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A preview is optional; storage/configuration failure leaves a truthful empty page.
                null
            }
        }

    private val _weather = MutableStateFlow<WeatherData?>(null)
    private val _weatherCityKey = MutableStateFlow<String?>(null)
    // 邻页与刷新期间先展示最近一次可用数据，再决定是否需要重新拉取。
    private val _citySnapshots = MutableStateFlow<Map<String, CachedWeather>>(emptyMap())
    val citySnapshots: StateFlow<Map<String, CachedWeather>> = _citySnapshots

    private fun publishCitySnapshot(key: String, entry: CachedWeather) {
        _citySnapshots.value = _citySnapshots.value + (key to entry)
    }

    private fun seedCitySnapshots(cities: List<City>) {
        viewModelScope.launch {
            val revision = _previewRevision.value
            val loaded = withContext(Dispatchers.IO) {
                val map = mutableMapOf<String, CachedWeather>()
                for (city in cities) {
                    val entry = loadCityPreview(city)
                    if (entry != null) map[city.locationKey] = entry
                }
                map
            }
            if (revision != _previewRevision.value) return@launch
            val keys = cities.map { it.locationKey }.toSet()
            val merged = _citySnapshots.value.filterKeys { it in keys }.toMutableMap()
            loaded.forEach { (key, entry) ->
                val current = merged[key]
                if (current == null || entry.savedAtMillis > current.savedAtMillis) merged[key] = entry
            }
            _citySnapshots.value = merged
        }
    }

    private val _loading = MutableStateFlow(false)
    private val _locating = MutableStateFlow(false)
    private val _locateMessage = MutableStateFlow<String?>(null)
    private val _staleAge = MutableStateFlow<Long?>(null)
    private val _sceneWeather = MutableStateFlow<SceneWeatherData?>(null)
    private val _sceneCityKey = MutableStateFlow<String?>(null)

    private var lastFetchedKey: String? = null
    private var lastFetchKey: String? = null
    private val refreshGate = com.zhisheng.weather.data.RefreshGate()

    // 同一时间只保留一次抓取：换城市立即取消旧任务，
    // 避免新旧城市结果乱序覆盖（v0.0.1：切城市偶发数据错乱的修复）
    private var fetchJob: kotlinx.coroutines.Job? = null
    private var sceneJob: kotlinx.coroutines.Job? = null
    private val cityInitializationJob = viewModelScope.launch { CityRepository.ensureDefaultCity() }

    // citiesLoaded：DataStore 异步首发前 cities 的占位空表会被 UI 误判为
    // 「用户删光了城市」，已存城市的用户冷启动闪一屏"未接入城市"再淡出（0.0.9-debug 修复）。
    // 首个真实值发出后置 true，UI 据此把占位期渲染成 loading 而非空态。
    private val _citiesLoaded = MutableStateFlow(false)
    val citiesLoaded: StateFlow<Boolean> = _citiesLoaded

    val cities: StateFlow<List<City>> = CityRepository.cities
        .onEach { _citiesLoaded.value = true }
        .catch { e ->
            // 文件级读取异常（区别于键级解码失败，键级已在 CityRepository 兜底）：
            // 保持 citiesLoaded=false 让 UI 停在加载态并留日志，绝不发空表把已存城市的用户误判成"未接入城市"。
            android.util.Log.e("ZhishengWeather", "城市数据流读取失败", e)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedCity: StateFlow<City?> = CityRepository.selectedCity
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 数据源偏好单独暴露：refresh 时要读当前值（combine 的 6 元上限已满）
    val sourcePref: StateFlow<SourcePref> = SettingsRepository.sourcePref
        .stateIn(viewModelScope, SharingStarted.Eagerly, SourcePref.AUTO)

    // combine 只到 5 元，第二组偏好得单独并一次。原来并成 List<Any> 再按下标强转，
    // 顺序错一位编译期不报错、运行期才炸；换成具名字段，编译器替我们盯着（0.0.9）。
    private data class VisualPrefs(
        val windUnit: String,
        val pressureUnit: String,
        val scanlines: Boolean,
        val ambience: AmbienceLevel,
        val bootAnim: Boolean,
    )

    private val displayPrefs: StateFlow<DisplayPrefs> = combine(
        SettingsRepository.showAqi,
        SettingsRepository.showIndices,
        SettingsRepository.showYesterday,
        SettingsRepository.showPrecip,
        SettingsRepository.showTelemetry,
    ) { aqi, ix, y, p, tele ->
        DisplayPrefs(showAqi = aqi, showIndices = ix, showYesterday = y, showPrecip = p, showTelemetry = tele)
    }.combine(SettingsRepository.showSpacetime) { prefs, show ->
        prefs.copy(showSpacetime = show)
    }.combine(SettingsRepository.showSkyPhotography) { prefs, show ->
        prefs.copy(showSkyPhotography = show)
    }.combine(SettingsRepository.showCoastWeather) { prefs, show ->
        prefs.copy(showCoastWeather = show)
    }.combine(
        combine(
            SettingsRepository.windUnit,
            SettingsRepository.pressureUnit,
            SettingsRepository.scanlines,
            SettingsRepository.ambience,
            SettingsRepository.bootAnim,
        ) { w, pr, sl, amb, boot -> VisualPrefs(w, pr, sl, amb, boot) }
    ) { base, extra ->
        base.copy(
            windUnit = extra.windUnit,
            pressureUnit = extra.pressureUnit,
            scanlines = extra.scanlines,
            ambience = extra.ambience,
            bootAnim = extra.bootAnim,
        )
    }.combine(SettingsRepository.moduleOrder) { prefs, order ->
        prefs.copy(moduleOrder = order)
    }.combine(SettingsRepository.homeBriefingStyle) { prefs, style ->
        prefs.copy(homeBriefingStyle = style)
    }.combine(SettingsRepository.homeSurfaceStyle) { prefs, style ->
        prefs.copy(homeSurfaceStyle = style)
    }.combine(SettingsRepository.telemetryMetrics) { prefs, metrics ->
        prefs.copy(telemetryMetrics = metrics)
    }.combine(SettingsRepository.lifeIndexMetrics) { prefs, metrics ->
        prefs.copy(lifeIndexMetrics = metrics)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPrefs())

    private data class WeatherCore(
        val cities: List<City>,
        val selected: City?,
        val weather: WeatherData?,
        val weatherCityKey: String?,
        val loading: Boolean,
    )

    private val weatherCore = combine(cities, selectedCity, _weather, _weatherCityKey, _loading) {
            cityList, selected, weather, weatherCityKey, loading ->
        WeatherCore(cityList, selected, weather, weatherCityKey, loading)
    }

    private data class SceneCore(val data: SceneWeatherData?, val cityKey: String?)
    private val sceneCore = combine(_sceneWeather, _sceneCityKey) { data, key -> SceneCore(data, key) }

    private val baseState: StateFlow<HomeUiState> = combine(
        weatherCore,
        sceneCore,
        SettingsRepository.tempUnit,
        SettingsRepository.showTyphoon,
    ) { core, scene, tempUnit, showTyphoon ->
        HomeUiState(
            cities = core.cities,
            selectedCity = core.selected,
            weather = core.weather.takeIf { core.weatherCityKey == core.selected?.locationKey },
            sceneWeather = scene.data.takeIf { scene.cityKey == core.selected?.locationKey },
            loading = core.loading,
            tempUnit = tempUnit,
            showTyphoon = showTyphoon,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    // combine 的 6 元没有具名 lambda 重载，与 baseState 一样用 Array 风格
    val uiState: StateFlow<HomeUiState> = combine(
        baseState, displayPrefs, sourcePref, _locating, _locateMessage, _staleAge, _citiesLoaded, _citySnapshots,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        (arr[0] as HomeUiState).copy(
            prefs = arr[1] as DisplayPrefs,
            sourcePref = arr[2] as SourcePref,
            locating = arr[3] as Boolean,
            locateMessage = arr[4] as String?,
            staleAgeMillis = arr[5] as Long?,
            citiesLoaded = arr[6] as Boolean,
            citySnapshots = arr[7] as Map<String, CachedWeather>,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            selectedCity.distinctUntilChangedBy { it?.locationKey }.collectLatest { city ->
                if (city == null) {
                    sceneJob?.cancel()
                    _sceneCityKey.value = null
                    _sceneWeather.value = null
                }
                if (city != null && city.locationKey != lastFetchedKey) {
                    lastFetchedKey = city.locationKey
                    // Keep the snapshot on screen while consecutive swipes settle.
                    fetchJob?.cancel()
                    sceneJob?.cancel()
                    _loading.value = false
                    val cached = loadCityPreview(city)
                    if (cached != null) publishCitySnapshot(city.locationKey, cached)
                    if (selectedCity.value?.locationKey != city.locationKey) return@collectLatest
                    _weather.value = cached?.data
                    _weatherCityKey.value = city.locationKey
                    _staleAge.value = cached?.let { System.currentTimeMillis() - it.savedAtMillis }
                    kotlinx.coroutines.delay(1_000)
                    if (selectedCity.value?.locationKey != city.locationKey) return@collectLatest
                    if (citySwitchNeedsRefresh(cached?.savedAtMillis, System.currentTimeMillis())) refresh(city)
                    else refreshScene(city, force = false)
                }
            }
        }
        // 城市列表就绪/变化时，把每城磁盘缓存提升为内存快照，翻页第一帧即可显示。
        viewModelScope.launch {
            cities.collect { list -> seedCitySnapshots(list) }
        }
        // 数据源改了就立即重拉当前城市，不用等用户手动下拉（v0.0.2）
        viewModelScope.launch {
            var first = true
            combine(sourcePref, com.zhisheng.weather.data.SecretStore.qwRuntimeFlow,
                com.zhisheng.weather.data.SecretStore.caiyunRuntimeFlow,
                com.zhisheng.weather.data.SecretStore.amapRuntimeFlow,
                com.zhisheng.weather.data.SecretStore.baiduRuntimeFlow) { pref, qw, caiyun, amap, baidu ->
                listOf(pref, qw, caiyun, amap, baidu)
            }.combine(com.zhisheng.weather.data.SecretStore.seniverseRuntimeFlow) { credentials, seniverse -> credentials + seniverse }
                .combine(com.zhisheng.weather.data.SecretStore.mojiRuntimeFlow) { credentials, moji -> credentials + moji }
                .combine(SettingsRepository.moduleSources) { credentials, modules -> credentials to modules }
                .combine(SettingsRepository.sourcePriority) { setup, priority -> setup to priority }
                .combine(SettingsRepository.developerMode) { setup, mode -> setup to mode }
                .distinctUntilChanged().collect {
                if (first) { first = false; return@collect }
                _previewRevision.value++
                _citySnapshots.value = emptyMap()
                seedCitySnapshots(cities.value)
                refresh()
            }
        }
        viewModelScope.launch {
            SettingsRepository.purgeRetiredProviderData()
        }
        viewModelScope.launch {
            var first = true
            combine(SettingsRepository.showSkyPhotography, SettingsRepository.showCoastWeather) { sky, coast -> sky to coast }
                .collect {
                    if (first) { first = false; return@collect }
                    selectedCity.value?.let { refreshScene(it, force = false) }
                }
        }
    }

    // force=false 用于前台自动刷新：成功请求按用户间隔去重，失败短暂退避，
    // 避免与启动时 selectedCity 首发射叠加成双份请求（v0.0.1）
    fun refresh(city: City? = null, force: Boolean = true, intervalElapsed: Boolean = false) {
        val target = city ?: selectedCity.value ?: return
        val now = System.currentTimeMillis()
        val interval = com.zhisheng.weather.data.AutomationSettingsRepository.read(getApplication()).foregroundMinutes * 60_000L
        if (!force && !refreshGate.shouldFetch(target.locationKey, now, interval,
                lastFetchKey.takeIf { fetchJob?.isActive == true }, intervalElapsed)) return
        refreshScene(target, force)
        if (target.locationKey != lastFetchedKey) lastFetchedKey = target.locationKey
        lastFetchKey = target.locationKey
        fetchJob?.cancel()
        val knownSnapshot = _citySnapshots.value[target.locationKey]
            ?.takeIf { it.data.current != null && it.data.error == null && it.isUsableOfflineAt(now) &&
                com.zhisheng.weather.data.weatherCoordinatesMatch(target, it.data) }
        if (_weatherCityKey.value != target.locationKey ||
            _weather.value?.let { !com.zhisheng.weather.data.weatherCoordinatesMatch(target, it) } == true) {
            // 城市名切换后绝不允许继续挂着上一城市的温度/天气；有快照则先顶上，避免翻页空白。
            _weather.value = knownSnapshot?.data
            _staleAge.value = knownSnapshot?.let { now - it.savedAtMillis }
        }
        _weatherCityKey.value = target.locationKey
        var job: kotlinx.coroutines.Job? = null
        job = viewModelScope.launch {
            _loading.value = true
            try {
                // stateIn 的占位值是 AUTO；冷启动时城市可能先于 DataStore 中的真实来源发出。
                // 每次请求直接读取已落盘的来源，避免设置页显示锁定和风、首页却先走自动源。
                val requestedSource = SettingsRepository.sourcePref.first()
                if (_weather.value?.let { !WeatherRepository.cacheCompatible(it, requestedSource) } == true) {
                    val compatibleSnapshot = _citySnapshots.value[target.locationKey]
                        ?.takeIf { WeatherRepository.cacheCompatible(it.data, requestedSource) }
                    _weather.value = compatibleSnapshot?.data
                    _staleAge.value = compatibleSnapshot?.let { System.currentTimeMillis() - it.savedAtMillis }
                }
                if (_weather.value == null) {
                    WeatherCache.load(getApplication(), target)
                        ?.takeIf { WeatherRepository.cacheCompatible(it.data, requestedSource) && it.isUsableOfflineAt(System.currentTimeMillis()) &&
                            com.zhisheng.weather.data.weatherCoordinatesMatch(target, it.data) }
                        ?.let { cached ->
                            publishCitySnapshot(target.locationKey, cached)
                            if (selectedCity.value?.locationKey == target.locationKey) {
                                _weather.value = WeatherConsistency.align(cached.data)
                                _staleAge.value = System.currentTimeMillis() - cached.savedAtMillis
                            }
                        }
                }
                // 全局超时兜底：三源降级链最坏可串行 60s+，超过 25s 直接判失败走离线缓存。
                // 注意 TimeoutCancellationException 是 CancellationException 子类，必须先于它 catch（v0.0.4）。
                var result = try {
                    kotlinx.coroutines.withTimeout(FETCH_TIMEOUT_MS) {
                        WeatherRepository.fetchWeather(target, requestedSource)
                    }
                } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.w("ZhishengWeather", "抓取超时 ${target.name}，走缓存兜底")
                    WeatherData(error = "请求超时")
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    android.util.Log.w("ZhishengWeather", "抓取异常 ${target.name}", e)
                    WeatherData(error = WeatherRepository.userFacingFetchError("天气数据"))
                }
                if (result.current == null || result.error != null) {
                    refreshGate.failure(target.locationKey, System.currentTimeMillis())
                    // 失败兜底：只复用同一数据源的缓存。换源失败时不能把上一源的卡片当成当前结果。
                    val cacheNow = System.currentTimeMillis()
                    val cached = WeatherCache.load(getApplication(), target)
                        ?.takeIf { WeatherRepository.cacheCompatible(it.data, requestedSource) }
                        ?.takeIf { it.isUsableOfflineAt(cacheNow) }
                        ?.takeIf { com.zhisheng.weather.data.weatherCoordinatesMatch(target, it.data) }
                    if (cached != null) {
                        publishCitySnapshot(target.locationKey, cached)
                        result = WeatherConsistency.align(cached.data)
                        _staleAge.value = System.currentTimeMillis() - cached.savedAtMillis
                        android.util.Log.i("ZhishengWeather", "${target.name} 抓取失败，展示 ${_staleAge.value}ms 前的缓存")
                    } else {
                        _staleAge.value = null
                    }
                } else {
                    if (SettingsRepository.sourcePref.first() != requestedSource ||
                        !WeatherRepository.cacheCompatible(result, requestedSource)) return@launch
                    refreshGate.success(target.locationKey, System.currentTimeMillis())
                    _staleAge.value = null
                    publishCitySnapshot(target.locationKey, CachedWeather(result, System.currentTimeMillis()))
                    runCatching { WeatherCache.save(getApplication(), target.locationKey, result) }
                    runCatching { WidgetRuntime.publishAndRender(getApplication(), target, result) }
                    com.zhisheng.weather.data.WeatherReminderSchedule.schedule(getApplication())
                }
                if (selectedCity.value?.locationKey == target.locationKey) {
                    _weather.value = result
                }
            } finally {
                // 仅当自己仍是当前任务时才清 loading：换城市取消旧任务时，
                // 旧任务的 finally 不应把新任务刚置的 loading 清掉（v0.0.3）
                if (fetchJob === job) _loading.value = false
            }
        }
        fetchJob = job
    }

    fun refreshSceneIfNeeded() {
        if (sceneJob?.isActive == true) return
        selectedCity.value?.let { refreshScene(it, force = false) }
    }

    private fun refreshScene(target: City, force: Boolean) {
        sceneJob?.cancel()
        if (_sceneCityKey.value != target.locationKey) _sceneWeather.value = null
        _sceneCityKey.value = target.locationKey
        sceneJob = viewModelScope.launch {
            val wantSky = SettingsRepository.showSkyPhotography.first()
            val wantCoast = SettingsRepository.showCoastWeather.first()
            if (!wantSky && !wantCoast) {
                if (selectedCity.value?.locationKey == target.locationKey) _sceneWeather.value = null
                return@launch
            }
            val result = try {
                kotlinx.coroutines.withTimeout(15_000L) {
                    SceneWeatherRepository.fetch(target, wantSky, wantCoast, force)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                _sceneWeather.value?.copy(
                    sky = _sceneWeather.value?.sky?.copy(stale = true),
                    coast = _sceneWeather.value?.coast?.copy(stale = true),
                ) ?: SceneWeatherData(skyError = "天空摄影请求超时，下拉可重试", coastError = "海岸数据请求超时，下拉可重试")
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Exception) {
                SceneWeatherData(skyError = "天空摄影暂不可用，下拉可重试", coastError = "海岸数据暂不可用，下拉可重试")
            }
            if (selectedCity.value?.locationKey == target.locationKey) _sceneWeather.value = result
        }
    }

    fun selectCity(locationKey: String) {
        cancelLocateForSelection()
        _locateMessage.value = null
        // Per-city snapshots remain available to the pager throughout selection.
        viewModelScope.launch { CityRepository.selectCity(locationKey) }
    }

    /** WidgetLaunchActivity has already persisted the explicitly chosen widget city. */
    fun preserveWidgetCityOnEntry() {
        cancelLocateForSelection()
        _locateMessage.value = null
    }

    fun addCityAndSelect(city: City) {
        cancelLocateForSelection()
        _locateMessage.value = null
        viewModelScope.launch {
            val savedCity = CityRepository.addCity(city)
            lastFetchedKey = savedCity.locationKey
            refresh(savedCity)
        }
    }

    fun removeCity(locationKey: String) {
        if (selectedCity.value?.locationKey == locationKey) cancelLocateForSelection()
        viewModelScope.launch {
            CityRepository.removeCity(locationKey)
        }
    }

    fun toggleCityFavorite(locationKey: String) {
        // Pin the displayed address before an in-flight fix can replace the location slot.
        cancelLocateForSelection()
        viewModelScope.launch {
            when (CityRepository.toggleFavorite(locationKey)) {
                FavoriteToggleResult.LIMIT_REACHED -> {
                    _locateMessage.value = "最多收藏 6 个城市，请先取消一个收藏"
                }
                FavoriteToggleResult.FAVORITED,
                FavoriteToggleResult.UNFAVORITED -> {
                    if (_locateMessage.value?.startsWith("最多收藏") == true) _locateMessage.value = null
                }
                FavoriteToggleResult.NOT_FOUND -> Unit
            }
        }
    }

    // —— 定位（v0.0.2）——
    // 手动触发时总是优先取新位置；权限申请由 UI 层负责，这里假定已授权。
    fun locateCurrentCity() {
        cancelLocateForSelection()
        beginLocate(automatic = false)
    }

    /** The ViewModel owns locating and its success commit across Activity recreation. */
    private val initialLocationRequest = RetainedLocationRequest(viewModelScope)

    suspend fun locateInitialCity(): Boolean = initialLocationRequest.run {
        cancelLocateForSelection(cancelInitial = false)
        val revision = selectionRevision
        _locateMessage.value = null
        cityInitializationJob.join()
        if (selectionRevision != revision) return@run false
        beginLocate(automatic = false)
        locateJob?.join()
        if (selectionRevision != revision || lastAppliedLocationRevision != revision) return@run false
        val locatedKey = CityRepository.locatedCityKey.first()
        if (locatedKey == null || CityRepository.selectedCity.first()?.locationKey != locatedKey) return@run false
        SettingsRepository.setLocationEnabled(true)
        true
    }

    suspend fun resumeInitialLocation(): Boolean = initialLocationRequest.awaitResult()

    fun cancelLocationRequest() {
        cancelLocateForSelection()
        _locateMessage.value = null
    }

    // 定位开关开启且已授权后，回到前台最多每 30 分钟复核一次城市；
    // 不申请后台位置，也不会在 App 未打开时持续跟踪。
    // 1B：自动定位闸门持久化（上次自动定位时间落盘，进程重启不归零）
    private var lastLocateAttemptAt = 0L
    private var locateJob: kotlinx.coroutines.Job? = null
    private var autoLocateJob: kotlinx.coroutines.Job? = null
    private var selectionRevision = 0L
    private var lastAppliedLocationRevision = -1L

    private fun cancelLocateForSelection(cancelInitial: Boolean = true) {
        if (cancelInitial) initialLocationRequest.cancel()
        selectionRevision++
        autoLocateJob?.cancel()
        autoLocateJob = null
        locateJob?.cancel()
        locateJob = null
        _locating.value = false
    }

    /** Refreshing a saved city must never switch the user back to their GPS city. */
    fun refreshFromUser() {
        val target = selectedCity.value ?: return
        val revision = selectionRevision
        viewModelScope.launch {
            val followsLocation = CityRepository.locatedCityKey.first() == target.locationKey
            if (selectionRevision != revision || selectedCity.value?.locationKey != target.locationKey) return@launch
            if (followsLocation && SettingsRepository.locationEnabled.first() &&
                LocationSource.hasPermission(getApplication())) {
                beginLocate(automatic = false, refreshTarget = target)
            } else {
                refresh(target)
            }
        }
    }

    fun autoLocateIfEnabled() {
        if (_locating.value || autoLocateJob?.isActive == true) return
        val revision = selectionRevision
        autoLocateJob = viewModelScope.launch {
            try {
                if (!SettingsRepository.locationEnabled.first()) return@launch
                if (!LocationSource.hasPermission(getApplication())) return@launch
                // Wait for persisted selection and first-install seeding, rather than
                // using stateIn's temporary null as the location request's starting city.
                cityInitializationJob.join()
                val storedSelection = CityRepository.selectedCity.first()
                if (selectionRevision != revision) return@launch
                selectedCity.first { it?.locationKey == storedSelection?.locationKey }
                if (selectionRevision != revision) return@launch

                // Restoring a known location is independent of the GPS throttle.
                // Otherwise a recent fix leaves the last manually viewed city selected
                // after reopening, even though automatic location following is enabled.
                val restored = CityRepository.restoreLocatedCity(storedSelection?.locationKey)
                if (selectionRevision != revision) return@launch
                if (restored != null) {
                    selectedCity.first { it?.locationKey == restored.locationKey }
                }
                if (selectionRevision != revision) return@launch

                val last = SettingsRepository.lastAutoLocateAt.first()
                val now = System.currentTimeMillis()
                if (now - last in 0 until AUTO_LOCATE_INTERVAL_MS) return@launch
                if (now - lastLocateAttemptAt in 0 until 60_000L) return@launch
                if (!SettingsRepository.locationEnabled.first()) return@launch
                if (!LocationSource.hasPermission(getApplication())) return@launch
                if (selectionRevision != revision) return@launch
                beginLocate(automatic = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("ZhishengWeather", "自动恢复定位地区失败，保留当前城市", error)
            }
        }
    }

    private fun beginLocate(automatic: Boolean, refreshTarget: City? = null) {
        if (_locating.value) return
        val startedKey = selectedCity.value?.locationKey
        val revision = selectionRevision
        _locating.value = true
        lastLocateAttemptAt = System.currentTimeMillis()
        var job: kotlinx.coroutines.Job? = null
        job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            if (!automatic) _locateMessage.value = null
            var appliedFix: City? = null
            try {
                when (val r = LocationSource.locate(getApplication()) { initial ->
                    if (selectionRevision != revision ||
                        !com.zhisheng.weather.data.shouldApplyLocation(startedKey, selectedCity.value?.locationKey) ||
                        (refreshTarget != null && selectedCity.value?.locationKey != refreshTarget.locationKey)) {
                        false
                    } else {
                        val located = CityRepository.addOrUpdateLocatedCity(initial.city, startedKey, initial.configurationRevision) {
                            lastFetchedKey = it.locationKey
                        }
                        if (located == null || selectionRevision != revision) false
                        else {
                            appliedFix = located
                            lastAppliedLocationRevision = revision
                            _locateMessage.value = if (initial.usingRecentApproximateFix)
                                "已取得最近大致位置，正在识别地址…" else "已取得当前位置，正在识别地址…"
                            SettingsRepository.setLastAutoLocateAt(System.currentTimeMillis())
                            refresh(located)
                            true
                        }
                    }
                }) {
                    is LocationSource.Result.Ok -> {
                        val initial = appliedFix ?: return@launch
                        if (selectionRevision != revision) return@launch
                        val enriched = r.city.copy(locationKey = initial.locationKey)
                        val locatedCity = CityRepository.updateLocatedLabel(enriched, r.configurationRevision) ?: return@launch
                        val prefix = if (r.usingRecentApproximateFix) "最近大致位置" else if (automatic) "已自动更新定位" else "已定位"
                        val suffix = when (r.streetStatus) {
                            LocationSource.StreetStatus.APPROXIMATE_PERMISSION -> "（系统仅授予大致位置）"
                            LocationSource.StreetStatus.INSUFFICIENT_ACCURACY -> "（精度不足，未显示街道）"
                            LocationSource.StreetStatus.UNAVAILABLE -> "（暂未识别到街道）"
                            else -> ""
                        }
                        val accuracy = r.accuracyMeters?.takeIf { it > 0f }?.let { "约${it.toInt().coerceAtLeast(1)}米" } ?: "精度未知"
                        _locateMessage.value = "$prefix：${locatedCity.displayName}$suffix · $accuracy · ${r.fixAgeSeconds}秒前的位置"
                    }
                    is LocationSource.Result.Failed -> {
                        if (selectionRevision != revision) return@launch
                        if (!automatic) _locateMessage.value = if (appliedFix != null) "已保留取得的坐标，地址暂不可用" else r.message
                        if (appliedFix == null && refreshTarget != null && selectedCity.value?.locationKey == refreshTarget.locationKey) refresh(refreshTarget)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (selectionRevision != revision) return@launch
                if (!automatic) _locateMessage.value = if (appliedFix != null) "已保留取得的坐标，地址暂不可用" else "暂时无法更新位置，已保留原地点"
                if (appliedFix == null && refreshTarget != null && selectedCity.value?.locationKey == refreshTarget.locationKey) refresh(refreshTarget)
            } finally {
                if (locateJob === job) _locating.value = false
            }
        }
        locateJob = job
        job.start()
    }

    fun clearLocateMessage() {
        _locateMessage.value = null
    }

    private companion object {
        const val AUTO_LOCATE_INTERVAL_MS = 30 * 60_000L
        const val FETCH_TIMEOUT_MS = 25_000L
    }
}
