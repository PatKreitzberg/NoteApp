package com.wyldsoft.notes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.NotebookRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val folderRepository: FolderRepository,
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
        _showCreateNotebookDialog.value = true
    }
    
    fun hideCreateNotebookDialog() {
        _showCreateNotebookDialog.value = false
    }
    
    suspend fun getFirstNoteInNotebook(notebookId: String): String? {
        return notebookRepository.getFirstNoteInNotebook(notebookId)?.id
    }
    
    fun renameNotebook(notebookId: String, newName: String) {
        viewModelScope.launch {
            notebookRepository.updateNotebookName(notebookId, newName)
        }
    }
}