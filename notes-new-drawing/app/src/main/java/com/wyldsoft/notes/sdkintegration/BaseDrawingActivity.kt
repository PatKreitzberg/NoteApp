package com.wyldsoft.notes.sdkintegration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
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
    abstract fun disableRawDrawing()
    abstract fun enableRawDrawing()
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
        observePenProfile()
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
                handleGestureForScroll(event)
            }
        )
        sv.setOnTouchListener(gestureHandler)
        //sv.setOnTouchListener(SettingsDismissTouchWrapper(gestureHandler!!))
    }

    /**
     * Touch listener wrapper that intercepts all touches when in SETTINGS mode.
     * On ACTION_DOWN: emits dismissSettings to close open menus.
     * On ACTION_UP: transitions to DRAWING mode.
     * In non-SETTINGS modes, delegates to the wrapped GestureHandler.
     */
    private class SettingsDismissTouchWrapper(
        private val delegate: View.OnTouchListener
    ) : View.OnTouchListener {
        companion object {
            private const val TAG = "SettingsDismissTouch"
        }

        private var dismissedOnDown = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            Log.d(TAG, "SettingsDismissTouchWrapper.onTouch")
            if (EditorState.currentMode.value != AppMode.SETTINGS) {
                return delegate.onTouch(view, event)
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Touch down in SETTINGS mode — dismissing settings")
                    dismissedOnDown = true
                    EditorState.emitDismissSettings()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dismissedOnDown) {
                        Log.d(TAG, "Touch up in SETTINGS mode — entering DRAWING mode")
                        dismissedOnDown = false
                        EditorState.setMode(AppMode.DRAWING)
                    }
                }
            }
            return true
        }
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
                updateTouchHelperWithProfile()
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

    fun updateExclusionZones(excludeRects: List<Rect>) {
        updateTouchHelperExclusionZones(excludeRects)
    }
    protected abstract fun initializeDeviceReceiver()
    protected abstract fun onCleanupDeviceReceiver()
}
