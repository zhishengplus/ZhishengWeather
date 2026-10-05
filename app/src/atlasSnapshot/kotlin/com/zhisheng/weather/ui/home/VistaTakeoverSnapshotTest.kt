package com.zhisheng.weather.ui.home

import android.view.ViewGroup
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.DisplayPrefs
import com.zhisheng.weather.ui.DailyForecastScreen
import com.zhisheng.weather.ui.LandscapeWeatherCoreScreen
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Synthetic, offline layout fixtures; not bundled with the app. */
@RunWith(Parameterized::class)
class VistaTakeoverSnapshotTest(private val light: Boolean, private val width: Int, private val scale: Float) {
    companion object {
        private val settingsFixtureState = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
        private val settingsFixtureStore = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = settingsFixtureState
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences = transform(settingsFixtureState.value).also { settingsFixtureState.value = it }
        }
        @JvmStatic @Parameterized.Parameters(name = "{0}_{1}dp_{2}x")
        fun configurations() = listOf(
            arrayOf<Any>(true, 375, 1f), arrayOf<Any>(false, 375, 1f),
            arrayOf<Any>(true, 320, 1.5f),
            arrayOf<Any>(true, 320, 2f), arrayOf<Any>(false, 320, 2f),
            arrayOf<Any>(true, 414, 1f), arrayOf<Any>(false, 768, 1f), arrayOf<Any>(true, 768, 1f),
        )
    }
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = if (width >= 768) 1125 else 2436, fontScale = scale,
            orientation = if (width >= 768) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT),
        theme = "android:Theme.Material.Light.NoActionBar",
    )
    private val now = java.time.LocalDate.now(java.time.ZoneOffset.ofHours(8))
        .atTime(12, 0).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
    private val city = City("金昌市", "甘肃", 38.50, 102.18, "fixture")
    private val weather = WeatherData(
        current = CurrentWeather(temperature = 24.0, feelsLike = 25.0, condition = WeatherCondition.CLEAR,
            weatherText = "晴", windSpeed = 5.0, windDirectionDeg = 90.0, humidity = 44.0, pressure = 847.0),
        hourly = (-1..23).map { HourlyWeather(now + it * 3_600_000L, 24.0 + (it % 6), WeatherCondition.CLEAR, windSpeed = 5.0, windDirectionDeg = 90.0, precipProb = if (it in 6..12) 10 + it * 4 else 0) },
        daily = (0..14).map { DailyWeather(now + it * 86_400_000L, 30.0 - it % 4, 16.0 - it % 5, WeatherCondition.CLEAR,
            weatherText = "晴转多云", windSpeed = 12.0, windDirectionDeg = 90.0, sunrise = "06:43", sunset = "19:36",
            moonPhase = "waning-gibbous", moonrise = "22:31", moonset = "13:17", aqi = 62) },
        aqi = AqiInfo(value = 62, level = "良", standard = "中国"),
        dataSource = "XIAOMI", fetchedAt = now, updateTime = now, utcOffsetSeconds = 28800,
    )
    private fun render(softGlow: Boolean = true, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA, offsetMillis: Long = 0L, inspection: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent { androidx.compose.runtime.CompositionLocalProvider(com.zhisheng.weather.ui.LocalWeatherPreviewTime provides now,
                androidx.compose.ui.platform.LocalInspectionMode provides inspection) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = softGlow) { content() }
            } }
        }
        paparazzi.snapshot(host, offsetMillis = offsetMillis)
    }
    @Test fun refinedIcons() = render {
        Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }.chunked(4).forEach { conditions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    conditions.forEach { condition ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            com.zhisheng.weather.ui.components.WeatherIcon(condition, Modifier.size(40.dp))
                            Text(condition.label, color = LocalZhishengPalette.current.textSecondary)
                        }
                    }
                }
            }
        }
    }
    @Test fun home() = render {
        SimulatedWeatherSurface(weather, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = now, header = {
            TopBar(city.displayName, false, {}, {}, {})
        })
    }
    @Test fun reworkNightHome() = render {
        val midnight = now + 12 * 3_600_000L
        val nightWeather = weather.copy(current = weather.current!!.copy(temperature = 15.0, feelsLike = 15.0,
            condition = WeatherCondition.CLEAR_NIGHT, weatherText = "晴"),
            hourly = weather.hourly.map { it.copy(timeMillis = it.timeMillis + 12 * 3_600_000L,
                temperature = 15.0, condition = WeatherCondition.CLEAR_NIGHT) })
        androidx.compose.runtime.CompositionLocalProvider(com.zhisheng.weather.ui.LocalWeatherPreviewTime provides midnight) {
            SimulatedWeatherSurface(nightWeather, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
                showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = midnight,
                header = { TopBar(city.displayName, false, {}, {}, {}) })
        }
    }
    @Test fun feedbackHome() = render {
        val longCity = city.copy(name = "金川区", street = "金川路街道·金川路")
        val observed = weather.copy(current = weather.current!!.copy(temperature = 13.0, feelsLike = 18.0,
            condition = WeatherCondition.DRIZZLE, weatherText = "小雨"),
            hourly = weather.hourly.mapIndexed { index, hour -> hour.copy(temperature = 13.0 + index % 3,
                condition = WeatherCondition.DRIZZLE) },
            daily = weather.daily.map { it.copy(high = 15.0, low = 10.0) }, alerts = listOf(
            AlertInfo("金川发布地质灾害气象风险黄色预警", detail = "请留意地质灾害风险。", level = "黄色", severity = AlertLevel.YELLOW),
            AlertInfo("金川发布大风蓝色预警", detail = "请留意大风天气。", level = "蓝色", severity = AlertLevel.BLUE)))
        SimulatedWeatherSurface(observed, longCity, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = now,
            header = { TopBar(longCity.displayName, false, {}, {}, {}) })
    }
    @Test fun feedbackDetails() = render { feedbackDetailsContent() }
    @Test fun takeoverGlowOff() = render(softGlow = false) { feedbackDetailsContent() }
    @Test fun beta66Flowing() = render {
        androidx.compose.runtime.CompositionLocalProvider(LocalVistaGlowLevel provides com.zhisheng.weather.data.SoftGlowLevel.FLOWING,
            LocalVistaGlowPhase provides androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.4) }) { feedbackDetailsContent() }
    }
    @Test fun beta66CityDeck() = render(inspection = true) { cityDeckFixture() }
    @Test fun beta66ClassicDeck() = render(style = InterfaceStyle.CLASSIC_TERMINAL, inspection = true) { cityDeckFixture() }
    @Test fun beta7UpdateNotice() {
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
            }
        }
        render {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                com.zhisheng.weather.ui.AppUpdateDialog(
                    initialInfo = com.zhisheng.weather.data.AppUpdateInfo(versionCode = 20260912, versionName = "0.1.5-beta7", apkUrl = "https://example.com/test.apk", notes = "横屏常亮防护、逐时布局调整与版本统计。"),
                    onDeclineVersion = {}, onClose = {})
            }
        }
    }
    @Test fun airyHourlyChange() = render {
        val changing = weather.copy(hourly = weather.hourly.mapIndexed { index, h ->
            h.copy(temperature = listOf(23.0, 24.0, 25.0, 23.0, 22.0)[index % 5],
                condition = when (index % 5) {
                    0 -> WeatherCondition.CLEAR
                    1 -> WeatherCondition.PARTLY_CLOUDY
                    2 -> WeatherCondition.CLOUDY
                    else -> WeatherCondition.DRIZZLE
                }, precipProb = when (index % 5) { 0 -> null; 1 -> 0; 2 -> 20; else -> 65 })
        })
        SimulatedWeatherSurface(changing, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = now,
            header = { TopBar(city.displayName, false, {}, {}, {}) })
    }
    @Test fun fragranceGlassHome() {
        org.junit.Assume.assumeTrue(width == 375 && scale == 1f)
        render {
            SimulatedWeatherSurface(weather, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
                showCoastWeather = false, scanlines = false, bootAnim = false,
                homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS), referenceTimeMillis = now,
                header = { TopBar(city.displayName, false, {}, {}, {}) })
        }
    }
    @Test fun beta7Hourly() = render { hourlyFixture() }
    @Test fun beta7HourlyFlat() = render {
        val flat = weather.copy(current = weather.current!!.copy(temperature = 11.0, condition = WeatherCondition.DRIZZLE),
            hourly = weather.hourly.map { it.copy(temperature = 11.0, condition = WeatherCondition.DRIZZLE,
                precipProb = if (it.timeMillis <= now) null else 40) })
        Column(Modifier.fillMaxSize().zhishengScreen()) {
            SectionTitle(1, "逐时预报", "", false)
            HourlySection(flat, "c", "kmh", 28800, Modifier)
            SectionTitle(2, "短时降水", "", false)
            PrecipCard(flat, Modifier)
        }
    }
    @Test fun beta7HourlyClassic() = render(style = InterfaceStyle.CLASSIC_TERMINAL) { hourlyFixture() }
    @androidx.compose.runtime.Composable private fun hourlyFixture() {
        val mixed = weather.copy(hourly = weather.hourly.map {
            it.copy(precipProb = if (it.timeMillis <= now) null else if (it.timeMillis <= now + 3_600_000L) 0 else 45)
        })
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            SectionTitle(1, "逐时预报", "", false)
            HourlySection(mixed, "c", "kmh", 28800, Modifier)
            SectionTitle(2, "天气详情", "", false)
            TelemetryGrid(weather.current!!, weather.daily.first(), "c", DisplayPrefs(), Modifier, city, 28800)
        }
    }
    @Test fun beta7Standby() = standbyFixture(InterfaceStyle.PHOSPHOR_VISTA, com.zhisheng.weather.data.LandscapeStandbyStyle.WEATHER_CORE)
    @Test fun beta7StandbyClassic() = standbyFixture(InterfaceStyle.CLASSIC_TERMINAL, com.zhisheng.weather.data.LandscapeStandbyStyle.CLASSIC)
    private fun standbyFixture(style: InterfaceStyle, standbyStyle: com.zhisheng.weather.data.LandscapeStandbyStyle) {
        org.junit.Assume.assumeTrue(width >= 768)
        render(style = style, inspection = true) {
            com.zhisheng.weather.ui.StandbyBurnInFrame(offset = { androidx.compose.ui.unit.IntOffset(6, 4) }) {
                com.zhisheng.weather.ui.LandscapeStandbyScreen(HomeUiState(selectedCity = city, weather = weather), standbyStyle, {}, {}, {})
            }
        }
    }
    @androidx.compose.runtime.Composable private fun cityDeckFixture() {
        CityDeckOverlay(true, true, listOf(city.copy(name = "北京市", locationKey = "beijing", latitude = 39.9, longitude = 116.4), city,
            city.copy(name = "杭州市", locationKey = "hangzhou", latitude = 30.27, longitude = 120.15)), 1f, 1f, {}, {}, {}, {},
            outline = { selected, modifier ->
                val palette = LocalZhishengPalette.current
                val marker = if (isPhosphorVista) palette.mint else palette.red
                // Synchronously prepare the real PR geometry and renderer for a deterministic
                // screenshot. Production keeps its IO/bitmap work in background coroutines.
                val bitmap = androidx.compose.runtime.remember(selected, palette, marker) {
                    kotlinx.coroutines.runBlocking {
                        com.zhisheng.weather.data.BoundaryRepository.ensureLoaded(paparazzi.context)
                        val entry = requireNotNull(com.zhisheng.weather.data.BoundaryRepository.resolve(selected.name, selected.affiliation, selected.latitude, selected.longitude))
                        val geometry = requireNotNull(com.zhisheng.weather.data.BoundaryRepository.geometry(entry))
                        com.zhisheng.weather.ui.components.renderCityMap(geometry, palette.isLight, palette.cyan, marker, 600, 540)
                    }
                }
                androidx.compose.foundation.Image(bitmap = bitmap.asImageBitmap(), contentDescription = "行政区划轮廓", modifier = modifier)
            })
    }
    @Test fun beta66AmapVerifying() = providerVerifying(com.zhisheng.weather.ui.ProviderWizardKind.AMAP, 2)
    @Test fun beta66BaiduVerifying() = providerVerifying(com.zhisheng.weather.ui.ProviderWizardKind.BAIDU, 2)
    @Test fun beta66CaiyunVerifying() = providerVerifying(com.zhisheng.weather.ui.ProviderWizardKind.CAIYUN, 3)
    @Test fun beta66QweatherVerifying() = providerVerifying(com.zhisheng.weather.ui.ProviderWizardKind.QWEATHER, 4)
    private fun providerVerifying(kind: com.zhisheng.weather.ui.ProviderWizardKind, step: Int) = render {
        val model = androidx.compose.runtime.remember { com.zhisheng.weather.ui.ProviderSetupViewModel(kind) }
        val state = com.zhisheng.weather.ui.ProviderSetupUiState(kind = kind,
            step = step, amapKey = "synthetic-test-key", status = com.zhisheng.weather.ui.ProviderSetupStatus.TESTING,
            activeStage = com.zhisheng.weather.data.ProviderTestStage.CONNECT)
        Box(Modifier.fillMaxSize().zhishengScreen().padding(12.dp)) {
            com.zhisheng.weather.ui.ProviderSetupPanel(state, model, width < 360, true, {}, {}, Modifier.fillMaxSize())
        }
    }
    @Test fun releaseFlowLater() = render {
        androidx.compose.runtime.CompositionLocalProvider(LocalVistaGlowPhase provides androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.4) }) {
            feedbackDetailsContent()
        }
    }
    @Test fun releaseSettings() = settingsFixture(2)
    @Test fun beta7About() = settingsFixture(3)
    private fun settingsFixture(section: Int, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA) {
        val fixture = androidx.datastore.preferences.core.preferencesOf(
            androidx.datastore.preferences.core.booleanPreferencesKey("vista_soft_glow") to true,
            androidx.datastore.preferences.core.booleanPreferencesKey("release_appearance_20260907") to true,
            androidx.datastore.preferences.core.stringPreferencesKey("theme_mode") to if (light) "light" else "dark",
        )
        settingsFixtureState.value = fixture
        listOf(com.zhisheng.weather.data.SettingsRepository, com.zhisheng.weather.data.SecretStore).forEach { repository ->
            repository.javaClass.getDeclaredField("store").apply { isAccessible = true }.set(repository, settingsFixtureStore)
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
            }
        }
        render(style = style) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                com.zhisheng.weather.ui.SettingsScreen({}, false, {}, {}, false, null, {}, "XIAOMI", emptyList(), "金昌市", false, {}, {}, null, initialSection = section)
            }
        }
    }
    @Test fun beta8ClassicSettingsData() = settingsFixture(0, InterfaceStyle.CLASSIC_TERMINAL)
    @Test fun beta8ClassicSettingsAppearance() = settingsFixture(2, InterfaceStyle.CLASSIC_TERMINAL)
    @Test fun beta8ClassicHome() = render(style = InterfaceStyle.CLASSIC_TERMINAL) {
        SimulatedWeatherSurface(weather, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = now, header = {
            TopBar(city.displayName, false, {}, {}, {})
        })
    }
    @Test fun beta8ClassicDaily() = render(style = InterfaceStyle.CLASSIC_TERMINAL) {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            SectionTitle(1, "未来五天", "", true)
            DailySection(weather.daily, "c", "kmh", 28800, Modifier, {})
        }
    }
    @Test fun beta8ClassicForecast() = render(style = InterfaceStyle.CLASSIC_TERMINAL) {
        DailyForecastScreen(city, weather.daily, null, "c", "kmh", 28800, {})
    }
    @Test fun beta8ClassicSearch() = render(style = InterfaceStyle.CLASSIC_TERMINAL) {
        com.zhisheng.weather.ui.SearchScreen({}, {})
    }
    @Test fun beta8ClassicLandscape() = render(style = InterfaceStyle.CLASSIC_TERMINAL) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            LandscapeWeatherCoreScreen(HomeUiState(selectedCity = city, weather = weather), {}, {}, {})
        }
    }
    @androidx.compose.runtime.Composable private fun feedbackDetailsContent() {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            TelemetryGrid(weather.current!!.copy(humidity = 99.0, cloudCover = 100.0, uvIndex = 1,
                visibility = 14.0, dewPoint = 10.0, windGust = 29.0),
                weather.daily.first(), "c", DisplayPrefs(), Modifier, city, 28800)
            Spacer(Modifier.height(20.dp))
            IndicesRow(weather.copy(carWashOk = false, sportsOk = true,
                extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "强"))),
                com.zhisheng.weather.data.LifeIndexMetric.defaultSelection, Modifier)
        }
    }
    @Test fun takeoverForecastAndRain() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            SectionTitle(1, "未来五天", "", true)
            VistaDailyOverview(weather.daily, "c", "kmh", 28800, Modifier, {})
            SectionTitle(2, "短时降水", "", false)
            PrecipCard(weather.copy(rainMinutes = (0..119).map { MinutePrecip(now + it * 60_000L, 0f) },
                rainMeta = RainMeta("XIAOMI", 1, now)), Modifier)
        }
    }
    @Test fun takeoverLuminary() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp)) {
                VistaLuminary(weather.daily.first().copy(moonrise = "01:55", moonset = "17:28"), 28800)
            }
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp)) {
                VistaLuminary(weather.daily.first().copy(moonrise = "23:51", moonset = "23:58"), 28800)
            }
        }
    }
    @Test fun takeoverAirAndIndices() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            SectionTitle(1, "空气质量", "", false)
            AqiCard(AqiInfo(value = 124, level = "轻度污染", standard = "中国", primary = "细颗粒物（PM2.5）",
                pm25 = "94", pm10 = "20",
                o3 = "75", no2 = "2", so2 = "0", co = "0.2", pollutantUnits = mapOf(
                    "pm2p5" to "μg/m³", "pm10" to "μg/m³", "o3" to "μg/m³", "no2" to "μg/m³",
                    "so2" to "μg/m³", "co" to "mg/m³")), Modifier)
            SectionTitle(2, "生活指数", "", false)
            IndicesRow(weather.copy(carWashOk = false, sportsOk = true,
                extraIndices = listOf(LifeIndexExtra("紫外线", "UV", "强"))),
                com.zhisheng.weather.data.LifeIndexMetric.defaultSelection, Modifier)
        }
    }
    @Test fun forecast() = render {
        DailyForecastScreen(city, weather.daily, null, "c", "kmh", 28800, {})
    }
    @Test fun precipitationDetail() = render {
        com.zhisheng.weather.ui.PrecipitationDetailScreen(weather.copy(
            rainMinutes = (0..60).map { MinutePrecip(now + it * 60_000L, if (it in 18..42) (1f - kotlin.math.abs(it - 30) / 13f) else 0f) },
            rainMeta = RainMeta("XIAOMI", 1, now)), city.displayName, {})
    }
    @Test fun settingsIsland() = render {
        Box(Modifier.fillMaxSize().zhishengScreen(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.zhisheng.weather.ui.VistaSettingsIsland(2, {}, Modifier.padding(20.dp))
        }
    }
    @Test fun beta7LongPressKey() = render {
        Box(Modifier.fillMaxSize().zhishengScreen(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            CityTouchSensor(active = false, scrolling = true, enabled = true, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
    @Test fun beta7KeyAtRest() = render(offsetMillis = 2_000L) {
        Box(Modifier.fillMaxSize().zhishengScreen(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            CityTouchSensor(active = false, scrolling = false, enabled = true, modifier = Modifier.padding(bottom = 20.dp))
        }
    }
    @Test fun beta7GlassDeformation() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            com.zhisheng.weather.ui.Text("松开", color = LocalZhishengPalette.current.text)
            com.zhisheng.weather.ui.LiquidTabLens(Modifier.size(84.dp, 60.dp), { 0f })
            com.zhisheng.weather.ui.Text("拖动形变", color = LocalZhishengPalette.current.text)
            com.zhisheng.weather.ui.LiquidTabLens(Modifier.size(100.dp, 57.dp), { 1f })
            com.zhisheng.weather.ui.VistaSettingsIsland(1, {})
        }
    }
    @Test fun citySearch() = render { com.zhisheng.weather.ui.SearchScreen({}, {}) }
    private val coast = CoastalForecast(TideTrend.RISING,
        (0..24).map { MarinePoint(now + it * 3_600_000L, kotlin.math.sin(it * 0.4) * 0.5) },
        null, null, 0.8, 120.0, 8.0, 0.6, 10.0, 21.0, 1.8, 75.0, 2.4,
        updatedAtMillis = now, zoneId = "Asia/Shanghai", windWaveHeightM = 0.3)
    @Test fun coastDetails() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) { AtlasCoastDetail(SceneWeatherData(coast = coast), "c") }
    }
    @Test fun coastWithoutTide() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AtlasCoastDetail(SceneWeatherData(coast = coast.copy(points = emptyList(), waveDirectionDeg = null, currentDirectionDeg = null)), "c")
        }
    }
    @Test fun starsDetails() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AtlasStarsDetail(SceneWeatherData(sky = SkyPhotographyForecast(null,
                StarForecast(78, "适合观星", now + 8 * 3_600_000L, now + 11 * 3_600_000L,
                    listOf("云量较少", "月光影响较小"), "20:00 后留意云量变化", 0.28),
                updatedAtMillis = now, zoneId = "Asia/Shanghai")), city)
        }
    }
    @Test fun airyFooter() = render {
        val history = weather.copy(current = null, hourly = emptyList(), daily = emptyList(), aqi = null,
            yesterday = YesterdayInfo(high = 26.0, low = 10.0, aqi = 80, condition = WeatherCondition.PARTLY_CLOUDY,
                weatherStart = WeatherCondition.CLEAR, weatherEnd = WeatherCondition.PARTLY_CLOUDY,
                sunrise = "06:43", sunset = "19:36", windDirectionStartDeg = 90.0, windSpeedStart = 12.0))
        SimulatedWeatherSurface(history, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, showPrecip = false, scanlines = false, bootAnim = false), referenceTimeMillis = now,
            header = { TopBar(city.displayName, false, {}, {}, {}) })
    }
    @Test fun airyAqiStates() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
            listOf(62 to "良", 175 to "中度污染", 350 to "严重污染").forEach { (value, label) ->
                SectionTitle(1, "空气质量", "", false)
                AqiCard(AqiInfo(value = value, level = label, standard = "中国"), Modifier)
            }
        }
    }
    @Test fun airyAtlas() = render {
        val scene = SceneWeatherData(sky = SkyPhotographyForecast(
            GlowForecast(GlowKind.DUSK, "可以碰碰运气", now + 6 * 3_600_000L, now + 7 * 3_600_000L,
                listOf("有中高云可被染色，云量组合尚可")),
            null, updatedAtMillis = now, zoneId = "Asia/Shanghai"))
        Column(Modifier.fillMaxSize().zhishengScreen().vistaSky(weather, false)
            .verticalScroll(rememberScrollState())) {
            SectionTitle(1, "空气质量", "", false)
            AqiCard(weather.aqi!!, Modifier)
            AtlasSectionHeading(2)
            WeatherAtlas(weather, scene, city, "c", DisplayPrefs(showSkyPhotography = true, showSpacetime = true),
                onHistoryClick = {}, onRadarClick = {})
        }
    }
    @Test fun glowDetails() = render {
        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AtlasGlowDetail(SceneWeatherData(sky = SkyPhotographyForecast(
                GlowForecast(GlowKind.DUSK, "可以碰碰运气", now + 6 * 3_600_000L, now + 7 * 3_600_000L,
                    listOf("有中高云可被染色，云量组合尚可", "本地点低云较少；远处地平线仍需现场观察"),
                    "有染色云层条件；颜色、面积取决于远处光路，不能保证火烧云"),
                null, updatedAtMillis = now, zoneId = "Asia/Shanghai")), city)
        }
    }
    @Test fun mapToolbar() = render {
        Box(Modifier.fillMaxSize().zhishengScreen()) {
            com.zhisheng.weather.ui.components.VistaMapToolbar("降水雷达", "金昌市 · 更新于 12:00", {}) {
                com.zhisheng.weather.ui.components.VistaMapTool(com.zhisheng.weather.R.drawable.ph_crosshair, "回到当前城市", {})
                com.zhisheng.weather.ui.components.VistaMapTool(com.zhisheng.weather.R.drawable.ph_info, "图层说明", {})
                com.zhisheng.weather.ui.components.VistaMapTool(com.zhisheng.weather.R.drawable.ph_arrow_clockwise, "刷新", {})
            }
        }
    }
    @Test fun historyWeek() = render {
        Box(Modifier.fillMaxSize().zhishengScreen()) {
            val end = java.time.Instant.ofEpochMilli(now).atOffset(java.time.ZoneOffset.ofHours(8)).toLocalDate().minusDays(1)
            com.zhisheng.weather.ui.RecentWeekContent(city, RecentWeatherWeek((0..6).map {
                HistoricalDay(end.minusDays((6 - it).toLong()).toString(), weatherCode = if (it == 3) 61 else 0,
                    high = 25.0 + it, low = 14.0 + it % 3, precipitationMm = if (it == 3) 1.2 else 0.0, windMaxKmh = 12.0)
            }), "c", "kmh")
        }
    }
    @Test fun atmosphereLab() = render {
        com.zhisheng.weather.ui.AtmosphereLabScreen(com.zhisheng.weather.data.AmbienceLevel.INTENSE, {})
    }
    @Test fun rain() = render {
        val rainy = weather.copy(current = weather.current!!.copy(condition = WeatherCondition.RAIN, weatherText = "小雨"))
        SimulatedWeatherSurface(rainy, city, DisplayPrefs(showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, scanlines = false, bootAnim = false), referenceTimeMillis = now, header = {
            TopBar(city.displayName, false, {}, {}, {})
        })
    }
    @Test fun landscape() = render {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            LandscapeWeatherCoreScreen(HomeUiState(selectedCity = city, weather = weather), {}, {}, {})
        }
    }
}

