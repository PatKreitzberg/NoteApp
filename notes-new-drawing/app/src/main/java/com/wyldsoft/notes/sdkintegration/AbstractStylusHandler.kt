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
import android.util.Log
import com.wyldsoft.notes.utils.surfacePointsToNoteTouchPoints

/**
 * Base class for stylus handlers. Coordinates drawing, erasing, geometry, and selection.
 * SDK-specific input wiring is done by subclasses (Onyx, Generic).
 * Geometry drawing delegated to [GeometryDrawingHandler].
 * Selection input delegated to [SelectionInputHandler].
 * Mode-based input dispatch delegated to [ModeInputRouter].
 */
abstract class AbstractStylusHandler(
    protected val surfaceView: SurfaceView,
    protected val viewModel: EditorViewModel,
    protected val bitmapManager: BitmapManager,
    private val getShapesManager: () -> ShapesManager,
    protected val displaySettingsRepository: DisplaySettingsRepository,
    protected val onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    protected val onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>, tiltX: List<Int>, tiltY: List<Int>) -> Unit,
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

    protected val textModeInputHandler = TextModeInputHandler(
        viewModel = viewModel,
        getShapesManager = getShapesManager
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
    /** Called before text mode begins input (e.g. to disable raw drawing render on Onyx). */
    protected open fun onTextModeBegin() {}
    /** Called before geometry mode begins input. */
    protected open fun onGeometryModeBegin() {}
    /** Called before pen/eraser drawing begins. */
    protected open fun onPenModeBegin() {}

    // --- ModeInputRouter (refactoring 1) ---

    /**
     * Creates a [ModeInputRouter] wired to this handler's methods.
     * Subclasses can use this in their SDK-specific input callbacks.
     */
    protected fun createModeInputRouter(): ModeInputRouter = ModeInputRouter(viewModel, object : ModeInputRouter.Callbacks {
        override fun onBeginPenOrEraser(touchPoint: TouchPoint?) {
            onPenModeBegin()
            beginDrawing(touchPoint)
        }

        override fun onBeginGeometry(touchPoint: TouchPoint) {
            onGeometryModeBegin()
            beginGeometryDrawing(touchPoint)
        }

        override fun onBeginSelection(touchPoint: TouchPoint?) {
            onDrawingStateChanged(true)
            selectionInputHandler.handleBegin(touchPoint)
        }

        override fun onBeginText(touchPoint: TouchPoint) {
            onTextModeBegin()
            handleTextBegin(touchPoint)
        }

        override fun onMovePen(touchPoint: TouchPoint) {
            if (isLineSnapped) { updateLineSnapMove(touchPoint); return }
            trackLineSnapMove(touchPoint)
        }

        override fun onMoveGeometry(touchPoint: TouchPoint) {
            updateGeometryPreview(touchPoint)
        }

        override fun onMoveSelection(touchPoint: TouchPoint) {
            handleSelectionMoveUpdate(touchPoint)
        }

        override fun onEndPenOrEraser(touchPointList: TouchPointList) {
            if (finalizeWithLineSnap(touchPointList)) return
            finalizeStroke(touchPointList)
        }

        override fun onEndGeometry(touchPointList: TouchPointList) {
            finalizeGeometryShape(touchPointList)
        }

        override fun onEndSelection(touchPointList: TouchPointList) {
            // SelectionInputHandler.handleEnd returns true if this was a cancel
            val wasCancelled = selectionInputHandler.handleEnd(touchPointList)
            onDrawingStateChanged(false)
            if (wasCancelled) onEndCancelledStroke()
        }

        override fun onEndCancelledStroke() {
            onDrawingStateChanged(false)
        }
    })

    /** Override in subclasses if SDK-specific text begin behavior is needed. */
    protected open fun handleTextBegin(touchPoint: TouchPoint) {
        textModeInputHandler.handleBegin(touchPoint)
    }

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
        val points = touchPointList.points
        if (points == null) {
            Log.w("DROPSTROKEBUG", "finalizeStroke: touchPointList.points is NULL — stroke silently dropped! " +
                "touchPointList.size=${touchPointList.size()}")
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel.endDrawing()
            return
        }
        if (points.isEmpty()) {
            Log.w("DROPSTROKEBUG", "finalizeStroke: touchPointList.points is EMPTY — stroke silently dropped!")
            isDrawingInProgress = false
            onDrawingStateChanged(false)
            viewModel.endDrawing()
            return
        }
        Log.d("DROPSTROKEBUG", "finalizeStroke: ${points.size} points, converting to note coords")
        val notePointList = surfacePointsToNoteTouchPoints(touchPointList, viewModel.viewportManager)
        Log.d("DROPSTROKEBUG", "finalizeStroke: notePointList size=${notePointList.size()}, creating shape")
        val newShape = drawManager.newShape(notePointList)
        Log.d("DROPSTROKEBUG", "finalizeStroke: shape created id=${newShape.id}, adding to shapesManager")
        shapesManager.addShape(newShape)
        Log.d("DROPSTROKEBUG", "finalizeStroke: shape added to shapesManager, total shapes=${shapesManager.shapes().size}")
        isDrawingInProgress = false
        onDrawingStateChanged(false)
        viewModel.endDrawing()
    }

    protected fun handleSelectorStrokeEnd(touchPointList: TouchPointList): Boolean {
        if (viewModel.uiState.value.mode is EditorMode.Select) {
            val wasCancelled = selectionInputHandler.handleEnd(touchPointList)
            onDrawingStateChanged(false)
            if (wasCancelled) {
                // SelectionInputHandler already called viewModel.cancelSelection()
            }
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
