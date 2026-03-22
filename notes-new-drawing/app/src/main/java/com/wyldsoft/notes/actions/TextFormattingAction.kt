package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.TextShape

class TextFormattingAction(
    internal val noteId: String,
    internal val beforeShapes: List<Shape>,
    internal val afterShapes: List<Shape>,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager
) : ActionInterface {

    override suspend fun undo() {
        for (shape in beforeShapes) {
            applyToSdkShape(shape)
            noteRepository.updateShape(noteId, shape)
        }
    }

    override suspend fun redo() {
        for (shape in afterShapes) {
            applyToSdkShape(shape)
            noteRepository.updateShape(noteId, shape)
        }
    }

    private fun applyToSdkShape(shape: Shape) {
        val sdkShape = shapesManager.shapes().find { it.id == shape.id } as? TextShape ?: return
        sdkShape.fontSize = shape.fontSize
        sdkShape.fontFamily = shape.fontFamily
        sdkShape.strokeColor = shape.strokeColor
        sdkShape.updateShapeRect()
    }
}
