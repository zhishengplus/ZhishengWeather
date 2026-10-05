/* Frontend Design · subject: developer weather-signal lab · layout: real-home sandbox
 * signature: condition matrix drives the production home surface · no repository writes
 */
package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.PrecipitationPhase
import com.zhisheng.weather.model.ThermalModifier
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.model.WeatherProfile
import com.zhisheng.weather.ui.home.SimulatedWeatherSurface
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.i18n.uiText

internal data class AtmosphereScenario(
    val code: String,
    val name: String,
    val condition: WeatherCondition,
    val night: Boolean = false,
    val thermal: ThermalModifier = ThermalModifier.NONE,
    val minuteOfDay: Int = if (night) 22 * 60 else 12 * 60,
)

internal val atmosphereScenarios = listOf(
    AtmosphereScenario("CLR", "晴", WeatherCondition.CLEAR),
    AtmosphereScenario("NIT", "晴夜", WeatherCondition.CLEAR_NIGHT, night = true),
    AtmosphereScenario("PCL", "多云", WeatherCondition.PARTLY_CLOUDY),
    AtmosphereScenario("PCN", "夜间多云", WeatherCondition.PARTLY_CLOUDY_NIGHT, night = true),
    AtmosphereScenario("CLD", "阴云", WeatherCondition.CLOUDY),
    AtmosphereScenario("OVC", "阴", WeatherCondition.OVERCAST),
    AtmosphereScenario("DRZ", "小雨", WeatherCondition.DRIZZLE),
    AtmosphereScenario("RAN", "雨", WeatherCondition.RAIN),
    AtmosphereScenario("STM", "雷暴", WeatherCondition.THUNDERSTORM),
    AtmosphereScenario("SLT", "雨夹雪", WeatherCondition.SLEET),
    AtmosphereScenario("SNW", "雪", WeatherCondition.SNOW),
    AtmosphereScenario("HAL", "冰雹", WeatherCondition.HAIL),
    AtmosphereScenario("FZR", "冻雨", WeatherCondition.FREEZING_RAIN),
    AtmosphereScenario("FZD", "冻毛毛雨", WeatherCondition.FREEZING_DRIZZLE),
    AtmosphereScenario("FOG", "雾", WeatherCondition.FOG),
    AtmosphereScenario("HAZ", "霾", WeatherCondition.HAZE),
    AtmosphereScenario("SND", "沙尘", WeatherCondition.SAND),
    AtmosphereScenario("WND", "大风", WeatherCondition.WIND),
    AtmosphereScenario("HOT", "酷热", WeatherCondition.CLEAR, thermal = ThermalModifier.HOT),
    AtmosphereScenario("ICE", "严寒", WeatherCondition.CLEAR, thermal = ThermalModifier.COLD),
).let { daytime ->
    daytime + daytime.filter { !it.night && it.thermal == ThermalModifier.NONE &&
        it.condition != WeatherCondition.CLEAR && it.condition != WeatherCondition.PARTLY_CLOUDY }
        .map { it.copy(code = it.code + "N", name = it.name + "·夜", night = true, minuteOfDay = 22 * 60) } +
        listOf(AtmosphereScenario("DAWN", "晨光", WeatherCondition.CLEAR, minuteOfDay = 6 * 60 + 31),
            AtmosphereScenario("DUSK", "暮色", WeatherCondition.CLEAR, minuteOfDay = 19 * 60 + 42),
            AtmosphereScenario("UNKNOWN", "暂无天气", WeatherCondition.UNKNOWN))
}

private val SIM_CITY = City(
    name = "模拟站",
    affiliation = "ATMOSPHERE LAB",
    latitude = 38.52,
    longitude = 102.21,
    locationKey = "simulation:atmosphere-lab",
)

