package com.zhisheng.weather.ui

/** A stable tilt must enter standby even when the sensor reports no new angle. */
internal class StandbyTiltGate(
    private val scheduleEntry: (Long, () -> Unit) -> Unit,
    private val cancelEntry: () -> Unit,
    private val onLandscapeChanged: (Boolean) -> Unit,
) {
    private var landscape = false
    private var entryPending = false
    private var generation = 0L

    fun updateAngle(angle: Int) {
        if (angle in 65..115 || angle in 245..295) {
            if (!landscape && !entryPending) {
                entryPending = true
                val candidate = ++generation
                scheduleEntry(500L) {
                    if (entryPending && candidate == generation) {
                        entryPending = false
                        setLandscape(true)
                    }
                }
            }
        } else {
            cancelPendingEntry()
            // Unknown/flat and the hysteresis zone retain an existing landscape.
            if (angle in 0..40 || angle in 320..359) setLandscape(false)
        }
    }

    fun reset() {
        cancelPendingEntry()
        setLandscape(false)
    }

    private fun cancelPendingEntry() {
        if (entryPending) {
            entryPending = false
            generation++
            cancelEntry()
        }
    }

    private fun setLandscape(value: Boolean) {
        if (landscape != value) {
            landscape = value
            onLandscapeChanged(value)
        }
    }
}
