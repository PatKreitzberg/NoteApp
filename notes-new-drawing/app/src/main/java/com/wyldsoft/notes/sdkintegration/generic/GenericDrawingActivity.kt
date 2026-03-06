package com.wyldsoft.notes.sdkintegration.generic

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import com.wyldsoft.notes.drawing.DrawingManager
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.shapemanagement.ShapesManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Drawing activity for non-Onyx Android devices.
 * Uses standard Android APIs for stylus input and screen rendering.
 */
open class GenericDrawingActivity : BaseDrawingActivity() {

    private lateinit var stylusHandler: GenericStylusHandler
    // gestureHandler is declared in BaseDrawingActivity
    private var deviceReceiver: GenericDeviceReceiverWrapper? = null
    private var drawingManager: DrawingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NEW GenericDrawingActivity")
    }

    override fun initializeStylusHandler() {
        stylusHandler = GenericStylusHandler(
            surfaceView,
            editorViewModel,
            bitmapManager,
            shapesManager,
            onDrawingStateChanged = { isDrawing ->
                // No finger touch enable/disable needed on generic devices
            },
            onShapeCompleted = { id, points, pressures, timestamps ->
                onShapeCompleted(id, points, pressures, timestamps)
            },
            onShapeRemoved = { shapeId -> onShapeRemoved(shapeId) },
            onForceScreenRefresh = { forceScreenRefresh() }
        )
        stylusHandler.updatePenProfile(currentPenProfile)
    }

    override fun initializeShapeMaanager() {
        shapesManager = ShapesManager(editorViewModel)
        forceScreenRefresh()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun createTouchHelper() {
        // Set touch listener for both stylus and finger input
        surfaceView.setOnTouchListener { _, event ->
            val toolType = event.getToolType(0)
            val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
            val isEraser = toolType == MotionEvent.TOOL_TYPE_ERASER

            if (isStylus || isEraser) {
                // Stylus/eraser goes to stylus handler
                stylusHandler.onTouchEvent(event)
            } else {
                // Finger: check if drawing tool is selected (finger draws on non-Onyx)
                val tool = editorViewModel.uiState.value.selectedTool
                if (tool == Tool.PEN || tool == Tool.SELECTOR) {
                    stylusHandler.onTouchEvent(event)
                } else {
                    gestureHandler.onTouchEvent(event)
                }
            }
        }

        // Observe tool changes
        lifecycleScope.launch {
            editorViewModel.uiState.collect { state ->
                // No touch helper reconfiguration needed on generic devices
            }
        }
    }

    override fun initializeBitmapManager(sv: SurfaceView, vm: EditorViewModel) {
        Log.d(TAG, "BitmapManager initialized (no RxManager)")
        this.bitmapManager = BitmapManager(
            surfaceView = sv,
            viewModel = vm,
            rxManager = null,
            getBitmap = { getOrCreateBitmap() },
            getBitmapCanvas = { bitmapCanvas }
        )
    }

    override fun createDeviceReceiver(): BaseDeviceReceiver {
        deviceReceiver = GenericDeviceReceiverWrapper()
        return deviceReceiver!!
    }

    override fun enableFingerTouch() {
        // No-op on generic devices
    }

    override fun disableFingerTouch() {
        // No-op on generic devices
    }

    override fun cleanSurfaceView(surfaceView: SurfaceView): Boolean {
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        if (bitmap == null) return
        val viewRect = RenderingUtils.checkSurfaceView(surfaceView) ?: return
        val canvas = surfaceView.holder.lockCanvas() ?: return
        try {
            RenderingUtils.renderBackground(canvas, viewRect)
            RenderingUtils.drawRendererContent(bitmap, canvas)

            // Draw page separators if pagination is enabled
            if (editorViewModel.isPaginationEnabled.value) {
                if (drawingManager == null) {
                    drawingManager = DrawingManager(editorViewModel.viewportManager)
                }
                drawingManager?.drawPageSeparators(
                    canvas = canvas,
                    screenWidth = editorViewModel.screenWidth.value,
                    pageHeight = editorViewModel.pageHeight.value,
                    isPaginationEnabled = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onResumeDrawing() {
        Log.d(TAG, "onResumeDrawing (generic)")
    }

    override fun onPauseDrawing() {
        Log.d(TAG, "onPauseDrawing (generic)")
    }

    override fun onCleanupSDK() {
        Log.d(TAG, "onCleanupSDK (generic)")
        stylusHandler.clearDrawing()
        gestureHandler.cleanup()
    }

    override fun updateActiveSurface() {
        // No touch helper reconfiguration needed
    }

    override fun updateTouchHelperWithProfile() {
        stylusHandler.updatePenProfile(currentPenProfile)
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        // No-op on generic devices — no Onyx touch helper exclusion zones
    }

    override fun initializeDeviceReceiver() {
        Log.d(TAG, "initializeDeviceReceiver (generic)")
        val receiver = createDeviceReceiver() as GenericDeviceReceiverWrapper
        receiver.enable(this, true)
        receiver.setSystemScreenOnListener {
            renderToScreen(surfaceView, bitmap)
        }
    }

    override fun onCleanupDeviceReceiver() {
        deviceReceiver?.enable(this, false)
    }

    override fun onViewportChanged() {
        forceScreenRefresh()
    }

    override fun forceScreenRefresh() {
        surfaceView.let { sv ->
            // Note: cleanSurfaceView() is intentionally omitted here. The renderToScreen()
            // call already draws a white background via renderBackground() before drawing the
            // bitmap, so a separate white clear would cause a visible flash between frames.
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    override fun setViewModel(viewModel: EditorViewModel) {
        super.setViewModel(viewModel)
        Log.d(TAG, "setViewModel called (generic)")
    }
}
