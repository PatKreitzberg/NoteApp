package com.wyldsoft.notes.htr

import android.util.Log
import com.wyldsoft.notes.data.database.entities.RecognizedSegmentEntity
import com.wyldsoft.notes.data.repository.RecognizedSegmentRepository
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.*
import java.util.UUID

class HTRSegmentManager(
    private val htrManager: HTRManager,
    private val recognizedSegmentRepository: RecognizedSegmentRepository
) {
    companion object {
        private const val TAG = "HTRSegmentManager"
        private const val DEBOUNCE_MS = 1000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lineDetector = LineDetector()

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
            processRecognition(noteId)
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

    private suspend fun processRecognition(noteId: String) {
        if (!htrManager.isReady()) {
            Log.w(TAG, "HTR model not ready, skipping recognition")
            return
        }

        val shapesToProcess: List<Shape>
        synchronized(pendingShapes) {
            shapesToProcess = pendingShapes[noteId]?.toList() ?: emptyList()
            pendingShapes[noteId]?.clear()
        }

        if (shapesToProcess.isEmpty()) return

        Log.d(TAG, "Processing ${shapesToProcess.size} shapes for note $noteId")

        val lines = lineDetector.detectLines(shapesToProcess)
        Log.d(TAG, "Detected ${lines.size} lines")

        val segments = mutableListOf<RecognizedSegmentEntity>()

        for (line in lines) {
            val result = recognizeLine(line) ?: continue

            Log.d(TAG, "Line ${line.lineNumber}: \"${result.text}\" (confidence: ${result.confidence})")

            segments.add(
                RecognizedSegmentEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    shapeIds = line.shapes.map { it.id },
                    recognizedText = result.text,
                    confidence = result.confidence,
                    lineNumber = line.lineNumber,
                    boundingBoxLeft = line.boundingRect.left,
                    boundingBoxTop = line.boundingRect.top,
                    boundingBoxRight = line.boundingRect.right,
                    boundingBoxBottom = line.boundingRect.bottom
                )
            )
        }

        if (segments.isNotEmpty()) {
            recognizedSegmentRepository.saveSegments(segments)
            Log.d(TAG, "Saved ${segments.size} recognized segments")
        }
    }

    private suspend fun recognizeLine(line: Line): RecognitionResult? {
        return suspendCancellableCoroutine { continuation ->
            htrManager.recognizeShapes(line.shapes) { result ->
                continuation.resumeWith(Result.success(result))
            }
        }
    }

    fun close() {
        debounceJob?.cancel()
        scope.cancel()
        htrManager.close()
    }
}
