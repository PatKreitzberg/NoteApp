package com.wyldsoft.notes.home

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
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
        launchWithFullRefresh { folderRepository.createFolder(name, _uiState.value.currentFolderId) }
    }

    fun createNotebook(name: String) {
        Log.d(TAG, "createNotebook name=$name")
        launchWithFolderRefresh {
            notebookRepository.createNotebookWithFirstNote(
                name,
                _uiState.value.currentFolderId,
                appSettings.defaultPaginationEnabled
            )
        }
    }

    fun renameFolder(id: String, newName: String) {
        Log.d(TAG, "renameFolder id=$id newName=$newName")
        launchWithFullRefresh { folderRepository.renameFolder(id, newName) }
    }

    fun renameNotebook(id: String, newName: String) {
        Log.d(TAG, "renameNotebook id=$id newName=$newName")
        launchWithFolderRefresh { notebookRepository.renameNotebook(id, newName) }
    }

    fun moveFolder(id: String, newParentId: String) {
        Log.d(TAG, "moveFolder id=$id newParentId=$newParentId")
        launchWithFullRefresh { folderRepository.moveFolder(id, newParentId) }
    }

    fun moveNotebook(id: String, newFolderId: String) {
        Log.d(TAG, "moveNotebook id=$id newFolderId=$newFolderId")
        launchWithFolderRefresh { notebookRepository.moveNotebook(id, newFolderId) }
    }

    fun moveFolderToTrash(id: String) {
        Log.d(TAG, "moveFolderToTrash id=$id")
        launchWithFullRefresh { folderRepository.moveToTrash(id) }
    }

    fun moveNotebookToTrash(id: String) {
        Log.d(TAG, "moveNotebookToTrash id=$id")
        launchWithFolderRefresh { notebookRepository.moveToTrash(id) }
    }

    fun restoreFolderFromTrash(id: String) {
        Log.d(TAG, "restoreFolderFromTrash id=$id")
        launchWithFullRefresh { folderRepository.restoreFromTrash(id) }
    }

    fun restoreNotebookFromTrash(id: String) {
        Log.d(TAG, "restoreNotebookFromTrash id=$id")
        launchWithFolderRefresh { notebookRepository.restoreFromTrash(id) }
    }

    private fun launchWithFullRefresh(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block(); refreshCurrentFolder(); loadAllFolders() }
    }

    private fun launchWithFolderRefresh(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block(); refreshCurrentFolder() }
    }

    /**
     * Imports a PDF from [uri], creating a new notebook named [displayName] containing
     * a single PDF-backed note. Calls [onResult] with the new note's ID on the main thread.
     */
    fun importPdf(uri: Uri, displayName: String, onResult: (noteId: String) -> Unit) {
        Log.d(TAG, "importPdf uri=$uri displayName=$displayName")
        viewModelScope.launch(Dispatchers.IO) {
            val (pageCount, pageAspectRatio) = readPdfMetadata(uri)

            val now = System.currentTimeMillis()
            val notebook = NotebookEntity(
                id = NanoIdUtils.randomNanoId(),
                name = displayName,
                folderId = _uiState.value.currentFolderId,
                createdAt = now,
                modifiedAt = now
            )
            db.notebookDao().insert(notebook)

            val note = NoteEntity(
                id = NanoIdUtils.randomNanoId(),
                title = "Page 1",
                parentNotebookId = notebook.id,
                createdAt = now,
                modifiedAt = now,
                isPaginationEnabled = true,
                pdfPath = uri.toString(),
                pdfPageCount = pageCount,
                pdfPageAspectRatio = pageAspectRatio,
                overrideNotebookSettings = true
            )
            db.noteDao().insert(note)

            refreshCurrentFolder()
            launch(Dispatchers.Main) { onResult(note.id) }
        }
    }

    private fun readPdfMetadata(uri: Uri): Pair<Int, Float> {
        return try {
            getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtLeast(1)
                    val ratio = if (count > 0) {
                        renderer.openPage(0).use { page ->
                            if (page.width > 0) page.height.toFloat() / page.width.toFloat()
                            else 11f / 8.5f
                        }
                    } else 11f / 8.5f
                    Pair(count, ratio)
                }
            } ?: Pair(1, 11f / 8.5f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF metadata", e)
            Pair(1, 11f / 8.5f)
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
