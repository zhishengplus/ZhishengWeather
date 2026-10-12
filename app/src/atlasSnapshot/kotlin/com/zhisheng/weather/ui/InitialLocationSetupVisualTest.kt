package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
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
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the production panel and its actual measured text and callback semantics. */
class InitialLocationSetupVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun everyInitialStateExplainsTheActualSituationAndKeepsManualCitySelection() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for (stage in InitialLocationSetupStage.entries) {
                val callbacks = IntArray(5)
                capture("location_${stage.name}_${style.key}_$light", style, light, 320, 520, 1.3f,
                    stage, false, callbacks) { root ->
                    val labels = labels(root)
                    assertFalse("A default city must never be presented as the user's location", labels.any { "北京" in it })
                    if (stage == InitialLocationSetupStage.INTRO) assertTrue("先选一个天气地点" in labels)
                    if (stage == InitialLocationSetupStage.PERMISSION_DENIED) assertTrue("尚未获得位置权限" in labels)
                    if (stage == InitialLocationSetupStage.SERVICES_OFF) assertTrue("系统定位尚未开启" in labels)
                    if (stage == InitialLocationSetupStage.FAILED) assertTrue("暂时未能获取当前位置，请重试或选择城市" in labels)
                    val busy = stage == InitialLocationSetupStage.REQUESTING_PERMISSION || stage == InitialLocationSetupStage.LOCATING
                    assertEquals("Progress must describe real pending work", busy,
                        nodes(root).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null })
                    val primary = action(root, primaryLabel(stage, false))
                    assertEquals("A pending location request must not start another request", busy,
                        primary.config.contains(SemanticsProperties.Disabled))
                    assertActionFits(root, primary)
                    if (!busy) {
                        assertTrue(primary.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals(1, callbacks[if (stage == InitialLocationSetupStage.SERVICES_OFF) 3 else 0])
                    }
                    val manual = action(root, "手动选择城市")
                    assertActionFits(root, manual)
                    assertFalse(manual.config.contains(SemanticsProperties.Disabled))
                    assertTrue(manual.config[SemanticsActions.OnClick].action?.invoke() == true)
                    assertEquals("Manual selection must call its own route", 1, callbacks[1])
                    assertTrue(action(root, "稍后选择").config[SemanticsActions.OnClick].action?.invoke() == true)
                    assertEquals(1, callbacks[4])
                    assertTextWraps(root)
                    true
                }
            }
        }
    }

    @Test fun permanentDenialKeepsBothFixedRoutesReachableAtLargeTypeAndShortLandscape() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for ((width, height) in listOf(320 to 480, 640 to 160)) {
                val callbacks = IntArray(5)
                capture("location_recovery_${style.key}_${light}_${width}_$height", style, light,
                    width, height, 2f, InitialLocationSetupStage.PERMISSION_DENIED, true, callbacks) { root ->
                    val settings = action(root, "打开应用设置")
                    val manual = action(root, "手动选择城市")
                    assertActionFits(root, settings)
                    assertActionFits(root, manual)
                    assertTextWraps(root)
                    assertTrue(settings.config[SemanticsActions.OnClick].action?.invoke() == true)
                    assertTrue(manual.config[SemanticsActions.OnClick].action?.invoke() == true)
                    assertEquals(1, callbacks[2])
                    assertEquals(1, callbacks[1])
                    assertEquals("Permanent denial must not re-request permission", 0, callbacks[0])
                    true
                }
            }
        }
    }

    @Test fun shortPanelCanScrollItsExplanationWithoutMovingTheCityActions() {
        val callbacks = IntArray(5)
        var phase = 0
        capture("location_scroll", InterfaceStyle.PHOSPHOR_VISTA, false, 640, 160, 2f,
            InitialLocationSetupStage.INTRO, false, callbacks, duration = 750L) { root ->
            val manual = action(root, "手动选择城市")
            assertActionFits(root, manual)
            if (phase == 0) {
                val scroll = nodes(root).first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                assertTrue("The real explanation must be scrollable", scroll.config[SemanticsActions.ScrollBy].action?.invoke(0f, 1000f) == true)
                phase++
                false
            } else {
                assertTrue("The use of approximate location must remain in the actual content",
                    labels(root).any { "大致位置" in it })
                assertTrue(manual.config[SemanticsActions.OnClick].action?.invoke() == true)
                assertEquals(1, callbacks[1])
                assertTextWraps(root)
                true
            }
        }
    }

    private fun primaryLabel(stage: InitialLocationSetupStage, settings: Boolean): String = when (stage) {
        InitialLocationSetupStage.INTRO -> "定位当前位置"
        InitialLocationSetupStage.REQUESTING_PERMISSION -> "正在请求权限"
        InitialLocationSetupStage.LOCATING -> "正在定位…"
        InitialLocationSetupStage.PERMISSION_DENIED -> if (settings) "打开应用设置" else "再次允许定位"
        InitialLocationSetupStage.SERVICES_OFF -> "打开定位设置"
        InitialLocationSetupStage.FAILED -> "重新定位"
    }

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        font: Float, stage: InitialLocationSetupStage, needsSettings: Boolean, callbacks: IntArray,
        duration: Long = 500L, check: (SemanticsNode) -> Boolean) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg), contentAlignment = Alignment.Center) {
                        InitialLocationSetupPanel(stage = stage, permissionNeedsSettings = needsSettings,
                            onUseLocation = { callbacks[0]++ }, onChooseCity = { callbacks[1]++ },
                            onOpenAppSettings = { callbacks[2]++ }, onOpenLocationSettings = { callbacks[3]++ },
                            onDismiss = { callbacks[4]++ },
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = height.dp))
                    }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        } ?: error("Missing production Compose semantics")
                        checked = check(owner.unmergedRootSemanticsNode)
                    }
                    if (!checked) this@apply.postInvalidateOnAnimation()
                    return true
                }
            })
        }
        paparazzi.gif(host, name, end = duration, fps = 4)
        assertTrue("Production location actions must finish before disposal", checked)
    }

    private fun assertActionFits(root: SemanticsNode, node: SemanticsNode) {
        val bounds = node.boundsInRoot
        val viewport = root.boundsInRoot
        assertTrue("An action must retain a 48dp touch target", node.size.height >= 143)
        assertTrue("Fixed action must remain completely reachable", bounds.top >= viewport.top - 1f &&
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
