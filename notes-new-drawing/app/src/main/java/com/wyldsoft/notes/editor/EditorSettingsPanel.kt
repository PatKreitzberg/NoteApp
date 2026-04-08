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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.wyldsoft.notes.models.PaperTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "EditorSettingsPanel"

@Composable
fun EditorSettingsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val notebookTemplate by EditorState.notebookTemplate.collectAsState()
    val notebookPagination by EditorState.notebookPaginationEnabled.collectAsState()
    val overrideNotebook by EditorState.overrideNotebookSettings.collectAsState()
    val noteTemplate by EditorState.noteTemplate.collectAsState()
    val notePagination by EditorState.notePaginationEnabled.collectAsState()

    var showRenameNote by remember { mutableStateOf(false) }
    var showRenameNotebook by remember { mutableStateOf(false) }
    var renameNoteText by remember { mutableStateOf("") }
    var renameNotebookText by remember { mutableStateOf("") }
    var notebookTemplateMenuExpanded by remember { mutableStateOf(false) }
    var noteTemplateMenuExpanded by remember { mutableStateOf(false) }

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
                .width(260.dp)
        ) {
            Text("Editor Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))

            // ── Notebook Defaults ──────────────────────────────────────────────
            Text("Notebook Defaults", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))

            // Notebook template dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Template", fontSize = 12.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { notebookTemplateMenuExpanded = true }) {
                    Text(notebookTemplate.displayName(), fontSize = 11.sp)
                }
                DropdownMenu(
                    expanded = notebookTemplateMenuExpanded,
                    onDismissRequest = { notebookTemplateMenuExpanded = false }
                ) {
                    PaperTemplate.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.displayName()) },
                            onClick = {
                                notebookTemplateMenuExpanded = false
                                val notebookId = EditorState.currentNotebookId ?: return@DropdownMenuItem
                                EditorState.setNotebookTemplate(t)
                                val db = (context.applicationContext as ScrotesApp).database
                                CoroutineScope(Dispatchers.IO).launch {
                                    NotebookRepository(db.notebookDao(), db.noteDao())
                                        .updateTemplate(notebookId, t.name)
                                }
                                Log.d(TAG, "Notebook template set to $t")
                            }
                        )
                    }
                }
            }

            // Notebook pagination toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pagination", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = notebookPagination,
                    onCheckedChange = { enabled ->
                        Log.d(TAG, "Notebook pagination toggled to $enabled")
                        val notebookId = EditorState.currentNotebookId ?: return@Switch
                        EditorState.setNotebookPagination(enabled)
                        val db = (context.applicationContext as ScrotesApp).database
                        CoroutineScope(Dispatchers.IO).launch {
                            NotebookRepository(db.notebookDao(), db.noteDao())
                                .updatePagination(notebookId, enabled)
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Note Settings ──────────────────────────────────────────────────
            Text("Note Settings", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))

            // Override notebook settings checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = overrideNotebook,
                    onCheckedChange = { enabled ->
                        Log.d(TAG, "Override notebook settings: $enabled")
                        val noteId = EditorState.currentNoteId ?: return@Checkbox
                        EditorState.setOverrideNotebook(enabled)
                        val db = (context.applicationContext as ScrotesApp).database
                        CoroutineScope(Dispatchers.IO).launch {
                            NoteRepository(db.noteDao()).updateOverrideNotebook(noteId, enabled)
                        }
                    }
                )
                Text("Override notebook settings", fontSize = 12.sp)
            }

            // Note-specific template and pagination — only shown when override is on
            if (overrideNotebook) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Template", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { noteTemplateMenuExpanded = true }) {
                        Text(noteTemplate.displayName(), fontSize = 11.sp)
                    }
                    DropdownMenu(
                        expanded = noteTemplateMenuExpanded,
                        onDismissRequest = { noteTemplateMenuExpanded = false }
                    ) {
                        PaperTemplate.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName()) },
                                onClick = {
                                    noteTemplateMenuExpanded = false
                                    val noteId = EditorState.currentNoteId ?: return@DropdownMenuItem
                                    EditorState.setNoteTemplate(t)
                                    val db = (context.applicationContext as ScrotesApp).database
                                    CoroutineScope(Dispatchers.IO).launch {
                                        NoteRepository(db.noteDao()).updateTemplate(noteId, t.name)
                                    }
                                    Log.d(TAG, "Note template set to $t")
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pagination", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = notePagination,
                        onCheckedChange = { enabled ->
                            Log.d(TAG, "Note pagination toggled to $enabled")
                            val noteId = EditorState.currentNoteId ?: return@Switch
                            EditorState.setNotePagination(enabled)
                            val db = (context.applicationContext as ScrotesApp).database
                            CoroutineScope(Dispatchers.IO).launch {
                                NoteRepository(db.noteDao()).updatePagination(noteId, enabled)
                            }
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Rename ─────────────────────────────────────────────────────────
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
