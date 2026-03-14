package com.wyldsoft.notes.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.data.repository.FolderRepository.Companion.TRASH_FOLDER_ID
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
        .flatMapLatest { folderId -> folderRepository.getSubfolders(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notebooks: StateFlow<List<NotebookEntity>> = _currentFolderId
        .filterNotNull()
        .flatMapLatest { folderId -> notebookRepository.getNotebooksInFolder(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val looseNotes: StateFlow<List<NoteEntity>> = _currentFolderId
        .filterNotNull()
        .flatMapLatest { folderId -> noteRepository.getLooseNotesInFolder(folderId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _allFolders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val allFolders: StateFlow<List<FolderEntity>> = _allFolders.asStateFlow()

    private val _allNotebooks = MutableStateFlow<List<NotebookEntity>>(emptyList())
    val allNotebooks: StateFlow<List<NotebookEntity>> = _allNotebooks.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _showCreateNotebookDialog = MutableStateFlow(false)
    val showCreateNotebookDialog: StateFlow<Boolean> = _showCreateNotebookDialog.asStateFlow()

    init {
        viewModelScope.launch {
            val rootFolder = folderRepository.getRootFolder()
            folderRepository.ensureTrashFolderExists()
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

    fun showCreateFolderDialog() { _showCreateFolderDialog.value = true }
    fun hideCreateFolderDialog() { _showCreateFolderDialog.value = false }
    fun showCreateNotebookDialog() { _showCreateNotebookDialog.value = true }
    fun hideCreateNotebookDialog() { _showCreateNotebookDialog.value = false }

    suspend fun getFirstNoteInNotebook(notebookId: String): String? {
        return notebookRepository.getFirstNoteInNotebook(notebookId)?.id
    }

    // Notebook operations
    fun renameNotebook(notebookId: String, newName: String) {
        viewModelScope.launch { notebookRepository.renameNotebook(notebookId, newName) }
    }

    fun deleteNotebook(notebookId: String) {
        viewModelScope.launch { notebookRepository.moveNotebookToTrash(notebookId) }
    }

    fun moveNotebook(notebookId: String, targetFolderId: String) {
        viewModelScope.launch { notebookRepository.moveNotebook(notebookId, targetFolderId) }
    }

    // Folder operations
    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch { folderRepository.renameFolder(folderId, newName) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.deleteFolder(folderId)
            // Navigate to parent if current folder was deleted
            if (_currentFolderId.value == folderId) {
                val rootFolder = folderRepository.getRootFolder()
                navigateToFolder(rootFolder.id)
            }
        }
    }

    fun moveFolder(folderId: String, targetParentFolderId: String) {
        viewModelScope.launch { folderRepository.moveFolder(folderId, targetParentFolderId) }
    }

    // Note operations
    fun createLooseNote() {
        viewModelScope.launch {
            val currentId = _currentFolderId.value ?: return@launch
            noteRepository.createLooseNote(currentId)
        }
    }

    fun renameNote(noteId: String, newName: String) {
        viewModelScope.launch { noteRepository.renameNote(noteId, newName) }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { noteRepository.moveNoteToTrash(noteId) }
    }

    fun moveNoteToFolder(noteId: String, folderId: String) {
        viewModelScope.launch { noteRepository.moveNoteToFolder(noteId, folderId) }
    }

    fun moveNoteToNotebook(noteId: String, notebookId: String) {
        viewModelScope.launch { noteRepository.moveNoteToNotebook(noteId, notebookId) }
    }

    fun updateNoteNotebooks(noteId: String, notebookIds: List<String>) {
        viewModelScope.launch { noteRepository.updateNoteNotebooks(noteId, notebookIds) }
    }

    suspend fun getNotebooksForNote(noteId: String): List<String> {
        return noteRepository.getNotebooksForNote(noteId)
    }

    fun isTrashFolder(folderId: String) = folderId == TRASH_FOLDER_ID

    fun isCurrentFolderTrash() = _currentFolderId.value == TRASH_FOLDER_ID

    // Notebook trash operations
    fun restoreNotebook(notebookId: String) {
        viewModelScope.launch { notebookRepository.restoreNotebook(notebookId) }
    }

    fun permanentlyDeleteNotebook(notebookId: String) {
        viewModelScope.launch { notebookRepository.permanentlyDeleteNotebook(notebookId) }
    }

    // Folder trash operations
    fun restoreFolder(folderId: String) {
        viewModelScope.launch { folderRepository.restoreFolder(folderId) }
    }

    fun permanentlyDeleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.permanentlyDeleteFolder(folderId)
            if (_currentFolderId.value == folderId) {
                navigateToFolder(folderRepository.getRootFolder().id)
            }
        }
    }

    // Note trash operations
    fun restoreNote(noteId: String) {
        viewModelScope.launch { noteRepository.restoreNote(noteId) }
    }

    fun permanentlyDeleteNote(noteId: String) {
        viewModelScope.launch { noteRepository.permanentlyDeleteNote(noteId) }
    }

    fun emptyTrash() {
        viewModelScope.launch { folderRepository.emptyTrash() }
    }

    fun loadAllFoldersAndNotebooks() {
        viewModelScope.launch {
            _allFolders.value = folderRepository.getAllFolders()
            _allNotebooks.value = notebookRepository.getAllNotebooks()
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
