package com.zhisheng.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.manualWeatherPlace
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.PlaceRegion
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel

@Composable
internal fun SearchPlaceTools(state: SearchUiState, onAreaChanged: (String, String) -> Unit, onPick: (City) -> Unit) {
    val p = LocalZhishengPalette.current
    var areaDialog by rememberSaveable { mutableStateOf(0) }
    var coordinates by rememberSaveable { mutableStateOf(false) }
    if (state.region == PlaceRegion.DOMESTIC) Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = { areaDialog = 1 }, modifier = Modifier.weight(1f)) { Text(state.province.ifBlank { "先选省份 ▾" }, color = p.mint) }
        TextButton(onClick = { areaDialog = 2 }, enabled = state.province.isNotBlank(), modifier = Modifier.weight(1f)) {
            Text(state.municipality.ifBlank { "再选城市 ▾" }, color = p.mint)
        }
    }
    TextButton(onClick = { coordinates = true }, modifier = Modifier.fillMaxWidth()) { Text("用经纬度添加自定义地点", color = p.mint) }
    if (areaDialog != 0) WeatherOverlayDialog(onDismiss = { areaDialog = 0 }, maxWidth = 420.dp) { modifier ->
        Column(modifier.heightIn(max = 520.dp).zhishengDialogPanel(glass = true).padding(16.dp)) {
            Text(if (areaDialog == 1) "选择省份" else "选择城市", color = p.text, style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f, fill = false)) {
                item { Text(if (areaDialog == 1) "全部省份" else "全省 / 全地区", Modifier.fillMaxWidth().clickable {
                    onAreaChanged(if (areaDialog == 1) "" else state.province, ""); areaDialog = 0
                }.padding(vertical = 16.dp), color = p.mint) }
                val choices = if (areaDialog == 1) state.provinces else state.municipalities
                items(choices.size) { index -> Text(choices[index], Modifier.fillMaxWidth().clickable {
                    if (areaDialog == 1) onAreaChanged(choices[index], "") else onAreaChanged(state.province, choices[index])
                    areaDialog = 0
                }.padding(vertical = 16.dp), color = p.text) }
            }
            TextButton(onClick = { areaDialog = 0 }) { Text("返回", color = p.textSecondary) }
        }
    }
    if (coordinates) ManualPlaceDialog(onDismiss = { coordinates = false }, onPick = { coordinates = false; onPick(it) })
}

@Composable
internal fun ManualPlaceDialog(onDismiss: () -> Unit, onPick: (City) -> Unit) {
    WeatherOverlayDialog(onDismiss, maxWidth = 460.dp) { modifier ->
        ManualPlacePanel(onDismiss, onPick, modifier)
    }
}

@Composable
internal fun ManualPlacePanel(onDismiss: () -> Unit, onPick: (City) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalZhishengPalette.current
    var name by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("WGS84") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val city = manualWeatherPlace(name, lat, lon, system)
    Column(modifier.zhishengDialogPanel(glass = true).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("自定义天气地点", color = p.text, style = MaterialTheme.typography.titleLarge)
        Text("无需定位权限。输入地图提供的坐标，并选择对应坐标系；天气精细程度仍取决于天气源。", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("地点名称") }, singleLine = true)
        OutlinedTextField(lat, { lat = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("纬度（−90 至 90）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
        OutlinedTextField(lon, { lon = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("经度（−180 至 180）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
        VistaSettingsChoices(listOf("GPS / WGS84" to "WGS84", "高德 / GCJ02" to "GCJ02", "百度 / BD09" to "BD09"), system, { system = it })
        if (attempted && city == null) Text("请填写地点名和有效的经纬度，使用小数点，勿填写度分秒。", color = p.red, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDismiss) { Text("取消", color = p.textSecondary) }
            Button(onClick = { attempted = true; city?.let(onPick) }) { Text("添加地点") }
        }
    }
}
