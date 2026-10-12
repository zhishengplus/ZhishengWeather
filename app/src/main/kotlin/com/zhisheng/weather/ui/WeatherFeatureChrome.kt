package com.zhisheng.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.*

@Composable
internal fun WeatherFeatureHeader(title: String, location: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            PhosphorIcon(R.drawable.ph_arrow_left, "返回", Modifier.size(24.dp), ZhishengText)
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, color = ZhishengText)
            location?.takeIf(String::isNotBlank)?.let {
                Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            }
        }
    }
}

@Composable
internal fun WeatherFeatureNavigation(title: String, location: String?, onBack: () -> Unit,
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenHeightDp <= 360) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(.45f)) { WeatherFeatureHeader(title, null, onBack) }
            Box(Modifier.weight(.55f)) { WeatherFeatureTabs(labels, selected, onSelect) }
        }
    } else {
        WeatherFeatureHeader(title, location, onBack)
        WeatherFeatureTabs(labels, selected, onSelect)
    }
}

@Composable
internal fun WeatherFeatureTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .then(if (index == selected) Modifier.zhishengCompactPanel() else Modifier)
                .selectable(index == selected, role = Role.Tab) { onSelect(index) }
                .padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (index == selected) ZhishengText else ZhishengTextSecondary)
            }
        }
    }
}

@Composable
internal fun WeatherSourceDisclosure(title: String, description: String) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(title + if (expanded) " · 收起" else " · 数据说明", Modifier.fillMaxWidth()
            .clickable(role = Role.Button) { expanded = !expanded }.heightIn(min = 48.dp).padding(vertical = 14.dp),
            style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
        if (expanded) Text(description, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
    }
}
