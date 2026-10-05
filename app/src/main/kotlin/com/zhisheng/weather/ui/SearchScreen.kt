package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(onCityPicked: (City) -> Unit, onBack: () -> Unit) {
    val chrome = LocalZhishengChrome.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val leaveSearch = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onBack()
    }
    val pickCity: (City) -> Unit = { city ->
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onCityPicked(city)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var preciseAddress by rememberSaveable { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(query, preciseAddress, retry) {
        val term = query.trim()
        results = emptyList()
        searchFailed = false
        searching = term.isNotEmpty()
        if (term.isEmpty()) return@LaunchedEffect
        try {
            delay(350)
            results = withContext(Dispatchers.IO) {
                if (preciseAddress) CityRepository.searchPreciseAddress(term) else CityRepository.search(term)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            searchFailed = true
        } finally {
            searching = false
        }
    }

    Box(Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = chrome.contentMaxWidth)
                .fillMaxWidth().padding(horizontal = if (isPhosphorVista) chrome.pagePadding else 14.dp),
        ) {
        if (isPhosphorVista) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = leaveSearch) {
                    PhosphorIcon(R.drawable.ph_arrow_left, "返回", Modifier.size(24.dp), ZhishengText)
                }
                Text("添加城市", style = MaterialTheme.typography.headlineSmall, color = ZhishengText)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                placeholder = { Text(if (preciseAddress) "城市、街道或地点" else "城市、区县或拼音", color = ZhishengTextTertiary) },
                leadingIcon = { PhosphorIcon(R.drawable.ph_magnifying_glass, null, Modifier.size(22.dp), ZhishengTextSecondary) },
                trailingIcon = if (query.isNotEmpty()) ({
                    androidx.compose.material3.IconButton(onClick = { query = "" }) {
                        PhosphorIcon(R.drawable.ph_x, "清空搜索", Modifier.size(20.dp), ZhishengTextSecondary)
                    }
                }) else null,
                singleLine = true, shape = chrome.compactShape,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZhishengMint,
                    unfocusedBorderColor = ZhishengTextTertiary.copy(alpha = 0.35f),
                    focusedTextColor = ZhishengText, unfocusedTextColor = ZhishengText,
                    cursorColor = ZhishengMint,
                ),
            )
        } else {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(56.dp)
                    .then(if (isPhosphorVista) Modifier else Modifier.border(1.dp, ZhishengTextTertiary.copy(alpha = 0.30f), RectangleShape))
                    .clickable(role = Role.Button, onClickLabel = uiText("返回"), onClick = leaveSearch)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPhosphorVista) {
                    PhosphorIcon(R.drawable.ph_arrow_left, null, Modifier.size(22.dp), ZhishengOrange)
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = ZhishengOrange)
                }
                Spacer(Modifier.width(5.dp))
                Text(uiText("返回"), color = ZhishengText, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { value -> query = value },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        when {
                            preciseAddress && isPhosphorVista -> "输入小区、街道或地点"
                            preciseAddress -> "> 输入小区、街道或地点_"
                            isPhosphorVista -> "输入城市或区县"
                            else -> "> 输入城市、区县或拼音_"
                        },
                        color = ZhishengTextTertiary,
                    )
                },
                leadingIcon = if (isPhosphorVista) ({
                    PhosphorIcon(R.drawable.ph_magnifying_glass, null, Modifier.size(21.dp), ZhishengTextSecondary)
                }) else null,
                singleLine = true,
                shape = chrome.compactShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZhishengMint,
                    unfocusedBorderColor = ZhishengTextTertiary.copy(alpha = 0.38f),
                    focusedTextColor = ZhishengText,
                    unfocusedTextColor = ZhishengText,
                    cursorColor = ZhishengMint,
                ),
            )
        }
        }

        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("选择城市", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
            Spacer(Modifier.weight(1f))
            if (query.isNotBlank() && !searching && !searchFailed) {
                Text("${results.size} 个结果", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }

        Row(Modifier.fillMaxWidth().selectableGroup().padding(bottom = 12.dp)) {
            SearchScopeButton("城市", !preciseAddress, Modifier.weight(1f)) {
                if (preciseAddress) {
                    preciseAddress = false
                    results = emptyList()
                }
            }
            SearchScopeButton("精确地址", preciseAddress, Modifier.weight(1f)) {
                if (!preciseAddress) {
                    preciseAddress = true
                    results = emptyList()
                }
            }
        }

        when {
            searchFailed -> Column {
                SearchMessage("暂时无法连接城市服务，请检查网络后重试", ZhishengTextSecondary)
                androidx.compose.material3.TextButton(onClick = { retry++ }) { Text("重试", color = ZhishengMint) }
            }
            query.isBlank() -> Column(Modifier.padding(horizontal = 8.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPhosphorVista) PhosphorIcon(R.drawable.ph_map_trifold, null, Modifier.size(40.dp), ZhishengTextTertiary)
                Text(if (preciseAddress) "搜索街道或地点" else "查看其他城市的天气",
                    style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                Text(if (preciseAddress) "输入城市和街道、小区或地标，使用更贴近你所在位置的预报。" else "输入城市、区县或拼音。选择结果即可添加，并查看当地天气。",
                    style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
            }
            searching -> SearchMessage(if (isPhosphorVista) "正在查找城市…" else "> 正在查找城市…", ZhishengMint)
            query.isNotBlank() && results.isEmpty() -> SearchMessage(
                if (preciseAddress) "没有找到「$query」，请补充城市或区县名" else "没有找到「$query」",
                ZhishengTextSecondary,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(count = results.size, key = { index -> "${results[index].locationKey}-$index" }) { index ->
                    SearchResultRow(results[index], index, pickCity)
                }
            }
        }
        }
    }
}

@Composable
private fun SearchResultRow(city: City, index: Int, onCityPicked: (City) -> Unit) {
    val primary = if (city.isPreciseLocation) city.street ?: city.name else city.name
    val secondary = if (city.isPreciseLocation) {
        listOf(city.name, city.affiliation).filter(String::isNotBlank).distinct().joinToString(" · ")
    } else city.affiliation
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (isPhosphorVista) Modifier else Modifier.zhishengCompactPanel())
                .clickable { onCityPicked(city) }
                .heightIn(min = 76.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPhosphorVista) {
                PhosphorIcon(if (city.isPreciseLocation) R.drawable.ph_crosshair else R.drawable.ph_map_pin,
                    null, Modifier.size(24.dp), ZhishengTextSecondary)
            } else {
                Text("%02d".format(index + 1), style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(primary, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                Text(
                    "${if (city.isPreciseLocation) "精确坐标" else "城市坐标"} · %.4f, %.4f".format(city.latitude, city.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
            if (isPhosphorVista) {
                PhosphorIcon(R.drawable.ph_plus, "添加$primary", Modifier.size(21.dp), ZhishengMint)
            } else {
                Text(if (city.isPreciseLocation) "[精确]" else "[+]", style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
            }
        }
        if (isPhosphorVista) HorizontalDivider(color = ZhishengTextTertiary.copy(alpha = 0.20f), thickness = 1.dp)
    }
}

@Composable
private fun SearchMessage(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, Modifier.padding(top = 24.dp, start = 4.dp), style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun SearchScopeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (!isPhosphorVista && selected) "[ $label ]" else label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ZhishengMint else ZhishengTextSecondary,
        )
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(if (selected) 2.dp else 1.dp).background(if (selected) ZhishengMint else ZhishengTextTertiary.copy(alpha = 0.22f)))
    }
}
