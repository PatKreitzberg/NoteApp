package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records a circle-to-select gesture as an undoable action.
 *
 * Undo re-adds the circle stroke to the canvas and calls [onUndoCallback] to exit
 * selection mode. Redo removes the circle stroke again and calls [onRedoCallback]
 * to re-enter selection mode with the original encircled shapes.
 *
 * The callbacks are volatile (not persisted to DB). On reconstruction after app restart
 * the callbacks are no-ops, but shape add/remove still works correctly.
 */
class CircleSelectAction(
    internal val circleShape: Shape,
    internal val encircledShapeIds: List<String>,
    private val pipeline: DrawingPipeline,
    private val onUndoCallback: suspend () -> Unit = {},
    private val onRedoCallback: suspend (List<Shape>) -> Unit = {},
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "CircleSelectAction"

    override suspend fun undo() {
        Log.d(TAG, "undo circleShape=${circleShape.entityId}")
        pipeline.addShape(circleShape)
        onUndoCallback()
    }

    override suspend fun redo() {
        Log.d(TAG, "redo circleShape=${circleShape.entityId}")
        pipeline.removeShape(circleShape)
        val encircled = encircledShapeIds.mapNotNull { pipeline.getShapeById(it) }
        onRedoCallback(encircled)
    }
}
