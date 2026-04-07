package com.wyldsoft.notes.editor

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global event bus for drawing lifecycle and UI state, using SharedFlows.
 *
 * Emits events that coordinate drawing activity and UI:
 * - drawingStarted / drawingEnded: fired by OnyxDrawingActivity's RawInputCallback
 *   so the UI can hide overlays during active drawing.
 * - forceScreenRefresh: triggers a full bitmap re-render to the SurfaceView.
 * - refreshUi: triggers Compose recomposition for UI elements.
 *
 * Holds a reference to BaseDrawingActivity for screen-refresh operations.
 * All emissions use GlobalScope to fire-and-forget from non-coroutine contexts.
 */
class EditorState {
    companion object {
        private const val TAG = "EditorState"
        private val _currentMode = MutableStateFlow(AppMode.DRAWING)
        var previousMode = AppMode.DRAWING
        val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

        private val _dismissSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val dismissSettings = _dismissSettings.asSharedFlow()

        private val _canUndo = MutableStateFlow(false)
        val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
        private val _canRedo = MutableStateFlow(false)
        val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

        private val _undoRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val undoRequested = _undoRequested.asSharedFlow()
        private val _redoRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val redoRequested = _redoRequested.asSharedFlow()

        private val _currentPenProfile = MutableStateFlow(PenProfile.getDefaultProfile(PenType.BALLPEN))
        val currentPenProfile: StateFlow<PenProfile> = _currentPenProfile.asStateFlow()

        private val _penProfile1 = MutableStateFlow(PenProfile.getDefaultProfile(PenType.BALLPEN))
        private val _penProfile2 = MutableStateFlow(PenProfile.getDefaultProfile(PenType.MARKER))
        private val _penProfile3 = MutableStateFlow(PenProfile.getDefaultProfile(PenType.PENCIL))
        private val _penProfile4 = MutableStateFlow(PenProfile.getDefaultProfile(PenType.FOUNTAIN))
        private val _penProfile5 = MutableStateFlow(PenProfile.getDefaultProfile(PenType.CHARCOAL))
        val penProfile1: StateFlow<PenProfile> = _penProfile1.asStateFlow()
        val penProfile2: StateFlow<PenProfile> = _penProfile2.asStateFlow()
        val penProfile3: StateFlow<PenProfile> = _penProfile3.asStateFlow()
        val penProfile4: StateFlow<PenProfile> = _penProfile4.asStateFlow()
        val penProfile5: StateFlow<PenProfile> = _penProfile5.asStateFlow()

        private val _activePenSlot = MutableStateFlow(1)
        val activePenSlot: StateFlow<Int> = _activePenSlot.asStateFlow()

        private fun penProfileForSlot(slot: Int) = when (slot) {
            1 -> _penProfile1.value
            2 -> _penProfile2.value
            3 -> _penProfile3.value
            4 -> _penProfile4.value
            5 -> _penProfile5.value
            else -> _penProfile1.value
        }

        fun switchToPenSlot(slot: Int) {
            Log.d(TAG, "switchToPenSlot: $slot")
            _activePenSlot.value = slot
            _currentPenProfile.value = penProfileForSlot(slot)
        }

        private val _paginationEnabled = MutableStateFlow(false)
        val paginationEnabled: StateFlow<Boolean> = _paginationEnabled.asStateFlow()

        fun togglePagination() {
            _paginationEnabled.value = !_paginationEnabled.value
        }

        var currentNoteId: String? = null
        var currentNotebookId: String? = null

        private var toolbarRect: Rect? = null
        var exclusionRects = mutableListOf<Rect>()

        @SuppressLint("StaticFieldLeak")
        private var mainActivity: BaseDrawingActivity? = null

        fun setMainActivity(activity: BaseDrawingActivity) {
            mainActivity = activity
        }

        fun setMode(mode: AppMode) {
            previousMode = _currentMode.value
            if (previousMode == mode) return
            _currentMode.value = mode
        }

        fun setUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
            _canUndo.value = canUndo
            _canRedo.value = canRedo
        }

        fun requestUndo() {
            Log.d(TAG, "requestUndo")
            _undoRequested.tryEmit(Unit)
        }

        fun requestRedo() {
            Log.d(TAG, "requestRedo")
            _redoRequested.tryEmit(Unit)
        }

        fun emitDismissSettings() {
            Log.d(TAG, "emitDismissSettings")
            _dismissSettings.tryEmit(Unit)
        }

        fun setPenProfile(profile: PenProfile) {
            Log.d(TAG, "setPenProfile: ${profile.penType.displayName}, width=${profile.strokeWidth}")
            _currentPenProfile.value = profile
            when (_activePenSlot.value) {
                1 -> _penProfile1.value = profile
                2 -> _penProfile2.value = profile
                3 -> _penProfile3.value = profile
                4 -> _penProfile4.value = profile
                5 -> _penProfile5.value = profile
            }
        }

        fun addExclusionRect(rect: Rect) {
            exclusionRects.add(rect)
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return exclusionRects
        }
    }
}