package com.wyldsoft.notes.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Syncing : SyncUiState()
    data class Success(val lastSyncedAt: Long) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

class SyncViewModel(private val syncRepository: SyncRepository) : ViewModel() {

    private val _syncUiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncUiState: StateFlow<SyncUiState> = _syncUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSync = syncRepository.getLastSyncTimestamp()
            if (lastSync > 0L) {
                _syncUiState.value = SyncUiState.Success(lastSync)
            }
        }
    }

    fun triggerSync() {
        if (_syncUiState.value is SyncUiState.Syncing) return
        viewModelScope.launch {
            _syncUiState.value = SyncUiState.Syncing
            _syncUiState.value = when (val result = syncRepository.performSync()) {
                is SyncResult.Success -> SyncUiState.Success(System.currentTimeMillis())
                is SyncResult.PartialSuccess -> SyncUiState.Success(System.currentTimeMillis())
                is SyncResult.NotSignedIn -> SyncUiState.Error("Not signed in to Google Drive")
                is SyncResult.AlreadyRunning -> SyncUiState.Error("Sync already in progress")
                is SyncResult.Failure -> SyncUiState.Error(result.error.message ?: "Sync failed")
            }
        }
    }
}
