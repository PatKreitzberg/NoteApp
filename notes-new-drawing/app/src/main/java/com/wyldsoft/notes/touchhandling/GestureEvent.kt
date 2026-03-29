package com.wyldsoft.notes.touchhandling
//
///**
// * Sealed class hierarchy representing all recognized finger gestures.
// * Each subclass carries the finger count and gesture-specific data.
// * displayName() returns a human-readable label for logging and on-screen display.
// */
//sealed class GestureEvent(val fingerCount: Int) {
//
//    class Tap(fingerCount: Int, val tapCount: Int) : GestureEvent(fingerCount)
//
//    class LongPress(fingerCount: Int) : GestureEvent(fingerCount)
//
//    class Flick(fingerCount: Int, val direction: Direction) : GestureEvent(fingerCount)
//
//    class PanStart(fingerCount: Int) : GestureEvent(fingerCount)
//    class PanMove(fingerCount: Int, val deltaX: Float, val deltaY: Float) : GestureEvent(fingerCount)
//    class PanEnd(fingerCount: Int) : GestureEvent(fingerCount)
//
//    class PinchStart(val centerX: Float, val centerY: Float) : GestureEvent(2)
//    class PinchMove(val centerX: Float, val centerY: Float, val scaleFactor: Float) : GestureEvent(2)
//    class PinchEnd(val centerX: Float, val centerY: Float) : GestureEvent(2)
//
//    enum class Direction { UP, DOWN, LEFT, RIGHT }
//
//    fun displayName(): String = when (this) {
//        is Tap -> "$fingerCount-finger ${tapName(tapCount)}"
//        is LongPress -> "$fingerCount-finger long press"
//        is Flick -> "$fingerCount-finger flick ${direction.name.lowercase()}"
//        is PanStart -> "$fingerCount-finger pan start"
//        is PanMove -> "$fingerCount-finger pan"
//        is PanEnd -> "$fingerCount-finger pan end"
//        is PinchStart -> "pinch start"
//        is PinchMove -> if (scaleFactor > 1f) "pinch expand" else "pinch collapse"
//        is PinchEnd -> "pinch end"
//    }
//
//    private fun tapName(count: Int) = when (count) {
//        1 -> "tap"
//        2 -> "double tap"
//        3 -> "triple tap"
//        4 -> "quadruple tap"
//        else -> "${count}x tap"
//    }
//}
