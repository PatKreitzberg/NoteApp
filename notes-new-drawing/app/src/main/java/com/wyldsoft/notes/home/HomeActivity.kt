package com.wyldsoft.notes.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wyldsoft.notes.MainActivity
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.sync.GoogleDriveManager
import com.wyldsoft.notes.sync.SyncViewModel
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        isSignedIn = GoogleDriveManager.getSignedInAccount(this) != null

        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
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
                        onOpenNotebook = { notebookId -> openNotebook(notebookId) }
                    )
                }
            }
        }
    }

    private fun openNotebook(notebookId: String) {
        Log.d(TAG, "openNotebook notebookId=$notebookId")
        viewModel.getFirstNoteIdForNotebook(notebookId) { noteId ->
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
}
