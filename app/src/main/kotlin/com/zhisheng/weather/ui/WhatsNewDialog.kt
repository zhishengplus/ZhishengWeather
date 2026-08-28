package com.zhisheng.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary

internal const val WhatsNewVersion = "0.1.4"
internal const val WhatsNewPreferenceFile = "zhisheng_whats_new"
internal const val WhatsNewSeenKey = "last_seen_version"

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = 2

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.90f))
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelHeight = minOf(maxHeight - 24.dp, 700.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = panelHeight)
                    .background(ZhishengSurface, RectangleShape)
                    .border(1.dp, ZhishengCyan.copy(alpha = 0.54f), RectangleShape),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            "ZHISHENG WEATHER / 0.1.4",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.3.sp,
                        )
                        Text(
                            "更新说明",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier.size(52.dp).clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(pageCount) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index <= page) ZhishengCyan else ZhishengCardBorder),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        val enter = fadeIn(tween(140, easing = LinearOutSlowInEasing)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it * direction / 6 }
                        val exit = fadeOut(tween(110, easing = FastOutLinearInEasing)) +
                            slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it * direction / 8 }
                        enter togetherWith exit
                    },
                    label = "whats-new-page",
                    modifier = Modifier.weight(1f),
                ) { currentPage ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (currentPage) {
                            0 -> forecastPage()
                            else -> sourcePage()
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = page > 0, role = Role.Button) { page-- }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                    ) {
                        Text(
                            if (page > 0) "[ 上一步 ]" else "更新说明",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (page > 0) ZhishengTextSecondary else ZhishengTextTertiary,
                        )
                    }
                    Text(
                        "0${page + 1}/0$pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 1.sp,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Button) {
                                if (page < pageCount - 1) page++ else onClose()
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            if (page < pageCount - 1) "[ 下一步 ]" else "[ 进入 0.1.4 ]",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (page < pageCount - 1) ZhishengCyan else ZhishengMint,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.forecastPage() {
    item {
        UpdateTitle("01//", "往年同日回顾", "WEATHER ARCHIVE", ZhishengOrange)
    }
    item {
        EmphasisBlock(
            "今天冷不冷、雨大不大，不只能凭感觉：现在可以直接和往年同一天对照。",
        )
    }
    item { FeatureBlock("近5年 / 近10年", "一键切换回看范围，每一年都给出天气、高低温、降水和风速。") }
    item { FeatureBlock("温度航迹", "把各年高低温放在同一标尺上，一眼看出哪年更冷、更热。") }
    item { FeatureBlock("诚实展示", "数据不足时保留已成功年份并明确提示；闰年2月29日也会自动跳过非闰年。") }
    item { FeatureBlock("跟随当前设置", "历史页自动跟随当前城市、温度单位、风速单位和深浅主题。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sourcePage() {
    item { UpdateTitle("02//", "近两小时雷达", "RADAR ECHO", ZhishengCyan) }
    item { EmphasisBlock("看雨云从哪来、往哪走：优先显示最新一帧，其余回波在后台逐步补齐。", ZhishengCyan) }
    item { FeatureBlock("可播放时间轴", "按时间从早到新查看，可点选单帧，也可播放回波变化。") }
    item { FeatureBlock("当前城市视野", "城市切换后自动重定位，网络不稳时优先使用短时本机缓存。") }
    item { FeatureBlock("国内可用免费方案", "回波直接在应用内阅读；数据不可用时提供中央气象台官方雷达图备用入口。") }
    item { FeatureBlock("和原有功能联动", "往年同日与雷达回波合并为一个“时空观测”模块，共用一个序号、开关和排序位置；旧的自定义顺序会原样保留。") }
    item {
        Text(
            "这两项都是辅助判断；出行和防灾请同时关注当地气象部门预警。",
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengOrange,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun UpdateTitle(index: String, title: String, en: String, accent: androidx.compose.ui.graphics.Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Text(en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ZhishengText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmphasisBlock(text: String, accent: androidx.compose.ui.graphics.Color = ZhishengOrange) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZhishengCard)
            .border(1.dp, accent.copy(alpha = 0.58f), RectangleShape)
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ZhishengText, lineHeight = 21.sp)
    }
}

@Composable
private fun FeatureBlock(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("> $title", style = MaterialTheme.typography.titleSmall, color = ZhishengCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary, lineHeight = 20.sp)
    }
}
