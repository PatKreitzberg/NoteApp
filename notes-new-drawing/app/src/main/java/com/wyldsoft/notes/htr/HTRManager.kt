package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.tasks.await

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

    internal suspend fun basicRecognize(map: MutableMap<String, MutableList<Shape>>) {
        Log.d(TAG, "Starting basic recognition with ${map.size} notes")
        // Build out ink objects for MLKit
        val inkBuilder = Ink.builder()
        for ((noteId, shapes) in map) {
            Log.d(TAG, "Processing note $noteId with ${shapes.size} shapes")
            for (shape in shapes) {
                Log.d(TAG, "Processing shape ${shape.id} with ${shape.points.size} points")
                val strokeBuilder = Ink.Stroke.builder()

                // process each point in the shape, using timestamps if available
                for (i in shape.points.indices) {
                    val point = shape.points[i]
                    strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, shape.pointTimestamps[i]))
                }
                inkBuilder.addStroke(strokeBuilder.build())
            }
        }
        Log.d(TAG, "Finished building ink with ${inkBuilder.build().strokes.size} strokes")
        // Build out ink
        val ink = inkBuilder.build()
        Log.d(TAG, "Built ink with ${ink.strokes.size} strokes")
        if (ink.strokes.isEmpty()) return

        val resultString = recognizer?.recognize(ink)?.await()?.candidates?.getOrNull(0)?.text ?: "No text recognized"
        Log.d(TAG, "Basic recognition result: $resultString")
    }

}
