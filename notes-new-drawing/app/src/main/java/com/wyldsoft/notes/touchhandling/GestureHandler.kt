package com.wyldsoft.notes.touchhandling

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.wyldsoft.notes.editor.AppMode
import com.wyldsoft.notes.editor.EditorState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Finger gesture recognizer attached to the SurfaceView as an OnTouchListener.
 *
 * Recognizes: taps (1-4 fingers, 1-4 taps), long press, pinch expand/collapse,
 * pan/scroll, and flicks (1-2 fingers, 4 directions).
 *
 * Suppressed when isDrawingCheck() returns true (stylus in use).
 * Treats ACTION_CANCEL identically to all-fingers-up.
 */
class GestureHandler(
    private val currentModeProvider: () -> AppMode,
    private val changeMode: (AppMode) -> Unit,
    //private val setSkipStroke: () -> Unit,
    private val onGestureEvent: (GestureEvent) -> Unit
) : View.OnTouchListener {

    companion object {
        private const val TAG = "GestureHandler"
        private const val MOVE_THRESHOLD = 20f
        private const val FLICK_VELOCITY_THRESHOLD = 2000f
        private const val FLICK_MIN_DISTANCE = 50f
        private const val MULTI_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 500L
        private const val VELOCITY_WINDOW_SIZE = 5
    }

    private enum class GesturePhase { IDLE, TOUCHING, MOVING, PINCHING }

    private data class PointerTrack(
        val downX: Float,
        val downY: Float,
        val downTime: Long,
        var lastX: Float,
        var lastY: Float,
        var lastTime: Long,
        val recentPositions: MutableList<Triple<Float, Float, Long>> = mutableListOf()
    )

    // Pointer tracking
    private val activePointers = mutableMapOf<Int, PointerTrack>()
    private var maxPointersInGesture = 0
    private var gesturePhase = GesturePhase.IDLE

    // Pan state
    var isPanning: Boolean = false
        private set
    private var lastPanX = 0f
    private var lastPanY = 0f

    // Pinch state
    private var lastPinchSpan = 0f
    private var lastPinchCenterX = 0f
    private var lastPinchCenterY = 0f

    // Tap tracking
    private var tapCount = 0
    private var tapFingerCount = 0
    private var tapTimeoutRunnable: Runnable? = null

    // Long press
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false

    private val handler = Handler(Looper.getMainLooper())

    fun isStylusOrEraser(event: MotionEvent) : Boolean {
        for (i in 0 until event.pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                toolType == MotionEvent.TOOL_TYPE_ERASER) {
                Log.d(TAG, "Type is stylus or eraser")
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        Log.d(TAG, "GestureHandler.onTouch current mode=${currentModeProvider()}")

        // only proceed if action is triggered by something other than stylus or eraser
        if (currentModeProvider() == AppMode.SETTINGS) {
            if (isStylusOrEraser(event)) {
                Log.d(TAG, "onTouch set mode to drawing, dismissing settings")
                changeMode(AppMode.DRAWING)
                //setSkipStroke()
                EditorState.emitDismissSettings()
                return false
            }
        }

        if (isStylusOrEraser(event)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> handleAllPointersUp(event)
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_CANCEL -> handleAllPointersUp(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
        }
        return true
    }

    private fun handleActionDown(event: MotionEvent) {
        Log.d(TAG, "handleActionDown")
        // Cancel long press from any previous gesture
        cancelLongPress()
        longPressFired = false

        // If not continuing a multi-tap sequence, reset fully
        if (tapCount == 0) {
            resetGestureState()
        }

        registerPointer(event, 0)
        maxPointersInGesture = 1
        gesturePhase = GesturePhase.TOUCHING

        // Start long press timer
        longPressRunnable = Runnable {
            if (gesturePhase == GesturePhase.TOUCHING) {
                longPressFired = true
                emitGesture(GestureEvent.LongPress(maxPointersInGesture))
            }
        }
        handler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
    }

    private fun handlePointerDown(event: MotionEvent) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        Log.d(TAG, "handlePointerDown pointerId=$pointerId")

        registerPointer(event, pointerIndex)
        maxPointersInGesture = maxOf(maxPointersInGesture, activePointers.size)

        // Reset long press timer for new finger count
        cancelLongPress()
        longPressFired = false
        longPressRunnable = Runnable {
            if (gesturePhase == GesturePhase.TOUCHING) {
                longPressFired = true
                emitGesture(GestureEvent.LongPress(maxPointersInGesture))
            }
        }
        handler.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)

        // If now 2 fingers, compute initial pinch baseline
        if (activePointers.size == 2) {
            computePinchBaseline()
        }
    }

    private fun handleMove(event: MotionEvent) {
        updateAllPointers(event)

        when (gesturePhase) {
            GesturePhase.TOUCHING -> {
                if (anyPointerMovedBeyondThreshold()) {
                    cancelLongPress()
                    if (activePointers.size >= 2) {
                        gesturePhase = GesturePhase.PINCHING
                        computePinchBaseline()
                        emitGesture(GestureEvent.PinchStart(lastPinchCenterX, lastPinchCenterY))
                    } else {
                        gesturePhase = GesturePhase.MOVING
                        isPanning = true
                        computePanBaseline()
                        emitGesture(GestureEvent.PanStart(maxPointersInGesture))
                    }
                    // Cancel any pending tap sequence since we're now moving
                    cancelTapTimeout()
                    tapCount = 0
                }
            }
            GesturePhase.PINCHING -> {
                val (centerX, centerY, span) = computePinchState()
                if (lastPinchSpan > 0f) {
                    val scaleFactor = span / lastPinchSpan
                    emitGesture(GestureEvent.PinchMove(centerX, centerY, scaleFactor))
                }
                lastPinchSpan = span
                lastPinchCenterX = centerX
                lastPinchCenterY = centerY
            }
            GesturePhase.MOVING -> {
                val (avgX, avgY) = computeAveragePosition()
                val deltaX = avgX - lastPanX
                val deltaY = avgY - lastPanY
                if (deltaX != 0f || deltaY != 0f) {
                    emitGesture(GestureEvent.PanMove(maxPointersInGesture, deltaX, deltaY))
                }
                lastPanX = avgX
                lastPanY = avgY
            }
            GesturePhase.IDLE -> { /* ignore */ }
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerId = event.getPointerId(event.actionIndex)
        Log.d(TAG, "handlePointerUp pointerId=$pointerId")
        activePointers.remove(pointerId)

        // If down to 1 pointer during pinch, transition to pan
        if (gesturePhase == GesturePhase.PINCHING && activePointers.size == 1) {
            emitGesture(GestureEvent.PinchEnd(lastPinchCenterX, lastPinchCenterY))
            gesturePhase = GesturePhase.MOVING
            isPanning = true
            computePanBaseline()
        }
    }

    private fun handleAllPointersUp(event: MotionEvent) {
        Log.d(TAG, "handleAllPointersUp phase=$gesturePhase")
        cancelLongPress()

        when (gesturePhase) {
            GesturePhase.PINCHING -> {
                emitGesture(GestureEvent.PinchEnd(lastPinchCenterX, lastPinchCenterY))
            }
            GesturePhase.MOVING -> {
                val flick = detectFlick()
                if (flick != null) {
                    emitGesture(flick)
                } else {
                    emitGesture(GestureEvent.PanEnd(maxPointersInGesture))
                }
                isPanning = false
            }
            GesturePhase.TOUCHING -> {
                if (!longPressFired) {
                    handleTapUp()
                }
            }
            GesturePhase.IDLE -> { /* ignore */ }
        }

        activePointers.clear()
        gesturePhase = GesturePhase.IDLE
    }

    private fun handleTapUp() {
        // If finger count changed from previous tap sequence, finalize previous first
        if (tapCount > 0 && maxPointersInGesture != tapFingerCount) {
            finalizePendingTaps()
        }

        cancelTapTimeout()
        tapFingerCount = maxPointersInGesture
        tapCount++

        // Cap at 4 taps
        if (tapCount >= 4) {
            finalizePendingTaps()
            return
        }

        tapTimeoutRunnable = Runnable { finalizePendingTaps() }
        handler.postDelayed(tapTimeoutRunnable!!, MULTI_TAP_TIMEOUT)
    }

    private fun finalizePendingTaps() {
        cancelTapTimeout()
        if (tapCount > 0) {
            emitGesture(GestureEvent.Tap(tapFingerCount, tapCount))
            tapCount = 0
            tapFingerCount = 0
        }
    }

    private fun detectFlick(): GestureEvent.Flick? {
        // Use the first pointer that has velocity data
        for ((_, track) in activePointers) {
            if (track.recentPositions.size < 2) continue

            val first = track.recentPositions.first()
            val last = track.recentPositions.last()
            val dt = (last.third - first.third) / 1000f // seconds
            if (dt <= 0f) continue

            val dx = last.first - first.first
            val dy = last.second - first.second
            val vx = dx / dt
            val vy = dy / dt
            val speed = hypot(vx, vy)
            val distance = hypot(
                track.lastX - track.downX,
                track.lastY - track.downY
            )

            if (speed > FLICK_VELOCITY_THRESHOLD && distance > FLICK_MIN_DISTANCE) {
                val direction = if (abs(vx) > abs(vy)) {
                    if (vx > 0) GestureEvent.Direction.RIGHT else GestureEvent.Direction.LEFT
                } else {
                    if (vy > 0) GestureEvent.Direction.DOWN else GestureEvent.Direction.UP
                }
                return GestureEvent.Flick(maxPointersInGesture, direction)
            }
        }
        return null
    }

    // --- Helper methods ---

    private fun registerPointer(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val time = event.eventTime
        val track = PointerTrack(x, y, time, x, y, time)
        track.recentPositions.add(Triple(x, y, time))
        activePointers[pointerId] = track
    }

    private fun updateAllPointers(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            activePointers[pointerId]?.let { track ->
                val x = event.getX(i)
                val y = event.getY(i)
                val time = event.eventTime
                track.lastX = x
                track.lastY = y
                track.lastTime = time
                track.recentPositions.add(Triple(x, y, time))
                if (track.recentPositions.size > VELOCITY_WINDOW_SIZE) {
                    track.recentPositions.removeAt(0)
                }
            }
        }
    }

    private fun anyPointerMovedBeyondThreshold(): Boolean {
        return activePointers.values.any { track ->
            hypot(track.lastX - track.downX, track.lastY - track.downY) > MOVE_THRESHOLD
        }
    }

    private fun computePinchBaseline() {
        val pointers = activePointers.values.toList()
        if (pointers.size >= 2) {
            val p1 = pointers[0]
            val p2 = pointers[1]
            lastPinchSpan = hypot(p1.lastX - p2.lastX, p1.lastY - p2.lastY)
            lastPinchCenterX = (p1.lastX + p2.lastX) / 2f
            lastPinchCenterY = (p1.lastY + p2.lastY) / 2f
        }
    }

    private fun computePinchState(): Triple<Float, Float, Float> {
        val pointers = activePointers.values.toList()
        return if (pointers.size >= 2) {
            val p1 = pointers[0]
            val p2 = pointers[1]
            val span = hypot(p1.lastX - p2.lastX, p1.lastY - p2.lastY)
            val cx = (p1.lastX + p2.lastX) / 2f
            val cy = (p1.lastY + p2.lastY) / 2f
            Triple(cx, cy, span)
        } else {
            Triple(lastPinchCenterX, lastPinchCenterY, lastPinchSpan)
        }
    }

    private fun computePanBaseline() {
        val (avgX, avgY) = computeAveragePosition()
        lastPanX = avgX
        lastPanY = avgY
    }

    private fun computeAveragePosition(): Pair<Float, Float> {
        if (activePointers.isEmpty()) return Pair(0f, 0f)
        var sumX = 0f
        var sumY = 0f
        for (track in activePointers.values) {
            sumX += track.lastX
            sumY += track.lastY
        }
        val count = activePointers.size
        return Pair(sumX / count, sumY / count)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun cancelTapTimeout() {
        tapTimeoutRunnable?.let { handler.removeCallbacks(it) }
        tapTimeoutRunnable = null
    }

    private fun resetGestureState() {
        activePointers.clear()
        maxPointersInGesture = 0
        gesturePhase = GesturePhase.IDLE
        isPanning = false
        lastPinchSpan = 0f
        lastPinchCenterX = 0f
        lastPinchCenterY = 0f
        lastPanX = 0f
        lastPanY = 0f
        longPressFired = false
    }

    private fun emitGesture(event: GestureEvent) {
        Log.d(TAG, "Gesture: ${event.displayName()}")
        onGestureEvent(event)
    }
}
