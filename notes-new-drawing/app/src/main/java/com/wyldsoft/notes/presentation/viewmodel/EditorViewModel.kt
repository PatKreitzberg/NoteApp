package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.PointF
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class EditorViewModel(
    private val noteRepository: NoteRepository
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
    
    // Pagination state
    private val _isPaginationEnabled = MutableStateFlow(true)
    val isPaginationEnabled: StateFlow<Boolean> = _isPaginationEnabled.asStateFlow()
    
    private val _paperSize = MutableStateFlow(PaperSize.LETTER)
    val paperSize: StateFlow<PaperSize> = _paperSize.asStateFlow()
    
    private val _currentPageNumber = MutableStateFlow(1)
    val currentPageNumber: StateFlow<Int> = _currentPageNumber.asStateFlow()
    
    private val _screenWidth = MutableStateFlow(0)
    val screenWidth: StateFlow<Int> = _screenWidth.asStateFlow()
    
    private val _pageHeight = MutableStateFlow(0f)
    val pageHeight: StateFlow<Float> = _pageHeight.asStateFlow()
    
    init {
        viewModelScope.launch {
            if (currentNote.value == null) {
                noteRepository.createNewNote()
            }
        }
        
        // Save viewport state when it changes (with debounce to avoid too frequent saves)
        viewModelScope.launch {
            viewportState
                .debounce(500) // Wait 500ms after last change before saving
                .collect { state ->
                    currentNote.value?.let { note ->
                        noteRepository.updateViewportState(
                            note.id,
                            state.scale,
                            state.offsetX,
                            state.offsetY
                        )
                    }
                }
        }
        
        // Restore viewport state when note changes
        viewModelScope.launch {
            currentNote.collect { note ->
                note?.let {
                    viewportManager.setState(
                        it.viewportScale,
                        it.viewportOffsetX,
                        it.viewportOffsetY
                    )
                    // Initialize pagination state
                    _isPaginationEnabled.value = it.isPaginationEnabled
                    _paperSize.value = PaperSize.fromString(it.paperSize)
                    calculatePageDimensions()
                }
            }
        }
    }
    
    fun startDrawing() {
        _isDrawing.value = true
        _uiState.value = _uiState.value.copy(isStrokeOptionsOpen = false)
    }
    
    fun endDrawing() {
        _isDrawing.value = false
        forceRefresh()
    }
    
    fun addShape(points: List<PointF>, pressures: List<Float> = emptyList()) {
        viewModelScope.launch {
            currentNote.value?.let { note ->
                val shape = Shape(
                    type = ShapeType.STROKE,
                    points = points,
                    strokeWidth = _currentPenProfile.value.strokeWidth,
                    strokeColor = _currentPenProfile.value.getColorAsInt(),
                    pressure = pressures
                )
                noteRepository.addShape(note.id, shape)
            }
        }
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
            currentNote.value?.let { note ->
                noteRepository.updatePaginationSettings(note.id, enabled, _paperSize.value.name)
            }
        }
        // Update viewport restrictions
        if (enabled) {
            viewportManager.setPaginationMode(true, _screenWidth.value)
        } else {
            viewportManager.setPaginationMode(false, 0)
        }
    }
    
    fun updatePaperSize(paperSize: PaperSize) {
        _paperSize.value = paperSize
        calculatePageDimensions()
        viewModelScope.launch {
            currentNote.value?.let { note ->
                noteRepository.updatePaginationSettings(note.id, _isPaginationEnabled.value, paperSize.name)
            }
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
        
        val separatorHeight = 10 // 10dp as specified
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