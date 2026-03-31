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

    var paginationEnabled = false

    val MAX_SCALE_FACTOR = 4f
    val MIN_SCALE_FACTOR = 0.5f

    // Snapshot of viewport state when a gesture begins.
    // Used to compute the transform for drawing the existing bitmap during gestures.
    private var snapshotScrollX = 0f
    private var snapshotScrollY = 0f
    private var snapshotScale = 1f
    private var hasSnapshot = false

    /**
     * Save a snapshot of the current viewport state at gesture start.
     * The bitmap was rendered at this state, so we compute a delta transform
     * from this snapshot to the current state for smooth live updates.
     */
    fun saveSnapshot() {
        snapshotScrollX = scrollX
        snapshotScrollY = scrollY
        snapshotScale = scale
        hasSnapshot = true
        Log.d(TAG, "saveSnapshot scrollX=$scrollX scrollY=$scrollY scale=$scale")
    }

    fun clearSnapshot() {
        hasSnapshot = false
    }

    /**
     * Apply a transform to a Canvas that maps the bitmap (rendered at snapshot state)
     * to the current viewport state. This allows drawing the existing bitmap with
     * a translate/scale during gestures for smooth visual feedback.
     *
     * Math: bitmapCoord -> noteCoord -> currentViewportCoord
     *   screenPos = bitmapPos * (newScale/oldScale) + (oldScroll - newScroll) * newScale
     */
    fun applyGestureTransformToCanvas(canvas: Canvas): Boolean {
        if (!hasSnapshot) return false
        val sx = scale / snapshotScale
        val sy = scale / snapshotScale
        val tx = (snapshotScrollX - scrollX) * scale
        val ty = (snapshotScrollY - scrollY) * scale
        canvas.translate(tx, ty)
        canvas.scale(sx, sy)
        Log.d(TAG, "applyGestureTransform sx=$sx tx=$tx ty=$ty")
        return true
    }

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

    /**
     * Convert a TouchPointList from note coords to viewport (screen) coords.
     * Inverse of viewportToNoteTouchPoints. Used when rendering shapes to the
     * offscreen bitmap so strokes are drawn at screen-space dimensions,
     * matching the Onyx SDK's real-time rendering.
     */
    fun noteToViewportTouchPoints(notePoints: TouchPointList): TouchPointList {
        Log.d(TAG, "noteToViewportTouchPoints")
        val viewportPoints = TouchPointList()
        for (tp in notePoints.points) {
            val viewportTp = TouchPoint(
                noteToViewportX(tp.x),
                noteToViewportY(tp.y),
                tp.pressure,
                tp.size,
                tp.tiltX,
                tp.tiltY,
                tp.timestamp
            )
            viewportPoints.add(viewportTp)
        }
        return viewportPoints
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
    fun resetViewport() {
        scrollX = 0f
        scrollY = 0f
        scale = 1f
        Log.d(TAG, "resetViewport")
    }

    fun handlePanMove(deltaX: Float, deltaY: Float) {
        if (!paginationEnabled) {
            scrollX = max(0f, scrollX - deltaX / scale)
        }
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
        scrollX = if (paginationEnabled) 0f else max(0f, anchorNoteX - centerX / newScale)
        scrollY = max(0f, anchorNoteY - centerY / newScale)

        scale = newScale
        Log.d(TAG, "handlePinchMove scale=$scale scrollX=$scrollX scrollY=$scrollY")
    }
}