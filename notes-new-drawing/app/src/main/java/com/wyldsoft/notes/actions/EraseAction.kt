package com.wyldsoft.notes.actions

import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

class EraseAction(
    private val noteId: String,
    private val erasedShapes: List<Shape>,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        // Re-add all erased shapes to database and in-memory
        for (shape in erasedShapes) {
            noteRepository.addShape(noteId, shape)
            val sdkShape = shapesManager.convertDomainShapeToSdkShape(shape)
            shapesManager.addShape(sdkShape)
        }
        // Recreate bitmap with restored shapes
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }

    override suspend fun redo() {
        // Re-remove all shapes
        for (shape in erasedShapes) {
            noteRepository.removeShape(noteId, shape.id)
            val sdkShape = shapesManager.shapes().find { it.id == shape.id }
            if (sdkShape != null) {
                shapesManager.removeShape(sdkShape)
            }
        }
        // Recreate bitmap without erased shapes
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }
}
