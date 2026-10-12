package com.zhisheng.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.RadarSource
import com.zhisheng.weather.ui.theme.*

@Composable
private fun RadarDialogPanel(title: String, onDismiss: () -> Unit, modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalZhishengPalette.current
    Surface(modifier.zhishengDialogPanel(glass = true), color = Color.Transparent, contentColor = palette.text) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)) {
            Column {
                Text(title, Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp))
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = palette.mint, contentColor = palette.bg)) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
internal fun RadarInfoPanel(source: RadarSource, staleMetadata: Boolean, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, officialLink: @Composable () -> Unit = {}) {
    val palette = LocalZhishengPalette.current
    var details by remember(source) { mutableStateOf(false) }
    RadarDialogPanel("这张图怎么看", onDismiss, modifier) {
        Text(if (source == RadarSource.CAIYUN)
            "时间轴左侧是历史观测，右侧有数据时才显示未来外推；颜色含义以彩云原图为准。"
        else "蓝色是较弱回波，黄色到红色是较强回波。时间轴可以回看过去约两小时。")
        Text("放大方便辨认位置，不会提高回波精度。图上没有颜色，不等于地面没有降雨。", color = palette.textSecondary)
        Text("防灾以当地气象部门发布为准。", color = palette.textSecondary)
        if (staleMetadata) Text("网络不稳定，暂时使用上次保存的帧目录。", color = palette.orange)
        TextButton(onClick = { details = !details }) { Text(if (details) "收起数据说明" else "更多数据说明", color = palette.mint) }
        if (details) {
            Text(if (source == RadarSource.CAIYUN)
                "彩云区域拼图包含实测与外推，需 Token 单独开通雷达权限。图片带时效签名，仅短缓存几分钟，离线时不会长期保留旧图。"
            else "RainViewer 公开接口提供历史回波，不提供未来回波。页面读取覆盖掩膜；边界与缺覆盖区域仍应以当地气象部门信息为准。",
                color = palette.textSecondary)
            Text("底图使用 OpenFreeMap / OpenStreetMap，街道与地名随缩放显示，仅用于回波定位。", color = palette.textSecondary)
        }
        officialLink()
    }
}

@Composable
internal fun RadarSourcePanel(current: RadarSource, caiyunConfigured: Boolean, onSelect: (RadarSource) -> Unit,
    onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    RadarDialogPanel("雷达数据源", onDismiss, modifier) {
        RadarPanelChoice("RainViewer", "海外覆盖为主 · 历史回波 · 无需账号", current == RadarSource.RAINVIEWER, true) {
            onSelect(RadarSource.RAINVIEWER)
        }
        RadarPanelChoice("彩云天气", if (caiyunConfigured) "区域拼图 · 实测与外推" else "请先在设置中接入彩云服务",
            current == RadarSource.CAIYUN, caiyunConfigured) { onSelect(RadarSource.CAIYUN) }
        Text("国内未来区域图可打开中国天气网；本机彩云拼图需个人Token另行开通雷达权限。失败时保留你的选择。", color = palette.textSecondary)
    }
}

@Composable
private fun RadarPanelChoice(title: String, detail: String, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Surface(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 48.dp), shape = LocalZhishengChrome.current.compactShape,
        color = if (selected) palette.mint.copy(alpha = .14f) else palette.surface.copy(alpha = .3f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Medium, color = if (enabled) palette.text else palette.textTertiary)
                Text(detail, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
            }
            RadioButton(selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = palette.mint, unselectedColor = palette.textSecondary))
        }
    }
}
