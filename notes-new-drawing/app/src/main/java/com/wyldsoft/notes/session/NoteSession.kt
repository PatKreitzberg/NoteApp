package com.wyldsoft.notes.session

import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.ShapesManager

/**
 * Encapsulates per-note state. Switching notes becomes:
 * create session → activate it → done.
 */
class NoteSession(
    val noteId: String,
    val shapesManager: ShapesManager,
    val actionManager: ActionManager
) {
    companion object {
        fun create(editorViewModel: EditorViewModel): NoteSession {
            val noteId = editorViewModel.currentNote.value.id
            val shapesManager = ShapesManager.fromViewModel(editorViewModel)
            val actionManager = ActionManager()
            return NoteSession(noteId, shapesManager, actionManager)
        }

        fun createFromNote(note: Note): NoteSession {
            val shapesManager = ShapesManager.fromNote(note)
            val actionManager = ActionManager()
            return NoteSession(note.id, shapesManager, actionManager)
        }
    }
}
