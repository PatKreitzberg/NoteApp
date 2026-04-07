package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records all shapes erased in a single erase stroke as one undoable action.
 * Undo restores all erased shapes; redo removes them again.
 */
class EraseAction(
    internal val erasedShapes: List<Shape>,
    private val pipeline: DrawingPipeline,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "EraseAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${erasedShapes.size} shapes")
        for (shape in erasedShapes) {
            pipeline.addShape(shape)
        }
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${erasedShapes.size} shapes")
        for (shape in erasedShapes) {
            pipeline.removeShape(shape)
        }
    }
}
