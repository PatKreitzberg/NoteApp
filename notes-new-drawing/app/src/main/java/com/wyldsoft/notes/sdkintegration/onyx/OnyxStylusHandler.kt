package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.PointF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.sdkintegration.AbstractStylusHandler
import com.wyldsoft.notes.settings.DisplaySettingsRepository

/**
 * Onyx-specific stylus handler. Receives input via Onyx SDK's RawInputCallback
 * and delegates mode-based dispatch to [ModeInputRouter].
 */
class OnyxStylusHandler(
    surfaceView: SurfaceView,
    viewModel: EditorViewModel,
    rxManager: RxManager,
    bitmapManager: BitmapManager,
    getShapesManager: () -> ShapesManager,
    displaySettingsRepository: DisplaySettingsRepository,
    onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>, tiltX: List<Int>, tiltY: List<Int>) -> Unit,
    onShapeRemoved: (shapeId: String) -> Unit,
    private val onSetRawDrawingRenderEnabled: (Boolean) -> Unit,
    onForceScreenRefresh: () -> Unit
) : AbstractStylusHandler(
    surfaceView, viewModel, bitmapManager, getShapesManager, displaySettingsRepository,
    onDrawingStateChanged, onShapeCompleted, onShapeRemoved,
    onForceScreenRefresh, rxManager
) {
    companion object {
        private const val TAG = "OnyxStylusHandler"
    }

    private val modeRouter = createModeInputRouter()

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

    override fun onLineSnapActivated() {
        onSetRawDrawingRenderEnabled(false)
    }

    override fun onTextModeBegin() {
        onSetRawDrawingRenderEnabled(false)
    }

    override fun onGeometryModeBegin() {
        onSetRawDrawingRenderEnabled(false)
    }

    override fun onPenModeBegin() {
        onSetRawDrawingRenderEnabled(true)
    }

    // --- Onyx SDK callback (uses ModeInputRouter for dispatch) ---

    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d("DROPSTROKEBUG", "onBeginRawDrawing: b=$b, touchPoint=${touchPoint != null}")
            modeRouter.routeBegin(touchPoint)
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d("DROPSTROKEBUG", "onEndRawDrawing: b=$b, isGeometry=$isGeometryDrawingInProgress, isLineSnapped=$isLineSnapped")
            if (!isGeometryDrawingInProgress && !isLineSnapped) {
                isDrawingInProgress = false
            }
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            modeRouter.routeMove(touchPoint)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d("DROPSTROKEBUG", "onRawDrawingTouchPointListReceived: " +
                "touchPointList=${touchPointList != null}, " +
                "size=${touchPointList?.size() ?: "null"}, " +
                "points=${touchPointList?.points?.size ?: "null"}")



            modeRouter.routeEnd(touchPointList)
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