@Composable
fun AtmosphereLabScreen(
    initialLevel: AmbienceLevel,
    onBack: () -> Unit,
) {
    var scenarioIndex by rememberSaveable { mutableIntStateOf(0) }
    var intensityIndex by rememberSaveable { mutableIntStateOf(1) }
    var levelKey by rememberSaveable { mutableStateOf(initialLevel.key) }
    val scenario = atmosphereScenarios[scenarioIndex]
    val intensity = WeatherIntensity.entries[intensityIndex]
    val level = AmbienceLevel.from(levelKey)
    val data = remember(scenarioIndex, intensityIndex) { simulatedWeather(scenario, intensity) }
    val prefs = remember(level, scenario.condition) {
        DisplayPrefs(
            showAqi = true,
            showIndices = false,
            showYesterday = false,
            showPrecip = scenario.condition.isPrecipitation,
            showTelemetry = true,
            showSpacetime = false,
            scanlines = false,
            ambience = level,
            bootAnim = false,
        )
    }

    // The preview is not the live city's shared element, even while an overlay animates.
    CompositionLocalProvider(LocalWeatherContinuity provides null) {
    SimulatedWeatherSurface(
        data = data,
        city = SIM_CITY,
        prefs = prefs,
        night = scenario.night,
        referenceTimeMillis = data.updateTime,
        header = {
            LabHeader(
                scenario = scenario,
                selectedIndex = scenarioIndex,
                level = level,
                intensity = intensity,
                onBack = onBack,
                onSelect = { scenarioIndex = it },
                onToggleLevel = {
                    levelKey = when (level) {
                        AmbienceLevel.SUBTLE -> AmbienceLevel.VIVID.key
                        AmbienceLevel.VIVID -> AmbienceLevel.INTENSE.key
                        AmbienceLevel.INTENSE -> AmbienceLevel.OFF.key
                        else -> AmbienceLevel.SUBTLE.key
                    }
                },
                onCycleIntensity = { intensityIndex = (intensityIndex + 1) % WeatherIntensity.entries.size },
            )
        },
    )
    }
}

