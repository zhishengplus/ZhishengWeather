package com.zhisheng.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhisheng.weather.data.AmbienceLevel
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val _weather = MutableStateFlow<WeatherData?>(null)
    private val _weatherCityKey = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(false)
    private val _locating = MutableStateFlow(false)
    private val _locateMessage = MutableStateFlow<String?>(null)
    private val _staleAge = MutableStateFlow<Long?>(null)
    private val _sceneWeather = MutableStateFlow<SceneWeatherData?>(null)
    private val _sceneCityKey = MutableStateFlow<String?>(null)

    private var lastFetchedKey: String? = null
    private var lastFetchKey: String? = null
    private var lastFetchAt: Long = 0L

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
        baseState, displayPrefs, sourcePref, _locating, _locateMessage, _staleAge, _citiesLoaded,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        (arr[0] as HomeUiState).copy(
            prefs = arr[1] as DisplayPrefs,
            sourcePref = arr[2] as SourcePref,
            locating = arr[3] as Boolean,
            locateMessage = arr[4] as String?,
            staleAgeMillis = arr[5] as Long?,
            citiesLoaded = arr[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            selectedCity.collect { city ->
                if (city == null) {
                    sceneJob?.cancel()
                    _sceneCityKey.value = null
                    _sceneWeather.value = null
                }
                if (city != null && city.locationKey != lastFetchedKey) {
                    lastFetchedKey = city.locationKey
                    refresh(city)
                }
            }
        }
        // 数据源改了就立即重拉当前城市，不用等用户手动下拉（v0.0.2）
        viewModelScope.launch {
            var first = true
            combine(sourcePref, com.zhisheng.weather.data.SecretStore.qwRuntimeFlow,
                com.zhisheng.weather.data.SecretStore.caiyunRuntimeFlow) { pref, qw, caiyun ->
                pref to when (pref) {
                    SourcePref.QWEATHER -> qw
                    SourcePref.CAIYUN -> caiyun
                    // 枳生天气源"有钥匙就上桌"：新配和风/彩云凭据时重拉一次
                    SourcePref.ZHISHENG -> qw to caiyun
                    else -> Unit
                }
            }.distinctUntilChanged().collect {
                if (first) { first = false; return@collect }
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

    // force=false 用于 ON_RESUME 自动刷新：同城 10 分钟内不重复拉，
    // 避免与启动时 selectedCity 首发射叠加成双份请求（v0.0.1）
    fun refresh(city: City? = null, force: Boolean = true) {
        val target = city ?: selectedCity.value ?: return
        refreshScene(target, force)
        val now = System.currentTimeMillis()
        if (!force && target.locationKey == lastFetchKey && now - lastFetchAt < 10 * 60_000L) return
        if (target.locationKey != lastFetchedKey) lastFetchedKey = target.locationKey
        lastFetchKey = target.locationKey
        lastFetchAt = now
        fetchJob?.cancel()
        if (_weatherCityKey.value != target.locationKey) {
            // 城市名切换后绝不允许继续挂着上一城市的温度/天气。
            _weather.value = null
            _staleAge.value = null
        }
        _weatherCityKey.value = target.locationKey
        var job: kotlinx.coroutines.Job? = null
        job = viewModelScope.launch {
            _loading.value = true
            try {
                // stateIn 的占位值是 AUTO；冷启动时城市可能先于 DataStore 中的真实来源发出。
                // 每次请求直接读取已落盘的来源，避免设置页显示锁定和风、首页却先走自动源。
                val requestedSource = SettingsRepository.sourcePref.first()
                if (_weather.value?.let { !requestedSource.matches(it) } == true) {
                    _weather.value = null
                    _staleAge.value = null
                }
                if (_weather.value == null) {
                    WeatherCache.load(getApplication(), target.locationKey)
                        ?.takeIf { requestedSource.matches(it.data) && it.isUsableOfflineAt(System.currentTimeMillis()) }
                        ?.let { cached ->
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
                if (result.current == null) {
                    // 失败兜底：只复用同一数据源的缓存。换源失败时不能把上一源的卡片当成当前结果。
                    val cacheNow = System.currentTimeMillis()
                    val cached = WeatherCache.load(getApplication(), target.locationKey)
                        ?.takeIf { requestedSource.matches(it.data) }
                        ?.takeIf { it.isUsableOfflineAt(cacheNow) }
                    if (cached != null) {
                        result = WeatherConsistency.align(cached.data)
                        _staleAge.value = System.currentTimeMillis() - cached.savedAtMillis
                        android.util.Log.i("ZhishengWeather", "${target.name} 抓取失败，展示 ${_staleAge.value}ms 前的缓存")
                    } else {
                        _staleAge.value = null
                    }
                } else {
                    _staleAge.value = null
                    runCatching { WeatherCache.save(getApplication(), target.locationKey, result) }
                    runCatching { WidgetRuntime.publishAndRender(getApplication(), target, result) }
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
        if (selectedCity.value?.locationKey != locationKey) {
            _weather.value = null
            _weatherCityKey.value = null
        }
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
        autoLocateJob?.cancel()
        autoLocateJob = null
        beginLocate(automatic = false)
    }

    // 定位开关开启且已授权后，回到前台最多每 30 分钟复核一次城市；
    // 不申请后台位置，也不会在 App 未打开时持续跟踪。
    // 1B：自动定位闸门持久化（上次自动定位时间落盘，进程重启不归零）
    private var lastLocateAttemptAt = 0L
    private var locateJob: kotlinx.coroutines.Job? = null
    private var autoLocateJob: kotlinx.coroutines.Job? = null
    private var selectionRevision = 0L

    private fun cancelLocateForSelection() {
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
            try {
                when (val r = LocationSource.locate(getApplication())) {
                    is LocationSource.Result.Ok -> {
                        if (selectionRevision != revision) return@launch
                        if (!com.zhisheng.weather.data.shouldApplyLocation(startedKey, selectedCity.value?.locationKey)) return@launch
                        if (refreshTarget != null && selectedCity.value?.locationKey != refreshTarget.locationKey) return@launch
                        val locatedCity = CityRepository.addOrUpdateLocatedCity(r.city, startedKey) ?: return@launch
                        if (selectionRevision != revision) return@launch
                        val prefix = when {
                            automatic && r.streetStatus == LocationSource.StreetStatus.RESOLVED -> "已自动精确定位"
                            automatic -> "已自动更新定位"
                            r.streetStatus == LocationSource.StreetStatus.RESOLVED -> "已精确定位"
                            else -> "已定位"
                        }
                        val suffix = when (r.streetStatus) {
                            LocationSource.StreetStatus.APPROXIMATE_PERMISSION -> "（系统仅授予大致位置）"
                            LocationSource.StreetStatus.INSUFFICIENT_ACCURACY -> "（精度不足，未显示街道）"
                            LocationSource.StreetStatus.UNAVAILABLE -> "（暂未识别到街道）"
                            else -> ""
                        }
                        val accuracy = r.accuracyMeters?.let { "约${it.toInt()}米" } ?: "精度未知"
                        _locateMessage.value = "$prefix：${locatedCity.displayName}$suffix · $accuracy · ${r.fixAgeSeconds}秒前的位置"
                        lastFetchedKey = locatedCity.locationKey
                        // 定位结果需要覆盖同城旧坐标与街道；手动搜索城市仍保持原有去重行为。
                        SettingsRepository.setLastAutoLocateAt(System.currentTimeMillis())
                        if (selectionRevision == revision) refresh(locatedCity)
                    }
                    is LocationSource.Result.Failed -> {
                        if (selectionRevision != revision) return@launch
                        if (!automatic) _locateMessage.value = r.message
                        if (refreshTarget != null && selectedCity.value?.locationKey == refreshTarget.locationKey) refresh(refreshTarget)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (selectionRevision != revision) return@launch
                if (!automatic) _locateMessage.value = "暂时无法更新位置，已保留原地点"
                if (refreshTarget != null && selectedCity.value?.locationKey == refreshTarget.locationKey) refresh(refreshTarget)
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
