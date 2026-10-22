package com.zhisheng.weather.ui.home

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.LocalWeatherPreviewTime
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Offline production-card rendering and native touches; no phone, weather API or account data. */
class HourlyTemperatureVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val now = java.time.Instant.parse("2026-10-16T04:00:00Z").toEpochMilli()
    private val weather = WeatherData(
        current = CurrentWeather(temperature = 24.0, condition = WeatherCondition.CLEAR),
        hourly = (-1..22).map { index ->
            HourlyWeather(now + index * 3_600_000L,
                listOf(23.0, 24.0, 26.0, 27.0, 25.0, 22.0, 21.0)[(index + 1) % 7],
                if (index < 3) WeatherCondition.CLEAR else WeatherCondition.PARTLY_CLOUDY,
                windSpeed = 5.0, windDirectionDeg = 90.0,
                precipProb = if (index > 3) 45 else 0)
        }, fetchedAt = now, updateTime = now, utcOffsetSeconds = 28_800,
    )

    @Test fun temperatureReadingsFollowTheirContourAndFitBothThemesAndLargeType() {
        for (light in listOf(true, false)) {
            for ((width, height, font) in listOf(Triple(375, 370, 1f), Triple(320, 600, 2f), Triple(640, 360, 2f))) {
                capture("hourly_trace_${light}_${width}", light, width, height, font,
                    unit = if (font > 1f) "f" else "c")
            }
        }
    }

    @Test fun steadyMissingSparseAndSingleHourStatesRender() {
        val flat = weather.copy(current = weather.current?.copy(temperature = 11.0),
            hourly = weather.hourly.map { it.copy(temperature = 11.0) })
        val missing = weather.copy(hourly = weather.hourly.mapIndexed { index, hour ->
            hour.copy(temperature = if (index in listOf(2, 4)) null else hour.temperature,
                timeMillis = hour.timeMillis + if (index >= 6) 7_200_000L else 0L)
        })
        val single = weather.copy(hourly = emptyList())
        val noTemperature = weather.copy(current = null,
            hourly = weather.hourly.map { it.copy(temperature = null) })
        for (light in listOf(true, false)) for ((name, data) in listOf(
            "flat" to flat, "missing" to missing, "single" to single, "no_temperature" to noTemperature)) {
            capture("hourly_trace_${name}_$light", light, data = data)
        }
        val cold = weather.copy(current = weather.current?.copy(temperature = -18.0),
            hourly = weather.hourly.mapIndexed { index, hour -> hour.copy(temperature = -24.0 + index % 12) })
        for (light in listOf(true, false)) capture("hourly_trace_cold_$light", light,
            width = 320, height = 600, font = 2f, data = cold)
    }

    @Test fun risingAndFallingContoursFlowThroughTheFirstAndLastCells() {
        for (light in listOf(true, false)) for (rising in listOf(true, false)) {
            fun temperature(index: Int) = if (rising) 20.0 + index else 25.0 - index
            val edgeWeather = weather.copy(current = weather.current?.copy(temperature = temperature(1)),
                hourly = (-1..4).map { index -> weather.hourly.first().copy(
                    timeMillis = now + index * 3_600_000L, temperature = temperature(index + 1), precipProb = 0) })
            capture("hourly_trace_edges_${light}_$rising", light, data = edgeWeather)
        }
    }

    @Test fun classicHourlyLayoutStillFits() {
        for (light in listOf(true, false)) capture("hourly_trace_classic_$light", light,
            style = InterfaceStyle.CLASSIC_TERMINAL)
    }

    @Test fun nativeDragsOnTraceAndReadingsKeepScrollingCityAndDetailActionsSeparate() {
        for (traceBand in listOf(false, true)) for (forward in listOf(true, false)) {
            val cities = listOf("金昌市", "成都市", "上海市").mapIndexed { index, name ->
                City(name, "离线夹具", 30.0 + index, 105.0, "hourly-$index")
            }
            var pager: androidx.compose.foundation.pager.PagerState? = null
            var phase = 0
            var y = 0f
            var released = 0L
            var detailCalls = 0
            val selected = mutableListOf<String>()
            capture("hourly_trace_native_${traceBand}_$forward", true, width = 360, height = 400,
                progressive = { host, root, time ->
                    val card = nodes(root).first { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.label == "查看逐时详情" }
                    val chart = nodes(card).first { it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null }
                    val range = chart.config[SemanticsProperties.HorizontalScrollAxisRange]
                    val startX = if (forward) 290f else 60f
                    val endX = if (forward) 60f else 290f
                    when {
                        phase == 0 -> {
                            val reading = nodes(card).first { node -> node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
                                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "24°" } == true }.boundsInRoot
                            // Touch the line just below the moving number in this 160dpi host.
                            y = if (traceBand) reading.bottom + 6f else reading.center.y
                            touch(host, MotionEvent.ACTION_DOWN, startX, y, time); phase++; false
                        }
                        phase in 1..8 -> {
                            touch(host, MotionEvent.ACTION_MOVE, startX + (endX - startX) * phase / 8f, y, time)
                            phase++; false
                        }
                        phase == 9 -> {
                            touch(host, MotionEvent.ACTION_UP, endX, y, time); released = time; phase++; false
                        }
                        phase == 10 && time - released > 700 -> {
                            if (forward) assertTrue("The real hourly card must scroll", range.value() > 0f)
                            else assertEquals("The first-hour edge must stay put", 0f, range.value(), 0.01f)
                            assertEquals(1, pager!!.settledPage)
                            assertEquals(0f, pager!!.currentPageOffsetFraction, .001f)
                            assertTrue("Hourly swipes must not request another city", selected.isEmpty())
                            assertEquals("A drag must not open details", 0, detailCalls)
                            touch(host, MotionEvent.ACTION_DOWN, 170f, y, time)
                            phase++; false
                        }
                        phase == 11 -> { touch(host, MotionEvent.ACTION_UP, 170f, y, time); phase++; false }
                        phase == 12 && detailCalls > 0 -> {
                            assertEquals("A subsequent tap must still open hourly details", 1, detailCalls)
                            true
                        }
                        else -> false
                    }
                }, content = {
                    CityWeatherPager(cities, cities[1].locationKey, true, { selected += it }, Modifier.fillMaxSize()) { _, state ->
                        pager = state
                        Column(Modifier.fillMaxSize().zhishengScreen()) {
                            SectionTitle(1, "逐时预报", "", false)
                            HourlySection(weather, "c", "kmh", 28_800, Modifier, { detailCalls++ })
                        }
                    }
                })
        }
    }

    private fun capture(name: String, light: Boolean, width: Int = 375, height: Int = 370,
        font: Float = 1f, data: WeatherData = weather, unit: String = "c",
        style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
        progressive: ((ComposeView, SemanticsNode, Long) -> Boolean)? = null,
        content: (@Composable () -> Unit)? = null) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width,
            screenHeight = height, density = Density.MEDIUM, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        var failure: Throwable? = null
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides now) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    if (content != null) content() else {
                        Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState())) {
                            SectionTitle(1, "逐时预报", "", false)
                            HourlySection(data, unit, "kmh", 28_800, Modifier)
                        }
                    }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) try {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view -> view.javaClass.methods
                            .firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                            ?.invoke(view) as? SemanticsOwner }?.unmergedRootSemanticsNode ?: error("Missing Compose semantics")
                        assertTextFits(root, followContour = style == InterfaceStyle.PHOSPHOR_VISTA)
                        checked = progressive?.invoke(this@apply, root, SystemClock.uptimeMillis()) ?: true
                    } catch (error: Throwable) { failure = error; checked = true }
                    return true
                }
            })
        }
        if (progressive == null) paparazzi.snapshot(host, name)
        else paparazzi.gif(host, name, end = 3_000, fps = 12)
        failure?.let { throw AssertionError("Native hourly scenario $name", it) }
        assertTrue("Native hourly check must finish: $name", checked)
    }

    private fun assertTextFits(root: SemanticsNode, followContour: Boolean) {
        nodes(root).forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { result ->
                val tolerance = 1f / 3f // One physical pixel in the established 480dpi fixture.
                assertFalse("Measured hourly text overflow: ${result.layoutInput.text}; ${result.size}",
                    result.didOverflowHeight || (0 until result.lineCount).any { line ->
                        result.getLineLeft(line) < -tolerance || result.getLineRight(line) > result.size.width + tolerance ||
                            result.getLineBottom(line) > result.size.height + tolerance
                    })
            }
        }
        val temperatures = nodes(root).filter { node -> node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.matches(Regex("-?\\d+°")) } == true }
        if (followContour && temperatures.isNotEmpty()) {
            assertTrue("The temperature contour must stay within its compact 36dp travel",
                temperatures.maxOf { it.boundsInRoot.top } - temperatures.minOf { it.boundsInRoot.top } <= 36f + 1f / 3f)
            temperatures.sortedBy { it.boundsInRoot.left }.zipWithNext().forEach { (left, right) ->
                fun value(node: SemanticsNode) = node.config[SemanticsProperties.Text].first().text.removeSuffix("°").toInt()
                val difference = value(right) - value(left)
                val verticalChange = right.boundsInRoot.top - left.boundsInRoot.top
                if (difference > 0) assertTrue("Warmer readings must follow the line upward", verticalChange <= 1f / 3f)
                if (difference < 0) assertTrue("Cooler readings must follow the line downward", verticalChange >= -1f / 3f)
            }
        }
    }

    private fun touch(host: View, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(0L, time, action, x, y, 0)
        host.dispatchTouchEvent(event)
        event.recycle()
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
