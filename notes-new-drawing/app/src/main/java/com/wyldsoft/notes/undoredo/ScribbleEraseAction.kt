package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records a scribble-to-erase gesture as an undoable action.
 *
 * Undo restores all erased shapes AND re-adds the scribble stroke to the canvas,
 * so the user can then undo the scribble stroke itself with a second undo.
 * Redo removes the scribble stroke and all erased shapes again.
 */
class ScribbleEraseAction(
    internal val scribbleShape: Shape,
    internal val erasedShapes: List<Shape>,
    private val pipeline: DrawingPipeline,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "ScribbleEraseAction"

    override suspend fun undo() {
        Log.d(TAG, "undo erasedShapes=${erasedShapes.size}")
        for (shape in erasedShapes) {
            pipeline.addShape(shape)
        }
        pipeline.addShape(scribbleShape)
    }

    override suspend fun redo() {
        Log.d(TAG, "redo erasedShapes=${erasedShapes.size}")
        pipeline.removeShape(scribbleShape)
        for (shape in erasedShapes) {
            pipeline.removeShape(shape)
        }
    }
}
