package com.wyldsoft.notes.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.MainActivity
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme

class HomeActivity : ComponentActivity() {
    companion object {
        private const val TAG = "HomeActivity"
    }

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    HomeView(
                        viewModel = viewModel,
                        onOpenNotebook = { notebookId ->
                            openNotebook(notebookId)
                        }
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
