package com.wyldsoft.notes.sdkintegration

import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.geometry.GeometryShapeCalculator
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Manages geometry shape drawing (preview + finalization).
 * Extracted from AbstractStylusHandler to keep it under 300 lines.
 */
class GeometryDrawingHandler(
    private val viewModel: EditorViewModel,
    private val bitmapManager: BitmapManager,
    private val getShapesManager: () -> ShapesManager,
    private val displaySettingsRepository: DisplaySettingsRepository,
    private val onStarted: () -> Unit,
    private val onFinalized: () -> Unit,
    private val onForceScreenRefresh: () -> Unit,
    private val getCurrentPenProfile: () -> PenProfile
) {
    private val shapesManager: ShapesManager get() = getShapesManager()
    var isActive = false
        private set
    private var startNoteX = 0f
    private var startNoteY = 0f
    private var lastPreviewTime = 0L

    fun begin(touchPoint: TouchPoint) {
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        startNoteX = notePoint.x
        startNoteY = notePoint.y
        isActive = true
        bitmapManager.beginGeometryDrawing()
        onStarted()
    }

    fun updatePreview(touchPoint: TouchPoint) {
        if (!isActive || !displaySettingsRepository.smoothMotion.value) return
        val now = System.currentTimeMillis()
        if (now - lastPreviewTime < displaySettingsRepository.minRefreshIntervalMs) return
        lastPreviewTime = now
        val noteEnd = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        val shapeType = viewModel.uiState.value.selectedGeometricShape
        val notePoints = GeometryShapeCalculator.calculate(shapeType, startNoteX, startNoteY, noteEnd.x, noteEnd.y)
        bitmapManager.drawGeometryPreview(notePoints, getCurrentPenProfile())
    }

    fun finalize(touchPointList: TouchPointList) {
        if (!isActive) return
        val penProfile = getCurrentPenProfile()
        val lastPoint = touchPointList.points?.lastOrNull()

        if (lastPoint != null) {
            val noteEnd = viewModel.viewportManager.surfaceToNoteCoordinates(lastPoint.x, lastPoint.y)
            val shapeType = viewModel.uiState.value.selectedGeometricShape
            val notePoints = GeometryShapeCalculator.calculate(shapeType, startNoteX, startNoteY, noteEnd.x, noteEnd.y)

            val shapePointList = TouchPointList()
            val now = System.currentTimeMillis()
            notePoints.forEach { pt -> shapePointList.add(TouchPoint(pt.x, pt.y, 1.0f, 1.0f, now)) }

            val sdkShapeType = ShapesManager.penTypeToShapeType(penProfile.penType)
            val baseShape = ShapeFactory.createShape(sdkShapeType).apply {
                setTouchPointList(shapePointList)
                setStrokeColor(penProfile.getColorAsInt())
                setStrokeWidth(penProfile.strokeWidth)
                setShapeType(sdkShapeType)
            }
            ShapesManager.applyCharcoalTexture(baseShape, penProfile.penType)
            baseShape.layer = viewModel.activeLayer.value
            baseShape.updateShapeRect()
            shapesManager.addShape(baseShape)

            val domainShape = Shape(
                id = baseShape.id,
                type = shapeType.toDomainShapeType(),
                points = notePoints,
                strokeWidth = penProfile.strokeWidth,
                strokeColor = penProfile.getColorAsInt(),
                penType = penProfile.penType,
                layer = viewModel.activeLayer.value
            )
            viewModel.addGeometricShape(domainShape)

            val noteBounds = baseShape.boundingRect
            if (noteBounds != null) bitmapManager.partialRefresh(noteBounds, shapesManager.shapes(), null)
            else onForceScreenRefresh()
        } else {
            onForceScreenRefresh()
        }

        bitmapManager.endGeometryDrawing()
        isActive = false
        onFinalized()
    }
}
