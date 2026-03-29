package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Point
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import kotlin.math.max

/**
 * Centralizes all viewport state (scroll + scale) and coordinate transformations
 * between viewport (screen) space and note (infinite canvas) space.
 *
 * Coordinate systems:
 *   - Viewport: (0,0) at screen top-left, sized to SurfaceView dimensions.
 *   - Note: infinite canvas; shapes are stored in this space.
 *
 * Transforms:
 *   viewportToNote:  noteX = viewportX / scale + scrollX
 *   noteToViewport:  viewportX = (noteX - scrollX) * scale
 */

class ViewportManager {
    private val TAG = "ViewportManager"
    var scrollX = 0f
        private set
    var scrollY = 0f
        private set
    var scale = 1f
        private set

    val MAX_SCALE_FACTOR = 4f
    val MIN_SCALE_FACTOR = 0.5f

    // --- Coordinate conversion: single points ---

    fun viewportToNoteX(vx: Float): Float = vx / scale + scrollX
    fun viewportToNoteY(vy: Float): Float = vy / scale + scrollY

    fun noteToViewportX(nx: Float): Float = (nx - scrollX) * scale
    fun noteToViewportY(ny: Float): Float = (ny - scrollY) * scale

    // --- Coordinate conversion: rects ---

    fun noteToViewport(rect: RectF): RectF {
        return RectF(
            noteToViewportX(rect.left),
            noteToViewportY(rect.top),
            noteToViewportX(rect.right),
            noteToViewportY(rect.bottom)
        )
    }

    fun viewportToNote(rect: RectF): RectF {
        return RectF(
            viewportToNoteX(rect.left),
            viewportToNoteY(rect.top),
            viewportToNoteX(rect.right),
            viewportToNoteY(rect.bottom)
        )
    }

    // --- Coordinate conversion: TouchPointList ---

    /**
     * Convert a TouchPointList from viewport coords to note coords.
     * Creates new TouchPoint objects because the SDK's translateAllPoints()
     * only does additive translation and cannot handle scaling.
     */
    fun viewportToNoteTouchPoints(viewportPoints: TouchPointList): TouchPointList {
        Log.d(TAG, "viewportToNoteTouchPoints")
        val notePoints = TouchPointList()
        for (tp in viewportPoints.points) {
            val noteTp = TouchPoint(
                viewportToNoteX(tp.x),
                viewportToNoteY(tp.y),
                tp.pressure,
                tp.size,
                tp.tiltX,
                tp.tiltY,
                tp.timestamp
            )
            notePoints.add(noteTp)
        }
        return notePoints
    }

    // --- Canvas transform ---
    /**
     * Apply the full viewport transform to a canvas for rendering note-coord shapes.
     * Call canvas.save() before and canvas.restore() after rendering.
     */
    fun applyToCanvas(canvas: Canvas) {
        Log.d(TAG, "applyToCanvas scale=$scale scrollX=$scrollX scrollY=$scrollY")
        canvas.scale(scale, scale)
        canvas.translate(-scrollX, -scrollY)
    }

    /**
     * Get the viewPoint for RenderContext, used by CharcoalScribbleShape
     * via RenderingUtils.getPointMatrix().
     */
    fun getViewPoint(): Point {
        return Point((-scrollX * scale).toInt(), (-scrollY * scale).toInt())
    }

    // --- Gesture handlers ---
    /**
     * Handle a pan (scroll) gesture. Divides deltas by scale so panning
     * feels consistent regardless of zoom level.
     */
    fun handlePanMove(deltaX: Float, deltaY: Float) {
        scrollX = max(0f, scrollX - deltaX / scale)
        scrollY = max(0f, scrollY - deltaY / scale)
        Log.d(TAG, "handlePanMove scrollX=$scrollX scrollY=$scrollY")
    }

    /**
     * Handle a pinch-to-zoom gesture. Zooms around the pinch center so that
     * the note-space point under the pinch center stays visually fixed.
     */
    fun handlePinchMove(centerX: Float, centerY: Float, scaleFactor: Float) {
        // Note-space point under the pinch center BEFORE scale change
        val anchorNoteX = centerX / scale + scrollX
        val anchorNoteY = centerY / scale + scrollY

        // Apply scale change, clamped to reasonable range
        val newScale = (scale * scaleFactor).coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)

        // Adjust scroll so the anchor stays at (centerX, centerY) on screen
        scrollX = max(0f, anchorNoteX - centerX / newScale)
        scrollY = max(0f, anchorNoteY - centerY / newScale)

        scale = newScale
        Log.d(TAG, "handlePinchMove scale=$scale scrollX=$scrollX scrollY=$scrollY")
    }
}