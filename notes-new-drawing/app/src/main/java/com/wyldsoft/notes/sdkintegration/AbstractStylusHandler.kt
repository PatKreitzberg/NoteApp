package com.wyldsoft.notes.sdkintegration

import android.graphics.PointF
import android.graphics.RectF
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.DrawManager
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.utils.surfacePointsToNoteTouchPoints

/**
 * Base class for stylus handlers. Coordinates drawing, erasing, geometry, and selection.
 * SDK-specific input wiring is done by subclasses (Onyx, Generic).
 * Geometry drawing delegated to [GeometryDrawingHandler].
 * Selection input delegated to [SelectionInputHandler].
 */
abstract class AbstractStylusHandler(
    protected val surfaceView: SurfaceView,
    protected val viewModel: EditorViewModel,
    protected val bitmapManager: BitmapManager,
    private val getShapesManager: () -> ShapesManager,
    protected val displaySettingsRepository: DisplaySettingsRepository,
    protected val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    protected val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    protected val onShapeRemoved: (shapeId: String) -> Unit,
    protected val onForceScreenRefresh: () -> Unit,
    rxManager: RxManager? = null
) {
    protected val shapesManager: ShapesManager get() = getShapesManager()
    protected var drawManager = DrawManager(bitmapManager, onShapeCompleted, getActiveLayer = { viewModel.activeLayer.value })
    protected val eraseManager = EraseManager(surfaceView, rxManager, bitmapManager, onShapeRemoved)

    protected var isDrawingInProgress = false
    protected var isErasingInProgress = false
    private var lastEraseDidPartialRefresh = false
    protected var currentPenProfile: PenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)
    protected var refreshCount: Int = 0
    protected val REFRESH_COUNT_LIMIT: Int = 100

    protected val geometryHandler = GeometryDrawingHandler(
        viewModel = viewModel,
        bitmapManager = bitmapManager,
        getShapesManager = getShapesManager,
        displaySettingsRepository = displaySettingsRepository,
        onStarted = { isDrawingInProgress = true; onDrawingStateChanged(true); viewModel.startDrawing() },
        onFinalized = { isDrawingInProgress = false; onDrawingStateChanged(false); viewModel.endDrawing() },
        onForceScreenRefresh = onForceScreenRefresh,
        getCurrentPenProfile = { currentPenProfile }
    )

    protected val selectionInputHandler = SelectionInputHandler(
        viewModel = viewModel,
        bitmapManager = bitmapManager,
        getShapesManager = getShapesManager,
        onSelectionTransformStarted = { onSelectionTransformStarted() },
        onLassoStarted = { onLassoStarted() },
        onLassoSelectionCompleted = { onLassoSelectionCompleted() },
        onForceScreenRefresh = onForceScreenRefresh
    )

    protected val lineSnapHandler = LineSnapHandler(
        viewModel = viewModel,
        bitmapManager = bitmapManager,
        getShapesManager = getShapesManager,
        onSnapActivated = { onLineSnapActivated() },
        onFinalized = { isDrawingInProgress = false; onDrawingStateChanged(false); viewModel.endDrawing() },
        onForceScreenRefresh = onForceScreenRefresh,
        getCurrentPenProfile = { currentPenProfile }
    )

    // --- Hooks for SDK-specific behavior ---
    protected open fun onSelectionTransformStarted() {}
    protected open fun onLassoSelectionCompleted() {}
    protected open fun onLassoStarted() {}
    protected open fun onLineSnapActivated() {}

    // --- Drawing ---

    protected fun beginDrawing(touchPoint: TouchPoint? = null) {
        isDrawingInProgress = true
        onDrawingStateChanged(true)
        viewModel.startDrawing()
        touchPoint?.let { lineSnapHandler.onStrokeBegin(it) }
    }

    protected fun beginSelectionStroke(touchPoint: TouchPoint?) {
        onDrawingStateChanged(true)
        selectionInputHandler.handleBegin(touchPoint)
    }

    protected fun finalizeStroke(touchPointList: TouchPointList) {
        touchPointList.points?.let {
            val notePointList = surfacePointsToNoteTouchPoints(touchPointList, viewModel.viewportManager)
            val newShape = drawManager.newShape(notePointList)
            shapesManager.addShape(newShape)
        }
        isDrawingInProgress = false
        onDrawingStateChanged(false)
        viewModel.endDrawing()
    }

    protected fun handleCancelledStroke(): Boolean {
        if (selectionInputHandler.wasCancelled) {
            selectionInputHandler.clearCancelled()
            onDrawingStateChanged(false)
            // Now that stylus is lifted, switch to Draw mode so next touch draws
            viewModel.cancelSelection()
            return true
        }
        return false
    }

    protected fun handleSelectorStrokeEnd(touchPointList: TouchPointList): Boolean {
        if (viewModel.uiState.value.mode is EditorMode.Select) {
            selectionInputHandler.handleEnd(touchPointList)
            onDrawingStateChanged(false)
            return true
        }
        return false
    }

    // --- Line snap (delegated) ---

    protected fun trackLineSnapMove(touchPoint: TouchPoint) = lineSnapHandler.onStrokeMove(touchPoint)
    protected fun updateLineSnapMove(touchPoint: TouchPoint) = lineSnapHandler.onSnapMove(touchPoint)
    protected fun finalizeWithLineSnap(touchPointList: TouchPointList): Boolean = lineSnapHandler.onStrokeEnd(touchPointList)
    protected val isLineSnapped get() = lineSnapHandler.isSnapped

    // --- Geometry (delegated) ---

    protected fun beginGeometryDrawing(touchPoint: TouchPoint) = geometryHandler.begin(touchPoint)
    protected fun updateGeometryPreview(touchPoint: TouchPoint) = geometryHandler.updatePreview(touchPoint)
    protected fun finalizeGeometryShape(touchPointList: TouchPointList) = geometryHandler.finalize(touchPointList)

    // --- Erasing ---

    protected fun beginErasing() {
        isErasingInProgress = true
        viewModel.startErasing()
    }

    protected fun finalizeErase(noteErasePointList: TouchPointList) {
        lastEraseDidPartialRefresh = eraseManager.handleErasing(noteErasePointList, shapesManager, viewModel.activeLayer.value)
    }

    protected fun endErasing() {
        isErasingInProgress = false
        viewModel.endErasing()
        if (!lastEraseDidPartialRefresh) onForceScreenRefresh()
        lastEraseDidPartialRefresh = false
    }

    // --- Selection move (delegated) ---

    protected fun handleSelectionMoveUpdate(touchPoint: TouchPoint) =
        selectionInputHandler.handleMoveUpdate(touchPoint)

    protected fun handleSelectionInput(touchPointList: TouchPointList) =
        selectionInputHandler.handleEnd(touchPointList)

    protected fun doPartialSelectionRefresh(oldBBoxNote: RectF?, newBBoxNote: RectF?, drawOverlay: Boolean = true) =
        selectionInputHandler.doPartialRefresh(oldBBoxNote, newBBoxNote, drawOverlay)

    // --- Backwards-compatible accessors for subclasses ---

    protected val selectionManager get() = viewModel.selectionManager
    protected val isGeometryDrawingInProgress get() = geometryHandler.isActive

    protected fun convertTouchPointListToNoteCoordinates(surfacePointList: TouchPointList): TouchPointList =
        surfacePointsToNoteTouchPoints(surfacePointList, viewModel.viewportManager)

    // --- Utilities ---

    fun isErasing(): Boolean = isErasingInProgress

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
        drawManager.updatePenProfile(penProfile)
    }

    fun clearDrawing() {
        shapesManager.clear()
        bitmapManager.clearDrawing()
    }
}
