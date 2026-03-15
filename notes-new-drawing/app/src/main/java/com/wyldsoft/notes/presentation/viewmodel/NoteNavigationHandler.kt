package com.wyldsoft.notes.presentation.viewmodel

import android.util.Log
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.session.NoteSessionCache
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles note navigation within a notebook (prev/next page).
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class NoteNavigationHandler(
    val notebookId: String?,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val scope: CoroutineScope,
    private val viewportManager: ViewportManager,
    private val getCurrentNote: () -> Note,
    private val onSwitchNote: suspend (String) -> Unit,
    private val sessionCache: NoteSessionCache
) {
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(true)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    var onNoteSwitched: (() -> Unit)? = null

    fun initNavigationState() {
        if (notebookId == null) {
            _canGoBack.value = false
            _canGoForward.value = false
            return
        }
        scope.launch {
            updateNavigationState()
            precacheInitialNotes()
        }
    }

    suspend fun updateNavigationState() {
        val nbId = notebookId ?: return
        val notes = notebookRepository.getNotesInNotebookOnce(nbId)
        val currentIndex = notes.indexOfFirst { it.id == getCurrentNote().id }
        _canGoBack.value = currentIndex > 0
        _canGoForward.value = true
    }

    fun navigateBackward() {
        val nbId = notebookId ?: return
        scope.launch {
            val notes = notebookRepository.getNotesInNotebookOnce(nbId)
            val currentIndex = notes.indexOfFirst { it.id == getCurrentNote().id }
            if (currentIndex > 0) switchToNote(notes[currentIndex - 1].id)
        }
    }

    fun navigateForward() {
        val nbId = notebookId ?: return
        scope.launch {
            val notes = notebookRepository.getNotesInNotebookOnce(nbId)
            val currentIndex = notes.indexOfFirst { it.id == getCurrentNote().id }
            if (currentIndex < notes.size - 1) {
                switchToNote(notes[currentIndex + 1].id)
            } else if (getCurrentNote().shapes.isNotEmpty()) {
                val newNote = notebookRepository.createNoteInNotebook(nbId)
                switchToNote(newNote.id)
            }
        }
    }

    private suspend fun switchToNote(noteId: String) {
        noteRepository.setCurrentNote(noteId)
        val note = getCurrentNote()
        viewportManager.setState(note.viewportScale, note.viewportScrollX, note.viewportScrollY)
        onSwitchNote(noteId)
        updateNavigationState()
        onNoteSwitched?.invoke()
        scope.launch { precacheAdjacentNotes() }
    }

    private suspend fun precacheAdjacentNotes() {
        val nbId = notebookId ?: return
        val notes = notebookRepository.getNotesInNotebookOnce(nbId)
        val currentIndex = notes.indexOfFirst { it.id == getCurrentNote().id }
        if (currentIndex < 0) return

        if (currentIndex > 0) {
            sessionCache.preload(notes[currentIndex - 1].id, noteRepository)
        }
        if (currentIndex < notes.size - 1) {
            sessionCache.preload(notes[currentIndex + 1].id, noteRepository)
        }
    }

    private suspend fun precacheInitialNotes() {
        val nbId = notebookId ?: return
        val notes = notebookRepository.getNotesInNotebookOnce(nbId)
        val limit = minOf(3, notes.size)
        for (i in 0 until limit) {
            sessionCache.preload(notes[i].id, noteRepository)
        }
        Log.d("NoteNavigationHandler", "Pre-cached $limit initial notes")
    }
}
