package com.zhisheng.weather.ui.badge

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.badge.*

@Composable
internal fun BadgeCollectionDialog(state: BadgeUiState, filter: String?, gridState: LazyGridState,
    onFilter: (String?) -> Unit, onClose: () -> Unit, onInspect: (BadgeGrant) -> Unit,
    onFeature: (String) -> Unit, onMore: () -> Unit) {
    val context = LocalContext.current
    val firstLight = remember { runCatching { context.assets.open("badges/preview-front.webp").use {
        BitmapFactory.decodeStream(it)?.asImageBitmap()
    } }.getOrNull() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.zhisheng.weather.ui.HighRefreshDialogContent()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text("返回收藏柜") }
                    Text("全部徽章", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                }
                Text("每一枚，都珍藏", style = MaterialTheme.typography.headlineSmall)
                Text("已缓存 ${state.collection.size} 枚 · 精选只占三个展位", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    (listOf<String?>(null) + state.collection.map { it.badgeId }.distinct()).map { id -> id to (state.collection.firstOrNull { it.badgeId == id }?.let(::badgeDisplayName) ?: "全部") }.forEach { (id, label) ->
                        FilterChip(selected = filter == id, enabled = !state.busy, onClick = { onFilter(id) }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), state = gridState, modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(state.collection.filter { filter == null || it.badgeId == filter }, key = { it.grantId }) { grant ->
                        OutlinedCard(onClick = { onInspect(grant) }) {
                            Column(Modifier.padding(12.dp)) {
                                if (grant.badgeId == FIRST_LIGHT_BADGE && firstLight != null)
                                    Image(firstLight, "${grant.edition} ${badgeSerialText(grant.serial)}", Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
                                else Box(Modifier.fillMaxWidth().aspectRatio(1f)) { Text(if (grant.protocol == 2 || grant.badgeId in setOf("clear-sky-crown","together-with-you")) "专属模型 · 点击按需下载" else "专属模型待提供", Modifier.padding(8.dp)) }
                                Text(badgeDisplayName(grant), style = MaterialTheme.typography.titleSmall)
                                Text(badgeSerialText(grant.serial), style = MaterialTheme.typography.bodySmall)
                                TextButton(enabled = !state.busy, onClick = { onFeature(grant.grantId) }) {
                                    Text(if (grant.grantId in state.featuredGrantIds) "取消精选" else "加入精选")
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth()) {
                            if (state.collection.none { filter == null || it.badgeId == filter }) Text("这个系列尚无已验证收藏。草稿与示例不代表已拥有。")
                            if (state.nextCursor != null) OutlinedButton(onClick = onMore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Text("离线显示上次已验证的收藏；收回状态在联网同步后生效。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
