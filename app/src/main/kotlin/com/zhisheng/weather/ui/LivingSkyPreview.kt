package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.components.LocalSolarSkyPreview
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import com.zhisheng.weather.ui.components.SolarSkyState
import com.zhisheng.weather.ui.home.SimulatedWeatherSurface
import com.zhisheng.weather.ui.theme.*

internal data class SkyPreviewMoment(val label: String, val altitude: Float, val minutes: Int, val rising: Boolean = false)
internal val skyPreviewMoments = listOf(
    SkyPreviewMoment("晨曦", -2f, 6 * 60 + 20, true), SkyPreviewMoment("日间", 35f, 14 * 60),
    SkyPreviewMoment("暖金", 5f, 19 * 60 + 15), SkyPreviewMoment("霞光", -1.5f, 19 * 60 + 42),
    SkyPreviewMoment("蓝调", -7f, 20 * 60 + 10), SkyPreviewMoment("夜色", -24f, 22 * 60),
)

@Composable
internal fun LivingSkyPreviewDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LivingSkyPreviewContent(onClose, Modifier.fillMaxSize().systemBarsPadding().padding(12.dp))
    }
}

/** Production native sky and home layout; this example never writes weather, cities, or preferences. */
@Composable
internal fun LivingSkyPreviewContent(onClose: () -> Unit, modifier: Modifier = Modifier,
    initialMoment: Int = 3, initialCondition: Int = 1) {
    var momentIndex by rememberSaveable { mutableIntStateOf(initialMoment.coerceIn(skyPreviewMoments.indices)) }
    var conditionIndex by rememberSaveable { mutableIntStateOf(initialCondition.coerceIn(0, 3)) }
    val moment = skyPreviewMoments[momentIndex]
    val conditions = listOf("晴" to WeatherCondition.CLEAR, "多云" to WeatherCondition.PARTLY_CLOUDY,
        "下雨" to WeatherCondition.RAIN, "雾" to WeatherCondition.FOG)
    val night = moment.altitude < -4f
    val scenario = AtmosphereScenario("SKY_PREVIEW", conditions[conditionIndex].first,
        conditions[conditionIndex].second, night = night, minuteOfDay = moment.minutes)
    val data = remember(momentIndex, conditionIndex) { simulatedWeather(scenario, WeatherIntensity.MODERATE) }
    val prefs = remember { DisplayPrefs(ambience = AmbienceLevel.VIVID, scanlines = false,
        showIndices = false, showYesterday = false, showTelemetry = false, showSpacetime = false,
        showSkyPhotography = false, showCoastWeather = false, showPrecip = false, bootAnim = false) }
    val city = remember { City("示例城市", "天空预览", 38.52, 102.21, "simulation:living-sky") }
    val solar = SolarSkyState(moment.altitude, if (moment.rising) 95f else 265f, moment.rising)
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = ZhishengBg) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(ZhishengSurface).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("一天的天空", color = ZhishengText, style = MaterialTheme.typography.titleLarge)
                        Text("示例预览 · 仅展示效果", color = ZhishengTextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onClose) { Text("完成", color = ZhishengMint) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    skyPreviewMoments.forEachIndexed { index, value ->
                        FilterChip(selected = index == momentIndex, onClick = { momentIndex = index },
                            label = { Text(value.label) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    conditions.forEachIndexed { index, value ->
                        FilterChip(selected = index == conditionIndex, onClick = { conditionIndex = index },
                            label = { Text(value.first) })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalSolarSkyPreview provides solar, LocalWeatherContinuity provides null) {
                    SimulatedWeatherSurface(data, city, prefs, night = night, referenceTimeMillis = data.updateTime,
                        livingSkyOverride = true, header = {})
                }
            }
        }
    }
}
