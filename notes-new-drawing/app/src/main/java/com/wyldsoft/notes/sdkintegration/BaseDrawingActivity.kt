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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureHandler
import com.wyldsoft.notes.presentation.viewmodel.DrawTool
import com.wyldsoft.notes.presentation.viewmodel.EditorMode
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.session.NoteSession
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking


abstract class BaseDrawingActivity : ComponentActivity(), DrawingActivityInterface {
    protected val TAG = "BaseDrawingActivity"

    protected var paint = Paint()
    protected var bitmap: Bitmap? = null
    protected var bitmapCanvas: Canvas? = null
    protected var currentPenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)

    protected lateinit var surfaceView: SurfaceView
    protected lateinit var bitmapManager: BitmapManager
    protected lateinit var editorViewModel: EditorViewModel
    protected lateinit var shapesManager: ShapesManager
    protected lateinit var gestureHandler: GestureHandler
    protected lateinit var displaySettingsRepository: DisplaySettingsRepository

    protected var isScrollingOrZooming = false
    private var lastViewportRefreshTime = 0L

    // Abstract methods that must be implemented by SDK-specific classes
    abstract fun initializeStylusHandler()
    abstract fun createDeviceReceiver(): BaseDeviceReceiver
    abstract fun enableFingerTouch()
    abstract fun disableFingerTouch()
    abstract fun cleanSurfaceView(surfaceView: SurfaceView): Boolean
    abstract fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as com.wyldsoft.notes.ScrotesApp
        displaySettingsRepository = app.displaySettingsRepository
        val noteRepository = app.noteRepository
        val notebookRepository = app.notebookRepository
        val noteId = intent.getStringExtra("noteId") ?: return
        val notebookId = intent.getStringExtra("notebookId")

        runBlocking(Dispatchers.IO) { noteRepository.setCurrentNote(noteId) }

        Log.d(TAG, "Setting EditorView as content with noteId: $noteId, notebookId: $notebookId")
        editorViewModel = EditorViewModel(noteRepository, notebookRepository, app.htrRunManager, notebookId)
        setEditorViewAsContent()
    }

    fun setEditorViewAsContent() {
        setContent {
            MinimaleditorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EditorView(editorViewModel, displaySettingsRepository = displaySettingsRepository, syncViewModel = (application as com.wyldsoft.notes.ScrotesApp).syncViewModel, onSurfaceViewCreated = { sv, vm ->
                        Log.d(TAG, "SurfaceView created in EditorView")
                        handleSurfaceViewCreated(sv, vm)
                    })
                }
            }
        }
    }

    open fun createTouchHelper() {}
    open fun initializeBitmapManager(surfaceView: SurfaceView, editorViewModel: EditorViewModel) {}

    override fun onResume() { super.onResume(); onResumeDrawing() }
    override fun onPause() { super.onPause(); onPauseDrawing(); SyncWorker.scheduleOneTime(this) }
    override fun onDestroy() { super.onDestroy(); cleanupResources() }

    private fun initializePaint() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        updatePaintFromProfile()
    }

    protected fun updatePaintFromProfile() {
        paint.color = currentPenProfile.getColorAsInt()
        paint.strokeWidth = currentPenProfile.strokeWidth
    }

    private fun handleSurfaceViewCreated(sv: SurfaceView, vm: EditorViewModel) {
        surfaceView = sv
        initializeBitmapManager(surfaceView, vm)
        initializeGestureHandler()
        setViewModel(vm)
        val session = editorViewModel.getOrCreateSession()
        shapesManager = session.shapesManager
        initializeStylusHandler()
        editorViewModel.setDrawingManagers(bitmapManager) { forceScreenRefresh() }
        editorViewModel.activateSession(session)
        initializePaint()
        initializeDeviceReceiver()
        initializeSurfaceCallback()
        createTouchHelper()
        setObservers()
        forceScreenRefresh()
        editorViewModel.initNavigationState()
        editorViewModel.onNoteSwitched = { onNoteSwitched() }
    }

    private fun onNoteSwitched() {
        lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Note switched — getting or creating session")
            bitmapCanvas?.drawColor(Color.WHITE)
            val session = editorViewModel.getOrCreateSession()
            shapesManager = session.shapesManager
            editorViewModel.activateSession(session)
            forceScreenRefresh()
        }
    }

    protected open fun initializeSurfaceCallback() {
        surfaceView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateActiveSurface() }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cleanSurfaceView(surfaceView)
                createDrawingBitmap()
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                editorViewModel.viewportManager.viewWidth = width
                editorViewModel.viewportManager.viewHeight = height
                updateActiveSurface()
                if (bitmap == null || bitmap!!.width != width || bitmap!!.height != height) {
                    bitmap?.recycle()
                    bitmap = null
                    bitmapCanvas = null
                    createDrawingBitmap()
                    recreateBitmapFromShapes()
                }
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) { holder.removeCallback(this) }
        })
    }

    override fun setViewModel(viewModel: EditorViewModel) {
        this.editorViewModel = viewModel
        viewModel.setScreenWidth(resources.displayMetrics.widthPixels)
    }

    fun setObservers() {
        observePenProfile()
        observePaginationState()
        observeViewportChanges()
        observeUndoRedoState()
        observeDrawingBlocked()
        observeModeChanges()
        Log.d("DebugAug12", "DONE Setting observers in BaseDrawingActivity")
    }

    private fun observePenProfile() {
        lifecycleScope.launch {
            editorViewModel.currentPenProfile.collect { profile ->
                Log.d("DebugAug12", "OBSERVER: Pen profile changed: $profile")
                updatePenProfile(profile)
            }
        }
    }

    private fun observePaginationState() {
        lifecycleScope.launch {
            editorViewModel.isPaginationEnabled.collect { enabled ->
                updatePaginationExclusionZones()
                Log.d("DebugAug12", "OBSERVER: Pagination enabled: $enabled")
            }
        }
    }

    private fun observeViewportChanges() {
        lifecycleScope.launch {
            editorViewModel.viewportState.collect { _ ->
                val vm = editorViewModel
                if (vm.isPaginationEnabled.value) {
                    vm.updateCurrentPage(vm.viewportState.value.scrollY)
                    updatePaginationExclusionZones()
                }
                val smoothMotion = displaySettingsRepository.smoothMotion.value
                if (!smoothMotion && isScrollingOrZooming) return@collect
                val now = System.currentTimeMillis()
                if (now - lastViewportRefreshTime < displaySettingsRepository.minRefreshIntervalMs) return@collect
                lastViewportRefreshTime = now
                onViewportChanged()
            }
        }
    }

    private fun observeUndoRedoState() {
        lifecycleScope.launch { editorViewModel.canUndo.collect { refreshUIChrome() } }
        lifecycleScope.launch { editorViewModel.canRedo.collect { refreshUIChrome() } }
    }

    private fun observeDrawingBlocked() {
        lifecycleScope.launch {
            editorViewModel.isDrawingBlocked.collect { blocked -> setDrawingEnabled(!blocked) }
        }
    }

    private fun observeModeChanges() {
        lifecycleScope.launch {
            editorViewModel.uiState
                .map { it.mode }
                .distinctUntilChanged()
                .collect { _ ->
                    if (!editorViewModel.isDrawingBlocked.value) {
                        setDrawingEnabled(true)
                    }
                }
        }
    }

    private fun switchModeFromGesture(block: () -> Unit) {
        block()
        setDrawingEnabled(true)
        bitmapManager.renderBitmapToScreen()
    }

    open fun initializeGestureHandler() {
        gestureHandler = GestureHandler(this, surfaceView)
        gestureHandler.setViewportManager(editorViewModel.viewportManager)
        val app = application as com.wyldsoft.notes.ScrotesApp
        gestureHandler.gestureMappings = app.gestureSettingsRepository.mappings.value

        gestureHandler.onScrollingStateChanged = { isScrolling ->
            isScrollingOrZooming = isScrolling
            if (!isScrolling) onViewportChanged()
        }

        gestureHandler.onGestureAction = { action ->
            when (action) {
                GestureAction.RESET_ZOOM_AND_CENTER -> {
                    val vm = editorViewModel
                    vm.viewportManager.resetZoomAndCenter(vm.isPaginationEnabled.value, vm.screenWidth.value.toFloat())
                    forceScreenRefresh()
                }
                GestureAction.TOGGLE_SELECTION_MODE -> switchModeFromGesture {
                    editorViewModel.toggleMode(EditorMode.Select)
                }
                GestureAction.TOGGLE_TEXT_MODE -> switchModeFromGesture {
                    editorViewModel.toggleMode(EditorMode.Text)
                }
                GestureAction.SWITCH_TAB -> switchModeFromGesture {
                    val nextMode = when (editorViewModel.uiState.value.mode) {
                        is EditorMode.Draw -> EditorMode.Select
                        is EditorMode.Select -> EditorMode.Text
                        is EditorMode.Text -> EditorMode.Draw()
                    }
                    editorViewModel.switchMode(nextMode)
                }
                GestureAction.DRAW_GEOMETRIC_SHAPE -> switchModeFromGesture {
                    editorViewModel.switchMode(EditorMode.Draw(DrawTool.GEOMETRY))
                }
                GestureAction.COPY_SELECTION -> {
                    if (editorViewModel.uiState.value.mode is EditorMode.Select) editorViewModel.copySelection()
                }
                GestureAction.PASTE_SELECTION -> {
                    editorViewModel.pasteSelection()
                    forceScreenRefresh()
                }
                GestureAction.UNDO -> editorViewModel.undo()
                GestureAction.REDO -> editorViewModel.redo()
                else -> Log.d(TAG, "Gesture action $action handled inline")
            }
        }
    }

    override fun onShapeCompleted(id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long>) {
        editorViewModel.addShape(id, points, pressures, timestamps)
    }

    override fun onShapeRemoved(shapeId: String) {
        editorViewModel.removeShape(shapeId)
    }

    private fun updatePaginationExclusionZones() {
        val allExcludeRects = editorViewModel.excludeRects.value + editorViewModel.getPageSeparatorRects()
        updateTouchHelperExclusionZones(allExcludeRects)
    }

    fun updatePenProfile(penProfile: PenProfile) {
        currentPenProfile = penProfile
        updatePaintFromProfile()
        updateTouchHelperWithProfile()
    }

    open fun recreateBitmapFromShapes() {
        getOrCreateBitmap()
        bitmapManager.recreateBitmapFromShapes(shapesManager.shapes())
        val selMgr = editorViewModel.selectionManager
        if (selMgr.hasSelection) {
            bitmapManager.drawSelectionOverlay(selMgr, editorViewModel.viewportManager)
        }
    }

    protected fun createDrawingBitmap(): Bitmap? {
        if (bitmap == null) {
            bitmap = createBitmap(surfaceView.width, surfaceView.height)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.WHITE)
        }
        return bitmap
    }

    fun getOrCreateBitmap(): Bitmap? {
        if (bitmap == null) createDrawingBitmap()
        return bitmap
    }

    fun ensureBitmapCanvas(): Canvas? {
        if (bitmapCanvas == null && bitmap != null) bitmapCanvas = Canvas(bitmap!!)
        return bitmapCanvas
    }

    private fun cleanupResources() {
        onCleanupSDK()
        bitmap?.recycle()
        bitmap = null
        onCleanupDeviceReceiver()
    }

    protected open fun refreshUIChrome() { window.decorView.postInvalidate() }
    open fun setDrawingEnabled(enabled: Boolean) {}

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
