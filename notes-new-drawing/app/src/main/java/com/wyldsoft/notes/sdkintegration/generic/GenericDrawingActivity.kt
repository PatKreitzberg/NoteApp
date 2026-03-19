package com.wyldsoft.notes.sdkintegration.generic

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import com.wyldsoft.notes.presentation.viewmodel.DrawTool
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity

/**
 * Drawing activity for non-Onyx Android devices.
 * Uses standard Android APIs for stylus input and screen rendering.
 */
open class GenericDrawingActivity : BaseDrawingActivity() {

    private lateinit var stylusHandler: GenericStylusHandler
    // gestureHandler is declared in BaseDrawingActivity
    private var deviceReceiver: GenericDeviceReceiverWrapper? = null
    private var isDrawingEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NEW GenericDrawingActivity")
    }

    override fun initializeStylusHandler() {
        stylusHandler = GenericStylusHandler(
            surfaceView,
            editorViewModel,
            bitmapManager,
            getShapesManager = { shapesManager },
            displaySettingsRepository,
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

    @SuppressLint("ClickableViewAccessibility")
    override fun createTouchHelper() {
        // Set touch listener for both stylus and finger input
        surfaceView.setOnTouchListener { _, event ->
            // If drawing is blocked, consume the touch and dismiss any dismissible UI
            if (editorViewModel.isDrawingBlocked.value) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    editorViewModel.closeAllDropdowns()
                }
                return@setOnTouchListener true
            }
            if (!isDrawingEnabled) return@setOnTouchListener false
            val toolType = event.getToolType(0)
            val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
            val isEraser = toolType == MotionEvent.TOOL_TYPE_ERASER

            if (isStylus || isEraser) {
                // Stylus/eraser goes to stylus handler
                stylusHandler.onTouchEvent(event)
            } else {
                // Finger: check if drawing tool is selected (finger draws on non-Onyx)
                val mode = editorViewModel.uiState.value.mode
                if (mode is EditorMode.Draw && mode.drawTool == DrawTool.PEN || mode is EditorMode.Select) {
                    stylusHandler.onTouchEvent(event)
                } else {
                    gestureHandler.onTouchEvent(event)
                }
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

    override fun setDrawingEnabled(enabled: Boolean) {
        isDrawingEnabled = enabled
    }

    override fun enableFingerTouch() {
        // No-op on generic devices
    }

    override fun disableFingerTouch() {
        // No-op on generic devices
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
            Log.d("RefreshDebug", "GenericDrawingActivity.screenOn → renderBitmapToScreen")
            bitmapManager.renderBitmapToScreen("GenericDrawingActivity.screenOn")
        }
    }

    override fun onCleanupDeviceReceiver() {
        deviceReceiver?.enable(this, false)
    }

    override fun onViewportChanged() {
        Log.d("RefreshDebug", "GenericDrawingActivity.onViewportChanged → forceScreenRefresh")
        forceScreenRefresh()
    }

    override fun setViewModel(viewModel: EditorViewModel) {
        super.setViewModel(viewModel)
        Log.d(TAG, "setViewModel called (generic)")
    }
}
