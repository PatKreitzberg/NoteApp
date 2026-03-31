package com.wyldsoft.notes.sdkintegration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.repository.NoteRepository
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.editor.EditorView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.rendering.PaginationManager
import com.wyldsoft.notes.rendering.ViewportManager
import com.wyldsoft.notes.touchhandling.GestureEvent
import com.wyldsoft.notes.touchhandling.GestureHandler
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme

/**
 * Abstract base activity for all drawing functionality (Template Method pattern).
 * Owns the shared drawing state: the offscreen Bitmap, Canvas, Paint, and SurfaceView.
 * Implements the activity lifecycle (onCreate/onResume/onPause/onDestroy) and
 * delegates SDK-specific work to abstract methods:
 *   initializeSDK, createDeviceReceiver, enableFingerTouch, disableFingerTouch,
 *   cleanSurfaceView, renderToScreen, onResumeDrawing, onPauseDrawing, etc.
 *
 * Sets up the Compose UI via EditorView and wires the SurfaceView's holder callbacks
 * so that subclasses get notified of surface lifecycle events.
 *
 * Subclass: OnyxDrawingActivity (Onyx e-ink SDK implementation).
 */
abstract class BaseDrawingActivity : ComponentActivity() {
    protected open val TAG = "BaseDrawingActivity"

    // Common drawing state
    protected var paint = Paint()
    protected var bitmap: Bitmap? = null
    protected var bitmapCanvas: Canvas? = null
    protected var surfaceView: SurfaceView? = null
    protected var isDrawingInProgress = false
    protected var isErasingInProgress = false
    protected var currentPenProfile = PenProfile.getDefaultProfile(PenType.BALLPEN)
    protected var gestureHandler: GestureHandler? = null
    val gestureLabel = mutableStateOf("")

    // Centralized viewport state: scroll position + scale
    protected val viewportManager = ViewportManager()
    protected var paginationManager: PaginationManager? = null
    protected var currentNoteId: String? = null
    private var noteRepository: NoteRepository? = null

    // Throttle for smooth scroll/zoom updates (ms between renders)
    private val GESTURE_RENDER_INTERVAL_MS = 150L
    private var lastGestureRenderTime = 0L

