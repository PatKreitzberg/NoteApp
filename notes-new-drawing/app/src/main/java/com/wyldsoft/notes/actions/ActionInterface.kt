package com.wyldsoft.notes.actions

interface ActionInterface {
    suspend fun undo()
    suspend fun redo()
}
