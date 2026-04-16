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
import com.wyldsoft.notes.data.mappers.ShapeMapper
import com.wyldsoft.notes.models.PaperTemplate
import com.wyldsoft.notes.pdf.NoteExportData
import com.wyldsoft.notes.pdf.PdfExporter
import com.wyldsoft.notes.rendering.PaginationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeSearchResult(
    val notebookId: String,
    val notebookName: String,
    val noteId: String,
    val matchText: String,
    val boundingTop: Float
)

sealed class NotebookExportState {
    object Idle : NotebookExportState()
    object InProgress : NotebookExportState()
    data class Done(val file: java.io.File) : NotebookExportState()
    data class Error(val message: String) : NotebookExportState()
}

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

    private val _notebookExportState = MutableStateFlow<NotebookExportState>(NotebookExportState.Idle)
    val notebookExportState: StateFlow<NotebookExportState> = _notebookExportState.asStateFlow()

    init {
        navigateToFolder(FolderEntity.ROOT_ID)
        loadAllFolders()
    }

    fun navigateUp() {
        Log.d(TAG, "navigateUp")
        val breadcrumbs = _uiState.value.breadcrumbs
        val parentId = if (breadcrumbs.size >= 2) breadcrumbs[breadcrumbs.size - 2].id
                       else FolderEntity.ROOT_ID
        navigateToFolder(parentId)
    }

    fun reorderFolders(orderedFolders: List<FolderEntity>) {
        Log.d(TAG, "reorderFolders count=${orderedFolders.size}")
        viewModelScope.launch(Dispatchers.IO) {
            orderedFolders.forEachIndexed { index, folder ->
                db.folderDao().updateSortOrder(folder.id, index)
            }
            refreshCurrentFolder()
        }
    }

    fun reorderNotebooks(orderedNotebooks: List<NotebookEntity>) {
        Log.d(TAG, "reorderNotebooks count=${orderedNotebooks.size}")
        viewModelScope.launch(Dispatchers.IO) {
            orderedNotebooks.forEachIndexed { index, notebook ->
                db.notebookDao().updateSortOrder(notebook.id, index)
            }
            refreshCurrentFolder()
        }
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

    private val _searchResults = MutableStateFlow<List<HomeSearchResult>>(emptyList())
    val searchResults: StateFlow<List<HomeSearchResult>> = _searchResults.asStateFlow()

    fun search(query: String) {
        Log.d(TAG, "search query='$query'")
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<HomeSearchResult>()

            // HTR results across all non-trash notes
            val htrRows = db.htrResultDao().searchAcrossNotes(query)
            for (row in htrRows) {
                // noteId from htr row — get the parent note for the notebook's noteId mapping
                results.add(
                    HomeSearchResult(
                        notebookId = row.notebookId,
                        notebookName = row.notebookName,
                        noteId = row.noteId,
                        matchText = row.text,
                        boundingTop = row.boundingTop
                    )
                )
            }

            // TextShapes across all non-trash notes
            val textRows = db.shapeDao().searchTextShapes(query)
            for (row in textRows) {
                // Compute boundingTop from first point in the points JSON
                val top = extractFirstPointY(row.points)
                results.add(
                    HomeSearchResult(
                        notebookId = row.notebookId,
                        notebookName = row.notebookName,
                        noteId = row.noteId,
                        matchText = row.text ?: "",
                        boundingTop = top
                    )
                )
            }

            _searchResults.value = results.sortedBy { it.notebookName }
        }
    }

    private fun extractFirstPointY(pointsJson: String): Float {
        return try {
            // Points are stored as [[x,y], [x,y], ...] or similar JSON
            val arr = org.json.JSONArray(pointsJson)
            if (arr.length() > 0) {
                val first = arr.getJSONArray(0)
                first.getDouble(1).toFloat()
            } else 0f
        } catch (e: Exception) {
            0f
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

    fun startNotebookExport(notebookId: String) {
        Log.d(TAG, "startNotebookExport notebookId=$notebookId")
        viewModelScope.launch(Dispatchers.IO) {
            _notebookExportState.value = NotebookExportState.InProgress
            try {
                val context = getApplication<Application>()
                val dm = context.resources.displayMetrics

                val notebook = db.notebookDao().getById(notebookId)
                    ?: throw Exception("Notebook not found")
                val notes = db.noteDao().getByNotebook(notebookId)
                if (notes.isEmpty()) throw Exception("No notes in notebook")

                val notesData = notes.map { note ->
                    val effectiveTemplate = if (note.overrideNotebookSettings) {
                        runCatching { PaperTemplate.valueOf(note.paperTemplate) }.getOrDefault(PaperTemplate.BLANK)
                    } else {
                        runCatching { PaperTemplate.valueOf(notebook.template) }.getOrDefault(PaperTemplate.BLANK)
                    }
                    val aspectRatio = if (note.pdfPageAspectRatio > 0f) note.pdfPageAspectRatio
                                      else PaginationManager.DEFAULT_ASPECT_RATIO
                    val pm = PaginationManager(dm.widthPixels, dm.heightPixels, dm.density, aspectRatio)
                    if (note.pdfPath != null && note.pdfPageCount > 1) {
                        pm.addPages(note.pdfPageCount - 1)
                    }
                    val shapes = db.shapeDao().getByNoteId(note.id).map { ShapeMapper.toShape(it) }
                    val pdfUri = note.pdfPath?.let { Uri.parse(it) }
                    NoteExportData(
                        noteId = note.id,
                        pdfUri = pdfUri,
                        shapes = shapes,
                        paginationManager = pm,
                        template = effectiveTemplate
                    )
                }

                val file = PdfExporter.exportNotebook(context, notebookId, notesData)
                _notebookExportState.value = NotebookExportState.Done(file)
            } catch (e: Exception) {
                Log.e(TAG, "Notebook export failed", e)
                _notebookExportState.value = NotebookExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun clearExportState() {
        Log.d(TAG, "clearExportState")
        _notebookExportState.value = NotebookExportState.Idle
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
