package com.wyldsoft.notes.undoredo

import android.util.Log
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.selection.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Records a completed selection-move operation.
 * Undo translates shapes back by (-dNoteX, -dNoteY); redo re-applies the move.
 */
class MoveAction(
    private val movedShapes: List<Shape>,
    private val dNoteX: Float,
    private val dNoteY: Float,
    private val pipeline: DrawingPipeline,
    private val selectionManager: SelectionManager
) : ActionInterface {
    private val TAG = "MoveAction"

    override suspend fun undo() {
        Log.d(TAG, "undo ${movedShapes.size} shapes by (-$dNoteX, -$dNoteY)")
        for (shape in movedShapes) {
            selectionManager.translateShape(shape, -dNoteX, -dNoteY)
            pipeline.updateShape(shape)
        }
    }

    override suspend fun redo() {
        Log.d(TAG, "redo ${movedShapes.size} shapes by ($dNoteX, $dNoteY)")
        for (shape in movedShapes) {
            selectionManager.translateShape(shape, dNoteX, dNoteY)
            pipeline.updateShape(shape)
        }
    }
}
