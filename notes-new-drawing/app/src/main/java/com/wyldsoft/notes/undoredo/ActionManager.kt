package com.wyldsoft.notes.undoredo

import android.util.Log
import com.wyldsoft.notes.editor.EditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages undo and redo stacks. Actions are recorded via [recordAction].
 * After each undo or redo, [onComplete] is called on the main thread so the
 * caller can trigger a screen refresh.
 */
class ActionManager {
    private val TAG = "ActionManager"

    private val undoStack = ArrayDeque<ActionInterface>()
    private val redoStack = ArrayDeque<ActionInterface>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun recordAction(action: ActionInterface) {
        Log.d(TAG, "recordAction ${action::class.simpleName}")
        undoStack.addLast(action)
        redoStack.clear()
        updateState()
    }

    fun undo(scope: CoroutineScope, onComplete: () -> Unit) {
        Log.d(TAG, "undo stackSize=${undoStack.size}")
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        scope.launch(Dispatchers.IO) {
            action.undo()
            redoStack.addLast(action)
            updateState()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun redo(scope: CoroutineScope, onComplete: () -> Unit) {
        Log.d(TAG, "redo stackSize=${redoStack.size}")
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        scope.launch(Dispatchers.IO) {
            action.redo()
            undoStack.addLast(action)
            updateState()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clear() {
        Log.d(TAG, "clear")
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        EditorState.setUndoRedoState(_canUndo.value, _canRedo.value)
    }
}