    // Abstract methods that must be implemented by SDK-specific classes
    abstract fun initializeSDK()
    abstract fun createDeviceReceiver(): BaseDeviceReceiver
    abstract fun enableFingerTouch()
    abstract fun disableFingerTouch()
    abstract fun disableRawDrawing()
    abstract fun enableRawDrawing()
    abstract fun cleanSurfaceView(surfaceView: SurfaceView): Boolean
    abstract fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?)

    // Template methods - common implementation for all SDKs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentNoteId = intent.getStringExtra("noteId")
        val db = (application as ScrotesApp).database
        noteRepository = NoteRepository(db.noteDao())

        // Restore viewport state from the note if available
        currentNoteId?.let { noteId ->
            lifecycleScope.launch(Dispatchers.IO) {
                val note = noteRepository?.getById(noteId)
                if (note != null) {
                    launch(Dispatchers.Main) {
                        viewportManager.restoreState(
                            note.viewportScale,
                            note.viewportScrollX,
                            note.viewportScrollY
                        )
                    }
                }
            }
        }

        initializeSDK()
        initializePaint()
        initializeDeviceReceiver()
        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditorView(
                        onSurfaceViewCreated = { sv ->
                            handleSurfaceViewCreated(sv)
                        },
                        gestureLabel = gestureLabel
                    )
                }
            }
        }

        EditorState.setMainActivity(this as com.wyldsoft.notes.MainActivity)
        observeAppMode()
        observePenProfile()
        observePagination()
    }

    private fun observePenProfile() {
        lifecycleScope.launch {
            EditorState.currentPenProfile.collect { profile ->
                Log.d(TAG, "Pen profile changed: ${profile.penType.displayName}, width=${profile.strokeWidth}")
                currentPenProfile = profile
                updatePaintFromProfile()
                updateTouchHelperWithProfile()
            }
        }
    }

    private fun observePagination() {
        lifecycleScope.launch {
            EditorState.paginationEnabled.collect { enabled ->
                Log.d(TAG, "Pagination changed: $enabled")
                viewportManager.paginationEnabled = enabled
                if (enabled) {
                    viewportManager.resetViewport()
                    surfaceView?.let { sv ->
                        paginationManager = PaginationManager(
                            screenWidthPx = sv.width,
                            screenHeightPx = sv.height,
                            density = resources.displayMetrics.density
                        )
                    }
                } else {
                    paginationManager = null
                }
                onPaginationChanged(enabled)
                forceScreenRefresh()
                updatePaginationExclusions()
            }
        }
    }

    protected open fun onPaginationChanged(enabled: Boolean) {}

    private fun checkLazyPageCreation() {
        paginationManager?.let { pm ->
            surfaceView?.let { sv ->
                val viewportHeightInNote = sv.height / viewportManager.scale
                if (pm.maybeAddPage(viewportManager.scrollY, viewportHeightInNote)) {
                    Log.d(TAG, "Lazy page created, now ${pm.pageCount} pages")
                    forceScreenRefresh()
                    updatePaginationExclusions()
                }
            }
        }
    }

    private fun updatePaginationExclusions() {
        paginationManager?.let { pm ->
            surfaceView?.let { sv ->
                val gapExclusions = pm.computeExclusionRects(
                    viewportManager.scrollX, viewportManager.scrollY,
                    viewportManager.scale, sv.width, sv.height
                )
                val allExclusions = EditorState.getCurrentExclusionRects().toMutableList()
                allExclusions.addAll(gapExclusions)
                updateExclusionZones(allExclusions)
            }
        }
    }

    private fun observeAppMode() {
        lifecycleScope.launch {
            EditorState.currentMode.collect { mode ->
                Log.d(TAG, "App mode changed to: $mode")
                onModeChanged(mode)
            }
        }
    }

    /**
     * Called when the app mode changes. Subclasses enable/disable SDK features accordingly.
     */
    protected open fun onModeChanged(newMode: AppMode) {
        Log.d(TAG, "onModeChanged called newMode=$newMode previous mode=${EditorState.previousMode}")
        if (newMode != EditorState.previousMode) {
            Log.d(TAG, "mode != current mode")
            exitCurrentMode(EditorState.previousMode)
            enterNewMode(newMode)
        }
    }

    protected abstract fun enterNewMode(mode: AppMode)
    protected abstract fun exitCurrentMode(mode: AppMode)

    protected open fun isInMode(mode: AppMode): Boolean {
        return (mode == EditorState.currentMode.value)
    }

    open fun createTouchHelper(surfaceView: SurfaceView) { }

    override fun onResume() {
        super.onResume()
        onResumeDrawing()
    }

    override fun onPause() {
        super.onPause()
        onPauseDrawing()
        saveViewportState()
    }

    private fun saveViewportState() {
        val noteId = currentNoteId ?: return
        val repo = noteRepository ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            repo.updateViewport(
                noteId,
                viewportManager.scale,
                viewportManager.scrollX,
                viewportManager.scrollY
            )
        }
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

    private fun handleSurfaceViewCreated(sv: SurfaceView) {
        surfaceView = sv
        initializeTouchHelper(sv)
        createTouchHelper(sv)
        attachGestureHandler(sv)
    }

    private fun attachGestureHandler(sv: SurfaceView) {
        Log.d(TAG, "attachGestureHandler")
        gestureHandler = GestureHandler(
            currentModeProvider = { EditorState.currentMode.value },
            changeMode = {mode ->
                EditorState.setMode(mode)
                         },
            onGestureEvent = { event ->
                gestureLabel.value = event.displayName()
                handleGestureThatTransformsViewport(event)
            }
        )
        sv.setOnTouchListener(gestureHandler)
    }

    private fun handleGestureThatTransformsViewport(event: GestureEvent) {
        when (event) {
            is GestureEvent.PanStart -> {
                if (event.fingerCount == 1) {
                    onGestureStart()
                    viewportManager.saveSnapshot()
                }
            }
            is GestureEvent.PanMove -> {
                if (event.fingerCount == 1) {
                    viewportManager.handlePanMove(event.deltaX, event.deltaY)
                    renderBitmapWithGestureTransform()
                }
            }
            is GestureEvent.PanEnd -> {
                if (event.fingerCount == 1) {
                    Log.d(TAG, "Pan ended, refreshing")
                    viewportManager.clearSnapshot()
                    forceScreenRefresh()
                    checkLazyPageCreation()
                    updatePaginationExclusions()
                }
            }
            is GestureEvent.PinchStart -> {
                onGestureStart()
                viewportManager.saveSnapshot()
            }
            is GestureEvent.PinchMove -> {
                viewportManager.handlePinchMove(event.centerX, event.centerY, event.scaleFactor)
                renderBitmapWithGestureTransform()
            }
            is GestureEvent.PinchEnd -> {
                Log.d(TAG, "Pinch ended, refreshing at scale ${viewportManager.scale}")
                viewportManager.clearSnapshot()
                forceScreenRefresh()
                updateTouchHelperWithProfile()
                checkLazyPageCreation()
                updatePaginationExclusions()
            }
            else -> { /* other gestures don't affect viewport */ }
        }
    }

    /**
     * Renders shapes at the current viewport position during scroll/zoom gestures.
     * Recreates the bitmap from shapes so that shapes scrolling into view appear
     * correctly. Throttled to avoid overwhelming the e-ink display.
     */
    private fun renderBitmapWithGestureTransform() {
        val now = SystemClock.uptimeMillis()
        if (now - lastGestureRenderTime < GESTURE_RENDER_INTERVAL_MS) return
        lastGestureRenderTime = now

        val sv = surfaceView ?: return

        recreateBitmapAtCurrentViewport()

        val bmp = bitmap ?: return
        val canvas = sv.holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bmp, 0f, 0f, null)
        } finally {
            sv.holder.unlockCanvasAndPost(canvas)
        }
    }

    /**
     * Recreates the offscreen bitmap from all shapes at the current viewport.
     * Subclasses override to call their DrawingPipeline.
     */
    protected open fun recreateBitmapAtCurrentViewport() {
        // Default no-op; subclasses with a DrawingPipeline override this.
    }

    protected open fun initializeTouchHelper(surfaceView: SurfaceView) {
        surfaceView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateActiveSurface()
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cleanSurfaceView(surfaceView)
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                updateActiveSurface()
                bitmap?.let { renderToScreen(surfaceView, it) }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(this)
            }
        }
        surfaceView.holder.addCallback(surfaceCallback)
    }

    protected open fun forceScreenRefresh() {
        Log.d("BaseDrawingActivity:", "forceScreenRefresh()")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    protected fun createDrawingBitmap(): Bitmap? {
        return surfaceView?.let { sv ->
            if (bitmap == null) {
                bitmap = createBitmap(sv.width, sv.height)
                bitmapCanvas = Canvas(bitmap!!)
                bitmapCanvas?.drawColor(Color.WHITE)
            }
            bitmap
        }
    }

    private fun cleanupResources() {
        onCleanupSDK()
        bitmap?.recycle()
        bitmap = null
        onCleanupDeviceReceiver()
    }

    // Abstract methods for SDK-specific lifecycle
    protected abstract fun onGestureStart()
    protected abstract fun onResumeDrawing()
    protected abstract fun onPauseDrawing()
    protected abstract fun onCleanupSDK()
    protected abstract fun updateActiveSurface()
    protected abstract fun updateTouchHelperWithProfile()
    protected abstract fun updateTouchHelperExclusionZones(excludeRects: List<Rect>)

    fun updateExclusionZones(excludeRects: List<Rect>) {
        updateTouchHelperExclusionZones(excludeRects)
    }
    protected abstract fun initializeDeviceReceiver()
    protected abstract fun onCleanupDeviceReceiver()
}
