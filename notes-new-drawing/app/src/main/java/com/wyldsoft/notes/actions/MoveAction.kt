package com.wyldsoft.notes.actions

import android.graphics.PointF
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Records a move operation so it can be undone/redone.
 * Stores the original and moved domain shapes for each affected shape.
 */
class MoveAction(
    private val noteId: String,
    private val originalShapes: List<Shape>,
    private val dx: Float,
    private val dy: Float,
    private val noteRepository: NoteRepository,
    private val shapesManager: ShapesManager,
    private val bitmapManager: BitmapManager
) : ActionInterface {

    override suspend fun undo() {
        // Move shapes back by -dx, -dy
        for (original in originalShapes) {
            // Restore original points in database
            noteRepository.updateShape(noteId, original)

            // Restore original points in memory (SDK shape)
            val sdkShape = shapesManager.shapes().find { it.id == original.id }
            if (sdkShape != null) {
                val newList = TouchPointList()
                for (pt in original.points) {
                    val pressure = original.pressure.getOrElse(original.points.indexOf(pt)) { 0.5f }
                    newList.add(TouchPoint(pt.x, pt.y, pressure, 1f, System.currentTimeMillis()))
                }
                sdkShape.touchPointList = newList
                sdkShape.updateShapeRect()
            }
        }
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }

    override suspend fun redo() {
        // Move shapes forward by dx, dy
        for (original in originalShapes) {
            val movedPoints = original.points.map { PointF(it.x + dx, it.y + dy) }
            val movedShape = original.copy(points = movedPoints)
            noteRepository.updateShape(noteId, movedShape)

            val sdkShape = shapesManager.shapes().find { it.id == original.id }
            if (sdkShape != null) {
                val newList = TouchPointList()
                for ((idx, pt) in movedPoints.withIndex()) {
                    val pressure = original.pressure.getOrElse(idx) { 0.5f }
                    newList.add(TouchPoint(pt.x, pt.y, pressure, 1f, System.currentTimeMillis()))
                }
                sdkShape.touchPointList = newList
                sdkShape.updateShapeRect()
            }
        }
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
    }
}
