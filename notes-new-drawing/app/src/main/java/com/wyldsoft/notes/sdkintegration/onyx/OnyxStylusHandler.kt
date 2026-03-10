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
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import com.wyldsoft.notes.sdkintegration.AbstractStylusHandler
import com.wyldsoft.notes.settings.DisplaySettingsRepository

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
    displaySettingsRepository: DisplaySettingsRepository,
    onDrawingStateChanged: (isDrawing: Boolean) -> Unit,
    onShapeCompleted: (id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) -> Unit,
    onShapeRemoved: (shapeId: String) -> Unit,
    private val onSetRawDrawingRenderEnabled: (Boolean) -> Unit,
    onForceScreenRefresh: () -> Unit
) : AbstractStylusHandler(
    surfaceView, viewModel, bitmapManager, shapesManager, displaySettingsRepository,
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

    override fun onLineSnapActivated() {
        onSetRawDrawingRenderEnabled(false)
    }

    // --- Text shape hit testing ---

    private fun findTextShapeAtNotePoint(noteX: Float, noteY: Float): TextShape? {
        return shapesManager.shapes()
            .filterIsInstance<TextShape>()
            .firstOrNull { shape ->
                shape.updateShapeRect()
                shape.boundingRect?.contains(noteX, noteY) == true
            }
    }

    // --- Onyx SDK callback ---

    fun createOnyxCallback(): RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.SELECTOR) {
                beginSelectionStroke(touchPoint)
                return
            }
            if (tool == Tool.GEOMETRY) {
                onSetRawDrawingRenderEnabled(false) // We handle rendering for geometry
                touchPoint?.let { beginGeometryDrawing(it) }
                return
            }
            if (tool == Tool.TEXT) {
                onSetRawDrawingRenderEnabled(false)
                touchPoint?.let {
                    val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(it.x, it.y)
                    val hitShape = findTextShapeAtNotePoint(notePoint.x, notePoint.y)
                    if (hitShape != null) {
                        val anchor = hitShape.touchPointList?.points?.firstOrNull()
                        val anchorX = anchor?.x ?: notePoint.x
                        val anchorY = anchor?.y ?: notePoint.y
                        viewModel.beginEditingTextShape(
                            shapeId = hitShape.id,
                            anchorNoteX = anchorX,
                            anchorNoteY = anchorY,
                            existingText = hitShape.text,
                            existingFontSize = hitShape.fontSize,
                            existingFontFamily = hitShape.fontFamily,
                            existingColor = hitShape.strokeColor
                        )
                    } else {
                        viewModel.beginTextInput(notePoint.x, notePoint.y)
                    }
                }
                return
            }
            onSetRawDrawingRenderEnabled(true) // Re-enable in case a prior snap had disabled it
            beginDrawing(touchPoint)
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            if (!isGeometryDrawingInProgress && !isLineSnapped) {
                isDrawingInProgress = false
            }
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.TEXT) return
            if (tool == Tool.GEOMETRY) {
                touchPoint?.let { updateGeometryPreview(it) }
                return
            }

            if (tool == Tool.PEN && touchPoint != null) {
                if (isLineSnapped) {
                    updateLineSnapMove(touchPoint)
                    return
                }
                trackLineSnapMove(touchPoint)
            }

            if (refreshCount < REFRESH_COUNT_LIMIT) {
                refreshCount++
                return
            }
            refreshCount = 0

            if (touchPoint == null) return
            if (tool != Tool.SELECTOR) return
            if (!selectionManager.hasSelection) return

            handleSelectionMoveUpdate(touchPoint)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            val tool = viewModel.uiState.value.selectedTool
            if (tool == Tool.TEXT) return
            if (tool == Tool.GEOMETRY) {
                touchPointList?.let { finalizeGeometryShape(it) }
                return
            }
            if (handleCancelledStroke()) return
            if (touchPointList != null && handleSelectorStrokeEnd(touchPointList)) return
            if (touchPointList != null && finalizeWithLineSnap(touchPointList)) return

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
