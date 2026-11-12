package com.zhisheng.weather.ui.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 经典终端（CLASSIC_TERMINAL）与澄空对齐轮的守卫：
 * 纯函数直接断言；组合内行为以仓库惯用的源码结构断言锁定，防止后续改动悄悄回退。
 */
class ClassicTerminalParityTest {
    private val root = File(requireNotNull(System.getProperty("user.dir")))
    private fun mainSource(rel: String) = File(root, "src/main/kotlin/com/zhisheng/weather/$rel").readText()

    // —— classicHeroNumberSize：经典大温度按可用宽自适应 ——

    @Test fun heroNumberSizeStaysAtCapWhenSpaceAllows() {
        // 常规手机两字符温度在 1x 字体下维持既有 84sp 观感。
        assertEquals(84f, classicHeroNumberSize(availableWidthDp = 200f, textLength = 2, fontScale = 1f), 0.01f)
    }

    @Test fun heroNumberSizeShrinksForLongReadingsAndLargeFont() {
        // 三字符负温 + 1.5x 字体：200dp 可用宽必须收缩到能放下。
        val size = classicHeroNumberSize(availableWidthDp = 200f, textLength = 3, fontScale = 1.5f)
        assertTrue(size in 40f..84f)
        assertTrue(3 * 0.62f * size * 1.5f <= 200f + 0.01f)
    }

    @Test fun heroNumberSizeFloorProtectsAbsurdNarrowWidths() {
        assertEquals(40f, classicHeroNumberSize(availableWidthDp = 8f, textLength = 3, fontScale = 2f), 0.01f)
    }

    @Test fun heroNumberSizeTreatsSingleDigitAsTwoCharsToAvoidJump() {
        assertEquals(
            classicHeroNumberSize(200f, 1, 1f),
            classicHeroNumberSize(200f, 2, 1f),
            0.001f,
        )
    }

    // —— 经典预警区：过滤、展开态、无障碍 ——

    @Test fun classicAlertsFilterBlankTitlesAndKeepExpansionSaveable() {
        val home = mainSource("ui/home/HomeScreen.kt")
        val alerts = home.substringAfter("private fun AlertSection(").substringBefore("private fun hazardStripes")
        assertTrue(alerts.contains("alerts.filter { it.title.isNotBlank() }"))
        assertTrue(alerts.contains("var expandedKeys by rememberSaveable"))
        // 长度前缀身份：同标题多条预警不得互相串位（对齐澄空做法）。
        assertTrue(alerts.contains("listOf(alert.title, alert.pubTime.orEmpty(), alert.type.orEmpty())"))
        assertTrue(alerts.contains("stateDescription = if (expanded) \"已展开\" else \"已收起\""))
        assertTrue(alerts.contains("onClickLabel = if (expanded) \"收起预警\" else \"查看完整预警\""))
    }

    // —— 功能对等：雨强读数与遥测列数 ——

    @Test fun rainIntensityIsNoLongerVistaOnly() {
        val home = mainSource("ui/home/HomeScreen.kt")
        assertTrue(home.contains("当前雨强"))
        assertFalse(home.contains("!presentation.dry && isPhosphorVista"))
    }

    @Test fun telemetryGridColumnsAdaptInBothStyles() {
        val home = mainSource("ui/home/HomeScreen.kt")
        // 经典此前被 `!isPhosphorVista -> 2` 短路，大字体不降单列、宽屏不升三列。
        assertFalse(home.contains("!isPhosphorVista -> 2"))
        assertTrue(home.contains("fontScale > 1.25f -> 1"))
        assertTrue(home.contains("screenWidthDp >= 600 -> 3"))
    }

    // —— 经典抽屉每城天气：与澄空同一套合并管线 ——

    @Test fun classicDrawerShowsPerCityWeatherFromSharedPipeline() {
        val home = mainSource("ui/home/HomeScreen.kt")
        assertTrue(home.contains("private fun rememberClassicDrawerWeather(ui: HomeUiState, active: Boolean)"))
        assertTrue(home.contains("mergeCityWeatherCache(weather, city, candidate, ui.sourcePref)"))
        // live 更新必须独立成效应，不得并进会重启邻城请求的键组。
        assertTrue(home.contains("LaunchedEffect(active, ui.weather, ui.selectedCity?.let { com.zhisheng.weather.widget.weatherCityIdentity(it) },"))
        // 内存快照垫底：抽屉打开即可见最近看过的城市读数。
        assertTrue(home.contains("ui.citySnapshots.forEach { (key, snapshot) ->"))
        assertTrue(home.contains("drawerWeather[city.locationKey]?.data"))
    }

    // —— 功耗：无期限循环必须按生命周期门控 ——

    @Test fun standbyClockAndBootCursorLoopsAreLifecycleGated() {
        val standby = mainSource("ui/LandscapeStandbyScreen.kt")
        val classicStandby = standby.substringAfter("private fun ClassicLandscapeStandbyScreen(")
        assertTrue(classicStandby.contains("repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED)"))
        val chrome = mainSource("ui/FeatureScreenChrome.kt")
        assertTrue(chrome.contains("repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED)"))
    }

    // —— 首帧一致性：collectAsState 初始值对齐 DataStore 缺省 ——

    @Test fun settingsAndActivityInitialsMatchDataStoreDefaults() {
        val settings = mainSource("ui/SettingsScreen.kt")
        assertTrue(settings.contains("softGlow.collectAsState(initial = true)"))
        assertTrue(settings.contains("usageStatistics.collectAsState(initial = true)"))
        assertTrue(settings.contains("serviceDiagnostics.collectAsState(initial = true)"))
        assertFalse(settings.contains("softGlow.collectAsState(initial = false)"))
        val activity = mainSource("MainActivity.kt")
        // SoftGlowLevel.from(null) == FLOWING（AppearanceDefaultsTest.glowStartsFlowing）。
        assertTrue(activity.contains("SoftGlowLevel.FLOWING)"))
        assertFalse(activity.contains("SoftGlowLevel.SUBTLE)"))
    }

    // —— 减少动态效果：经典入场动画尊重系统动画开关 ——

    @Test fun classicStaggerHonorsSystemAnimationSetting() {
        val home = mainSource("ui/home/HomeScreen.kt")
        val stagger = home.substringAfter("private fun Stagger(").substringBefore("private fun Modifier.graphicsLayerAlpha")
        assertTrue(stagger.contains("ValueAnimator.areAnimatorsEnabled()"))
    }

    // —— 中文日期格式固定 Locale，避免非拉丁数字区域出现两套数字 ——

    @Test fun chineseDatePatternsPinLocale() {
        val history = mainSource("ui/HistoryScreen.kt")
        assertTrue(history.contains("ofPattern(\"M月d日\", Locale.CHINA)"))
        assertFalse(history.contains("ofPattern(\"M月d日\")"))
        val typhoon = mainSource("ui/TyphoonScreen.kt")
        assertFalse(typhoon.contains("ofPattern(\"MM月dd日 HH:mm\")"))
        val source = mainSource("ui/WeatherSourceSettings.kt")
        assertFalse(source.contains("ofPattern(\"MM-dd HH:mm\")"))
        assertFalse(source.contains("ofPattern(\"MM-dd HH:mm 'UTC'\")"))
    }
}
