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
import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Render actual panel content; native window/system bars are checked separately on Android. */
class WeatherDiagnosticsVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    private val report = WeatherDiagnosticsReport(
        listOf("枳生天气 测试版", "Android 14 / API 34") +
            List(30) { "桌面组件 $it · 宿主范围 320–560 × 100–300dp · 跟随主页地点 · 数字 1.0" },
        "SensitiveMaker SensitiveModel", "SensitiveTown · SensitiveStreet", SourcePref.XIAOMI,
        CachedWeather(WeatherData(current = CurrentWeather(temperature = 15.0),
            dataSource = "XIAOMI", fetchedAt = 1_800_000_000_000L), 1_800_000_000_000L), true)

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        state: WeatherDiagnosticsState, includePrivate: Boolean = false, copied: Boolean = false,
        onPrivacy: (Boolean) -> Unit = {}, onCopy: () -> Unit = {}, onRetry: () -> Unit = {},
        onDismiss: () -> Unit = {}, check: (SemanticsNode) -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = 1.5f,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = false
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).padding(12.dp),
                        contentAlignment = Alignment.Center) {
                        WeatherDiagnosticsPanel(state, includePrivate, copied, false, onPrivacy,
                            onCopy, onRetry, onDismiss, Modifier.fillMaxWidth().heightIn(max = (height - 24).dp))
                    }
                }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val root = descendants(this@apply).firstNotNullOfOrNull { child ->
                            child.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(child) as? SemanticsOwner
                        }?.unmergedRootSemanticsNode ?: error("Missing actual Compose semantics")
                        check(root)
                        checked = true
                    }
                    return true
                }
            })
        }
        paparazzi.snapshot(view, name)
        assertTrue("The assertion must execute before the composition is disposed", checked)
    }

    @Test fun diagnosticCopyIsDisabledWhileReadingAndAfterFailure() {
        for (state in listOf(WeatherDiagnosticsState.Loading, WeatherDiagnosticsState.Failed)) {
            var copied = 0
            capture("diagnostics_copy_${state.javaClass.simpleName}", InterfaceStyle.PHOSPHOR_VISTA,
                true, 320, 300, state, onCopy = { copied++ }) { root ->
                val copy = button(root, "复制诊断")
                assertTrue("Reading and failure text must never be copyable as diagnostics",
                    copy.config.contains(SemanticsProperties.Disabled))
                copy.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                assertEquals("The copy action must also reject a non-ready state", 0, copied)
            }
        }
    }

    @Test fun diagnosticPanelKeepsFixedActionsReadableAndReachableInNarrowAndShortLayouts() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for ((width, height) in listOf(320 to 800, 640 to 300, 320 to 160)) {
                capture("diagnostics_${style.key}_${light}_${width}_${height}_150",
                    style, light, width, height, WeatherDiagnosticsState.Ready(report)) { root ->
                    for (label in listOf("关闭", "复制诊断")) {
                        val action = button(root, label)
                        assertTrue("$label must retain a touchable height", action.size.height >= 48 * 3)
                        assertTrue("$label must stay inside the visible panel", action.boundsInRoot.bottom <= height * 3)
                        assertTrue("$label must stay below the visible top", action.boundsInRoot.top >= 0f)
                        nodes(action).forEach { text ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            text.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                            layouts.forEach { layout ->
                                val geometry = "$style / light=$light / target=${width}x$height / $label: " +
                                    "overflowWidth=${layout.didOverflowWidth}, overflowHeight=${layout.didOverflowHeight}, " +
                                    "textSize=${layout.size}, actionSize=${action.size}, bounds=${action.boundsInRoot}, " +
                                    "paragraphWidth=${layout.multiParagraph.width}, paragraphHeight=${layout.multiParagraph.height}, " +
                                    "intrinsicWidth=${layout.multiParagraph.intrinsics.maxIntrinsicWidth}, " +
                                    "lineCount=${layout.lineCount}, lineRight=${layout.getLineRight(0)}, lineBottom=${layout.getLineBottom(0)}, " +
                                    "constraints=${layout.layoutInput.constraints}, style=${layout.layoutInput.style}"
                                // A wrap-content caption can keep its paragraph at the
                                // parent's maximum width: native evidence was a 132px
                                // caption/lineRight inside a 768px paragraph. That makes
                                // hasVisualOverflow true although every glyph fits.
                                // Check actual lines, full caption and vertical overflow.
                                assertEquals("The rendered action must retain its complete caption", label, layout.layoutInput.text.text)
                                assertFalse(geometry, layout.didOverflowHeight)
                                assertTrue(geometry, (0 until layout.lineCount).all { line ->
                                    layout.getLineLeft(line) >= -1f &&
                                        layout.getLineRight(line) <= layout.size.width + 1f &&
                                        layout.getLineBottom(line) <= layout.size.height + 1f
                                })
                                assertEquals("No caption characters may be truncated", label.length,
                                    layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
                            }
                        }
                    }
                    assertTrue("Long diagnostics must have a real scroll range", nodes(root).any {
                        it.config.getOrNull(SemanticsActions.ScrollBy) != null &&
                            (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f) > 0f
                    })
                }
            }
        }
    }

    @Test fun retryPrivacySelectionAndCopyFeedbackUseTheActualPanelActions() {
        var retries = 0
        var closes = 0
        capture("diagnostics_failed_recovery", InterfaceStyle.CLASSIC_TERMINAL, false, 320, 400,
            WeatherDiagnosticsState.Failed, onRetry = { retries++ }, onDismiss = { closes++ }) { root ->
            button(root, "重新读取").config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            button(root, "关闭").config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            assertEquals(1, retries)
            assertEquals(1, closes)
        }
        var privateChoice: Boolean? = null
        var copies = 0
        capture("diagnostics_copied_private_choice", InterfaceStyle.PHOSPHOR_VISTA, false, 320, 800,
            WeatherDiagnosticsState.Ready(report), copied = true,
            onPrivacy = { privateChoice = it }, onCopy = { copies++ }) { root ->
            val choice = nodes(root).first {
                it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox &&
                    it.config.getOrNull(SemanticsActions.OnClick) != null
            }
            choice.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            assertEquals(true, privateChoice)
            val copy = button(root, "已复制")
            assertFalse(copy.config.contains(SemanticsProperties.Disabled))
            copy.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            assertEquals(1, copies)
        }
    }

    private fun button(root: SemanticsNode, label: String): SemanticsNode = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && nodes(node).any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
