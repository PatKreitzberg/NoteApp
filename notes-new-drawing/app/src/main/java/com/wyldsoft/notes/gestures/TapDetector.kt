package com.wyldsoft.notes.gestures

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects single, double, and multi-finger taps with a debounce timeout.
 * Extracted from GestureHandler to keep it under 300 lines.
 */
class TapDetector(
    private val tapTimeout: Long,
    private val tapSlop: Int,
    private val gestureMappings: () -> List<GestureMapping>,
    private val onGestureAction: (GestureAction) -> Unit
) {
    companion object {
        private const val TAG = "TapDetector"
    }

    private val tapHandler = Handler(Looper.getMainLooper())
    private var tapRunnable: Runnable? = null
    private var tapCount = 0
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var pendingTapFingerCount = 0

    /**
     * Record a tap at the given position with [maxFingers] fingers used during the gesture.
     * Returns true if tap detection is pending (caller should not process other events yet).
     */
    fun onTapUp(x: Float, y: Float, maxFingers: Int) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTap = currentTime - lastTapTime
        val distanceFromLastTap = sqrt((x - lastTapX) * (x - lastTapX) + (y - lastTapY) * (y - lastTapY))

        tapRunnable?.let { tapHandler.removeCallbacks(it) }

        if (timeSinceLastTap < tapTimeout && distanceFromLastTap < tapSlop && tapCount > 0) {
            tapCount++
        } else {
            tapCount = 1
        }

        lastTapTime = currentTime
        lastTapX = x
        lastTapY = y
        pendingTapFingerCount = maxFingers

        tapRunnable = Runnable {
            val gestureType = getTapGestureType(pendingTapFingerCount, tapCount)
            if (gestureType != null) {
                val action = gestureMappings().find { it.gesture == gestureType }?.action
                if (action != null && action != GestureAction.NONE) {
                    Log.d(TAG, "Executing action $action for $pendingTapFingerCount-finger ${tapCount}x tap")
                    onGestureAction(action)
                }
            }
            tapCount = 0
        }

        tapHandler.postDelayed(tapRunnable!!, tapTimeout)
    }

    /** Cancel any pending tap detection (call when pan or pinch starts). */
    fun cancelPendingTap() {
        tapRunnable?.let { tapHandler.removeCallbacks(it) }
        tapCount = 0
    }

    private fun getTapGestureType(fingerCount: Int, tapCount: Int): GestureType? {
        if (tapCount > 2) return null
        return when (fingerCount) {
            1 -> if (tapCount == 1) GestureType.ONE_FINGER_SINGLE_TAP else GestureType.ONE_FINGER_DOUBLE_TAP
            2 -> if (tapCount == 1) GestureType.TWO_FINGER_SINGLE_TAP else GestureType.TWO_FINGER_DOUBLE_TAP
            3 -> if (tapCount == 1) GestureType.THREE_FINGER_SINGLE_TAP else GestureType.THREE_FINGER_DOUBLE_TAP
            4 -> if (tapCount == 1) GestureType.FOUR_FINGER_SINGLE_TAP else null
            else -> null
        }
    }

    fun cleanup() {
        tapRunnable?.let { tapHandler.removeCallbacks(it) }
    }
}
