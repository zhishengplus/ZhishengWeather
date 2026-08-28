package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary

@Composable
internal fun FeaturePageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = ZhishengText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ZhishengOrange)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextSecondary,
                letterSpacing = 2.sp,
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun FeatureSectionTitle(index: Int, title: String, en: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d//".format(index),
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengOrange,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "  $title  ",
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengText,
        )
        Text(
            en,
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextSecondary,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
internal fun TerminalPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(ZhishengSurface, RectangleShape)
            .border(1.dp, ZhishengCardBorder, RectangleShape),
    ) { content() }
}
