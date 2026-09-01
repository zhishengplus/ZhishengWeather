/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V5 */
/* Hallmark · component: searchable contributor ledger · genre: atmospheric · theme: existing Zhisheng terminal
 * states: default · focus · filtered · matched · empty
 * contrast: pass
 */
package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary

@Composable
fun ContributorsDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.82f))
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelMaxHeight = minOf(maxHeight - 24.dp, 620.dp)
            val columnCount = when {
                maxWidth >= 760.dp -> 4
                maxWidth >= 540.dp -> 3
                else -> 2
            }
            var query by remember { mutableStateOf("") }
            val visibleSections = remember(query) {
                val needle = query.trim()
                if (needle.isBlank()) {
                    CommunityContributorSections
                } else {
                    CommunityContributorSections.mapNotNull { section ->
                        val matches = section.contributors.filter { contributor ->
                            contributor.contains(needle, ignoreCase = true)
                        }
                        section.copy(contributors = matches).takeIf { matches.isNotEmpty() }
                    }
                }
            }
            val visibleCount = visibleSections.sumOf { it.contributors.size }
            val sequenceById = remember {
                CommunityContributors.withIndex().associate { (index, contributor) -> contributor to index + 1 }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = panelMaxHeight)
                    .background(ZhishengSurface, RectangleShape)
                    .border(1.dp, ZhishengCardBorder, RectangleShape),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            "COMMUNITY / ${CommunityContributors.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.4.sp,
                        )
                        Text(
                            "社区贡献者",
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
                Text(
                    "感谢每一次认真试用、具体反馈与真诚支持。名单按参与方式整理，也可以直接搜索自己的酷安 ID。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                )
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                ContributorSearchField(
                    query = query,
                    resultCount = visibleCount,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                )
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                LazyColumn(modifier = Modifier.weight(1f)) {
                    visibleSections.forEach { section ->
                        item(key = "section-${section.key}") {
                            ContributorSectionHeader(section)
                        }
                        items(
                            items = section.contributors.chunked(columnCount),
                            key = { row -> "${section.key}-${row.joinToString("|")}" },
                        ) { contributorRow ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                contributorRow.forEach { contributor ->
                                    ContributorCell(
                                        sequence = sequenceById.getValue(contributor),
                                        contributor = contributor,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(columnCount - contributorRow.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 1.dp,
                                color = ZhishengCardBorder.copy(alpha = 0.45f),
                            )
                        }
                    }
                    if (visibleSections.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "没有找到这个 ID",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ZhishengTextSecondary,
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "可以检查空格、符号或字母拼写",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZhishengTextTertiary,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "按有效反馈与社区支持整理",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "[ 关闭 ]",
                        style = MaterialTheme.typography.labelLarge,
                        color = ZhishengCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorSearchField(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ZhishengText),
        cursorBrush = SolidColor(ZhishengMint),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZhishengBg)
                    .border(1.dp, ZhishengCardBorder, RectangleShape)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(">", style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                    if (query.isBlank()) {
                        Text(
                            "输入酷安 ID 查找",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZhishengTextTertiary,
                        )
                    }
                    field()
                }
                Text(
                    if (query.isBlank()) "$resultCount 位" else "$resultCount 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengCyan,
                )
                if (query.isNotBlank()) {
                    Box(
                        modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.titleMedium, color = ZhishengTextSecondary)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun ContributorSectionHeader(section: ContributorSection) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ZhishengBg).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
            Text(
                section.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            section.contributors.size.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = ZhishengCyan,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ContributorCell(
    sequence: Int,
    contributor: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sequence.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengMint,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            contributor,
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
