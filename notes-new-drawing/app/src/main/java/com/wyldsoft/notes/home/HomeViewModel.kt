package com.wyldsoft.notes.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.repository.FolderRepository
import com.wyldsoft.notes.data.database.repository.NotebookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentFolderId: String = FolderEntity.ROOT_ID,
    val breadcrumbs: List<FolderEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val db = (application as ScrotesApp).database
    private val appSettings = (application as ScrotesApp).appSettings
    private val folderRepository = FolderRepository(db.folderDao(), db.deletedItemDao())
    private val notebookRepository = NotebookRepository(db.notebookDao(), db.noteDao(), db.deletedItemDao())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // All non-trash folders for the move dialog
    private val _allFolders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val allFolders: StateFlow<List<FolderEntity>> = _allFolders.asStateFlow()

    init {
        navigateToFolder(FolderEntity.ROOT_ID)
        loadAllFolders()
    }

    fun navigateToFolder(folderId: String) {
        Log.d(TAG, "navigateToFolder folderId=$folderId")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val allChildren = folderRepository.getChildFolders(folderId)
            // Exclude the trash folder from regular folder listing (shown separately at root)
            val folders = if (folderId == FolderEntity.ROOT_ID) {
                allChildren.filter { it.id != FolderEntity.TRASH_ID }
            } else {
                allChildren
            }
            val notebooks = notebookRepository.getByFolder(folderId)
            val breadcrumbs = folderRepository.getBreadcrumbPath(folderId)
            _uiState.value = HomeUiState(
                currentFolderId = folderId,
                breadcrumbs = breadcrumbs,
                folders = folders,
                notebooks = notebooks,
                isLoading = false
            )
        }
    }

    fun createFolder(name: String) {
        Log.d(TAG, "createFolder name=$name")
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.createFolder(name, _uiState.value.currentFolderId)
            refreshCurrentFolder()
            loadAllFolders()
        }
    }

    fun createNotebook(name: String) {
        Log.d(TAG, "createNotebook name=$name")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.createNotebookWithFirstNote(
                name,
                _uiState.value.currentFolderId,
                appSettings.defaultPaginationEnabled
            )
            refreshCurrentFolder()
        }
    }

    fun renameFolder(id: String, newName: String) {
        Log.d(TAG, "renameFolder id=$id newName=$newName")
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.renameFolder(id, newName)
            refreshCurrentFolder()
            loadAllFolders()
        }
    }

    fun renameNotebook(id: String, newName: String) {
        Log.d(TAG, "renameNotebook id=$id newName=$newName")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.renameNotebook(id, newName)
            refreshCurrentFolder()
        }
    }

    fun moveFolder(id: String, newParentId: String) {
        Log.d(TAG, "moveFolder id=$id newParentId=$newParentId")
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.moveFolder(id, newParentId)
            refreshCurrentFolder()
            loadAllFolders()
        }
    }

    fun moveNotebook(id: String, newFolderId: String) {
        Log.d(TAG, "moveNotebook id=$id newFolderId=$newFolderId")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.moveNotebook(id, newFolderId)
            refreshCurrentFolder()
        }
    }

    fun moveFolderToTrash(id: String) {
        Log.d(TAG, "moveFolderToTrash id=$id")
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.moveToTrash(id)
            refreshCurrentFolder()
            loadAllFolders()
        }
    }

    fun moveNotebookToTrash(id: String) {
        Log.d(TAG, "moveNotebookToTrash id=$id")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.moveToTrash(id)
            refreshCurrentFolder()
        }
    }

    fun restoreFolderFromTrash(id: String) {
        Log.d(TAG, "restoreFolderFromTrash id=$id")
        viewModelScope.launch(Dispatchers.IO) {
            folderRepository.restoreFromTrash(id)
            refreshCurrentFolder()
            loadAllFolders()
        }
    }

    fun restoreNotebookFromTrash(id: String) {
        Log.d(TAG, "restoreNotebookFromTrash id=$id")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.restoreFromTrash(id)
            refreshCurrentFolder()
        }
    }

    fun getMostRecentNoteIdForNotebook(
        notebookId: String,
        onResult: (noteId: String?) -> Unit
    ) {
        Log.d(TAG, "getMostRecentNoteIdForNotebook notebookId=$notebookId")
        viewModelScope.launch(Dispatchers.IO) {
            val notes = db.noteDao().getByNotebook(notebookId)
            // Open the most recently modified note (updated on draw/viewport save)
            val noteId = notes.maxByOrNull { it.modifiedAt }?.id ?: notes.firstOrNull()?.id
            launch(Dispatchers.Main) {
                onResult(noteId)
            }
        }
    }

    private fun loadAllFolders() {
        Log.d(TAG, "loadAllFolders")
        viewModelScope.launch(Dispatchers.IO) {
            val all = folderRepository.getAllFolders()
            _allFolders.value = all.filter {
                it.id != FolderEntity.TRASH_ID && it.id != FolderEntity.ROOT_ID
            }
        }
    }

    private suspend fun refreshCurrentFolder() {
        Log.d(TAG, "refreshCurrentFolder")
        val folderId = _uiState.value.currentFolderId
        val allChildren = folderRepository.getChildFolders(folderId)
        val folders = if (folderId == FolderEntity.ROOT_ID) {
            allChildren.filter { it.id != FolderEntity.TRASH_ID }
        } else {
            allChildren
        }
        val notebooks = notebookRepository.getByFolder(folderId)
        _uiState.value = _uiState.value.copy(
            folders = folders,
            notebooks = notebooks
        )
    }
}
