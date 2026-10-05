package com.zhisheng.weather.ui

/** Consumer-facing names for shared legacy controls. Never rewrites weather values or source names. */
private val vistaLabels = mapOf(
        "遥测数据" to "天气详情",
        "实时遥测" to "当前天气详情",
        "遥测项目" to "显示哪些天气指标",
        "昨日复盘" to "昨天天气",
        "逐日预报" to "未来五天",
        "天气娘简报" to "天气娘播报",
        "数据轨道" to "逐日天气",
        "本夜较佳窗口" to "今晚适合拍摄的时段",
        "本夜较佳拍摄窗口" to "今晚适合拍摄的时段",
        "参考窗口" to "参考时段",
        "月面照明" to "月亮亮面比例",
        "日月" to "太阳与月亮",
        "气象中枢" to "天气时钟",
        "自动优选" to "自动选择",
        "小米公开接口" to "小米天气",
        "气象视界 · 天空" to "霞光与星空",
        "气象视界 · 海岸" to "海边天气",
        "气象视界 · 回看与雷达" to "历史天气与降雨雷达",
        "未来六小时趋势已就绪" to "未来六小时天气",
        "当前城市暂未返回逐日预报" to "暂时无法获取这座城市的预报",
        "模型趋势 · 非测站潮汐" to "预估潮位 · 非实测",
        "打开历史曲线与逐日记录" to "查看过去天气",
        "数据来源" to "天气来源",
        "横向滑动  →" to "左右滑动",
    )
private val releasePrefix = Regex("^\\[(新增|优化|重构|保留)\\]\\s*")
private val commandPrefix = Regex("^>\\s+(?=[\\u4e00-\\u9fff])")

internal fun vistaLabel(text: String): String {
    // Labels are called for every shared Text. Reuse the dictionary and compiled
    // patterns, and leave ordinary weather values on the allocation-free path.
    val prefixed = when {
        text.startsWith("[") -> text.replace(releasePrefix, "")
        text.startsWith(">") -> text.replace(commandPrefix, "")
        else -> text
    }
    // "[ ]" is a three-character progress marker, not a wrapped button label.
    val clean = if (prefixed.length >= 4 && prefixed.startsWith("[ ") && prefixed.endsWith(" ]")) {
        prefixed.substring(2, prefixed.length - 2)
    } else prefixed
    return vistaLabels[clean] ?: clean
}

/**
 * 经典终端的术语人话化：只做词典映射，不清理前缀与括号——
 * "> " 提示符和 "[ 数据 ]" 选中括号是经典终端的美术组成部分，必须保留。
 */
internal fun classicLabel(text: String): String = vistaLabels[text] ?: text
