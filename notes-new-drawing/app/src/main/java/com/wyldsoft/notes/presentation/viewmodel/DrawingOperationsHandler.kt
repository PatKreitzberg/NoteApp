package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.DrawAction
import com.wyldsoft.notes.actions.EraseAction
import com.wyldsoft.notes.actions.SnapToLineAction
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.geometry.GeometricShapeType
import com.wyldsoft.notes.geometry.GeometryShapeCalculator
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.utils.ShapeGeometryUtils
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
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
    private val getActionManager: () -> ActionManager,
    private val getCurrentNote: () -> Note,
    private val getCurrentPenProfile: () -> PenProfile,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onUpdateContentBounds: () -> Unit,
    private val onScreenRefreshNeeded: () -> Unit = {},
    private val htrRunManager: HTRRunManager? = null,
    private val getActiveLayer: () -> Int = { 1 },
    private val onCircleSelect: ((Set<String>, RectF) -> Unit)? = null,
    private val isShapeRecognitionEnabled: () -> Boolean = { false },
    private val isScribbleToEraseEnabled: () -> Boolean = { true },
    private val isCircleToSelectEnabled: () -> Boolean = { true }
) {
    companion object {
        private const val TAG = "DrawingOpsHandler"
    }
    private val _isDrawing = MutableStateFlow(false)
    val isDrawing: StateFlow<Boolean> = _isDrawing.asStateFlow()

    private val pendingErasedShapes = mutableListOf<Shape>()
    private var isErasingInProgress = false

    fun startDrawing() {
        Log.d(TAG, "Starting drawing on note: ${getCurrentNote().id}")
        _isDrawing.value = true
    }

    fun endDrawing() {
        Log.d(TAG, "Ending drawing on note: ${getCurrentNote().id}")
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
                pointTimestamps = timestamps,
                layer = getActiveLayer()
            )
            noteRepository.addShape(getCurrentNote().id, shape)

            val sm = getShapesManager()
            val bm = getBitmapManager()

            // Try immediate scribble-to-erase
            if (sm != null && bm != null && htrRunManager != null && timestamps.isNotEmpty() && isScribbleToEraseEnabled()) {
                val isScribble = htrRunManager.isScribbleGesture(shape)
                if (isScribble) {
                    val coveredShapes = ShapeGeometryUtils.findShapesCoveredByScribble(shape, sm)
                    if (coveredShapes.isNotEmpty()) {
                        Log.d(TAG, "Scribble-to-erase: erasing ${coveredShapes.size} shape(s)")
                        // Remove the scribble itself (no DrawAction — not undoable)
                        noteRepository.removeShape(getCurrentNote().id, shape.id)
                        val scribbleSdk = sm.shapes().find { it.id == shape.id }
                        if (scribbleSdk != null) sm.removeShape(scribbleSdk)

                        // Remove covered shapes and record as EraseAction
                        val coveredDomainShapes = mutableListOf<Shape>()
                        for (covered in coveredShapes) {
                            val domainShape = getCurrentNote().shapes.find { it.id == covered.id }
                            if (domainShape != null) coveredDomainShapes.add(domainShape)
                            sm.removeShape(covered)
                            noteRepository.removeShape(getCurrentNote().id, covered.id)
                        }

                        if (coveredDomainShapes.isNotEmpty()) {
                            getActionManager().recordAction(
                                EraseAction(getCurrentNote().id, coveredDomainShapes, noteRepository, sm, bm)
                            )
                            htrRunManager.onShapesDeleted(
                                getCurrentNote().id,
                                coveredDomainShapes.map { it.id }.toSet()
                            )
                        }

                        bm.recreateBitmapFromShapes(sm.shapes())
                        onScreenRefreshNeeded()
                        onUpdateContentBounds()
                        return@launch
                    }
                }
            }

            // Try shape recognition (when enabled, replaces freehand with geometric shape)
            if (sm != null && bm != null && htrRunManager != null && timestamps.isNotEmpty()
                && isShapeRecognitionEnabled()
            ) {
                val result = htrRunManager.recognizeShape(shape)
                if (result != null) {
                    Log.d(TAG, "Shape recognition: replacing stroke with ${result.shapeType.displayName()} (score=${result.confidence})")
                    // Remove the freehand stroke
                    noteRepository.removeShape(getCurrentNote().id, shape.id)
                    val freehandSdk = sm.shapes().find { it.id == shape.id }
                    if (freehandSdk != null) sm.removeShape(freehandSdk)

                    // Compute bounding box of freehand stroke, use as start/end for geometry
                    val bbox = ShapeGeometryUtils.computeBoundingBox(shape)
                    if (bbox != null) {
                        val centerX = bbox.centerX()
                        val centerY = bbox.centerY()
                        val endX = bbox.right
                        val endY = bbox.centerY()
                        val notePoints = GeometryShapeCalculator.calculate(
                            result.shapeType, centerX, centerY, endX, endY
                        )

                        // Create SDK BaseShape so it renders
                        val shapePointList = TouchPointList()
                        val now = System.currentTimeMillis()
                        notePoints.forEach { pt ->
                            shapePointList.add(TouchPoint(pt.x, pt.y, 1.0f, 1.0f, now))
                        }
                        val sdkShapeType = ShapesManager.penTypeToShapeType(shape.penType)
                        val baseShape = ShapeFactory.createShape(sdkShapeType).apply {
                            setTouchPointList(shapePointList)
                            setStrokeColor(shape.strokeColor)
                            setStrokeWidth(shape.strokeWidth)
                            setShapeType(sdkShapeType)
                        }
                        ShapesManager.applyCharcoalTexture(baseShape, shape.penType)
                        baseShape.layer = shape.layer
                        baseShape.updateShapeRect()
                        sm.addShape(baseShape)

                        // Persist domain shape + record undoable action
                        val geometricShape = Shape(
                            id = baseShape.id,
                            type = result.shapeType.toDomainShapeType(),
                            points = notePoints,
                            strokeWidth = shape.strokeWidth,
                            strokeColor = shape.strokeColor,
                            penType = shape.penType,
                            layer = shape.layer
                        )
                        addGeometricShape(geometricShape)
                    }
                    bm.recreateBitmapFromShapes(sm.shapes())
                    onScreenRefreshNeeded()
                    return@launch
                }
            }

            // Try circle-to-select (skipped when shape recognition is enabled or feature disabled)
            if (sm != null && bm != null && htrRunManager != null && timestamps.isNotEmpty()
                && onCircleSelect != null && !isShapeRecognitionEnabled() && isCircleToSelectEnabled()
            ) {
                val isCircle = htrRunManager.isCircleGesture(shape)
                if (isCircle) {
                    val encircledShapes = ShapeGeometryUtils.findShapesEncircledBy(shape, sm)
                    if (encircledShapes.isNotEmpty()) {
                        Log.d(TAG, "Circle-to-select: selecting ${encircledShapes.size} shape(s)")
                        // Remove the circle shape — never persist it
                        noteRepository.removeShape(getCurrentNote().id, shape.id)
                        val circleSdk = sm.shapes().find { it.id == shape.id }
                        if (circleSdk != null) sm.removeShape(circleSdk)

                        val selectedIds = encircledShapes.map { it.id }.toSet()
                        val selectedSdkShapes = sm.shapes().filter { it.id in selectedIds }
                        selectedSdkShapes.forEach { it.updateShapeRect() }
                        val boundingBox = calculateShapesBoundingBox(selectedSdkShapes)

                        if (boundingBox != null) {
                            bm.recreateBitmapFromShapes(sm.shapes())
                            onScreenRefreshNeeded()
                            onCircleSelect.invoke(selectedIds, boundingBox)
                        }
                        return@launch
                    }
                }
            }

            // Normal shape — record DrawAction
            if (sm != null && bm != null) {
                getActionManager().recordAction(DrawAction(getCurrentNote().id, shape, noteRepository, sm, bm))
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
                getActionManager().recordAction(
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
                getActionManager().recordAction(DrawAction(getCurrentNote().id, shape, noteRepository, sm, bm))
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
            getActionManager().recordAction(DrawAction(noteId, originalShape, noteRepository, sm, bm))
            getActionManager().recordAction(SnapToLineAction(noteId, originalShape, lineShape, noteRepository, sm, bm))
            onUpdateContentBounds()
        }
    }

}
