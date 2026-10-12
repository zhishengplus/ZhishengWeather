package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel

/** Keep the current value in its setting row; open the alternatives only on demand. */
@Composable
internal fun SettingsChoiceRow(label: String, options: List<Pair<String, String>>, selected: String,
    hint: String? = null, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    VistaSettingInfo(label, options.firstOrNull { it.second == selected }?.first.orEmpty(),
        onClick = { expanded = true }, attention = false)
    if (expanded) WeatherOverlayDialog(onDismiss = { expanded = false }, maxWidth = 420.dp) { modifier ->
        SettingsChoicePanel(label, options, selected, hint, onDismiss = { expanded = false }, onSelect = {
            if (it != selected) onSelect(it)
            expanded = false
        }, modifier = modifier)
    }
}

/** Full-width choices avoid partial grid rows. The body scrolls; dismiss stays reachable. */
@Composable
internal fun SettingsChoicePanel(label: String, options: List<Pair<String, String>>, selected: String,
    hint: String? = null, onDismiss: () -> Unit, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    BoxWithConstraints(modifier.zhishengDialogPanel(glass = true)) {
        val availableHeight = maxHeight
        val compact = availableHeight < 260.dp
        if (availableHeight < 144.dp) {
            // A short safe area cannot fit a header, a 48dp option and a footer.
            // Give the list the full height; keep cancellation alongside it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(max = availableHeight).verticalScroll(rememberScrollState()).selectableGroup()) {
                    Text(label, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), color = palette.text,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
                    SettingsChoiceOptions(options, selected, true, onSelect)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 4.dp).heightIn(min = 48.dp)) {
                    Text("取消", Modifier.width((24 * LocalDensity.current.fontScale + 4).dp), textAlign = TextAlign.Center, color = palette.textSecondary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp))
                }
            }
            return@BoxWithConstraints
        }
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 2.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, Modifier.fillMaxWidth(), color = palette.text, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (compact) 12.sp else 18.sp,
                        lineHeight = if (compact) 16.sp else 25.sp))
                if (!compact && !hint.isNullOrBlank()) Text(hint, Modifier.fillMaxWidth(), color = palette.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
            }
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                SettingsChoiceOptions(options, selected, compact, onSelect)
            }
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 4.dp),
                horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("取消", Modifier.width(((if (compact) 24 else 28) * LocalDensity.current.fontScale + 4).dp), textAlign = TextAlign.Center, color = palette.textSecondary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = if (compact) 12.sp else 14.sp, lineHeight = if (compact) 16.sp else 20.sp))
                }
            }
        }
    }
}

@Composable
private fun SettingsChoiceOptions(options: List<Pair<String, String>>, selected: String, compact: Boolean,
    onSelect: (String) -> Unit) {
    val palette = LocalZhishengPalette.current
    options.forEach { (title, value) ->
        Row(Modifier.fillMaxWidth().selectable(value == selected, role = Role.RadioButton,
            onClick = { onSelect(value) }).heightIn(min = if (compact) 48.dp else 56.dp)
            .padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, Modifier.weight(1f), color = palette.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 13.sp else 15.sp,
                    lineHeight = if (compact) 18.sp else 22.sp))
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (value == selected) PhosphorIcon(R.drawable.ph_check, null, Modifier.size(19.dp), palette.mint)
            }
        }
    }
}
