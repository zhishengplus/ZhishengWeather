package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.home.*
import com.zhisheng.weather.ui.theme.*
import com.zhisheng.weather.widget.*
import org.junit.Rule
import org.junit.Test

/** Website assets only: public campus examples and offline weather, no user state/network. */
class WebsiteCampusCaptureTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436),
        theme = "android:Theme.Material.Light.NoActionBar", useDeviceResolution = true,
        snapshotHandler = object : app.cash.paparazzi.SnapshotHandler {
            override fun newFrameHandler(snapshot: app.cash.paparazzi.Snapshot, frameCount: Int, fps: Int) =
                java.lang.reflect.Proxy.newProxyInstance(app.cash.paparazzi.SnapshotHandler.FrameHandler::class.java.classLoader,
                    arrayOf(app.cash.paparazzi.SnapshotHandler.FrameHandler::class.java), run {
                    var frame = 0
                    java.lang.reflect.InvocationHandler { _, method, args ->
                      if (method.name == "handle" && ++frame == frameCount) {
                        val test = snapshot.testName
                        val target = java.io.File("src/test/snapshots/images",
                            "${test.packageName}_${test.className}_${test.methodName}_${snapshot.name}.png")
                        target.parentFile.mkdirs()
                        // Android test compilation excludes java.desktop; the host renderer supplies it at runtime.
                        Class.forName("javax.imageio.ImageIO").getMethod("write", Class.forName("java.awt.image.RenderedImage"),
                            String::class.java, java.io.File::class.java).invoke(null, args!![0], "png", target)
                      }
                      null
                    }
                }) as app.cash.paparazzi.SnapshotHandler.FrameHandler
            override fun close() {}
        })
    private val tsinghua = City("清华大学", "北京市海淀区", 40.0032, 116.3269, "geo:40.0032,116.3269", street = "清华园")
    private val peking = City("北京大学", "北京市海淀区", 39.9928, 116.3051, "geo:39.9928,116.3051", street = "燕园")
    private val chengdu = City("成都大学", "成都市龙泉驿区", 30.6508, 104.1887, "geo:30.6508,104.1887", street = "十陵校区")

    private fun capture(name: String, city: City, condition: WeatherCondition, text: String,
        minutes: Int = 14 * 60, altitude: Float = 35f, light: Boolean = true,
        style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA, landscape: Boolean = false) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1125, screenHeight = if (name == "tsinghua-classic-full") 5500 else 2436,
            orientation = if (landscape) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val night = altitude < -4f
        val sample = simulatedWeather(AtmosphereScenario(name, text, condition, night = night, minuteOfDay = minutes), WeatherIntensity.MODERATE)
        val data = sample.copy(current = sample.current!!.copy(pressure = 1012.0,
            uvIndex = if (night) 0 else if (condition.isPrecipitation || condition == WeatherCondition.FOG) 1 else 4,
            cloudCover = if (condition.isPrecipitation || condition == WeatherCondition.FOG) 94.0 else 22.0))
        val prefs = DisplayPrefs(ambience = AmbienceLevel.VIVID, scanlines = false, bootAnim = false,
            showSpacetime = false, showSkyPhotography = false, showCoastWeather = false)
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalNaturalPreviewPhase provides .173,
                    LocalWeatherPreviewTime provides data.updateTime,
                    LocalSolarSkyPreview provides SolarSkyState(altitude, 265f, false)) {
                    ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                        if (landscape) LandscapeWeatherCoreScreen(HomeUiState(selectedCity = city, weather = data, prefs = prefs), {}, {})
                        else SimulatedWeatherSurface(data, city, prefs, night = night,
                            referenceTimeMillis = data.updateTime, livingSkyOverride = true,
                            header = { TopBar(city.displayName, false, {}, {}, {}) })
                    }
                }
            }
        }
        paparazzi.snapshot(view, name, offsetMillis = 1_000)
    }
    @Test fun campusWeatherScenes() {
        capture("tsinghua-clear", tsinghua, WeatherCondition.CLEAR, "晴")
        capture("peking-dusk", peking, WeatherCondition.PARTLY_CLOUDY, "多云", 18 * 60 + 26, -1.5f)
        capture("chengdu-rain", chengdu, WeatherCondition.RAIN, "雨")
        capture("peking-snow", peking, WeatherCondition.SNOW, "雪", 10 * 60, 20f)
        capture("chengdu-fog", chengdu, WeatherCondition.FOG, "雾", 7 * 60 + 15, 5f)
        capture("tsinghua-night", tsinghua, WeatherCondition.CLEAR_NIGHT, "晴", 22 * 60, -24f, false)
        capture("tsinghua-classic", tsinghua, WeatherCondition.CLEAR, "晴", style = InterfaceStyle.CLASSIC_TERMINAL, light = false)
        capture("peking-landscape", peking, WeatherCondition.PARTLY_CLOUDY, "多云", 18 * 60 + 26, -1.5f, landscape = true)
    }
    @Test fun campusClassicFull() {
        capture("tsinghua-classic-full", tsinghua, WeatherCondition.CLEAR, "晴", style = InterfaceStyle.CLASSIC_TERMINAL, light = false)
    }
    @Test fun campusWidgets() {
        val sample = widgetPreviewSample()
        val data = sample.copy(city = tsinghua)
        fun dp(n: Int) = (n * paparazzi.context.resources.displayMetrics.density).toInt()
        for (kind in listOf(WidgetKind.NOW, WidgetKind.WEEK, WidgetKind.SCENE)) {
            val height = if (kind == WidgetKind.SCENE) 380 else if (kind == WidgetKind.NOW) 194 else kind.height
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                screenWidth = minOf(dp(kind.width), dp(height)), screenHeight = maxOf(dp(kind.width), dp(height)),
                orientation = if (kind.width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
            val frame = android.widget.FrameLayout(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(dp(kind.width), dp(height))
                setBackgroundColor(android.graphics.Color.rgb(183, 206, 215))
            }
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, kind),
                WidgetConfig(theme = "light", opacity = 60), data, kind.width, height,
                now = requireNotNull(data.weather.updateTime), interactive = false, livingSkyEnabled = true,
                previewBackground = android.graphics.Color.rgb(183, 206, 215), ambienceLevel = AmbienceLevel.VIVID).apply(paparazzi.context, frame)
            view.findViewById<android.widget.TextClock>(com.zhisheng.weather.R.id.widget_time)?.apply {
                format12Hour = "'09:41'"; format24Hour = "'09:41'"
            }
            view.findViewById<android.widget.TextClock>(com.zhisheng.weather.R.id.widget_date)?.apply {
                format12Hour = "'10月6日 周二'"; format24Hour = "'10月6日 周二'"
            }
            frame.addView(view, android.widget.FrameLayout.LayoutParams(dp(kind.width), dp(height)))
            paparazzi.snapshot(frame, "campus-widget-${kind.name.lowercase()}")
        }
    }
    private fun screen(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436))
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true, LocalNaturalPreviewPhase provides .173,
                LocalWeatherPreviewTime provides java.time.LocalDate.now(java.time.ZoneOffset.ofHours(8))
                    .atTime(14, 0).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()) {
                ZhishengWeatherTheme(isLight = true, interfaceStyle = InterfaceStyle.PHOSPHOR_VISTA, softGlow = false) { content() }
            } }
        }
        if (name == "campus-city-list") {
            // Advance native frames while isolated DataStore reads complete; keep the final frame.
            paparazzi.gif(view, name, start = 0, end = 3_000, fps = 4)
        } else paparazzi.snapshot(view, name, offsetMillis = 1_000)
        view.disposeComposition()
    }
    @Test fun campusFeatureTour() {
        val baseRain = simulatedWeather(AtmosphereScenario("RAN", "雨", WeatherCondition.RAIN, minuteOfDay = 14 * 60), WeatherIntensity.MODERATE)
        val rain = baseRain.copy(rainMinutes = (0..24).map { i ->
            com.zhisheng.weather.model.MinutePrecip(requireNotNull(baseRain.updateTime) + i * 5 * 60_000L,
                if (i > 10) 0f else (0.35f + kotlin.math.sin(i * .35f) * .28f).coerceAtLeast(.08f))
        }, rainMeta = com.zhisheng.weather.model.RainMeta("XIAOMI", 5, baseRain.updateTime, 120))
        val sunny = simulatedWeather(AtmosphereScenario("CLR", "晴", WeatherCondition.CLEAR), WeatherIntensity.LIGHT)
        screen("campus-hourly") { HourlyDetailScreen(sunny, tsinghua.displayName, "c", "kmh", {}) }
        screen("campus-daily") { DailyForecastScreen(peking, sunny.daily, null, "c", "kmh", 28800, {}) }
        screen("campus-precipitation") { PrecipitationDetailScreen(rain, chengdu.displayName, {}) }
        screen("campus-air-life") {
            Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
                TopBar(tsinghua.displayName, false, {}, {}, {})
                SectionTitle(1, "空气质量", "", false)
                AqiCard(sunny.aqi!!, Modifier)
                SectionTitle(2, "生活指数", "", false)
                IndicesRow(sunny.copy(carWashOk = true, sportsOk = true), com.zhisheng.weather.data.LifeIndexMetric.defaultSelection, Modifier)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp)) { VistaLuminary(sunny.daily.first(), 28800) }
            }
        }
        screen("campus-city-management") {
            CityDeckOverlay(true, true, listOf(tsinghua.copy(name = "海淀区", street = "清华大学"),
                peking.copy(name = "海淀区", street = "北京大学", isFavorite = true),
                chengdu.copy(name = "龙泉驿区", street = "成都大学")), 1f, 1f, {}, {}, {}, {}, outline = { selected, modifier ->
                val palette = com.zhisheng.weather.ui.theme.LocalZhishengPalette.current
                val bitmap = androidx.compose.runtime.remember(selected) { kotlinx.coroutines.runBlocking {
                    com.zhisheng.weather.data.BoundaryRepository.ensureLoaded(paparazzi.context)
                    val entry = requireNotNull(com.zhisheng.weather.data.BoundaryRepository.resolve(selected.name, selected.affiliation, selected.latitude, selected.longitude))
                    renderCityMap(requireNotNull(com.zhisheng.weather.data.BoundaryRepository.geometry(entry)), true, palette.cyan, palette.mint, 600, 540)
                } }
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), "校园所在行政区", modifier)
            })
        }
        screen("campus-widget-editor") {
            val sample = widgetPreviewSample().copy(city = tsinghua)
            WidgetStudio(WidgetStyle.VISTA, null, WidgetKind.SCENE, WidgetConfig.defaults(WidgetStyle.VISTA),
                listOf(tsinghua, peking, chengdu), tsinghua, null, NotificationSettings(), {}, {}, { _, _ -> }, sample, WidgetPinState.IDLE)
        }
    }
    @Test fun campusSettings() {
        val fixture = androidx.datastore.preferences.core.preferencesOf(
            androidx.datastore.preferences.core.stringPreferencesKey("theme_mode") to "light",
            androidx.datastore.preferences.core.booleanPreferencesKey("release_appearance_20260907") to true,
        )
        val state = kotlinx.coroutines.flow.MutableStateFlow(fixture)
        val store = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = state
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) = transform(state.value).also { state.value = it }
        }
        listOf(com.zhisheng.weather.data.SettingsRepository, com.zhisheng.weather.data.SecretStore).forEach { repository ->
            repository.javaClass.getDeclaredField("store").apply { isAccessible = true }.set(repository, store)
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
            }
        }
        for (section in listOf(0, 2)) screen("campus-settings-$section") {
            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                SettingsScreen({}, {}, false, null, {}, "XIAOMI", emptyList(), tsinghua.displayName, false, {}, {}, null, initialSection = section)
            }
        }
    }
    @Test fun campusCityListAndHistory() {
        val cities = listOf(tsinghua, peking.copy(isFavorite = true), chengdu)
        com.zhisheng.weather.data.CityRepository.init(paparazzi.context)
        val weather = simulatedWeather(AtmosphereScenario("CLR", "晴", WeatherCondition.CLEAR), WeatherIntensity.LIGHT)
        kotlinx.coroutines.runBlocking {
            cities.forEach { com.zhisheng.weather.data.CityRepository.addCity(it) }
            cities.forEachIndexed { index, city ->
                val condition = if (index == 2) WeatherCondition.RAIN else WeatherCondition.CLEAR
                val value = weather.copy(dataSource = "XIAOMI", blockSources = emptyMap(),
                    fetchedAt = System.currentTimeMillis(), updateTime = System.currentTimeMillis(),
                    current = weather.current!!.copy(temperature = 19.0 + index * 2, condition = condition, weatherText = if (index == 2) "雨" else "晴"))
                com.zhisheng.weather.data.WeatherCache.save(paparazzi.context, city.locationKey, value)
            }
        }
        screen("campus-city-list") {
            Column(Modifier.fillMaxSize().zhishengScreen().padding(16.dp)) {
                TopBar("城市管理", false, {}, {}, {})
                CityWeatherList(HomeUiState(cities = cities, selectedCity = tsinghua, prefs = DisplayPrefs(ambience = AmbienceLevel.VIVID)),
                    Modifier.fillMaxSize(), {}, {}, {}, {}, active = true)
            }
        }
        screen("campus-history") {
            val end = java.time.LocalDate.now(java.time.ZoneOffset.ofHours(8)).minusDays(1)
            Box(Modifier.fillMaxSize().zhishengScreen()) {
                RecentWeekContent(peking, com.zhisheng.weather.model.RecentWeatherWeek((0..6).map {
                    com.zhisheng.weather.model.HistoricalDay(end.minusDays((6-it).toLong()).toString(),
                        weatherCode = if (it==3) 61 else 0, high = 23.0 + it, low = 13.0 + it%3,
                        precipitationMm = if (it==3) 2.4 else 0.0, windMaxKmh = 12.0)
                }), "c", "kmh")
            }
        }
    }
}
