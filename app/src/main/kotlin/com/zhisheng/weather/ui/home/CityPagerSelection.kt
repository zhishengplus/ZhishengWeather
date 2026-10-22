package com.zhisheng.weather.ui.home

/** Keeps delayed persistence acknowledgements from pulling a rapidly swiped pager backwards. */
internal class CityPagerSelection {
    private val pending = mutableListOf<String>()
    private var observed: String? = null

    fun request(key: String, selected: String?): Boolean {
        if (key == (pending.lastOrNull() ?: selected)) return false
        pending += key
        return true
    }

    /** true means a genuinely external selection which the pager must follow. */
    fun observe(selected: String?, keys: List<String>): Boolean {
        pending.removeAll { it !in keys }
        if (observed == selected) return false
        observed = selected
        val acknowledged = pending.indexOf(selected)
        if (acknowledged >= 0) {
            repeat(acknowledged + 1) { pending.removeAt(0) }
            return false
        }
        pending.clear()
        return selected != null && selected in keys
    }
}

internal fun cityIndicatorWindow(count: Int, position: Float): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val start = (position.toInt() - 3).coerceIn(0, (count - 7).coerceAtLeast(0))
    return start until minOf(start + 7, count)
}
