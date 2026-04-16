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
import com.wyldsoft.notes.data.database.repository.LayerRepository
import com.wyldsoft.notes.data.database.repository.ShapeRepository
import com.wyldsoft.notes.data.database.repository.UndoHistoryRepository
import com.wyldsoft.notes.layers.LayerManager
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
import com.wyldsoft.notes.undoredo.TransformAction
import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.wyldsoft.notes.data.database.repository.HtrResultRepository
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.htr.ShapeGeometryUtils
import com.wyldsoft.notes.utils.copyWith
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
    private lateinit var htrRunManager: HTRRunManager
    private var currentPdfPageRenderer: com.wyldsoft.notes.pdf.PdfPageRenderer? = null
    private lateinit var layerRepo: LayerRepository
    private lateinit var layerManager: LayerManager
    private var pendingExportFile: java.io.File? = null

    private val saveFileLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) {
            Log.d(TAG, "saveFileLauncher: user cancelled")
            return@registerForActivityResult
        }
        val file = pendingExportFile ?: return@registerForActivityResult
        pendingExportFile = null
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    java.io.FileInputStream(file).use { it.copyTo(out) }
                }
                Log.d(TAG, "saveFileLauncher: saved to $uri")
            } catch (e: Exception) {
                Log.e(TAG, "saveFileLauncher: write failed", e)
            }
        }
    }

    // ── Selection state ───────────────────────────────────────────────────────
    private enum class SelectionSubState { DRAWING_LASSO, SELECTED, MOVING, STRETCHING, ROTATING }
    private enum class HandleType {
        CORNER_TL, CORNER_TR, CORNER_BL, CORNER_BR,
        MIDPOINT_T, MIDPOINT_B, MIDPOINT_L, MIDPOINT_R,
        ROTATE
    }
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
    // Transform (stretch / rotate) state
    private var activeHandle: HandleType? = null
    private var transformSnapshotBitmap: Bitmap? = null   // full bitmap before transform started
    private var selectionCropBitmap: Bitmap? = null        // cropped selection region
    private var selectionCropLeft = 0f
    private var selectionCropTop = 0f
    private var transformStartX = 0f
    private var transformStartY = 0f
    private var originalBoundingRectNote: RectF? = null    // note-space bounds before transform
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
    private val handleFillPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleStrokePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val handleStemPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val HANDLE_RADIUS = 22f
    private val HANDLE_HIT_RADIUS = 40f
    private val ROTATE_HANDLE_OFFSET = 70f

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

    private val searchHighlightPaint = Paint().apply {
        color = Color.argb(200, 255, 200, 0)   // semi-transparent yellow
        style = Paint.Style.STROKE
        strokeWidth = 8f
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
        layerRepo = LayerRepository(db.layerDao())
        layerManager = LayerManager(layerRepo, shapeRepo)
        htrRunManager = HTRRunManager(htrResultRepository = HtrResultRepository(db.htrResultDao()))
        val noteId = intent.getStringExtra("noteId")
        if (noteId != null) setupPipelineForNote(noteId)
        observeUndoRedo()
        observeExportPdf()
        observeLayerOperations()
        observeSearchNavigation()
    }

    override fun onSwitchNoteSDK(noteId: String) {
        Log.d(TAG, "onSwitchNoteSDK: $noteId")
        EditorState.setUndoRedoState(false, false)
        setupPipelineForNote(noteId)
    }

    /**
     * Creates a fresh DrawingPipeline and ActionManager for [noteId], then launches
     * an IO coroutine to load shapes and action history from the database.
     * Called both on initial launch (initializeSDK) and when switching notes (onSwitchNoteSDK).
     */
    private fun setupPipelineForNote(noteId: String) {
        Log.d(TAG, "setupPipelineForNote: $noteId")
        drawingPipeline = DrawingPipeline(
            viewportManager = viewportManager,
            scope = lifecycleScope,
            shapeRepository = shapeRepo,
            noteId = noteId
        )
        Log.d(TAG, "setupPipelineForNote 1")
        drawingPipeline.paginationManager = paginationManager
        actionManager = ActionManager(
            undoHistoryRepository = undoHistoryRepo,
            noteId = noteId,
            scope = lifecycleScope,
            selectionManager = selectionManager,
            paginationManager = paginationManager
        )
        Log.d(TAG, "setupPipelineForNote 2")
        shapesLoaded = false
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "setupPipelineForNote 3")
            // Load note entity to wire up PDF page renderer if this is a PDF-backed note
            val note = noteRepository?.getById(noteId)
            val pdfPath = note?.pdfPath
            if (pdfPath != null) {
                val uri = android.net.Uri.parse(pdfPath)
                currentPdfPageRenderer?.close()
                val renderer = com.wyldsoft.notes.pdf.PdfPageRenderer(this@OnyxDrawingActivity, uri)
                currentPdfPageRenderer = renderer
                drawingPipeline.pdfPageRenderer = renderer
            } else {
                currentPdfPageRenderer?.close()
                currentPdfPageRenderer = null
                drawingPipeline.pdfPageRenderer = null
            }
            Log.d(TAG, "setupPipelineForNote 4")
            drawingPipeline.loadShapes(noteId)
            Log.d(TAG, "setupPipelineForNote 5")
            actionManager.loadFromDatabase(drawingPipeline)
            Log.d(TAG, "setupPipelineForNote 6")
            // Load layers and reset active layer to 1 on note switch
            val loadedLayers = layerManager.loadLayersForNote(noteId)
            Log.d(TAG, "setupPipelineForNote 7")
            EditorState.setLayers(loadedLayers)
            EditorState.setActiveLayer(1)
            shapesLoaded = true
            Log.d(TAG, "forceScreenRefresh from loading note")
            Log.d(TAG, "setupPipelineForNote 8")
            launch(Dispatchers.Main) {
                Log.d(TAG, "setupPipelineForNote 9")
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

    private fun observeExportPdf() {
        Log.d(TAG, "observeExportPdf")
        lifecycleScope.launch {
            EditorState.exportPdfRequested.collect {
                val pm = paginationManager ?: run {
                    Log.w(TAG, "observeExportPdf: no paginationManager, using single-page fallback")
                    val dm = resources.displayMetrics
                    val sv = surfaceView
                    val w = sv?.width?.takeIf { it > 0 } ?: dm.widthPixels
                    val h = sv?.height?.takeIf { it > 0 } ?: dm.heightPixels
                    com.wyldsoft.notes.rendering.PaginationManager(w, h, dm.density)
                }
                val shapes = drawingPipeline.getShapes()
                val pdfUriStr = EditorState.pdfPath.value
                val pdfUri = if (pdfUriStr != null) android.net.Uri.parse(pdfUriStr) else null
                val noteId = currentNoteId ?: return@collect
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val file = com.wyldsoft.notes.pdf.PdfExporter.export(
                            context = applicationContext,
                            noteId = noteId,
                            pdfUri = pdfUri,
                            shapes = shapes,
                            paginationManager = pm,
                            template = drawingPipeline.currentTemplate
                        )
                        launch(Dispatchers.Main) { showExportDialog(file) }
                    } catch (e: Exception) {
                        Log.e(TAG, "PDF export failed", e)
                    }
                }
            }
        }
    }

    private fun observeLayerOperations() {
        Log.d(TAG, "observeLayerOperations")
        lifecycleScope.launch {
            EditorState.addLayerRequested.collect {
                val noteId = EditorState.currentNoteId ?: return@collect
                val newLayer = layerManager.addLayer(noteId)
                val updatedLayers = layerManager.loadLayersForNote(noteId)
                EditorState.setLayers(updatedLayers)
                EditorState.setActiveLayer(newLayer.position)
            }
        }
        lifecycleScope.launch {
            EditorState.deleteLayerRequested.collect { layer ->
                val db = (application as ScrotesApp).database
                layerManager.deleteLayer(layer, db.shapeDao())
                val noteId = EditorState.currentNoteId ?: return@collect
                val updatedLayers = layerManager.loadLayersForNote(noteId)
                EditorState.setLayers(updatedLayers)
                // If active layer was the deleted one, switch to layer 1
                if (EditorState.activeLayer.value == layer.position) {
                    EditorState.setActiveLayer(1)
                }
                // Remove shapes from in-memory pipeline for deleted layer
                val shapesToRemove = drawingPipeline.getShapes().filter { it.layer == layer.position }
                shapesToRemove.forEach { drawingPipeline.removeShape(it) }
                withContext(Dispatchers.Main) { forceScreenRefresh() }
            }
        }
        lifecycleScope.launch {
            EditorState.renameLayerRequested.collect { (layer, newName) ->
                layerManager.renameLayer(layer, newName)
                val noteId = EditorState.currentNoteId ?: return@collect
                val updatedLayers = layerManager.loadLayersForNote(noteId)
                EditorState.setLayers(updatedLayers)
            }
        }
        lifecycleScope.launch {
            EditorState.toggleLayerVisibilityRequested.collect { layer ->
                layerManager.toggleVisibility(layer)
                val noteId = EditorState.currentNoteId ?: return@collect
                val updatedLayers = layerManager.loadLayersForNote(noteId)
                EditorState.setLayers(updatedLayers)
                withContext(Dispatchers.Main) { forceScreenRefresh() }
            }
        }
    }

    private fun observeSearchNavigation() {
        Log.d(TAG, "observeSearchNavigation")
        lifecycleScope.launch {
            EditorState.navigateToSearchHit.collect { hit ->
                val padding = 80f
                viewportManager.scrollToY((hit.boundingBox.top - padding).coerceAtLeast(0f))
                EditorState.setSearchHighlight(hit.boundingBox)
                withContext(Dispatchers.Main) { forceScreenRefresh() }
                // Clear highlight after 2 seconds
                launch {
                    kotlinx.coroutines.delay(2000)
                    EditorState.setSearchHighlight(null)
                    withContext(Dispatchers.Main) { forceScreenRefresh() }
                }
            }
        }
    }

    override fun onSearchQueryChanged(query: String) {
        Log.d(TAG, "onSearchQueryChanged query='$query'")
        executeSearch(query)
    }

    fun executeSearch(query: String) {
        Log.d(TAG, "executeSearch query='$query'")
        val noteId = EditorState.currentNoteId ?: return
        val db = (application as ScrotesApp).database
        val htrRepo = HtrResultRepository(db.htrResultDao())

        lifecycleScope.launch(Dispatchers.IO) {
            val hits = mutableListOf<com.wyldsoft.notes.editor.SearchHit>()

            // HTR results from DB
            if (query.isNotBlank()) {
                val htrResults = htrRepo.getByNoteId(noteId)
                    .filter { it.text.contains(query, ignoreCase = true) }
                for (result in htrResults) {
                    hits.add(
                        com.wyldsoft.notes.editor.SearchHit(
                            text = result.text,
                            boundingBox = android.graphics.RectF(
                                result.boundingLeft, result.boundingTop,
                                result.boundingRight, result.boundingBottom
                            )
                        )
                    )
                }

                // TextShapes from in-memory pipeline
                val textShapes = drawingPipeline.getShapes()
                    .filterIsInstance<com.wyldsoft.notes.shapemanagement.shapes.TextShape>()
                    .filter { it.text.contains(query, ignoreCase = true) }
                for (shape in textShapes) {
                    val pts = shape.touchPointList?.points?.filterNotNull() ?: continue
                    if (pts.isEmpty()) continue
                    var left = pts[0].x; var top = pts[0].y
                    var right = left; var bottom = top
                    for (p in pts) {
                        if (p.x < left) left = p.x
                        if (p.y < top) top = p.y
                        if (p.x > right) right = p.x
                        if (p.y > bottom) bottom = p.y
                    }
                    // Ensure minimum hit rect height for legible text
                    if (bottom - top < 40f) bottom = top + 40f
                    if (right - left < 40f) right = left + 40f
                    hits.add(
                        com.wyldsoft.notes.editor.SearchHit(
                            text = shape.text,
                            boundingBox = android.graphics.RectF(left, top, right, bottom)
                        )
                    )
                }
            }

            // Sort by Y position (top of bounding box)
            hits.sortBy { it.boundingBox.top }
            withContext(Dispatchers.Main) {
                EditorState.setSearchHits(hits)
            }
        }
    }

    private fun showExportDialog(file: java.io.File) {
        Log.d(TAG, "showExportDialog: ${file.absolutePath}")
        val noteTitle = EditorState.currentNoteId ?: "note"
        val suggestedName = "$noteTitle.pdf"

        android.app.AlertDialog.Builder(this)
            .setTitle("Export PDF")
            .setItems(arrayOf("Share", "Save to file")) { _, which ->
                when (which) {
                    0 -> sharePdfFile(file)
                    1 -> {
                        pendingExportFile = file
                        saveFileLauncher.launch(suggestedName)
                    }
                }
            }
            .show()
    }

    private fun sharePdfFile(file: java.io.File) {
        Log.d(TAG, "sharePdfFile: ${file.absolutePath}")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share PDF"))
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
        // Re-wire pdf renderer in case pagination recreated the manager
        drawingPipeline.pdfPageRenderer = currentPdfPageRenderer
    }

    override fun onTemplateChanged(template: PaperTemplate) {
        Log.d(TAG, "onTemplateChanged: $template")
        drawingPipeline.currentTemplate = template
    }

    override fun enterNewMode(mode: AppMode) {
        Log.d(TAG, "enterNewMode $mode")
        when (mode) {
            AppMode.DRAWING -> updateTouchHelperWithProfile()
            AppMode.ERASER -> {
                savedPenProfile = currentPenProfile
                // Enable raw drawing to capture pen touch points, but suppress Onyx ink rendering
                updateTouchHelperWithProfile()
                disableInkRendering()
            }
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
                disableInkRendering()
            }
            else -> {}
        }
    }

    /** Restores the pen profile saved when a mode was entered, then triggers a screen refresh. */
    private fun restoreSavedPenProfile() {
        savedPenProfile?.let {
            // Update field directly for timing safety before enterNewMode(DRAWING)
            currentPenProfile = it
            EditorState.setPenProfile(it)
        }
        savedPenProfile = null
        forceScreenRefresh()
    }

    override fun exitCurrentMode(mode: AppMode) {
        Log.d(TAG, "exitCurrentMode $mode")
        if (isInMode(mode)) return
        when (mode) {
            AppMode.DRAWING -> disableRawDrawing()
            AppMode.ERASER -> restoreSavedPenProfile()
            AppMode.SELECTION -> {
                selectedShapes.clear()
                selectionBoundingRectNote = null
                ghostBitmap?.recycle()
                ghostBitmap = null
                selectionCropBitmap?.recycle()
                selectionCropBitmap = null
                transformSnapshotBitmap?.recycle()
                transformSnapshotBitmap = null
                activeHandle = null
                selectionSubState = SelectionSubState.DRAWING_LASSO
                EditorState.setHasSelection(false)
                restoreSavedPenProfile()
            }
            AppMode.SEPARATION -> {
                separationShapes = emptyList()
                separationBackgroundBitmap?.recycle()
                separationBackgroundBitmap = null
                separationGhostBitmap?.recycle()
                separationGhostBitmap = null
                separationSubState = SeparationSubState.DRAWING_SPLIT_LINE
                restoreSavedPenProfile()
            }
            AppMode.TEXT -> {
                restoreSavedPenProfile()
            }
            AppMode.GEOMETRY -> {
                geometrySnapshotBitmap?.recycle()
                geometrySnapshotBitmap = null
                geometryStartPoint = null
                restoreSavedPenProfile()
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
            .setLimitRect(limit, ArrayList(excludeRects))
            .openRawDrawing()
            .setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            .setStrokeColor(currentPenProfile.getColorAsInt())
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

            // Draw search highlight rect on the bitmap if active
            val highlightNoteRect = EditorState.searchHighlightNoteRect.value
            if (highlightNoteRect != null) {
                val vpRect = viewportManager.noteToViewport(highlightNoteRect)
                bitmapCanvas?.drawRect(vpRect, searchHighlightPaint)
            }

            EpdController.enablePost(sv, 1)
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    override fun recreateBitmapAtCurrentViewport() {
        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height, skipTemplate = isPanningGesture)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
        }
    }

    /** Enables only the Onyx ink rendering layer. Input capture is unaffected. */
    private fun enableInkRendering() {
        onyxTouchHelper?.isRawDrawingRenderEnabled = true
    }

    /** Disables only the Onyx ink rendering layer. Input capture is unaffected. */
    private fun disableInkRendering() {
        onyxTouchHelper?.isRawDrawingRenderEnabled = false
    }

    override fun enableRawDrawing() {
        enableInkRendering()
        onyxTouchHelper?.setRawDrawingEnabled(true)
    }

    override fun disableRawDrawing() {
        disableInkRendering()
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
        disableInkRendering()
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
                        val handle = findHandleAtPoint(tp.x, tp.y)
                        if (handle != null) {
                            startTransform(tp, handle)
                        } else if (isTouchInsideSelection(tp)) {
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
                    disableInkRendering()
                }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            if (EditorState.currentMode.value == AppMode.GEOMETRY) {
                disableInkRendering()
                createDrawingBitmap()
                geometryStartPoint = touchPoint
                geometrySnapshotBitmap?.recycle()
                geometrySnapshotBitmap = bitmap?.let { bmp -> bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) }
                isDrawingInProgress = true
                disableFingerTouch()
                return
            }
            if (EditorState.currentMode.value == AppMode.ERASER) {
                disableInkRendering()
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
            if (EditorState.currentMode.value == AppMode.SELECTION
                && (selectionSubState == SelectionSubState.STRETCHING
                    || selectionSubState == SelectionSubState.ROTATING)
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastSelectionRenderTime < 80L) return
                lastSelectionRenderTime = now
                touchPoint?.let { tp -> renderTransformPreview(tp) }
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
                        SelectionSubState.STRETCHING -> handleTransformComplete(tpl, isRotation = false)
                        SelectionSubState.ROTATING -> handleTransformComplete(tpl, isRotation = true)
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
            if (EditorState.currentMode.value == AppMode.ERASER) {
                touchPointList?.let { handleErasing(it) }
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
        com.wyldsoft.notes.geometry.GeometryShapeRenderer.draw(
            canvas, shapeType, startPt.x, startPt.y, endPt.x, endPt.y, paint
        )
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
        com.wyldsoft.notes.geometry.GeometryShapeRenderer.appendOutlinePoints(tpl, shapeType, sx, sy, ex, ey, ts)
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
        val found = selectionManager.findShapesInsideLasso(drawingPipeline.getShapes(), noteLasso, EditorState.activeLayer.value)
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
        disableInkRendering()
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

        enableInkRendering()
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

    // ── Transform (stretch / rotate) helpers ─────────────────────────────────

    /** Returns handle centers in viewport space for the current selection bounding rect. */
    private fun computeHandleCenters(vpRect: RectF, dX: Float = 0f, dY: Float = 0f): Map<HandleType, PointF> {
        val left = vpRect.left + dX - 8f
        val top = vpRect.top + dY - 8f
        val right = vpRect.right + dX + 8f
        val bottom = vpRect.bottom + dY + 8f
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return mapOf(
            HandleType.CORNER_TL to PointF(left, top),
            HandleType.CORNER_TR to PointF(right, top),
            HandleType.CORNER_BL to PointF(left, bottom),
            HandleType.CORNER_BR to PointF(right, bottom),
            HandleType.MIDPOINT_T to PointF(cx, top),
            HandleType.MIDPOINT_B to PointF(cx, bottom),
            HandleType.MIDPOINT_L to PointF(left, cy),
            HandleType.MIDPOINT_R to PointF(right, cy),
            HandleType.ROTATE to PointF(cx, top - ROTATE_HANDLE_OFFSET)
        )
    }

    /** Returns the handle under (vx, vy) if within hit radius, or null. */
    private fun findHandleAtPoint(vx: Float, vy: Float): HandleType? {
        val noteRect = selectionBoundingRectNote ?: return null
        val vpRect = viewportManager.noteToViewport(noteRect)
        val centers = computeHandleCenters(vpRect)
        // Check ROTATE first so it takes priority over potential overlap
        val ordered = listOf(HandleType.ROTATE) + centers.keys.filter { it != HandleType.ROTATE }
        for (handle in ordered) {
            val pt = centers[handle] ?: continue
            if (hypot(vx - pt.x, vy - pt.y) <= HANDLE_HIT_RADIUS) return handle
        }
        return null
    }

    /**
     * Returns (scaleX, scaleY, anchorVpX, anchorVpY) for the current drag point and handle.
     * The anchor is the viewport point that stays fixed during the stretch.
     */
    private fun computeStretchTransform(currentTp: com.onyx.android.sdk.data.note.TouchPoint, vpRect: RectF): FloatArray {
        val origRect = originalBoundingRectNote?.let { viewportManager.noteToViewport(it) } ?: vpRect
        val left = origRect.left - 8f; val top = origRect.top - 8f
        val right = origRect.right + 8f; val bottom = origRect.bottom + 8f
        val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
        val origW = right - left; val origH = bottom - top
        val handle = activeHandle ?: return floatArrayOf(1f, 1f, cx, cy)

        val dragX = currentTp.x; val dragY = currentTp.y

        return when (handle) {
            HandleType.CORNER_TL -> {
                val scaleX = (right - dragX) / origW
                val scaleY = (bottom - dragY) / origH
                floatArrayOf(scaleX, scaleY, right, bottom)
            }
            HandleType.CORNER_TR -> {
                val scaleX = (dragX - left) / origW
                val scaleY = (bottom - dragY) / origH
                floatArrayOf(scaleX, scaleY, left, bottom)
            }
            HandleType.CORNER_BL -> {
                val scaleX = (right - dragX) / origW
                val scaleY = (dragY - top) / origH
                floatArrayOf(scaleX, scaleY, right, top)
            }
            HandleType.CORNER_BR -> {
                val scaleX = (dragX - left) / origW
                val scaleY = (dragY - top) / origH
                floatArrayOf(scaleX, scaleY, left, top)
            }
            HandleType.MIDPOINT_T -> floatArrayOf(1f, (bottom - dragY) / origH, cx, bottom)
            HandleType.MIDPOINT_B -> floatArrayOf(1f, (dragY - top) / origH, cx, top)
            HandleType.MIDPOINT_L -> floatArrayOf((right - dragX) / origW, 1f, right, cy)
            HandleType.MIDPOINT_R -> floatArrayOf((dragX - left) / origW, 1f, left, cy)
            HandleType.ROTATE -> floatArrayOf(1f, 1f, cx, cy)
        }
    }

    /** Returns the rotation delta in radians from initial touch to [currentTp] around box center. */
    private fun computeRotationDelta(currentTp: com.onyx.android.sdk.data.note.TouchPoint, vpRect: RectF): Float {
        val origRect = originalBoundingRectNote?.let { viewportManager.noteToViewport(it) } ?: vpRect
        val cx = origRect.centerX(); val cy = origRect.centerY()
        val startAngle = atan2(transformStartY - cy, transformStartX - cx)
        val currentAngle = atan2(currentTp.y - cy, currentTp.x - cx)
        return currentAngle - startAngle
    }

    private fun startTransform(tp: com.onyx.android.sdk.data.note.TouchPoint, handle: HandleType) {
        Log.d(TAG, "startTransform handle=$handle x=${tp.x} y=${tp.y}")
        disableInkRendering()
        activeHandle = handle
        transformStartX = tp.x
        transformStartY = tp.y
        originalBoundingRectNote = selectionBoundingRectNote?.let { RectF(it) } ?: return
        lastSelectionRenderTime = 0L

        val noteRect = selectionBoundingRectNote ?: return
        val sv = surfaceView ?: return
        val bmp = bitmap ?: return

        // Snapshot the full bitmap (including selected shapes) for the crop
        val snapshot = bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)

        // Crop the selection region from the snapshot
        val vpRect = viewportManager.noteToViewport(noteRect)
        val l = (vpRect.left - 8f).coerceAtLeast(0f).toInt()
        val t = (vpRect.top - 8f).coerceAtLeast(0f).toInt()
        val w = (vpRect.width() + 16f).toInt()
            .coerceAtLeast(1).coerceAtMost(snapshot.width - l)
        val h = (vpRect.height() + 16f).toInt()
            .coerceAtLeast(1).coerceAtMost(snapshot.height - t)
        if (w <= 0 || h <= 0) { snapshot.recycle(); return }
        selectionCropBitmap = Bitmap.createBitmap(snapshot, l, t, w, h)
        selectionCropLeft = l.toFloat()
        selectionCropTop = t.toFloat()
        snapshot.recycle()

        // Recreate background bitmap without selected shapes
        val bgState = drawingPipeline.recreateBitmapExcluding(selectedShapes, bitmap, sv.width, sv.height)
        bitmap = bgState.bitmap
        bitmapCanvas = bgState.canvas

        selectionSubState = if (handle == HandleType.ROTATE) SelectionSubState.ROTATING else SelectionSubState.STRETCHING
    }

    private fun renderTransformPreview(currentTp: com.onyx.android.sdk.data.note.TouchPoint) {
        val sv = surfaceView ?: return
        val bg = bitmap ?: return
        val crop = selectionCropBitmap ?: return
        val noteRect = originalBoundingRectNote ?: return
        val vpRect = viewportManager.noteToViewport(noteRect)

        EpdController.enablePost(sv, 1)
        val canvas = sv.holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bg, 0f, 0f, null)
            canvas.save()
            when (selectionSubState) {
                SelectionSubState.STRETCHING -> {
                    val p = computeStretchTransform(currentTp, vpRect)
                    // p = [scaleX, scaleY, anchorX, anchorY]
                    canvas.scale(p[0], p[1], p[2], p[3])
                }
                SelectionSubState.ROTATING -> {
                    val angleRad = computeRotationDelta(currentTp, vpRect)
                    val degrees = Math.toDegrees(angleRad.toDouble()).toFloat()
                    canvas.rotate(degrees, vpRect.centerX(), vpRect.centerY())
                }
                else -> {}
            }
            canvas.drawBitmap(crop, selectionCropLeft, selectionCropTop, null)
            canvas.restore()
        } finally {
            sv.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun handleTransformComplete(touchPointList: TouchPointList, isRotation: Boolean) {
        Log.d(TAG, "handleTransformComplete isRotation=$isRotation")
        val lastPt = touchPointList.points.lastOrNull() ?: run {
            cleanupTransform()
            return
        }
        val noteRect = originalBoundingRectNote ?: run {
            cleanupTransform()
            return
        }
        val vpRect = viewportManager.noteToViewport(noteRect)

        // Snapshot original points before applying transform
        val originalPoints = selectedShapes
            .filter { it.entityId != null && it.touchPointList != null }
            .associate { shape ->
                shape.entityId!! to TransformAction.copyTouchPointList(shape.touchPointList!!)
            }

        if (isRotation) {
            val angleRad = computeRotationDelta(lastPt, vpRect)
            selectionManager.rotateShapes(selectedShapes, noteRect.centerX(), noteRect.centerY(), angleRad)
        } else {
            val p = computeStretchTransform(lastPt, vpRect)
            // Convert viewport anchor to note-space for the actual point transform
            val anchorNoteX = viewportManager.viewportToNoteX(p[2])
            val anchorNoteY = viewportManager.viewportToNoteY(p[3])
            // Compute note-space scale: viewport scale factors map directly (no additional scaling)
            selectionManager.scaleShapes(selectedShapes, anchorNoteX, anchorNoteY, p[0], p[1])
        }

        for (shape in selectedShapes) { drawingPipeline.updateShape(shape) }

        val newPoints = selectedShapes
            .filter { it.entityId != null && it.touchPointList != null }
            .associate { shape ->
                shape.entityId!! to TransformAction.copyTouchPointList(shape.touchPointList!!)
            }

        if (originalPoints.isNotEmpty()) {
            actionManager.recordAction(TransformAction(
                shapeIds = selectedShapes.mapNotNull { it.entityId },
                originalPoints = originalPoints,
                newPoints = newPoints,
                pipeline = drawingPipeline
            ))
        }

        selectionBoundingRectNote = selectionManager.computeBoundingRect(selectedShapes)
        cleanupTransform()

        surfaceView?.let { sv ->
            val state = drawingPipeline.recreateBitmapFromShapes(bitmap, sv.width, sv.height)
            bitmap = state.bitmap
            bitmapCanvas = state.canvas
            EpdController.enablePost(sv, 1)
            renderBitmapWithSelectionOverlay()
        }
    }

    private fun cleanupTransform() {
        enableInkRendering()
        selectionCropBitmap?.recycle()
        selectionCropBitmap = null
        transformSnapshotBitmap?.recycle()
        transformSnapshotBitmap = null
        activeHandle = null
        selectionSubState = SelectionSubState.SELECTED
    }

    // ─────────────────────────────────────────────────────────────────────────

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
            if (pt != null) newList.add(pt.copyWith(pt.x, pt.y))
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
        val left = vpRect.left + dX - 8f
        val top = vpRect.top + dY - 8f
        val right = vpRect.right + dX + 8f
        val bottom = vpRect.bottom + dY + 8f
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        canvas.drawRect(left, top, right, bottom, selectionBoxPaint)

        // Stretch handles at corners and midpoints
        val handlePositions = listOf(
            left to top,    // TL
            right to top,   // TR
            left to bottom, // BL
            right to bottom,// BR
            cx to top,      // T
            cx to bottom,   // B
            left to cy,     // L
            right to cy     // R
        )
        for ((hx, hy) in handlePositions) {
            canvas.drawCircle(hx, hy, HANDLE_RADIUS, handleFillPaint)
            canvas.drawCircle(hx, hy, HANDLE_RADIUS, handleStrokePaint)
        }

        // Rotate handle: above top-center with a stem line
        val rotateY = top - ROTATE_HANDLE_OFFSET
        canvas.drawLine(cx, top, cx, rotateY + HANDLE_RADIUS, handleStemPaint)
        canvas.drawCircle(cx, rotateY, HANDLE_RADIUS, handleFillPaint)
        canvas.drawCircle(cx, rotateY, HANDLE_RADIUS, handleStrokePaint)
        // Draw a curved arrow indicator inside the rotate handle
        val arrowPaint = Paint(handleStrokePaint).apply { strokeWidth = 2f }
        canvas.drawArc(
            cx - HANDLE_RADIUS * 0.55f, rotateY - HANDLE_RADIUS * 0.55f,
            cx + HANDLE_RADIUS * 0.55f, rotateY + HANDLE_RADIUS * 0.55f,
            -30f, 240f, false, arrowPaint
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
        disableInkRendering()

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
