// MainActivity.kt - Refactored
package com.wyldsoft.notes

import android.content.Intent
import androidx.activity.ComponentActivity
import com.wyldsoft.notes.sdkintegration.onyx.OnyxDrawingActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import com.wyldsoft.notes.data.repository.*
import com.wyldsoft.notes.data.database.NotesDatabase
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wyldsoft.notes.home.HomeView
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel


/**
 * Main activity that uses Onyx SDK implementation.
 * This class now simply extends the Onyx-specific implementation.
 *
 * To support a different SDK in the future, you would:
 * 1. Create a new implementation like HuionDrawingActivity
 * 2. Change this class to extend that implementation instead
 * 3. Or use a factory pattern to choose the implementation at runtime
 */
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Factory method to create the appropriate drawing activity
         * based on device type or configuration
         */
        fun createForDevice(): Class<out MainActivity> {
            // Future: Add device detection logic here
            // For now, always return Onyx implementation
            return MainActivity::class.java
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repositories
        val database = NotesDatabase.getDatabase(this)
        val noteRepository = NoteRepositoryImpl(
            noteDao = database.noteDao(),
            shapeDao = database.shapeDao()
        )
        val folderRepository = FolderRepositoryImpl(
            folderDao = database.folderDao()
        )
        val notebookRepository = NotebookRepositoryImpl(
            notebookDao = database.notebookDao(),
            noteDao = database.noteDao()
        )
        val viewModel = HomeViewModel(noteRepository, notebookRepository, folderRepository)

        setContent {
            MinimaleditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            val context = LocalContext.current
                            HomeView(
                                viewModel,
                                onNotebookSelected = { notebookId, noteId ->
                                    val intent = Intent(context, OnyxDrawingActivity::class.java).apply {
                                        putExtra("notebookId", notebookId)
                                        putExtra("noteId", noteId)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }



    // MainActivity can add any app-specific functionality here
    // while inheriting all the drawing capabilities from OnyxDrawingActivity
}