package com.wyldsoft.notes.editor

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.data.database.entities.LayerEntity
import com.wyldsoft.notes.geometry.GeometryShapeType
import com.wyldsoft.notes.models.PaperTemplate
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.text.TextProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchHit(
    val text: String,
    val boundingBox: RectF   // note-space coordinates
)

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

        // PDF metadata for the currently open note
        private val _pdfPath = MutableStateFlow<String?>(null)
        val pdfPath: StateFlow<String?> = _pdfPath.asStateFlow()

        private val _pdfPageCount = MutableStateFlow(0)
        val pdfPageCount: StateFlow<Int> = _pdfPageCount.asStateFlow()

        private val _pdfPageAspectRatio = MutableStateFlow(0f)
        val pdfPageAspectRatio: StateFlow<Float> = _pdfPageAspectRatio.asStateFlow()

        private val _exportPdfRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val exportPdfRequested = _exportPdfRequested.asSharedFlow()

        fun requestExportPdf() {
            Log.d(TAG, "requestExportPdf")
            _exportPdfRequested.tryEmit(Unit)
        }

        private val _canUndo = MutableStateFlow(false)
        val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
        private val _canRedo = MutableStateFlow(false)
        val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

        private val _undoRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val undoRequested = _undoRequested.asSharedFlow()
        private val _redoRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val redoRequested = _redoRequested.asSharedFlow()

        private val _hasSelection = MutableStateFlow(false)
        val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()

        private val _hasCopied = MutableStateFlow(false)
        val hasCopied: StateFlow<Boolean> = _hasCopied.asStateFlow()

        private val _copyRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val copyRequested = _copyRequested.asSharedFlow()

        private val _pasteRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val pasteRequested = _pasteRequested.asSharedFlow()

        private val _currentPenProfile = MutableStateFlow(PenProfile.getDefaultProfile(PenType.BALLPEN))
        val currentPenProfile: StateFlow<PenProfile> = _currentPenProfile.asStateFlow()

        private val _penProfiles = listOf(
            MutableStateFlow(PenProfile.getDefaultProfile(PenType.BALLPEN)),
            MutableStateFlow(PenProfile.getDefaultProfile(PenType.MARKER)),
            MutableStateFlow(PenProfile.getDefaultProfile(PenType.PENCIL)),
            MutableStateFlow(PenProfile.getDefaultProfile(PenType.FOUNTAIN)),
            MutableStateFlow(PenProfile.getDefaultProfile(PenType.CHARCOAL)),
        )
        val penProfiles: List<StateFlow<PenProfile>> = _penProfiles.map { it.asStateFlow() }

        private val _activePenSlot = MutableStateFlow(1)
        val activePenSlot: StateFlow<Int> = _activePenSlot.asStateFlow()

        private fun penProfileForSlot(slot: Int) =
            _penProfiles.getOrElse(slot - 1) { _penProfiles[0] }.value

        fun switchToPenSlot(slot: Int) {
            Log.d(TAG, "switchToPenSlot: $slot")
            _activePenSlot.value = slot
            _currentPenProfile.value = penProfileForSlot(slot)
        }

        private val _activeGeometryShape = MutableStateFlow(GeometryShapeType.CIRCLE)
        val activeGeometryShape: StateFlow<GeometryShapeType> = _activeGeometryShape.asStateFlow()

        fun setActiveGeometryShape(type: GeometryShapeType) {
            Log.d(TAG, "setActiveGeometryShape: $type")
            _activeGeometryShape.value = type
        }

        private val _textProfile = MutableStateFlow(TextProfile())
        val textProfile: StateFlow<TextProfile> = _textProfile.asStateFlow()

        fun setTextProfile(profile: TextProfile) {
            Log.d(TAG, "setTextProfile fontSize=${profile.fontSize} font=${profile.fontFamily}")
            _textProfile.value = profile
        }

        // Effective pagination (observed by activity to drive PaginationManager)
        private val _paginationEnabled = MutableStateFlow(false)
        val paginationEnabled: StateFlow<Boolean> = _paginationEnabled.asStateFlow()

        // Effective draw-outside-bounds (pagination must also be enabled to matter)
        private val _drawOutsideBounds = MutableStateFlow(false)
        val drawOutsideBounds: StateFlow<Boolean> = _drawOutsideBounds.asStateFlow()

        // Notebook-level defaults
        private val _notebookPaginationEnabled = MutableStateFlow(false)
        val notebookPaginationEnabled: StateFlow<Boolean> = _notebookPaginationEnabled.asStateFlow()
        private val _notebookTemplate = MutableStateFlow(PaperTemplate.BLANK)
        val notebookTemplate: StateFlow<PaperTemplate> = _notebookTemplate.asStateFlow()
        private val _notebookDrawOutsideBounds = MutableStateFlow(false)
        val notebookDrawOutsideBounds: StateFlow<Boolean> = _notebookDrawOutsideBounds.asStateFlow()

        // Note-level values (used when overrideNotebookSettings is true)
        private val _notePaginationEnabled = MutableStateFlow(false)
        val notePaginationEnabled: StateFlow<Boolean> = _notePaginationEnabled.asStateFlow()
        private val _noteTemplate = MutableStateFlow(PaperTemplate.BLANK)
        val noteTemplate: StateFlow<PaperTemplate> = _noteTemplate.asStateFlow()
        private val _noteDrawOutsideBounds = MutableStateFlow(false)
        val noteDrawOutsideBounds: StateFlow<Boolean> = _noteDrawOutsideBounds.asStateFlow()

        // Override flag: if true, note uses its own template/pagination instead of notebook's
        private val _overrideNotebookSettings = MutableStateFlow(false)
        val overrideNotebookSettings: StateFlow<Boolean> = _overrideNotebookSettings.asStateFlow()

        // Effective template (observed by activity to drive TemplateRenderer)
        private val _currentTemplate = MutableStateFlow(PaperTemplate.BLANK)
        val currentTemplate: StateFlow<PaperTemplate> = _currentTemplate.asStateFlow()

        private fun updateEffectiveSettings() {
            val override = _overrideNotebookSettings.value
            _paginationEnabled.value = if (override) _notePaginationEnabled.value else _notebookPaginationEnabled.value
            _currentTemplate.value = if (override) _noteTemplate.value else _notebookTemplate.value
            _drawOutsideBounds.value = if (override) _noteDrawOutsideBounds.value else _notebookDrawOutsideBounds.value
        }

        fun setNotebookTemplate(template: PaperTemplate) {
            Log.d(TAG, "setNotebookTemplate: $template")
            _notebookTemplate.value = template
            updateEffectiveSettings()
        }

        fun setNotebookPagination(enabled: Boolean) {
            Log.d(TAG, "setNotebookPagination: $enabled")
            _notebookPaginationEnabled.value = enabled
            updateEffectiveSettings()
        }

        fun setNotebookDrawOutsideBounds(enabled: Boolean) {
            Log.d(TAG, "setNotebookDrawOutsideBounds: $enabled")
            _notebookDrawOutsideBounds.value = enabled
            updateEffectiveSettings()
        }

        fun setNoteTemplate(template: PaperTemplate) {
            Log.d(TAG, "setNoteTemplate: $template")
            _noteTemplate.value = template
            updateEffectiveSettings()
        }

        fun setNotePagination(enabled: Boolean) {
            Log.d(TAG, "setNotePagination: $enabled")
            _notePaginationEnabled.value = enabled
            updateEffectiveSettings()
        }

        fun setNoteDrawOutsideBounds(enabled: Boolean) {
            Log.d(TAG, "setNoteDrawOutsideBounds: $enabled")
            _noteDrawOutsideBounds.value = enabled
            updateEffectiveSettings()
        }

        fun setOverrideNotebook(enabled: Boolean) {
            Log.d(TAG, "setOverrideNotebook: $enabled")
            _overrideNotebookSettings.value = enabled
            updateEffectiveSettings()
        }

        /** Load settings from the current note and its parent notebook. Called on note open/switch. */
        fun loadNoteAndNotebookSettings(
            notePagination: Boolean,
            noteTemplate: PaperTemplate,
            overrideNotebook: Boolean,
            notebookPagination: Boolean,
            notebookTemplate: PaperTemplate,
            pdfPath: String? = null,
            pdfPageCount: Int = 0,
            pdfPageAspectRatio: Float = 0f,
            noteDrawOutsideBounds: Boolean = false,
            notebookDrawOutsideBounds: Boolean = false
        ) {
            Log.d(TAG, "loadNoteAndNotebookSettings pdfPath=$pdfPath pdfPageCount=$pdfPageCount")
            // Set PDF data before updateEffectiveSettings so pagination observer can read it
            _pdfPath.value = pdfPath
            _pdfPageCount.value = pdfPageCount
            _pdfPageAspectRatio.value = pdfPageAspectRatio
            _notePaginationEnabled.value = notePagination
            _noteTemplate.value = noteTemplate
            _noteDrawOutsideBounds.value = noteDrawOutsideBounds
            _overrideNotebookSettings.value = overrideNotebook
            _notebookPaginationEnabled.value = notebookPagination
            _notebookTemplate.value = notebookTemplate
            _notebookDrawOutsideBounds.value = notebookDrawOutsideBounds
            updateEffectiveSettings()
        }

        fun togglePagination() {
            _paginationEnabled.value = !_paginationEnabled.value
        }

        var currentNoteId: String? = null
        var currentNotebookId: String? = null

        // Note navigation within a notebook
        private val _notesInNotebook = MutableStateFlow<List<String>>(emptyList())
        val notesInNotebook: StateFlow<List<String>> = _notesInNotebook.asStateFlow()

        private val _currentNoteIndex = MutableStateFlow(0)
        val currentNoteIndex: StateFlow<Int> = _currentNoteIndex.asStateFlow()

        private val _navigateToNote = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val navigateToNote = _navigateToNote.asSharedFlow()

        private val _createNewNote = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val createNewNote = _createNewNote.asSharedFlow()

        fun setNotesInNotebook(noteIds: List<String>, currentNoteId: String) {
            Log.d(TAG, "setNotesInNotebook count=${noteIds.size} currentNoteId=$currentNoteId")
            _notesInNotebook.value = noteIds
            val idx = noteIds.indexOf(currentNoteId)
            _currentNoteIndex.value = if (idx >= 0) idx else 0
        }

        fun updateCurrentNoteIndex(noteId: String) {
            Log.d(TAG, "updateCurrentNoteIndex noteId=$noteId")
            val idx = _notesInNotebook.value.indexOf(noteId)
            if (idx >= 0) _currentNoteIndex.value = idx
        }

        fun requestNavigatePrev() {
            Log.d(TAG, "requestNavigatePrev")
            val idx = _currentNoteIndex.value
            val notes = _notesInNotebook.value
            if (idx > 0) _navigateToNote.tryEmit(notes[idx - 1])
        }

        fun requestNavigateNext() {
            Log.d(TAG, "requestNavigateNext")
            val idx = _currentNoteIndex.value
            val notes = _notesInNotebook.value
            if (idx < notes.size - 1) {
                _navigateToNote.tryEmit(notes[idx + 1])
            } else {
                _createNewNote.tryEmit(Unit)
            }
        }

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

        fun setHasSelection(has: Boolean) {
            Log.d(TAG, "setHasSelection: $has")
            _hasSelection.value = has
        }

        fun setHasCopied(has: Boolean) {
            Log.d(TAG, "setHasCopied: $has")
            _hasCopied.value = has
        }

        fun requestCopy() {
            Log.d(TAG, "requestCopy")
            _copyRequested.tryEmit(Unit)
        }

        fun requestPaste() {
            Log.d(TAG, "requestPaste")
            _pasteRequested.tryEmit(Unit)
        }

        fun emitDismissSettings() {
            Log.d(TAG, "emitDismissSettings")
            _dismissSettings.tryEmit(Unit)
        }

        fun setPenProfile(profile: PenProfile) {
            Log.d(TAG, "setPenProfile: ${profile.penType.displayName}, width=${profile.strokeWidth}")
            _currentPenProfile.value = profile
            _penProfiles.getOrNull(_activePenSlot.value - 1)?.value = profile
        }

        fun initializePenProfiles(profiles: List<PenProfile>, activePenSlot: Int) {
            Log.d(TAG, "initializePenProfiles activeSlot=$activePenSlot count=${profiles.size}")
            profiles.forEachIndexed { i, profile ->
                _penProfiles.getOrNull(i)?.value = profile
            }
            _activePenSlot.value = activePenSlot.coerceIn(1, _penProfiles.size)
            _currentPenProfile.value = penProfileForSlot(_activePenSlot.value)
        }

        fun addExclusionRect(rect: Rect) {
            exclusionRects.add(rect)
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return exclusionRects
        }

        // ── Layer state ────────────────────────────────────────────────────────
        private val _layers = MutableStateFlow<List<LayerEntity>>(emptyList())
        val layers: StateFlow<List<LayerEntity>> = _layers.asStateFlow()

        private val _activeLayer = MutableStateFlow(1)
        val activeLayer: StateFlow<Int> = _activeLayer.asStateFlow()

        private var _lastRealActiveLayer = 1  // not a flow, just internal tracking

        fun setLayers(layers: List<LayerEntity>) {
            Log.d(TAG, "setLayers count=${layers.size}")
            _layers.value = layers
        }

        fun setActiveLayer(position: Int) {
            Log.d(TAG, "setActiveLayer position=$position")
            if (position != -1) _lastRealActiveLayer = position
            _activeLayer.value = position
        }

        fun getLastRealActiveLayer(): Int = _lastRealActiveLayer

        fun getActiveLayerForDrawing(): Int =
            if (_activeLayer.value == -1) _lastRealActiveLayer else _activeLayer.value

        // Layer operation requests (handled by OnyxDrawingActivity)
        private val _addLayerRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val addLayerRequested = _addLayerRequested.asSharedFlow()

        private val _deleteLayerRequested = MutableSharedFlow<LayerEntity>(extraBufferCapacity = 1)
        val deleteLayerRequested = _deleteLayerRequested.asSharedFlow()

        private val _renameLayerRequested = MutableSharedFlow<Pair<LayerEntity, String>>(extraBufferCapacity = 1)
        val renameLayerRequested = _renameLayerRequested.asSharedFlow()

        private val _toggleLayerVisibilityRequested = MutableSharedFlow<LayerEntity>(extraBufferCapacity = 1)
        val toggleLayerVisibilityRequested = _toggleLayerVisibilityRequested.asSharedFlow()

        fun requestAddLayer() {
            Log.d(TAG, "requestAddLayer")
            _addLayerRequested.tryEmit(Unit)
        }

        fun requestDeleteLayer(layer: LayerEntity) {
            Log.d(TAG, "requestDeleteLayer layerId=${layer.id}")
            _deleteLayerRequested.tryEmit(layer)
        }

        fun requestRenameLayer(layer: LayerEntity, newName: String) {
            Log.d(TAG, "requestRenameLayer layerId=${layer.id} newName=$newName")
            _renameLayerRequested.tryEmit(Pair(layer, newName))
        }

        fun requestToggleLayerVisibility(layer: LayerEntity) {
            Log.d(TAG, "requestToggleLayerVisibility layerId=${layer.id}")
            _toggleLayerVisibilityRequested.tryEmit(layer)
        }

        // ── In-note search state ───────────────────────────────────────────────
        private val _isSearchActive = MutableStateFlow(false)
        val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

        private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
        val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()

        private val _searchIndex = MutableStateFlow(0)
        val searchIndex: StateFlow<Int> = _searchIndex.asStateFlow()

        // Emitted when search navigation requests a viewport scroll + highlight
        private val _navigateToSearchHit = MutableSharedFlow<SearchHit>(extraBufferCapacity = 1)
        val navigateToSearchHit = _navigateToSearchHit.asSharedFlow()

        fun activateSearch() {
            Log.d(TAG, "activateSearch")
            _isSearchActive.value = true
            _searchHits.value = emptyList()
            _searchIndex.value = 0
        }

        fun deactivateSearch() {
            Log.d(TAG, "deactivateSearch")
            _isSearchActive.value = false
            _searchHits.value = emptyList()
            _searchIndex.value = 0
        }

        fun setSearchHits(hits: List<SearchHit>) {
            Log.d(TAG, "setSearchHits count=${hits.size}")
            _searchHits.value = hits
            _searchIndex.value = 0
            if (hits.isNotEmpty()) _navigateToSearchHit.tryEmit(hits[0])
        }

        fun navigateSearchNext() {
            Log.d(TAG, "navigateSearchNext")
            val hits = _searchHits.value
            if (hits.isEmpty()) return
            val next = (_searchIndex.value + 1) % hits.size
            _searchIndex.value = next
            _navigateToSearchHit.tryEmit(hits[next])
        }

        fun navigateSearchPrev() {
            Log.d(TAG, "navigateSearchPrev")
            val hits = _searchHits.value
            if (hits.isEmpty()) return
            val prev = (_searchIndex.value - 1 + hits.size) % hits.size
            _searchIndex.value = prev
            _navigateToSearchHit.tryEmit(hits[prev])
        }

        // Note-space bounding rect of the current search hit, drawn as a highlight overlay.
        // Set by BaseDrawingActivity after navigating; cleared after 2 seconds.
        private val _searchHighlightNoteRect = MutableStateFlow<RectF?>(null)
        val searchHighlightNoteRect: StateFlow<RectF?> = _searchHighlightNoteRect.asStateFlow()

        fun setSearchHighlight(rect: RectF?) {
            _searchHighlightNoteRect.value = rect
        }
    }
}