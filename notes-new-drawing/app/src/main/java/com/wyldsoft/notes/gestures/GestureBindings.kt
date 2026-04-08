package com.wyldsoft.notes.gestures

import com.wyldsoft.notes.touchhandling.GestureEvent

/**
 * Defines all gesture keys and their human-readable display names.
 * Excludes Pan and Pinch events (those drive viewport transforms, not user actions).
 */
object GestureBindings {

    private val TAP_NAMES = mapOf(1 to "tap", 2 to "double tap", 3 to "triple tap", 4 to "quadruple tap")
    private val FINGER_LABELS = mapOf(1 to "1-finger", 2 to "2-finger", 3 to "3-finger", 4 to "4-finger")
    private val DIRECTIONS = listOf("up", "down", "left", "right")

    /** Ordered list of (key, displayName) pairs for all mappable gestures. */
    val ALL_GESTURE_KEYS: List<Pair<String, String>> = buildList {
        // Taps: 1–4 fingers × 1–4 taps
        for (fingers in 1..4) {
            for (taps in 1..4) {
                val key = "tap_${fingers}_${taps}"
                val name = "${FINGER_LABELS[fingers]} ${TAP_NAMES[taps]}"
                add(key to name)
            }
        }
        // Long press: 1–4 fingers
        for (fingers in 1..4) {
            val key = "longpress_${fingers}"
            val name = "${FINGER_LABELS[fingers]} long press"
            add(key to name)
        }
        // Flick: 1–4 fingers × 4 directions
        for (fingers in 1..4) {
            for (dir in DIRECTIONS) {
                val key = "flick_${fingers}_${dir}"
                val name = "${FINGER_LABELS[fingers]} flick $dir"
                add(key to name)
            }
        }
    }

    /** Returns the storage key for this gesture, or null if it's not mappable (Pan/Pinch). */
    fun GestureEvent.toKey(): String? = when (this) {
        is GestureEvent.Tap -> "tap_${fingerCount}_${tapCount}"
        is GestureEvent.LongPress -> "longpress_${fingerCount}"
        is GestureEvent.Flick -> "flick_${fingerCount}_${direction.name.lowercase()}"
        else -> null
    }
}
