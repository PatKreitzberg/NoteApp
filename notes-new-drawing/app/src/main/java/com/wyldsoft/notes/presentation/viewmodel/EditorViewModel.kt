package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.geometry.GeometricShapeType
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.FlowPreview
import com.wyldsoft.notes.session.NoteSession
import com.wyldsoft.notes.session.NoteSessionCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    private val _currentPenProfile = MutableStateFlow(PenProfile.defaultProfiles[0])
    val currentPenProfile: StateFlow<PenProfile> = _currentPenProfile.asStateFlow()

    private val _excludeRects = MutableStateFlow<List<Rect>>(emptyList())
    val excludeRects: StateFlow<List<Rect>> = _excludeRects.asStateFlow()

    val viewportManager = ViewportManager()
    val viewportState = viewportManager.viewportState

    private var activeSession: NoteSession? = null
    val sessionCache = NoteSessionCache()
    private val fallbackActionManager = ActionManager()
    private val _activeActionManager = MutableStateFlow(fallbackActionManager)

    val actionManager: ActionManager get() = activeSession?.actionManager ?: fallbackActionManager

    val canUndo: StateFlow<Boolean> = _activeActionManager
        .flatMapLatest { it.canUndo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canRedo: StateFlow<Boolean> = _activeActionManager
        .flatMapLatest { it.canRedo }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _contentMaxY = MutableStateFlow(0f)
    val contentMaxY: StateFlow<Float> = _contentMaxY.asStateFlow()

    // Late-initialized references set from BaseDrawingActivity
    private var shapesManager: ShapesManager? = null
    private var bitmapManager: BitmapManager? = null
    private var onScreenRefreshNeeded: (() -> Unit)? = null

    val selectionManager = SelectionManager()

    private val _hasSelection = MutableStateFlow(false)
    val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()

    private val _selectionContainsTextShape = MutableStateFlow(false)
    val selectionContainsTextShape: StateFlow<Boolean> = _selectionContainsTextShape.asStateFlow()

    // Dropdown open/close tracking
    private val _openDropdownCount = MutableStateFlow(0)
    val openDropdownCount: StateFlow<Int> = _openDropdownCount.asStateFlow()

    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen: StateFlow<Boolean> = _isDialogOpen.asStateFlow()

    val isAnyDropdownOpen: Boolean
        get() = isDrawingBlocked.value

    val isDrawingBlocked: StateFlow<Boolean> = combine(
        _uiState.map { it.isStrokeOptionsOpen },
        _openDropdownCount,
        _isDialogOpen
    ) { strokeOpen, dropdownCount, dialogOpen ->
        strokeOpen || dropdownCount > 0 || dialogOpen
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _closeAllDropdownsEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeAllDropdownsEvent: SharedFlow<Unit> = _closeAllDropdownsEvent.asSharedFlow()

    fun onDropdownOpened() { _openDropdownCount.value++ }
    fun onDropdownClosed() { _openDropdownCount.value = maxOf(0, _openDropdownCount.value - 1) }
    fun setDialogOpen(open: Boolean) { _isDialogOpen.value = open }
    fun closeAllDropdowns() {
        closeStrokeOptions()
        _openDropdownCount.value = 0
        _isDialogOpen.value = false
        _closeAllDropdownsEvent.tryEmit(Unit)
    }

    // --- Delegated handlers ---

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

    val isPdfNote: StateFlow<Boolean> = currentNote
        .map { it.pdfPath != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentNote.value.pdfPath != null)

    val navigationHandler = NoteNavigationHandler(
        notebookId = notebookId,
        noteRepository = noteRepository,
        notebookRepository = notebookRepository,
        scope = viewModelScope,
        viewportManager = viewportManager,
        getCurrentNote = { currentNote.value },
        onSwitchNote = {
            paginationHandler.resetForNote(currentNote.value)
        },
        sessionCache = sessionCache
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

    val drawingOperationsHandler = DrawingOperationsHandler(
        noteRepository = noteRepository,
        scope = viewModelScope,
        actionManager = actionManager,
        getCurrentNote = { currentNote.value },
        getCurrentPenProfile = { _currentPenProfile.value },
        getShapesManager = { shapesManager },
        getBitmapManager = { bitmapManager },
        onUpdateContentBounds = { updateContentBounds() },
        htrRunManager = htrRunManager
    )

    val isDrawing: StateFlow<Boolean> = drawingOperationsHandler.isDrawing

    val textInputHandler = TextInputHandler(
        noteRepository = noteRepository,
        scope = viewModelScope,
        actionManager = actionManager,
        getCurrentNote = { currentNote.value },
        getShapesManager = { shapesManager },
        getBitmapManager = { bitmapManager },
        onScreenRefreshNeeded = { onScreenRefreshNeeded?.invoke() },
        onUpdateContentBounds = { updateContentBounds() },
        applyFormattingToSelection = { size, family, color ->
            if (selectionManager.hasSelection)
                selectionTransformHandler.applyTextFormattingToSelection(size, family, color)
        }
    )

    val textInputPosition: StateFlow<PointF?> = textInputHandler.textInputPosition
    val liveTextContent: StateFlow<String> = textInputHandler.liveTextContent
    val textFontSize: StateFlow<Float> = textInputHandler.textFontSize
    val textFontFamily: StateFlow<String> = textInputHandler.textFontFamily
    val textColor: StateFlow<Int> = textInputHandler.textColor

    private val clipboardSelectionHandler = ClipboardSelectionHandler(
        noteRepository = noteRepository,
        scope = viewModelScope,
        actionManager = actionManager,
        getCurrentNote = { currentNote.value },
        getShapesManager = { shapesManager },
        getBitmapManager = { bitmapManager },
        selectionManager = selectionManager,
        viewportManager = viewportManager,
        onScreenRefreshNeeded = { onScreenRefreshNeeded?.invoke() },
        onUpdateContentBounds = { updateContentBounds() },
        onNotifySelectionChanged = { notifySelectionChanged() },
        onSwitchToSelectMode = { switchMode(EditorMode.Select) },
        htrRunManager = htrRunManager
    )

    val copiedShapes: StateFlow<List<Shape>> = clipboardSelectionHandler.copiedShapes
    val isConvertingToText: StateFlow<Boolean> = clipboardSelectionHandler.isConvertingToText

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

    fun setDrawingManagers(
        bitmapManager: BitmapManager,
        onScreenRefreshNeeded: () -> Unit
    ) {
        this.bitmapManager = bitmapManager
        this.onScreenRefreshNeeded = onScreenRefreshNeeded
    }

    fun getOrCreateSession(): NoteSession {
        val noteId = currentNote.value.id
        val cached = sessionCache.get(noteId)
        if (cached != null) {
            Log.d("EditorViewModel", "Using cached session for note $noteId")
            return cached
        }
        val session = NoteSession.create(this)
        sessionCache.put(noteId, session)
        return session
    }

    fun activateSession(session: NoteSession) {
        // Cache outgoing session before replacing
        activeSession?.let { outgoing ->
            sessionCache.put(outgoing.noteId, outgoing)
        }
        activeSession = session
        sessionCache.put(session.noteId, session)
        shapesManager = session.shapesManager
        _activeActionManager.value = session.actionManager
        bitmapManager?.onNoteChanged(
            currentNote.value.pdfPath,
            paginationHandler.screenWidth.value,
            currentNote.value.pdfPageAspectRatio
        )
        updateContentBounds()
        if (selectionManager.hasSelection) {
            selectionManager.clearSelection()
            notifySelectionChanged()
        }
    }

    fun updateContentBounds() {
        val maxY = shapesManager?.getContentMaxY() ?: return
        viewportManager.contentMaxY = maxY
        _contentMaxY.value = maxY
    }

    fun notifySelectionChanged() {
        _hasSelection.value = selectionManager.hasSelection
        _selectionContainsTextShape.value = selectionManager.hasSelection &&
            shapesManager?.shapes()?.any { it.id in selectionManager.selectedShapeIds && it is TextShape } == true
    }

    fun addPdfPage() {
        viewModelScope.launch {
            noteRepository.addPdfPage(currentNote.value.id)
            viewportManager.pdfPageCount = currentNote.value.pdfPageCount
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

    fun switchMode(newMode: EditorMode) {
        val oldMode = _uiState.value.mode
        if (oldMode == newMode) return

        if (oldMode is EditorMode.Draw && newMode is EditorMode.Draw) {
            _uiState.value = _uiState.value.copy(mode = newMode)
            return
        }

        exitMode(oldMode)
        _uiState.value = _uiState.value.copy(mode = newMode)
        enterMode(newMode)
    }

    private fun exitMode(mode: EditorMode) {
        when (mode) {
            is EditorMode.Select -> {
                val hadSelection = selectionManager.hasSelection
                selectionManager.clearSelection()
                notifySelectionChanged()
                if (hadSelection) {
                    val bm = bitmapManager
                    val sm = shapesManager
                    if (bm != null && sm != null) bm.recreateBitmapFromShapes(sm.shapes())
                }
            }
            is EditorMode.Text -> {
                if (textInputHandler.textInputPosition.value != null) textInputHandler.commitLiveTextInput()
            }
            is EditorMode.Draw -> { /* no cleanup needed */ }
        }
    }

    private fun enterMode(mode: EditorMode) {
        when (mode) {
            is EditorMode.Select -> { /* ready for lasso */ }
            is EditorMode.Text -> { /* ready for tap-to-place */ }
            is EditorMode.Draw -> { /* no setup needed */ }
        }
        onScreenRefreshNeeded?.invoke()
    }

    fun selectGeometricShape(shape: GeometricShapeType) {
        _uiState.value = _uiState.value.copy(selectedGeometricShape = shape)
    }

    fun cancelSelection() {
        switchMode(EditorMode.Draw())
    }

    /** Toggle into [target] mode, or exit back to Draw if already in it. */
    fun toggleMode(target: EditorMode) {
        if (_uiState.value.mode == target) switchMode(EditorMode.Draw())
        else switchMode(target)
    }

    // --- Delegation to DrawingOperationsHandler ---

    fun startDrawing() = drawingOperationsHandler.startDrawing()
    fun endDrawing() = drawingOperationsHandler.endDrawing()
    fun addShape(id: String, points: List<android.graphics.PointF>, pressures: List<Float> = emptyList(), timestamps: List<Long> = emptyList()) =
        drawingOperationsHandler.addShape(id, points, pressures, timestamps)
    fun removeShape(shapeId: String) = drawingOperationsHandler.removeShape(shapeId)
    fun startErasing() = drawingOperationsHandler.startErasing()
    fun endErasing() = drawingOperationsHandler.endErasing()
    fun addGeometricShape(shape: Shape) = drawingOperationsHandler.addGeometricShape(shape)
    fun addSnapToLineAction(originalShape: Shape, lineShape: Shape) =
        drawingOperationsHandler.addSnapToLineAction(originalShape, lineShape)

    // --- Delegation to TextInputHandler ---

    fun setTextFontSize(size: Float) = textInputHandler.setTextFontSize(size)
    fun setTextFontFamily(family: String) = textInputHandler.setTextFontFamily(family)
    fun setTextColor(color: Int) = textInputHandler.setTextColor(color)
    fun beginTextInput(noteX: Float, noteY: Float) = textInputHandler.beginTextInput(noteX, noteY)
    fun beginEditingTextShape(shapeId: String, anchorNoteX: Float, anchorNoteY: Float, existingText: String, existingFontSize: Float, existingFontFamily: String, existingColor: Int) =
        textInputHandler.beginEditingTextShape(shapeId, anchorNoteX, anchorNoteY, existingText, existingFontSize, existingFontFamily, existingColor)
    fun updateLiveTextContent(text: String) = textInputHandler.updateLiveTextContent(text)
    fun commitLiveTextInput() = textInputHandler.commitLiveTextInput()
    fun commitTextInput(text: String) = textInputHandler.commitTextInput(text)
    fun cancelTextInput() = textInputHandler.cancelTextInput()

    // --- Delegation to ClipboardSelectionHandler ---

    fun copySelection() = clipboardSelectionHandler.copySelection()
    fun pasteSelection() = clipboardSelectionHandler.pasteSelection()
    fun convertSelectionToText() = clipboardSelectionHandler.convertSelectionToText()

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
        onScreenRefreshNeeded?.invoke()
    }

    // --- Note management ---

    private val _allNotebooks = MutableStateFlow<List<NotebookEntity>>(emptyList())
    val allNotebooks: StateFlow<List<NotebookEntity>> = _allNotebooks.asStateFlow()

    private val _noteNotebooks = MutableStateFlow<List<String>>(emptyList())
    val noteNotebooks: StateFlow<List<String>> = _noteNotebooks.asStateFlow()

    fun loadNoteManagementData() {
        viewModelScope.launch {
            _allNotebooks.value = notebookRepository.getAllNotebooks()
            _noteNotebooks.value = noteRepository.getNotebooksForNote(currentNote.value.id)
        }
    }

    fun renameNote(newTitle: String) {
        viewModelScope.launch {
            noteRepository.renameNote(currentNote.value.id, newTitle)
        }
    }

    fun updateNoteNotebooks(notebookIds: List<String>) {
        viewModelScope.launch {
            noteRepository.updateNoteNotebooks(currentNote.value.id, notebookIds)
            _noteNotebooks.value = notebookIds
        }
    }

    // Export helpers

    suspend fun getNotebookNotesForExport(notebookId: String) =
        notebookRepository.getNotesInNotebookOnce(notebookId).map { noteRepository.getNote(it.id) }

    suspend fun getNotebookName(notebookId: String): String =
        notebookRepository.getNotebook(notebookId)?.name ?: "Notebook"
}

data class EditorUiState(
    val isStrokeOptionsOpen: Boolean = false,
    val mode: EditorMode = EditorMode.Draw(),
    val selectedGeometricShape: GeometricShapeType = GeometricShapeType.LINE
) {
    val isGeometryMode: Boolean
        get() = mode is EditorMode.Draw && (mode as EditorMode.Draw).drawTool == DrawTool.GEOMETRY

    val isPenMode: Boolean
        get() = mode is EditorMode.Draw && (mode as EditorMode.Draw).drawTool == DrawTool.PEN
}
