package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.RectF
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.ActionUtils
import com.wyldsoft.notes.actions.DrawAction
import com.wyldsoft.notes.actions.EraseAction
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.geometry.GeometricShapeType
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class EditorViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val htrRunManager: HTRRunManager? = null,
    val notebookId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val currentNote = noteRepository.getCurrentNote()

    private val _isDrawing = MutableStateFlow(false)
    val isDrawing: StateFlow<Boolean> = _isDrawing.asStateFlow()

    private val _currentPenProfile = MutableStateFlow(PenProfile.defaultProfiles[0])
    val currentPenProfile: StateFlow<PenProfile> = _currentPenProfile.asStateFlow()

    private val _refreshUi = MutableStateFlow(0L)
    val refreshUi: StateFlow<Long> = _refreshUi.asStateFlow()

    private val _excludeRects = MutableStateFlow<List<Rect>>(emptyList())
    val excludeRects: StateFlow<List<Rect>> = _excludeRects.asStateFlow()

    val viewportManager = ViewportManager()
    val viewportState = viewportManager.viewportState

    // Undo/redo
    val actionManager = ActionManager()
    val canUndo: StateFlow<Boolean> = actionManager.canUndo
    val canRedo: StateFlow<Boolean> = actionManager.canRedo

    // Late-initialized references set from BaseDrawingActivity
    private var shapesManager: ShapesManager? = null
    private var bitmapManager: BitmapManager? = null
    private var onScreenRefreshNeeded: (() -> Unit)? = null

    val selectionManager = SelectionManager()

    private val _copiedShapes = MutableStateFlow<List<Shape>>(emptyList())
    val copiedShapes: StateFlow<List<Shape>> = _copiedShapes.asStateFlow()

    private val _hasSelection = MutableStateFlow(false)
    val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()

    private val _isConvertingToText = MutableStateFlow(false)
    val isConvertingToText: StateFlow<Boolean> = _isConvertingToText.asStateFlow()

    // Text input state
    private val _textInputPosition = MutableStateFlow<android.graphics.PointF?>(null)
    val textInputPosition: StateFlow<android.graphics.PointF?> = _textInputPosition.asStateFlow()

    private val _liveTextContent = MutableStateFlow("")
    val liveTextContent: StateFlow<String> = _liveTextContent.asStateFlow()

    // ID of an existing TextShape being edited (null when creating a new one)
    private var editingShapeId: String? = null

    // Text formatting settings
    private val _textFontSize = MutableStateFlow(32f)
    val textFontSize: StateFlow<Float> = _textFontSize.asStateFlow()

    private val _textFontFamily = MutableStateFlow("sans-serif")
    val textFontFamily: StateFlow<String> = _textFontFamily.asStateFlow()

    private val _textColor = MutableStateFlow(android.graphics.Color.BLACK)
    val textColor: StateFlow<Int> = _textColor.asStateFlow()

    // Dropdown open/close tracking
    private val _openDropdownCount = MutableStateFlow(0)
    val openDropdownCount: StateFlow<Int> = _openDropdownCount.asStateFlow()
    val isAnyDropdownOpen: Boolean
        get() = _openDropdownCount.value > 0 || _uiState.value.isStrokeOptionsOpen

    private val _closeAllDropdownsEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeAllDropdownsEvent: SharedFlow<Unit> = _closeAllDropdownsEvent.asSharedFlow()

    fun onDropdownOpened() { _openDropdownCount.value++ }
    fun onDropdownClosed() { _openDropdownCount.value = maxOf(0, _openDropdownCount.value - 1) }
    fun closeAllDropdowns() {
        closeStrokeOptions()
        _openDropdownCount.value = 0
        _closeAllDropdownsEvent.tryEmit(Unit)
    }

    fun setTextFontSize(size: Float) {
        _textFontSize.value = size
        if (selectionManager.hasSelection) {
            selectionTransformHandler.applyTextFormattingToSelection(size, _textFontFamily.value, _textColor.value)
        }
    }

    fun setTextFontFamily(family: String) {
        _textFontFamily.value = family
        if (selectionManager.hasSelection) {
            selectionTransformHandler.applyTextFormattingToSelection(_textFontSize.value, family, _textColor.value)
        }
    }

    fun setTextColor(color: Int) {
        _textColor.value = color
        if (selectionManager.hasSelection) {
            selectionTransformHandler.applyTextFormattingToSelection(_textFontSize.value, _textFontFamily.value, color)
        }
    }

    fun notifySelectionChanged() {
        _hasSelection.value = selectionManager.hasSelection
    }

    // Erase stroke grouping
    private val pendingErasedShapes = mutableListOf<Shape>()
    private var isErasingInProgress = false

    // --- Delegated handlers (paginationHandler first — navigationHandler's lambda references it) ---

    val paginationHandler = PaginationHandler(
        noteRepository = noteRepository,
        scope = viewModelScope,
        viewportManager = viewportManager,
        getCurrentNote = { currentNote.value },
        initialNote = currentNote.value
    )

    val isPaginationEnabled: StateFlow<Boolean> = paginationHandler.isPaginationEnabled
    val paperSize: StateFlow<PaperSize> = paginationHandler.paperSize
    val currentPageNumber: StateFlow<Int> = paginationHandler.currentPageNumber
    val paperTemplate: StateFlow<PaperTemplate> = paginationHandler.paperTemplate
    val screenWidth: StateFlow<Int> = paginationHandler.screenWidth
    val pageHeight: StateFlow<Float> = paginationHandler.pageHeight

    val navigationHandler = NoteNavigationHandler(
        notebookId = notebookId,
        noteRepository = noteRepository,
        notebookRepository = notebookRepository,
        scope = viewModelScope,
        viewportManager = viewportManager,
        getCurrentNote = { currentNote.value },
        onSwitchNote = { paginationHandler.resetForNote(currentNote.value); actionManager.clear() }
    )

    val canGoBack: StateFlow<Boolean> = navigationHandler.canGoBack
    val canGoForward: StateFlow<Boolean> = navigationHandler.canGoForward
    var onNoteSwitched: (() -> Unit)?
        get() = navigationHandler.onNoteSwitched
        set(value) { navigationHandler.onNoteSwitched = value }

    private val selectionTransformHandler = SelectionTransformHandler(
        noteRepository = noteRepository,
        scope = viewModelScope,
        selectionManager = selectionManager,
        actionManager = actionManager,
        getCurrentNote = { currentNote.value },
        getShapesManager = { shapesManager },
        getBitmapManager = { bitmapManager },
        onScreenRefreshNeeded = { onScreenRefreshNeeded?.invoke() }
    )

    init {
        viewportManager.isPaginationEnabled = currentNote.value.isPaginationEnabled

        viewModelScope.launch {
            if (currentNote.value == null) {
                Log.d("EditorViewModel", "No current note found, creating a new one")
                noteRepository.createNewNote()
            }
        }

        viewModelScope.launch {
            viewportState
                .drop(1)
                .debounce(100)
                .collect { state ->
                    Log.d("EditorViewModel", "Saving viewport state for note: ${currentNote.value.id}")
                    noteRepository.updateViewportState(
                        currentNote.value.id,
                        state.scale,
                        state.scrollX,
                        state.scrollY
                    )
                }
        }
    }

    fun setDrawingReferences(
        shapesManager: ShapesManager,
        bitmapManager: BitmapManager,
        onScreenRefreshNeeded: () -> Unit
    ) {
        this.shapesManager = shapesManager
        this.bitmapManager = bitmapManager
        this.onScreenRefreshNeeded = onScreenRefreshNeeded
        updateContentBounds()
    }

    fun updateContentBounds() {
        viewportManager.contentMaxY = shapesManager?.getContentMaxY() ?: return
    }

    fun startDrawing() {
        Log.d("DebugMarch6", "Starting drawing on note: ${currentNote.value.id}")
        _isDrawing.value = true
    }

    fun endDrawing() {
        Log.d("DebugMarch6", "Ending drawing on note: ${currentNote.value.id}")
        _isDrawing.value = false
    }

    fun addShape(id: String, points: List<android.graphics.PointF>, pressures: List<Float> = emptyList(), timestamps: List<Long> = emptyList()) {
        viewModelScope.launch {
            val shape = Shape(
                id = id,
                type = ShapeType.STROKE,
                points = points,
                strokeWidth = _currentPenProfile.value.strokeWidth,
                strokeColor = _currentPenProfile.value.getColorAsInt(),
                penType = _currentPenProfile.value.penType,
                pressure = pressures,
                pointTimestamps = timestamps
            )
            noteRepository.addShape(currentNote.value.id, shape)

            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                actionManager.recordAction(DrawAction(currentNote.value.id, shape, noteRepository, sm, bm))
            }

            htrRunManager?.addShapesForRecognition(currentNote.value.id, listOf(shape))
            updateContentBounds()
        }
    }

    fun removeShape(shapeId: String) {
        viewModelScope.launch {
            val note = currentNote.value
            val shape = note.shapes.find { it.id == shapeId }
            if (shape != null && isErasingInProgress) pendingErasedShapes.add(shape)
            noteRepository.removeShape(note.id, shapeId)
        }
    }

    fun startErasing() {
        isErasingInProgress = true
        pendingErasedShapes.clear()
    }

    fun endErasing() {
        isErasingInProgress = false
        updateContentBounds()
        if (pendingErasedShapes.isNotEmpty()) {
            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                actionManager.recordAction(
                    EraseAction(currentNote.value.id, pendingErasedShapes.toList(), noteRepository, sm, bm)
                )
            }
            htrRunManager?.onShapesDeleted(currentNote.value.id, pendingErasedShapes.map { it.id }.toSet())
            pendingErasedShapes.clear()
        }
    }

    fun undo() {
        viewModelScope.launch {
            actionManager.undo()
            updateContentBounds()
            onScreenRefreshNeeded?.invoke()
        }
    }

    fun redo() {
        viewModelScope.launch {
            actionManager.redo()
            updateContentBounds()
            onScreenRefreshNeeded?.invoke()
        }
    }

    fun selectTool(tool: Tool) {
        if (tool != Tool.SELECTOR) selectionManager.clearSelection()
        notifySelectionChanged()
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectGeometricShape(shape: GeometricShapeType) {
        _uiState.value = _uiState.value.copy(selectedGeometricShape = shape)
    }

    fun addGeometricShape(shape: Shape) {
        viewModelScope.launch {
            noteRepository.addShape(currentNote.value.id, shape)
            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                actionManager.recordAction(DrawAction(currentNote.value.id, shape, noteRepository, sm, bm))
            }
            updateContentBounds()
        }
    }

    fun beginTextInput(noteX: Float, noteY: Float) {
        if (_textInputPosition.value != null) return // already editing, ignore subsequent taps
        editingShapeId = null
        _liveTextContent.value = ""
        _textInputPosition.value = android.graphics.PointF(noteX, noteY)
    }

    fun beginEditingTextShape(
        shapeId: String,
        anchorNoteX: Float,
        anchorNoteY: Float,
        existingText: String,
        existingFontSize: Float,
        existingFontFamily: String,
        existingColor: Int
    ) {
        if (_textInputPosition.value != null) return
        editingShapeId = shapeId

        // Update toolbar settings to match the shape being edited
        _textFontSize.value = existingFontSize
        _textFontFamily.value = existingFontFamily
        _textColor.value = existingColor

        _liveTextContent.value = existingText
        _textInputPosition.value = android.graphics.PointF(anchorNoteX, anchorNoteY)

        // Remove from visual display so the LiveTextInput overlay takes over
        val sm = shapesManager
        val bm = bitmapManager
        if (sm != null && bm != null) {
            val sdkShape = sm.findShapeById(shapeId)
            if (sdkShape != null) {
                sm.removeShape(sdkShape)
                bm.recreateBitmapFromShapes(sm.shapes())
            }
        }
        onScreenRefreshNeeded?.invoke()
    }

    fun updateLiveTextContent(text: String) {
        _liveTextContent.value = text
    }

    fun commitLiveTextInput() {
        commitTextInput(_liveTextContent.value)
        _liveTextContent.value = ""
    }

    fun commitTextInput(text: String) {
        val position = _textInputPosition.value ?: return
        val editId = editingShapeId
        _textInputPosition.value = null
        editingShapeId = null

        viewModelScope.launch {
            // Delete the original shape from DB when editing
            if (editId != null) {
                noteRepository.removeShape(currentNote.value.id, editId)
            }

            if (text.isBlank()) {
                // Empty text: old shape already removed from memory; refresh to show deletion
                val bm = bitmapManager
                val sm = shapesManager
                if (bm != null && sm != null && editId != null) {
                    bm.recreateBitmapFromShapes(sm.shapes())
                    onScreenRefreshNeeded?.invoke()
                }
                return@launch
            }

            val shape = Shape(
                type = ShapeType.TEXT,
                points = listOf(position),
                strokeWidth = 2f,
                strokeColor = _textColor.value,
                text = text,
                fontSize = _textFontSize.value,
                fontFamily = _textFontFamily.value
            )
            noteRepository.addShape(currentNote.value.id, shape)
            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                val sdkShape = sm.convertDomainShapeToSdkShape(shape)
                sm.addShape(sdkShape)
                bm.recreateBitmapFromShapes(sm.shapes())
                actionManager.recordAction(DrawAction(currentNote.value.id, shape, noteRepository, sm, bm))
                onScreenRefreshNeeded?.invoke()
            }
            updateContentBounds()
        }
    }

    fun cancelTextInput() {
        val editId = editingShapeId
        _textInputPosition.value = null
        _liveTextContent.value = ""
        editingShapeId = null

        // When editing is cancelled, old shape was already removed from memory; delete from DB
        if (editId != null) {
            viewModelScope.launch {
                noteRepository.removeShape(currentNote.value.id, editId)
            }
        }
    }

    fun cancelSelection() {
        selectionManager.clearSelection()
        notifySelectionChanged()
        _uiState.value = _uiState.value.copy(selectedTool = Tool.PEN)
    }

    fun convertSelectionToText() {
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return
        val boundingBox = selectionManager.selectionBoundingBox ?: return
        val note = currentNote.value
        val selectedShapes = note.shapes.filter { it.id in selectedIds }
        if (selectedShapes.isEmpty()) return
        val htr = htrRunManager ?: return

        _isConvertingToText.value = true
        viewModelScope.launch {
            try {
                val text = htr.recognizeShapesDirectly(note.id, selectedShapes) ?: return@launch
                val sm = shapesManager ?: return@launch
                val bm = bitmapManager ?: return@launch

                for (shape in selectedShapes) {
                    ActionUtils.removeShapeFromNoteAndMemory(note.id, shape, noteRepository, sm)
                }

                val textShape = Shape(
                    type = ShapeType.TEXT,
                    points = listOf(android.graphics.PointF(boundingBox.left, boundingBox.top)),
                    strokeWidth = 2f,
                    strokeColor = android.graphics.Color.BLACK,
                    text = text,
                    fontSize = 24f,
                    fontFamily = "sans-serif"
                )
                ActionUtils.addShapeToNoteAndMemory(note.id, textShape, noteRepository, sm)
                bm.recreateBitmapFromShapes(sm.shapes())
                selectionManager.clearSelection()
                notifySelectionChanged()
                onScreenRefreshNeeded?.invoke()
                updateContentBounds()
            } finally {
                _isConvertingToText.value = false
            }
        }
    }

    fun copySelection() {
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return
        _copiedShapes.value = currentNote.value.shapes.filter { it.id in selectedIds }
    }

    fun pasteSelection() {
        val shapes = _copiedShapes.value
        if (shapes.isEmpty()) return

        viewModelScope.launch {
            val sm = shapesManager ?: return@launch
            val bm = bitmapManager ?: return@launch
            val note = currentNote.value

            val allPoints = shapes.flatMap { it.points }
            if (allPoints.isEmpty()) return@launch

            // Build tight bounding box from first point to avoid including origin
            val copyBox = RectF(allPoints[0].x, allPoints[0].y, allPoints[0].x, allPoints[0].y)
            allPoints.drop(1).forEach { copyBox.union(it.x, it.y) }
            val copyCenterX = copyBox.centerX()
            val copyCenterY = copyBox.centerY()

            // Convert screen center to note coordinates using the viewport matrix
            val screenCenter = viewportManager.surfaceToNoteCoordinates(
                viewportManager.viewWidth / 2f, viewportManager.viewHeight / 2f
            )
            val dx = screenCenter.x - copyCenterX
            val dy = screenCenter.y - copyCenterY

            val newShapes = shapes.map { shape ->
                shape.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    points = shape.points.map { pt -> android.graphics.PointF(pt.x + dx, pt.y + dy) }
                )
            }

            for (newShape in newShapes) {
                ActionUtils.addShapeToNoteAndMemory(note.id, newShape, noteRepository, sm)
                actionManager.recordAction(DrawAction(note.id, newShape, noteRepository, sm, bm))
            }

            // Build tight bounding box for pasted shapes
            val newPoints = newShapes.flatMap { it.points }
            val newBox = RectF(newPoints[0].x, newPoints[0].y, newPoints[0].x, newPoints[0].y)
            newPoints.drop(1).forEach { newBox.union(it.x, it.y) }

            selectionManager.clearSelection()
            selectionManager.setSelection(newShapes.map { it.id }.toSet(), newBox)
            notifySelectionChanged()

            bm.recreateBitmapFromShapes(sm.shapes())
            onScreenRefreshNeeded?.invoke()
            updateContentBounds()
            _uiState.value = _uiState.value.copy(selectedTool = Tool.SELECTOR)
        }
    }

    // --- Delegation to SelectionTransformHandler ---

    fun applyPenProfileToSelection(profile: PenProfile) =
        selectionTransformHandler.applyPenProfileToSelection(profile)

    fun applyTextFormattingToSelection(fontSize: Float, fontFamily: String, color: Int) =
        selectionTransformHandler.applyTextFormattingToSelection(fontSize, fontFamily, color)

    fun recordMoveAction(originalShapes: List<Shape>, dx: Float, dy: Float) =
        selectionTransformHandler.recordMoveAction(originalShapes, dx, dy)

    fun persistMovedShapes(shapeIds: Set<String>, dx: Float, dy: Float) =
        selectionTransformHandler.persistMovedShapes(shapeIds, dx, dy)

    fun recordTransformAction(originalShapes: List<Shape>, transformType: TransformType, param: Float, centerX: Float, centerY: Float) =
        selectionTransformHandler.recordTransformAction(originalShapes, transformType, param, centerX, centerY)

    fun persistScaledShapes(shapeIds: Set<String>, scaleFactor: Float, centerX: Float, centerY: Float) =
        selectionTransformHandler.persistScaledShapes(shapeIds, scaleFactor, centerX, centerY)

    fun persistRotatedShapes(shapeIds: Set<String>, angleRad: Float, centerX: Float, centerY: Float) =
        selectionTransformHandler.persistRotatedShapes(shapeIds, angleRad, centerX, centerY)

    // --- Delegation to NavigationHandler ---

    fun initNavigationState() = navigationHandler.initNavigationState()
    fun navigateBackward() = navigationHandler.navigateBackward()
    fun navigateForward() = navigationHandler.navigateForward()

    // --- Delegation to PaginationHandler ---

    fun setScreenWidth(width: Int) = paginationHandler.setScreenWidth(width)
    fun updatePaginationEnabled(enabled: Boolean) = paginationHandler.updatePaginationEnabled(enabled)
    fun updatePaperSize(paperSize: PaperSize) = paginationHandler.updatePaperSize(paperSize)
    fun updatePaperTemplate(template: PaperTemplate) = paginationHandler.updatePaperTemplate(template)
    fun updateCurrentPage(scrollY: Float) = paginationHandler.updateCurrentPage(scrollY)
    fun getPageSeparatorRects(): List<Rect> = paginationHandler.getPageSeparatorRects()

    // --- Pen / UI state ---

    fun updatePenProfile(profile: PenProfile) {
        _currentPenProfile.value = profile
    }

    fun toggleStrokeOptions() {
        _uiState.value = _uiState.value.copy(isStrokeOptionsOpen = !_uiState.value.isStrokeOptionsOpen)
    }

    fun closeStrokeOptions() {
        if (_uiState.value.isStrokeOptionsOpen) {
            _uiState.value = _uiState.value.copy(isStrokeOptionsOpen = false)
        }
    }

    fun updateExclusionZones(rects: List<Rect>) {
        _excludeRects.value = rects
    }

    fun forceRefresh() {
        _refreshUi.value = System.currentTimeMillis()
    }
}

data class EditorUiState(
    val isStrokeOptionsOpen: Boolean = false,
    val selectedTool: Tool = Tool.PEN,
    val selectedGeometricShape: GeometricShapeType = GeometricShapeType.LINE
)

enum class Tool {
    PEN,
    ERASER,
    SELECTOR,
    GEOMETRY,
    TEXT
}
