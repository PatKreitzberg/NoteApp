package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records all shapes created by a single paste operation as one undoable action.
 * Undo removes the pasted shapes; redo re-adds them.
 */
class PasteAction(
    internal val pastedShapes: List<Shape>,
    private val pipeline: DrawingPipeline,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "PasteAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${pastedShapes.size} shapes")
        for (shape in pastedShapes) {
            pipeline.removeShape(shape)
        }
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${pastedShapes.size} shapes")
        for (shape in pastedShapes) {
            pipeline.addShape(shape)
        }
    }
}
