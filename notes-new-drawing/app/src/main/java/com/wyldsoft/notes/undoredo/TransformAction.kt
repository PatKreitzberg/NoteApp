package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.utils.copyWith

/**
 * Records a completed selection stretch or rotate operation.
 * Stores a deep copy of touch point lists before and after the transform so
 * undo/redo can restore either state without re-computing the transform math.
 */
class TransformAction(
    private val shapeIds: List<String>,
    private val originalPoints: Map<String, TouchPointList>,
    private val newPoints: Map<String, TouchPointList>,
    private val pipeline: DrawingPipeline,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "TransformAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${shapeIds.size} shapes")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            shape.touchPointList = copyTouchPointList(originalPoints[shapeId] ?: continue)
            shape.boundingRect = null
            shape.originRect = null
            shape.updateShapeRect()
            pipeline.updateShape(shape)
        }
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${shapeIds.size} shapes")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            shape.touchPointList = copyTouchPointList(newPoints[shapeId] ?: continue)
            shape.boundingRect = null
            shape.originRect = null
            shape.updateShapeRect()
            pipeline.updateShape(shape)
        }
    }

    companion object {
        fun copyTouchPointList(src: TouchPointList): TouchPointList {
            val dst = TouchPointList()
            for (pt in src.points) {
                if (pt == null) continue
                dst.add(pt.copyWith(pt.x, pt.y))
            }
            return dst
        }
    }
}
