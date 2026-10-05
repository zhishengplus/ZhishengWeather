package com.zhisheng.weather.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.zhisheng.weather.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.HomeUiState
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun CityWeatherList(ui: HomeUiState, modifier: Modifier, onAdd: () -> Unit,
    onSelect: (String) -> Unit, onFavorite: (String) -> Unit, onRemove: (String) -> Unit, active: Boolean) {
    val context = LocalContext.current
    val glass = isPhosphorVista && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    val weather = remember { mutableStateMapOf<String, CachedWeather>() }
    val located by CityRepository.locatedCityKey.collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val visibleCityKeys by remember(list) {
        derivedStateOf(structuralEqualityPolicy()) {
            list.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }
    val clock = if (LocalVistaSoftGlow.current) LocalVistaGlowPhase.current
        else rememberVistaGlowPhase(active && ui.prefs.ambience != AmbienceLevel.OFF)
    LaunchedEffect(active, ui.cities.map { it.locationKey }, ui.weather, ui.sourcePref) {
        if (!active) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val savedKeys = ui.cities.map { it.locationKey }.toSet()
        weather.keys.toList().forEach { key ->
            if (key !in savedKeys || usableCityWeatherCache(weather[key], ui.sourcePref, now) == null) weather.remove(key)
        }
        ui.cities.forEach { city ->
            val disk = try {
                withContext(Dispatchers.IO) { WeatherCache.load(context, city.locationKey) }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
            val cached = chooseCityWeatherCache(weather[city.locationKey], disk, ui.sourcePref, now)
            if (cached == null) weather.remove(city.locationKey) else weather[city.locationKey] = cached
        }
        ui.selectedCity?.let { city -> ui.weather?.let { data ->
            val live = CachedWeather(data, data.fetchedAt ?: data.updateTime ?: now)
            usableCityWeatherCache(live, ui.sourcePref, now)?.let { weather[city.locationKey] = it }
        } }
        ui.cities.filter { cityWeatherNeedsRefresh(weather[it.locationKey], ui.sourcePref, now) }.forEach { city ->
            try {
                val result = withTimeoutOrNull(15_000) { WeatherRepository.fetchWeather(city, ui.sourcePref) }
                val fetchedAt = System.currentTimeMillis()
                val entry = result?.let { usableCityWeatherCache(CachedWeather(it, fetchedAt), ui.sourcePref, fetchedAt) }
                if (entry != null) {
                    weather[city.locationKey] = entry
                    withContext(Dispatchers.IO) { WeatherCache.save(context, city.locationKey, entry.data) }
                }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* neutral card */ }
        }
    }
    CompositionLocalProvider(LocalVistaGlowPhase provides clock, LocalVistaSoftGlow provides true) {
        LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索位置", Modifier.weight(1f)
                        .then(if (glass) Modifier.zhishengCompactPanel()
                            else Modifier.clip(RoundedCornerShape(28.dp)).background(ZhishengCard))
                        .clickable(onClick = onAdd).padding(horizontal = 18.dp, vertical = 12.dp), color = ZhishengTextSecondary)
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) "完成" else "管理") }
                }
            }
            val groups = listOf("当前定位" to ui.cities.filter { it.locationKey == located },
                "已添加城市" to ui.cities.filter { it.locationKey != located })
            groups.forEach { (label, cities) ->
                if (cities.isNotEmpty()) {
                    item(key = label) { Text(label, Modifier.padding(top = 12.dp, start = 8.dp),
                        color = ZhishengTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    items(cities, key = { it.locationKey }) { city ->
                        val data = weather[city.locationKey]?.data
                        val now = weatherPresentationTime()
                        val night = data?.let {
                            phaseAwareCondition(WeatherCondition.CLEAR, it, now) == WeatherCondition.CLEAR_NIGHT
                        } ?: false
                        val selected = city.locationKey == ui.selectedCity?.locationKey
                        val moving = active && city.locationKey in visibleCityKeys
                        Column {
                            CityAtmosphere(data, ui.prefs.ambience, night,
                                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(26.dp))
                                    .clickable { onSelect(city.locationKey) }, active = moving) {
                                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                                    ZhishengBg.copy(alpha = .25f), Color.Transparent, ZhishengBg.copy(alpha = .20f)))))
                                Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(city.displayName, color = ZhishengText, fontSize = 23.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val needsCoordinates = city.street.isNullOrBlank() &&
                                            (city.isPreciseLocation || ui.cities.any {
                                                it.locationKey != city.locationKey && it.name == city.name
                                            })
                                        val addressContext = if (needsCoordinates) {
                                            listOf(String.format(java.util.Locale.US, "%.4f, %.4f", city.latitude, city.longitude), city.affiliation)
                                                .filter(String::isNotBlank).joinToString(" · ")
                                        } else city.contextLabel
                                        Text(addressContext, color = ZhishengTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.weight(1f))
                                        Text(data?.current?.weatherText ?: data?.current?.condition?.label ?: "天气暂缺", color = ZhishengText)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text((Fmt.temp(data?.current?.temperature, ui.tempUnit) ?: "—") + "°", color = ZhishengText, fontSize = 38.sp, fontWeight = FontWeight.Light)
                                        Spacer(Modifier.weight(1f))
                                        val day = data?.todayDaily(now)
                                        Text("${Fmt.temp(day?.high, ui.tempUnit) ?: "—"}° / ${Fmt.temp(day?.low, ui.tempUnit) ?: "—"}°", color = ZhishengTextSecondary)
                                    }
                                }
                                if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                                    .width(20.dp).height(2.dp).background(ZhishengMint, RoundedCornerShape(2.dp)))
                            }
                            if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { onFavorite(city.locationKey) }) { Text(if (city.isFavorite) "取消收藏" else "收藏") }
                                TextButton(onClick = { onRemove(city.locationKey) }) { Text("删除", color = ZhishengOrange) }
                            }
                        }
                    }
                }
            }
            if (ui.cities.isEmpty()) item { Text("还没有保存的城市，搜索位置添加", color = ZhishengTextSecondary) }
        }
    }
}
