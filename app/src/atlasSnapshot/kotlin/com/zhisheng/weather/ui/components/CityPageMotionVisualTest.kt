package com.zhisheng.weather.ui.components

import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Small native fixture: actual draw reads, with no weather data, network or global animation loop. */
class CityPageMotionVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(
        screenWidth = 180, screenHeight = 160, density = Density.MEDIUM),
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun activePreparedTransitionsPauseAndResumeDrawingWithoutRecomposingStaticBody() {
        val running = mutableStateOf(true)
        val source = CountingClock(.1)
        val drawPulse = mutableIntStateOf(0)
        val probe = Probe()
        var stage = 0
        var lastInputDraw = 0
        var contentAtStart = 0
        var bodyAtStart = 0
        var dynamicAtTick = 0
        var pausedReads = 0
        var checked = false
        var failure: Throwable? = null

        fun stableBody() {
            assertEquals("A motion transition must not recompose the static page content scope",
                contentAtStart, probe.contentCompositions)
            assertEquals("A body which reads neither motion nor phase must keep its composition",
                bodyAtStart, probe.bodyCompositions)
        }
        fun requestDraw() {
            // Explicit redraw proves freezing even if another visual change redraws a hidden page.
            // This value is consumed only in the Canvas draw lambda, never in composition.
            lastInputDraw = probe.draws
            drawPulse.intValue++
            Snapshot.sendApplyNotifications()
        }
        fun near(expected: Double) = abs(probe.phase - expected) < .000001

        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalVistaGlowPhase provides source) {
                    MotionSwitch(running) {
                        SideEffect { probe.contentCompositions++ }
                        Column(Modifier.fillMaxSize().background(Color(0xFF152331)).padding(10.dp)) {
                            StaticBody(probe)
                            MovingMark(probe, drawPulse)
                        }
                    }
                }
            }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (checked || failure != null) return true
                    try {
                        when (stage) {
                            0 -> if (probe.draws > 0) { requestDraw(); stage++ }
                            1 -> if (probe.draws > lastInputDraw) {
                                assertTrue("ACTIVE initially samples the shared clock", near(.1))
                                contentAtStart = probe.contentCompositions
                                bodyAtStart = probe.bodyCompositions
                                dynamicAtTick = probe.dynamicCompositions
                                source.advance(.2); requestDraw(); stage++
                            }
                            2 -> if (probe.draws > lastInputDraw && near(.2)) {
                                stableBody()
                                assertEquals("Shared clock ticks belong to drawing, not composition",
                                    dynamicAtTick, probe.dynamicCompositions)
                                running.value = false; requestDraw(); stage++
                            }
                            3 -> if (!probe.running && probe.draws > lastInputDraw) {
                                assertTrue("PREPARED retains the most recently visible phase", near(.2))
                                stableBody()
                                pausedReads = source.reads
                                source.advance(.8); requestDraw(); stage++
                            }
                            4 -> if (probe.draws > lastInputDraw) {
                                assertTrue("A forced native redraw must preserve the held phase", near(.2))
                                assertEquals("PREPARED must not read or subscribe to the advancing shared clock",
                                    pausedReads, source.reads)
                                stableBody()
                                running.value = true; requestDraw(); stage++
                            }
                            5 -> if (probe.running && probe.draws > lastInputDraw) {
                                assertTrue("Resume must start at the held phase rather than the elapsed global phase", near(.2))
                                stableBody()
                                dynamicAtTick = probe.dynamicCompositions
                                source.advance(.9); requestDraw(); stage++
                            }
                            6 -> if (probe.draws > lastInputDraw && near(.3)) {
                                stableBody()
                                assertEquals("Subsequent resumed ticks must still be draw-only",
                                    dynamicAtTick, probe.dynamicCompositions)
                                assertTrue("The fixture must execute real native draws across every transition", probe.draws >= 7)
                                checked = true
                            }
                        }
                    } catch (error: Throwable) {
                        // Complete and detach the host before surfacing a failed assertion.
                        failure = AssertionError("City page motion stage=$stage; " +
                            "phase=${probe.phase}, draws=${probe.draws}, running=${probe.running}, " +
                            "content=${probe.contentCompositions}/$contentAtStart, " +
                            "body=${probe.bodyCompositions}/$bodyAtStart, clockReads=${source.reads}", error)
                    }
                    return true
                }
            })
        }
        paparazzi.gif(host, "city_page_motion_retains_static_body", end = 2_000, fps = 12)
        failure?.let { throw it }
        assertTrue("Native motion checks must finish; stage=$stage, phase=${probe.phase}, " +
            "draws=${probe.draws}, content=${probe.contentCompositions}, body=${probe.bodyCompositions}", checked)
    }

    /** Reading running is deliberately isolated from the static content lambda. */
    @Composable private fun MotionSwitch(running: State<Boolean>, content: @Composable () -> Unit) {
        ProvideCityPageMotion(running.value, content)
    }

    @Composable private fun StaticBody(probe: Probe) {
        SideEffect { probe.bodyCompositions++ }
        BasicText("WEATHER CONTENT", style = androidx.compose.ui.text.TextStyle(color = Color.White))
    }

    @Composable private fun MovingMark(probe: Probe, pulse: State<Int>) {
        val clock = LocalVistaGlowPhase.current
        val running = LocalCityPageMotionRunning.current
        SideEffect { probe.running = running; probe.dynamicCompositions++ }
        Canvas(Modifier.size(150.dp, 96.dp)) {
            pulse.value
            val phase = clock.value
            probe.phase = phase
            probe.draws++
            drawCircle(if (running) Color(0xFF9ADACB) else Color(0xFFD6B68D), 5.dp.toPx(),
                Offset(10.dp.toPx() + phase.toFloat() * 120.dp.toPx(), 48.dp.toPx()))
        }
    }

    private class Probe {
        var contentCompositions = 0
        var bodyCompositions = 0
        var dynamicCompositions = 0
        var draws = 0
        var phase = Double.NaN
        var running = true
    }

    private class CountingClock(initial: Double) : State<Double> {
        private val phase = mutableStateOf(initial)
        var reads = 0
        override val value: Double get() { reads++; return phase.value }
        fun advance(value: Double) { phase.value = value }
    }
}
