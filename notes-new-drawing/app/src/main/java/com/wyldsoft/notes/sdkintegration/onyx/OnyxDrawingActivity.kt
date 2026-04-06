package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.lifecycleScope
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.repository.ShapeRepository
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.selection.SelectionManager
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.onyx.android.sdk.api.device.epd.EpdController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Onyx SDK implementation of BaseDrawingActivity. This is the core drawing engine.
 *
 * Drawing flow:
 *   Onyx TouchHelper delivers pen strokes via RawInputCallback ->
 *   onRawDrawingTouchPointListReceived() -> DrawingPipeline.drawScribbleToBitmap() ->
 *   ShapeFactory creates a typed Shape -> shape is rendered to the offscreen bitmap
 *   -> RendererToScreenRequest blits bitmap to SurfaceView via RxManager.
 *
 * Erasing flow:
 *   onRawErasingTouchPointListReceived() -> DrawingPipeline.handleErasing() ->
 *   EraseManager.findIntersectingShapes() hit-tests erase points against stored shapes ->
 *   matching shapes removed -> PartialEraseRefresh redraws just the affected region.
 *
 * Selection flow:
 *   Tap "Select" toolbar button -> AppMode.SELECTION -> pen switches to dashed grey ->
 *   user draws freehand lasso -> SelectionManager finds fully-contained shapes ->
 *   dashed bounding box shown around selection -> user drags selection with stylus ->
 *   ghost image technique for smooth movement -> on pen lift shapes translated + saved to DB.
 *
 * Manages the Onyx TouchHelper lifecycle (open/close raw drawing, stroke style),
 * finger-touch suppression during pen input, and the GlobalDeviceReceiver for
 * system UI events. Extended by MainActivity as the app entry point.
 */
open class OnyxDrawingActivity : BaseDrawingActivity() {
    override var TAG = "OnyxDrawingActivity"
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null
    private lateinit var drawingPipeline: DrawingPipeline
    private var shapesLoaded = false

    // ── Selection state ───────────────────────────────────────────────────────
    private enum class SelectionSubState { DRAWING_LASSO, SELECTED, MOVING }
    private var selectionSubState = SelectionSubState.DRAWING_LASSO
    private var selectedShapes = mutableListOf<com.wyldsoft.notes.shapemanagement.shapes.Shape>()
    private var selectionBoundingRectNote: RectF? = null  // note-space bounding rect
    private var ghostBitmap: Bitmap? = null
    private var ghostBaseX = 0f
    private var ghostBaseY = 0f
    private var moveStartX = 0f
    private var moveStartY = 0f
    private var lastSelectionRenderTime = 0L
    private var savedPenProfile: PenProfile? = null
    private val selectionManager = SelectionManager()

    private val SELECTION_LASSO_PROFILE = PenProfile(
        strokeWidth = 3f,
        penType = PenType.DASH,
        strokeColor = androidx.compose.ui.graphics.Color(0xFF888888.toInt())
    )

