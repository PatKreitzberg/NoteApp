package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.shapemanagement.ShapesManager

class DrawAction(
    internal val noteId: String,
    internal val shape: Shape,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager
) : ActionInterface {

    override suspend fun undo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, shape, noteRepository, shapesManager)
    }

    override suspend fun redo() {
        ActionUtils.addShapeToNoteAndMemory(noteId, shape, noteRepository, shapesManager)
    }
}
