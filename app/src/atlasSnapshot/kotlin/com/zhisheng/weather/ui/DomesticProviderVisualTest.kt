package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.*
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DomesticProviderVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5, theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun newProviderFormsKeepVerificationAndBackReachable() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for (kind in listOf(ProviderWizardKind.SENIVERSE, ProviderWizardKind.MOJI)) {
                for ((width, height, font) in listOf(Triple(320, 640, 2f), Triple(800, 360, 1.3f))) {
                    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
                        screenHeight = height * 3, fontScale = font,
                        orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
                    var checked = false
                    var scrolled = false
                    var diagnostic = "not rendered"
                    val host = ComposeView(paparazzi.context).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                            ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                                homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                                val model = remember { ProviderSetupViewModel(kind) }
                                Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).padding(12.dp), contentAlignment = Alignment.Center) {
                                    ProviderSetupPanel(ProviderSetupUiState(kind, step = 2), model, width < 360, true, {}, {},
                                        Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = (height - 24).dp))
                                }
                            }
                        } }
                        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                                    view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                        ?.invoke(view) as? SemanticsOwner
                                } ?: return true
                                val root = owner.unmergedRootSemanticsNode
                                val nodes = nodes(root)
                                val action = nodes.firstOrNull { node -> node.config.getOrNull(SemanticsActions.OnClick) != null &&
                                    nodes(node).any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "开始真实验证并保存" } } }
                                    ?: error("Missing verification action")
                                val bounds = action.boundsInRoot
                                assertTrue("Verification must fit the viewport", bounds.bottom <= root.boundsInRoot.bottom + 1f &&
                                    bounds.top >= root.boundsInRoot.top - 1f && bounds.height + 1f >= action.size.height)
                                assertTrue("Actual provider fields must be present", nodes.any { it.config.getOrNull(SemanticsProperties.EditableText) != null })
                                assertTrue("Long credential forms must retain scrolling", nodes.any { it.config.getOrNull(SemanticsActions.ScrollBy) != null })
                                if (!scrolled) {
                                    val viewport = nodes.first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                                    val input = nodes.last { it.config.getOrNull(SemanticsProperties.EditableText) != null }
                                    // Scroll to the input, not past it to the trailing paste
                                    // button. Issue one gesture, then await rendered bounds.
                                    val offset = (input.positionInRoot.y - viewport.boundsInRoot.top).coerceAtLeast(0f)
                                    assertTrue(viewport.config[SemanticsActions.ScrollBy].action?.invoke(0f, offset) == true)
                                    scrolled = true
                                    this@apply.postInvalidateOnAnimation()
                                    return true
                                }
                                val field = nodes.last { it.config.getOrNull(SemanticsProperties.EditableText) != null }
                                diagnostic = "field=${field.boundsInRoot},size=${field.size},position=${field.positionInRoot},action=${action.boundsInRoot}"
                                // ScrollBy launches on the real Compose coroutine scope. Wait
                                // for a rendered frame to apply it before checking the same bounds.
                                if (!(field.boundsInRoot.height > 1f && field.boundsInRoot.height + 1f >= field.size.height &&
                                    field.boundsInRoot.bottom <= action.boundsInRoot.top + 1f)) {
                                    this@apply.postInvalidateOnAnimation()
                                    return true
                                }
                                val back = nodes.first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("返回上一步") == true }
                                assertTrue("The header back control must keep its vector icon", nodes(back).none {
                                    it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "←" }
                                })
                                checked = true
                                return true
                            }
                        })
                    }
                    paparazzi.gif(host, "${kind}_${style.key}_${light}_${width}", end = 1500, fps = 4)
                    assertTrue("The last credential field and fixed verification action must be reachable after scrolling: $kind/$style/$light/$width $diagnostic", checked)
                }
            }
        }
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