    private val selectionBoxPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        isAntiAlias = true
    }
    // ─────────────────────────────────────────────────────────────────────────

    override fun initializeSDK() {
        Log.d(TAG, "initializeSDK")
        val db = (application as ScrotesApp).database
        val shapeRepo = ShapeRepository(db.shapeDao())
        val noteId = intent.getStringExtra("noteId")

        drawingPipeline = DrawingPipeline(
            viewportManager = viewportManager,
            scope = lifecycleScope,
            shapeRepository = shapeRepo,
            noteId = noteId
        )

        if (noteId != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                drawingPipeline.loadShapes(noteId)
                shapesLoaded = true
                launch(Dispatchers.Main) {
                    forceScreenRefresh()
                }
            }
        }
    }

    override fun createTouchHelper(surfaceView: SurfaceView) {
        val callback = createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)
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
        Log.d(TAG, "cleanSurfaceView")
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun onGestureStart() {
        Log.d(TAG, "onGestureStart")
        surfaceView?.let { EpdController.enablePost(it, 1) }
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        Log.d(TAG, "renderToScreen")
        if (bitmap != null) {
            getRxManager().enqueue(
                RendererToScreenRequest(surfaceView, bitmap), null
            )
        }
    }

    override fun onResumeDrawing() {
        if (isInMode(AppMode.DRAWING)) {
            onyxTouchHelper?.setRawDrawingEnabled(true)
        }
    }

    override fun onPauseDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun onPaginationChanged(enabled: Boolean) {
        Log.d(TAG, "onPaginationChanged: $enabled")
        drawingPipeline.paginationManager = paginationManager
    }

    override fun enterNewMode(mode: AppMode) {
        Log.d(TAG, "enterNewMode $mode")
        when (mode) {
            AppMode.DRAWING -> updateTouchHelperWithProfile()
            AppMode.SELECTION -> {
                savedPenProfile = currentPenProfile
                selectionSubState = SelectionSubState.DRAWING_LASSO
                selectedShapes.clear()
                selectionBoundingRectNote = null
                // Update field directly for timing safety, then emit to StateFlow for UI
                currentPenProfile = SELECTION_LASSO_PROFILE
                EditorState.setPenProfile(SELECTION_LASSO_PROFILE)
                // observePenProfile triggers updateTouchHelperWithProfile → re-enables raw drawing
            }
            else -> {}
        }
    }

    override fun exitCurrentMode(mode: AppMode) {
        Log.d(TAG, "exitCurrentMode $mode")
        if (isInMode(mode)) return
        when (mode) {
            AppMode.DRAWING -> disableRawDrawing()
            AppMode.SELECTION -> {
                selectedShapes.clear()
                selectionBoundingRectNote = null
                ghostBitmap?.recycle()
                ghostBitmap = null
                selectionSubState = SelectionSubState.DRAWING_LASSO
                savedPenProfile?.let {
                    // Update field directly for timing safety before enterNewMode(DRAWING)
                    currentPenProfile = it
                    EditorState.setPenProfile(it)
                }
                savedPenProfile = null
                forceScreenRefresh()
            }
            else -> {}
        }
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        drawingPipeline.clearShapes()
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        Log.d(TAG, "updateTouchHelperWithProfile mode=${EditorState.currentMode.value}")
        if (EditorState.currentMode.value == AppMode.SETTINGS) {
            Log.d(TAG, "updateTouchHelperWithProfile skipped — SETTINGS mode, will apply on mode exit")
            return
        }
        onyxTouchHelper?.let { helper ->
            updateTouchHelper(helper, EditorState.getCurrentExclusionRects())
        }
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        Log.d(TAG, "updateTouchHelperExclusionZones")
        onyxTouchHelper?.let { helper -> updateTouchHelper(helper, excludeRects) }
    }

    fun updateTouchHelper(helper: TouchHelper, excludeRects: List<Rect>) {
        val limit = Rect()
        surfaceView?.getLocalVisibleRect(limit)

        helper.setRawDrawingEnabled(false)
        helper.closeRawDrawing()

        helper.setStrokeWidth(currentPenProfile.strokeWidth * viewportManager.scale)
            .setStrokeColor(currentPenProfile.getColorAsInt())
            .setLimitRect(limit, ArrayList(excludeRects))
            .openRawDrawing()
            .setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            .setRawDrawingEnabled(true).isRawDrawingRenderEnabled = true
    }

    override fun initializeDeviceReceiver() {
        Log.d(TAG, "initializeDeviceReceiver")
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            surfaceView?.let { sv -> renderToScreen(sv, bitmap) }
        }.setSystemScreenOnListener {
            surfaceView?.let { sv -> renderToScreen(sv, bitmap) }
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }

    override fun forceScreenRefresh() {
        Log.d(TAG, "forceScreenRefresh() called")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas

            EpdController.enablePost(sv, 1)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    override fun recreateBitmapAtCurrentViewport() {
        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
        }
    }

    override fun enableRawDrawing() {
        onyxTouchHelper?.isRawDrawingRenderEnabled = true
        onyxTouchHelper?.setRawDrawingEnabled(true)
    }

    override fun disableRawDrawing() {
        onyxTouchHelper?.isRawDrawingRenderEnabled = false
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    private fun getRxManager(): RxManager {
        Log.d(TAG, "getRxManager")
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }

    // ── Onyx RawInputCallback ─────────────────────────────────────────────────

    private fun createOnyxCallback() = object : com.onyx.android.sdk.pen.RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawDrawing mode=${EditorState.currentMode.value}")
            if (EditorState.currentMode.value == AppMode.SELECTION) {
                val tp = touchPoint ?: run {
                    isDrawingInProgress = true
                    disableFingerTouch()
                    return
                }
                when (selectionSubState) {
                    SelectionSubState.SELECTED -> {
                        if (isTouchInsideSelection(tp)) {
                            startGhostMove(tp)
                        } else {
                            EditorState.setMode(AppMode.DRAWING)
                        }
                    }
                    else -> { /* DRAWING_LASSO: SDK renders lasso stroke naturally */ }
                }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            isDrawingInProgress = true
            disableFingerTouch()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawDrawing")
            isDrawingInProgress = false
            enableFingerTouch()
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            if (EditorState.currentMode.value == AppMode.SELECTION
                && selectionSubState == SelectionSubState.MOVING
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastSelectionRenderTime < 150L) return
                lastSelectionRenderTime = now
                touchPoint?.let { tp ->
                    renderBitmapWithGhost(tp.x - moveStartX, tp.y - moveStartY)
                }
            }
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawDrawingTouchPointListReceived")
            if (EditorState.currentMode.value == AppMode.SELECTION) {
                touchPointList?.let { tpl ->
                    when (selectionSubState) {
                        SelectionSubState.DRAWING_LASSO -> handleLassoComplete(tpl)
                        SelectionSubState.MOVING -> handleMoveComplete(tpl)
                        SelectionSubState.SELECTED -> { /* no-op */ }
                    }
                }
                return
            }
            touchPointList?.points?.let { points ->
                if (!isDrawingInProgress) {
                    isDrawingInProgress = true
                }
                handleDrawing(points, touchPointList)
            }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawErasing")
            isErasingInProgress = true
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawErasing")
            isErasingInProgress = false
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawErasingTouchPointListReceived")
            if (!isErasingInProgress) {
                isErasingInProgress = true
            }
            touchPointList?.let { handleErasing(it) }
        }
    }

    // ── Normal drawing ────────────────────────────────────────────────────────

    private fun handleDrawing(points: List<TouchPoint>, touchPointList: TouchPointList) {
        Log.d(TAG, "handleDrawing")
        surfaceView?.let { sv ->
            createDrawingBitmap()
            bitmap?.let { bmp ->
                drawingPipeline.drawScribbleToBitmap(touchPointList, bmp, currentPenProfile)
                renderToScreen(sv, bitmap)
            }
        }
    }

    private fun handleErasing(erasePointList: TouchPointList) {
        Log.d(TAG, "handleErasing")
        surfaceView?.let { sv ->
            val newState = drawingPipeline.handleErasing(
                erasePointList, bitmap, sv, getRxManager()
            )
            if (newState != null) {
                bitmap = newState.bitmap
                bitmapCanvas = newState.canvas
            }
        }
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    private fun isTouchInsideSelection(tp: TouchPoint): Boolean {
        val noteRect = selectionBoundingRectNote ?: return false
        val vpRect = viewportManager.noteToViewport(noteRect)
        val padded = RectF(vpRect).apply { inset(-24f, -24f) }
        return padded.contains(tp.x, tp.y)
    }

    private fun handleLassoComplete(touchPointList: TouchPointList) {
        Log.d(TAG, "handleLassoComplete points=${touchPointList.size()}")
        val noteLasso = viewportManager.viewportToNoteTouchPoints(touchPointList)
        val found = selectionManager.findShapesInsideLasso(drawingPipeline.getShapes(), noteLasso)
        if (found.isNotEmpty()) {
            selectedShapes = found.toMutableList()
            selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)
            selectionSubState = SelectionSubState.SELECTED
            Log.d(TAG, "handleLassoComplete selected ${selectedShapes.size} shapes")
        } else {
            selectionSubState = SelectionSubState.DRAWING_LASSO
            Log.d(TAG, "handleLassoComplete no shapes selected")
        }
        // SDK drew lasso to surface but NOT to our bitmap → re-render bitmap to clear it
        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
            EpdController.enablePost(sv, 1)
            renderBitmapWithSelectionOverlay()
        }
    }

    private fun startGhostMove(tp: TouchPoint) {
        Log.d(TAG, "startGhostMove x=${tp.x} y=${tp.y}")
        // Disable ink rendering so the SDK does not draw a stroke during the drag
        onyxTouchHelper?.isRawDrawingRenderEnabled = false
        moveStartX = tp.x
        moveStartY = tp.y
        lastSelectionRenderTime = 0L

        val noteRect = selectionBoundingRectNote ?: return
        val vpRect = viewportManager.noteToViewport(noteRect)
        val bmp = bitmap ?: return

        // Crop the ghost bitmap from the current (full) bitmap at the selection viewport rect
        val l = vpRect.left.coerceAtLeast(0f).toInt()
        val t = vpRect.top.coerceAtLeast(0f).toInt()
        val w = vpRect.width().toInt().coerceAtLeast(1).coerceAtMost(bmp.width - l)
        val h = vpRect.height().toInt().coerceAtLeast(1).coerceAtMost(bmp.height - t)
        if (w <= 0 || h <= 0) return

        ghostBitmap = Bitmap.createBitmap(bmp, l, t, w, h)
        ghostBaseX = vpRect.left
        ghostBaseY = vpRect.top

        // Recreate background bitmap without selected shapes
        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapExcluding(selectedShapes, bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
        }
        selectionSubState = SelectionSubState.MOVING
    }

    private fun handleMoveComplete(touchPointList: TouchPointList) {
        Log.d(TAG, "handleMoveComplete")
        val lastPt = touchPointList.points.lastOrNull()
        val dViewX = (lastPt?.x ?: moveStartX) - moveStartX
        val dViewY = (lastPt?.y ?: moveStartY) - moveStartY
        val dNoteX = dViewX / viewportManager.scale
        val dNoteY = dViewY / viewportManager.scale

        for (shape in selectedShapes) {
            selectionManager.translateShape(shape, dNoteX, dNoteY)
            drawingPipeline.updateShape(shape)
        }
        selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)

        onyxTouchHelper?.isRawDrawingRenderEnabled = true
        ghostBitmap?.recycle()
        ghostBitmap = null
        selectionSubState = SelectionSubState.SELECTED

        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
            EpdController.enablePost(sv, 1)
            renderBitmapWithSelectionOverlay()
        }
    }

    private fun renderBitmapWithGhost(dX: Float, dY: Float) {
        val sv = surfaceView ?: return
        EpdController.enablePost(sv, 1)
        val canvas = sv.holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.WHITE)
            bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            ghostBitmap?.let { canvas.drawBitmap(it, ghostBaseX + dX, ghostBaseY + dY, null) }
            drawSelectionBox(canvas, dX, dY)
        } finally {
            sv.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun renderBitmapWithSelectionOverlay() {
        val sv = surfaceView ?: return
        val canvas = sv.holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.WHITE)
            bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            drawSelectionBox(canvas, 0f, 0f)
        } finally {
            sv.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawSelectionBox(canvas: Canvas, dX: Float, dY: Float) {
        val noteRect = selectionBoundingRectNote ?: return
        val vpRect = viewportManager.noteToViewport(noteRect)
        canvas.drawRect(
            vpRect.left + dX - 8f,
            vpRect.top + dY - 8f,
            vpRect.right + dX + 8f,
            vpRect.bottom + dY + 8f,
            selectionBoxPaint
        )
    }
}
