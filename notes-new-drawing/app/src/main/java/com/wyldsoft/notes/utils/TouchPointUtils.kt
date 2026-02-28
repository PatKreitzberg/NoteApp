package com.wyldsoft.notes.utils

import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import android.graphics.PointF
import com.wyldsoft.notes.viewport.ViewportManager

fun notePointsToSurfaceTouchPoints(
    touchPointList: TouchPointList,
    viewportManager: ViewportManager
): TouchPointList {
    val surfaceTouchPoints = TouchPointList()
    for (i in 0 until touchPointList.size()) {
        val notePoint = touchPointList.get(i)
        val surfacePoint = viewportManager.noteToSurfaceCoordinates(notePoint.x, notePoint.y)
        surfaceTouchPoints.add(
            TouchPoint(
                surfacePoint.x,
                surfacePoint.y,
                notePoint.pressure,
                notePoint.size,
                notePoint.timestamp
            )
        )
    }
    return surfaceTouchPoints
}

fun domainPointsToTouchPointList(
    points: List<PointF>,
    pressure: List<Float>
): TouchPointList {
    val touchPointList = TouchPointList()
    for (i in points.indices) {
        val point = points[i]
        val p = if (i < pressure.size) pressure[i] else 0.5f
        touchPointList.add(TouchPoint(point.x, point.y, p, 1f, System.currentTimeMillis()))
    }
    return touchPointList
}
