package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records the creation of a single drawn stroke.
 * Undo removes it; redo re-adds it.
 */
class DrawAction(
    internal val shape: Shape,
    private val pipeline: DrawingPipeline,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "DrawAction"

    override suspend fun undo() {
        Log.d(TAG, "undo entityId=${shape.entityId}")
        pipeline.removeShape(shape)
    }

    override suspend fun redo() {
        Log.d(TAG, "redo entityId=${shape.entityId}")
        pipeline.addShape(shape)
    }
}
