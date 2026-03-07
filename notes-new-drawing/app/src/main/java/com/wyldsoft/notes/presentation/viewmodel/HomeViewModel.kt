package com.wyldsoft.notes.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.data.repository.RecognizedSegmentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchResult(
    val noteId: String,
    val noteTitle: String,
    val notebookId: String?,
    val matchedText: String
)

class HomeViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val folderRepository: FolderRepository,
    private val recognizedSegmentRepository: RecognizedSegmentRepository? = null,
) : ViewModel() {
    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()
    
    private val _currentFolder = MutableStateFlow<FolderEntity?>(null)
    val currentFolder: StateFlow<FolderEntity?> = _currentFolder.asStateFlow()
    
    private val _folderPath = MutableStateFlow<List<FolderEntity>>(emptyList())
    val folderPath: StateFlow<List<FolderEntity>> = _folderPath.asStateFlow()
    
    val subfolders: StateFlow<List<FolderEntity>> = _currentFolderId
        .filterNotNull()
        .flatMapLatest { folderId ->
            folderRepository.getSubfolders(folderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val notebooks: StateFlow<List<NotebookEntity>> = _currentFolderId
        .filterNotNull()
        .flatMapLatest { folderId ->
            notebookRepository.getNotebooksInFolder(folderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()
    
    private val _showCreateNotebookDialog = MutableStateFlow(false)
    val showCreateNotebookDialog: StateFlow<Boolean> = _showCreateNotebookDialog.asStateFlow()
    
    init {
        // Load root folder on initialization
        viewModelScope.launch {
            val rootFolder = folderRepository.getRootFolder()
            navigateToFolder(rootFolder.id)
        }
    }
    
    fun navigateToFolder(folderId: String) {
        viewModelScope.launch {
            _currentFolderId.value = folderId
            _currentFolder.value = folderRepository.getFolder(folderId)
            _folderPath.value = folderRepository.getFolderPath(folderId)
        }
    }
    
    fun createFolder(name: String) {
        viewModelScope.launch {
            val currentId = _currentFolderId.value ?: return@launch
            folderRepository.createFolder(name, currentId)
            _showCreateFolderDialog.value = false
        }
    }
    
    fun createNotebook(name: String) {
        Log.d("HomeViewModel", "Creating notebook with name: $name")
        viewModelScope.launch {
            val currentId = _currentFolderId.value ?: return@launch
            notebookRepository.createNotebook(name, currentId)
            _showCreateNotebookDialog.value = false
        }
    }
    
    fun showCreateFolderDialog() {
        _showCreateFolderDialog.value = true
    }
    
    fun hideCreateFolderDialog() {
        _showCreateFolderDialog.value = false
    }
    
    fun showCreateNotebookDialog() {
        Log.d("HomeViewModel", "Showing create notebook dialog")
        _showCreateNotebookDialog.value = true
    }
    
    fun hideCreateNotebookDialog() {
        Log.d("HomeViewModel", "Hiding create notebook dialog")
        _showCreateNotebookDialog.value = false
    }
    
    suspend fun getFirstNoteInNotebook(notebookId: String): String? {
        return notebookRepository.getFirstNoteInNotebook(notebookId)?.id
    }
    
    fun renameNotebook(notebookId: String, newName: String) {
        viewModelScope.launch {
            notebookRepository.renameNotebook(notebookId, newName)
        }
    }

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val repo = recognizedSegmentRepository
            if (repo == null) {
                _isSearching.value = false
                return@launch
            }
            val segments = repo.searchText(query)
            val results = segments.mapNotNull { segment ->
                try {
                    val note = noteRepository.getNote(segment.noteId)
                    val notebookId = noteRepository.getParentNotebookId(segment.noteId)
                    SearchResult(
                        noteId = segment.noteId,
                        noteTitle = note.title,
                        notebookId = notebookId,
                        matchedText = segment.recognizedText
                    )
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "Note ${segment.noteId} not found for search result", e)
                    null
                }
            }.distinctBy { it.noteId }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }
}