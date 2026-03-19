package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.shapemanagement.ShapesManager

class ConvertToTextAction(
    internal val noteId: String,
    internal val originalShapes: List<Shape>,
    internal val textShape: Shape,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager
) : ActionInterface {

    override suspend fun undo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, textShape, noteRepository, shapesManager)
        for (shape in originalShapes) {
            ActionUtils.addShapeToNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        }
    }

    override suspend fun redo() {
        for (shape in originalShapes) {
            ActionUtils.removeShapeFromNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        }
        ActionUtils.addShapeToNoteAndMemory(noteId, textShape, noteRepository, shapesManager)
    }
}
