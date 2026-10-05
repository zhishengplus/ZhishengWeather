package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.theme.*
import com.zhisheng.weather.data.HomeSurfaceStyle

internal fun settingsActionLabel(label: String): String = label.trimStart('>', '⌖', ' ')

@Composable
internal fun VistaSettingsChoices(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, singleRow: Boolean = false) {
    if (options.isEmpty()) return
    val palette = LocalZhishengPalette.current
    val glass = LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val longest = options.maxOf { it.first.length }.coerceAtMost(10)
        val minimum = maxOf(72f, 24f + longest * 13f * fontScale)
        val columns = if (singleRow) options.size else ((maxWidth.value - 8f) / minimum).toInt().coerceIn(1, options.size)
        Column(Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(16.dp))
            .background(if (glass) {
                if (palette.isLight) Color(0xFF345269).copy(alpha = .055f) else Color.White.copy(alpha = .045f)
            }
                else lerp(palette.card, palette.textSecondary, if (palette.isLight) 0.07f else 0.10f))
            .padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (label, value) ->
                        val selected = value == current
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) {
                                if (glass) Color.White.copy(alpha = if (palette.isLight) .46f else .12f)
                                else lerp(palette.card, palette.mint, if (palette.isLight) 0.04f else 0.10f)
                            } else Color.Transparent)
                            .selectable(selected, role = Role.RadioButton) { onPick(value) }
                            .padding(horizontal = if (singleRow) 3.dp else 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(label, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) palette.mint else palette.textSecondary)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
