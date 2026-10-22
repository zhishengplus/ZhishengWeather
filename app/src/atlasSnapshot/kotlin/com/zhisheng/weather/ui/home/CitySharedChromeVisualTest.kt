package com.zhisheng.weather.ui.home

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.DisplayPrefs
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.LocalWeatherPreviewTime
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Production pages and the production shared overlay, driven by native finger events. */
class CitySharedChromeVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val now = java.time.Instant.parse("2026-10-18T04:00:00Z").toEpochMilli()
    private val cities = listOf("金川区", "成都市", "上海市").mapIndexed { index, name ->
        City(name, "原生离线夹具", 30.0 + index, 105.0, "shared-chrome-$index")
    }
    private val temperatures = listOf("12", "23", "-5")
    private val prefs = DisplayPrefs(showAqi = false, showIndices = false, showYesterday = false,
        showPrecip = false, showTelemetry = false, showSpacetime = false,
        showSkyPhotography = false, showCoastWeather = false, bootAnim = false,
        homeBriefingStyle = HomeBriefingStyle.OFF,
        moduleOrder = listOf(HomeModule.HOURLY, HomeModule.DAILY))
    private fun weather(index: Int) = WeatherData(
        current = CurrentWeather(temperature = temperatures[index].toDouble(),
            condition = WeatherCondition.CLEAR, weatherText = "晴", windSpeed = 5.0, windDirectionDeg = 90.0),
        hourly = (-1..23).map { HourlyWeather(now + it * 3_600_000L,
            30.0 + (it + 1) % 5, WeatherCondition.CLEAR, windSpeed = 5.0, windDirectionDeg = 90.0, precipProb = 0) },
        daily = (0..9).map { DailyWeather(now + it * 86_400_000L,
            34.0, 28.0, WeatherCondition.CLEAR, weatherText = "晴", windSpeed = 5.0, windDirectionDeg = 90.0) },
        dataSource = "OFFLINE", fetchedAt = now, updateTime = now, utcOffsetSeconds = 28_800,
    )

    private inner class Fixture(private val missing: Int? = null, initialCity: Int = 0,
        val cities: List<City> = this@CitySharedChromeVisualTest.cities) {
        val selected = mutableStateOf(cities[initialCity].locationKey)
        val requests = mutableListOf<String>()
        var pager: PagerState? = null
        var chrome: CitySharedChromeState? = null
        var menuCalls = 0
        var refreshCalls = 0
        var settingsCalls = 0
        private val data = cities.indices.associateWith { if (it == missing) null else weather(it) }

        @Composable fun Surface() {
            val shared = remember { CitySharedChromeState() }
            chrome = shared
            val city = cities.first { it.locationKey == selected.value }
            val ui = HomeUiState(cities = cities, selectedCity = city,
                weather = data[cities.indexOf(city)], prefs = prefs, citiesLoaded = true)
            CompositionLocalProvider(LocalCitySharedChrome provides shared) {
                CityWeatherPager(cities, selected.value, true, {
                    requests += it
                    selected.value = it
                }, Modifier.fillMaxSize(), onPagerReady = { pager = it }, overlay = { state ->
                    CitySharedChromeHost(shared, cities, state, ui,
                        { menuCalls++ }, { refreshCalls++ }, { settingsCalls++ })
                }) { pageCity, state ->
                    HomeCityPage(pageCity, data[cities.indexOf(pageCity)], null, ui,
                        pageCity.locationKey == selected.value, null, 0, {}, {},
                        { menuCalls++ }, { refreshCalls++ }, { settingsCalls++ },
                        { CityPageIndicator(cities, pageCity, state) }, {}, {}, {}, {}, {}, {})
                }
            }
        }

        fun ready(): Boolean {
            val state = chrome ?: return false
            val entry = state.entries[selected.value] ?: return false
            return state.headerHeight > 0 && (entry.data == null ||
                entry.heroAttached && entry.heroPortalReady && entry.heroHeight > 0)
        }

        fun position(index: Int): Pair<Int, Int> {
            val list = chrome!!.entries[cities[index].locationKey]!!.scroll
            return list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        }
        fun scrolling(index: Int) = chrome!!.entries[cities[index].locationKey]?.scroll?.isScrollInProgress == true
        fun diagnostic() = "ready=${ready()}, selected=${selected.value}, header=${chrome?.headerHeight}, " +
            "pager=${pager?.let { "${it.currentPage}/${it.currentPageOffsetFraction},settled=${it.settledPage},scroll=${it.isScrollInProgress}" }}, " +
            "requests=$requests, entries=${chrome?.entries?.mapValues { (_, entry) ->
                "data=${entry.data?.current?.temperature},heroHeight=${entry.heroHeight},attached=${entry.heroAttached},portalReady=${entry.heroPortalReady},heroTop=${entry.heroTop}"
            }}"
    }

    @Test fun sharedHeaderAndHeroFitBothSkinsThemesNarrowTypeAndShortLandscape() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false))
            for ((width, height, font) in listOf(Triple(360, 760, 1f), Triple(320, 620, 2f), Triple(640, 360, 2f))) {
                val fixture = Fixture()
                capture("shared_chrome_layout_${style.key}_${light}_${width}", fixture,
                    width, height, font, light, style, duration = 1_000) { _, root, _ ->
                    if (!fixture.ready() || heroNodes(root).size != 1) false else {
                        assertEquals(listOf("12"), heroValues(root))
                        assertTextFits(root)
                        assertHeaderControlsFit(root)
                        assertEquals("Only the selected city's heading may be accessible", 1,
                            texts(root).count { it == cities[0].name })
                        true
                    }
                }
            }
    }

    @Test fun nativeCityDragKeepsTheHeaderAndTemperatureAnchoredUntilSelectionSettles() {
        for (style in InterfaceStyle.entries) {
            val fixture = Fixture()
            var phase = 0
            var titleX = 0f
            var temperatureX = 0f
            var temperatureY = 0f
            var heroHeight = 0
            var settingsX = 0f
            var swipeY = 0f
            var released = 0L
            var sampledDrag = false
            capture("shared_chrome_drag_${style.key}", fixture, style = style, duration = 3_000) { host, root, time ->
                when {
                    phase == 0 && fixture.ready() && heroValues(root) == listOf("12") -> {
                        titleX = textNode(root, cities[0].name).boundsInRoot.left
                        temperatureX = heroNodes(root).single().boundsInRoot.left
                        temperatureY = heroNodes(root).single().boundsInRoot.top
                        heroHeight = fixture.chrome!!.entries[cities[0].locationKey]!!.heroHeight
                        settingsX = control(root, "设置").boundsInRoot.left
                        swipeY = swipeBand(root, fixture)
                        touch(host, MotionEvent.ACTION_DOWN, 300f, swipeY, time)
                        phase++; false
                    }
                    phase in 1..10 -> {
                        if (phase >= 4 && abs(fixture.pager!!.currentPageOffsetFraction) > .1f) {
                            assertEquals("The header container must stay fixed during a real drag", settingsX,
                                control(root, "设置").boundsInRoot.left, 1f / 3f)
                            assertTrue("The city heading may move at most its restrained 16dp crossfade distance",
                                abs(textNode(root, cities[0].name).boundsInRoot.left - titleX) <= 16f + 1f / 3f)
                            assertEquals("The large temperature must not follow the page horizontally", temperatureX,
                                heroNodes(root).single().boundsInRoot.left, 1f / 3f)
                            assertEquals("The shared hero must not jump vertically while both lists remain at the top", temperatureY,
                                heroNodes(root).single().boundsInRoot.top, 1f / 3f)
                            assertEquals("Measuring the shared hero must not change its retained list-slot height during a drag", heroHeight,
                                fixture.chrome!!.entries[cities[0].locationKey]!!.heroHeight)
                            sampledDrag = true
                        }
                        assertTrue("A held drag must not select or fetch another city", fixture.requests.isEmpty())
                        touch(host, MotionEvent.ACTION_MOVE, 300f - phase * 24f, swipeY, time)
                        phase++; false
                    }
                    phase == 11 -> {
                        touch(host, MotionEvent.ACTION_UP, 60f, swipeY, time)
                        released = time; phase++; false
                    }
                    phase == 12 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertTrue("The fixture must sample actual page movement before release", sampledDrag)
                        assertEquals(1, fixture.pager!!.settledPage)
                        assertEquals(listOf(cities[1].locationKey), fixture.requests)
                        assertEquals("Only one selected-city hero may remain accessible", listOf("23"), heroValues(root))
                        assertEquals(1, texts(root).count { it == cities[1].name })
                        assertFalse(texts(root).contains(cities[0].name))
                        assertHeaderControlsFit(root)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @Test fun nativeDragsStartingOnTheSharedHeadingOrSettingsButtonStillPageAndTap() {
        for (style in InterfaceStyle.entries) for (fromButton in listOf(false, true)) {
            val fixture = Fixture(initialCity = 1)
            val target = if (fromButton) 2 else 0
            val direction = if (fromButton) -1f else 1f
            var phase = 0
            var startX = 0f
            var startY = 0f
            var released = 0L
            var moved = false
            capture("shared_chrome_top_drag_${style.key}_$fromButton", fixture, style = style,
                duration = 3_500) { host, root, time ->
                fun source() = if (fromButton) control(root, "设置")
                    else textNode(root, cities[if (phase < 12) 1 else target].name)
                when {
                    phase == 0 && fixture.ready() && heroValues(root) == listOf("23") -> {
                        val bounds = source().boundsInRoot
                        startX = bounds.center.x; startY = bounds.center.y
                        touch(host, MotionEvent.ACTION_DOWN, startX, startY, time); phase++; false
                    }
                    phase in 1..10 -> {
                        assertTrue("A header drag must not request a city while the finger is held", fixture.requests.isEmpty())
                        assertEquals("A dragged title must not open the menu", 0, fixture.menuCalls)
                        assertEquals("A dragged settings button must not open settings", 0, fixture.settingsCalls)
                        if (abs(fixture.pager!!.currentPageOffsetFraction) > .1f) moved = true
                        touch(host, MotionEvent.ACTION_MOVE, startX + direction * phase * 24f, startY, time)
                        phase++; false
                    }
                    phase == 11 -> {
                        touch(host, MotionEvent.ACTION_UP, startX + direction * 240f, startY, time)
                        released = time; phase++; false
                    }
                    phase == 12 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertTrue("The actual overlay header must move the production pager", moved)
                        assertEquals(target, fixture.pager!!.settledPage)
                        assertEquals(listOf(cities[target].locationKey), fixture.requests)
                        assertEquals(listOf(temperatures[target]), heroValues(root))
                        assertEquals(0, fixture.menuCalls)
                        assertEquals(0, fixture.settingsCalls)
                        val bounds = source().boundsInRoot
                        startX = bounds.center.x; startY = bounds.center.y
                        touch(host, MotionEvent.ACTION_DOWN, startX, startY, time); phase++; false
                    }
                    phase == 13 -> { touch(host, MotionEvent.ACTION_UP, startX, startY, time); phase++; false }
                    phase == 14 -> {
                        assertEquals("A subsequent native title tap must still open the menu", if (fromButton) 0 else 1, fixture.menuCalls)
                        assertEquals("A subsequent native settings tap must still open settings", if (fromButton) 1 else 0, fixture.settingsCalls)
                        assertEquals(0, fixture.refreshCalls)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @Test fun aLongNeighbourAddressCannotChangeTheSelectedHeaderHeightDuringADrag() {
        val longAddress = "春熙路街道红星路三段国际金融中心商业街区东侧步行广场"
        val variedCities = cities.mapIndexed { index, city ->
            if (index == 1) city.copy(street = longAddress) else city
        }
        for (style in InterfaceStyle.entries) {
            val fixture = Fixture(cities = variedCities)
            var phase = 0
            var headerHeight = 0
            var heroTop = 0f
            var swipeY = 0f
            var released = 0L
            var moved = false
            capture("shared_chrome_long_neighbour_${style.key}", fixture, width = 320, height = 700,
                font = 2f, style = style, duration = 3_500) { host, root, time ->
                when {
                    phase == 0 && fixture.ready() && heroValues(root) == listOf("12") -> {
                        headerHeight = fixture.chrome!!.headerHeight
                        heroTop = fixture.chrome!!.entries[cities[0].locationKey]!!.heroTop
                        swipeY = swipeBand(root, fixture)
                        touch(host, MotionEvent.ACTION_DOWN, 280f, swipeY, time); phase++; false
                    }
                    phase in 1..10 -> {
                        assertEquals("An unselected multiline address must not inflate the shared header", headerHeight,
                            fixture.chrome!!.headerHeight)
                        assertEquals("A hidden neighbour must not move the selected list's hero anchor", heroTop,
                            fixture.chrome!!.entries[cities[0].locationKey]!!.heroTop, 1f / 3f)
                        assertEquals(0 to 0, fixture.position(0))
                        assertTrue(fixture.requests.isEmpty())
                        if (abs(fixture.pager!!.currentPageOffsetFraction) > .1f) moved = true
                        touch(host, MotionEvent.ACTION_MOVE, 280f - phase * 23f, swipeY, time); phase++; false
                    }
                    phase == 11 -> {
                        touch(host, MotionEvent.ACTION_UP, 50f, swipeY, time); released = time; phase++; false
                    }
                    phase == 12 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertTrue("The long-title test must contain a real native drag", moved)
                        assertEquals(1, fixture.pager!!.settledPage)
                        assertEquals(listOf(cities[1].locationKey), fixture.requests)
                        assertTrue("After selection, the new city must receive enough height for its real multiline address",
                            fixture.chrome!!.headerHeight > headerHeight)
                        val address = if (style == InterfaceStyle.PHOSPHOR_VISTA) longAddress else variedCities[1].displayName
                        assertTrue("The neighbouring address must really wrap in this fixture", layouts(textNode(root, address)).any { it.lineCount > 1 })
                        assertHeaderControlsFit(root)
                        assertTextFits(root)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @Test fun nativeVerticalScrollsKeepEachCitysOwnPositionAcrossARoundTrip() {
        for (style in InterfaceStyle.entries) {
            val fixture = Fixture()
            var phase = 0
            var released = 0L
            var swipeY = 0f
            var firstPosition = 0 to 0
            var secondPosition = 0 to 0
            capture("shared_chrome_scroll_${style.key}", fixture, height = 800, style = style,
                duration = 9_000) { host, root, time ->
                when {
                    phase == 0 && fixture.ready() && heroValues(root) == listOf("12") -> {
                        touch(host, MotionEvent.ACTION_DOWN, 180f, 650f, time); phase++; false
                    }
                    phase in 1..8 -> {
                        touch(host, MotionEvent.ACTION_MOVE, 180f, 650f - phase * 40f, time); phase++; false
                    }
                    phase == 9 -> {
                        touch(host, MotionEvent.ACTION_UP, 180f, 330f, time); released = time; phase++; false
                    }
                    phase == 10 && time - released > 800 && !fixture.scrolling(0) -> {
                        firstPosition = fixture.position(0)
                        assertTrue("The real first-city list must actually scroll", firstPosition != (0 to 0))
                        swipeY = swipeBand(root, fixture)
                        touch(host, MotionEvent.ACTION_DOWN, 300f, swipeY, time); phase++; false
                    }
                    phase in 11..20 -> {
                        touch(host, MotionEvent.ACTION_MOVE, 300f - (phase - 10) * 24f, swipeY, time); phase++; false
                    }
                    phase == 21 -> {
                        touch(host, MotionEvent.ACTION_UP, 60f, swipeY, time); released = time; phase++; false
                    }
                    phase == 22 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertEquals(1, fixture.pager!!.settledPage)
                        assertEquals(firstPosition, fixture.position(0))
                        assertEquals("The next city must start with its own top position", 0 to 0, fixture.position(1))
                        touch(host, MotionEvent.ACTION_DOWN, 180f, 650f, time); phase++; false
                    }
                    phase in 23..30 -> {
                        touch(host, MotionEvent.ACTION_MOVE, 180f, 650f - (phase - 22) * 25f, time); phase++; false
                    }
                    phase == 31 -> {
                        touch(host, MotionEvent.ACTION_UP, 180f, 450f, time); released = time; phase++; false
                    }
                    phase == 32 && time - released > 800 && !fixture.scrolling(1) -> {
                        secondPosition = fixture.position(1)
                        assertTrue("The second-city list must actually scroll independently", secondPosition != (0 to 0))
                        swipeY = swipeBand(root, fixture)
                        touch(host, MotionEvent.ACTION_DOWN, 60f, swipeY, time); phase++; false
                    }
                    phase in 33..42 -> {
                        touch(host, MotionEvent.ACTION_MOVE, 60f + (phase - 32) * 24f, swipeY, time); phase++; false
                    }
                    phase == 43 -> {
                        touch(host, MotionEvent.ACTION_UP, 300f, swipeY, time); released = time; phase++; false
                    }
                    phase == 44 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertEquals(0, fixture.pager!!.settledPage)
                        assertEquals("The first city's vertical position must survive the complete round trip", firstPosition, fixture.position(0))
                        assertEquals(secondPosition, fixture.position(1))
                        assertEquals(listOf(cities[1].locationKey, cities[0].locationKey), fixture.requests)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    @Test fun aMissingCacheNeverBorrowsAnotherTemperatureAndHeaderActionsRemainNative() {
        for (style in InterfaceStyle.entries) {
            val fixture = Fixture(missing = 1)
            var phase = 0
            var released = 0L
            var swipeY = 0f
            var tapX = 0f
            var tapY = 0f
            capture("shared_chrome_missing_${style.key}", fixture, style = style, duration = 4_000) { host, root, time ->
                when {
                    phase == 0 && fixture.ready() && heroValues(root) == listOf("12") -> {
                        swipeY = swipeBand(root, fixture)
                        touch(host, MotionEvent.ACTION_DOWN, 300f, swipeY, time); phase++; false
                    }
                    phase in 1..10 -> {
                        touch(host, MotionEvent.ACTION_MOVE, 300f - phase * 24f, swipeY, time); phase++; false
                    }
                    phase == 11 -> {
                        touch(host, MotionEvent.ACTION_UP, 60f, swipeY, time); released = time; phase++; false
                    }
                    phase == 12 && time - released > 750 && !fixture.pager!!.isScrollInProgress -> {
                        assertEquals(1, fixture.pager!!.settledPage)
                        assertTrue("An uncached city must not expose any other city's main temperature", heroValues(root).isEmpty())
                        assertTrue(texts(root).contains("—°"))
                        assertTrue(texts(root).contains("正在获取天气"))
                        assertHeaderControlsFit(root)
                        val bounds = control(root, "设置").boundsInRoot
                        tapX = bounds.center.x; tapY = bounds.center.y
                        touch(host, MotionEvent.ACTION_DOWN, tapX, tapY, time); phase++; false
                    }
                    phase == 13 -> { touch(host, MotionEvent.ACTION_UP, tapX, tapY, time); phase++; false }
                    phase == 14 -> {
                        assertEquals(1, fixture.settingsCalls)
                        val bounds = control(root, "刷新").boundsInRoot
                        tapX = bounds.center.x; tapY = bounds.center.y
                        touch(host, MotionEvent.ACTION_DOWN, tapX, tapY, time); phase++; false
                    }
                    phase == 15 -> { touch(host, MotionEvent.ACTION_UP, tapX, tapY, time); phase++; false }
                    phase == 16 -> {
                        assertEquals(1, fixture.refreshCalls)
                        val bounds = control(root, "城市列表").boundsInRoot
                        tapX = bounds.center.x; tapY = bounds.center.y
                        touch(host, MotionEvent.ACTION_DOWN, tapX, tapY, time); phase++; false
                    }
                    phase == 17 -> { touch(host, MotionEvent.ACTION_UP, tapX, tapY, time); phase++; false }
                    phase == 18 -> {
                        assertEquals(1, fixture.menuCalls)
                        assertEquals(1, fixture.refreshCalls)
                        assertEquals(1, fixture.settingsCalls)
                        assertEquals(listOf(cities[1].locationKey), fixture.requests)
                        assertTrue(heroValues(root).isEmpty())
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun capture(name: String, fixture: Fixture, width: Int = 360, height: Int = 760,
        font: Float = 1f, light: Boolean = true, style: InterfaceStyle,
        duration: Long, check: (View, SemanticsNode, Long) -> Boolean) {
        scenarioContext = "$name/style=${style.key}/light=$light/${width}x$height/font=$font"
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width, screenHeight = height,
            density = Density.MEDIUM, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        var failure: Throwable? = null
        var layoutPasses = 0
        var lastDiagnostic = "No preDraw callback"
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides now) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                    Box(Modifier.fillMaxSize().background(ZhishengBg)) { fixture.Surface() }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    Snapshot.sendApplyNotifications()
                    layoutPasses++
                    // Registration, overlay measurement and slot replacement each need a real
                    // layout pass; exercise the settled production layout after those passes.
                    if (!checked) try {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view -> view.javaClass.methods
                            .firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                            ?.invoke(view) as? SemanticsOwner }?.unmergedRootSemanticsNode ?: error("Missing Compose semantics")
                        lastDiagnostic = "passes=$layoutPasses; ${fixture.diagnostic()}; heroValues=${heroValues(root)}; " +
                            "relatedSemantics=${relatedDiagnostics(root, fixture.cities)}"
                        if (layoutPasses <= 4) println("SHARED_CHROME_DIAGNOSTIC $name $lastDiagnostic")
                        if (layoutPasses >= 3) checked = check(this@apply, root, SystemClock.uptimeMillis())
                    } catch (error: Throwable) { failure = error; checked = true }
                    if (!checked) this@apply.invalidate()
                    return true
                }
            })
        }
        paparazzi.gif(host, name, end = duration, fps = if (duration <= 1_000) 4 else 12)
        failure?.let { throw AssertionError("Native shared-chrome scenario $name", it) }
        assertTrue("Native shared-chrome check must finish: $name; $lastDiagnostic", checked)
    }

    private fun heroNodes(root: SemanticsNode) = accessibleNodes(root).filter { node ->
        node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text in temperatures } == true &&
            layouts(node).any { it.layoutInput.style.fontSize.value >= 40f }
    }
    private fun heroValues(root: SemanticsNode) = heroNodes(root).flatMap {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
    }
    private fun texts(root: SemanticsNode) = accessibleNodes(root).filter {
        it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f
    }.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
    private fun textNode(root: SemanticsNode, text: String) = accessibleNodes(root).first { node ->
        node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }
    private fun control(root: SemanticsNode, label: String) = accessibleNodes(root).filter { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && accessibleNodes(node).any {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        }
    }.minBy { it.boundsInRoot.width * it.boundsInRoot.height }
    private fun assertHeaderControlsFit(root: SemanticsNode) {
        val viewport = root.boundsInRoot
        for (label in listOf("城市列表", "刷新", "设置")) {
            val node = control(root, label)
            val bounds = node.boundsInRoot
            val tolerance = 1f / 3f
            val diagnostic = "$scenarioContext/control=$label/bounds=$bounds/nativeSize=${node.size}/viewport=$viewport"
            assertTrue("Header control must fit: $diagnostic", bounds.left >= viewport.left - tolerance &&
                bounds.top >= viewport.top - tolerance && bounds.right <= viewport.right + tolerance && bounds.bottom <= viewport.bottom + tolerance)
            assertTrue("Header control must retain a 48dp native touch target: $diagnostic",
                bounds.width >= 48f - tolerance && bounds.height >= 48f - tolerance)
        }
    }
    private fun assertTextFits(root: SemanticsNode) {
        rawNodes(root).forEach { node -> layouts(node).forEach { layout ->
            val tolerance = 1f / 3f
            assertFalse("Shared chrome text overflow: ${layout.layoutInput.text}; ${layout.size}",
                layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                    layout.getLineLeft(line) < -tolerance || layout.getLineRight(line) > layout.size.width + tolerance ||
                        layout.getLineBottom(line) > layout.size.height + tolerance
                })
        } }
    }
    private fun layouts(node: SemanticsNode): List<TextLayoutResult> = mutableListOf<TextLayoutResult>().also {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it)
    }

    /** Choose real body text outside a horizontal chart, so the pager receives the gesture. */
    private fun swipeBand(root: SemanticsNode, fixture: Fixture): Float {
        fun bodyText(node: SemanticsNode, insidePager: Boolean): List<SemanticsNode> {
            val horizontal = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            // The first horizontal container is the production pager, whose body is exactly
            // where a city swipe belongs. Only horizontal charts nested inside it own the drag.
            if (horizontal && insidePager) return emptyList()
            if (node.config.isClearingSemantics) return listOf(node)
            return listOf(node) + node.children.flatMap { bodyText(it, insidePager || horizontal) }
        }
        return bodyText(root, false).first { node -> node.config.getOrNull(SemanticsProperties.Text) != null &&
            node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
            node.boundsInRoot.center.y > fixture.chrome!!.headerHeight + 24f &&
            node.boundsInRoot.center.y < root.boundsInRoot.bottom - 24f }.boundsInRoot.center.y
    }
    private var scenarioContext = "No native scenario"
    private var downTime = 0L
    private fun touch(host: View, action: Int, x: Float, y: Float, time: Long) {
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        try { host.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    /** Raw unmerged children remain available below clearAndSetSemantics for visual checks. */
    private fun rawNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::rawNodes)
    /** Accessibility stops at a clearAndSetSemantics boundary, retaining only its own config. */
    private fun accessibleNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) +
        if (node.config.isClearingSemantics) emptyList() else node.children.flatMap(::accessibleNodes)

    private fun relatedDiagnostics(root: SemanticsNode, cities: List<City>): List<String> {
        val names = cities.flatMap { listOf(it.name, it.displayName, it.street.orEmpty()) }.filter(String::isNotEmpty).toSet()
        fun visit(node: SemanticsNode, clearAncestors: List<Int>): List<String> {
            val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val fonts = if (text.isNotEmpty()) layouts(node).map { it.layoutInput.style.fontSize } else emptyList()
            val next = if (node.config.isClearingSemantics) clearAncestors + node.id else clearAncestors
            val children = node.children.flatMap { visit(it, next) }
            val related = fonts.any { it.value >= 40f } || text.any { it.text in names } ||
                node.config.isClearingSemantics && children.isNotEmpty()
            val description = if (related) listOf("id=${node.id},text=$text,bounds=${node.boundsInRoot},size=${node.size}," +
                "font=$fonts,clear=${node.config.isClearingSemantics},clearAncestors=$clearAncestors") else emptyList()
            return description + children
        }
        return visit(root, emptyList())
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
