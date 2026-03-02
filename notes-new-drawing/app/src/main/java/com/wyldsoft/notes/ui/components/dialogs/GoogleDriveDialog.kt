package com.wyldsoft.notes.ui.components.dialogs

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wyldsoft.notes.sync.GoogleDriveManager
import kotlinx.coroutines.launch

@Composable
fun GoogleDriveDialog(
    signInLauncher: ActivityResultLauncher<Intent>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleDriveManager.getSignedInAccount(context)) }
    var statusText by remember { mutableStateOf("") }
    var isBackingUp by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
        title = "Google Drive",
        onDismiss = onDismiss,
        scrollable = true
    ) {
        if (account != null) {
            Text(
                text = "Signed in as: ${account?.email}",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = {
                    GoogleDriveManager.signOut(context) {
                        account = null
                        statusText = "Signed out"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    isBackingUp = true
                    statusText = "Backing up..."
                    coroutineScope.launch {
                        val result = GoogleDriveManager.uploadDatabaseFile(context, account!!)
                        isBackingUp = false
                        statusText = result.fold(
                            onSuccess = { "Backup uploaded: $it" },
                            onFailure = { "Backup failed: ${it.message}" }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBackingUp
            ) {
                Text(if (isBackingUp) "Backing up..." else "Backup to Drive")
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
                Text("Sign In")
            }
        }

        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
