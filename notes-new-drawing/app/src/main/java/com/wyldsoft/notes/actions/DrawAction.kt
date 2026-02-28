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
        // Remove from database
        noteRepository.removeShape(noteId, shape.id)
        // Remove from in-memory SDK shapes
        val sdkShape = shapesManager.shapes().find { it.id == shape.id }
        if (sdkShape != null) {
            shapesManager.removeShape(sdkShape)
        }
        // Recreate bitmap from remaining shapes
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }

    override suspend fun redo() {
        // Re-add to database
        noteRepository.addShape(noteId, shape)
        // Re-add to in-memory SDK shapes
        val sdkShape = shapesManager.convertDomainShapeToSdkShape(shape)
        shapesManager.addShape(sdkShape)
        // Recreate bitmap
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }
}
