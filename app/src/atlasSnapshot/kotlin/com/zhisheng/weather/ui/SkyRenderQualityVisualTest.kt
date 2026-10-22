package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot as ComposeSnapshot
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.resources.Density
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/** Native Canvas fallback and handoff checks. Layoutlib cannot validate the device AGSL driver. */
class SkyRenderQualityVisualTest {
    private data class Pixels(val width: Int, val height: Int, val data: IntArray)
    private class DrawnFrame { var frame: NaturalSkyFrame? = null; var count = 0 }
    private var inspectImage: ((Pixels) -> Unit)? = null
    private var frameFailure: Throwable? = null
    private val snapshots = object : SnapshotHandler {
        override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
            var frame = 0
            return Proxy.newProxyInstance(SnapshotHandler.FrameHandler::class.java.classLoader,
                arrayOf(SnapshotHandler.FrameHandler::class.java)) { _, method, args ->
                if (method.name == "handle") {
                    val image = requireNotNull(args?.get(0))
                    val width = image.javaClass.getMethod("getWidth").invoke(image) as Int
                    val height = image.javaClass.getMethod("getHeight").invoke(image) as Int
                    val integer = Integer.TYPE
                    val pixels = image.javaClass.getMethod("getRGB", integer, integer, integer, integer,
                        IntArray::class.java, integer, integer).invoke(image, 0, 0, width, height, null, 0, width) as IntArray
                    if (frameFailure == null) try {
                        inspectImage?.invoke(Pixels(width, height, pixels))
                    } catch (failure: Throwable) {
                        // Let the native render sequence finish and detach its Compose host before
                        // reporting an assertion. A failed frame must not advance the input driver.
                        frameFailure = AssertionError("Native sky ${snapshot.name}, frame $frame", failure)
                    }
                    val test = snapshot.testName
                    val target = File("src/test/snapshots/images",
                        "${test.packageName}_${test.className}_${test.methodName}_${snapshot.name}_${frame++}.png")
                    requireNotNull(target.parentFile).mkdirs()
                    // The host has java.desktop; Android test compilation intentionally does not.
                    Class.forName("javax.imageio.ImageIO").getMethod("write", Class.forName("java.awt.image.RenderedImage"),
                        String::class.java, File::class.java).invoke(null, image, "png", target)
                }
                null
            } as SnapshotHandler.FrameHandler
        }
        override fun close() {}
    }

    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(
        screenWidth = 360, screenHeight = 640, density = Density.MEDIUM),
        theme = "android:Theme.Material.Light.NoActionBar", snapshotHandler = snapshots)

    private fun frame(kind: NaturalWeather, light: Boolean, identity: String = "origin"): NaturalSkyFrame {
        val state = NaturalLightState(kind, daylight = 1f, twilight = .08f, intensity = .65f,
            cloud = if (kind == NaturalWeather.CLEAR) .10f else .85f, wind = .2f, freezing = false)
        return NaturalSkyFrame(state, naturalLightPalette(state, light), identity = identity)
    }

    private fun capabilities(quality: SkyRenderQuality) = when (quality) {
        SkyRenderQuality.HIGH -> SkyRenderCapabilities(34)
        SkyRenderQuality.SIMPLIFIED -> SkyRenderCapabilities(26, lowMemory = true)
        SkyRenderQuality.STATIC -> SkyRenderCapabilities(26, animationsEnabled = false)
    }

    @Test fun allCapabilityTiersKeepTheSamePaletteAndWeatherInBothThemes() {
        for (light in listOf(true, false)) for (kind in listOf(NaturalWeather.CLEAR, NaturalWeather.RAIN, NaturalWeather.SNOW)) {
            val expected = frame(kind, light)
            val bottomColors = mutableListOf<Int>()
            for (quality in SkyRenderQuality.entries) {
                val drawn = DrawnFrame()
                var inspected = false
                val capability = capabilities(quality)
                assertEquals(quality, skyRenderQuality(capability, active = true, enabled = true))
                inspectImage = { pixels ->
                    assertEquals(expected, drawn.frame)
                    val bottom = pixels.data[(pixels.height - 1) * pixels.width + pixels.width / 2]
                    assertColorClose(expected.palette.base.toArgb(), bottom, 2)
                    bottomColors += bottom
                    assertTrue("The actual atmosphere must have depth", pixels.data[20 * pixels.width + 20] != bottom)
                    inspected = true
                }
                val host = host(light) {
                    CompositionLocalProvider(LocalSkyRenderCapabilities provides capability,
                        LocalSkyRenderQuality provides quality, LocalNaturalPreviewPhase provides .173) {
                        NaturalWeatherBackdrop(expected.state, expected.palette, AmbienceLevel.VIVID,
                            onFrame = { drawn.frame = it }, identity = expected.identity)
                    }
                }
                capture { paparazzi.snapshot(host, "sky_tier_${quality.name}_${kind.name}_$light") }
                assertTrue("Native $quality / $kind / $light must render", inspected)
            }
            bottomColors.zipWithNext().forEach { (a, b) -> assertColorClose(a, b, 0) }
        }
        inspectImage = null
    }

    @Test fun citySkyMovesContinuouslyReversesAndHoldsWhenTheNextEndpointIsMissing() {
        for (light in listOf(true, false)) {
            val clear = frame(NaturalWeather.CLEAR, light)
            val rain = frame(NaturalWeather.RAIN, light, "adjacent")
            val progress = mutableFloatStateOf(0f)
            val endpoints = mutableStateOf<Pair<NaturalSkyFrame, NaturalSkyFrame>?>(clear to rain)
            val drawn = DrawnFrame()
            val sequence = listOf(0f, .25f, .5f, .75f, 1f, .75f, .5f, .25f, 0f, .5f)
            var stage = 0
            var beforeMissing: Pixels? = null
            lateinit var host: ComposeView
            inspectImage = { pixels ->
                if (stage < sequence.size) {
                    assertEquals("The rendered sky follows the continuous pager position", clear.mix(rain, sequence[stage]), drawn.frame)
                    stage++
                    if (stage < sequence.size) postForNextRender(host) { progress.floatValue = sequence[stage] }
                    else { beforeMissing = pixels; postForNextRender(host) { endpoints.value = null } }
                } else {
                    assertEquals("Missing weather must retain the last real sky", clear.mix(rain, .5f), drawn.frame)
                    assertArrayEquals("A missing target cannot replace the sky with a fabricated/empty frame",
                        beforeMissing!!.data, pixels.data)
                    stage++
                }
            }
            host = this@SkyRenderQualityVisualTest.host(light) {
                CompositionLocalProvider(LocalSkyRenderCapabilities provides SkyRenderCapabilities(26),
                    LocalNaturalPreviewPhase provides .173) {
                    NaturalWeatherBackdrop(clear.state, clear.palette, AmbienceLevel.VIVID,
                        pagingFrames = endpoints.value, pagingFraction = { progress.floatValue }, pagingActive = { true },
                        onFrame = { drawn.frame = it }, identity = clear.identity)
                }
            }
            capture { paparazzi.gif(host, "sky_paging_reverse_missing_$light", end = 2_000, fps = 8) }
            assertTrue("Both drag directions and missing-data frames must execute", stage > sequence.size)
        }
        inspectImage = null
    }

    @Test fun staticRainAndSnowDoNotMoveWhenTheSharedClockChanges() {
        for (light in listOf(true, false)) for (kind in listOf(NaturalWeather.RAIN, NaturalWeather.SNOW)) {
            val expected = frame(kind, light)
            val sharedClock = mutableStateOf(.173)
            val redrawEpoch = mutableIntStateOf(0)
            val drawn = DrawnFrame()
            var first: Pixels? = null
            var checked = 0
            lateinit var host: ComposeView
            inspectImage = { pixels ->
                assertEquals(expected, drawn.frame)
                first?.let { assertArrayEquals("STATIC must freeze actual Canvas particles, not only the palette", it.data, pixels.data) }
                    ?: run { first = pixels }
                checked++
                postForNextRender(host) {
                    sharedClock.value = (sharedClock.value + .21) % 1.0
                    redrawEpoch.intValue++
                }
            }
            host = this@SkyRenderQualityVisualTest.host(light) {
                // Force the old-API Canvas renderer while exercising the real (non-preview) phase gate.
                // Inspection's normal fixed phase would otherwise make this assertion vacuous.
                CompositionLocalProvider(LocalInspectionMode provides false,
                    LocalSkyRenderCapabilities provides capabilities(SkyRenderQuality.STATIC),
                    LocalSkyRenderQuality provides SkyRenderQuality.STATIC,
                    LocalNaturalPreviewPhase provides null, LocalVistaGlowPhase provides sharedClock) {
                    NaturalWeatherBackdrop(expected.state, expected.palette, AmbienceLevel.VIVID,
                        // Invalidate the real draw observer without moving its parallax. Invalidating
                        // only the host can legally replay an unchanged Compose graphics-layer cache.
                        parallax = { redrawEpoch.intValue; 0f },
                        onFrame = { drawn.frame = it; drawn.count++ }, identity = expected.identity)
                }
            }
            capture { paparazzi.gif(host, "sky_static_${kind.name}_$light", end = 1_000, fps = 4) }
            assertTrue("Static comparison must span multiple clock values: captured=$checked, drawn=${drawn.count}, epoch=${redrawEpoch.intValue}", checked >= 3)
            assertTrue("Static comparison must redraw the production Canvas across clock changes: captured=$checked, drawn=${drawn.count}, epoch=${redrawEpoch.intValue}", drawn.count >= 3)
        }
        inspectImage = null
    }

    private fun host(light: Boolean, content: @Composable () -> Unit) = ComposeView(paparazzi.context).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = InterfaceStyle.PHOSPHOR_VISTA, softGlow = false) {
                    content()
                }
            }
        }
    }

    private fun postForNextRender(host: ComposeView, change: () -> Unit) {
        // FrameHandler runs after render, outside the Android/Compose UI event loop. Queue the
        // input before Paparazzi's next Handler + Choreographer pass and publish its snapshot.
        host.post {
            change()
            ComposeSnapshot.sendApplyNotifications()
            host.invalidate()
            host.postInvalidateOnAnimation()
        }
    }

    private fun capture(render: () -> Unit) {
        frameFailure = null
        try {
            render()
        } catch (failure: Throwable) {
            frameFailure?.let { failure.addSuppressed(it) }
            throw failure
        }
        frameFailure?.let { throw it }
    }

    private fun assertColorClose(expected: Int, actual: Int, tolerance: Int) {
        for (shift in listOf(24, 16, 8, 0)) assertTrue("Native Canvas palette differs: $expected / $actual",
            kotlin.math.abs(((expected ushr shift) and 255) - ((actual ushr shift) and 255)) <= tolerance)
    }
}
