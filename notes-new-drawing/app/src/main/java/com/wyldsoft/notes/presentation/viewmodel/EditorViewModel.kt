package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.DrawAction
import com.wyldsoft.notes.actions.EraseAction
import com.wyldsoft.notes.actions.MoveAction
import com.wyldsoft.notes.actions.TransformAction
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.utils.PaginationConstants
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    
    // Viewport manager for coordinate transformations
    val viewportManager = ViewportManager()
    val viewportState = viewportManager.viewportState
    
    // Pagination state - initialized from the current note's settings
    private val _isPaginationEnabled = MutableStateFlow(currentNote.value.isPaginationEnabled)
    val isPaginationEnabled: StateFlow<Boolean> = _isPaginationEnabled.asStateFlow()

    private val _paperSize = MutableStateFlow(
        PaperSize.entries.find { it.name == currentNote.value.paperSize } ?: PaperSize.LETTER
    )
    val paperSize: StateFlow<PaperSize> = _paperSize.asStateFlow()
    
    private val _currentPageNumber = MutableStateFlow(1)
    val currentPageNumber: StateFlow<Int> = _currentPageNumber.asStateFlow()

    private val _paperTemplate = MutableStateFlow(
        PaperTemplate.fromString(currentNote.value.paperTemplate.name)
    )
    val paperTemplate: StateFlow<PaperTemplate> = _paperTemplate.asStateFlow()
    
    private val _screenWidth = MutableStateFlow(0)
    val screenWidth: StateFlow<Int> = _screenWidth.asStateFlow()
    
    private val _pageHeight = MutableStateFlow(0f)
    val pageHeight: StateFlow<Float> = _pageHeight.asStateFlow()

    // Undo/Redo
    val actionManager = ActionManager()
    val canUndo: StateFlow<Boolean> = actionManager.canUndo
    val canRedo: StateFlow<Boolean> = actionManager.canRedo

    // Late-initialized references needed by actions (set from BaseDrawingActivity)
    private var shapesManager: ShapesManager? = null
    private var bitmapManager: BitmapManager? = null
    private var onScreenRefreshNeeded: (() -> Unit)? = null

    // Selection manager
    val selectionManager = SelectionManager()

    // Note navigation within notebook
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
    private val _canGoForward = MutableStateFlow(true)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    // Callback invoked when switching notes, so BaseDrawingActivity can re-init
    var onNoteSwitched: (() -> Unit)? = null

    // Erase stroke grouping: accumulates erased shapes during one erase gesture
    private val pendingErasedShapes = mutableListOf<Shape>()
    private var isErasingInProgress = false

    fun setDrawingReferences(
        shapesManager: ShapesManager,
        bitmapManager: BitmapManager,
        onScreenRefreshNeeded: () -> Unit
    ) {
        this.shapesManager = shapesManager
        this.bitmapManager = bitmapManager
        this.onScreenRefreshNeeded = onScreenRefreshNeeded
    }

    init {
        viewModelScope.launch {
            if (currentNote.value == null) {
                Log.d("EditorViewModel", "No current note found, creating a new one")
                noteRepository.createNewNote()
            }
        }
        
        // Save viewport state when it changes (with debounce to avoid too frequent saves)
        viewModelScope.launch {
            viewportState
                .drop(1) // Skip initial because currentNote is not the actual note yet, just a default one
                .debounce(100) // Wait 100ms after last change before saving
                .collect { state ->
                    Log.d("EditorViewModel", "Saving viewport state for note: ${currentNote.value.id}, scale: ${state.scale}, offsetX: ${state.offsetX}, offsetY: ${state.offsetY}")
                    noteRepository.updateViewportState(
                        currentNote.value.id,
                        state.scale,
                        state.offsetX,
                        state.offsetY
                    )
                }
        }
    }
    
    fun startDrawing() {
        _isDrawing.value = true
        _uiState.value = _uiState.value.copy(isStrokeOptionsOpen = false)
    }
    
    fun endDrawing() {
        _isDrawing.value = false
    }
    
    fun addShape(id: String, points: List<PointF>, pressures: List<Float> = emptyList(), timestamps: List<Long> = emptyList()) {
        viewModelScope.launch {
            Log.d("EditorViewModel", "Adding shape to note: ${currentNote.value.id}, points: $points, pressures: $pressures")
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

            // Record action for undo/redo
            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                val action = DrawAction(currentNote.value.id, shape, noteRepository, sm, bm)
                actionManager.recordAction(action)
            }

            // Submit shape for HTR recognition
            htrRunManager?.addShapesForRecognition(currentNote.value.id, listOf(shape))

        }
    }

    fun removeShape(shapeId: String) {
        viewModelScope.launch {
            currentNote.value.let { note ->
                // Capture the full shape before deleting (needed for undo)
                val shape = note.shapes.find { it.id == shapeId }
                if (shape != null && isErasingInProgress) {
                    pendingErasedShapes.add(shape)
                }
                noteRepository.removeShape(note.id, shapeId)
            }
        }
    }

    fun startErasing() {
        isErasingInProgress = true
        pendingErasedShapes.clear()
    }

    fun endErasing() {
        isErasingInProgress = false
        if (pendingErasedShapes.isNotEmpty()) {
            val sm = shapesManager
            val bm = bitmapManager
            if (sm != null && bm != null) {
                val action = EraseAction(
                    currentNote.value.id,
                    pendingErasedShapes.toList(),
                    noteRepository, sm, bm
                )
                actionManager.recordAction(action)
            }

            // Notify HTR of deleted shapes
            val deletedIds = pendingErasedShapes.map { it.id }.toSet()
            htrRunManager?.onShapesDeleted(currentNote.value.id, deletedIds)

            pendingErasedShapes.clear()
        }
    }

    fun undo() {
        viewModelScope.launch {
            actionManager.undo()
            onScreenRefreshNeeded?.invoke()
        }
    }

    fun redo() {
        viewModelScope.launch {
            actionManager.redo()
            onScreenRefreshNeeded?.invoke()
        }
    }
    
    fun selectTool(tool: Tool) {
        if (tool != Tool.SELECTOR) {
            selectionManager.clearSelection()
        }
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun cancelSelection() {
        selectionManager.clearSelection()
        _uiState.value = _uiState.value.copy(selectedTool = Tool.PEN)
    }

    /**
     * Record a move action for undo/redo after shapes have been dragged.
     * [originalShapes] are the domain shapes *before* the move.
     */
    fun recordMoveAction(originalShapes: List<Shape>, dx: Float, dy: Float) {
        val sm = shapesManager
        val bm = bitmapManager
        if (sm != null && bm != null) {
            val action = MoveAction(
                currentNote.value.id, originalShapes, dx, dy,
                noteRepository, sm, bm
            )
            actionManager.recordAction(action)
        }
    }

    /**
     * Persist moved shapes to the database after a drag operation.
     */
    fun persistMovedShapes(shapeIds: Set<String>, dx: Float, dy: Float) {
        viewModelScope.launch {
            val note = currentNote.value
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val movedPoints = shape.points.map { PointF(it.x + dx, it.y + dy) }
                    val movedShape = shape.copy(points = movedPoints)
                    noteRepository.updateShape(note.id, movedShape)
                }
            }
        }
    }

    /**
     * Record a transform action (scale or rotate) for undo/redo.
     */
    fun recordTransformAction(
        originalShapes: List<Shape>,
        transformType: TransformType,
        param: Float,
        centerX: Float,
        centerY: Float
    ) {
        val sm = shapesManager
        val bm = bitmapManager
        if (sm != null && bm != null) {
            val action = TransformAction(
                currentNote.value.id, originalShapes, transformType, param,
                centerX, centerY, noteRepository, sm, bm
            )
            actionManager.recordAction(action)
        }
    }

    /**
     * Persist scaled shapes to the database.
     */
    fun persistScaledShapes(shapeIds: Set<String>, scaleFactor: Float, centerX: Float, centerY: Float) {
        viewModelScope.launch {
            val note = currentNote.value
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val scaledPoints = shape.points.map { pt ->
                        PointF(
                            centerX + (pt.x - centerX) * scaleFactor,
                            centerY + (pt.y - centerY) * scaleFactor
                        )
                    }
                    noteRepository.updateShape(note.id, shape.copy(points = scaledPoints))
                }
            }
        }
    }

    /**
     * Persist rotated shapes to the database.
     */
    fun persistRotatedShapes(shapeIds: Set<String>, angleRad: Float, centerX: Float, centerY: Float) {
        viewModelScope.launch {
            val note = currentNote.value
            val cosA = kotlin.math.cos(angleRad)
            val sinA = kotlin.math.sin(angleRad)
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val rotatedPoints = shape.points.map { pt ->
                        val dx = pt.x - centerX
                        val dy = pt.y - centerY
                        PointF(
                            centerX + dx * cosA - dy * sinA,
                            centerY + dx * sinA + dy * cosA
                        )
                    }
                    noteRepository.updateShape(note.id, shape.copy(points = rotatedPoints))
                }
            }
        }
    }

    // --- Note navigation ---

    fun initNavigationState() {
        if (notebookId == null) {
            _canGoBack.value = false
            _canGoForward.value = false
            return
        }
        viewModelScope.launch {
            updateNavigationState()
        }
    }

    private suspend fun updateNavigationState() {
        val nbId = notebookId ?: return
        val notes = notebookRepository.getNotesInNotebookOnce(nbId)
        val currentIndex = notes.indexOfFirst { it.id == currentNote.value.id }
        _canGoBack.value = currentIndex > 0
        // Always allow forward (creates new note if at end and current note has shapes)
        _canGoForward.value = true
    }

    fun navigateBackward() {
        val nbId = notebookId ?: return
        viewModelScope.launch {
            val notes = notebookRepository.getNotesInNotebookOnce(nbId)
            val currentIndex = notes.indexOfFirst { it.id == currentNote.value.id }
            if (currentIndex > 0) {
                switchToNote(notes[currentIndex - 1].id)
            }
        }
    }

    fun navigateForward() {
        val nbId = notebookId ?: return
        viewModelScope.launch {
            val notes = notebookRepository.getNotesInNotebookOnce(nbId)
            val currentIndex = notes.indexOfFirst { it.id == currentNote.value.id }
            if (currentIndex < notes.size - 1) {
                // Go to next existing note
                switchToNote(notes[currentIndex + 1].id)
            } else {
                // At end — only create new note if current note has shapes
                if (currentNote.value.shapes.isNotEmpty()) {
                    val newNote = notebookRepository.createNoteInNotebook(nbId)
                    switchToNote(newNote.id)
                }
            }
        }
    }

    private suspend fun switchToNote(noteId: String) {
        noteRepository.setCurrentNote(noteId)
        // Reset viewport to the new note's saved viewport
        val note = currentNote.value
        viewportManager.setState(note.viewportScale, note.viewportOffsetX, note.viewportOffsetY)
        // Update pagination state from the new note
        _isPaginationEnabled.value = note.isPaginationEnabled
        _paperSize.value = PaperSize.entries.find { it.name == note.paperSize } ?: PaperSize.LETTER
        _paperTemplate.value = PaperTemplate.fromString(note.paperTemplate.name)
        calculatePageDimensions()
        // Clear undo/redo history (it belongs to the previous note)
        actionManager.clear()
        // Update navigation button states
        updateNavigationState()
        // Notify BaseDrawingActivity to re-init drawing surfaces
        onNoteSwitched?.invoke()
    }

    fun updatePenProfile(profile: PenProfile) {
        _currentPenProfile.value = profile
    }
    
    fun toggleStrokeOptions() {
        _uiState.value = _uiState.value.copy(
            isStrokeOptionsOpen = !_uiState.value.isStrokeOptionsOpen
        )
    }
    
    fun updateExclusionZones(rects: List<Rect>) {
        _excludeRects.value = rects
    }
    
    fun forceRefresh() {
        _refreshUi.value = System.currentTimeMillis()
    }
    
    fun setScreenWidth(width: Int) {
        _screenWidth.value = width
        calculatePageDimensions()
    }
    
    private fun calculatePageDimensions() {
        if (_screenWidth.value > 0) {
            _pageHeight.value = _screenWidth.value * _paperSize.value.aspectRatio
        }
    }
    
    fun updatePaginationEnabled(enabled: Boolean) {
        _isPaginationEnabled.value = enabled
        viewModelScope.launch {
            Log.d("EditorViewModel", "Updating pagination settings for note: ${currentNote.value.id}, enabled: $enabled, paperSize: ${_paperSize.value.name}")
            noteRepository.updatePaginationSettings(currentNote.value.id, enabled, _paperSize.value.name)
        }
    }
    
    fun updatePaperSize(paperSize: PaperSize) {
        _paperSize.value = paperSize
        calculatePageDimensions()
        viewModelScope.launch {
            currentNote.value.let { note ->
                Log.d("EditorViewModel", "Updating paper size for note: ${note.id}, new size: ${paperSize.name}")
                noteRepository.updatePaginationSettings(note.id, _isPaginationEnabled.value, paperSize.name)
            }
        }
    }
    
    fun updatePaperTemplate(template: PaperTemplate) {
        _paperTemplate.value = template
        viewModelScope.launch {
            noteRepository.updatePaperTemplate(currentNote.value.id, template.name)
        }
    }

    fun updateCurrentPage(scrollY: Float) {
        if (_isPaginationEnabled.value && _pageHeight.value > 0) {
            _currentPageNumber.value = (scrollY / _pageHeight.value).toInt() + 1
        }
    }
    
    fun getPageSeparatorRects(): List<Rect> {
        if (!_isPaginationEnabled.value || _pageHeight.value <= 0) {
            return emptyList()
        }
        
        val separatorHeight = PaginationConstants.SEPARATOR_HEIGHT.toInt()
        val rects = mutableListOf<Rect>()
        val pageHeightInt = _pageHeight.value.toInt()
        
        // Calculate visible separators based on viewport
        val viewportTop = viewportState.value.offsetY.toInt()
        val viewportBottom = viewportTop + viewportManager.viewHeight
        
        var pageY = pageHeightInt
        var pageNum = 2
        
        while (pageY - separatorHeight < viewportBottom + pageHeightInt) {
            if (pageY + separatorHeight > viewportTop - pageHeightInt) {
                rects.add(Rect(0, pageY, _screenWidth.value, pageY + separatorHeight))
            }
            pageY += pageHeightInt
            pageNum++
        }
        
        return rects
    }
}

data class EditorUiState(
    val isStrokeOptionsOpen: Boolean = false,
    val selectedTool: Tool = Tool.PEN
)

enum class Tool {
    PEN,
    ERASER,
    SELECTOR,
    GEOMETRY
}