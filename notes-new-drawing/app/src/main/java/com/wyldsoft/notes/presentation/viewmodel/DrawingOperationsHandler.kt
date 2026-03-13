package com.wyldsoft.notes.presentation.viewmodel

import android.util.Log
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.DrawAction
import com.wyldsoft.notes.actions.EraseAction
import com.wyldsoft.notes.actions.SnapToLineAction
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles draw, erase, and shape operations (pen strokes, geometry, snap-to-line).
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class DrawingOperationsHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val actionManager: ActionManager,
    private val getCurrentNote: () -> Note,
    private val getCurrentPenProfile: () -> PenProfile,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onUpdateContentBounds: () -> Unit,
    private val htrRunManager: HTRRunManager? = null
) {
    private val _isDrawing = MutableStateFlow(false)
    val isDrawing: StateFlow<Boolean> = _isDrawing.asStateFlow()

    private val pendingErasedShapes = mutableListOf<Shape>()
    private var isErasingInProgress = false

    fun startDrawing() {
        Log.d("DebugMarch6", "Starting drawing on note: ${getCurrentNote().id}")
        _isDrawing.value = true
    }

    fun endDrawing() {
        Log.d("DebugMarch6", "Ending drawing on note: ${getCurrentNote().id}")
        _isDrawing.value = false
    }

    fun addShape(
        id: String,
        points: List<android.graphics.PointF>,
        pressures: List<Float> = emptyList(),
        timestamps: List<Long> = emptyList()
    ) {
        scope.launch {
            val profile = getCurrentPenProfile()
            val shape = Shape(
                id = id,
                type = ShapeType.STROKE,
                points = points,
                strokeWidth = profile.strokeWidth,
                strokeColor = profile.getColorAsInt(),
                penType = profile.penType,
                pressure = pressures,
                pointTimestamps = timestamps
            )
            noteRepository.addShape(getCurrentNote().id, shape)

            val sm = getShapesManager()
            val bm = getBitmapManager()
            if (sm != null && bm != null) {
                actionManager.recordAction(DrawAction(getCurrentNote().id, shape, noteRepository, sm, bm))
            }

            htrRunManager?.addShapesForRecognition(getCurrentNote().id, listOf(shape))
            onUpdateContentBounds()
        }
    }

    fun removeShape(shapeId: String) {
        scope.launch {
            val note = getCurrentNote()
            val shape = note.shapes.find { it.id == shapeId }
            if (shape != null && isErasingInProgress) pendingErasedShapes.add(shape)
            noteRepository.removeShape(note.id, shapeId)
        }
    }

    fun startErasing() {
        isErasingInProgress = true
        pendingErasedShapes.clear()
    }

    fun endErasing() {
        isErasingInProgress = false
        onUpdateContentBounds()
        if (pendingErasedShapes.isNotEmpty()) {
            val sm = getShapesManager()
            val bm = getBitmapManager()
            if (sm != null && bm != null) {
                actionManager.recordAction(
                    EraseAction(getCurrentNote().id, pendingErasedShapes.toList(), noteRepository, sm, bm)
                )
            }
            htrRunManager?.onShapesDeleted(getCurrentNote().id, pendingErasedShapes.map { it.id }.toSet())
            pendingErasedShapes.clear()
        }
    }

    fun addGeometricShape(shape: Shape) {
        scope.launch {
            noteRepository.addShape(getCurrentNote().id, shape)
            val sm = getShapesManager()
            val bm = getBitmapManager()
            if (sm != null && bm != null) {
                actionManager.recordAction(DrawAction(getCurrentNote().id, shape, noteRepository, sm, bm))
            }
            onUpdateContentBounds()
        }
    }

    /**
     * Records a snap-to-line conversion as two undo steps:
     * 1. DrawAction(originalShape) — second undo removes the restored stroke
     * 2. SnapToLineAction          — first undo reverts line → original stroke
     */
    fun addSnapToLineAction(originalShape: Shape, lineShape: Shape) {
        scope.launch {
            val noteId = getCurrentNote().id
            noteRepository.addShape(noteId, lineShape)
            val sm = getShapesManager() ?: return@launch
            val bm = getBitmapManager() ?: return@launch
            actionManager.recordAction(DrawAction(noteId, originalShape, noteRepository, sm, bm))
            actionManager.recordAction(SnapToLineAction(noteId, originalShape, lineShape, noteRepository, sm, bm))
            onUpdateContentBounds()
        }
    }
}
