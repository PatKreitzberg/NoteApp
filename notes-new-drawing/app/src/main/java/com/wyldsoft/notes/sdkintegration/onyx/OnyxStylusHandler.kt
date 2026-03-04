package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.PointF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.sdkintegration.AbstractStylusHandler

/**
 * Onyx-specific stylus handler. Receives input via Onyx SDK's RawInputCallback
 * and delegates shared logic to AbstractStylusHandler. Overrides hooks to
 * toggle Onyx's raw drawing render mode during selection transforms.
 */
class OnyxStylusHandler(
    surfaceView: SurfaceView,
    viewModel: EditorViewModel,
    rxManager: RxManager,
    bitmapManager: BitmapManager,
    shapesManager: ShapesManager,
    onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    onShapeRemoved: (shapeId: String) -> Unit,
    private val onSetRawDrawingRenderEnabled: (Boolean) -> Unit,
    onForceScreenRefresh: () -> Unit
) : AbstractStylusHandler(
    surfaceView, viewModel, bitmapManager, shapesManager,
    onDrawingStateChanged, onShapeCompleted, onShapeRemoved,
    onForceScreenRefresh, rxManager
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    init {
        Log.d(TAG, "NEW OnyxStylusHandler")
    }

    // --- Onyx-specific hooks ---

    override fun onSelectionTransformStarted() {
        onSetRawDrawingRenderEnabled(false)
    }

    override fun onLassoSelectionCompleted() {
        onSetRawDrawingRenderEnabled(false)
    }

    override fun onLassoStarted() {
        onSetRawDrawingRenderEnabled(true)
    }

    // --- Onyx SDK callback ---

    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.SELECTOR) {
                beginSelectionStroke(touchPoint)
                return
            }
            beginDrawing()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            isDrawingInProgress = false
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            if (refreshCount < REFRESH_COUNT_LIMIT) {
                refreshCount++
                return
            }
            refreshCount = 0

            if (touchPoint == null) return
            val tool = viewModel.uiState.value.selectedTool
            if (tool != Tool.SELECTOR) return
            if (!selectionManager.hasSelection) return

            handleSelectionMoveUpdate(touchPoint)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            if (handleCancelledStroke()) return
            if (touchPointList != null && handleSelectorStrokeEnd(touchPointList)) return

            touchPointList?.let { finalizeStroke(it) }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            beginErasing()
            Log.d(TAG, "Erasing started")
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            endErasing()
            Log.d(TAG, "Erasing ended")
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            touchPointList?.let { erasePointList ->
                val noteErasePointList = convertTouchPointListToNoteCoordinates(erasePointList)
                finalizeErase(noteErasePointList)
            }
        }
    }
}
