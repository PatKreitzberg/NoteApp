package com.wyldsoft.notes.htr

import android.util.Log
import com.wyldsoft.notes.data.repository.RecognizedSegmentRepository
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.*

class HTRSegmentManager(
    private val htrManager: HTRManager,
    private val recognizedSegmentRepository: RecognizedSegmentRepository
) {
    companion object {
        private const val TAG = "HTRSegmentManager"
        private const val DEBOUNCE_MS = 2000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Pending shapes per note, keyed by noteId
    private val pendingShapes = mutableMapOf<String, MutableList<Shape>>()
    private var debounceJob: Job? = null
    private var currentNoteId: String? = null

    fun addShapesForRecognition(noteId: String, shapes: List<Shape>) {
        synchronized(pendingShapes) {
            currentNoteId = noteId
            val list = pendingShapes.getOrPut(noteId) { mutableListOf() }
            list.addAll(shapes)
        }

        // Reset debounce timer
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            basicRecognition()
        }
    }

    suspend fun basicRecognition() {
        if (!htrManager.isReady()) {
            Log.w(TAG, "HTR model not ready, skipping recognition")
            return
        }
        Log.d(TAG, "Starting basic recognition for all pending shapes")
        htrManager.basicRecognize(pendingShapes)
    }

    fun onShapesDeleted(noteId: String, deletedShapeIds: Set<String>) {
        synchronized(pendingShapes) {
            pendingShapes[noteId]?.removeAll { it.id in deletedShapeIds }
        }

        // Also clean up recognized segments that reference deleted shapes
        scope.launch {
            val existing = recognizedSegmentRepository.getSegmentsForNoteOnce(noteId)
            val toDelete = existing.filter { segment ->
                segment.shapeIds.any { it in deletedShapeIds }
            }
            if (toDelete.isNotEmpty()) {
                recognizedSegmentRepository.deleteByIds(toDelete.map { it.id })
                Log.d(TAG, "Deleted ${toDelete.size} recognized segments referencing deleted shapes")
            }
        }
    }

    fun close() {
        debounceJob?.cancel()
        scope.cancel()
        htrManager.close()
    }
}


