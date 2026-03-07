package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.tasks.await

data class RecognitionResult(
    val noteId: String,
    val text: String,
    val confidence: Float,
    val shapeIds: List<String>,
    val boundingBoxLeft: Float,
    val boundingBoxTop: Float,
    val boundingBoxRight: Float,
    val boundingBoxBottom: Float
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
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        modelReady = true
        Log.d(TAG, "Recognizer initialized")
    }

    fun isReady(): Boolean = modelReady

    fun close() {
        recognizer?.close()
        recognizer = null
        modelReady = false
    }

    internal suspend fun basicRecognize(map: MutableMap<String, MutableList<Shape>>): List<RecognitionResult> {
        Log.d(TAG, "Starting basic recognition with ${map.size} notes")
        val results = mutableListOf<RecognitionResult>()

        for ((noteId, shapes) in map) {
            if (shapes.isEmpty()) continue
            Log.d(TAG, "Processing note $noteId with ${shapes.size} shapes")

            val inkBuilder = Ink.builder()
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (shape in shapes) {
                val strokeBuilder = Ink.Stroke.builder()
                for (i in shape.points.indices) {
                    val point = shape.points[i]
                    strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, shape.pointTimestamps[i]))
                    if (point.x < minX) minX = point.x
                    if (point.y < minY) minY = point.y
                    if (point.x > maxX) maxX = point.x
                    if (point.y > maxY) maxY = point.y
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
                    shapeIds = shapes.map { it.id },
                    boundingBoxLeft = minX,
                    boundingBoxTop = minY,
                    boundingBoxRight = maxX,
                    boundingBoxBottom = maxY
                )
            )
        }

        return results
    }

}
