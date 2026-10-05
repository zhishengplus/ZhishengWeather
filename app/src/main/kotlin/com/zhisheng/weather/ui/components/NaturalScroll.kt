package com.zhisheng.weather.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/** Follow a shared visible anchor; include real item spacing without guessing unseen heights. */
internal suspend fun observeNaturalScroll(state: LazyListState, onScroll: (Float) -> Unit) {
    var previous = emptyList<Triple<Int, Int, Int>>()
    var distance = 0f
    snapshotFlow { state.layoutInfo.visibleItemsInfo.map { Triple(it.index, it.size, it.offset) } }
        .collect { visible ->
            val first = visible.firstOrNull() ?: return@collect
            val anchor = visible.firstOrNull { item -> previous.any { it.first == item.first } }
            distance = when {
                first.first == 0 -> (-first.third).coerceAtLeast(0).toFloat()
                anchor != null -> distance + previous.first { it.first == anchor.first }.third - anchor.third
                else -> maxOf(distance, state.layoutInfo.viewportSize.height.toFloat())
            }.coerceAtLeast(0f)
            previous = visible
            onScroll(distance)
        }
}
