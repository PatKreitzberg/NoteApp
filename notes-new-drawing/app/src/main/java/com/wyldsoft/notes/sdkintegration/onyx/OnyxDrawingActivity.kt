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
import androidx.lifecycle.lifecycleScope
import com.onyx.android.sdk.api.device.epd.EpdController
import kotlinx.coroutines.launch
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.rendering.BitmapManager


open class OnyxDrawingActivity : BaseDrawingActivity() {
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null

    // Stylus handler for all stylus-related operations
    private var stylusHandler: OnyxStylusHandler? = null
    
    // Gesture handler
    private var gestureHandler: GestureHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun initializeSDK() {
        // Onyx-specific initialization
        // Note: stylus handler will be created in createTouchHelper when surfaceView is available
        
        // Subscribe to current note changes to load existing shapes
        lifecycleScope.launch {
            editorViewModel?.currentNote?.collect { note ->
                note?.let { 
                    loadShapesFromNote(it)
                    // Viewport state is restored in ViewModel, just need to refresh
                    forceScreenRefresh()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun createTouchHelper(surfaceView: SurfaceView) {
        // Create stylus handler now that surfaceView is available
        if (stylusHandler == null) {
            Log.d("DebugAug11.1", "creating new OnyxStylusHandler. vewModel is null = ${editorViewModel == null}")
            stylusHandler = OnyxStylusHandler(
                surfaceView,
                editorViewModel,
                getRxManager(),
                bitmapManager,
                onDrawingStateChanged = { isDrawing ->
                    if (isDrawing) {
                        disableFingerTouch()
                    } else {
                        enableFingerTouch()
                        forceScreenRefresh()
                    }
                },
                onShapeCompleted = { points, pressures ->
                    onShapeCompleted(points, pressures)
                },
                onBitmapChanged = {
                    forceScreenRefresh()
                },
                getBitmap = { getOrCreateBitmap() },
                getBitmapCanvas = { bitmapCanvas }
            )
            stylusHandler?.updatePenProfile(currentPenProfile)
        }
        
        val callback = stylusHandler?.createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)
        
        // Initialize gesture handler (viewportManager will be set later when viewModel is available)
        gestureHandler = GestureHandler(this, surfaceView)
        
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
            val isErasing = stylusHandler?.isErasing() ?: false
            
            // Only handle events if no stylus/eraser is detected and not erasing
            if (!hasStylusOrEraser && !isErasing) {
                gestureHandler?.onTouchEvent(event) ?: false
            } else {
                if (isErasing) {
                    Log.d(TAG, "Erasing in progress, ignoring gesture handling")
                } else if (hasStylusOrEraser) {
                    Log.d(TAG, "Stylus/eraser detected, ignoring gesture handling")
                }
                false // Let Onyx SDK handle stylus/eraser events
            }
        }
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
        onyxTouchHelper?.setRawDrawingEnabled(true)
    }

    override fun onPauseDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        stylusHandler?.clearDrawing()
        stylusHandler = null
        gestureHandler?.cleanup()
        gestureHandler = null
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        stylusHandler?.updatePenProfile(currentPenProfile)
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            val excludeRects = editorViewModel?.excludeRects?.value ?: emptyList()
            Log.d("ExclusionRects", "updateTouchHelperWithProfile Current exclusion rects ${excludeRects.size}")

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
        stylusHandler?.updatePenProfile(currentPenProfile)
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            Log.d("ExclusionRects", "updateTouchHelperExclusionZones Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()
            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())

            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun initializeDeviceReceiver() {
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }.setSystemScreenOnListener {
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }
    
    override fun onViewportChanged() {
        Log.d("DebugAug11.1", "Viewport changed, updating touch helper and bitmap, stylusHandler: $stylusHandler")
        // Recreate bitmap with new viewport transformation
        bitmapManager.recreateBitmapFromShapes(stylusHandler?.drawnShapes)
        // Request screen refresh to show the updated shapes
        forceScreenRefresh()
    }

    override fun forceScreenRefresh() {
        EpdController.enablePost(surfaceView, 1) // this is absolutely necessary to ensure the screen refreshes properly
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            // Recreate bitmap from all stored shapes
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(sv, it) }
        }
    }
    
    override fun recreateBitmapFromShapes() {
        getOrCreateBitmap() // Ensure bitmap exists
        bitmapManager.recreateBitmapFromShapes(stylusHandler?.drawnShapes)
    }

    override fun initializeBitmapManager(sv: SurfaceView, vm: EditorViewModel) {
        Log.d(TAG, "BitmapManager initialized with current bitmap")
        bitmapManager = BitmapManager(
            surfaceView = sv,
            viewModel = vm,
            rxManager = getRxManager(),
            getBitmap = { getOrCreateBitmap() },
            getBitmapCanvas = { bitmapCanvas }
        )
    }
    
    override fun setViewModel(viewModel: EditorViewModel) {
        super.setViewModel(viewModel)
        // Update gesture handler with viewport manager now that it's available
        gestureHandler?.setViewportManager(viewModel.viewportManager)
        Log.d(TAG, "Updated GestureHandler with ViewportManager")
        
        // Update stylus handler if it exists
        if (stylusHandler == null && surfaceView != null) {
            Log.d("DebugAug11.1", "creating new OnyxStylusHandler and surfaceView is not null. vewModel is null = ${viewModel == null}")

            stylusHandler = OnyxStylusHandler(
                surfaceView,
                viewModel,
                getRxManager(),
                bitmapManager,
                onDrawingStateChanged = { isDrawing ->
                    if (isDrawing) {
                        disableFingerTouch()
                    } else {
                        enableFingerTouch()
                        forceScreenRefresh()
                    }
                },
                onShapeCompleted = { points, pressures ->
                    onShapeCompleted(points, pressures)
                },
                onBitmapChanged = {
                    forceScreenRefresh()
                },
                getBitmap = { getOrCreateBitmap() },
                getBitmapCanvas = { bitmapCanvas }
            )
            stylusHandler?.updatePenProfile(currentPenProfile)
        }
    }

    private fun getRxManager(): RxManager {
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }

    // Add method to clear all drawings
    fun clearDrawing() {
        stylusHandler?.clearDrawing()
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
        }
    }
    
    // Load shapes from note into the drawing
    private fun loadShapesFromNote(note: com.wyldsoft.notes.domain.models.Note) {
        // Clear existing shapes
        stylusHandler?.drawnShapes?.clear()
        
        // Convert domain shapes to SDK shapes
        for (domainShape in note.shapes) {
            val sdkShape = stylusHandler?.convertDomainShapeToSdkShape(domainShape)
            sdkShape?.let { stylusHandler?.drawnShapes?.add(it) }
        }
        
        // Recreate bitmap with all shapes
        forceScreenRefresh()
    }
    
}