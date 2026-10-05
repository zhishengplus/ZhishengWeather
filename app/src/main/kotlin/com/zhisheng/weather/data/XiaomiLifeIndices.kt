package com.zhisheng.weather.data

import com.zhisheng.weather.model.LifeIndexExtra

/** The public response observed on 2026-09-07 uses 0/1 for these two flags only. */
internal fun xiaomiSuitability(indices: XiaomiIndices?, type: String): Boolean? =
    when (indices?.indices?.firstOrNull { it.type.equals(type, ignoreCase = true) }?.value?.trim()) {
        "0" -> true
        "1" -> false
        else -> null
    }

/** Humidity, feelsLike and pressure belong to current conditions, not invented life-index grades. */
internal fun xiaomiLifeIndices(indices: XiaomiIndices?): List<LifeIndexExtra> =
    indices?.indices.orEmpty().mapNotNull { item ->
        if (!item.type.equals("uvIndex", ignoreCase = true)) return@mapNotNull null
        val uv = item.value?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
        val level = when {
            uv < 3 -> "弱"
            uv < 6 -> "中等"
            uv < 8 -> "强"
            uv < 11 -> "很强"
            else -> "极强"
        }
        LifeIndexExtra("紫外线", "UV", level)
    }.distinctBy { it.en }
