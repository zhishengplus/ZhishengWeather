@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengPanel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A source-independent route: a weather provider's empty list says nothing about active storms. */
@Composable
internal fun TyphoonEntryCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val vista = isPhosphorVista
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    Column(
        modifier.fillMaxWidth()
            .padding(horizontal = chrome.pagePadding)
            .clip(chrome.panelShape)
            .zhishengPanel()
            .clickable(role = Role.Button, onClickLabel = "打开台风路径", onClick = onClick)
            .padding(chrome.panelPadding),
        verticalArrangement = Arrangement.spacedBy(if (vista) 14.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TyphoonEntrySymbol(Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "台风追踪",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!vista) Text("TYPHOON", style = MaterialTheme.typography.labelSmall, color = palette.orange)
            }
        }
        Text("西北太平洋 · 南海", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("实况路径", "预报走势", "风圈范围").forEach { label ->
                Text(label, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            }
        }
        HorizontalDivider(color = palette.cardBorder.copy(alpha = if (vista) 0.4f else 0.7f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "打开台风路径",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = palette.cyan,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(20.dp), palette.cyan)
        }
    }
}

/** A clear eye and layered spiral bands, readable in both the card and drawer. */
@Composable
private fun TyphoonEntrySymbol(modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
            val band = Path().apply {
                fun edge(index: Int, outer: Boolean): Offset {
                    val t = index / 96f
                    val theta = (230f * (1 - t) - 90f) * PI / 180
                    val width = 2.5f * (1 - t) + 12f * sin(PI * t).toFloat()
                    val radius = 8.5f + 37f * t + width * if (outer) .5f else -.5f
                    return Offset(50f + radius * cos(theta).toFloat(), 50f + radius * sin(theta).toFloat())
                }
                for (index in 0..96) {
                    val point = edge(index, false)
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                for (index in 96 downTo 0) { val point = edge(index, true); lineTo(point.x, point.y) }
                close()
            }
            repeat(4) { arm ->
                rotate(arm * 90f, Offset(50f, 50f)) {
                    drawPath(band, Brush.linearGradient(
                        listOf(Color(0xFF348FEA), Color(0xFF75C5F8), Color(0xFFA7DEFC)),
                        Offset(45f, 50f), Offset(62f, 2f)))
                }
            }
        }
    }
}
