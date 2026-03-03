package com.wyldsoft.notes

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import com.wyldsoft.notes.sdkintegration.generic.GenericDrawingActivity
import com.wyldsoft.notes.sdkintegration.onyx.OnyxDrawingActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.ui.theme.MinimaleditorTheme
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
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

    val signInError = mutableStateOf<String?>(null)

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                signInError.value = null
            } catch (e: ApiException) {
                Log.e("MainActivity", "Google sign-in failed: status=${e.statusCode}", e)
                signInError.value = "Sign-in failed (code ${e.statusCode}): ${e.message}"
            }
        } else {
            Log.e("MainActivity", "Google sign-in cancelled or failed: resultCode=${result.resultCode}")
            signInError.value = "Sign-in was cancelled or failed (result code ${result.resultCode})"
        }
    }

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
                                signInLauncher = signInLauncher,
                                signInError = signInError,
                                onNotebookSelected = { notebookId, noteId ->
                                    val activityClass = if (DeviceHelper.isOnyxDevice) {
                                        OnyxDrawingActivity::class.java
                                    } else {
                                        GenericDrawingActivity::class.java
                                    }
                                    val intent = Intent(context, activityClass).apply {
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
