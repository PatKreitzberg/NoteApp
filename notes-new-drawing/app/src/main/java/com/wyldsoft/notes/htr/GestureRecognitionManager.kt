package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.shapemanagement.shapes.Shape
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
        Log.d(TAG, "downloadModel")
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
        Log.d(TAG, "initializeRecognizer")
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        modelReady = true
        Log.d(TAG, "Gesture recognizer initialized")
    }

    fun isReady(): Boolean = modelReady

    fun close() {
        Log.d(TAG, "close")
        recognizer?.close()
        recognizer = null
        modelReady = false
    }

    /**
     * Recognize gesture for a single shape immediately (no debounce).
     * Returns the top gesture candidate name (e.g. "SCRIBBLE") or null.
     */
    suspend fun recognizeSingleShapeGesture(shape: Shape): String? {
        Log.d(TAG, "recognizeSingleShapeGesture")
        if (!modelReady) return null
        val points = shape.touchPointList?.points ?: return null
        if (points.isEmpty()) return null

        val strokeBuilder = Ink.Stroke.builder()
        for (point in points) {
            if (point == null) continue
            strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
        }
        val ink = Ink.builder().addStroke(strokeBuilder.build()).build()

        val candidates = recognizer?.recognize(ink)?.await()?.candidates
        if (candidates.isNullOrEmpty()) return null

        val top = candidates[0]
        Log.d(TAG, "Immediate gesture: '${top.text}' (score: ${top.score})")
        return top.text
    }
}
