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
    item { UpdateTitle("02//", "过去的天气也能查", "WEATHER HISTORY", ZhishengOrange) }
    item { EmphasisBlock("昨天、过去 7 日和往年同日，各自说明日期与数据含义。") }
    item { FeatureBlock("[新增] 过去7日", "按完整自然日列出天气、最高最低温、降水和最大风速；今天还没结束，不会提前混进统计。") }
    item { FeatureBlock("[新增] 往年同日", "每条历史记录都标出具体年份，再与今天的预报并排比较，偏暖还是偏凉一眼就能看懂。") }
    item { FeatureBlock("[新增] 温度对比", "各年的最高温、最低温和今天的预报落在同一条温度带上，不用在一排数字里来回找。") }
    item { FeatureBlock("[新增] 前后查日期", "可以逐日向前、向后查看，也可以在近 5 年与近 10 年之间切换。") }
    item { FeatureBlock("[优化] 只显示有效记录", "缺少可比温度的年份会自动略过，不再用满屏“未知／--”占位置。") }
    item { FeatureBlock("[整理] 时空观测", "天气回看与雷达仍放在同一个首页模块里，共用开关和排序位置。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeUpgradePage() {
    item { UpdateTitle("01//", "主页先说重点", "HOME", ZhishengMint) }
    item { EmphasisBlock("先看接下来几小时和未来五天；需要更远的天气，再进入 15 日预报。", ZhishengMint) }
    item { FeatureBlock("[调整] 逐时预报", "“现在”与本小时预报紧挨着显示，温度、降水概率和风速重新对齐；主页最多保留 24 个时间格，不再横着划很久。") }
    item { FeatureBlock("[调整] 五日预报", "主页恢复紧凑的 5 天布局，日期、天气文字、降水概率和高低温放在固定位置，扫一眼就够。") }
    item { FeatureBlock("[新增] 近15日天气", "独立页面补上昨天并压暗显示，往后可看天气、温度走势、降水和风况；天气娘会在下方用更自然的话总结变化。") }
    item { FeatureBlock("[新增] 天气娘简报", "天气娘会按当前天气、时段和风险挑一句最值得看的提醒，不再和预警重复；不想显示形象时，也可以在设置里换成纯文字 Tips。") }
    item { FeatureBlock("[新增] 表情与说法", "晴雨、冷热、大风、夜晚和预警都有对应表情，同类天气也准备了多种自然说法，尽量少让你反复看到同一句。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sourcePage() {
    item { UpdateTitle("03//", "雷达可以直接上手", "RADAR ECHO", ZhishengCyan) }
    item { EmphasisBlock("把地图留给地图，把播放控制收在底部。", ZhishengCyan) }
    item { FeatureBlock("[调整] 地图手势", "单指拖动、双指缩放、双击放大，操作方式与常见地图一致，可以直接查看周边雨带。") }
    item { FeatureBlock("[调整] 回波播放", "相邻画面平滑衔接；拖动时间轴会立即停播，方便停在某一帧细看。") }
    item { FeatureBlock("[新增] 多个回波入口", "可以在可用的回波源之间切换，也保留中央气象台官方雷达入口，某一路暂时不可用时还有选择。") }
    item { FeatureBlock("[说明] 无雨不等于无数据", "“附近没有明显回波”和“当地暂缺雷达覆盖”会分开提示，不会把缺少资料说成没有降水。") }
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
    item { UpdateTitle("04//", "台风路径来了", "TYPHOON TRACK", ZhishengOrange) }
    item { EmphasisBlock("实况走到哪里、强度怎样、接下来可能往哪走，都放在一张图里。") }
    item { FeatureBlock("[新增] 国内公开资料", "路径来自浙江省水利厅台风路径实时发布系统，页面会写明来源、发布时间和当前缓存状态。") }
    item { FeatureBlock("[新增] 路径与风圈", "地图支持拖动和双指缩放。实况、预报使用不同线型，节点颜色表示强度，风圈范围也会随位置绘出。") }
    item { FeatureBlock("[新增] 多机构预报", "默认查看中央气象台预报，也可以切换其他机构；各家的判断分别画线，不会混成一条。") }
    item { FeatureBlock("[说明] 资料是否新鲜", "暂时连不上时会显示最近一次有效资料并标出时间；资料较旧会直接提醒，不会当作实时路径。") }
}

private fun androidx.compose.foundation.lazy.LazyListScope.qualityPage() {
    item { UpdateTitle("05//", "日常使用更顺手", "DATA / SETTINGS", ZhishengMint) }
    item { EmphasisBlock("来源返回什么就显示什么，缺少的项目宁可留空，也不用别的数值代替。", ZhishengMint) }
    item { FeatureBlock("[核对] 天气数据", "重新核对和风、彩云、小米与 Open-Meteo 的温度、风速、降水、气压、能见度和空气质量；时间按城市当地时区显示。") }
    item { FeatureBlock("[整理] 设置", "设置页重新分组，常用选项更容易找到；新增日本語界面，并保留天气娘、纯文字 Tips 等显示选择。") }
    item { FeatureBlock("[新增] 横屏气象中枢", "横屏待机默认使用新的“气象中枢”，也可以换回经典样式；横屏里能打开完整设置，也能一键回到竖屏。") }
    item { FeatureBlock("[新增] 组件底色", "桌面组件有全透明、玻璃和不透明三档。壁纸简单时可以更轻，背景复杂时也能保持清楚。") }
    item { FeatureBlock("[新增] 城市收藏", "在城市列表点亮星标，收藏城市就会排在前面；每组城市仍保持原来的顺序。") }
    item { FeatureBlock("[新增] 更新提醒", "应用启动后会在后台检查一次版本。发现新版只在设置的“检查更新”旁显示红点，不弹窗，也不会自动下载。") }
    item { FeatureBlock("[修复] 常见问题", "修正部分三星、真我设备横向冷启动误进城市选择，以及夜间图标、预警颜色、短时降水和大屏排版等问题。") }
    item { FeatureBlock("[更新] 社区贡献者", "贡献者名单已收录 ${CommunityContributors.size} 位伙伴，完整名单可以在设置中查看。") }
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
