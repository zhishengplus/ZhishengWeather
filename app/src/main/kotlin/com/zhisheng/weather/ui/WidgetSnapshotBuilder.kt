package com.zhisheng.weather.ui

import android.content.Context
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.WidgetCache
import com.zhisheng.weather.data.WidgetDay
import com.zhisheng.weather.data.WidgetHour
import com.zhisheng.weather.data.WidgetLifeTip
import com.zhisheng.weather.data.WidgetSnapshot
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.widget.ZhishengWidgetProvider
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

// 小组件快照构建：从 WeatherData 组装 WidgetSnapshot 并落盘 + 刷新桌面。
// v0.0.4 从 WeatherViewModel 提取为共享逻辑——后台刷新 Worker 与主 App 抓取共用同一份实现。
object WidgetSnapshotBuilder {

    suspend fun markNoCity(context: Context) {
        WidgetCache.save(context, WidgetSnapshot())
        ZhishengWidgetProvider.refreshAll(context)
    }

    suspend fun markCityPending(context: Context, city: City) {
        WidgetCache.save(
            context,
            WidgetSnapshot(city = city.displayName, updateMillis = 0L),
        )
        ZhishengWidgetProvider.refreshAll(context)
    }

    suspend fun save(context: Context, city: City, data: WeatherData) {
        val unit = SettingsRepository.tempUnit.first()
        val windUnit = SettingsRepository.windUnit.first()
        val showLifeIndices = SettingsRepository.showIndices.first()
        val selectedLifeIndices = SettingsRepository.lifeIndexMetrics.first()
        fun t(v: Double?): Int? = v?.let {
            (if (unit == "f") it * 9.0 / 5.0 + 32.0 else it).roundToInt()
        }
        val nowMillis = System.currentTimeMillis()
        val today = data.todayDaily(nowMillis)
        val nowIdx = WeatherConsistency.currentHourIndex(data.hourly, nowMillis)
        val upcomingStart = WeatherConsistency.upcomingHourStartIndex(data.hourly, nowMillis)
        val hi = if (today?.high != null && today.low != null) maxOf(today.high, today.low) else today?.high
        val lo = if (today?.high != null && today.low != null) minOf(today.high, today.low) else today?.low
        WidgetCache.save(
            context,
            WidgetSnapshot(
                city = city.displayName,
                temp = t(data.current?.temperature),
                high = t(hi),
                low = t(lo),
                feelsLike = t(data.current?.feelsLike),
                humidity = data.current?.humidity?.roundToInt(),
                windText = Fmt.wind(data.current?.windSpeed, windUnit).orEmpty(),
                rainChance = (
                    nowIdx.takeIf { it >= 0 }?.let { data.hourly.getOrNull(it)?.precipProb }
                        ?: today?.precipProbability
                    )?.takeIf { it in 1..100 },
                text = data.current?.weatherText ?: data.current?.condition?.label.orEmpty(),
                conditionName = phaseAwareCondition(data.current?.condition, data, nowMillis)?.name.orEmpty(),
                aqi = data.aqi?.value,
                aqiLevel = data.aqi?.level.orEmpty(),
                aqiStandard = data.aqi?.standard.orEmpty(),
                updateMillis = data.updateTime ?: System.currentTimeMillis(),
                source = data.dataSource.orEmpty(),
                utcOffsetSeconds = data.utcOffsetSeconds,
                // 中号取前四项；大号用完整六点绘制腕表式温度轨迹。
                hours = data.hourly.drop(upcomingStart).take(6).map { h ->
                    WidgetHour(
                        label = Fmt.hour(h.timeMillis, data.utcOffsetSeconds),
                        temp = t(h.temperature),
                        conditionName = phaseAwareCondition(h.condition, data, h.timeMillis)?.name.orEmpty(),
                    )
                },
                lifeTips = widgetLifeTips(data, selectedLifeIndices, showLifeIndices),
                days = data.currentAndFutureDaily(nowMillis).take(7).mapIndexed { i, d ->
                    val dh = if (d.high != null && d.low != null) maxOf(d.high, d.low) else d.high
                    val dl = if (d.high != null && d.low != null) minOf(d.high, d.low) else d.low
                    WidgetDay(
                        label = if (i == 0) {
                            "今天 ${Fmt.dayOfMonth(d.dateMillis, data.utcOffsetSeconds)}"
                        } else {
                            "${Fmt.weekday(d.dateMillis, i, data.utcOffsetSeconds)} ${Fmt.dayOfMonth(d.dateMillis, data.utcOffsetSeconds)}"
                        },
                        high = t(dh),
                        low = t(dl),
                        conditionName = d.condition?.name.orEmpty(),
                    )
                },
            ),
        )
        ZhishengWidgetProvider.refreshAll(context)
    }
}

internal fun widgetLifeTips(
    data: WeatherData,
    selected: Set<LifeIndexMetric>,
    enabled: Boolean,
): List<WidgetLifeTip> {
    if (!enabled || selected.isEmpty()) return emptyList()

    val candidates = buildList {
        data.extraIndices.forEach { index ->
            val metric = LifeIndexMetric.fromEnglish(index.en)
            if (metric != null && metric !in selected) return@forEach
            if (metric == null && index.name.isBlank()) return@forEach
            val value = compactLifeValue(index.category)
            if (value.isNotBlank()) {
                add(WidgetLifeTip(metric?.cn ?: index.name.trim(), value))
            }
        }
        if (LifeIndexMetric.UV in selected && none { it.label == LifeIndexMetric.UV.cn }) {
            data.current?.uvIndex?.let { add(WidgetLifeTip(LifeIndexMetric.UV.cn, uvLevel(it))) }
        }
        if (LifeIndexMetric.SPORTS in selected && none { it.label == LifeIndexMetric.SPORTS.cn }) {
            data.sportsOk?.let { add(WidgetLifeTip(LifeIndexMetric.SPORTS.cn, if (it) "适宜" else "不适宜")) }
        }
        if (LifeIndexMetric.CAR_WASH in selected && none { it.label == LifeIndexMetric.CAR_WASH.cn }) {
            data.carWashOk?.let { add(WidgetLifeTip(LifeIndexMetric.CAR_WASH.cn, if (it) "适宜" else "不适宜")) }
        }
    }

    val priority = listOf("紫外线", "穿衣", "运动", "舒适度", "洗车", "感冒", "旅游")
    return candidates
        .distinctBy { it.label }
        .sortedBy { tip -> priority.indexOf(tip.label).let { if (it < 0) Int.MAX_VALUE else it } }
        .take(3)
}

private fun compactLifeValue(raw: String): String = raw
    .trim()
    .replace(Regex("\\s+"), "")
    .substringBefore('，')
    .substringBefore('。')
    .substringBefore('；')
    .take(8)

private fun uvLevel(value: Int): String = when {
    value <= 2 -> "低"
    value <= 5 -> "中等"
    value <= 7 -> "较强"
    value <= 10 -> "很强"
    else -> "极强"
}
