package com.wyldsoft.notes.gestures

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.wyldsoft.notes.viewport.ViewportManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class GestureHandler(
    context: Context,
    private val view: View,
    private var viewportManager: ViewportManager? = null
) {
    companion object {
        private const val TAG = "GestureHandler"
        private const val TAP_TIMEOUT = 300L
        private const val MULTI_FINGER_TAP_SLOP = 100
        private const val FLICK_THRESHOLD_VELOCITY = 1000
        private const val FLICK_MAX_DURATION = 200L
        private const val MEANINGFUL_SCALE_THRESHOLD = 0.05f
    }

    private val scaleGestureDetector: ScaleGestureDetector

    private var isPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var totalPanX = 0f
    private var totalPanY = 0f
    private var activeTouchCount = 0
    private var maxTouchCount = 0
    private var isPinching = false
    private var pinchCenterX = 0f
    private var pinchCenterY = 0f
    private var currentScale = 1f
    private var hasScaledMeaningfully = false

    var gestureMappings: List<GestureMapping> = GestureSettingsRepository.DEFAULT_MAPPINGS
    var onGestureAction: ((GestureAction) -> Unit)? = null
    var onScrollingStateChanged: ((isScrolling: Boolean) -> Unit)? = null

    private val tapDetector = TapDetector(
        tapTimeout = TAP_TIMEOUT,
        tapSlop = MULTI_FINGER_TAP_SLOP,
        gestureMappings = { gestureMappings },
        onGestureAction = { onGestureAction?.invoke(it) }
    )

    private val flickDetector = FlickDetector(
        thresholdVelocity = FLICK_THRESHOLD_VELOCITY,
        maxDurationMs = FLICK_MAX_DURATION,
        gestureMappings = { gestureMappings },
        onGestureAction = { onGestureAction?.invoke(it) }
    )

    init {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                hasScaledMeaningfully = false
                pinchCenterX = detector.focusX
                pinchCenterY = detector.focusY
                currentScale = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                pinchCenterX = detector.focusX
                pinchCenterY = detector.focusY
                val pinchAction = gestureMappings.find { it.gesture == GestureType.PINCH }?.action
                if (abs(currentScale - 1f) > MEANINGFUL_SCALE_THRESHOLD && pinchAction == GestureAction.ZOOM) {
                    if (!hasScaledMeaningfully) {
                        hasScaledMeaningfully = true
                        onScrollingStateChanged?.invoke(true)
                        tapDetector.cancelPendingTap()
                    }
                    viewportManager?.updateScale(detector.scaleFactor, pinchCenterX, pinchCenterY)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
            }
        })
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        flickDetector.addMovement(event)
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchCount = 1; maxTouchCount = 1
                handleDown(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activeTouchCount++
                maxTouchCount = maxOf(maxTouchCount, activeTouchCount)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isPinching && activeTouchCount == 1) handleMove(event)
            }
            MotionEvent.ACTION_POINTER_UP -> { activeTouchCount-- }
            MotionEvent.ACTION_UP -> {
                handleUp(event)
                activeTouchCount = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                handleUp(event)
                activeTouchCount = 0
                resetStates()
            }
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        val currentTime = System.currentTimeMillis()
        flickDetector.recordStart(currentTime)
        isPanning = false
        panStartX = event.x; panStartY = event.y
        totalPanX = 0f; totalPanY = 0f
    }

    private fun handleMove(event: MotionEvent) {
        val x = event.x; val y = event.y
        val deltaX = x - panStartX; val deltaY = y - panStartY
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance > 10 && !isPanning) {
            isPanning = true
            onScrollingStateChanged?.invoke(true)
            tapDetector.cancelPendingTap()
        }

        if (isPanning) {
            val panAction = gestureMappings.find { it.gesture == GestureType.PAN }?.action
            if (panAction != GestureAction.SCROLL) return
            val currentDeltaX = x - (panStartX + totalPanX)
            val currentDeltaY = y - (panStartY + totalPanY)
            totalPanX += currentDeltaX; totalPanY += currentDeltaY
            viewportManager?.updateOffset(currentDeltaX, currentDeltaY)
        }
    }

    private fun handleUp(event: MotionEvent) {
        val currentTime = System.currentTimeMillis()

        if (flickDetector.checkFlick(currentTime, isPanning)) {
            resetStates()
            return
        }

        if (!isPanning && !hasScaledMeaningfully) {
            tapDetector.onTapUp(event.x, event.y, maxTouchCount)
        }

        if (isPanning) onScrollingStateChanged?.invoke(false)
        if (hasScaledMeaningfully) onScrollingStateChanged?.invoke(false)
        resetStates()
    }

    private fun resetStates() {
        isPanning = false; isPinching = false
        hasScaledMeaningfully = false
        maxTouchCount = 0; activeTouchCount = 0
    }

    fun cleanup() {
        flickDetector.cleanup()
        tapDetector.cleanup()
    }

    fun setViewportManager(viewportManager: ViewportManager?) {
        this.viewportManager = viewportManager
    }
}
