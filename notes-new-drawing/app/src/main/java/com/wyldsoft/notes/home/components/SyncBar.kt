package com.wyldsoft.notes.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.sync.SyncUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncBar(
    isSignedIn: Boolean,
    syncUiState: SyncUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSignedIn) {
            Text(
                text = "Google Drive: not signed in",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            TextButton(onClick = onSignInClick) {
                Text("Sign In")
            }
        } else {
            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (syncUiState) {
                    is SyncUiState.Syncing -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.caption
                        )
                    }
                    is SyncUiState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colors.primary
                        )
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(Date(syncUiState.lastSyncedAt))
                        Text(
                            text = "Synced $timeStr",
                            style = MaterialTheme.typography.caption
                        )
                    }
                    is SyncUiState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colors.error
                        )
                        Text(
                            text = syncUiState.message,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                    is SyncUiState.Idle -> {
                        Text(
                            text = "Drive",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onSyncNowClick,
                    enabled = syncUiState !is SyncUiState.Syncing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync now",
                        modifier = Modifier.size(18.dp)
                    )
                }
                TextButton(onClick = onSignOutClick) {
                    Text("Sign Out", style = MaterialTheme.typography.caption)
                }
            }
        }
    }
}
