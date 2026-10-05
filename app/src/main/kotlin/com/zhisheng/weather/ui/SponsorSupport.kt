package com.zhisheng.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhisheng.weather.ui.theme.zhishengScreen
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengDialogPanel

// 赞助排名（按赞助金额降序、同额按首次赞助时间升序）。
private val sponsorRanking = listOf(
    "披着牛皮的糖",
    "摆渡人",
    "Spark",
    "😀",
    "皮",
    "饕餮梼杌獬豸",
    "&",
    "追光者",
    "👁_👁",
    "Past-Present-Future",
    "雾岛笙南",
    "苍茫",
    "CK",
    "豆腐佬喔",
    "洛儿阿茶",
    "半夏",
    "大寶",
    "支持枳生的老板",
    "李勇",
    "bluebone",
    "深海罗非鱼",
    "轻轻飘过的落叶",
    "一月光含千世界",
    "JamoC",
    "野鸭子Evan",
    "白日梦想家",
    "YOGA",
    "Adrian",
    "Mrmuscle",
    "ayida999",
    "关",
    "∮",
    "ScottZzz",
    "*年",
    "凉粉",
    "浩然一切随缘",
    "十二",
    "2026",
    "偏我来时不逢春",
    "西伯利亚狼",
    "江湖暂过住",
    "亦世凡华",
    "llb",
    "屿光",
    "中未⬆️",
    "十三”画",
    "润物无声",
    "PickGear",
    "以后升级纯靠举报加经验太好玩了",
    "酷我哥哥",
    "李玄子",
    "果丽橙",
    "烟花易冷",
    "Ali",
    "如此而已",
)

// 固定席位：Sh4d0W 永远第 6 位，名次不随金额与时间变化。
private const val pinnedSponsor = "Sh4d0W"
private const val pinnedRank = 6

// 赞助榜单（顺序即名次）：仅第 1 名保留奖牌。
internal val SponsorBoard: List<String> = buildList {
    addAll(sponsorRanking.take(pinnedRank - 1))
    add(pinnedSponsor)
    addAll(sponsorRanking.drop(pinnedRank - 1))
}

@Composable
fun SponsorDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zhishengScreen()
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .zhishengDialogPanel(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            if (com.zhisheng.weather.ui.theme.isPhosphorVista) "赞赏支持" else "SUPPORT / 赞赏",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.4.sp,
                        )
                        Text(
                            "支持作者",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "枳生天气一直免费，也没有广告，以后也会是。如果你用着觉得舒服，想请作者喝杯奶茶，扫下面的码就好——不方便也没关系，能用得开心就是最大的支持。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )

                    Spacer(Modifier.size(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 300.dp)
                            .aspectRatio(1f)
                            .background(ZhishengBg)
                            .border(1.dp, ZhishengCyan.copy(alpha = 0.72f), RectangleShape)
                            .padding(7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.sponsor_wechat),
                            contentDescription = "微信赞赏码",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(Modifier.size(10.dp))
                    Text(
                        "赞赏时留个备注，你的名字会登上赞助榜单。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "愿你出门遇晴，代码一次通过，日子风调雨顺。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengMint,
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("[ 关闭 ]", style = MaterialTheme.typography.labelLarge, color = ZhishengCyan)
                }
            }
        }
    }
}
@Composable
fun SponsorBoardDialog(onClose: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val transition = if (reducedMotion) null else rememberInfiniteTransition(label = "sponsor-board")
    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val view = LocalView.current
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) entrance.snapTo(1f) else {
            // Native haptics respect the user's system setting; no vibrator permission or override flags.
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            entrance.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zhishengScreen()
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val settle = (entrance.value * 3f).coerceIn(0f, 1f)
                        alpha = .5f + settle * .5f
                        translationY = 16.dp.toPx() * (1 - settle)
                        val impact = (1f - entrance.value * 7f).coerceAtLeast(0f)
                        translationX = kotlin.math.sin(entrance.value * 70f) * 2.dp.toPx() * impact
                    }
                    .zhishengDialogPanel(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            if (com.zhisheng.weather.ui.theme.isPhosphorVista) "支持者" else "SPONSOR BOARD / 榜单",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.4.sp,
                        )
                        Text(
                            "赞助榜单 · ${SponsorBoard.size} 位",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    itemsIndexed(SponsorBoard) { index, name ->
                        SponsorBoardRow(
                            rank = index + 1,
                            name = name,
                            reducedMotion = reducedMotion,
                            transition = transition,
                        )
                        if (index != SponsorBoard.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = ZhishengCardBorder.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("[ 关闭 ]", style = MaterialTheme.typography.labelLarge, color = ZhishengCyan)
                }
            }
            if (!reducedMotion && entrance.value < 1f) {
                SponsorEntranceLight(entrance.value, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
internal fun SponsorBoardRow(
    rank: Int,
    name: String,
    reducedMotion: Boolean,
    transition: androidx.compose.animation.core.InfiniteTransition?,
) {
    // 前三名给一层极淡的席位底色，与各自名字的强调色同源；其余名次保持素净。
    val tierTint = when (rank) {
        1 -> Brush.horizontalGradient(
            listOf(ZhishengCyan.copy(alpha = 0.08f), ZhishengOrange.copy(alpha = 0.08f)),
        )
        2 -> Brush.horizontalGradient(
            listOf(ZhishengCyan.copy(alpha = 0.06f), ZhishengMint.copy(alpha = 0.06f)),
        )
        3 -> Brush.horizontalGradient(
            listOf(ZhishengOrange.copy(alpha = 0.07f), ZhishengOrange.copy(alpha = 0.03f)),
        )
        else -> null
    }
    Row(
        // All ranks share an unframed row; emphasis belongs to the crown and typography.
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (tierTint != null) {
                    Modifier.background(tierTint, RoundedCornerShape(9.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = if (tierTint != null) 9.dp else 4.dp, vertical = if (tierTint != null) 10.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sponsorBadge(rank)?.let { badge -> Text(
            text = badge,
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengTextSecondary,
        ) }
        when {
            rank == 1 -> {
                // 流光渐变：青→橙缓慢平移；减少动画时退化为静态双色渐变
                val flow = transition?.animateFloat(
                    initialValue = -300f,
                    targetValue = 300f,
                    animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
                    label = "sponsor-flow",
                )?.value ?: 0f
                val brush = Brush.linearGradient(
                    colors = listOf(ZhishengCyan, ZhishengOrange, ZhishengCyan),
                    start = Offset(if (reducedMotion) 0f else flow, 0f),
                    end = Offset(if (reducedMotion) 240f else flow + 240f, 40f),
                    tileMode = TileMode.Mirror,
                )
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = brush,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            rank == 2 -> {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(ZhishengCyan, ZhishengMint),
                        ),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            rank == 3 -> {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengOrange,
                    fontWeight = FontWeight.Bold,
                )
            }
            else -> {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall,
            color = when (rank) {
                1 -> ZhishengCyan
                2 -> ZhishengMint
                3 -> ZhishengOrange
                else -> ZhishengTextTertiary
            },
        )
    }
}

internal fun sponsorBadge(rank: Int): String? = if (rank == 1) "👑" else null

