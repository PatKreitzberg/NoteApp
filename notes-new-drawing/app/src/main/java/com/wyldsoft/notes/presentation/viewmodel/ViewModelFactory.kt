package com.wyldsoft.notes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.data.repository.FolderRepository
import com.wyldsoft.notes.data.repository.NotebookRepository

class ViewModelFactory(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val notebookRepository: NotebookRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(EditorViewModel::class.java) -> {
                EditorViewModel(noteRepository, notebookRepository) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(noteRepository, notebookRepository, folderRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}