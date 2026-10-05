package com.zhisheng.weather.widget

internal const val PIN_FEEDBACK_DELAY_MS = 15_000L
internal enum class WidgetPinState { IDLE, PREPARING, WAITING, UNCONFIRMED, ADDED, UNSUPPORTED, REJECTED, ERROR, BACKGROUND, CONFIG_FAILED }
internal enum class WidgetHomeGuide { XIAOMI, SAMSUNG, VIVO, OPPO, HONOR, HUAWEI, OTHER }

internal fun pinRequestResult(accepted: Boolean) = if (accepted) WidgetPinState.WAITING else WidgetPinState.REJECTED

// A launcher never reports a user's denial. A deadline is only a prompt to help,
// not a failure verdict; keep the token alive for a later success callback.
internal fun pinAwaitResult(elapsedMs: Long, completed: Boolean?) = when {
    completed == true -> WidgetPinState.ADDED
    completed == false -> WidgetPinState.CONFIG_FAILED
    elapsedMs >= PIN_FEEDBACK_DELAY_MS -> WidgetPinState.UNCONFIRMED
    else -> WidgetPinState.WAITING
}

internal fun widgetHomeGuide(manufacturer: String) = when (manufacturer.lowercase(java.util.Locale.ROOT)) {
    "xiaomi", "redmi", "poco" -> WidgetHomeGuide.XIAOMI
    "samsung" -> WidgetHomeGuide.SAMSUNG
    "vivo", "iqoo" -> WidgetHomeGuide.VIVO
    "oppo", "oneplus", "realme" -> WidgetHomeGuide.OPPO
    "honor" -> WidgetHomeGuide.HONOR
    "huawei" -> WidgetHomeGuide.HUAWEI
    else -> WidgetHomeGuide.OTHER
}

internal val WidgetHomeGuide.title: String get() = when (this) {
    WidgetHomeGuide.XIAOMI -> "小米 / 红米 / POCO"
    WidgetHomeGuide.SAMSUNG -> "三星"
    WidgetHomeGuide.VIVO -> "vivo / iQOO"
    WidgetHomeGuide.OPPO -> "OPPO / 一加 / realme"
    WidgetHomeGuide.HONOR -> "荣耀"
    WidgetHomeGuide.HUAWEI -> "华为"
    WidgetHomeGuide.OTHER -> "其他 / 第三方桌面"
}

internal val WidgetHomeGuide.steps: List<String> get() = when (this) {
    WidgetHomeGuide.XIAOMI -> listOf("长按桌面空白处，或双指捏合", "点“添加小组件”，找“枳生天气”", "选喜欢的款式，放到桌面空位")
    WidgetHomeGuide.SAMSUNG -> listOf("长按桌面空白处", "点“小组件”，找“枳生天气”", "选款式，点“添加”或拖到桌面")
    WidgetHomeGuide.VIVO -> listOf("长按桌面空白处", "点“组件”或“挂件”，找“枳生天气”", "长按喜欢的款式，拖到桌面空位")
    WidgetHomeGuide.OPPO -> listOf("长按桌面空白处，或双指捏合", "点“小组件”“插件”或“＋”", "找到“枳生天气”，拖到桌面空位")
    WidgetHomeGuide.HONOR -> listOf("在桌面双指捏合", "点“卡片”，滑到底部找“经典小工具”", "找到“枳生天气”，拖到桌面空位")
    WidgetHomeGuide.HUAWEI -> listOf("在桌面双指捏合", "点“窗口小工具”，找“枳生天气”", "长按喜欢的款式，拖到桌面空位")
    WidgetHomeGuide.OTHER -> listOf("长按桌面空白处", "点“小组件 / Widgets”，找“枳生天气”", "长按喜欢的款式，拖到桌面空位")
}
internal val WidgetHomeGuide.note: String get() = when (this) {
    WidgetHomeGuide.XIAOMI -> "没找到？再看看“安卓小组件”或“全部小组件”。"
    WidgetHomeGuide.VIVO -> "部分版本在“原子组件”中的安卓组件或应用挂件列表里。"
    WidgetHomeGuide.HONOR -> "旧版系统可直接从“窗口小工具”进入。"
    WidgetHomeGuide.HUAWEI -> "如果 App 在安卓兼容空间里运行，组件可能无法放到系统桌面，开权限也未必能解决。"
    WidgetHomeGuide.OTHER -> "如果当前桌面没有小组件入口，可换用支持小组件的桌面。"
    else -> "菜单名称可能略有不同，按手机上的提示操作就好。"
}
