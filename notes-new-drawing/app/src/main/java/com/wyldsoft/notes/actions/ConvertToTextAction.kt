package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

class ConvertToTextAction(
    private val noteId: String,
    private val originalShapes: List<Shape>,
    private val textShape: Shape,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        ActionUtils.removeShapeFromNoteAndMemory(noteId, textShape, noteRepository, shapesManager)
        for (shape in originalShapes) {
            ActionUtils.addShapeToNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        }
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }

    override suspend fun redo() {
        for (shape in originalShapes) {
            ActionUtils.removeShapeFromNoteAndMemory(noteId, shape, noteRepository, shapesManager)
        }
        ActionUtils.addShapeToNoteAndMemory(noteId, textShape, noteRepository, shapesManager)
        ActionUtils.refreshBitmap(shapesManager, bitmapManager)
    }
}
