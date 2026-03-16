package com.wyldsoft.notes.htr

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.geometry.GeometricShapeType
import kotlinx.coroutines.tasks.await

data class ShapeRecognitionResult(
    val shapeType: GeometricShapeType,
    val confidence: Float
)

class ShapeRecognitionManager {
    companion object {
        private const val TAG = "ShapeRecognition"
        private const val SHAPE_MODEL_TAG = "zxx-Zsym-x-shapes"
        private const val CONFIDENCE_THRESHOLD = 0.25f
    }

    private var recognizer: DigitalInkRecognizer? = null
    private var modelReady = false

    init {
        downloadModel()
    }

    private fun downloadModel() {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(SHAPE_MODEL_TAG)
        if (modelIdentifier == null) {
            Log.e(TAG, "No model found for language tag: $SHAPE_MODEL_TAG")
            return
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.isModelDownloaded(model)
            .addOnSuccessListener { isDownloaded ->
                if (isDownloaded) {
                    Log.d(TAG, "Shape model already downloaded")
                    initializeRecognizer(model)
                } else {
                    Log.d(TAG, "Downloading shape model...")
                    val conditions = DownloadConditions.Builder().build()
                    remoteModelManager.download(model, conditions)
                        .addOnSuccessListener {
                            Log.d(TAG, "Shape model downloaded successfully")
                            initializeRecognizer(model)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Shape model download failed", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check shape model download status", e)
            }
    }

    private fun initializeRecognizer(model: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        )
        modelReady = true
        Log.d(TAG, "Shape recognizer initialized")
    }

    fun isReady(): Boolean = modelReady

    fun close() {
        recognizer?.close()
        recognizer = null
        modelReady = false
    }

    /**
     * Recognize the shape drawn by a single stroke.
     * Returns a [ShapeRecognitionResult] if the top candidate is a supported shape
     * with confidence below the threshold, or null otherwise.
     */
    suspend fun recognizeShape(shape: Shape): ShapeRecognitionResult? {
        if (!modelReady || shape.points.isEmpty() || shape.pointTimestamps.isEmpty()) return null

        val strokeBuilder = Ink.Stroke.builder()
        for (i in shape.points.indices) {
            val point = shape.points[i]
            strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, shape.pointTimestamps[i]))
        }
        val ink = Ink.builder().addStroke(strokeBuilder.build()).build()

        val candidates = recognizer?.recognize(ink)?.await()?.candidates
        if (candidates.isNullOrEmpty()) {
            Log.d(TAG, "No shape candidates returned")
            return null
        }

        for (candidate in candidates) {
            Log.d(TAG, "Shape candidate: '${candidate.text}' (score: ${candidate.score})")
        }

        val top = candidates[0]
        val score = top.score?.toFloat() ?: Float.MAX_VALUE
        Log.d(TAG, "Top shape candidate: '${top.text}' score=$score (threshold=$CONFIDENCE_THRESHOLD)")

        if (score >= CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Score $score >= threshold $CONFIDENCE_THRESHOLD, not confident enough")
            return null
        }

        val geometricType = mapToGeometricShapeType(top.text)
        if (geometricType == null) {
            Log.d(TAG, "Shape '${top.text}' is not one of our supported geometric shapes")
            return null
        }

        Log.d(TAG, "Recognized shape: ${geometricType.displayName()} with score $score")
        return ShapeRecognitionResult(shapeType = geometricType, confidence = score)
    }

    private fun mapToGeometricShapeType(shapeName: String): GeometricShapeType? {
        val normalized = shapeName.trim().uppercase()
        return when {
            normalized.contains("CIRCLE") || normalized.contains("ELLIPSE") -> GeometricShapeType.CIRCLE
            normalized.contains("RECTANGLE") || normalized.contains("SQUARE") -> GeometricShapeType.SQUARE
            normalized.contains("TRIANGLE") -> GeometricShapeType.TRIANGLE
            normalized.contains("LINE") -> GeometricShapeType.LINE
            else -> null
        }
    }
}
