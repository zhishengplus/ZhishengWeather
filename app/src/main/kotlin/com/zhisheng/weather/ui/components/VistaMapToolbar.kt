package com.zhisheng.weather.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*

@Composable
internal fun VistaMapToolbar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .zhishengPanel(containerColor = ZhishengSurface.copy(alpha = 0.96f))
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
        VistaMapTool(R.drawable.ph_arrow_left, "返回", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ZhishengText)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary, maxLines = 2)
        }
        if (!compact) actions()
        }
        if (compact) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, content = actions)
        }
    }
}

@Composable
internal fun VistaMapTool(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        PhosphorIcon(icon, description, Modifier.size(22.dp), ZhishengTextSecondary)
    }
}
