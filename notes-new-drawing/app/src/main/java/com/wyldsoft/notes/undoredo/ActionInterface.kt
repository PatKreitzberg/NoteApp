package com.wyldsoft.notes.undoredo

/**
 * Contract for all undoable/redoable actions.
 * Implement this interface to create a new action type.
 */
interface ActionInterface {
    suspend fun undo()
    suspend fun redo()
}
