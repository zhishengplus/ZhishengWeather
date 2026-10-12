package com.zhisheng.weather.ui.home

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.LocalWeatherPreviewTime
import com.zhisheng.weather.ui.game.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SkyBirdVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val now = 1_800_000_000_000L
    @Test fun journeyScenesAndRewardsKeepControlsReachable() {
        for (light in listOf(true, false)) for (score in listOf(10, 20, 30, 40, 50, 100))
            for ((width, height, font) in listOf(Triple(360, 700, 1f), Triple(320, 520, 2f))) {
                val state = SkyBirdEngine.start(seed = 541).copy(phase = SkyBirdPhase.FLYING, score = score,
                    gates = listOf(CloudGate(0, 245f, 250f, gap = 112f)), distance = 400f,
                    celebration = SkyBirdCelebration(score, 240f, .55f))
                capture("reward_${score}_${light}_${width}_$font", width, height, font, light,
                    content = { SkyBirdPanel(state, 100, true, Modifier.fillMaxSize(), {}, {}, {}, {}) }) { root ->
                    fits(root, action(root, "关闭游戏")); fits(root, action(root, "暂停游戏"))
                    fits(root, action(root, "降水详情"))
                    val text = nodes(root).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                    assertTrue(text.contains("${score} 根 · ${SkyBirdJourney.medalTitle(score)}徽章"))
                    assertTrue(text.contains("抵达${SkyBirdJourney.scenery(score).title}"))
                }
            }
        for (light in listOf(true, false)) for ((width, height, font) in listOf(Triple(360, 700, 1f), Triple(320, 520, 2f))) {
            val state = SkyBirdEngine.start(seed = 54).copy(phase = SkyBirdPhase.LANDED, score = 100)
            capture("reward_result_${light}_${width}_$font", width, height, font, light,
                content = { SkyBirdPanel(state, 100, true, Modifier.fillMaxSize(), {}, {}, {}, {}) }) { root ->
                fits(root, action(root, "再飞一次")); fits(root, action(root, "关闭游戏"))
            }
        }
    }
    @Test fun terrainFramesKeepRiverAndGroundFixed() {
        for (light in listOf(true, false)) for (score in listOf(0, 10, 20, 30, 40)) for (distance in listOf(0, 360)) {
            val state = SkyBirdEngine.start(seed = 12).copy(score = score, distance = distance.toFloat())
            capture("terrain_${score}_${light}_$distance", 360, 520, 1f, light,
                content = { Canvas(Modifier.fillMaxSize()) { drawSkyBirdLandscape(state, light) } }) { }
        }
    }
    @Test fun allFlightStatesKeepControlsAndTextReachable() {
        for (light in listOf(true, false)) for (phase in SkyBirdPhase.entries)
            for ((width, height, font) in listOf(Triple(360, 700, 1f), Triple(320, 520, 2f), Triple(640, 360, 1f))) {
                var actions = 0; var pauses = 0; var closes = 0; var details = 0; var vibration: Boolean? = null
                val state = SkyBirdEngine.start().copy(phase = phase, score = 7,
                    gates = listOf(CloudGate(0, 240f, 250f)), distance = 180f)
                capture("bird_${phase}_${light}_${width}_$font", width, height, font, light,
                    content = { SkyBirdPanel(state, 12, true, Modifier.fillMaxSize(),
                        onAction = { actions++ }, onPause = { pauses++ }, onClose = { closes++ },
                        onHaptics = { vibration = it }, onWeatherDetails = { details++ }) }) { root ->
                    val close = action(root, "关闭游戏"); fits(root, close)
                    close.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, closes)
                    if (phase == SkyBirdPhase.FLYING) {
                        val pause = action(root, "暂停游戏"); fits(root, pause)
                        pause.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, pauses)
                    } else {
                        val label = when (phase) {
                            SkyBirdPhase.READY -> "开始飞行"
                            SkyBirdPhase.PAUSED -> "继续飞行"
                            else -> "再飞一次"
                        }
                        fits(root, action(root, label))
                    }
                    val field = action(root, "云间小鸟飞行区域")
                    field.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, actions)
                    val toggle = nodes(root).first { it.config.contains(SemanticsProperties.ToggleableState) }
                    fits(root, toggle)
                    toggle.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(false, vibration)
                    val weather = action(root, "降水详情"); fits(root, weather)
                    weather.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, details)
                }
            }
    }
    @Test fun dryCardArrowOpensGameWhileNonDryCardsStillOpenWeather() {
        val zero = (0 until 120).map { MinutePrecip(now + it * 60_000, 0f) }
        val full = WeatherData(rainMinutes = zero, rainMeta = RainMeta("XIAOMI", 1))
        for (light in listOf(true, false)) for ((key, data) in listOf("dry" to full,
            "wet" to full.copy(current = CurrentWeather(condition = WeatherCondition.RAIN)),
            "missing" to WeatherData(), "partial" to full.copy(rainMinutes = zero.take(15)))) {
            var details = 0
            capture("entry_${key}_$light", 320, 480, 2f, light, content = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PrecipCard(data, Modifier, onClick = { details++ })
                }
            }) { root ->
                val game = nodes(root).filter { it.config.getOrNull(SemanticsActions.OnClick)?.label == "打开云间小鸟游戏" }
                assertEquals(if (key == "dry") 1 else 0, game.size)
                if (key == "dry") {
                    fits(root, game.single())
                    val arrow = action(root, "打开云间小鸟"); fits(root, arrow)
                    arrow.config[SemanticsActions.OnClick].action!!.invoke()
                    assertEquals("Home arrow must never open precipitation on a dry card", 0, details)
                } else {
                    val detail = nodes(root).first { it.config.getOrNull(SemanticsActions.OnClick)?.label == "查看降水详情" }
                    detail.config[SemanticsActions.OnClick].action!!.invoke(); assertEquals(1, details)
                }
            }
        }
    }
    private fun capture(name: String, width: Int, height: Int, font: Float, light: Boolean,
        content: @androidx.compose.runtime.Composable () -> Unit, check: (SemanticsNode) -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width,
            screenHeight = height, density = Density.MEDIUM, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false; var failure: Throwable? = null
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides now) {
                ZhishengWeatherTheme(isLight = light, softGlow = false) {
                    Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg)) { content() }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        try {
                            val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                    ?.invoke(view) as? SemanticsOwner
                            } ?: error("Missing production semantics")
                            val root = owner.unmergedRootSemanticsNode
                            check(root); assertTextFits(root)
                        } catch (t: Throwable) { failure = t }
                        checked = true
                    }
                    return true
                }
            })
        }
        paparazzi.snapshot(host, name = name)
        failure?.let { throw it }
        assertTrue("Actual production layout checked", checked)
    }
    private fun fits(root: SemanticsNode, node: SemanticsNode) {
        val b = node.boundsInRoot; val v = root.boundsInRoot
        assertTrue("48dp touch target: ${node.config} / ${node.size}", node.size.height >= 48)
        assertTrue("Action stays in viewport: $b / $v", b.top >= v.top - 1 && b.bottom <= v.bottom + 1 &&
            b.left >= v.left - 1 && b.right <= v.right + 1 && b.height + 1 >= node.size.height)
    }
    private fun assertTextFits(root: SemanticsNode) {
        nodes(root).forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout -> assertFalse("Text overflow: ${layout.layoutInput.text.text} size=${layout.size} " +
                "height=${layout.didOverflowHeight} width=${layout.didOverflowWidth} lines=" +
                (0 until layout.lineCount).map { "${layout.getLineRight(it)},${layout.getLineBottom(it)}" },
                // Wrap-content rounds its intrinsic width to an integer. The width
                // flag alone can be true for a line that fits exactly (76 / 76).
                // Use actual line bounds, as the existing native page checks do.
                layout.didOverflowHeight || (0 until layout.lineCount).any {
                    layout.getLineLeft(it) < -1 || layout.getLineBottom(it) > layout.size.height + 1 ||
                        layout.getLineRight(it) > layout.size.width + 1
                }) }
        }
    }
    private fun action(root: SemanticsNode, label: String): SemanticsNode = nodes(root)
        .filter { it.config.contains(SemanticsActions.OnClick) && nodes(it).any { child ->
            child.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true ||
                child.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label }
        } }.minBy { it.size.width * it.size.height }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
