package com.wyldsoft.notes.htr

import android.util.Log
import com.wyldsoft.notes.data.database.entities.RecognizedSegmentEntity
import com.wyldsoft.notes.data.repository.RecognizedSegmentRepository
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.*
import java.util.UUID

class HTRRunManager(
    private val htrManager: HTRManager,
    private val recognizedSegmentRepository: RecognizedSegmentRepository
) {
    companion object {
        private const val TAG = "HTRRunManager"
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
        val results = htrManager.basicRecognize(pendingShapes)

        // Save results to database
        val entities = results.map { result ->
            RecognizedSegmentEntity(
                id = UUID.randomUUID().toString(),
                noteId = result.noteId,
                shapeIds = result.shapeIds,
                recognizedText = result.text,
                confidence = result.confidence,
                lineNumber = 0,
                boundingBoxLeft = result.boundingBoxLeft,
                boundingBoxTop = result.boundingBoxTop,
                boundingBoxRight = result.boundingBoxRight,
                boundingBoxBottom = result.boundingBoxBottom
            )
        }
        if (entities.isNotEmpty()) {
            recognizedSegmentRepository.saveSegments(entities)
            Log.d(TAG, "Saved ${entities.size} recognized segments to database")
        }

        // Clear processed shapes
        synchronized(pendingShapes) {
            for (result in results) {
                pendingShapes.remove(result.noteId)
            }
        }
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


