package com.wyldsoft.notes.actions

import android.graphics.PointF
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.utils.domainPointsToTouchPointList

object ActionUtils {

    fun updateSdkShapePoints(
        shape: Shape,
        points: List<PointF>,
        shapesManager: ShapesManager
    ) {
        val sdkShape = shapesManager.shapes().find { it.id == shape.id } ?: return
        sdkShape.touchPointList = domainPointsToTouchPointList(points, shape.pressure, shape.tiltX, shape.tiltY, shape.strokeWidth)
        sdkShape.updateShapeRect()
    }

    suspend fun addShapeToNoteAndMemory(
        noteId: String,
        shape: Shape,
        noteRepository: NoteRepository,
        shapesManager: ShapesManager
    ) {
        noteRepository.addShape(noteId, shape)
        val sdkShape = shapesManager.convertDomainShapeToSdkShape(shape)
        shapesManager.addShape(sdkShape)
    }

    suspend fun removeShapeFromNoteAndMemory(
        noteId: String,
        shape: Shape,
        noteRepository: NoteRepository,
        shapesManager: ShapesManager
    ) {
        noteRepository.removeShape(noteId, shape.id)
        val sdkShape = shapesManager.shapes().find { it.id == shape.id }
        if (sdkShape != null) {
            shapesManager.removeShape(sdkShape)
        }
    }
}
