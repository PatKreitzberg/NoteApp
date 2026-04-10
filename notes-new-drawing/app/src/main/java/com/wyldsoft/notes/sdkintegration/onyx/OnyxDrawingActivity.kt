package com.wyldsoft.notes.sdkintegration.onyx

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.repository.ShapeRepository
import com.wyldsoft.notes.data.database.repository.UndoHistoryRepository
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.models.PaperTemplate
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.selection.SelectionManager
import com.wyldsoft.notes.touchhandling.TouchUtils
import androidx.compose.ui.graphics.toArgb
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.geometry.GeometryShapeType
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import com.wyldsoft.notes.touchhandling.GestureEvent
import com.wyldsoft.notes.undoredo.ActionManager
import com.wyldsoft.notes.undoredo.CircleSelectAction
import com.wyldsoft.notes.undoredo.DrawAction
import com.wyldsoft.notes.undoredo.EraseAction
import com.wyldsoft.notes.undoredo.MoveAction
import com.wyldsoft.notes.undoredo.PasteAction
import com.wyldsoft.notes.undoredo.ScribbleEraseAction
import com.wyldsoft.notes.undoredo.SeparationAction
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.htr.ShapeGeometryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var shapeRepo: ShapeRepository
    private lateinit var undoHistoryRepo: UndoHistoryRepository
    private val htrRunManager = HTRRunManager()

    // ── Selection state ───────────────────────────────────────────────────────
    private enum class SelectionSubState { DRAWING_LASSO, SELECTED, MOVING }
    private var selectionSubState = SelectionSubState.DRAWING_LASSO
    private var selectedShapes = mutableListOf<com.wyldsoft.notes.shapemanagement.shapes.Shape>()
    private var selectionBoundingRectNote: RectF? = null  // note-space bounding rect
    private var circleSelectPreloaded = false  // prevents enterNewMode from clearing circle-to-select state
    private var ghostBitmap: Bitmap? = null
    private var ghostBaseX = 0f
    private var ghostBaseY = 0f
    private var moveStartX = 0f
    private var moveStartY = 0f
    private var lastSelectionRenderTime = 0L
    private var savedPenProfile: PenProfile? = null
    private var copiedShapes = listOf<com.wyldsoft.notes.shapemanagement.shapes.Shape>()
    private var pendingPaste = false
    // Stroke suppression: used when a touch should be swallowed without drawing,
    // then an action triggered on pen-lift (e.g. cancel selection, dismiss a panel).
    // Two separate fields because the callbacks that consume them can fire in either order.
    private var strokeDataSuppressed = false          // cleared by onRawDrawingTouchPointListReceived
    private var strokeEndAction: (() -> Unit)? = null // cleared by onEndRawDrawing
    private val selectionManager = SelectionManager()
    private lateinit var actionManager: ActionManager

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

    // ── Separation state ──────────────────────────────────────────────────────
    private enum class SeparationSubState { DRAWING_SPLIT_LINE, DRAGGING_OFFSET }
    private var separationSubState = SeparationSubState.DRAWING_SPLIT_LINE
    private var splitLineY = 0f                          // note-space Y of the drawn split line
    private var separationShapes = listOf<com.wyldsoft.notes.shapemanagement.shapes.Shape>()
    private var separationBackgroundBitmap: Bitmap? = null
    private var separationGhostBitmap: Bitmap? = null
    private var lastSeparationRenderTime = 0L
    private val splitLinePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(24f, 12f), 0f)
        isAntiAlias = true
    }

    // ── Geometry state ────────────────────────────────────────────────────────
    private var geometryStartPoint: com.onyx.android.sdk.data.note.TouchPoint? = null
    private var geometrySnapshotBitmap: Bitmap? = null
    private var lastGeometryRenderTime = 0L
    // ─────────────────────────────────────────────────────────────────────────

    override fun initializeSDK() {
        Log.d(TAG, "initializeSDK")
        val db = (application as ScrotesApp).database
        shapeRepo = ShapeRepository(db.shapeDao())
        undoHistoryRepo = UndoHistoryRepository(db.undoHistoryDao())
        val noteId = intent.getStringExtra("noteId")

        drawingPipeline = DrawingPipeline(
            viewportManager = viewportManager,
            scope = lifecycleScope,
            shapeRepository = shapeRepo,
            noteId = noteId
        )

        actionManager = ActionManager(
            undoHistoryRepository = undoHistoryRepo,
            noteId = noteId,
            scope = lifecycleScope,
            selectionManager = selectionManager,
            paginationManager = paginationManager
        )

        if (noteId != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                drawingPipeline.loadShapes(noteId)
                actionManager.loadFromDatabase(drawingPipeline)
                shapesLoaded = true
                launch(Dispatchers.Main) {
                    forceScreenRefresh()
                }
            }
        }

        observeUndoRedo()
    }

    override fun onSwitchNoteSDK(noteId: String) {
        Log.d(TAG, "onSwitchNoteSDK: $noteId")
        drawingPipeline = DrawingPipeline(
            viewportManager = viewportManager,
            scope = lifecycleScope,
            shapeRepository = shapeRepo,
            noteId = noteId
        )
        drawingPipeline.paginationManager = paginationManager
        actionManager = ActionManager(
            undoHistoryRepository = undoHistoryRepo,
            noteId = noteId,
            scope = lifecycleScope,
            selectionManager = selectionManager,
            paginationManager = paginationManager
        )
        EditorState.setUndoRedoState(false, false)
        shapesLoaded = false

        lifecycleScope.launch(Dispatchers.IO) {
            drawingPipeline.loadShapes(noteId)
            actionManager.loadFromDatabase(drawingPipeline)
            shapesLoaded = true
            launch(Dispatchers.Main) {
                forceScreenRefresh()
            }
        }
    }

    private fun observeUndoRedo() {
        Log.d(TAG, "observeUndoRedo")
        lifecycleScope.launch {
            EditorState.undoRequested.collect {
                actionManager.undo(lifecycleScope, ::onUndoRedoComplete)
            }
        }
        lifecycleScope.launch {
            EditorState.redoRequested.collect {
                actionManager.redo(lifecycleScope, ::onUndoRedoComplete)
            }
        }
        lifecycleScope.launch {
            EditorState.copyRequested.collect { handleCopy() }
        }
        lifecycleScope.launch {
            EditorState.pasteRequested.collect {
                if (EditorState.currentMode.value == AppMode.SELECTION) {
                    handlePaste()
                } else {
                    pendingPaste = true
                    EditorState.setMode(AppMode.SELECTION)
                }
            }
        }
    }

    private fun onUndoRedoComplete() {
        Log.d(TAG, "onUndoRedoComplete")
        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
            EpdController.enablePost(sv, 1)
            renderToScreen(sv, bitmap)
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

    override fun postFullEinkRefresh() {
        Log.d(TAG, "postFullEinkRefresh")
        val sv = surfaceView ?: return
        getRxManager().enqueue(object : com.onyx.android.sdk.rx.RxRequest() {
            override fun execute() {
                EpdController.refreshScreen(sv, UpdateMode.GC)
            }
        }, null)
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

    override fun onTemplateChanged(template: PaperTemplate) {
        Log.d(TAG, "onTemplateChanged: $template")
        drawingPipeline.currentTemplate = template
    }

    override fun enterNewMode(mode: AppMode) {
        Log.d(TAG, "enterNewMode $mode")
        when (mode) {
            AppMode.DRAWING -> updateTouchHelperWithProfile()
            AppMode.SELECTION -> {
                savedPenProfile = currentPenProfile
                if (circleSelectPreloaded) {
                    circleSelectPreloaded = false
                    // Selection state already populated by circle gesture — don't reset it
                } else {
                    selectionSubState = SelectionSubState.DRAWING_LASSO
                    selectedShapes.clear()
                    selectionBoundingRectNote = null
                    EditorState.setHasSelection(false)
                }
                // Update field directly for timing safety, then emit to StateFlow for UI
                currentPenProfile = SELECTION_LASSO_PROFILE
                EditorState.setPenProfile(SELECTION_LASSO_PROFILE)
                // observePenProfile triggers updateTouchHelperWithProfile → re-enables raw drawing
                if (pendingPaste) {
                    pendingPaste = false
                    handlePaste()
                }
                // Re-render selection overlay after pen profile is configured (needed for circle-to-select)
                if (selectionSubState == SelectionSubState.SELECTED) {
                    renderBitmapWithSelectionOverlay()
                }
            }
            AppMode.SEPARATION -> {
                savedPenProfile = currentPenProfile
                separationSubState = SeparationSubState.DRAWING_SPLIT_LINE
                separationShapes = emptyList()
                separationBackgroundBitmap?.recycle()
                separationBackgroundBitmap = null
                separationGhostBitmap?.recycle()
                separationGhostBitmap = null
                splitLineY = 0f
                currentPenProfile = SELECTION_LASSO_PROFILE
                EditorState.setPenProfile(SELECTION_LASSO_PROFILE)
            }
            AppMode.TEXT -> {
                savedPenProfile = currentPenProfile
                disableRawDrawing()
            }
            AppMode.GEOMETRY -> {
                savedPenProfile = currentPenProfile
                // Enable raw drawing to capture touch points, but suppress Onyx ink rendering
                updateTouchHelperWithProfile()
                onyxTouchHelper?.isRawDrawingRenderEnabled = false
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
                EditorState.setHasSelection(false)
                savedPenProfile?.let {
                    // Update field directly for timing safety before enterNewMode(DRAWING)
                    currentPenProfile = it
                    EditorState.setPenProfile(it)
                }
                savedPenProfile = null
                forceScreenRefresh()
            }
            AppMode.SEPARATION -> {
                separationShapes = emptyList()
                separationBackgroundBitmap?.recycle()
                separationBackgroundBitmap = null
                separationGhostBitmap?.recycle()
                separationGhostBitmap = null
                separationSubState = SeparationSubState.DRAWING_SPLIT_LINE
                savedPenProfile?.let {
                    currentPenProfile = it
                    EditorState.setPenProfile(it)
                }
                savedPenProfile = null
                forceScreenRefresh()
            }
            AppMode.TEXT -> {
                savedPenProfile?.let {
                    currentPenProfile = it
                    EditorState.setPenProfile(it)
                }
                savedPenProfile = null
                forceScreenRefresh()
            }
            AppMode.GEOMETRY -> {
                geometrySnapshotBitmap?.recycle()
                geometrySnapshotBitmap = null
                geometryStartPoint = null
                savedPenProfile?.let {
                    currentPenProfile = it
                    EditorState.setPenProfile(it)
                }
                savedPenProfile = null
                forceScreenRefresh()
            }
            else -> {}
        }
    }

    // ── Text mode ─────────────────────────────────────────────────────────────

    override fun handleModeSpecificGesture(event: GestureEvent): Boolean {
        Log.d(TAG, "handleModeSpecificGesture mode=${EditorState.currentMode.value}")
        if (EditorState.currentMode.value == AppMode.TEXT &&
            event is GestureEvent.Tap &&
            event.fingerCount == 1
        ) {
            showTextInputDialog(event.x, event.y)
            return true
        }
        return false
    }

    private fun showTextInputDialog(screenX: Float, screenY: Float) {
        Log.d(TAG, "showTextInputDialog screenX=$screenX screenY=$screenY")
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(32, 16, 32, 16)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter text")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotBlank()) placeText(text, screenX, screenY)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun placeText(text: String, screenX: Float, screenY: Float) {
        Log.d(TAG, "placeText text='$text' screenX=$screenX screenY=$screenY")
        val profile = EditorState.textProfile.value
        val noteX = viewportManager.viewportToNoteX(screenX)
        val noteY = viewportManager.viewportToNoteY(screenY)
        val shape = TextShape().apply {
            this.text = text
            strokeWidth = profile.fontSize
            strokeColor = profile.color.toArgb()
            fontFamily = profile.fontFamily
            shapeType = ShapeFactory.SHAPE_TEXT
            val tpl = TouchPointList()
            tpl.add(TouchPoint(noteX, noteY, 1f, 0f, 0, 0, System.currentTimeMillis()))
            touchPointList = tpl
            updateShapeRect()
            entityId = NanoIdUtils.randomNanoId()
        }
        drawingPipeline.addShape(shape)
        actionManager.recordAction(DrawAction(shape, drawingPipeline))
        forceScreenRefresh()
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        drawingPipeline.clearShapes()
        htrRunManager.close()
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
            if (sv.width <= 0 || sv.height <= 0) return
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

    // ── Stroke suppression ────────────────────────────────────────────────────

    /**
     * Swallows the current in-progress stroke: disables ink rendering for its duration
     * and skips its data. Runs [onPenLift] when the pen lifts (onEndRawDrawing).
     *
     * Use this whenever a touch should cancel a mode or dismiss UI without leaving a mark.
     * To add a new case: call suppressCurrentStroke { <action> } from onBeginRawDrawing.
     */
    private fun suppressCurrentStroke(onPenLift: () -> Unit) {
        onyxTouchHelper?.isRawDrawingRenderEnabled = false
        strokeDataSuppressed = true
        strokeEndAction = onPenLift
    }

    // ── Onyx RawInputCallback ─────────────────────────────────────────────────

    private fun createOnyxCallback() = object : com.onyx.android.sdk.pen.RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawDrawing mode=${EditorState.currentMode.value}")
            // Safety net: clear any stale suppression state from a previous stroke
            strokeDataSuppressed = false
            strokeEndAction = null
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
                            suppressCurrentStroke { EditorState.setMode(AppMode.DRAWING) }
                        }
                    }
                    else -> { /* DRAWING_LASSO: SDK renders lasso stroke naturally */ }
                }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            if (EditorState.currentMode.value == AppMode.SEPARATION) {
                if (separationSubState == SeparationSubState.DRAGGING_OFFSET) {
                    // Suppress ink rendering — we handle the ghost rendering ourselves
                    onyxTouchHelper?.isRawDrawingRenderEnabled = false
                }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            if (EditorState.currentMode.value == AppMode.GEOMETRY) {
                onyxTouchHelper?.isRawDrawingRenderEnabled = false
                createDrawingBitmap()
                geometryStartPoint = touchPoint
                geometrySnapshotBitmap?.recycle()
                geometrySnapshotBitmap = bitmap?.let { bmp -> bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            isDrawingInProgress = true
            disableFingerTouch()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawDrawing")
            strokeEndAction?.let { action ->
                strokeEndAction = null
                // Pen has lifted — safe to run the deferred action now.
                // updateTouchHelper (triggered by any mode change inside action) will
                // re-enable isRawDrawingRenderEnabled as part of the mode transition.
                action()
            }
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
                return
            }
            if (EditorState.currentMode.value == AppMode.SEPARATION
                && separationSubState == SeparationSubState.DRAGGING_OFFSET
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastSeparationRenderTime < 150L) return
                lastSeparationRenderTime = now
                touchPoint?.let { tp ->
                    val noteY = viewportManager.viewportToNoteY(tp.y)
                    val offsetNoteY = maxOf(0f, noteY - splitLineY)
                    val offsetViewportY = offsetNoteY * viewportManager.scale
                    renderSeparationPreview(offsetViewportY)
                }
                return
            }
            if (EditorState.currentMode.value == AppMode.GEOMETRY) {
                val now = SystemClock.uptimeMillis()
                if (now - lastGeometryRenderTime < 50L) return
                lastGeometryRenderTime = now
                touchPoint?.let { tp -> renderGeometryPreview(tp) }
            }
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawDrawingTouchPointListReceived")
            if (strokeDataSuppressed) {
                strokeDataSuppressed = false
                return
            }
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
            if (EditorState.currentMode.value == AppMode.SEPARATION) {
                touchPointList?.let { tpl ->
                    when (separationSubState) {
                        SeparationSubState.DRAWING_SPLIT_LINE -> handleSplitLineDrawn(tpl)
                        SeparationSubState.DRAGGING_OFFSET -> {
                            val lastPt = tpl.points.lastOrNull()
                            val noteY = viewportManager.viewportToNoteY(lastPt?.y ?: 0f)
                            val offsetNoteY = maxOf(0f, noteY - splitLineY)
                            commitSeparation(offsetNoteY)
                        }
                    }
                }
                return
            }
            if (EditorState.currentMode.value == AppMode.GEOMETRY) {
                if (strokeDataSuppressed) {
                    strokeDataSuppressed = false
                    return
                }
                val startPt = geometryStartPoint ?: return
                val endPt = touchPointList?.points?.lastOrNull() ?: return
                commitGeometryShape(startPt, endPt)
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
        val sv = surfaceView ?: return
        createDrawingBitmap()
        val bmp = bitmap ?: return
        val shape = drawingPipeline.drawScribbleToBitmap(touchPointList, bmp, currentPenProfile)
        renderToScreen(sv, bitmap)

        val noteId = drawingPipeline.noteId
        val appSettings = (application as ScrotesApp).appSettings

        lifecycleScope.launch(Dispatchers.Default) {
            // Check scribble-to-erase
            if (appSettings.scribbleToEraseEnabled && htrRunManager.isScribbleGesture(shape)) {
                val covered = ShapeGeometryUtils.findShapesCoveredByScribble(
                    shape, drawingPipeline.getShapes()
                )
                if (covered.isNotEmpty()) {
                    Log.d(TAG, "Scribble-to-erase: erasing ${covered.size} shape(s)")
                    withContext(Dispatchers.Main) {
                        drawingPipeline.removeShape(shape)
                        for (coveredShape in covered) {
                            drawingPipeline.removeShape(coveredShape)
                        }
                        actionManager.recordAction(DrawAction(shape, drawingPipeline))
                        actionManager.recordAction(ScribbleEraseAction(shape, covered, drawingPipeline))
                        val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
                        bitmap = state.bitmap
                        bitmapCanvas = state.canvas
                        EpdController.enablePost(sv, 1)
                        renderToScreen(sv, bitmap)
                    }
                    return@launch
                }
            }

            // Check circle-to-select
            if (appSettings.circleToSelectEnabled && htrRunManager.isCircleGesture(shape)) {
                val encircled = ShapeGeometryUtils.findShapesEncircledBy(
                    shape, drawingPipeline.getShapes()
                )
                if (encircled.isNotEmpty()) {
                    Log.d(TAG, "Circle-to-select: selecting ${encircled.size} shape(s)")
                    withContext(Dispatchers.Main) {
                        drawingPipeline.removeShape(shape)
                        selectedShapes = encircled.toMutableList()
                        selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)
                        selectionSubState = SelectionSubState.SELECTED
                        EditorState.setHasSelection(true)
                        circleSelectPreloaded = true
                        EditorState.setMode(AppMode.SELECTION)
                        val encircledIds = encircled.mapNotNull { it.entityId }
                        actionManager.recordAction(DrawAction(shape, drawingPipeline))
                        actionManager.recordAction(CircleSelectAction(
                            circleShape = shape,
                            encircledShapeIds = encircledIds,
                            pipeline = drawingPipeline,
                            onUndoCallback = {
                                withContext(Dispatchers.Main) {
                                    EditorState.setMode(AppMode.DRAWING)
                                }
                            },
                            onRedoCallback = { encircledShapes ->
                                withContext(Dispatchers.Main) {
                                    selectedShapes = encircledShapes.toMutableList()
                                    selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)
                                    selectionSubState = SelectionSubState.SELECTED
                                    EditorState.setHasSelection(true)
                                    circleSelectPreloaded = true
                                    EditorState.setMode(AppMode.SELECTION)
                                }
                            }
                        ))
                        val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
                        bitmap = state.bitmap
                        bitmapCanvas = state.canvas
                        EpdController.enablePost(sv, 1)
                        renderBitmapWithSelectionOverlay()
                    }
                    return@launch
                }
            }

            // Normal shape
            withContext(Dispatchers.Main) {
                actionManager.recordAction(DrawAction(shape, drawingPipeline))
                if (noteId != null) {
                    htrRunManager.addShapeForRecognition(noteId, shape)
                }
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
                val erased = drawingPipeline.lastErasedShapes
                if (erased.isNotEmpty()) {
                    actionManager.recordAction(EraseAction(erased, drawingPipeline))
                }
            }
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private fun renderGeometryPreview(currentVpPt: com.onyx.android.sdk.data.note.TouchPoint) {
        Log.d(TAG, "renderGeometryPreview")
        val sv = surfaceView ?: return
        val snapshot = geometrySnapshotBitmap ?: return
        val startVpPt = geometryStartPoint ?: return
        val bmp = bitmap ?: return

        // Restore snapshot onto current bitmap
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawBitmap(snapshot, 0f, 0f, null)

        // Convert viewport → note coords for storage, then back to viewport for drawing
        // Since we draw directly on the bitmap (already in viewport space), use vp coords
        val shapeType = EditorState.activeGeometryShape.value
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = currentPenProfile.getColorAsInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = currentPenProfile.strokeWidth * viewportManager.scale
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        drawGeometryShapeOnCanvas(canvas, shapeType, startVpPt, currentVpPt, paint)

        EpdController.enablePost(sv, 1)
        renderToScreen(sv, bmp)
    }

    private fun drawGeometryShapeOnCanvas(
        canvas: android.graphics.Canvas,
        shapeType: GeometryShapeType,
        startPt: com.onyx.android.sdk.data.note.TouchPoint,
        endPt: com.onyx.android.sdk.data.note.TouchPoint,
        paint: android.graphics.Paint
    ) {
        val sx = startPt.x; val sy = startPt.y
        val ex = endPt.x;   val ey = endPt.y
        val dx = ex - sx;   val dy = ey - sy
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < 1f) return

        when (shapeType) {
            GeometryShapeType.CIRCLE -> {
                canvas.drawCircle(sx, sy, dist, paint)
            }
            GeometryShapeType.LINE -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
            }
            GeometryShapeType.RECTANGLE -> {
                val aspectRatio = 1.618f
                val halfH = dist / kotlin.math.sqrt(1f + aspectRatio * aspectRatio)
                val halfW = halfH * aspectRatio
                val path = android.graphics.Path().apply {
                    addRect(android.graphics.RectF(-halfW, -halfH, halfW, halfH), android.graphics.Path.Direction.CW)
                }
                val angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val matrix = android.graphics.Matrix()
                matrix.postRotate(angleDeg)
                matrix.postTranslate(sx, sy)
                path.transform(matrix)
                canvas.drawPath(path, paint)
            }
            GeometryShapeType.TRIANGLE -> {
                val baseAngle = kotlin.math.atan2(dy, dx)
                val twoThirdsPi = (2.0 * Math.PI / 3.0).toFloat()
                val x0 = sx + dist * kotlin.math.cos(baseAngle)
                val y0 = sy + dist * kotlin.math.sin(baseAngle)
                val x1 = sx + dist * kotlin.math.cos(baseAngle + twoThirdsPi)
                val y1 = sy + dist * kotlin.math.sin(baseAngle + twoThirdsPi)
                val x2 = sx + dist * kotlin.math.cos(baseAngle - twoThirdsPi)
                val y2 = sy + dist * kotlin.math.sin(baseAngle - twoThirdsPi)
                val path = android.graphics.Path().apply {
                    moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close()
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun commitGeometryShape(
        vpStart: com.onyx.android.sdk.data.note.TouchPoint,
        vpEnd: com.onyx.android.sdk.data.note.TouchPoint
    ) {
        Log.d(TAG, "commitGeometryShape")
        val sv = surfaceView ?: return

        // Restore clean snapshot before committing so pipeline recreates from shapes
        geometrySnapshotBitmap?.let { snapshot ->
            val bmp = bitmap
            if (bmp != null) {
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawBitmap(snapshot, 0f, 0f, null)
            }
            snapshot.recycle()
            geometrySnapshotBitmap = null
        }
        geometryStartPoint = null

        // Convert viewport to note coords
        val noteStartX = viewportManager.viewportToNoteX(vpStart.x)
        val noteStartY = viewportManager.viewportToNoteY(vpStart.y)
        val noteEndX = viewportManager.viewportToNoteX(vpEnd.x)
        val noteEndY = viewportManager.viewportToNoteY(vpEnd.y)

        val geometryShapeType = EditorState.activeGeometryShape.value
        val shapeTypeInt = when (geometryShapeType) {
            GeometryShapeType.CIRCLE    -> ShapeFactory.SHAPE_GEOMETRY_CIRCLE
            GeometryShapeType.LINE      -> ShapeFactory.SHAPE_GEOMETRY_LINE
            GeometryShapeType.RECTANGLE -> ShapeFactory.SHAPE_GEOMETRY_RECTANGLE
            GeometryShapeType.TRIANGLE  -> ShapeFactory.SHAPE_GEOMETRY_TRIANGLE
        }

        val ts = System.currentTimeMillis()
        val tpl = TouchPointList()
        tpl.add(com.onyx.android.sdk.data.note.TouchPoint(noteStartX, noteStartY, 1f, 0f, 0, 0, ts))
        tpl.add(com.onyx.android.sdk.data.note.TouchPoint(noteEndX, noteEndY, 1f, 0f, 0, 0, ts))
        appendGeometryOutlinePoints(tpl, geometryShapeType, noteStartX, noteStartY, noteEndX, noteEndY, ts)

        val shape = ShapeFactory.createShape(shapeTypeInt).apply {
            shapeType = shapeTypeInt
            strokeColor = currentPenProfile.getColorAsInt()
            strokeWidth = currentPenProfile.strokeWidth
            touchPointList = tpl
            updateShapeRect()
            entityId = NanoIdUtils.randomNanoId()
        }

        drawingPipeline.addShape(shape)
        actionManager.recordAction(DrawAction(shape, drawingPipeline))

        val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
        bitmap = state.bitmap
        bitmapCanvas = state.canvas
        EpdController.enablePost(sv, 1)
        renderToScreen(sv, bitmap)
    }

    /**
     * Appends representative boundary points to [tpl] after the 2 defining points.
     * These extra points are used by SelectionManager's lasso hit-test
     * (which checks that ALL touchPointList points are inside the lasso).
     * render() in each geometry shape class only reads pts[0] and pts[1], so
     * extra points here do not affect drawing.
     */
    private fun appendGeometryOutlinePoints(
        tpl: TouchPointList,
        shapeType: GeometryShapeType,
        sx: Float, sy: Float,
        ex: Float, ey: Float,
        ts: Long
    ) {
        Log.d(TAG, "appendGeometryOutlinePoints shapeType=$shapeType")
        val dx = ex - sx; val dy = ey - sy
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < 1f) return

        fun addPt(x: Float, y: Float) {
            tpl.add(com.onyx.android.sdk.data.note.TouchPoint(x, y, 1f, 0f, 0, 0, ts))
        }

        when (shapeType) {
            GeometryShapeType.CIRCLE -> {
                // 24 points evenly distributed around the circumference
                val steps = 24
                for (i in 0 until steps) {
                    val angle = i * 2.0 * Math.PI / steps
                    addPt(
                        sx + dist * kotlin.math.cos(angle).toFloat(),
                        sy + dist * kotlin.math.sin(angle).toFloat()
                    )
                }
            }
            GeometryShapeType.LINE -> {
                // The 2 endpoints (pts[0], pts[1]) already fully define the lasso extent
            }
            GeometryShapeType.RECTANGLE -> {
                val aspectRatio = 1.618f
                val halfH = dist / kotlin.math.sqrt(1f + aspectRatio * aspectRatio)
                val halfW = halfH * aspectRatio
                val angle = kotlin.math.atan2(dy, dx)
                val cosA = kotlin.math.cos(angle)
                val sinA = kotlin.math.sin(angle)
                // The 4 corners of the rotated rectangle
                for ((lx, ly) in listOf(
                    Pair(+halfW, +halfH), Pair(-halfW, +halfH),
                    Pair(-halfW, -halfH), Pair(+halfW, -halfH)
                )) {
                    addPt(
                        sx + lx * cosA - ly * sinA,
                        sy + lx * sinA + ly * cosA
                    )
                }
            }
            GeometryShapeType.TRIANGLE -> {
                // The 3 equilateral triangle vertices
                val baseAngle = kotlin.math.atan2(dy, dx)
                val twoThirdsPi = 2.0 * Math.PI / 3.0
                for (i in 0..2) {
                    val a = baseAngle + i * twoThirdsPi
                    addPt(
                        sx + dist * kotlin.math.cos(a).toFloat(),
                        sy + dist * kotlin.math.sin(a).toFloat()
                    )
                }
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
            EditorState.setHasSelection(true)
            Log.d(TAG, "handleLassoComplete selected ${selectedShapes.size} shapes")
        } else {
            selectionSubState = SelectionSubState.DRAWING_LASSO
            EditorState.setHasSelection(false)
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

        val shapesBeforeMove = selectedShapes.toList()
        for (shape in selectedShapes) {
            selectionManager.translateShape(shape, dNoteX, dNoteY)
            drawingPipeline.updateShape(shape)
        }
        actionManager.recordAction(MoveAction(
            shapeIds = shapesBeforeMove.mapNotNull { it.entityId },
            dNoteX = dNoteX,
            dNoteY = dNoteY,
            pipeline = drawingPipeline,
            selectionManager = selectionManager
        ))
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

    // ── Copy / Paste ──────────────────────────────────────────────────────────

    private fun cloneShape(original: com.wyldsoft.notes.shapemanagement.shapes.Shape): com.wyldsoft.notes.shapemanagement.shapes.Shape {
        Log.d(TAG, "cloneShape entityId=${original.entityId}")
        val clone = com.wyldsoft.notes.shapemanagement.ShapeFactory.createShape(original.shapeType)
        clone.shapeType = original.shapeType
        clone.texture = original.texture
        clone.strokeColor = original.strokeColor
        clone.strokeWidth = original.strokeWidth
        clone.penType = original.penType
        val newList = com.onyx.android.sdk.pen.data.TouchPointList()
        original.touchPointList?.points?.forEach { pt ->
            if (pt != null) {
                val newPt = TouchPoint()
                newPt.x = pt.x
                newPt.y = pt.y
                newPt.pressure = pt.pressure
                newPt.tiltX = pt.tiltX
                newPt.tiltY = pt.tiltY
                newPt.timestamp = pt.timestamp
                newList.add(newPt)
            }
        }
        clone.touchPointList = newList
        clone.updateShapeRect()
        // entityId left null — assigned by ShapeMapper.toEntity when persisted
        return clone
    }

    private fun handleCopy() {
        Log.d(TAG, "handleCopy selectedShapes=${selectedShapes.size}")
        if (selectedShapes.isEmpty()) return
        copiedShapes = selectedShapes.map { cloneShape(it) }
        EditorState.setHasCopied(true)
    }

    private fun handlePaste() {
        Log.d(TAG, "handlePaste copiedShapes=${copiedShapes.size}")
        if (copiedShapes.isEmpty()) return
        val sv = surfaceView ?: return

        val centerNoteX = viewportManager.viewportToNoteX(sv.width / 2f)
        val centerNoteY = viewportManager.viewportToNoteY(sv.height / 2f)

        val copyBounds = selectionManager.computeBoundingRect(copiedShapes) ?: return
        val deltaX = centerNoteX - copyBounds.centerX()
        val deltaY = centerNoteY - copyBounds.centerY()

        val pastedShapes = copiedShapes.map { original ->
            val clone = cloneShape(original)
            selectionManager.translateShape(clone, deltaX, deltaY)
            clone
        }

        for (shape in pastedShapes) {
            drawingPipeline.addShape(shape)
        }

        actionManager.recordAction(PasteAction(pastedShapes, drawingPipeline))

        selectedShapes = pastedShapes.toMutableList()
        selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)
        selectionSubState = SelectionSubState.SELECTED
        EditorState.setHasSelection(true)

        val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
        bitmap = state.bitmap
        bitmapCanvas = state.canvas
        EpdController.enablePost(sv, 1)
        renderBitmapWithSelectionOverlay()
    }

    // ─────────────────────────────────────────────────────────────────────────

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

    // ── Separation helpers ────────────────────────────────────────────────────

    private fun handleSplitLineDrawn(touchPointList: TouchPointList) {
        Log.d(TAG, "handleSplitLineDrawn points=${touchPointList.size()}")
        val sv = surfaceView ?: return

        // Compute average Y of the stroke in note-space
        val notePoints = viewportManager.viewportToNoteTouchPoints(touchPointList)
        splitLineY = notePoints.points.mapNotNull { it?.y }.average().toFloat()
        Log.d(TAG, "handleSplitLineDrawn splitLineY=$splitLineY")

        // Find all shapes with any part below the split line
        separationShapes = drawingPipeline.getShapes()
            .filter { (it.boundingRect?.bottom ?: 0f) > splitLineY }
        Log.d(TAG, "handleSplitLineDrawn ${separationShapes.size} shapes below split")

        // Build background (shapes above split only) and ghost (shapes below split only).
        // Pass null so recreateBitmap allocates a fresh bitmap rather than reusing the
        // main offscreen bitmap — we need separationBackgroundBitmap to be a separate object.
        val bgState = drawingPipeline.recreateBitmapExcluding(separationShapes, null, sv.width, sv.height)
        separationBackgroundBitmap = bgState.bitmap

        val ghostState = drawingPipeline.recreateBitmapFromShapes(null, sv.width, sv.height, separationShapes)
        separationGhostBitmap = ghostState.bitmap

        separationSubState = SeparationSubState.DRAGGING_OFFSET
        lastSeparationRenderTime = 0L

        // Re-enable ink rendering for drawing the split line in step 1 was done by SDK;
        // now disable it for step 2 dragging
        onyxTouchHelper?.isRawDrawingRenderEnabled = false

        renderSeparationPreview(0f)
    }

    private fun renderSeparationPreview(offsetViewportY: Float) {
        val sv = surfaceView ?: return
        EpdController.enablePost(sv, 1)
        val canvas = sv.holder.lockCanvas() ?: return
        try {
            val splitViewportY = viewportManager.noteToViewportY(splitLineY)
            canvas.drawColor(Color.WHITE)
            // Draw background (shapes above split)
            separationBackgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            // Draw ghost (shapes below split) shifted down, clipped to below the split line
            separationGhostBitmap?.let { ghost ->
                canvas.save()
                canvas.clipRect(0f, splitViewportY, sv.width.toFloat(), sv.height.toFloat())
                canvas.drawBitmap(ghost, 0f, offsetViewportY, null)
                canvas.restore()
            }
            // Draw split line indicator
            canvas.drawLine(0f, splitViewportY, sv.width.toFloat(), splitViewportY, splitLinePaint)
        } finally {
            sv.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun commitSeparation(rawOffsetY: Float) {
        Log.d(TAG, "commitSeparation rawOffsetY=$rawOffsetY shapes=${separationShapes.size}")
        if (separationShapes.isEmpty() || rawOffsetY <= 0f) {
            EditorState.setMode(AppMode.DRAWING)
            return
        }
        val pm = paginationManager ?: run {
            EditorState.setMode(AppMode.DRAWING)
            return
        }

        // Gap-snapping: find extra Y needed so no shape top lands inside a page gap.
        // Apply the maximum avoidance uniformly to keep relative positions intact.
        val gapAvoidance = separationShapes.maxOf { shape ->
            val newTopY = (shape.boundingRect?.top ?: 0f) + rawOffsetY
            var avoidance = 0f
            for (p in 0 until pm.pageCount) {
                val gapTop = pm.pageBottomY(p)
                val gapBottom = pm.pageTopY(p + 1)
                if (newTopY > gapTop && newTopY < gapBottom) {
                    avoidance = maxOf(avoidance, gapBottom - newTopY)
                }
            }
            avoidance
        }
        val actualOffsetY = rawOffsetY + gapAvoidance
        Log.d(TAG, "commitSeparation actualOffsetY=$actualOffsetY (gapAvoidance=$gapAvoidance)")

        // Auto-create pages if any shape overflows past the last page
        val maxNewBottomY = separationShapes.maxOf { (it.boundingRect?.bottom ?: 0f) + actualOffsetY }
        var pagesAdded = 0
        while (maxNewBottomY > pm.pageBottomY(pm.pageCount - 1)) {
            pm.addPages(1)
            pagesAdded++
        }

        // Translate all shapes down
        for (shape in separationShapes) {
            selectionManager.translateShape(shape, 0f, actualOffsetY)
            drawingPipeline.updateShape(shape)
        }

        // Record undo action
        val shapeIds = separationShapes.mapNotNull { it.entityId }
        actionManager.recordAction(SeparationAction(
            shapeIds = shapeIds,
            deltaY = actualOffsetY,
            pagesAdded = pagesAdded,
            pipeline = drawingPipeline,
            paginationManager = pm,
            selectionManager = selectionManager
        ))

        // Clean up bitmaps before triggering mode exit
        separationShapes = emptyList()
        separationBackgroundBitmap?.recycle()
        separationBackgroundBitmap = null
        separationGhostBitmap?.recycle()
        separationGhostBitmap = null
        EditorState.setMode(AppMode.DRAWING)
    }
}
