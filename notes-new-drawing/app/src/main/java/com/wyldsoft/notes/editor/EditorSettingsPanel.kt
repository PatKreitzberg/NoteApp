package com.wyldsoft.notes.editor

import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.wyldsoft.notes.ScrotesApp
import androidx.compose.ui.platform.LocalContext
import com.wyldsoft.notes.data.database.repository.NoteRepository
import com.wyldsoft.notes.data.database.repository.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "EditorSettingsPanel"

@Composable
fun EditorSettingsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val paginationEnabled by EditorState.paginationEnabled.collectAsState()

    var showRenameNote by remember { mutableStateOf(false) }
    var showRenameNotebook by remember { mutableStateOf(false) }
    var renameNoteText by remember { mutableStateOf("") }
    var renameNotebookText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
                .padding(16.dp)
                .width(240.dp)
        ) {
            Text("Editor Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // Pagination toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pagination",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = paginationEnabled,
                    onCheckedChange = {
                        Log.d(TAG, "pagination toggled to $it")
                        EditorState.togglePagination()
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Rename note
            if (showRenameNote) {
                OutlinedTextField(
                    value = renameNoteText,
                    onValueChange = { renameNoteText = it },
                    label = { Text("Note title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = {
                        val title = renameNoteText.trim()
                        if (title.isNotEmpty()) {
                            val noteId = EditorState.currentNoteId
                            if (noteId != null) {
                                val db = (context.applicationContext as ScrotesApp).database
                                CoroutineScope(Dispatchers.IO).launch {
                                    NoteRepository(db.noteDao()).renameNote(noteId, title)
                                }
                            }
                        }
                        showRenameNote = false
                        EditorState.emitDismissSettings()
                    }) { Text("Save") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showRenameNote = false }) { Text("Cancel") }
                }
            } else {
                OutlinedButton(
                    onClick = { showRenameNote = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rename Note")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rename notebook
            if (showRenameNotebook) {
                OutlinedTextField(
                    value = renameNotebookText,
                    onValueChange = { renameNotebookText = it },
                    label = { Text("Notebook name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = {
                        val name = renameNotebookText.trim()
                        if (name.isNotEmpty()) {
                            val notebookId = EditorState.currentNotebookId
                            if (notebookId != null) {
                                val db = (context.applicationContext as ScrotesApp).database
                                CoroutineScope(Dispatchers.IO).launch {
                                    NotebookRepository(db.notebookDao(), db.noteDao())
                                        .renameNotebook(notebookId, name)
                                }
                            }
                        }
                        showRenameNotebook = false
                        EditorState.emitDismissSettings()
                    }) { Text("Save") }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showRenameNotebook = false }) { Text("Cancel") }
                }
            } else {
                OutlinedButton(
                    onClick = { showRenameNotebook = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rename Notebook")
                }
            }
        }
    }
}
