package com.wyldsoft.notes.undoredo

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.selection.SelectionManager

/**
 * Records a completed selection-move operation.
 * Stores shape IDs rather than object references so that undo/redo still works
 * correctly after a DrawAction.undo/redo has replaced the object in drawnShapes.
 * Undo translates shapes back by (-dNoteX, -dNoteY); redo re-applies the move.
 */
class MoveAction(
    val shapeIds: List<String>,
    val dNoteX: Float,
    val dNoteY: Float,
    private val pipeline: DrawingPipeline,
    private val selectionManager: SelectionManager,
    override val id: String = NanoIdUtils.randomNanoId()
) : ActionInterface {
    private val TAG = "MoveAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${shapeIds.size} shapes by (-$dNoteX, -$dNoteY)")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            selectionManager.translateShape(shape, -dNoteX, -dNoteY)
            pipeline.updateShape(shape)
        }
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${shapeIds.size} shapes by ($dNoteX, $dNoteY)")
        for (shapeId in shapeIds) {
            val shape = pipeline.getShapeById(shapeId) ?: continue
            selectionManager.translateShape(shape, dNoteX, dNoteY)
            pipeline.updateShape(shape)
        }
    }
}
