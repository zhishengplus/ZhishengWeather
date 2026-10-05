package com.zhisheng.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.zhishengPanel

@Composable
internal fun VistaSettingsCard(content: @Composable () -> Unit) {
    val palette = LocalZhishengPalette.current
    val glass = LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    Column(Modifier.fillMaxWidth().then(if (glass) Modifier.zhishengPanel()
        else Modifier.clip(RoundedCornerShape(20.dp)).background(palette.card))) {
        content()
    }
}

@Composable
internal fun VistaSettingToggle(
    label: String,
    hint: String,
    checked: Boolean,
    onInfo: (() -> Unit)?,
    onToggle: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch) { onToggle() }
        .heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingTitle(label, palette.text)
            if (hint.isNotBlank()) SettingDescription(hint, palette.textSecondary)
        }
        if (onInfo != null) Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onInfo),
            contentAlignment = Alignment.Center,
        ) {
            PhosphorIcon(R.drawable.ph_info, uiText("查看说明"), Modifier.size(19.dp), palette.textSecondary)
        }
        // The row owns the single switch action; this compact track is purely visual.
        val track by animateColorAsState(
            if (checked) palette.mint else lerp(palette.card, palette.textSecondary, 0.20f),
            tween(180), label = "settings-switch-track",
        )
        val position by animateDpAsState(if (checked) 21.dp else 3.dp, tween(180), label = "settings-switch-thumb")
        Box(Modifier.size(44.dp, 26.dp).clearAndSetSemantics { }.clip(CircleShape).background(track)) {
            Box(Modifier.offset(x = position, y = 3.dp).size(20.dp).clip(CircleShape)
                .background(if (checked) Color.White else palette.textSecondary))
        }
    }
}

@Composable
internal fun VistaSettingAction(label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val ink = when {
        !enabled -> palette.textSecondary
        label.contains("清除") || color == palette.red -> color
        else -> palette.text
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(settingsActionLabel(label), Modifier.weight(1f), color = ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.Medium)
        PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(17.dp), palette.textTertiary)
    }
}

@Composable
internal fun VistaSettingInfo(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    attention: Boolean,
    accent: Color? = null,
    forceSingleLine: Boolean = false,
) {
    val palette = LocalZhishengPalette.current
    val titleInk = when {
        attention -> palette.red
        accent != null -> accent
        else -> palette.text
    }
    val ink = when {
        attention -> palette.red
        accent != null -> accent.copy(alpha = 0.82f)
        else -> palette.textSecondary
    }
    val largeText = LocalDensity.current.fontScale > 1.2f
    BoxWithConstraints(Modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
        val stacked = !forceSingleLine && (largeText || maxWidth < 260.dp || value.length > 16 || '\n' in value || label.length > 12)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SettingTitle(label, titleInk)
                SettingDescription(value, ink)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(label, color = titleInk,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Medium)
            Text(value, Modifier.weight(1f), color = ink, textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                maxLines = if (forceSingleLine) 1 else Int.MAX_VALUE)
        }
            }
            if (onClick != null) PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(16.dp), palette.textTertiary)
        }
    }
}

@Composable
internal fun VistaSettingSource(
    pref: SourcePref,
    description: String,
    selected: Boolean,
    status: Pair<String, Boolean>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalZhishengPalette.current
    val active = selected && enabled
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SettingTitle(pref.cn, when {
                active -> palette.mint
                !enabled -> palette.textSecondary
                else -> palette.text
            })
            if (description.isNotBlank()) SettingDescription(description, palette.textSecondary)
        }
        if (active && status.second) {
            PhosphorIcon(R.drawable.ph_check, null, Modifier.size(21.dp), palette.mint)
        } else {
            Text(status.first, Modifier.widthIn(max = 84.dp),
                color = if (status.second) palette.textSecondary else palette.orange,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
        }
    }
}

@Composable
private fun SettingTitle(label: String, color: Color) {
    Text(label, color = color, fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp))
}

@Composable
private fun SettingDescription(value: String, color: Color) {
    Text(value, color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
}
