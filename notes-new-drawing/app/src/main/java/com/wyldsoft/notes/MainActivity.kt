package com.wyldsoft.notes

import android.content.Intent
import androidx.activity.ComponentActivity
import com.wyldsoft.notes.sdkintegration.onyx.OnyxDrawingActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wyldsoft.notes.home.HomeView
import com.wyldsoft.notes.presentation.viewmodel.HomeViewModel

/**
 * Main activity that uses Onyx SDK implementation.
 *
 * To support a different SDK in the future, you would:
 * 1. Create a new implementation like HuionDrawingActivity
 * 2. Change this class to extend that implementation instead
 * 3. Or use a factory pattern to choose the implementation at runtime
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ScrotesApp
        val viewModel = HomeViewModel(app.noteRepository, app.notebookRepository, app.folderRepository)

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
                                gestureSettingsRepository = app.gestureSettingsRepository,
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
}
