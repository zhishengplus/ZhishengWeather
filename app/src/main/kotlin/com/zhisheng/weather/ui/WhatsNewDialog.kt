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

internal const val WhatsNewVersion = "0.1.5-beta3"
internal const val WhatsNewPreferenceFile = "zhisheng_whats_new"
internal const val WhatsNewSeenKey = "last_seen_version"

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = 5

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
                            "ZHISHENG WEATHER / WHAT'S NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.3.sp,
                        )
                        Text(
                            "这次更新了什么",
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
                            0 -> homeUpgradePage()
                            1 -> forecastPage()
                            2 -> sourcePage()
                            3 -> typhoonPage()
                            else -> qualityPage()
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
                            if (page < pageCount - 1) "[ 下一步 ]" else "[ 开始使用 ]",
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
    item { UpdateTitle("02//", "天气回看更完整", "WEATHER HISTORY", ZhishengOrange) }
    item { EmphasisBlock("昨天、过去一周与往年同日，放在同一条时间线上。") }
    item { FeatureBlock("[新增] 时空观测", "天气回看与雷达合并为一个首页模块，共用一个开关和排序位置。") }
    item { FeatureBlock("[新增] 往年同日", "今日预报与往年同日平均分开标注，直接给出偏暖、偏凉或接近往年的结论。") }
    item { FeatureBlock("[新增] 温度带", "各年高温、低温与今日预报落在同一张图上，历史冷热一眼可见。") }
    item { FeatureBlock("[新增] 逐日探索", "支持前后切换日期，回看范围可在近 5 年与近 10 年之间切换。") }
    item { FeatureBlock("[新增] 过去7天", "新增过去 7 个完整自然日的回看，天气、温度、降水和最大风速逐日列出；今天未结束的数据不混入统计。") }
    item { FeatureBlock("[优化] 有效记录", "没有可比温度的年份自动略过，不再用整屏“未知／--”占位。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeUpgradePage() {
    item { UpdateTitle("01//", "主页天气更好读", "HOME", ZhishengMint) }
    item { EmphasisBlock("主页预报更紧凑，提醒和天气娘都更完整。", ZhishengMint) }
    item { FeatureBlock("[优化] 先看五天", "逐日预报改为紧凑的 5 天卡片，日期、天气、温度区间和降水概率一眼可读。") }
    item { FeatureBlock("[新增] 近15日天气", "新增独立的 15 日预报页，白天天气、高低温走势、夜间天气、降水和风况在同一数据轨道中查看。") }
    item { FeatureBlock("[新增] 天气娘播报", "播报与预警分开判断，细分高温、寒冷、大风、空气质量、能见度、紫外线、湿度和不同时段。") }
    item { FeatureBlock("[新增] 天气娘表情", "表情随晴天、雨雪、冷热、大风、夜晚和预警切换，相同天气也有不同说法。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sourcePage() {
    item { UpdateTitle("03//", "雷达回归地图", "RADAR ECHO", ZhishengCyan) }
    item { EmphasisBlock("道路、地名和回波占满屏幕，控制条只留底部一小块。", ZhishengCyan) }
    item { FeatureBlock("[优化] 正常地图手势", "单指拖动、双指缩放、双击放大，操作与常见地图一致。") }
    item { FeatureBlock("[优化] 平滑回放", "相邻回波短促过渡衔接；拖动时间轴立即停播，便于逐帧查看。") }
    item { FeatureBlock("[新增] 覆盖范围清楚", "明确区分“没有明显回波”与“当地暂缺雷达覆盖”，不把无数据当成无降水。") }
    item { FeatureBlock("[优化] 天地图底图", "雷达与台风共用天地图，中文注记随缩放覆盖城市、区县、乡镇和道路；回波可在 RainViewer 与彩云拼图之间切换，并保留中央气象台官方雷达备用入口。") }
    item {
        Text(
            "这两项都是辅助判断；出行和防灾请同时关注当地气象部门预警。",
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengOrange,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.typhoonPage() {
    item { UpdateTitle("04//", "台风路径观测", "TYPHOON TRACK", ZhishengOrange) }
    item { EmphasisBlock("实况路径、中心强度、风圈和官方预报，放在同一张可缩放的图里。") }
    item { FeatureBlock("[新增] 国内权威资料", "路径来自浙江省水利厅台风路径实时发布系统，来源、发布时间与缓存状态均有标注。") }
    item { FeatureBlock("[新增] 路径可以细看", "支持双指缩放、拖动和双击复位；实况为实线，预报为虚线，强度变化按节点颜色区分。") }
    item { FeatureBlock("[新增] 多机构预报", "默认展示中央气象台预报，可切换查看其他机构路径，分歧不混成一条线。") }
    item { FeatureBlock("[新增] 断网仍有交代", "连接失败时显示最近一次有效缓存并标注时间；路径过期提示“资料较旧”，不冒充实时。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.qualityPage() {
    item { UpdateTitle("05//", "数据与桌面组件", "DATA / WIDGET", ZhishengMint) }
    item { EmphasisBlock("每一项天气数据都按来源和单位重新核对；来源没有返回的内容保持为空，不拿别的数值冒充。", ZhishengMint) }
    item { FeatureBlock("[优化] 和风天气", "逐项核对当前套餐可用的逐时、逐日、分钟降水、空气质量、预警和生活指数；套餐不含的项目自动隐藏。") }
    item { FeatureBlock("[优化] 彩云天气", "补齐逐时体感、湿度、气压、能见度、云量、风向、空气质量和生活指数；按服务端更新时间和当地时区显示，各字段按单位分别换算。") }
    item { FeatureBlock("[新增] 显示语言", "设置新增显示语言，可切换至日本語界面。") }
    item { FeatureBlock("[新增] 组件底色", "桌面组件新增全透明、玻璃、不透明三档：全透明融入壁纸，玻璃保留原有效果，不透明在复杂壁纸上更清楚。") }
    item { FeatureBlock("[新增] 横屏样式", "横屏待机界面新增“气象中枢”样式，日照轨迹、天气趋势与沉浸光感一同展示。") }
    item { FeatureBlock("[更新] 社区贡献者", "感谢名单更新：参与试用、反馈和建议的伙伴已达 ${CommunityContributors.size} 位，完整名单在设置中查看。") }
    item { FeatureBlock("[修复] 冷启动", "修正部分三星、真我设备横着冷启动时误进城市选择的问题，原有启动动画保留。") }
    item {
        Text(
            "以上是本次更新的全部内容。",
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengTextTertiary,
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
