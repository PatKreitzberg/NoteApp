package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.ScrotesApp
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import kotlinx.coroutines.launch

private const val TAG = "PenSetTab"

/**
 * Small tab shown to the left of the five pen slot buttons.
 * Displays the active set name (with a "•" dirty indicator) and opens
 * a dropdown on tap to switch sets. Set management (rename/delete/create)
 * is handled in HomeView.
 */
@Composable
fun PenSetTab() {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ScrotesApp).penProfileSetRepository }

    val sets by EditorState.penProfileSets.collectAsState()
    val activeSetId by EditorState.activeSetId.collectAsState()
    val activeSetName by EditorState.activeSetName.collectAsState()
    val isSetDirty by EditorState.isSetDirty.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val displayName = when {
        activeSetName.isNotBlank() && isSetDirty -> "${activeSetName.take(7)} •"
        activeSetName.isNotBlank() -> activeSetName.take(8)
        else -> "Sets"
    }

    Box(
        modifier = Modifier
            .width(46.dp)
            .wrapContentSize(Alignment.TopStart)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .border(1.dp, Color.DarkGray)
                .clickable {
                    Log.d(TAG, "PenSetTab clicked, sets=${sets.size}")
                    EditorState.setMode(AppMode.SETTINGS)
                    expanded = true
                }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = displayName,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                EditorState.setMode(AppMode.DRAWING)
            }
        ) {
            if (sets.isEmpty()) {
                DropdownMenuItem(onClick = {}) {
                    Text("No sets", fontSize = 13.sp, color = Color.Gray)
                }
            }
            sets.forEach { set ->
                PenSetDropdownItem(
                    set = set,
                    isActive = set.id == activeSetId && !isSetDirty,
                    onClick = {
                        expanded = false
                        scope.launch { repository.loadSet(set.id) }
                        EditorState.setMode(AppMode.DRAWING)
                    }
                )
            }
        }
    }
}

@Composable
private fun PenSetDropdownItem(
    set: PenProfileSetEntity,
    isActive: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(onClick = onClick) {
        Text(
            text = if (isActive) "✓ ${set.name}" else "   ${set.name}",
            fontSize = 13.sp
        )
    }
}
