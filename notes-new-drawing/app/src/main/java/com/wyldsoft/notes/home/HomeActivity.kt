package com.wyldsoft.notes.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wyldsoft.notes.MainActivity
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.sync.GoogleDriveManager
import com.wyldsoft.notes.sync.SyncViewModel
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {
    companion object {
        private const val TAG = "HomeActivity"
    }

    private val viewModel: HomeViewModel by viewModels()

    private val syncViewModel: SyncViewModel by viewModels {
        val app = application as ScrotesApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SyncViewModel(app.syncRepository) as T
        }
    }

    private var isSignedIn by mutableStateOf(false)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "signInLauncher resultCode=${result.resultCode}")
        isSignedIn = GoogleDriveManager.getSignedInAccount(this) != null
        if (isSignedIn) {
            syncViewModel.triggerSync()
        }
    }

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            Log.d(TAG, "pdfPickerLauncher: user cancelled")
            return@registerForActivityResult
        }
        Log.d(TAG, "pdfPickerLauncher uri=$uri")
        // Take persistent read permission so we can re-open this file in future sessions
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val displayName = resolveDisplayName(uri)
        viewModel.importPdf(uri, displayName) { noteId ->
            openNoteAsPdf(noteId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        isSignedIn = GoogleDriveManager.getSignedInAccount(this) != null
        observeNotebookExport()

        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val app = application as ScrotesApp
                    val appSettings = app.appSettings
                    var defaultPagination by remember { mutableStateOf(appSettings.defaultPaginationEnabled) }
                    var gestureMappings by remember { mutableStateOf(appSettings.getAllGestureMappings()) }
                    var scribbleToErase by remember { mutableStateOf(appSettings.scribbleToEraseEnabled) }
                    var circleToSelect by remember { mutableStateOf(appSettings.circleToSelectEnabled) }
                    HomeView(
                        viewModel = viewModel,
                        syncViewModel = syncViewModel,
                        isSignedIn = isSignedIn,
                        onSignInClick = {
                            Log.d(TAG, "onSignInClick")
                            val intent = GoogleDriveManager.getSignInClient(this).signInIntent
                            signInLauncher.launch(intent)
                        },
                        onSignOutClick = {
                            Log.d(TAG, "onSignOutClick")
                            GoogleDriveManager.signOut(this) {
                                isSignedIn = false
                            }
                        },
                        onOpenNotebook = { notebookId -> openNotebook(notebookId) },
                        onOpenNotebookAtNote = { notebookId, noteId, scrollY ->
                            openNotebookAtNote(notebookId, noteId, scrollY)
                        },
                        onImportPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        onShareNotebook = { notebookId ->
                            Log.d(TAG, "onShareNotebook notebookId=$notebookId")
                            viewModel.startNotebookExport(notebookId)
                        },
                        defaultPaginationEnabled = defaultPagination,
                        onDefaultPaginationChanged = { enabled ->
                            appSettings.defaultPaginationEnabled = enabled
                            defaultPagination = enabled
                        },
                        gestureMappings = gestureMappings,
                        onGestureMappingsChanged = { mappings ->
                            appSettings.saveGestureMappings(mappings)
                            gestureMappings = appSettings.getAllGestureMappings()
                        },
                        scribbleToEraseEnabled = scribbleToErase,
                        onScribbleToEraseToggle = { enabled ->
                            appSettings.scribbleToEraseEnabled = enabled
                            scribbleToErase = enabled
                        },
                        circleToSelectEnabled = circleToSelect,
                        onCircleToSelectToggle = { enabled ->
                            appSettings.circleToSelectEnabled = enabled
                            circleToSelect = enabled
                        }
                    )
                }
            }
        }
    }

    private fun observeNotebookExport() {
        Log.d(TAG, "observeNotebookExport")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notebookExportState.collect { state ->
                    when (state) {
                        is NotebookExportState.Done -> {
                            sharePdfFile(state.file)
                            viewModel.clearExportState()
                        }
                        is NotebookExportState.Error -> {
                            Toast.makeText(this@HomeActivity, state.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearExportState()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun sharePdfFile(file: java.io.File) {
        Log.d(TAG, "sharePdfFile ${file.absolutePath}")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Notebook PDF"))
    }

    private fun openNotebook(notebookId: String) {
        Log.d(TAG, "openNotebook notebookId=$notebookId")
        viewModel.getMostRecentNoteIdForNotebook(notebookId) { noteId ->
            if (noteId != null) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("noteId", noteId)
                    putExtra("notebookId", notebookId)
                }
                startActivity(intent)
            } else {
                Log.e(TAG, "No notes found for notebook $notebookId")
            }
        }
    }

    private fun openNotebookAtNote(notebookId: String, noteId: String, scrollY: Float) {
        Log.d(TAG, "openNotebookAtNote notebookId=$notebookId noteId=$noteId scrollY=$scrollY")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("notebookId", notebookId)
            putExtra("initialScrollY", scrollY)
        }
        startActivity(intent)
    }

    private fun openNoteAsPdf(noteId: String) {
        Log.d(TAG, "openNoteAsPdf noteId=$noteId")
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("noteId", noteId)
        }
        startActivity(intent)
    }

    private fun resolveDisplayName(uri: android.net.Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        .removeSuffix(".pdf")
                } else null
            } ?: uri.lastPathSegment ?: "Imported PDF"
        } catch (e: Exception) {
            Log.e(TAG, "resolveDisplayName failed", e)
            "Imported PDF"
        }
    }
}
