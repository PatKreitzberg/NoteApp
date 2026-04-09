package com.wyldsoft.notes.htr

import android.util.Log
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.*

class HTRRunManager(
    private val htrManager: HTRManager = HTRManager(),
    private val gestureRecognitionManager: GestureRecognitionManager = GestureRecognitionManager()
) {
    companion object {
        private const val TAG = "HTRRunManager"
        private const val DEBOUNCE_MS = 2000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Pending shapes per note, keyed by noteId
    private val pendingShapes = mutableMapOf<String, MutableList<Shape>>()
    private var debounceJob: Job? = null

    fun addShapeForRecognition(noteId: String, shape: Shape) {
        Log.d(TAG, "addShapeForRecognition noteId=$noteId")
        synchronized(pendingShapes) {
            val list = pendingShapes.getOrPut(noteId) { mutableListOf() }
            list.add(shape)
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runRecognition()
        }
    }

    private suspend fun runRecognition() {
        Log.d(TAG, "runRecognition")
        if (!htrManager.isReady()) {
            Log.w(TAG, "HTR model not ready, skipping recognition")
            return
        }

        val snapshot: MutableMap<String, MutableList<Shape>>
        synchronized(pendingShapes) {
            snapshot = pendingShapes.toMutableMap()
            pendingShapes.clear()
        }

        val results = htrManager.recognizeShapes(snapshot)

        for (result in results) {
            Log.d("HTR", "noteId=${result.noteId} text='${result.text}' confidence=${result.confidence}")
        }
    }

    /**
     * Immediately check if a single shape is a scribble gesture.
     * Returns true if the top gesture candidate is "SCRIBBLE".
     */
    suspend fun isScribbleGesture(shape: Shape): Boolean {
        Log.d(TAG, "isScribbleGesture")
        if (!gestureRecognitionManager.isReady()) return false
        val gesture = gestureRecognitionManager.recognizeSingleShapeGesture(shape)
        return gesture?.uppercase() == "SCRIBBLE"
    }

    /**
     * Immediately check if a single shape is a circle gesture.
     * Returns true if the top gesture candidate is "CIRCLE".
     */
    suspend fun isCircleGesture(shape: Shape): Boolean {
        Log.d(TAG, "isCircleGesture")
        if (!gestureRecognitionManager.isReady()) return false
        val gesture = gestureRecognitionManager.recognizeSingleShapeGesture(shape)
        return gesture?.uppercase() == "CIRCLE"
    }

    fun close() {
        Log.d(TAG, "close")
        debounceJob?.cancel()
        scope.cancel()
        gestureRecognitionManager.close()
        htrManager.close()
    }
}
