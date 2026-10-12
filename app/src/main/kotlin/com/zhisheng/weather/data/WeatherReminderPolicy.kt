package com.zhisheng.weather.data

import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.cityDate
import com.zhisheng.weather.model.cityZone
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class WeatherReminder(val key: String, val title: String, val text: String, val expiresAt: Long)
internal enum class ReminderCheckState {
    READY, SENT, WAITING, LATE, NO_LOCATION, NO_DATA, CONFIG_CHANGED, STALE_DATA,
    NO_FORECAST, NO_TIMEZONE, NO_RAIN, RAIN_COOLDOWN, NOTIFICATIONS_BLOCKED, FAILED,
}
internal data class ReminderCheck(val kind: String, val state: ReminderCheckState, val detail: String,
    val nextAt: Long? = null, val reminder: WeatherReminder? = null)
internal data class ReminderCheckResult(val checks: List<ReminderCheck>, val checkedAt: Long)
internal data class ReminderPanelStatus(val cityName: String?, val utcOffsetSeconds: Int?,
    val result: ReminderCheckResult, val lastCheckedAt: Long? = null, val quietNotifications: Boolean = false,
    val backgroundRestricted: Boolean = false)

internal fun enabledReminderKinds(settings: AutomationSettings): List<String> = buildList {
    if (settings.morningEnabled) add("morning")
    if (settings.eveningEnabled) add("evening")
    if (settings.rainEnabled) add("rain")
}

internal fun reminderChecksWithState(settings: AutomationSettings, now: Long,
    state: ReminderCheckState, detail: String) = ReminderCheckResult(
        enabledReminderKinds(settings).map { ReminderCheck(it, state, detail) }, now)

/** Daily identity is its local date; a clock rollback must not discard a sent day. */
internal fun validReminderReceipts(receipts: Map<String, Long>, now: Long): Map<String, Long> =
    receipts.filter { (key, sentAt) ->
        sentAt > 0L && sentAt >= now - 7 * 24 * 3_600_000L &&
            (Regex(":(morning|evening):\\d{4}-\\d{2}-\\d{2}$").containsMatchIn(key) || sentAt <= now + 300_000L)
    }

/** A planned notification is successful only after both posting and receipt recording. */
internal fun finishReminderDelivery(result: ReminderCheckResult, sentKeys: Set<String>,
    notificationsAllowed: Boolean): ReminderCheckResult = result.copy(checks = result.checks.map { check ->
    when {
        check.reminder?.key in sentKeys -> check.copy(state = ReminderCheckState.SENT,
            detail = "已发送到通知栏", reminder = null)
        check.state == ReminderCheckState.READY -> check.copy(
            state = if (notificationsAllowed) ReminderCheckState.FAILED else ReminderCheckState.NOTIFICATIONS_BLOCKED,
            detail = if (notificationsAllowed) "这次发送未完成，点击立即检查重试" else "通知被系统关闭，请先开启天气通知",
            reminder = null)
        else -> check
    }
})

