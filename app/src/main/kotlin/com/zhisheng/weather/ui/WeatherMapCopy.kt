package com.zhisheng.weather.ui

import com.zhisheng.weather.model.RadarCoverageState
import com.zhisheng.weather.model.RadarSource

internal fun vistaRadarCoverageLine(source: RadarSource, coverage: RadarCoverageState): String =
    if (source == RadarSource.CAIYUN) "全国降雨图"
    else when (coverage) {
        RadarCoverageState.AVAILABLE -> "这附近看得到降雨"
        RadarCoverageState.OUTSIDE -> "这附近暂时没有雷达"
        RadarCoverageState.UNKNOWN -> "正在确认这附近有没有雷达"
    }

internal fun vistaRadarKindLine(viewingFuture: Boolean): String =
    if (viewingFuture) "未来一两小时的估计" else "刚才到现在的实况"

internal fun vistaRadarLimitLine(source: RadarSource, futureUnlocked: Boolean): String? = when {
    futureUnlocked -> null
    source == RadarSource.RAINVIEWER -> "现在只能回看过去两小时"
    else -> "接下来的降雨图还没送到"
}

internal fun vistaTyphoonStatus(warningLabel: String?, stale: Boolean, active: Boolean, switching: Boolean): String = when {
    switching -> "正在换到这条台风"
    warningLabel != null && stale -> "$warningLabel · 资料有点旧"
    warningLabel != null -> warningLabel
    stale -> "资料有点旧"
    active -> "正在追踪"
    else -> "已经结束"
}

internal fun vistaStormLabel(id: String, name: String): String =
    name.trim().ifBlank { id }

internal fun vistaRadarMapStatus(
    baseStyleApplied: Boolean,
    staleMetadata: Boolean,
    tileError: Boolean,
    radarTilesReady: Boolean,
    mapReady: Boolean,
): String? = when {
    !baseStyleApplied -> "正在打开地图"
    staleMetadata -> "网不太稳，先看着上次的图"
    tileError -> "图有点慢，正在再试一次"
    !radarTilesReady && mapReady -> "正在叠上降雨"
    else -> null
}

internal fun vistaRadarFallbackNotice(raw: String): String = when {
    "未配置彩云" in raw -> "彩云还没接上，先用公开降雨图"
    "权限未开通" in raw -> "彩云这张图还没开通，先用公开降雨图"
    "服务不可用" in raw -> "彩云暂时连不上，先用公开降雨图"
    "未返回可用" in raw -> "彩云这张图还没送来，先用公开降雨图"
    else -> raw.substringBefore(" · ").ifBlank { raw }
}

internal fun vistaRadarErrorTitle(raw: String): String = when (raw) {
    "先在主页选择一座城市" -> "先回主页选一座城市"
    "雷达数据连接失败" -> "暂时连不上降雨图"
    else -> raw
}
