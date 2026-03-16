package com.wyldsoft.notes.actions

import android.util.Log
import com.wyldsoft.notes.data.database.dao.ActionHistoryDao
import com.wyldsoft.notes.data.database.entities.ActionHistoryEntity
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager

class ActionHistoryRepository(private val dao: ActionHistoryDao) {

    companion object {
        private const val TAG = "ActionHistoryRepo"
    }

    suspend fun saveActions(noteId: String, actionManager: ActionManager) {
        val entities = mutableListOf<ActionHistoryEntity>()

        actionManager.getUndoActions().forEachIndexed { index, action ->
            val json = ActionSerializer.serialize(action)
            if (json != null) {
                entities.add(ActionHistoryEntity(
                    noteId = noteId,
                    onUndoStack = true,
                    position = index,
                    dataJson = json
                ))
            }
        }

        actionManager.getRedoActions().forEachIndexed { index, action ->
            val json = ActionSerializer.serialize(action)
            if (json != null) {
                entities.add(ActionHistoryEntity(
                    noteId = noteId,
                    onUndoStack = false,
                    position = index,
                    dataJson = json
                ))
            }
        }

        dao.deleteAllForNote(noteId)
        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
        Log.d(TAG, "Saved ${entities.size} actions for note $noteId")
    }

    suspend fun loadActions(
        noteId: String,
        noteRepository: NoteRepository,
        shapesManager: ShapesManager,
        bitmapManager: BitmapManager
    ): Pair<List<ActionInterface>, List<ActionInterface>> {
        val entities = dao.getActionsForNote(noteId)
        val undoActions = mutableListOf<ActionInterface>()
        val redoActions = mutableListOf<ActionInterface>()

        for (entity in entities) {
            val action = ActionSerializer.deserialize(
                entity.dataJson, noteRepository, shapesManager, bitmapManager
            )
            if (action != null) {
                if (entity.onUndoStack) undoActions.add(action)
                else redoActions.add(action)
            } else {
                Log.w(TAG, "Failed to deserialize action id=${entity.id}")
            }
        }

        // Entities are ordered by position ASC (from DAO query), which is stack bottom-to-top
        Log.d(TAG, "Loaded ${undoActions.size} undo + ${redoActions.size} redo actions for note $noteId")
        return Pair(undoActions, redoActions)
    }
}