/** Planning and the visible status share eligibility decisions, without notification side effects. */
internal fun evaluateWeatherReminders(cityKey: String?, weather: WeatherData?, settings: AutomationSettings,
    receipts: Map<String, Long>, now: Long): ReminderCheckResult {
    if (cityKey == null) return reminderChecksWithState(settings, now, ReminderCheckState.NO_LOCATION, "先选择一个天气地点")
    if (weather == null || weather.error != null) return reminderChecksWithState(settings, now,
        ReminderCheckState.NO_DATA, "还没有可用预报，点击立即检查重试")
    val results = mutableListOf<ReminderCheck>()
    fun daily(enabled: Boolean, scheduled: Int, type: String, tomorrow: Boolean) {
        if (!enabled) return
        if (weather.utcOffsetSeconds == null || weather.utcOffsetSeconds !in -64_800..64_800) {
            results += ReminderCheck(type, ReminderCheckState.NO_TIMEZONE, "地点时区尚未就绪，更新天气后安排检查")
            return
        }
        val zone = cityZone(weather.utcOffsetSeconds)
        val day = cityDate(now, weather.utcOffsetSeconds)
        val today = day.atTime(scheduled / 60, scheduled % 60).atZone(zone)
        // Daily identity survives time edits: a successful receipt always owns today.
        if ("$cityKey:$type:$day" in receipts) {
            results += ReminderCheck(type, ReminderCheckState.SENT, "今天已发送到通知栏",
                today.plusDays(1).toInstant().toEpochMilli())
            return
        }
        val previous = today.minusDays(1)
        val appointment = if (today.toInstant().toEpochMilli() <= now) today else previous
        val due = appointment.toInstant().toEpochMilli()
        val key = "$cityKey:$type:${appointment.toLocalDate()}"
        val nextAt = if (today.toInstant().toEpochMilli() > now) today.toInstant().toEpochMilli()
            else today.plusDays(1).toInstant().toEpochMilli()
        if (key in receipts) {
            val alreadyToday = appointment.toLocalDate() == day
            results += ReminderCheck(type, if (alreadyToday) ReminderCheckState.SENT else ReminderCheckState.WAITING,
                if (alreadyToday) "今天已发送到通知栏" else "等待下次提醒时间", nextAt)
            return
        }
        if (now - due !in 0 until 90 * 60_000L) {
            val lateToday = today.toInstant().toEpochMilli() <= now
            results += ReminderCheck(type, if (lateToday) ReminderCheckState.LATE else ReminderCheckState.WAITING,
                if (lateToday) "今天的检查时段已过，等待下次提醒" else "等待提醒时间，到时检查预报", nextAt)
            return
        }
        val forecastDay = if (tomorrow) appointment.toLocalDate().plusDays(1) else appointment.toLocalDate()
        val forecast = weather.daily.firstOrNull { cityDate(it.dateMillis, weather.utcOffsetSeconds) == forecastDay }
        val low = forecast?.low?.takeIf(Double::isFinite)
        val high = forecast?.high?.takeIf(Double::isFinite)
        if (forecast == null || low == null || high == null) {
            results += ReminderCheck(type, ReminderCheckState.NO_FORECAST,
                if (tomorrow) "缺少对应日期的明日预报，更新后重试" else "缺少对应日期的今日预报，更新后重试", nextAt)
            return
        }
        // A daily brief depends on its own forecast, not an unrelated live observation.
        val evidence = weather.moduleEvidence["forecast"]
        val retrieved = evidence?.fetchedAt ?: weather.fetchedAt
        val published = evidence?.observationTime ?: retrieved
        if (retrieved == null || now - retrieved !in -300_000..24 * 3_600_000L ||
            published == null || now - published !in -300_000..24 * 3_600_000L) {
            results += ReminderCheck(type, ReminderCheckState.STALE_DATA, "每日预报已过期或时间未知，更新后重试", nextAt)
            return
        }
        val condition = forecast.weatherText?.takeIf(String::isNotBlank) ?: forecast.condition?.label ?: "天气现象暂无数据"
        val title = when (forecastDay) {
            day -> "今日天气"
            day.plusDays(1) -> "明日天气"
            else -> "${forecastDay.monthValue}月${forecastDay.dayOfMonth}日天气"
        }
        val reminder = WeatherReminder(key, title, "$condition · ${low.roundToInt()}–${high.roundToInt()}°C" +
            (forecast.precipProbability?.takeIf { it in 0..100 }?.let { " · 降水概率 $it%" } ?: ""), now + 6 * 3_600_000L)
        results += ReminderCheck(type, ReminderCheckState.READY, "已到提醒时间，预报可用", nextAt, reminder)
    }
    daily(settings.morningEnabled, settings.morningMinute, "morning", false)
    daily(settings.eveningEnabled, settings.eveningMinute, "evening", true)
    if (settings.rainEnabled) {
        val last = receipts["$cityKey:rain"]
        val meta = weather.rainMeta
        val stamp = meta?.updateTime
        val fetched = weather.moduleEvidence["minutely"]?.fetchedAt ?: weather.fetchedAt
        val check = when {
            last != null && now - last in -300_000 until 4 * 3_600_000L ->
                ReminderCheck("rain", ReminderCheckState.RAIN_COOLDOWN, "近期已发送，同一地点4小时内不重复提醒", last + 4 * 3_600_000L)
            meta == null || weather.rainMinutes.isEmpty() || meta.intervalMinutes !in 1..60 ->
                ReminderCheck("rain", ReminderCheckState.NO_FORECAST, "当前来源未返回可用短时降水数据，更新后再检查")
            stamp == null || now - stamp !in -300_000..30 * 60_000L || fetched == null ||
                now - fetched !in -300_000..3_600_000L ->
                ReminderCheck("rain", ReminderCheckState.STALE_DATA, "短时降水数据已过期或时间未知，更新后重试")
            else -> {
                val upcoming = weather.rainMinutes.filter { it.timeMillis <= now + 30 * 60_000L &&
                    it.timeMillis + meta.intervalMinutes * 60_000L > now && it.precip.isFinite() && it.precip >= 0f }
                val sample = upcoming.filter { it.precip > .1f }.minByOrNull { it.timeMillis }
                if (upcoming.isEmpty()) ReminderCheck("rain", ReminderCheckState.NO_FORECAST, "短时预报未覆盖接下来的时段，更新后重试")
                else if (sample == null) ReminderCheck("rain", ReminderCheckState.NO_RAIN, "本次有效短时预报未显示明显降水，继续检查")
                else {
                    val minutes = ceil((sample.timeMillis - now).coerceAtLeast(0) / 60_000.0).toInt()
                    val label = when (sample.phase) {
                        com.zhisheng.weather.model.PrecipitationPhase.SNOW -> "降雪"
                        com.zhisheng.weather.model.PrecipitationPhase.MIXED -> "雨夹雪"
                        com.zhisheng.weather.model.PrecipitationPhase.FREEZING_RAIN,
                        com.zhisheng.weather.model.PrecipitationPhase.FREEZING_DRIZZLE -> "冻雨"
                        else -> "降水"
                    }
                    ReminderCheck("rain", ReminderCheckState.READY, "预报接下来有$label",
                        reminder = WeatherReminder("$cityKey:rain", "短时${label}提醒",
                            "${if (minutes == 0) "当前时段" else "未来约${minutes}分钟"}预报有$label · ${meta.source} ${meta.intervalMinutes}分钟粒度；以实际天气为准",
                            now + 60 * 60_000L))
                }
            }
        }
        results += check
    }
    return ReminderCheckResult(results, now)
}

internal fun planWeatherReminders(cityKey: String, weather: WeatherData, settings: AutomationSettings,
    receipts: Map<String, Long>, now: Long): List<WeatherReminder> =
    evaluateWeatherReminders(cityKey, weather, settings, receipts, now).checks.mapNotNull { it.reminder }
