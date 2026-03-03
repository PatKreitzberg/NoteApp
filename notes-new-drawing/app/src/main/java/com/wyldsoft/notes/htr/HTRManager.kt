package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.domain.models.Shape

data class RecognitionResult(
    val text: String,
    val confidence: Float
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

    fun recognizeShapes(
        shapes: List<Shape>,
        onResult: (RecognitionResult?) -> Unit
    ) {
        val currentRecognizer = recognizer
        if (currentRecognizer == null || !modelReady) {
            Log.w(TAG, "Recognizer not ready")
            onResult(null)
            return
        }

        if (shapes.isEmpty()) {
            onResult(null)
            return
        }

        val ink = shapesToInk(shapes)
        if (ink == null) {
            onResult(null)
            return
        }

        currentRecognizer.recognize(ink)
            .addOnSuccessListener { result ->
                if (result.candidates.isNotEmpty()) {
                    val best = result.candidates[0]
                    val confidence = if (best.score != null) best.score!!.toFloat() else 0f
                    onResult(RecognitionResult(best.text, confidence))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Recognition failed", e)
                onResult(null)
            }
    }

    private fun shapesToInk(shapes: List<Shape>): Ink? {
        val inkBuilder = Ink.builder()
        var hasStrokes = false

        for (shape in shapes) {
            if (shape.points.size < 2) continue

            val strokeBuilder = Ink.Stroke.builder()
            for (i in shape.points.indices) {
                val point = shape.points[i]
                val timestamp = if (i < shape.pointTimestamps.size) {
                    shape.pointTimestamps[i]
                } else {
                    shape.timestamp + i * 10L // fallback: 10ms spacing
                }
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, timestamp))
            }
            inkBuilder.addStroke(strokeBuilder.build())
            hasStrokes = true
        }

        return if (hasStrokes) inkBuilder.build() else null
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        modelReady = false
    }
}
