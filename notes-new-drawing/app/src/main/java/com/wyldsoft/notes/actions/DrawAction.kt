package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

class DrawAction(
    private val noteId: String,
    private val shape: Shape,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    override suspend fun redo() {
        ActionUtils.addShapeToNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
