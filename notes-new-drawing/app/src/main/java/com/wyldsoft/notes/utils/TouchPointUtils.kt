package com.wyldsoft.notes.utils

import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import android.graphics.PointF
import com.wyldsoft.notes.viewport.ViewportManager

fun transformTouchPointList(
    touchPointList: TouchPointList,
    transformXY: (Float, Float) -> PointF
): TouchPointList {
    val result = TouchPointList()
    for (i in 0 until touchPointList.size()) {
        val tp = touchPointList.get(i)
        val transformed = transformXY(tp.x, tp.y)
        val newTp = TouchPoint(transformed.x, transformed.y, tp.pressure, tp.size, tp.tiltX, tp.tiltY, tp.timestamp)
        result.add(newTp)
    }
    return result
}

fun notePointsToSurfaceTouchPoints(
    touchPointList: TouchPointList,
    viewportManager: ViewportManager
): TouchPointList {
    return transformTouchPointList(touchPointList) { x, y ->
        viewportManager.noteToSurfaceCoordinates(x, y)
    }
}

fun surfacePointsToNoteTouchPoints(
    touchPointList: TouchPointList,
    viewportManager: ViewportManager
): TouchPointList {
    return transformTouchPointList(touchPointList) { x, y ->
        viewportManager.surfaceToNoteCoordinates(x, y)
    }
}

data class ExtractedTouchData(
    val points: List<PointF>,
    val pressures: List<Float>,
    val timestamps: List<Long>,
    val tiltXValues: List<Int> = emptyList(),
    val tiltYValues: List<Int> = emptyList()
)

fun extractTouchData(touchPointList: TouchPointList): ExtractedTouchData {
    val points = mutableListOf<PointF>()
    val pressures = mutableListOf<Float>()
    val timestamps = mutableListOf<Long>()
    val tiltXValues = mutableListOf<Int>()
    val tiltYValues = mutableListOf<Int>()
    for (i in 0 until touchPointList.size()) {
        val tp = touchPointList.get(i)
        points.add(PointF(tp.x, tp.y))
        pressures.add(tp.pressure)
        timestamps.add(tp.timestamp)
        tiltXValues.add(tp.tiltX)
        tiltYValues.add(tp.tiltY)
    }
    return ExtractedTouchData(points, pressures, timestamps, tiltXValues, tiltYValues)
}

fun touchPointListToPoints(touchPointList: TouchPointList): List<PointF> {
    val points = mutableListOf<PointF>()
    for (i in 0 until touchPointList.size()) {
        val tp = touchPointList.get(i)
        points.add(PointF(tp.x, tp.y))
    }
    return points
}

fun domainPointsToTouchPointList(
    points: List<PointF>,
    pressure: List<Float>,
    tiltX: List<Int> = emptyList(),
    tiltY: List<Int> = emptyList(),
    strokeWidth: Float = 500f
): TouchPointList {
    val touchPointList = TouchPointList()
    for (i in points.indices) {
        val point = points[i]
        val p = if (i < pressure.size) pressure[i] else 0.5f
        val tx = if (i < tiltX.size) tiltX[i] else 0
        val ty = if (i < tiltY.size) tiltY[i] else 0
        touchPointList.add(TouchPoint(point.x, point.y, p, strokeWidth, tx, ty, System.currentTimeMillis()))
    }
    return touchPointList
}
