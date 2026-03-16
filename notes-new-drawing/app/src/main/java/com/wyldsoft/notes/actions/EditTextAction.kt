package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Records a text create/edit operation.
 * - oldShape: the TextShape before editing (null when creating new text)
 * - newShape: the TextShape after editing (null when deleting text by committing blank)
 */
class EditTextAction(
    internal val noteId: String,
    internal val oldShape: Shape?,
    internal val newShape: Shape?,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        if (newShape != null) {
            ActionUtils.removeShapeFromNoteAndMemory(noteId, newShape, noteRepository, shapesManager)
        }
        if (oldShape != null) {
            ActionUtils.addShapeToNoteAndMemory(noteId, oldShape, noteRepository, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    override suspend fun redo() {
        if (oldShape != null) {
            ActionUtils.removeShapeFromNoteAndMemory(noteId, oldShape, noteRepository, shapesManager)
        }
        if (newShape != null) {
            ActionUtils.addShapeToNoteAndMemory(noteId, newShape, noteRepository, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
