package com.zhisheng.weather.widget

internal enum class WidgetClockDestination { CLOCK_ENTRY, ALARMS }

/** No weather destination: an unavailable clock must report a clock failure. */
internal fun launchWidgetClock(attempt: (WidgetClockDestination) -> Boolean): Boolean =
    attempt(WidgetClockDestination.CLOCK_ENTRY) || attempt(WidgetClockDestination.ALARMS)
