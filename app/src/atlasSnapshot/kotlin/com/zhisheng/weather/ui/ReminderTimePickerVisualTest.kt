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
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the existing production entry point, never test-only picker helpers. */
class ReminderTimePickerVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun exactInitialMinuteAndFixedActionsFitLargeTypeWithoutAKeyboard() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for ((width, height, font) in listOf(Triple(320, 480, 2f), Triple(640, 160, 2f), Triple(320, 240, 2f))) {
                val initial = if (height == 480) 7 * 60 + 37 else 23 * 60 + 59
                var saved: Int? = null
                var dismissed = 0
                capture("time_wheel_${style.key}_${light}_${width}_${height}_${font}", style, light,
                    width, height, font, initial, { dismissed++ }, { saved = it }) { root ->
                    val wheels = wheels(root)
                    // Against the old input form this fails with 0, rather than a missing-helper compile error.
                    assertEquals("The actual time panel must expose two accessible sliding columns", 2, wheels.size)
                    assertTrue("Opening the picker must not create editable keyboard fields",
                        nodes(root).none { it.config.getOrNull(SemanticsActions.SetText) != null })
                    val selected = selectedValues(root)
                    assertEquals(setOf("%02d小时".format(Locale.ROOT, initial / 60), "%02d分钟".format(Locale.ROOT, initial % 60)), selected)
                    val chosen = nodes(root).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
                        it.config.getOrNull(SemanticsProperties.Selected) == true }
                    assertEquals(2, chosen.size)
                    chosen.forEach { value ->
                        assertTrue("Current hour/minute must retain at least 48dp", value.size.height >= 48)
                        assertFullyVisible(root, value, "Selected time")
                        val layouts = nodes(value).flatMap { node ->
                            val results = mutableListOf<TextLayoutResult>()
                            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                            results
                        }
                        assertTrue("Actual selected digits must have measured text", layouts.isNotEmpty())
                        assertTrue("Full selected digits and unit must fit large type: ${style.key}/$light/${width}x$height/font$font; row=${value.size}; layouts=${layouts.map { "${it.layoutInput.text.text}: size=${it.size}, line=${it.lineCount}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}" }}",
                            layouts.none { it.hasVisualOverflow })
                    }
                    for (label in listOf("取消", "保存时间")) assertFullyVisible(root, button(root, label), label)
                    val save = button(root, "保存时间")
                    if (save.config.contains(SemanticsProperties.Disabled)) false else {
                        assertTrue(save.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals("Save must return the exact initial minute, including non-five-minute values", initial, saved)
                        assertTrue(button(root, "取消").config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals("Cancel must invoke the actual dismiss action", 1, dismissed)
                        true
                    }
                }
            }
        }
    }

    @Test fun actualClickScrollAndBoundarySelectionSaveTheCenteredTime() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            var stage = 0
            var saved: Int? = null
            var dismissed = 0
            capture("time_wheel_actions_${style.key}_${light}", style, light, 320, 480, 1.5f,
                7 * 60 + 37, { dismissed++ }, { saved = it }, duration = 4_000) { root ->
                assertEquals("Both real picker columns must exist", 2, wheels(root).size)
                val save = button(root, "保存时间")
                val ready = !save.config.contains(SemanticsProperties.Disabled)
                when (stage) {
                    0 -> {
                        assertTrue("A neighboring hour must actually be clickable",
                            cell(root, "08小时").config[SemanticsActions.OnClick].action?.invoke() == true)
                        stage = 1; false
                    }
                    1 -> if (ready && "08小时" in selectedValues(root)) {
                        assertTrue(wheel(root, "分钟").config[SemanticsActions.ScrollToIndex].action?.invoke(59) == true)
                        stage = 2; false
                    } else false
                    2 -> if (ready && selectedValues(root) == setOf("08小时", "59分钟")) {
                        assertTrue(save.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals(8 * 60 + 59, saved)
                        assertTrue(wheel(root, "小时").config[SemanticsActions.ScrollToIndex].action?.invoke(23) == true)
                        stage = 3; false
                    } else false
                    3 -> if (ready && selectedValues(root) == setOf("23小时", "59分钟")) {
                        assertTrue(save.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals("The last items must not jump to the previous row", 1439, saved)
                        assertTrue(wheel(root, "小时").config[SemanticsActions.ScrollToIndex].action?.invoke(0) == true)
                        assertTrue(wheel(root, "分钟").config[SemanticsActions.ScrollToIndex].action?.invoke(0) == true)
                        stage = 4; false
                    } else false
                    4 -> if (ready && selectedValues(root) == setOf("00小时", "00分钟")) {
                        assertTrue(save.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals("Both first items must save midnight", 0, saved)
                        val hourRow = cell(root, "00小时").size.height.toFloat()
                        val minuteRow = cell(root, "00分钟").size.height.toFloat()
                        assertTrue(wheel(root, "小时").config[SemanticsActions.ScrollBy].action?.invoke(0f, hourRow * .6f) == true)
                        assertTrue(wheel(root, "分钟").config[SemanticsActions.ScrollBy].action?.invoke(0f, minuteRow * .6f) == true)
                        stage = 5; false
                    } else false
                    5 -> if (ready && selectedValues(root) == setOf("01小时", "01分钟")) {
                        // This verifies actual stopped-list geometry, not merely the enumerated values.
                        for (label in listOf("小时", "分钟")) {
                            val viewport = wheel(root, label).boundsInRoot
                            val selected = nodes(root).first { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
                                it.config.getOrNull(SemanticsProperties.Selected) == true &&
                                it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull()?.endsWith(label) == true }
                            val center = selected.positionInRoot.y + selected.size.height / 2f
                            assertTrue("Stopped $label must snap into the actual viewport center",
                                kotlin.math.abs(center - (viewport.top + viewport.bottom) / 2f) <= 2f / 3f)
                        }
                        assertTrue(save.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals("Save must use the newly centered values after a real scroll", 61, saved)
                        saved = null
                        val previousMinute = wheel(root, "分钟").config[SemanticsActions.CustomActions]
                            .first { it.label == "前一分钟" }
                        assertTrue("The real accessibility action must move the selected minute", previousMinute.action())
                        stage = 6; false
                    } else false
                    else -> if (ready && selectedValues(root) == setOf("01小时", "00分钟")) {
                        assertTrue(button(root, "取消").config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals(1, dismissed)
                        assertNull("Changing the time then cancelling must not call the save action", saved)
                        true
                    } else false
                }
            }
        }
    }

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        font: Float, initial: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit,
        duration: Long = 500L, check: (SemanticsNode) -> Boolean) {
        // Keep the same dp viewport and font scale while avoiding an unnecessary 3x raster.
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width,
            screenHeight = height, density = com.android.resources.Density.MEDIUM, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        var measurementFailure: Throwable? = null
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg), contentAlignment = Alignment.Center) {
                        ReminderTimePanel("早间提醒时间", initial, onDismiss, onSelect,
                            Modifier.fillMaxWidth().heightIn(max = height.dp))
                    }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        } ?: error("Missing actual Compose semantics")
                        try { checked = check(owner.unmergedRootSemanticsNode) }
                        catch (failure: Throwable) { measurementFailure = failure; checked = true }
                    }
                    // Paparazzi advances frames; recursive invalidation can trap Layoutlib at one time.
                    return true
                }
            })
        }
        paparazzi.gif(host, name, end = duration, fps = 8)
        measurementFailure?.let { throw AssertionError("Measured scenario: $name", it) }
        assertTrue("Actual picker actions must complete before the composition is disposed", checked)
    }

    private fun wheels(root: SemanticsNode) = nodes(root).filter {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() in setOf("小时", "分钟") &&
            it.config.getOrNull(SemanticsActions.ScrollToIndex) != null
    }
    private fun wheel(root: SemanticsNode, label: String) = wheels(root).first {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() == label
    }
    private fun cell(root: SemanticsNode, label: String) = nodes(root).first {
        it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() == label
    }
    private fun selectedValues(root: SemanticsNode) = nodes(root).filter {
        it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton &&
            it.config.getOrNull(SemanticsProperties.Selected) == true
    }.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() }.toSet()
    private fun button(root: SemanticsNode, label: String) = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && nodes(node).any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
    }
    private fun assertFullyVisible(root: SemanticsNode, node: SemanticsNode, label: String) {
        val viewport = root.boundsInRoot
        val bounds = node.boundsInRoot
        val tolerance = 1f / 3f // Preserve the former 480dpi check's tolerance in dp.
        assertTrue("$label must stay fully inside the real panel viewport",
            bounds.top >= viewport.top - tolerance && bounds.bottom <= viewport.bottom + tolerance &&
                bounds.left >= viewport.left - tolerance && bounds.right <= viewport.right + tolerance &&
                bounds.height + tolerance >= node.size.height && bounds.width + tolerance >= node.size.width)
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
