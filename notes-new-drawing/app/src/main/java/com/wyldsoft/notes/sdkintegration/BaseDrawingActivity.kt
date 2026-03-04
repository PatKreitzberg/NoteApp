package com.wyldsoft.notes.sdkintegration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.editor.EditorView
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.drawing.DrawingActivityInterface
import android.graphics.PointF
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking


abstract class BaseDrawingActivity : ComponentActivity(), DrawingActivityInterface {
    protected val TAG = "BaseDrawingActivity"

    // Common drawing state
    protected var paint = Paint()
    protected var bitmap: Bitmap? = null
    protected var bitmapCanvas: Canvas? = null
    protected var currentPenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    // Need EditorView to be created in order for them to be initialized.
    protected lateinit var surfaceView: SurfaceView // lateinite instead of ? = null if I am sure it will be initialized before use
    protected lateinit var bitmapManager: BitmapManager // lateinite instead of ? = null if I am sure it will be initialized before use
    protected lateinit var editorViewModel: EditorViewModel // lateinite instead of ? = null if I am sure it will be initialized before use
    protected lateinit var shapesManager: ShapesManager

    // Abstract methods that must be implemented by SDK-specific classes
    abstract fun initializeShapeMaanager()
    abstract fun initializeGestureHandler()
    abstract fun initializeStylusHandler()
    abstract fun createDeviceReceiver(): BaseDeviceReceiver
    abstract fun enableFingerTouch()
    abstract fun disableFingerTouch()
    abstract fun cleanSurfaceView(surfaceView: SurfaceView): Boolean
    abstract fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?)

    // Template methods - common implementation for all SDKs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as com.wyldsoft.notes.ScrotesApp
        val noteRepository = app.noteRepository
        val notebookRepository = app.notebookRepository
        val noteId = intent.getStringExtra("noteId") ?: return
        val notebookId = intent.getStringExtra("notebookId")

        // Must complete before creating UI so ShapesManager reads the correct note
        runBlocking(Dispatchers.IO) {
            noteRepository.setCurrentNote(noteId)
        }

        // Create EditorViewModel with repositories
        Log.d(TAG, "Setting EditorView as content with noteId: $noteId, notebookId: $notebookId")
        editorViewModel = EditorViewModel(noteRepository, notebookRepository, app.htrRunManager, notebookId)

        // Create the UI
        setEditorViewAsContent()
        // setEditorViewAsContent will create a SurfaceView and then call handleSurfaceViewCreated
        // which will initialize rest of items.
    }

    fun setEditorViewAsContent() {
        Log.d(TAG, "ViewModel created")
        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditorView(
                        editorViewModel,
                        onSurfaceViewCreated = { sv, vm ->
                            Log.d(TAG, "SurfaceView created in EditorView")
                            handleSurfaceViewCreated(sv, vm)
                        }
                    )
                }
            }
        }
    }

    open fun createTouchHelper() { }

    open fun initializeBitmapManager(surfaceView: SurfaceView, editorViewModel: EditorViewModel) { }

    override fun onResume() {
        super.onResume()
        onResumeDrawing()
    }

    override fun onPause() {
        super.onPause()
        onPauseDrawing()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    // Common functionality
    private fun initializePaint() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        updatePaintFromProfile()
    }

    protected fun updatePaintFromProfile() {
        paint.color = currentPenProfile.getColorAsInt()
        paint.strokeWidth = currentPenProfile.strokeWidth
        Log.d(TAG, "Updated paint: color=${currentPenProfile.strokeColor}, width=${currentPenProfile.strokeWidth}")
    }

    private fun handleSurfaceViewCreated(sv: SurfaceView, vm: EditorViewModel) {
        surfaceView = sv
        initializeBitmapManager(surfaceView, vm)
        /* NEEDS
        1. surfaceView
        2. editorViewModel
         */

        initializeGestureHandler()
        /* NEEDS
        1. surfaceView
        2. editorViewModel
         */
        setViewModel(vm)

        // Create items used for drawing
        initializeShapeMaanager()
        /* NEEDS
        1. editorViewModel
         */

        initializeStylusHandler()
        /*  NEEDS
        1. surfaceView
        2. editorViewModel
        3. bitmapManager
        4. shapesManager
         */

        // Wire up drawing references for undo/redo actions
        editorViewModel.setDrawingReferences(shapesManager, bitmapManager) {
            forceScreenRefresh()
        }

        initializePaint()

        initializeDeviceReceiver()
        /* NEEDS
        1. surfaceView
        2. onyxTouchHelper
        3. bitmap (can be null maybe?)
        */

        initializeSurfaceCallback()
        /* NEEDS
        1. surfaceView
        2. bitmap
        3. bitmapCanvas
         */

        createTouchHelper()
        /* NEEDS
        1. surfaceView
        2. stylusHandler
         */

        // Set observers for:
        // 1. When pen profile changes
        // 2. Pagination is enabled or disabled
        // 3. ViewportState changes
        setObservers()

        // Initialize note navigation state
        editorViewModel.initNavigationState()

        // Wire up note switch callback for re-initializing drawing surfaces
        editorViewModel.onNoteSwitched = {
            onNoteSwitched()
        }
    }

    /**
     * Called when the user navigates to a different note within a notebook.
     * Clears the bitmap and re-renders shapes from the new note.
     */
    private fun onNoteSwitched() {
        lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Note switched — reinitializing drawing surfaces")
            // Clear the bitmap
            bitmap?.let { bm ->
                bitmapCanvas?.drawColor(Color.WHITE)
            }
            // Reinitialize shapes manager to pick up new note's shapes
            initializeShapeMaanager()
            // Re-wire drawing references for undo/redo
            editorViewModel.setDrawingReferences(shapesManager, bitmapManager) {
                forceScreenRefresh()
            }
            // Redraw from the new note's shapes
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(surfaceView, it) }
        }
    }

    protected open fun initializeSurfaceCallback() {
        Log.d("DebugAug12", "Initializing touch helper")

        surfaceView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateActiveSurface()
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cleanSurfaceView(surfaceView)
                createDrawingBitmap() // Ensure bitmap is created
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                updateActiveSurface()
                if (bitmap == null || bitmap!!.width != width || bitmap!!.height != height) {
                    // Recreate bitmap if size changed
                    bitmap?.recycle()
                    bitmap = null
                    bitmapCanvas = null
                    createDrawingBitmap()
                    recreateBitmapFromShapes()
                }
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(this)
            }
        }
        surfaceView.holder.addCallback(surfaceCallback)
    }

    override fun setViewModel(viewModel: EditorViewModel) {
        Log.d("DebugAug12", "Setting ViewModel in BaseDrawingActivity")
        this.editorViewModel = viewModel

        // Set screen width for pagination calculations
        val screenWidth = resources.displayMetrics.widthPixels
        viewModel.setScreenWidth(screenWidth)
    }

    fun setObservers() {
        // Observe pen profile changes
        lifecycleScope.launch {
            editorViewModel.currentPenProfile.collect { profile ->
                Log.d("DebugAug12", "OBSERVER: Pen profile changed: $profile")
                updatePenProfile(profile)
            }
        }
        
        // Observe pagination state changes
        lifecycleScope.launch {
            editorViewModel.isPaginationEnabled.collect { enabled ->
                // Update exclusion zones when pagination state changes
                updatePaginationExclusionZones()
                Log.d("DebugAug12", "OBSERVER: Pagination enabled: $enabled")
            }
        }
        
        // Observe viewport changes to update page separator positions and redraw shapes
        lifecycleScope.launch {
            editorViewModel.viewportState.collect { _ ->
                Log.d("DebugAug12", "OBSERVER Viewport changed: ${editorViewModel.viewportState.value}")
                if (editorViewModel.isPaginationEnabled.value) {
                    // Update scroll position for page number calculation
                    editorViewModel.updateCurrentPage(-editorViewModel.viewportState.value.offsetY)
                    // Update exclusion zones when viewport changes
                    updatePaginationExclusionZones()
                }

                Log.d("DebugAug12", "OBSERVER Viewport changed calling onViewportChanged()")
                // Trigger redraw of shapes when viewport changes
                onViewportChanged()
            }
        }

        // Observe undo/redo state changes to refresh toolbar on e-ink display
        lifecycleScope.launch {
            editorViewModel.canUndo.collect { _ ->
                refreshUIChrome()
            }
        }
        lifecycleScope.launch {
            editorViewModel.canRedo.collect { _ ->
                refreshUIChrome()
            }
        }

        Log.d("DebugAug12", "DONE Setting ViewModel in BaseDrawingActivity")
    }

    override fun onShapeCompleted(id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) {
        editorViewModel.addShape(id, points, pressures, timestamps)
    }

    override fun onShapeRemoved(shapeId: String) {
        editorViewModel.removeShape(shapeId)
    }
    
    private fun updatePaginationExclusionZones() {
        editorViewModel.let { viewModel ->
            val currentExcludeRects = viewModel.excludeRects.value.toMutableList()
            val pageSeparatorRects = viewModel.getPageSeparatorRects()
            
            // Combine current exclusion rects with page separator rects
            val allExcludeRects = currentExcludeRects + pageSeparatorRects
            
            // Update touch helper with all exclusion zones
            updateTouchHelperExclusionZones(allExcludeRects)
        }
    }

    fun updatePenProfile(penProfile: PenProfile) {
        Log.d(TAG, "Updating pen profile: $penProfile")
        currentPenProfile = penProfile
        updatePaintFromProfile()
        updateTouchHelperWithProfile()
        // Don't call viewModel?.updatePenProfile here to avoid infinite loop
    }

    // Method to recreate bitmap from shapes
    abstract fun recreateBitmapFromShapes()

    protected fun createDrawingBitmap(): Bitmap? {
        return surfaceView.let { sv ->
            if (bitmap == null) {
                bitmap = createBitmap(sv.width, sv.height)
                bitmapCanvas = Canvas(bitmap!!)
                bitmapCanvas?.drawColor(Color.WHITE)
            }
            bitmap
        }
    }
    
    fun getOrCreateBitmap(): Bitmap? {
        if (bitmap == null) {
            createDrawingBitmap()
        }
        return bitmap
    }
    
    fun ensureBitmapCanvas(): Canvas? {
        if (bitmapCanvas == null && bitmap != null) {
            bitmapCanvas = Canvas(bitmap!!)
        }
        return bitmapCanvas
    }

    private fun cleanupResources() {
        onCleanupSDK()
        bitmap?.recycle()
        bitmap = null
        onCleanupDeviceReceiver()
    }

    /**
     * Refresh the UI chrome (toolbar, etc.) on e-ink display.
     * Override in SDK-specific classes for device-specific refresh behavior.
     */
    protected open fun refreshUIChrome() {
        window.decorView.postInvalidate()
    }

    // Abstract methods for SDK-specific lifecycle
    protected abstract fun onResumeDrawing()
    protected abstract fun onPauseDrawing()
    protected abstract fun onCleanupSDK()
    protected abstract fun updateActiveSurface()
    protected abstract fun updateTouchHelperWithProfile()
    protected abstract fun updateTouchHelperExclusionZones(excludeRects: List<Rect>)
    protected abstract fun initializeDeviceReceiver()
    protected abstract fun onCleanupDeviceReceiver()
    protected abstract fun onViewportChanged()
}
