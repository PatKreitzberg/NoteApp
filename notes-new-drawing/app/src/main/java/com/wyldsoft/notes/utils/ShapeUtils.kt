package com.wyldsoft.notes.utils

import android.graphics.RectF
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape

fun calculateShapesBoundingBox(shapes: List<BaseShape>, padding: Float = 10f): RectF? {
    if (shapes.isEmpty()) return null
    var rect: RectF? = null
    for (shape in shapes) {
        val br = shape.boundingRect ?: continue
        if (rect == null) {
            rect = RectF(br)
        } else {
            rect.union(br)
        }
    }
    rect?.inset(-padding, -padding)
    return rect
}
