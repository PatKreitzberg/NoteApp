package com.wyldsoft.notes.htr

import android.graphics.RectF
import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.wyldsoft.notes.data.database.entities.HtrResultEntity
import com.wyldsoft.notes.data.database.repository.HtrResultRepository
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.*
import org.json.JSONArray

class HTRRunManager(
    private val htrManager: HTRManager = HTRManager(),
    private val gestureRecognitionManager: GestureRecognitionManager = GestureRecognitionManager(),
    private val htrResultRepository: HtrResultRepository? = null
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
            Log.d(TAG, "noteId=${result.noteId} text='${result.text}' confidence=${result.confidence}")
            persistResult(result, snapshot[result.noteId] ?: emptyList())
        }
    }

    private suspend fun persistResult(result: RecognitionResult, shapes: List<Shape>) {
        Log.d(TAG, "persistResult noteId=${result.noteId} text='${result.text}'")
        val repo = htrResultRepository ?: return

        val boundingBox = computeBoundingBox(shapes)
        val shapeIdsJson = JSONArray(result.shapeIds).toString()

        val entity = HtrResultEntity(
            id = NanoIdUtils.randomNanoId(),
            noteId = result.noteId,
            text = result.text,
            confidence = result.confidence,
            shapeIds = shapeIdsJson,
            boundingLeft = boundingBox.left,
            boundingTop = boundingBox.top,
            boundingRight = boundingBox.right,
            boundingBottom = boundingBox.bottom,
            timestamp = System.currentTimeMillis()
        )
        repo.upsert(entity)
    }

    private fun computeBoundingBox(shapes: List<Shape>): RectF {
        val rect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
        for (shape in shapes) {
            val points = shape.touchPointList?.points ?: continue
            for (point in points) {
                if (point == null) continue
                if (point.x < rect.left) rect.left = point.x
                if (point.y < rect.top) rect.top = point.y
                if (point.x > rect.right) rect.right = point.x
                if (point.y > rect.bottom) rect.bottom = point.y
            }
        }
        if (rect.left == Float.MAX_VALUE) return RectF(0f, 0f, 0f, 0f)
        return rect
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

    suspend fun recognizeSelectedShapes(noteId: String, shapes: List<Shape>): String? {
        Log.d(TAG, "recognizeSelectedShapes noteId=$noteId shapes=${shapes.size}")
        if (!htrManager.isReady()) return null
        val map = mutableMapOf(noteId to shapes.toMutableList())
        val results = htrManager.recognizeShapes(map)
        return results.firstOrNull()?.text
    }

    fun close() {
        Log.d(TAG, "close")
        debounceJob?.cancel()
        scope.cancel()
        gestureRecognitionManager.close()
        htrManager.close()
    }
}
