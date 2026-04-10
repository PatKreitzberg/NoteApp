package com.wyldsoft.notes.utils

import com.onyx.android.sdk.data.note.TouchPoint

/**
 * Returns a copy of this TouchPoint with x and y replaced by [x] and [y].
 * All other fields (pressure, tiltX, tiltY, timestamp) are copied as-is.
 */
fun TouchPoint.copyWith(x: Float, y: Float): TouchPoint {
    val dst = TouchPoint()
    dst.x = x
    dst.y = y
    dst.pressure = this.pressure
    dst.tiltX = this.tiltX
    dst.tiltY = this.tiltY
    dst.timestamp = this.timestamp
    return dst
}