@Composable
private fun LabHeader(
    scenario: AtmosphereScenario,
    selectedIndex: Int,
    level: AmbienceLevel,
    intensity: WeatherIntensity,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
    onToggleLevel: () -> Unit,
    onCycleIntensity: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(ZhishengSurface).statusBarsPadding()) {
        BoxWithConstraints {
        val compact = com.zhisheng.weather.ui.theme.isPhosphorVista && (maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f)
        Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回设置"), tint = ZhishengText)
            }
            Column(Modifier.weight(1f)) {
                Text("氛围实验室", style = MaterialTheme.typography.titleMedium, color = ZhishengOrange, fontWeight = FontWeight.Bold)
                Text(
                    if (com.zhisheng.weather.ui.theme.isPhosphorVista) "天气效果预览" else "SIMULATION / NO WRITE / ${scenario.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!compact) {
                LabCommand(level.cn, ZhishengCyan, onToggleLevel)
                Spacer(Modifier.width(4.dp))
                LabCommand(intensityLabel(intensity), ZhishengMint, onCycleIntensity)
                Spacer(Modifier.width(8.dp))
            }
        }
        if (compact) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LabCommand("效果 · ${level.cn}", ZhishengCyan, onToggleLevel)
            LabCommand("天气强度 · ${intensityLabel(intensity)}", ZhishengMint, onCycleIntensity)
        }
        }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .border(width = 1.dp, color = ZhishengCardBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            atmosphereScenarios.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Text(
                    text = if (com.zhisheng.weather.ui.theme.isPhosphorVista) item.name else "${(index + 1).toString().padStart(2, '0')}//${item.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) ZhishengText else ZhishengTextSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (selected) ZhishengOrange.copy(alpha = 0.14f) else ZhishengSurface)
                        .border(1.dp, if (selected) ZhishengOrange else ZhishengCardBorder)
                        .clickable(role = Role.Button) { onSelect(index) }
                        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
        Text(
            "预览效果不会改变当前城市的天气。",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LabCommand(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        if (com.zhisheng.weather.ui.theme.isPhosphorVista) label else "[$label]",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 14.dp),
    )
}

private fun intensityLabel(v: WeatherIntensity): String = when (v) {
    WeatherIntensity.LIGHT -> "轻"
    WeatherIntensity.MODERATE -> "中"
    WeatherIntensity.HEAVY -> "强"
    WeatherIntensity.EXTREME -> "极"
}

internal fun simulatedWeather(s: AtmosphereScenario, intensity: WeatherIntensity): WeatherData {
    val now = java.time.LocalDate.now(java.time.ZoneOffset.ofHours(8))
        .atTime(s.minuteOfDay / 60, s.minuteOfDay % 60).toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
    val phase = when (s.condition) {
        WeatherCondition.SNOW -> PrecipitationPhase.SNOW
        WeatherCondition.SLEET -> PrecipitationPhase.MIXED
        WeatherCondition.HAIL -> PrecipitationPhase.HAIL
        WeatherCondition.FREEZING_RAIN -> PrecipitationPhase.FREEZING_RAIN
        WeatherCondition.FREEZING_DRIZZLE -> PrecipitationPhase.FREEZING_DRIZZLE
        WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.THUNDERSTORM -> PrecipitationPhase.RAIN
        else -> PrecipitationPhase.NONE
    }
    val precip = when (intensity) {
        WeatherIntensity.LIGHT -> 0.3
        WeatherIntensity.MODERATE -> 2.8
        WeatherIntensity.HEAVY -> 8.6
        WeatherIntensity.EXTREME -> 18.0
    }
    val profile = WeatherProfile(
        condition = s.condition,
        intensity = intensity,
        phase = phase,
        shower = s.condition in setOf(WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL, WeatherCondition.SLEET),
        thunder = s.condition == WeatherCondition.THUNDERSTORM || s.condition == WeatherCondition.HAIL,
        freezing = s.condition == WeatherCondition.FREEZING_RAIN || s.condition == WeatherCondition.FREEZING_DRIZZLE,
        thermal = s.thermal,
        source = "SIMULATION",
        rawCode = s.code,
    )
    val temp = when (s.thermal) { ThermalModifier.HOT -> 39.0; ThermalModifier.COLD -> -18.0; else -> if (s.condition == WeatherCondition.SNOW) -3.0 else 19.0 }
    val current = CurrentWeather(
        temperature = temp,
        feelsLike = temp + if (s.thermal == ThermalModifier.HOT) 4 else -2,
        condition = s.condition,
        weatherText = s.name,
        humidity = if (s.condition.isPrecipitation) 88.0 else 54.0,
        windSpeed = if (s.condition == WeatherCondition.WIND || s.condition == WeatherCondition.SAND) 52.0 else 18.0,
        windDirectionDeg = 315.0,
        pressure = 842.0,
        uvIndex = if (s.night) 0 else 4,
        visibility = when (s.condition) { WeatherCondition.FOG -> 1.2; WeatherCondition.HAZE -> 4.0; WeatherCondition.SAND -> 2.5; else -> 24.0 },
        dewPoint = temp - 3,
        cloudCover = when (s.condition) { WeatherCondition.OVERCAST -> 96.0; WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> 58.0; else -> 22.0 },
        windGust = if (s.condition == WeatherCondition.WIND) 76.0 else 28.0,
        precipMm = if (s.condition.isPrecipitation) precip else 0.0,
        profile = profile,
    )
    val hourly = List(8) { i ->
        HourlyWeather(
            timeMillis = now + i * 3_600_000L,
            temperature = temp + (i % 4) - 1,
            condition = s.condition,
            windSpeed = current.windSpeed,
            precipProb = if (s.condition.isPrecipitation) 92 - i * 5 else 8,
            aqi = if (s.condition == WeatherCondition.HAZE) 186 else 42,
            profile = profile,
        )
    }
    val daily = List(7) { i ->
        DailyWeather(
            dateMillis = now + i * 86_400_000L,
            high = temp + 5 + i % 2,
            low = temp - 3,
            condition = s.condition,
            windSpeed = current.windSpeed,
            precipProbability = if (s.condition.isPrecipitation) 86 else 10,
            sunrise = "06:31",
            sunset = "19:42",
            weatherText = s.name,
            profile = profile,
        )
    }
    val minute = if (s.condition.isPrecipitation) {
        List(120) { i -> MinutePrecip(now + i * 60_000L, (precip * (0.65 + (i % 9) * 0.04)).toFloat(), phase) }
    } else emptyList()
    return WeatherData(
        current = current,
        hourly = hourly,
        daily = daily,
        aqi = AqiInfo(
            value = if (s.condition == WeatherCondition.HAZE || s.condition == WeatherCondition.SAND) 186 else 42,
            level = if (s.condition == WeatherCondition.HAZE || s.condition == WeatherCondition.SAND) "中度污染" else "优",
            pm25 = if (s.condition == WeatherCondition.HAZE) "132" else "18",
            pm10 = if (s.condition == WeatherCondition.SAND) "286" else "36",
        ),
        updateTime = now,
        rainNowcast = if (s.condition.isPrecipitation) "模拟降水信号持续输入" else null,
        rainMinutes = minute,
        rainMeta = minute.takeIf { it.isNotEmpty() }?.let { RainMeta("SIMULATION", 1, now) },
        dataSource = "SIMULATION",
        blockSources = mapOf("current" to "SIMULATION", "forecast" to "SIMULATION"),
        utcOffsetSeconds = 8 * 3600,
    )
}
