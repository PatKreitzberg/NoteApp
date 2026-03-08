package com.wyldsoft.notes.gestures

import android.util.Log
import android.view.VelocityTracker
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Detects flick gestures based on velocity at touch-up.
 * Extracted from GestureHandler to keep it under 300 lines.
 */
class FlickDetector(
    private val thresholdVelocity: Int,
    private val maxDurationMs: Long,
    private val gestureMappings: () -> List<GestureMapping>,
    private val onGestureAction: (GestureAction) -> Unit
) {
    companion object {
        private const val TAG = "FlickDetector"
    }

    private val velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private var startTime = 0L

    fun recordStart(time: Long) { startTime = time }

    fun addMovement(event: android.view.MotionEvent) = velocityTracker.addMovement(event)

    /**
     * Check if the touch-up event constitutes a flick. Returns true if a flick was dispatched.
     */
    fun checkFlick(currentTime: Long, wasPanning: Boolean): Boolean {
        val duration = currentTime - startTime
        if (duration >= maxDurationMs || !wasPanning) return false

        velocityTracker.computeCurrentVelocity(1000)
        val velocityX = velocityTracker.xVelocity
        val velocityY = velocityTracker.yVelocity
        val velocity = sqrt(velocityX * velocityX + velocityY * velocityY)

        if (velocity <= thresholdVelocity) return false

        val direction = getDirection(velocityX, velocityY)
        Log.d(TAG, "FLICK detected — direction: $direction, velocity: $velocity")

        val flickGesture = getFlickGestureType(direction)
        if (flickGesture != null) {
            val action = gestureMappings().find { it.gesture == flickGesture }?.action
            if (action != null && action != GestureAction.NONE) {
                onGestureAction(action)
                return true
            }
        }
        return false
    }

    fun cleanup() = velocityTracker.recycle()

    private fun getDirection(deltaX: Float, deltaY: Float): String {
        val angle = atan2(-deltaY, deltaX) * 180 / Math.PI
        return when {
            angle > -45 && angle <= 45 -> "RIGHT"
            angle > 45 && angle <= 135 -> "UP"
            angle > 135 || angle <= -135 -> "LEFT"
            else -> "DOWN"
        }
    }

    private fun getFlickGestureType(direction: String): GestureType? = when (direction) {
        "UP" -> GestureType.FLICK_UP
        "DOWN" -> GestureType.FLICK_DOWN
        "LEFT" -> GestureType.FLICK_LEFT
        "RIGHT" -> GestureType.FLICK_RIGHT
        else -> null
    }
}
