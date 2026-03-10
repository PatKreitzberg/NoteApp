package com.wyldsoft.notes.sdkintegration

import android.os.Handler
import android.os.Looper
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.geometry.GeometricShapeType
import com.wyldsoft.notes.geometry.GeometryShapeCalculator
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Detects when the user holds the stylus still during a freehand stroke and snaps
 * it into a straight line. After snapping, the user can drag the endpoint before lifting.
 */
class LineSnapHandler(
    private val viewModel: EditorViewModel,
    private val bitmapManager: BitmapManager,
    private val shapesManager: ShapesManager,
    private val onSnapActivated: () -> Unit,
    private val onFinalized: () -> Unit,
    private val onForceScreenRefresh: () -> Unit,
    private val getCurrentPenProfile: () -> PenProfile
) {
    companion object {
        private const val HOLD_DURATION_MS = 500L
        private const val MOVE_THRESHOLD_PX = 15f
    }

    var isSnapped = false
        private set

    private var startNoteX = 0f
    private var startNoteY = 0f
    private var lastNoteEndX = 0f
    private var lastNoteEndY = 0f
    private var lastSurfaceX = 0f
    private var lastSurfaceY = 0f
    private var lastPreviewTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val snapRunnable = Runnable { activateSnap() }

    fun onStrokeBegin(touchPoint: TouchPoint) {
        isSnapped = false
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        startNoteX = notePoint.x
        startNoteY = notePoint.y
        lastNoteEndX = notePoint.x
        lastNoteEndY = notePoint.y
        lastSurfaceX = touchPoint.x
        lastSurfaceY = touchPoint.y
        handler.removeCallbacks(snapRunnable)
        handler.postDelayed(snapRunnable, HOLD_DURATION_MS)
    }

    /** Call for every move event while not yet snapped to track significant movement. */
    fun onStrokeMove(touchPoint: TouchPoint) {
        val dx = touchPoint.x - lastSurfaceX
        val dy = touchPoint.y - lastSurfaceY
        if (dx * dx + dy * dy > MOVE_THRESHOLD_PX * MOVE_THRESHOLD_PX) {
            handler.removeCallbacks(snapRunnable)
            lastSurfaceX = touchPoint.x
            lastSurfaceY = touchPoint.y
            val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
            lastNoteEndX = notePoint.x
            lastNoteEndY = notePoint.y
            handler.postDelayed(snapRunnable, HOLD_DURATION_MS)
        }
    }

    /** Call for every move event while snapped to update the line endpoint. */
    fun onSnapMove(touchPoint: TouchPoint) {
        val now = System.currentTimeMillis()
        if (now - lastPreviewTime < 32) return // ~30fps throttle
        lastPreviewTime = now
        val noteEnd = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        lastNoteEndX = noteEnd.x
        lastNoteEndY = noteEnd.y
        val linePoints = GeometryShapeCalculator.calculate(
            GeometricShapeType.LINE, startNoteX, startNoteY, noteEnd.x, noteEnd.y
        )
        bitmapManager.drawGeometryPreview(linePoints, getCurrentPenProfile())
    }

    /**
     * Call when the stroke ends (stylus lifted). Returns true if the snap handler
     * consumed the stroke (finalized as a line), false if the caller should handle it.
     */
    fun onStrokeEnd(touchPointList: TouchPointList): Boolean {
        handler.removeCallbacks(snapRunnable)
        if (!isSnapped) return false
        finalizeAsLine(touchPointList)
        return true
    }

    fun cancel() {
        handler.removeCallbacks(snapRunnable)
        if (isSnapped) {
            bitmapManager.endGeometryDrawing()
            isSnapped = false
        }
    }

    private fun activateSnap() {
        // Bitmap currently has the pre-stroke state (Onyx renders raw strokes separately).
        bitmapManager.beginGeometryDrawing()
        isSnapped = true
        onSnapActivated()
        val linePoints = GeometryShapeCalculator.calculate(
            GeometricShapeType.LINE, startNoteX, startNoteY, lastNoteEndX, lastNoteEndY
        )
        bitmapManager.drawGeometryPreview(linePoints, getCurrentPenProfile())
    }

    private fun finalizeAsLine(originalTouchPointList: TouchPointList) {
        val penProfile = getCurrentPenProfile()
        val linePoints = GeometryShapeCalculator.calculate(
            GeometricShapeType.LINE, startNoteX, startNoteY, lastNoteEndX, lastNoteEndY
        )
        val shapePointList = TouchPointList()
        val now = System.currentTimeMillis()
        linePoints.forEach { pt -> shapePointList.add(TouchPoint(pt.x, pt.y, 1.0f, 1.0f, now)) }

        val sdkShapeType = ShapesManager.penTypeToShapeType(penProfile.penType)
        val baseShape = ShapeFactory.createShape(sdkShapeType).apply {
            setTouchPointList(shapePointList)
            setStrokeColor(penProfile.getColorAsInt())
            setStrokeWidth(penProfile.strokeWidth)
            setShapeType(sdkShapeType)
        }
        ShapesManager.applyCharcoalTexture(baseShape, penProfile.penType)
        baseShape.updateShapeRect()
        shapesManager.addShape(baseShape)

        val domainShape = Shape(
            id = baseShape.id,
            type = GeometricShapeType.LINE.toDomainShapeType(),
            points = linePoints,
            strokeWidth = penProfile.strokeWidth,
            strokeColor = penProfile.getColorAsInt(),
            penType = penProfile.penType
        )

        // Reconstruct the original freehand stroke from the raw touch points (surface→note coords)
        val notePoints = originalTouchPointList.points?.map { tp ->
            viewModel.viewportManager.surfaceToNoteCoordinates(tp.x, tp.y)
        } ?: emptyList()
        val pressures = originalTouchPointList.points?.map { it.pressure } ?: emptyList()
        val originalShape = Shape(
            type = ShapeType.STROKE,
            points = notePoints,
            strokeWidth = penProfile.strokeWidth,
            strokeColor = penProfile.getColorAsInt(),
            penType = penProfile.penType,
            pressure = pressures
        )

        // Record as two undo steps: DrawAction(original) then SnapToLineAction(original→line)
        viewModel.addSnapToLineAction(originalShape, domainShape)

        val noteBounds = baseShape.boundingRect
        if (noteBounds != null) bitmapManager.partialRefresh(noteBounds, shapesManager.shapes(), null)
        else onForceScreenRefresh()

        bitmapManager.endGeometryDrawing()
        isSnapped = false
        onFinalized()
    }
}
