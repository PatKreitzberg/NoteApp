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
import androidx.compose.runtime.LaunchedEffect
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.editor.EditorView
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.data.repository.*
import com.wyldsoft.notes.data.database.NotesDatabase
import com.wyldsoft.notes.drawing.DrawingActivityInterface
import android.graphics.PointF
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.onyx.android.sdk.api.device.epd.EpdController
import com.wyldsoft.notes.rendering.BitmapManager


abstract class BaseDrawingActivity : ComponentActivity(), DrawingActivityInterface {
    protected val TAG = "BaseDrawingActivity"

    // Common drawing state
    protected var paint = Paint()
    protected var bitmap: Bitmap? = null
    protected var bitmapCanvas: Canvas? = null
    protected lateinit var surfaceView: SurfaceView // lateinite instead of ? = null if I am sure it will be initialized before use
    protected lateinit var bitmapManager: BitmapManager // lateinite instead of ? = null if I am sure it will be initialized before use
    protected var isDrawingInProgress = false
    protected var currentPenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)
    protected lateinit var editorViewModel: EditorViewModel // lateinite instead of ? = null if I am sure it will be initialized before use

    // Abstract methods that must be implemented by SDK-specific classes
    abstract fun initializeSDK()
    abstract fun createDeviceReceiver(): BaseDeviceReceiver
    abstract fun enableFingerTouch()
    abstract fun disableFingerTouch()
    abstract fun cleanSurfaceView(surfaceView: SurfaceView): Boolean
    abstract fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?)

    // Template methods - common implementation for all SDKs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // init database and repositories
        var noteRepository_notebookRepository = initializeDatabase()
        val notebookId = intent.getStringExtra("notebookId") ?: return // note used but will be
        val noteId = intent.getStringExtra("noteId") ?: return

        // Create EditorViewModel with repositories
        Log.d(TAG, "Setting EditorView as content with noteId: $noteId")
        editorViewModel = EditorViewModel(noteRepository_notebookRepository.first, noteRepository_notebookRepository.second)



        // Create the UI
        setEditorViewAsContent(noteId, noteRepository_notebookRepository.first, noteRepository_notebookRepository.second)
    }

    fun initializeDatabase(): Pair<NoteRepository, NotebookRepository> {
        // Initialize database and repositories
        val database = NotesDatabase.getDatabase(this)
        val noteRepository = NoteRepositoryImpl(
            noteDao = database.noteDao(),
            shapeDao = database.shapeDao()
        )
        val folderRepository = FolderRepositoryImpl(
            folderDao = database.folderDao()
        )
        val notebookRepository = NotebookRepositoryImpl(
            notebookDao = database.notebookDao(),
            noteDao = database.noteDao()
        )
        return Pair(noteRepository, notebookRepository)
    }

    fun setEditorViewAsContent(noteId: String, noteRepository: NoteRepository, notebookRepository: NotebookRepository) {
        Log.d(TAG, "ViewModel created")
        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LaunchedEffect(noteId) {
                        noteRepository.setCurrentNote(noteId)
                    }

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

    open fun createTouchHelper(surfaceView: SurfaceView) { }

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
        setViewModel(vm)

        // Create items used for drawing
        initializeSDK() // loads shapes, sets currentNote change listener. Importantly, refreshes screen so has to be called here
        initializePaint() // init paint, really not much
        initializeDeviceReceiver() // init device receiver for pen events

        Log.d("DebugAug12", "SurfaceView created in BaseDrawingActivity of size: ${sv.width}x${sv.height}")

        // have to initialize after set editorview as content because they rely on the viewmodel being set

        initializeTouchHelper(surfaceView)
        initializeBitmapManager(surfaceView, editorViewModel)
        createTouchHelper(surfaceView)

        Log.d("DebugAug12", "setting up observers in BaseDrawingActivity")
        setObservers()
    }

    protected open fun initializeTouchHelper(surfaceView: SurfaceView) {
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
        Log.d("DebugAug12", "DONE Setting ViewModel in BaseDrawingActivity")
    }

    override fun onShapeCompleted(points: List<PointF>, pressures: List<Float>) {
        editorViewModel?.addShape(points, pressures)
    }
    
    private fun updatePaginationExclusionZones() {
        editorViewModel?.let { viewModel ->
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

    fun updateExclusionZones(excludeRects: List<Rect>) {
        updateTouchHelperExclusionZones(excludeRects)
        editorViewModel?.updateExclusionZones(excludeRects)
        println("forceScreenRefresh() from updateExclusionZone")
        //forceScreenRefresh()
    }

    override fun forceScreenRefresh() {
        Log.d("BaseDrawingActivity:", "forceScreenRefresh()")
        EpdController.enablePost(surfaceView, 1)
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }
    
    // Method to recreate bitmap from shapes
    abstract fun recreateBitmapFromShapes()

    protected fun createDrawingBitmap(): Bitmap? {
        if (surfaceView == null) {
            Log.e(TAG, "SurfaceView is not initialized")
        }
        else {
            Log.d(TAG, "Creating drawing bitmap for SurfaceView: ${surfaceView.width}x${surfaceView.height}")
        }

        return surfaceView?.let { sv ->
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
