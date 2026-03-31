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
    private val folderRepository = FolderRepository(db.folderDao())
    private val notebookRepository = NotebookRepository(db.notebookDao(), db.noteDao())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        navigateToFolder(FolderEntity.ROOT_ID)
    }

    fun navigateToFolder(folderId: String) {
        Log.d(TAG, "navigateToFolder folderId=$folderId")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val folders = folderRepository.getChildFolders(folderId)
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
        }
    }

    fun createNotebook(name: String) {
        Log.d(TAG, "createNotebook name=$name")
        viewModelScope.launch(Dispatchers.IO) {
            notebookRepository.createNotebookWithFirstNote(
                name,
                _uiState.value.currentFolderId
            )
            refreshCurrentFolder()
        }
    }

    fun getFirstNoteIdForNotebook(
        notebookId: String,
        onResult: (noteId: String?) -> Unit
    ) {
        Log.d(TAG, "getFirstNoteIdForNotebook notebookId=$notebookId")
        viewModelScope.launch(Dispatchers.IO) {
            val notes = db.noteDao().getByNotebook(notebookId)
            val noteId = notes.firstOrNull()?.id
            launch(Dispatchers.Main) {
                onResult(noteId)
            }
        }
    }

    private suspend fun refreshCurrentFolder() {
        Log.d(TAG, "refreshCurrentFolder")
        val folderId = _uiState.value.currentFolderId
        val folders = folderRepository.getChildFolders(folderId)
        val notebooks = notebookRepository.getByFolder(folderId)
        _uiState.value = _uiState.value.copy(
            folders = folders,
            notebooks = notebooks
        )
    }
}
