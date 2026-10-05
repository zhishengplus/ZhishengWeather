package com.zhisheng.weather.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*

/* Hallmark · component: warning group · system: VISTA_DESIGN.md
 * One surface; every warning remains visible, with a full official notice on tap.
 * Native click indication and focus; immediate disclosure, no loading or blink.
 */
@Composable
internal fun VistaAlerts(alerts: List<AlertInfo>, modifier: Modifier = Modifier) {
    val visible = vistaVisibleAlerts(alerts)
    if (visible.isEmpty()) return
    var expandedKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Column(modifier.fillMaxWidth().padding(horizontal = LocalZhishengChrome.current.pagePadding, vertical = 4.dp).zhishengPanel()) {
        visible.forEachIndexed { index, alert ->
            // Length-prefixed fields avoid ambiguous concatenation. Reordering
            // or same-titled announcements must not move the expanded content.
            val identity = listOf(alert.title, alert.pubTime.orEmpty(), alert.type.orEmpty())
                .joinToString("") { "${it.length}:$it" }
            val expanded = identity in expandedKeys
            val heading = vistaAlertHeading(alert)
            val color = alertLevelColor(alert.severity)
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                color = ZhishengCardBorder.copy(alpha = 0.35f))
            Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "收起预警" else "查看完整预警") {
                    expandedKeys = if (expanded) expandedKeys - identity else expandedKeys + identity
                }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ph_warning), null, tint = color, modifier = Modifier.size(24.dp))
                Text(heading, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = ZhishengText, fontWeight = FontWeight.Medium)
                VistaDisclosureCaret(expanded)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heading != alert.title) Text(alert.title, style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengText, fontWeight = FontWeight.Medium)
                    alert.pubTime?.takeIf(String::isNotBlank)?.let {
                        Text("发布时间 ${formatAlertTime(it)}", style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextSecondary)
                    }
                    alert.detail?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp), color = ZhishengTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun VistaDisclosureCaret(expanded: Boolean) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "alert-caret")
    val stroke = ZhishengTextSecondary
    val fill = ZhishengSurface
    val rim = ZhishengCardBorder.copy(alpha = 0.55f)
    Box(
        Modifier.size(28.dp).background(fill, CircleShape).border(1.dp, rim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp).rotate(rotation)) {
            val padX = 1.6.dp.toPx()
            val top = 3.4.dp.toPx()
            val bottom = size.height - 3.2.dp.toPx()
            drawLine(stroke, Offset(padX, top), Offset(size.width / 2f, bottom), 1.7.dp.toPx(), StrokeCap.Round)
            drawLine(stroke, Offset(size.width - padX, top), Offset(size.width / 2f, bottom), 1.7.dp.toPx(), StrokeCap.Round)
        }
    }
}
