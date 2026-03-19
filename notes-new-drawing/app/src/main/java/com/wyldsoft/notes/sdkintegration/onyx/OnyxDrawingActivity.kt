package com.wyldsoft.notes.sdkintegration.onyx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import android.view.MotionEvent
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


open class OnyxDrawingActivity : BaseDrawingActivity() {
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null // can't use lateinit because of onResume being called
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null

    // Stylus handler for all stylus-related operations
    private lateinit var stylusHandler: OnyxStylusHandler

    // gestureHandler is declared in BaseDrawingActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NEW OnyxDrawingActivity")
    }

    fun createOnyxStylusHandler(): OnyxStylusHandler {
        Log.d(TAG, "createOnyxStylusHandler called")
        return OnyxStylusHandler(
            surfaceView,
            editorViewModel,
            getRxManager(),
            bitmapManager,
            getShapesManager = { shapesManager },
            displaySettingsRepository,
            onDrawingStateChanged = { isDrawing ->
                if (isDrawing) {
                    disableFingerTouch()
                } else {
                    enableFingerTouch()
                    // forceScreenRefresh() // todo should we force screen refresh here? I HIGHLY doubt it
                }
            },
            onShapeCompleted = { id, points, pressures, timestamps ->
                // add shape to NoteRepository
                onShapeCompleted(id, points, pressures, timestamps)
            },
            onShapeRemoved = {shapeId -> onShapeRemoved(shapeId)},
            onSetRawDrawingRenderEnabled = { enabled ->
                onyxTouchHelper?.setRawDrawingRenderEnabled(enabled)
            },
            onForceScreenRefresh = { forceScreenRefresh() }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun createTouchHelper() {
        Log.d(TAG, "createTouchHelper")

        val callback = stylusHandler.createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)

        // Set touch listener on the surface view to capture gestures
        surfaceView.setOnTouchListener { _, event ->
            // If any dropdown is open, close it on canvas touch and consume the event
            if (editorViewModel.isAnyDropdownOpen) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    editorViewModel.closeAllDropdowns()
                }
                return@setOnTouchListener true
            }

            // Check if any pointer is a stylus or eraser
            var hasStylusOrEraser = false
            for (i in 0 until event.pointerCount) {
                val toolType = event.getToolType(i)
                if (toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    toolType == MotionEvent.TOOL_TYPE_ERASER) {
                    hasStylusOrEraser = true
                    break
                }
            }

            // Check if erasing is in progress
            val isErasing = stylusHandler.isErasing()

            // Only handle events if no stylus/eraser is detected and not erasing
            if (!hasStylusOrEraser && !isErasing) {
                gestureHandler.onTouchEvent(event)
            } else {
                false // Let Onyx SDK handle stylus/eraser events
            }
        }

    }

    override fun setDrawingEnabled(enabled: Boolean) {
        if (!enabled) {
            onyxTouchHelper?.setRawDrawingEnabled(false)
        } else {
            val state = editorViewModel.uiState.value
            if (state.mode is EditorMode.Select) {
                reconfigureTouchHelperForSelection()
            } else {
                reconfigureTouchHelper(editorViewModel.excludeRects.value, applyColor = true)
            }
        }
    }

    override fun initializeStylusHandler() {
        stylusHandler = createOnyxStylusHandler()
        stylusHandler.updatePenProfile(currentPenProfile)
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

    override fun onResumeDrawing() {
        Log.d(TAG, "onResumeDrawing")
        if (!editorViewModel.isDrawingBlocked.value) {
            onyxTouchHelper?.setRawDrawingEnabled(true)
        }
    }

    override fun onPauseDrawing() {
        Log.d(TAG, "onPauseDrawing")
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun onCleanupSDK() {
        Log.d(TAG, "onCleanupSDK")
        onyxTouchHelper?.closeRawDrawing()
        stylusHandler.clearDrawing()
        gestureHandler.cleanup()
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        Log.d(TAG, "updateTouchHelperWithProfile")
        stylusHandler.updatePenProfile(currentPenProfile)
        if (editorViewModel.uiState.value.mode is EditorMode.Select) {
            reconfigureTouchHelperForSelection()
        } else {
            reconfigureTouchHelper(editorViewModel.excludeRects.value, applyColor = true)
        }
        // Re-render bitmap after closeRawDrawing()/openRawDrawing() to prevent recent strokes
        // from disappearing: the Onyx display refresh from openRawDrawing() can race with the
        // async RendererToScreenRequest queued by renderBitmapToScreen().
        Log.d("RefreshDebug", "OnyxDrawingActivity.updateTouchHelperWithProfile → renderBitmapToScreen")
        bitmapManager.renderBitmapToScreen("OnyxDrawingActivity.updateTouchHelperWithProfile")
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        Log.d(TAG, "updateTouchHelperExclusionZones")
        stylusHandler.updatePenProfile(currentPenProfile)
        reconfigureTouchHelper(excludeRects, applyColor = false)
    }

    /**
     * Reconfigure touch helper for selection mode: thin grey stroke for lasso visibility.
     */
    private fun reconfigureTouchHelperForSelection() {
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView.getLocalVisibleRect(limit)

            helper.setStrokeWidth(2f)
                .setStrokeColor(android.graphics.Color.GRAY)
                .setLimitRect(limit, ArrayList(editorViewModel.excludeRects.value))
                .openRawDrawing()

            if (!editorViewModel.isDrawingBlocked.value) {
                helper.setRawDrawingEnabled(true)
                helper.setRawDrawingRenderEnabled(true)
            }
        }
    }

    private fun reconfigureTouchHelper(excludeRects: List<Rect>, applyColor: Boolean) {
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView.getLocalVisibleRect(limit)

            val strokeConfig = helper.setStrokeWidth(currentPenProfile.strokeWidth)
            if (applyColor) {
                strokeConfig.setStrokeColor(currentPenProfile.getColorAsInt())
            }
            strokeConfig.setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()

            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            if (!editorViewModel.isDrawingBlocked.value) {
                helper.setRawDrawingEnabled(true)
                helper.setRawDrawingRenderEnabled(true)
            }
        }
    }

    override fun initializeDeviceReceiver() {
        Log.d(TAG, "initializeDeviceReceiver")
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            Log.d("RefreshDebug", "OnyxDrawingActivity.notificationPanelChange(open=$open) → renderBitmapToScreen")
            bitmapManager.renderBitmapToScreen("OnyxDrawingActivity.notificationPanelChange")
        }.setSystemScreenOnListener {
            Log.d("RefreshDebug", "OnyxDrawingActivity.screenOn → renderBitmapToScreen")
            bitmapManager.renderBitmapToScreen("OnyxDrawingActivity.screenOn")
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }
    
    override fun refreshUIChrome() {
        // Force e-ink display to refresh the Compose UI layer (toolbar buttons, etc.)
        RenderingUtils.enableScreenPost(window.decorView)
        window.decorView.postInvalidate()
    }

    override fun onViewportChanged() {
        Log.d(TAG, "Viewport changed, refreshing")
        Log.d("RefreshDebug", "OnyxDrawingActivity.onViewportChanged → forceScreenRefresh")
        // Recreate bitmap with new viewport transformation (includes selection overlay via recreateBitmapFromShapes)
        forceScreenRefresh()
    }

    override fun forceScreenRefresh() {
        Log.d("RefreshDebug", "OnyxDrawingActivity.forceScreenRefresh → enableScreenPost + super.forceScreenRefresh")
        RenderingUtils.enableScreenPost(surfaceView)
        super.forceScreenRefresh()
    }
    
    override fun initializeBitmapManager(sv: SurfaceView, vm: EditorViewModel) {
        Log.d(TAG, "BitmapManager initialized with current bitmap")
        this.bitmapManager = BitmapManager(
            surfaceView = sv,
            viewModel = vm,
            rxManager = getRxManager(),
            getBitmap = { getOrCreateBitmap() },
            getBitmapCanvas = { bitmapCanvas }
        )
    }
    
    override fun setViewModel(viewModel: EditorViewModel) {
        super.setViewModel(viewModel)
            Log.d(TAG, "setViewModel called")

    }

    private fun getRxManager(): RxManager {
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }
}