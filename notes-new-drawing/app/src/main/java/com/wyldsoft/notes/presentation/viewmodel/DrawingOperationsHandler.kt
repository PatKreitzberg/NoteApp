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
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
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
    private val actionManager: ActionManager,
    private val getCurrentNote: () -> Note,
    private val getCurrentPenProfile: () -> PenProfile,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onUpdateContentBounds: () -> Unit,
    private val onScreenRefreshNeeded: () -> Unit = {},
    private val htrRunManager: HTRRunManager? = null,
    private val getActiveLayer: () -> Int = { 1 },
    private val onCircleSelect: ((Set<String>, RectF) -> Unit)? = null
) {
    companion object {
        private const val TAG = "DrawingOpsHandler"
        private const val SCRIBBLE_COVERAGE_THRESHOLD = 0.80f
        private const val CIRCLE_ENCLOSURE_THRESHOLD = 0.90f
    }
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
                pointTimestamps = timestamps,
                layer = getActiveLayer()
            )
            noteRepository.addShape(getCurrentNote().id, shape)

            val sm = getShapesManager()
            val bm = getBitmapManager()

            // Try immediate scribble-to-erase
            if (sm != null && bm != null && htrRunManager != null && timestamps.isNotEmpty()) {
                val isScribble = htrRunManager.isScribbleGesture(shape)
                if (isScribble) {
                    val coveredShapes = findShapesCoveredByScribble(shape, sm)
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
                            actionManager.recordAction(
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

            // Try circle-to-select
            if (sm != null && bm != null && htrRunManager != null && timestamps.isNotEmpty() && onCircleSelect != null) {
                val isCircle = htrRunManager.isCircleGesture(shape)
                if (isCircle) {
                    val encircledShapes = findShapesEncircledBy(shape, sm)
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

    /**
     * Compute bounding box of a domain Shape from its points.
     */
    private fun computeBoundingBox(shape: Shape): RectF? {
        if (shape.points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in shape.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Find SDK shapes on the same layer whose bounding box is >80% covered
     * by the scribble's bounding box.
     */
    private fun findShapesCoveredByScribble(
        scribble: Shape,
        shapesManager: ShapesManager
    ): List<com.wyldsoft.notes.shapemanagement.shapes.BaseShape> {
        val scribbleRect = computeBoundingBox(scribble) ?: return emptyList()
        val activeLayer = scribble.layer
        val result = mutableListOf<com.wyldsoft.notes.shapemanagement.shapes.BaseShape>()

        for (sdkShape in shapesManager.shapes()) {
            if (sdkShape.id == scribble.id) continue
            if (sdkShape.layer != activeLayer) continue
            val shapeRect = sdkShape.boundingRect ?: continue

            // Compute intersection area
            val interLeft = maxOf(scribbleRect.left, shapeRect.left)
            val interTop = maxOf(scribbleRect.top, shapeRect.top)
            val interRight = minOf(scribbleRect.right, shapeRect.right)
            val interBottom = minOf(scribbleRect.bottom, shapeRect.bottom)

            if (interLeft >= interRight || interTop >= interBottom) continue

            val interArea = (interRight - interLeft) * (interBottom - interTop)
            val shapeArea = shapeRect.width() * shapeRect.height()
            if (shapeArea <= 0f) continue

            val coverage = interArea / shapeArea
            if (coverage >= SCRIBBLE_COVERAGE_THRESHOLD) {
                result.add(sdkShape)
            }
        }

        return result
    }

    /**
     * Find SDK shapes on the same layer where >90% of their points
     * fall inside the circle's polygon (the drawn stroke points).
     */
    private fun findShapesEncircledBy(
        circle: Shape,
        shapesManager: ShapesManager
    ): List<com.wyldsoft.notes.shapemanagement.shapes.BaseShape> {
        if (circle.points.size < 3) return emptyList()
        val polygon = circle.points
        val activeLayer = circle.layer
        val result = mutableListOf<com.wyldsoft.notes.shapemanagement.shapes.BaseShape>()

        for (sdkShape in shapesManager.shapes()) {
            if (sdkShape.id == circle.id) continue
            if (sdkShape.layer != activeLayer) continue
            val touchPoints = sdkShape.touchPointList ?: continue
            if (touchPoints.size() == 0) continue

            var insideCount = 0
            for (i in 0 until touchPoints.size()) {
                val tp = touchPoints.get(i)
                if (pointInPolygon(tp.x, tp.y, polygon)) insideCount++
            }

            val ratio = insideCount.toFloat() / touchPoints.size()
            if (ratio >= CIRCLE_ENCLOSURE_THRESHOLD) {
                result.add(sdkShape)
            }
        }

        return result
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: List<android.graphics.PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x; val yi = polygon[i].y
            val xj = polygon[j].x; val yj = polygon[j].y
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
