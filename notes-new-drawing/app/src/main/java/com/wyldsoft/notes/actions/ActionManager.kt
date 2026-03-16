package com.wyldsoft.notes.actions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Stack

class ActionManager {
    private val undoStack = Stack<ActionInterface>()
    private val redoStack = Stack<ActionInterface>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    var onChanged: (() -> Unit)? = null

    fun recordAction(action: ActionInterface) {
        undoStack.push(action)
        redoStack.clear()
        updateState()
        onChanged?.invoke()
    }

    suspend fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.pop()
        action.undo()
        redoStack.push(action)
        updateState()
        onChanged?.invoke()
    }

    suspend fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.pop()
        action.redo()
        undoStack.push(action)
        updateState()
        onChanged?.invoke()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    fun getUndoActions(): List<ActionInterface> = undoStack.toList()
    fun getRedoActions(): List<ActionInterface> = redoStack.toList()

    fun loadActions(undoActions: List<ActionInterface>, redoActions: List<ActionInterface>) {
        undoStack.clear()
        redoStack.clear()
        undoActions.forEach { undoStack.push(it) }
        redoActions.forEach { redoStack.push(it) }
        updateState()
    }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
