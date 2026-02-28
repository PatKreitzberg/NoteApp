package com.wyldsoft.notes.gestures

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
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
        
        // Tap timing constants
        private const val TAP_TIMEOUT = 300L // ms between taps
        private const val MULTI_FINGER_TAP_SLOP = 100 // pixels
        
        // Flick constants
        private const val FLICK_THRESHOLD_VELOCITY = 1000 // pixels per second
        private const val FLICK_MAX_DURATION = 200L // ms
        
        // Scale threshold - cumulative scale must differ from 1.0 by this much to count as real pinch
        private const val MEANINGFUL_SCALE_THRESHOLD = 0.05f

        // Direction constants
        private const val DIRECTION_UP = "UP"
        private const val DIRECTION_DOWN = "DOWN"
        private const val DIRECTION_LEFT = "LEFT"
        private const val DIRECTION_RIGHT = "RIGHT"
    }
    
    // Gesture detectors
    private val scaleGestureDetector: ScaleGestureDetector
    private val velocityTracker = VelocityTracker.obtain()
    
    // Handler for delayed tap detection
    private val tapHandler = Handler(Looper.getMainLooper())
    private var tapRunnable: Runnable? = null
    
    // Tap tracking
    private var tapCount = 0
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var activeTouchCount = 0
    private var maxTouchCount = 0
    private var pendingTapFingerCount = 0
    
    // Pan/Scroll tracking
    private var isPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var totalPanX = 0f
    private var totalPanY = 0f
    
    // Flick tracking
    private var flickStartTime = 0L
    private var flickStartX = 0f
    private var flickStartY = 0f
    
    // Pinch tracking
    private var isPinching = false
    private var pinchCenterX = 0f
    private var pinchCenterY = 0f
    private var currentScale = 1f
    private var hasScaledMeaningfully = false // true if actual zoom occurred during pinch

    // Gesture mappings and action callback
    var gestureMappings: List<GestureMapping> = GestureSettingsRepository.DEFAULT_MAPPINGS
    var onGestureAction: ((GestureAction) -> Unit)? = null
    
    init {
        // Scale gesture detector for pinch/expand
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                hasScaledMeaningfully = false
                pinchCenterX = detector.focusX
                pinchCenterY = detector.focusY
                currentScale = 1f
                Log.d(TAG, "Pinch gesture started at center: ($pinchCenterX, $pinchCenterY)")
                return true
            }
            
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                pinchCenterX = detector.focusX
                pinchCenterY = detector.focusY

                // Check if pinch is configured to zoom
                val pinchAction = gestureMappings.find { it.gesture == GestureType.PINCH }?.action

                // Check if scale change is meaningful (not just finger jitter)
                if (abs(currentScale - 1f) > MEANINGFUL_SCALE_THRESHOLD && pinchAction == GestureAction.ZOOM) {
                    if (!hasScaledMeaningfully) {
                        hasScaledMeaningfully = true
                        // Cancel pending taps only once we know it's a real pinch
                        tapRunnable?.let {
                            tapHandler.removeCallbacks(it)
                            tapCount = 0
                        }
                    }

                    // Update viewport with scale change
                    if (viewportManager != null) {
                        viewportManager?.updateScale(detector.scaleFactor, pinchCenterX, pinchCenterY)
                        Log.d(TAG, "ViewportManager.updateScale called successfully")
                    } else {
                        Log.w(TAG, "ViewportManager is null - cannot update scale!")
                    }

                    // Trigger view refresh
                    (view.context as? com.wyldsoft.notes.drawing.DrawingActivityInterface)?.forceScreenRefresh()

                    if (currentScale > 1f) {
                        Log.d(TAG, "Pinch EXPAND - Scale: $currentScale, Center: ($pinchCenterX, $pinchCenterY)")
                    } else {
                        Log.d(TAG, "Pinch COLLAPSE - Scale: $currentScale, Center: ($pinchCenterX, $pinchCenterY)")
                    }
                }
                return true
            }
            
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                Log.d(TAG, "Pinch gesture ended - Final scale: $currentScale")
                isPinching = false
            }
        })
    }
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        // Add event to velocity tracker
        velocityTracker.addMovement(event)
        
        // Handle scale gestures
        scaleGestureDetector.onTouchEvent(event)
        
        // Always track finger count, even during pinch
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchCount = 1
                maxTouchCount = 1
                handleDown(event)
                Log.d(TAG, "Finger down - Active fingers: $activeTouchCount")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                activeTouchCount++
                maxTouchCount = maxOf(maxTouchCount, activeTouchCount)
                Log.d(TAG, "Pointer down - Active fingers: $activeTouchCount")
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d(TAG, "Move event - Active fingers: $activeTouchCount, Max fingers during gesture: $maxTouchCount")
                if (!isPinching && activeTouchCount == 1) {
                    Log.d(TAG, "Processing move for potential pan/scroll")
                    handleMove(event)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                activeTouchCount--
                Log.d(TAG, "Pointer up - Active fingers: $activeTouchCount")
            }

            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Finger up - Active fingers: $activeTouchCount")
                handleUp(event)
                activeTouchCount = 0
            }

            MotionEvent.ACTION_CANCEL -> {
                // Treat cancel as end of gesture
                Log.d(TAG, "Touch event cancelled")
                handleUp(event)
                activeTouchCount = 0
                resetStates()
            }
        }
        
        return true
    }
    
    private fun handleDown(event: MotionEvent) {
        val x = event.x
        val y = event.y
        val currentTime = System.currentTimeMillis()
        
        // Start tracking for potential flick
        flickStartTime = currentTime
        flickStartX = x
        flickStartY = y
        
        // Reset pan tracking
        isPanning = false
        panStartX = x
        panStartY = y
        totalPanX = 0f
        totalPanY = 0f
    }
    
    private fun handleMove(event: MotionEvent) {
        val x = event.x
        val y = event.y

        val deltaX = x - panStartX
        val deltaY = y - panStartY
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance > 10 && !isPanning) { // Threshold to start panning
            isPanning = true
            Log.d(TAG, "Pan/Scroll gesture started")

            // Cancel any pending tap detection since we're now panning
            tapRunnable?.let {
                tapHandler.removeCallbacks(it)
                tapCount = 0
            }
        }

        if (isPanning) {
            // Check if pan is configured to scroll
            val panAction = gestureMappings.find { it.gesture == GestureType.PAN }?.action
            if (panAction != GestureAction.SCROLL) return

            Log.d(TAG, "Pan/Scroll gesture in progress - Delta: ($deltaX, $deltaY), Distance: $distance")
            val currentDeltaX = x - (panStartX + totalPanX)
            val currentDeltaY = y - (panStartY + totalPanY)

            totalPanX += currentDeltaX
            totalPanY += currentDeltaY

            // Update viewport with pan offset
            Log.d(TAG, "Updating viewport with pan offset: ($currentDeltaX, $currentDeltaY)")
            if (viewportManager != null) {
                viewportManager?.updateOffset(currentDeltaX, currentDeltaY)
                Log.d(TAG, "ViewportManager.updateOffset called successfully")
            } else {
                Log.w(TAG, "ViewportManager is null - cannot update offset!")
            }

            // Trigger view refresh
            (view.context as? com.wyldsoft.notes.drawing.DrawingActivityInterface)?.forceScreenRefresh()

            val direction = getDirection(totalPanX, totalPanY)
            Log.d(TAG, "Pan/Scroll - Direction: $direction, Distance: ${sqrt(totalPanX * totalPanX + totalPanY * totalPanY)}, Delta: ($totalPanX, $totalPanY)")
        }
    }
    
    private fun handleUp(event: MotionEvent) {
        Log.d(TAG, "Touch up event - Active fingers: $activeTouchCount, Max fingers during gesture: $maxTouchCount")
        val x = event.x
        val y = event.y
        val currentTime = System.currentTimeMillis()
        
        // Check for flick
        val duration = currentTime - flickStartTime
        if (duration < FLICK_MAX_DURATION && isPanning) {
            Log.d(TAG, "Evaluating for FLICK - Duration: $duration ms, Total pan distance: ${sqrt(totalPanX * totalPanX + totalPanY * totalPanY)}")
            velocityTracker.computeCurrentVelocity(1000) // pixels per second
            val velocityX = velocityTracker.xVelocity
            val velocityY = velocityTracker.yVelocity
            val velocity = sqrt(velocityX * velocityX + velocityY * velocityY)
            
            if (velocity > FLICK_THRESHOLD_VELOCITY) {
                val direction = getDirection(velocityX, velocityY)
                Log.d(TAG, "FLICK detected - Direction: $direction, Velocity: $velocity")

                val flickGesture = getFlickGestureType(direction)
                if (flickGesture != null) {
                    val action = gestureMappings.find { it.gesture == flickGesture }?.action
                    if (action != null && action != GestureAction.NONE) {
                        Log.d(TAG, "Executing action $action for flick $direction")
                        onGestureAction?.invoke(action)
                    }
                }

                resetStates()
                return
            }
        }
        
        // Check for tap (allow if no panning and no meaningful scaling occurred)
        if (!isPanning && !hasScaledMeaningfully) {
            Log.d(TAG, "Potential TAP detected at ($x, $y) with $activeTouchCount finger(s)")
            val timeSinceLastTap = currentTime - lastTapTime
            val distanceFromLastTap = sqrt((x - lastTapX) * (x - lastTapX) + (y - lastTapY) * (y - lastTapY))
            
            // Cancel any pending tap detection
            tapRunnable?.let {
                tapHandler.removeCallbacks(it)
            }
            
            if (timeSinceLastTap < TAP_TIMEOUT && distanceFromLastTap < MULTI_FINGER_TAP_SLOP && tapCount > 0) {
                tapCount++
            } else {
                tapCount = 1
            }
            
            lastTapTime = currentTime
            lastTapX = x
            lastTapY = y
            pendingTapFingerCount = maxTouchCount
            
            // Schedule tap detection after timeout
            tapRunnable = Runnable {
                Log.d(TAG, "Evaluating TAP - Count: $tapCount, Fingers: $pendingTapFingerCount")
                val tapName = when(tapCount) {
                    1 -> "single"
                    2 -> "double"
                    3 -> "triple"
                    4 -> "quadruple"
                    else -> "$tapCount"
                }
                Log.d(TAG, "TAP detected - $pendingTapFingerCount finger(s), $tapName tap")

                // Look up configured action for this tap gesture
                val gestureType = getTapGestureType(pendingTapFingerCount, tapCount)
                if (gestureType != null) {
                    val action = gestureMappings.find { it.gesture == gestureType }?.action
                    if (action != null && action != GestureAction.NONE) {
                        Log.d(TAG, "Executing action $action for gesture $gestureType")
                        onGestureAction?.invoke(action)
                    }
                }

                // Reset tap count after logging
                tapCount = 0
            }
            
            // Post with delay to wait for potential additional taps
            tapHandler.postDelayed(tapRunnable!!, TAP_TIMEOUT)
        }
        
        // Log end of pan if it was happening
        if (isPanning) {
            Log.d(TAG, "Pan/Scroll gesture ended - Total distance: ${sqrt(totalPanX * totalPanX + totalPanY * totalPanY)}")
        }
        
        resetStates()
    }
    
    private fun getDirection(deltaX: Float, deltaY: Float): String {
        val angle = atan2(-deltaY, deltaX) * 180 / Math.PI
        
        return when {
            angle > -45 && angle <= 45 -> DIRECTION_RIGHT
            angle > 45 && angle <= 135 -> DIRECTION_UP
            angle > 135 || angle <= -135 -> DIRECTION_LEFT
            else -> DIRECTION_DOWN
        }
    }
    
    private fun resetStates() {
        isPanning = false
        isPinching = false
        hasScaledMeaningfully = false
        maxTouchCount = 0
        activeTouchCount = 0
    }
    
    private fun getTapGestureType(fingerCount: Int, tapCount: Int): GestureType? {
        if (tapCount > 2) return null // Only single and double taps are mapped
        return when (fingerCount) {
            1 -> if (tapCount == 1) GestureType.ONE_FINGER_SINGLE_TAP else GestureType.ONE_FINGER_DOUBLE_TAP
            2 -> if (tapCount == 1) GestureType.TWO_FINGER_SINGLE_TAP else GestureType.TWO_FINGER_DOUBLE_TAP
            3 -> if (tapCount == 1) GestureType.THREE_FINGER_SINGLE_TAP else GestureType.THREE_FINGER_DOUBLE_TAP
            4 -> if (tapCount == 1) GestureType.FOUR_FINGER_SINGLE_TAP else null
            else -> null
        }
    }

    private fun getFlickGestureType(direction: String): GestureType? {
        return when (direction) {
            DIRECTION_UP -> GestureType.FLICK_UP
            DIRECTION_DOWN -> GestureType.FLICK_DOWN
            DIRECTION_LEFT -> GestureType.FLICK_LEFT
            DIRECTION_RIGHT -> GestureType.FLICK_RIGHT
            else -> null
        }
    }

    fun cleanup() {
        velocityTracker.recycle()
        tapRunnable?.let {
            tapHandler.removeCallbacks(it)
        }
    }
    
    fun setViewportManager(viewportManager: ViewportManager?) {
        this.viewportManager = viewportManager
        Log.d(TAG, "ViewportManager set: ${viewportManager != null}")
    }
}