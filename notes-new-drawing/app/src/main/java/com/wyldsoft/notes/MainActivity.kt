// MainActivity.kt - Refactored
package com.wyldsoft.notes

import androidx.activity.ComponentActivity
import com.wyldsoft.notes.sdkintegration.onyx.OnyxDrawingActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.core.graphics.createBitmap
import com.onyx.android.sdk.pen.TouchHelper
import com.wyldsoft.notes.editor.EditorView
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.ViewModelFactory
import com.wyldsoft.notes.data.repository.*
import com.wyldsoft.notes.data.database.NotesDatabase
import com.wyldsoft.notes.drawing.DrawingActivityInterface
import android.graphics.PointF
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.onyx.android.sdk.api.device.epd.EpdController
import com.wyldsoft.notes.home.HomeView

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

//        viewModelFactory = ViewModelFactory(
//            noteRepository = noteRepository,
//            folderRepository = folderRepository,
//            notebookRepository = notebookRepository
//        )
//
//        initializeSDK()
//        initializePaint()
//        initializeDeviceReceiver()

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
                            HomeView(
                                noteRepository,
                                notebookRepository,
                                folderRepository,
                                onNotebookSelected = { notebookId, noteId ->
                                    navController.navigate("editor/$notebookId/$noteId")
                                }
                            )
                        }

                        composable(
                            "editor/{notebookId}/{noteId}",
                            arguments = listOf(
                                navArgument("notebookId") { type = NavType.StringType },
                                navArgument("noteId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable

                            LaunchedEffect(noteId) {
                                noteRepository.setCurrentNote(noteId)
                            }

                            EditorView(
                                noteRepository,
                                notebookRepository,
                                onSurfaceViewCreated = { sv ->
                                    handleSurfaceViewCreated(sv)
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