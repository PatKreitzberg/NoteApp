package com.wyldsoft.notes.sdkintegration.onyx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.rendering.PaginationRendererToScreenRequest
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.gestures.GestureHandler
import android.view.MotionEvent
import com.wyldsoft.notes.rendering.RenderingUtils
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


open class OnyxDrawingActivity : BaseDrawingActivity() {
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null // can't use lateinit because of onResume being called
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null

    // Stylus handler for all stylus-related operations
    private lateinit var stylusHandler: OnyxStylusHandler

    // Gesture handler
    private lateinit var gestureHandler: GestureHandler//? = null

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
            shapesManager,
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

        // Observe tool changes to reconfigure touch helper for selection mode
        lifecycleScope.launch {
            editorViewModel.uiState.collect { state ->
                if (state.selectedTool == Tool.SELECTOR) {
                    reconfigureTouchHelperForSelection()
                } else {
                    // Restore normal pen profile rendering
                    reconfigureTouchHelper(editorViewModel.excludeRects.value, applyColor = true)
                }
            }
        }
    }

    override fun initializeStylusHandler() {
        stylusHandler = createOnyxStylusHandler()
        stylusHandler.updatePenProfile(currentPenProfile)
    }

    override fun initializeShapeMaanager() {
        shapesManager = ShapesManager(editorViewModel)
        forceScreenRefresh()
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
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        if (bitmap != null) {
            getRxManager().enqueue(
                PaginationRendererToScreenRequest(
                    surfaceView,
                    bitmap,
                    editorViewModel
                ), null)
        }
    }

    override fun onResumeDrawing() {
        Log.d(TAG, "onResumeDrawing")
        onyxTouchHelper?.setRawDrawingEnabled(true)
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
        reconfigureTouchHelper(editorViewModel.excludeRects.value, applyColor = true)
        // Re-render bitmap after closeRawDrawing()/openRawDrawing() to prevent recent strokes
        // from disappearing: the Onyx display refresh from openRawDrawing() can race with the
        // async RendererToScreenRequest queued by renderBitmapToScreen().
        bitmap?.let { renderToScreen(surfaceView, it) }
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

            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
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
            renderToScreen(surfaceView, bitmap)
        }.setSystemScreenOnListener {
            renderToScreen(surfaceView, bitmap)
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
        Log.d("DebugAug11.1", "Viewport changed, updating touch helper and bitmap, stylusHandler: $stylusHandler")
        // Recreate bitmap with new viewport transformation (includes selection overlay via recreateBitmapFromShapes)
        forceScreenRefresh()
    }

    override fun forceScreenRefresh() {
        RenderingUtils.enableScreenPost(surfaceView) // absolutely necessary to ensure the screen refreshes properly
        surfaceView.let { sv ->
            cleanSurfaceView(sv)
            // Recreate bitmap from all stored shapes
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(sv, it) }
        }
        // fixme: should we disable screen post after refresh?
        // RenderingUtils.enableScreenPost(surfaceView) with 0 instead of 1
    }
    
    override fun recreateBitmapFromShapes() {
        Log.d(TAG, "recreateBitmapFromShapes called from OnyxDrawingActivity")
        getOrCreateBitmap() // Ensure bitmap exists
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
        // Re-draw selection overlay so bounding box persists across redraws
        val selMgr = editorViewModel.selectionManager
        if (selMgr.hasSelection) {
            bitmapManager.drawSelectionOverlay(selMgr, editorViewModel.viewportManager)
        }
    }

    override fun initializeGestureHandler() {
        Log.d(TAG, "initializeGestureHandler called")
        // Initialize gesture handler with the current surface view
        gestureHandler = GestureHandler(this, surfaceView)
        // Set the viewport manager to the gesture handler
        gestureHandler.setViewportManager(editorViewModel.viewportManager)

        // Load gesture mappings from settings
        val app = application as com.wyldsoft.notes.ScrotesApp
        gestureHandler.gestureMappings = app.gestureSettingsRepository.mappings.value

        // Generic gesture action callback
        gestureHandler.onGestureAction = { action ->
            when (action) {
                com.wyldsoft.notes.gestures.GestureAction.RESET_ZOOM_AND_CENTER -> {
                    val vm = editorViewModel
                    val isPagination = vm.isPaginationEnabled.value
                    val pageWidth = vm.screenWidth.value.toFloat()
                    vm.viewportManager.resetZoomAndCenter(isPagination, pageWidth)
                    forceScreenRefresh()
                }
                com.wyldsoft.notes.gestures.GestureAction.TOGGLE_SELECTION_MODE -> {
                    val vm = editorViewModel
                    val currentTool = vm.uiState.value.selectedTool
                    if (currentTool == Tool.SELECTOR) {
                        vm.cancelSelection()
                    } else {
                        vm.selectTool(Tool.SELECTOR)
                    }
                    forceScreenRefresh()
                }
                else -> {
                    Log.d(TAG, "Gesture action $action handled inline")
                }
            }
        }
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