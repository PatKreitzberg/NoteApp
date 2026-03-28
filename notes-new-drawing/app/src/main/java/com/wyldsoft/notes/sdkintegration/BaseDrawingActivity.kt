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
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.utils.BroadcastHelper
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.editor.EditorView
import kotlinx.coroutines.launch
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
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
    protected open fun onModeChanged(mode: AppMode) {
        if (mode != EditorState.currentMode.value) {
            exitCurrentMode(EditorState.currentMode.value)
            enterNewMode(mode)
        }
    }

    protected open fun enterNewMode(mode: AppMode) {
        EditorState.setMode(mode)
        when (mode) {
            AppMode.DRAWING -> onEnterDrawingMode()
            AppMode.SELECTION -> onEnterSelectionMode()
            AppMode.TEXT -> onEnterTextMode()
            AppMode.HOME -> onEnterHomeMode()
            AppMode.SETTINGS -> onEnterSettingsMode()
        }
    }

    protected open fun exitCurrentMode(mode: AppMode) {
        when (mode) {
            AppMode.DRAWING -> onExitDrawingMode()
            AppMode.SELECTION -> onExitSelectionMode()
            AppMode.TEXT -> onExitTextMode()
            AppMode.HOME -> onExitHomeMode()
            AppMode.SETTINGS -> onExitSettingsMode()
        }
    }

    /** Enable stylus drawing. Subclasses override to activate SDK touch handling. */
    protected abstract fun onEnterDrawingMode()

    /** Disable stylus drawing. Subclasses override to deactivate SDK touch handling. */
    protected abstract fun onExitDrawingMode()

    /** Enable stylus Selection. Subclasses override to activate SDK touch handling. */
    protected abstract fun onEnterSelectionMode()

    /** Disable stylus Selection. Subclasses override to deactivate SDK touch handling. */
    protected abstract fun onExitSelectionMode()

    /** Enable stylus Text. Subclasses override to activate SDK touch handling. */
    protected abstract fun onEnterTextMode()

    /** Disable stylus Text. Subclasses override to deactivate SDK touch handling. */
    protected abstract fun onExitTextMode()

    /** Enable stylus Home. Subclasses override to activate SDK touch handling. */
    protected abstract fun onEnterHomeMode()

    /** Disable stylus Home. Subclasses override to deactivate SDK touch handling. */
    protected abstract fun onExitHomeMode()

    /** Enable stylus Settings. Subclasses override to activate SDK touch handling. */
    protected abstract fun onEnterSettingsMode()

    /** Disable stylus Settings. Subclasses override to deactivate SDK touch handling. */
    protected abstract fun onExitSettingsMode()

    open fun createTouchHelper(surfaceView: SurfaceView) { }

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

    private fun handleSurfaceViewCreated(sv: SurfaceView) {
        surfaceView = sv
        initializeTouchHelper(sv)
        createTouchHelper(sv)
        attachGestureHandler(sv)
    }

    private fun attachGestureHandler(sv: SurfaceView) {
        Log.d(TAG, "attachGestureHandler")
        gestureHandler = GestureHandler(
            onGestureEvent = { event ->
                gestureLabel.value = event.displayName()
                handleGestureForScroll(event)
            }
        )
        sv.setOnTouchListener(gestureHandler)
    }

    private fun handleGestureForScroll(event: GestureEvent) {
        when (event) {
            is GestureEvent.PanMove -> {
                if (event.fingerCount == 1) {
                    viewportManager.handlePanMove(event.deltaX, event.deltaY)
                }
            }
            is GestureEvent.PanEnd -> {
                if (event.fingerCount == 1) {
                    Log.d(TAG, "Pan ended, refreshing")
                    forceScreenRefresh()
                }
            }
            is GestureEvent.PinchMove -> {
                viewportManager.handlePinchMove(event.centerX, event.centerY, event.scaleFactor)
            }
            is GestureEvent.PinchEnd -> {
                Log.d(TAG, "Pinch ended, refreshing at scale ${viewportManager.scale}")
                forceScreenRefresh()
            }
            else -> { /* other gestures don't affect viewport */ }
        }
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
    protected abstract fun onResumeDrawing()
    protected abstract fun onPauseDrawing()
    protected abstract fun onCleanupSDK()
    protected abstract fun updateActiveSurface()
    protected abstract fun updateTouchHelperWithProfile()
    protected abstract fun updateTouchHelperExclusionZones(excludeRects: List<Rect>)
    protected abstract fun initializeDeviceReceiver()
    protected abstract fun onCleanupDeviceReceiver()
}
