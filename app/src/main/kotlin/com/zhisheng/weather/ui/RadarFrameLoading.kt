package com.zhisheng.weather.ui

import com.zhisheng.weather.model.RadarFrame

internal enum class RadarImageState { LOADING, READY, FAILED }

/** Map idle only describes tiles; a transparent ImageSource placeholder is never a loaded frame. */
internal fun radarFrameReady(frame: RadarFrame?, imageState: RadarImageState?, tilesReady: Boolean): Boolean =
    if (frame?.isImageFrame == true) imageState == RadarImageState.READY else tilesReady

internal fun radarFrameOpacity(frame: RadarFrame, selectedTime: Long, imageState: RadarImageState?): Float =
    if (frame.isImageFrame) {
        if (frame.timeMillis == selectedTime && imageState == RadarImageState.READY) .82f else 0f
    } else if (frame.timeMillis == selectedTime) .82f else .001f
