package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import java.time.LocalDate
import androidx.compose.runtime.*
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DomesticWeatherPagesVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5, theme = "android:Theme.Material.Light.NoActionBar")
    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        fontScale: Float = 1.5f,
        check: ((SemanticsNode) -> Unit)? = null,
        progressiveCheck: ((SemanticsNode) -> Boolean)? = null,
        content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width,
            screenHeight = height, density = com.android.resources.Density.MEDIUM, fontScale = fontScale,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = check == null && progressiveCheck == null
        return ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) { content() }
            } }
            if (check != null || progressiveCheck != null) viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        }?.unmergedRootSemanticsNode ?: error("Missing actual Compose semantics")
                        check?.invoke(root)
                        checked = progressiveCheck?.invoke(root) ?: true
                    }
                    // Paparazzi advances the frame clock; do not request recursive draws at the same time.
                    return true
                }
            })
            if (progressiveCheck == null) paparazzi.snapshot(this, name)
            else paparazzi.gif(this, name, end = 2_000, fps = 4)
            assertTrue("Native check must run before Paparazzi disposes the composition", checked)
        }
    }


    private val now = 1_800_000_000_000L
    private val sizes = listOf(Triple(360, 680, 1f), Triple(320, 600, 2f), Triple(640, 240, 2f))
    private val fixtureDays = (0L..6L).filter { it != 3L }.map { index -> HistoricalDay(LocalDate.of(2026,10,4).plusDays(index).toString(),
        weatherCode = 61, high = 15.0 + index, low = 5.0 + index, precipitationMm = .5, windMaxKmh = 12.0) }
    @Test fun futureForecastAndEmptyStateWrapAndTheirBottomActionsRemainReachable() {
        for (style in InterfaceStyle.entries) for (light in listOf(true,false)) for ((w,h,f) in sizes) for (empty in listOf(false,true)) {
            val forecast = radarPointForecast(if (empty) null else WeatherData(rainMeta = RainMeta("OPEN-METEO",15,now),
                rainMinutes = listOf(MinutePrecip(now,0f),MinutePrecip(now+900_000,.4f),MinutePrecip(now+3_600_000,.7f))), now)
            var phase = 0
            capture("future_${style.key}_${light}_${w}_$empty",style,light,w,h,f,progressiveCheck = { root ->
                assertTextWraps(root)
                if (phase == 0) { phase = 1; scroll(root,2); false }
                else if (phase == 1 && "本地点预报与地图 · 数据说明" in labels(root)) {
                    val button = action(root,"本地点预报与地图 · 数据说明")
                    assertActionFits(root,button)
                    assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke()); phase=2; false
                } else phase==2 && "本地点预报与地图 · 收起" in labels(root)
            }) {
                Column(Modifier.fillMaxSize().zhishengScreen()) {
                    WeatherFeatureNavigation("降水地图","金昌市·金川区·上海路",{},listOf("附近回波","未来2小时"),1,{})
                    Box(Modifier.weight(1f)) { RadarFutureContent(forecast,now,28800) }
                }
            }
        }
    }
    @Test fun historyPagesKeepMissingSlotsAndAllowScrollingToSourceDetails() {
        for (light in listOf(true,false)) for ((w,h,f) in sizes) for (sameDay in listOf(false,true)) {
            var scrolled = false
            val review = HistoricalReview(LocalDate.of(2026,10,11),listOf(HistoricalDay("2025-10-11",high=15.0,low=3.0),HistoricalDay("2023-10-11",high=12.0,low=2.0)),5)
            capture("history_${light}_${w}_$sameDay",InterfaceStyle.PHOSPHOR_VISTA,light,w,h,f,progressiveCheck = { root ->
                assertTextWraps(root)
                if (!scrolled) { scrolled=true;scroll(root,if(sameDay)2 else 1);false }
                else labels(root).any { it.startsWith("Open-Meteo ·") }
            }) {
                Column(Modifier.fillMaxSize().zhishengScreen()) {
                    WeatherFeatureNavigation("天气回看","金昌市·金川区·上海路",{},listOf("过去7天","往年同日"),if(sameDay)1 else 0,{})
                    Box(Modifier.weight(1f)) {
                        if(sameDay) VistaSameDayHistory(review,null,"c","kmh",5,review.referenceDate,null,{}, {})
                        else VistaRecentHistory(RecentWeatherWeek(fixtureDays),"c","kmh",LocalDate.of(2026,10,10))
                    }
                }
            }
        }
    }
    @Test fun dateChangesStayPendingUntilSaveAndCancelDoesNotCommit() {
        for (light in listOf(true,false)) for ((w,h,f) in sizes) for (save in listOf(false,true)) {
            var stage=0; var commits=0; var cancelled=false; var newDate: LocalDate?=null
            capture("date_${light}_${w}_$save",InterfaceStyle.PHOSPHOR_VISTA,light,w,h,f,progressiveCheck={ root ->
                assertTextWraps(root)
                when(stage) {
                    0 -> { scroll(root,2);stage=1;false }
                    1 -> if("12" in labels(root)) {
                        assertTrue(historyWeekdayLabels().all { it in labels(root) })
                        assertTrue(isSelected(root, "11"))
                        action(root,"12").config[SemanticsActions.OnClick].action!!.invoke();stage=2;false
                    } else false
                    2 -> {
                        assertEquals(LocalDate.of(2026,10,12),newDate)
                        assertTrue(isSelected(root, "12"))
                        assertFalse(isSelected(root, "11"))
                        val button=action(root,if(save)"查看" else "取消");assertActionFits(root,button);button.config[SemanticsActions.OnClick].action!!.invoke();stage=3;false
                    }
                    else -> { assertEquals(!save,cancelled);assertEquals(if(save)1 else 0,commits);true }
                }
            }) {
                var pending by remember { mutableStateOf(LocalDate.of(2026,10,11)) }
                HistoryDateContent(Modifier.fillMaxWidth().heightIn(max=h.dp), pending, LocalDate.of(2026,10,11),
                    {pending=it;newDate=it},{cancelled=true},{commits++})
            }
        }
    }
    @Test fun sameDayForecastDetailRefreshesWhenOnlyPrecipitationAndWindChange() {
        val edition = mutableStateOf(0)
        val review = HistoricalReview(LocalDate.of(2026, 10, 11), listOf(HistoricalDay("2025-10-11", high = 15.0, low = 3.0)), 5)
        capture("history_forecast_refresh", InterfaceStyle.PHOSPHOR_VISTA, true, 360, 680, 1f, progressiveCheck = { root ->
            assertTextWraps(root)
            val text = labels(root)
            val refreshed = text.any { it.contains("降水 8.5 mm") } && text.any { it.contains("36 km/h") }
            if (edition.value == 0 && text.any { it.contains("降水 1.0 mm") }) {
                edition.value = 1
                false
            } else if (!refreshed) false
            else {
                assertFalse(text.any { it.contains("降水 1.0 mm") || it.contains("12 km/h") })
                assertTrue(text.any { it.contains("预报风速 36 km/h") })
                assertFalse(text.any { it.contains("最大风速") })
                assertTrue(text.any { it.contains("22°") && it.contains("8°") })
                true
            }
        }) {
            val step = edition.value
            VistaSameDayHistory(review, DailyWeather(dateMillis = 0L, high = 22.0, low = 8.0,
                condition = WeatherCondition.RAIN, precipMm = if (step == 0) 1.0 else 8.5,
                windSpeed = if (step == 0) 12.0 else 36.0), "c", "kmh", 5, review.referenceDate, null, {}, {})
        }
    }
    @Test fun meanOnlyRecordsRemainVisibleWithoutInventingHighAndLowTemperatures() {
        val reference = LocalDate.of(2026, 10, 11)
        val review = HistoricalReview(reference, (1L..5L).map { offset ->
            HistoricalDay(reference.minusYears(offset).toString(), mean = 12.0, windMaxKmh = 12.0)
        }, 5)
        val end = reference.minusDays(1)
        val week = RecentWeatherWeek((0L..6L).map { offset ->
            HistoricalDay(end.minusDays(offset).toString(), mean = 12.0, windMaxKmh = 12.0)
        })
        for (light in listOf(true, false)) for ((w,h,f) in sizes) for (sameDay in listOf(false,true)) for (unit in listOf("c","f")) {
            var scrolled = false
            capture("history_mean_${light}_${w}_${sameDay}_$unit", InterfaceStyle.PHOSPHOR_VISTA, light, w, h, f, progressiveCheck = { root ->
                assertTextWraps(root)
                if (!scrolled) {
                    scrolled = true
                    scroll(root, if (sameDay) 1 else 0)
                    false
                } else {
                    val text = labels(root)
                    val expected = if (unit == "f") "平均 54°" else "平均 12°"
                    // ScrollToIndex can take another frame to place the lazy item on a short screen.
                    // Returning false keeps capture's final completion assertion active.
                    if (expected !in text) false else {
                        assertTrue("Both the selected detail and date rows must show the available mean ($light/$w/$sameDay/$unit): $text", text.count { it == expected } >= 2)
                        assertFalse("A mean-only record is still a valid record", "暂无记录" in text)
                        assertFalse("Missing high/low must not be replaced with the mean", text.any { it == "12°" || it == "54°" || it.contains("12° / 12°") || it.contains("54° / 54°") })
                        assertTrue(text.any { it.contains("最大风速 12 km/h") })
                        assertFalse(text.any { it.contains("预报风速") })
                        true
                    }
                }
            }) {
                Column(Modifier.fillMaxSize().zhishengScreen()) {
                    WeatherFeatureNavigation("天气回看","金昌市·金川区·上海路",{},listOf("过去7天","往年同日"),if(sameDay)1 else 0,{})
                    Box(Modifier.weight(1f)) {
                        if (sameDay) VistaSameDayHistory(review, null, unit, "kmh", 5, reference, null, {}, {})
                        else VistaRecentHistory(week, unit, "kmh", end)
                    }
                }
            }
        }
    }
    @Test fun meanOnlyChartLabelsFitWithoutInventingExtremesInStaticFrames() {
        val reference = LocalDate.of(2026, 10, 11)
        val review = HistoricalReview(reference, (1L..5L).map { offset ->
            HistoricalDay(reference.minusYears(offset).toString(), mean = 12.0)
        }, 5)
        val week = RecentWeatherWeek((0L..6L).map { offset ->
            HistoricalDay(reference.minusDays(offset).toString(), mean = 12.0)
        })
        for (light in listOf(true, false)) for ((w,h,f) in sizes.take(2))
            for (sameDay in listOf(false,true)) for (unit in listOf("c","f")) {
                capture("mean_static_${light}_${w}_${sameDay}_$unit", InterfaceStyle.PHOSPHOR_VISTA,
                    light, w, h, f, check = { root ->
                        assertTextWraps(root)
                        val expected = if (unit == "f") "平均 54°" else "平均 12°"
                        assertTrue(expected in labels(root))
                        assertFalse(labels(root).any { it == "12°" || it == "54°" || it.contains("12° / 12°") })
                    }) {
                    if (sameDay) VistaSameDayHistory(review, null, unit, "kmh", 5, reference, null, {}, {})
                    else VistaRecentHistory(week, unit, "kmh", reference)
                }
            }
    }
    private fun isSelected(root: SemanticsNode, label: String) =
        action(root, label).config.getOrNull(SemanticsProperties.Selected) == true
    private fun scroll(root: SemanticsNode,index:Int) {
        val scroll=nodes(root).first { it.config.getOrNull(SemanticsActions.ScrollToIndex)!=null }
        assertTrue(scroll.config[SemanticsActions.ScrollToIndex].action!!.invoke(index))
    }
    private fun assertActionFits(root: SemanticsNode, node: SemanticsNode) {
        val bounds = node.boundsInRoot
        val viewport = root.boundsInRoot
        assertTrue("An action must retain a 48dp touch target", node.size.height >= 47)
        assertTrue("Action must remain completely reachable: bounds=$bounds viewport=$viewport size=${node.size}", bounds.top >= viewport.top - 1f &&
            bounds.bottom <= viewport.bottom + 1f && bounds.left >= viewport.left - 1f &&
            bounds.right <= viewport.right + 1f && bounds.height + 1f >= node.size.height &&
            bounds.width + 1f >= node.size.width)
    }
    private fun assertTextWraps(root: SemanticsNode) {
        nodes(root).forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                assertFalse("Text must wrap in its measured width: ${layout.layoutInput.text.text}; size=${layout.size}; overflow=${layout.didOverflowHeight}; lines=" +
                    (0 until layout.lineCount).joinToString { line -> "${layout.getLineLeft(line)},${layout.getLineRight(line)},${layout.getLineBottom(line)}" },
                    layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                        layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                            layout.getLineBottom(line) > layout.size.height + 1f
                    })
            }
        }
    }
    private fun labels(root: SemanticsNode) = nodes(root).flatMap {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
    }
    private fun action(root: SemanticsNode, label: String) = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && (label in labels(node) || nodes(node).any {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        })
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
