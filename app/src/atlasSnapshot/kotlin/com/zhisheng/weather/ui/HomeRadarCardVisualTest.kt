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

class HomeRadarCardVisualTest {
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
        var failure: Throwable? = null
        return ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) { content() }
            } }
            if (check != null || progressiveCheck != null) viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        try {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        }?.unmergedRootSemanticsNode ?: error("Missing actual Compose semantics")
                        check?.invoke(root)
                        checked = progressiveCheck?.invoke(root) ?: true
                        } catch (error: Throwable) { failure=error;checked=true }
                    }
                    // Paparazzi advances the frame clock; do not request recursive draws at the same time.
                    return true
                }
            })
            if (progressiveCheck == null) paparazzi.snapshot(this, name)
            else paparazzi.gif(this, name, end = 2_000, fps = 4)
            failure?.let { throw it }
            assertTrue("Native check must run before Paparazzi disposes the composition", checked)
        }
    }


    @Test fun radarCardStatesWrapAndOpenTheExistingDetails() {
        val geography=kotlinx.coroutines.runBlocking { com.zhisheng.weather.ui.home.RadarPreviewGeography.load(paparazzi.context) }
        assertTrue("Bundled geographical reference must load",geography.isNotEmpty())
        val city=City("金昌市","甘肃",38.5,102.2,"fixture")
        val bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawCircle(130f,150f,34f,android.graphics.Paint().apply {color=0xff40aad0.toInt()})
            drawCircle(145f,158f,18f,android.graphics.Paint().apply {color=0xfff1cf56.toInt()})
        }
        val sample=radarTileSample(city.latitude,city.longitude,6)
        for (light in listOf(true,false)) for ((w,h,f) in listOf(Triple(360,680,1f),Triple(320,600,2f),Triple(640,240,2f))) {
            for (kind in listOf("loaded","cached","loading","unavailable","unaccepted","caiyun","no-city")) {
                val tile=when(kind) {
                    "loaded","cached" -> com.zhisheng.weather.ui.home.AtlasRadarTile(bitmap,sample,1800000000000L,if(kind=="cached")"缓存回波" else "最新可用帧")
                    "unavailable" -> com.zhisheng.weather.ui.home.AtlasRadarTile(null,null,null,"本地暂不在覆盖区")
                    else -> null
                }
                val state=com.zhisheng.weather.ui.home.AtlasRadarPreviewState(if(kind=="caiyun")RadarSource.CAIYUN else RadarSource.RAINVIEWER,tile)
                var calls=0
                capture("home_radar_${kind}_${light}_${w}",InterfaceStyle.PHOSPHOR_VISTA,light,w,h,f,
                    check={root ->
                        assertTextWraps(root)
                        val button=nodes(root).first {it.config.getOrNull(SemanticsActions.OnClick)?.label=="查看降水地图与未来两小时"}
                        assertTrue(button.size.height>=48)
                        assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
                        assertEquals(1,calls)
                    }) {
                    Column(Modifier.fillMaxSize().zhishengScreen().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        com.zhisheng.weather.ui.home.HomeRadarCard(if(kind=="no-city")null else city,"Asia/Shanghai",state,
                            kind!="unaccepted",Modifier.zhishengPanel(),{calls++},geographyOverride=geography)
                    }
                }
            }
        }
        bitmap.recycle()
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
