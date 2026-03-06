package com.wyldsoft.notes.ui.components.dialogs

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wyldsoft.notes.presentation.viewmodel.SyncUiState
import com.wyldsoft.notes.presentation.viewmodel.SyncViewModel
import com.wyldsoft.notes.sync.GoogleDriveManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleDriveDialog(
    signInLauncher: ActivityResultLauncher<Intent>,
    signInError: State<String?>,
    syncViewModel: SyncViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleDriveManager.getSignedInAccount(context)) }
    val syncUiState by syncViewModel.syncUiState.collectAsState()
    val errorText by signInError

    // Refresh account when activity resumes (e.g., after sign-in intent returns)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                account = GoogleDriveManager.getSignedInAccount(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDialogShell(
        title = "Google Drive Sync",
        onDismiss = onDismiss,
        scrollable = true
    ) {
        if (account != null) {
            Text(
                text = "Signed in as: ${account?.email}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Last sync status
            when (val state = syncUiState) {
                is SyncUiState.Success -> {
                    val formatted = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        .format(Date(state.lastSyncedAt))
                    Text(
                        text = "Last synced: $formatted",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is SyncUiState.Error -> {
                    Text(
                        text = "Sync error: ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            val isSyncing = syncUiState is SyncUiState.Syncing
            Button(
                onClick = { syncViewModel.triggerSync() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing
            ) {
                Text(if (isSyncing) "Syncing..." else "Sync Now")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    GoogleDriveManager.signOut(context) { account = null }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        } else {
            Text(
                text = "Not signed in",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    val signInIntent = GoogleDriveManager.getSignInClient(context).signInIntent
                    signInLauncher.launch(signInIntent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In with Google")
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }
}
