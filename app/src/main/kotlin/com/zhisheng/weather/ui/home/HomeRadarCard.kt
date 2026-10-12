package com.zhisheng.weather.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun HomeRadarCard(city: City?, zoneId: String, state: AtlasRadarPreviewState?,
    accepted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
    geographyOverride: List<List<PreviewWorldPoint>>? = null) {
    val context = LocalContext.current
    var geography by remember { mutableStateOf<List<List<PreviewWorldPoint>>>(emptyList()) }
    val tile = state?.tile
    LaunchedEffect(tile?.bitmap != null, geographyOverride) {
        if (tile?.bitmap != null && geographyOverride == null) {
            geography = try { RadarPreviewGeography.load(context.applicationContext) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { emptyList() }
        }
    }
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看降水地图与未来两小时", onClick = onClick)
        .padding(horizontal = 16.dp,vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("降水地图", Modifier.fillMaxWidth(), color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                Text("附近回波 · 未来2小时", Modifier.fillMaxWidth(), color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(20.dp), ZhishengTextSecondary)
        }
        if (content != null) content()
        else if (accepted && city != null && tile?.bitmap != null) {
            HomeRadarMap(city,tile,geographyOverride ?: geography)
            Text("RainViewer · ${tile.time?.let { sceneStamp(it,zoneId) } ?: "时间未记录"} · 历史回波",
                Modifier.fillMaxWidth(), color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(if (tile.message in listOf("上次更新","缓存回波")) "缓存预览 · 无回波不代表无雨" else "单帧区域预览 · 无回波不代表无雨",
                Modifier.fillMaxWidth(), color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PhosphorIcon(R.drawable.ph_broadcast,null,Modifier.size(34.dp),ZhishengTextSecondary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        city == null -> "选好地点，再看附近降水"
                        !accepted -> "查看降水变化与来源说明"
                        state?.source == RadarSource.CAIYUN -> "打开查看彩云区域回波"
                        state?.source == null -> "正在读取回波来源"
                        else -> tile?.message ?: "正在加载区域回波"
                    },Modifier.fillMaxWidth(),color = ZhishengText,style = MaterialTheme.typography.bodyMedium)
                    Text("打开地图查看完整范围",Modifier.fillMaxWidth(),color = ZhishengTextSecondary,style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
