package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Records a snap-to-line conversion. When undone, restores the original freehand stroke.
 * When redone, replaces the stroke with the snapped line again.
 *
 * Used as the second action in a two-step undo sequence:
 *   1. DrawAction(originalShape) - second undo removes the restored stroke
 *   2. SnapToLineAction         - first undo reverts line → original stroke
 */
class SnapToLineAction(
    private val noteId: String,
    private val originalShape: Shape,
    private val lineShape: Shape,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, lineShape, noteRepository, shapesManager)
        ActionUtils.addShapeToNoteAndMemory(noteId, originalShape, noteRepository, shapesManager)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    override suspend fun redo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, originalShape, noteRepository, shapesManager)
        ActionUtils.addShapeToNoteAndMemory(noteId, lineShape, noteRepository, shapesManager)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
