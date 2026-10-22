package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.*
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SourcePrioritySettings(primary: SourcePref, available: Set<SourcePref>, onSaving: (Boolean) -> Unit = {}) {
    val priority by SettingsRepository.sourcePriority.collectAsState(initial = emptyList())
    val p = LocalZhishengPalette.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun save(value: List<SourcePref>) {
        if (busy) return
        scope.launch {
            busy = true; error = false; onSaving(true)
            try { SettingsRepository.setSourcePriority(value) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = true }
            finally { busy = false; onSaving(false) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("缺失数据补全顺序", color = p.text, style = MaterialTheme.typography.titleSmall)
        Text("先用主源与模块设置，再按下列顺序补充缺失数据。已有数据保留，实际来源可在详情查看。",
            Modifier.padding(vertical = 8.dp), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (priority.isEmpty()) Text("未启用补全", color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
        priority.forEachIndexed { index, source ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}  ${source.cn}${if (source !in available) " · 暂不可用" else if (source == primary) " · 当前主源" else ""}",
                    Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy && index > 0, onClick = {
                    val next = priority.toMutableList(); next[index] = priority[index - 1]; next[index - 1] = source; save(next)
                }) { Text("上移", color = p.mint) }
                TextButton(enabled = !busy, onClick = { save(priority - source) }) { Text("移除", color = p.textSecondary) }
            }
        }
        TextButton(enabled = !busy, onClick = { expanded = !expanded }) { Text(if (expanded) "收起可用来源" else "添加补全天气源", color = p.mint) }
        if (expanded) available.filter { it != primary && it !in priority && it != SourcePref.AUTO && it != SourcePref.ZHISHENG }.forEach { source ->
            TextButton(enabled = !busy, onClick = { save(priority + source); expanded = false }) { Text("＋ ${source.cn}", color = p.text) }
        }
        if (error) Text("未能保存，请重试", color = p.red, style = MaterialTheme.typography.bodySmall)
    }
}
