package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.onyx.android.sdk.api.device.epd.EpdController
import com.wyldsoft.notes.editor.AppMode

/**
 * Onyx SDK implementation of BaseDrawingActivity. This is the core drawing engine.
 *
 * Drawing flow:
 *   Onyx TouchHelper delivers pen strokes via RawInputCallback ->
 *   onRawDrawingTouchPointListReceived() -> DrawingPipeline.drawScribbleToBitmap() ->
 *   ShapeFactory creates a typed Shape -> shape is rendered to the offscreen bitmap
 *   -> RendererToScreenRequest blits bitmap to SurfaceView via RxManager.
 *
 * Erasing flow:
 *   onRawErasingTouchPointListReceived() -> DrawingPipeline.handleErasing() ->
 *   EraseManager.findIntersectingShapes() hit-tests erase points against stored shapes ->
 *   matching shapes removed -> PartialEraseRefresh redraws just the affected region.
 *
 * Manages the Onyx TouchHelper lifecycle (open/close raw drawing, stroke style),
 * finger-touch suppression during pen input, and the GlobalDeviceReceiver for
 * system UI events. Extended by MainActivity as the app entry point.
 */
open class OnyxDrawingActivity : BaseDrawingActivity() {
    override var TAG = "OnyxDrawingActivity"
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null
    private val drawingPipeline = DrawingPipeline(viewportManager)

    override fun initializeSDK() {
        // Onyx-specific initialization
    }

    override fun createTouchHelper(surfaceView: SurfaceView) {
        val callback = createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)
    }

    override fun createDeviceReceiver(): BaseDeviceReceiver {
        onyxDeviceReceiver = GlobalDeviceReceiver()
        return OnyxDeviceReceiverWrapper(onyxDeviceReceiver!!)
    }

    override fun enableFingerTouch() {
        TouchUtils.enableFingerTouch(applicationContext)
    }

    override fun disableFingerTouch() {
        TouchUtils.disableFingerTouch(applicationContext)
    }

    override fun cleanSurfaceView(surfaceView: SurfaceView): Boolean {
        Log.d(TAG, "cleanSurfaceView")
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        Log.d(TAG, "renderToScreen")
        if (bitmap != null) {
            getRxManager().enqueue(
                RendererToScreenRequest(surfaceView, bitmap), null
            )
        }
    }

    override fun onResumeDrawing() {
        if (isInMode(AppMode.DRAWING)) {
            onyxTouchHelper?.setRawDrawingEnabled(true)
        }
    }

    override fun onPauseDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun enterNewMode(mode: AppMode) {
        Log.d(TAG, "enterNewMode $mode")
        if (mode == AppMode.DRAWING) updateTouchHelperWithProfile()
    }

    override fun exitCurrentMode(mode: AppMode) {
        Log.d(TAG, "exitCurrentMode $mode")
        if (isInMode(mode)) return
        if (mode == AppMode.DRAWING) disableRawDrawing()
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        drawingPipeline.clearShapes()
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        Log.d(TAG, "updateTouchHelperWithProfile mode=${EditorState.currentMode.value}")
        if (EditorState.currentMode.value == AppMode.SETTINGS) {
            Log.d(TAG, "updateTouchHelperWithProfile skipped — SETTINGS mode, will apply on mode exit")
            return
        }
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            val excludeRects = EditorState.getCurrentExclusionRects()

            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setStrokeColor(currentPenProfile.getColorAsInt())
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()
            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        Log.d(TAG, "updateTouchHelperExclusionZones")
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()
            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun initializeDeviceReceiver() {
        Log.d(TAG, "initializeDeviceReceiver")
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            surfaceView?.let { sv -> renderToScreen(sv, bitmap) }
        }.setSystemScreenOnListener {
            surfaceView?.let { sv -> renderToScreen(sv, bitmap) }
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }

    override fun forceScreenRefresh() {
        Log.d(TAG, "forceScreenRefresh() called")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas

            EpdController.enablePost(sv, 1)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    override fun enableRawDrawing() {
        onyxTouchHelper?.setRawDrawingRenderEnabled(true)
    }

    override fun disableRawDrawing() {
        onyxTouchHelper?.setRawDrawingRenderEnabled(false)
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    private fun getRxManager(): RxManager {
        Log.d(TAG, "getRxManager")
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }

    private fun createOnyxCallback() = object : com.onyx.android.sdk.pen.RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawDrawing mode=${EditorState.currentMode.value}")
            isDrawingInProgress = true
            disableFingerTouch()

            if (EditorState.currentMode.value == AppMode.SETTINGS) {
                Log.d(TAG, "Stylus down in SETTINGS mode — will skip stroke and dismiss menu")
                skipNextStroke = true
            }
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawDrawing")
            isDrawingInProgress = false
            enableFingerTouch()
            if (skipNextStroke) {
                unsetSkipStroke()
            }
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {}

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawDrawingTouchPointListReceived skipNextStroke=$skipNextStroke")
            if (!skipNextStroke) {
                touchPointList?.points?.let { points ->
                    if (!isDrawingInProgress) {
                        isDrawingInProgress = true
                    }
                    handleDrawing(points, touchPointList)
                }
            } else {
                Log.d(TAG, "Stroke skipped, dismissing settings")
                EditorState.emitDismissSettings()
                EditorState.setMode(AppMode.DRAWING)
            }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawErasing")
            isErasingInProgress = true
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawErasing")
            isErasingInProgress = false
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawErasingTouchPointListReceived")
            if (!isErasingInProgress) {
                isErasingInProgress = true
            }
            touchPointList?.let { handleErasing(it) }
        }
    }

    private fun handleDrawing(points: List<TouchPoint>, touchPointList: TouchPointList) {
        Log.d(TAG, "handleDrawing")
        surfaceView?.let { sv ->
            createDrawingBitmap()
            bitmap?.let { bmp ->
                drawingPipeline.drawScribbleToBitmap(points, touchPointList, bmp, currentPenProfile)
                renderToScreen(sv, bitmap)
            }
        }
    }

    private fun handleErasing(erasePointList: TouchPointList) {
        Log.d(TAG, "handleErasing")
        surfaceView?.let { sv ->
            val newState = drawingPipeline.handleErasing(
                erasePointList, bitmap, bitmapCanvas, sv, getRxManager()
            ) { com.wyldsoft.notes.rendering.BitmapState(bitmap!!, bitmapCanvas!!) }
            if (newState != null) {
                bitmap = newState.bitmap
                bitmapCanvas = newState.canvas
            }
        }
    }
}
