package com.zhisheng.weather.ui

import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.Nowcast

internal data class RadarPointForecast(
    val points: List<MinutePrecip>, val intervalMinutes: Int, val source: String,
    val publicationTime: Long?, val expired: Boolean, val endTime: Long?,
)

/** Reuse selected weather only; never send hidden requests to a personal service or fill a missing horizon. */
internal fun radarPointForecast(data: WeatherData?, now: Long): RadarPointForecast {
    val interval = data?.rainMeta?.intervalMinutes?.takeIf { it in 1..30 } ?: 1
    val published = data?.rainMeta?.updateTime
        ?: data?.moduleEvidence?.get("minutely")?.observationTime
        ?: data?.moduleEvidence?.get("minutely")?.fetchedAt
        ?: data?.fetchedAt?.takeIf { it > 0 }
    val expired = published != null && now - published !in -300_000L..1_800_000L
    val valid = if (expired) emptyList() else data?.rainMinutes.orEmpty()
        .filter { it.precip.isFinite() && it.precip >= 0f }
        .distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    val points = valid.filter { it.timeMillis + interval * 60_000L > now && it.timeMillis < now + 7_200_000L }
    val end = points.lastOrNull()?.timeMillis?.plus(interval * 60_000L)?.coerceAtMost(now + 7_200_000L)
    return RadarPointForecast(points, interval,
        (data?.rainMeta?.source ?: data?.blockSources?.get("minutely") ?: data?.dataSource).let { if (it.equals("OPEN-METEO", ignoreCase = true)) "Open-Meteo" else Nowcast.sourceLabel(it) },
        published, expired || (data?.rainMinutes?.isNotEmpty() == true && points.isEmpty()), end)
}
