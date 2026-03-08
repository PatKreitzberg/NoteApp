package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.Rect
import android.util.Log
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.utils.PaginationConstants
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages pagination and paper settings state.
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class PaginationHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val viewportManager: ViewportManager,
    private val getCurrentNote: () -> Note,
    initialNote: Note
) {
    private val _isPaginationEnabled = MutableStateFlow(initialNote.isPaginationEnabled)
    val isPaginationEnabled: StateFlow<Boolean> = _isPaginationEnabled.asStateFlow()

    private val _paperSize = MutableStateFlow(
        PaperSize.entries.find { it.name == initialNote.paperSize } ?: PaperSize.LETTER
    )
    val paperSize: StateFlow<PaperSize> = _paperSize.asStateFlow()

    private val _currentPageNumber = MutableStateFlow(1)
    val currentPageNumber: StateFlow<Int> = _currentPageNumber.asStateFlow()

    private val _paperTemplate = MutableStateFlow(
        PaperTemplate.fromString(initialNote.paperTemplate.name)
    )
    val paperTemplate: StateFlow<PaperTemplate> = _paperTemplate.asStateFlow()

    private val _screenWidth = MutableStateFlow(0)
    val screenWidth: StateFlow<Int> = _screenWidth.asStateFlow()

    private val _pageHeight = MutableStateFlow(0f)
    val pageHeight: StateFlow<Float> = _pageHeight.asStateFlow()

    /** Call when switching to a new note to reset pagination state. */
    fun resetForNote(note: Note) {
        _isPaginationEnabled.value = note.isPaginationEnabled
        viewportManager.isPaginationEnabled = note.isPaginationEnabled
        _paperSize.value = PaperSize.entries.find { it.name == note.paperSize } ?: PaperSize.LETTER
        _paperTemplate.value = PaperTemplate.fromString(note.paperTemplate.name)
        calculatePageDimensions()
    }

    fun setScreenWidth(width: Int) {
        _screenWidth.value = width
        calculatePageDimensions()
        viewportManager.pageWidth = width.toFloat()
    }

    fun calculatePageDimensions() {
        if (_screenWidth.value > 0) {
            _pageHeight.value = _screenWidth.value * _paperSize.value.aspectRatio
            viewportManager.pageHeight = _pageHeight.value
        }
    }

    fun updatePaginationEnabled(enabled: Boolean) {
        _isPaginationEnabled.value = enabled
        viewportManager.isPaginationEnabled = enabled
        scope.launch {
            Log.d("PaginationHandler", "Updating pagination for note: ${getCurrentNote().id}, enabled: $enabled")
            noteRepository.updatePaginationSettings(getCurrentNote().id, enabled, _paperSize.value.name)
        }
    }

    fun updatePaperSize(paperSize: PaperSize) {
        _paperSize.value = paperSize
        calculatePageDimensions()
        scope.launch {
            val note = getCurrentNote()
            Log.d("PaginationHandler", "Updating paper size for note: ${note.id}, new size: ${paperSize.name}")
            noteRepository.updatePaginationSettings(note.id, _isPaginationEnabled.value, paperSize.name)
        }
    }

    fun updatePaperTemplate(template: PaperTemplate) {
        _paperTemplate.value = template
        scope.launch {
            noteRepository.updatePaperTemplate(getCurrentNote().id, template.name)
        }
    }

    fun updateCurrentPage(scrollY: Float) {
        if (_isPaginationEnabled.value && _pageHeight.value > 0) {
            _currentPageNumber.value = (scrollY / _pageHeight.value).toInt() + 1
        }
    }

    fun getPageSeparatorRects(): List<Rect> {
        if (!_isPaginationEnabled.value || _pageHeight.value <= 0) return emptyList()

        val separatorHeight = PaginationConstants.SEPARATOR_HEIGHT.toInt()
        val rects = mutableListOf<Rect>()
        val pageHeightInt = _pageHeight.value.toInt()

        val viewportState = viewportManager.viewportState.value
        val viewportTop = viewportState.scrollY.toInt()
        val viewportBottom = viewportTop + (viewportManager.viewHeight / viewportState.scale).toInt()

        var pageY = pageHeightInt
        while (pageY - separatorHeight < viewportBottom + pageHeightInt) {
            if (pageY + separatorHeight > viewportTop - pageHeightInt) {
                rects.add(Rect(0, pageY, _screenWidth.value, pageY + separatorHeight))
            }
            pageY += pageHeightInt
        }

        return rects
    }
}
