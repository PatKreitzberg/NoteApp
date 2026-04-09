package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.tasks.await

data class RecognitionResult(
    val noteId: String,
    val text: String,
    val confidence: Float,
    val shapeIds: List<String>
)

class HTRManager {
    companion object {
        private const val TAG = "HTRManager"
        private const val LANGUAGE_TAG = "en-US"
    }

    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false

    init {
        downloadModel()
    }

    private fun downloadModel() {
        Log.d(TAG, "downloadModel")
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
        if (modelIdentifier == null) {
            Log.e(TAG, "No model found for language tag: $LANGUAGE_TAG")
            return
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    Log.d(TAG, "Model already downloaded")
                    initializeRecognizer(model)
                } else {
                    Log.d(TAG, "Downloading model...")
                    val conditions = DownloadConditions.Builder().build()
                    remoteModelManager.download(model, conditions)
                        .addOnSuccessListener {
                            Log.d(TAG, "Model downloaded successfully")
                            initializeRecognizer(model)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Model download failed", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check model download status", e)
            }
    }

    private fun initializeRecognizer(model: DigitalInkRecognitionModel) {
        Log.d(TAG, "initializeRecognizer")
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        modelReady = true
        Log.d(TAG, "Recognizer initialized")
    }

    fun isReady(): Boolean = modelReady

    fun close() {
        Log.d(TAG, "close")
        recognizer?.close()
        recognizer = null
        modelReady = false
    }

    internal suspend fun recognizeShapes(map: MutableMap<String, MutableList<Shape>>): List<RecognitionResult> {
        Log.d(TAG, "recognizeShapes with ${map.size} notes")
        val results = mutableListOf<RecognitionResult>()

        for ((noteId, shapes) in map) {
            if (shapes.isEmpty()) continue
            Log.d(TAG, "Processing note $noteId with ${shapes.size} shapes")

            val inkBuilder = Ink.builder()

            for (shape in shapes) {
                val points = shape.touchPointList?.points ?: continue
                if (points.isEmpty()) continue
                val strokeBuilder = Ink.Stroke.builder()
                for (point in points) {
                    if (point == null) continue
                    strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
                }
                inkBuilder.addStroke(strokeBuilder.build())
            }

            val ink = inkBuilder.build()
            if (ink.strokes.isEmpty()) continue

            val candidates = recognizer?.recognize(ink)?.await()?.candidates
            val topCandidate = candidates?.getOrNull(0)
            val text = topCandidate?.text ?: continue
            val score = topCandidate.score?.toFloat() ?: 0f

            Log.d(TAG, "Recognition result for note $noteId: $text (score: $score)")

            results.add(
                RecognitionResult(
                    noteId = noteId,
                    text = text,
                    confidence = score,
                    shapeIds = shapes.mapNotNull { it.entityId }
                )
            )
        }

        return results
    }
}
