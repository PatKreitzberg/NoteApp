package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.tasks.await

data class GestureRecognitionResult(
    val gesture: String,
    val confidence: Float,
    val shapeIds: List<String>
)

class GestureRecognitionManager {
    companion object {
        private const val TAG = "GestureRecognition"
        private const val GESTURE_MODEL_TAG = "en-US-x-gesture"
    }

    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false

    init {
        downloadModel()
    }

    private fun downloadModel() {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(GESTURE_MODEL_TAG)
        if (modelIdentifier == null) {
            Log.e(TAG, "No model found for language tag: $GESTURE_MODEL_TAG")
            return
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    Log.d(TAG, "Gesture model already downloaded")
                    initializeRecognizer(model)
                } else {
                    Log.d(TAG, "Downloading gesture model...")
                    val conditions = DownloadConditions.Builder().build()
                    remoteModelManager.download(model, conditions)
                        .addOnSuccessListener {
                            Log.d(TAG, "Gesture model downloaded successfully")
                            initializeRecognizer(model)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Gesture model download failed", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check gesture model download status", e)
            }
    }

    private fun initializeRecognizer(model: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        modelReady = true
        Log.d(TAG, "Gesture recognizer initialized")
    }

    fun isReady(): Boolean = modelReady

    fun close() {
        recognizer?.close()
        recognizer = null
        modelReady = false
    }

    suspend fun recognizeGestures(
        noteId: String,
        shapes: List<Shape>
    ): List<GestureRecognitionResult> {
        if (!modelReady || shapes.isEmpty()) return emptyList()

        val results = mutableListOf<GestureRecognitionResult>()

        val inkBuilder = Ink.builder()
        for (shape in shapes) {
            val strokeBuilder = Ink.Stroke.builder()
            for (i in shape.points.indices) {
                val point = shape.points[i]
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, shape.pointTimestamps[i]))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val ink = inkBuilder.build()
        if (ink.strokes.isEmpty()) return emptyList()

        val candidates = recognizer?.recognize(ink)?.await()?.candidates
        if (candidates.isNullOrEmpty()) {
            Log.d(TAG, "No gesture candidates for note $noteId")
            return emptyList()
        }

        for (candidate in candidates) {
            val gesture = candidate.text
            val score = candidate.score?.toFloat() ?: 0f
            Log.d(TAG, "Gesture candidate for note $noteId: '$gesture' (score: $score)")
            results.add(
                GestureRecognitionResult(
                    gesture = gesture,
                    confidence = score,
                    shapeIds = shapes.map { it.id }
                )
            )
        }

        return results
    }

    /**
     * Recognize gesture for a single shape immediately (no debounce).
     * Returns the top gesture candidate name (e.g. "SCRIBBLE") or null.
     */
    suspend fun recognizeSingleShapeGesture(shape: Shape): String? {
        if (!modelReady || shape.points.isEmpty() || shape.pointTimestamps.isEmpty()) return null

        val strokeBuilder = Ink.Stroke.builder()
        for (i in shape.points.indices) {
            val point = shape.points[i]
            strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, shape.pointTimestamps[i]))
        }
        val ink = Ink.builder().addStroke(strokeBuilder.build()).build()

        val candidates = recognizer?.recognize(ink)?.await()?.candidates
        if (candidates.isNullOrEmpty()) return null

        val top = candidates[0]
        Log.d(TAG, "Immediate gesture: '${top.text}' (score: ${top.score})")
        return top.text
    }
}
