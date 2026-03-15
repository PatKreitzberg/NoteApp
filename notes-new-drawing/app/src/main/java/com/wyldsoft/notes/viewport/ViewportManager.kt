package com.wyldsoft.notes.viewport

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import android.util.Log

/**
 * Manages the viewport transformation between NoteCoordinates and SurfaceViewCoordinates.
 *
 * Coordinate Systems:
 * - NoteCoordinates: The absolute position in the note (stored with shapes).
 *   Origin (0,0) is the top-left of the canvas; x increases rightward, y increases downward.
 * - SurfaceViewCoordinates: The position on the screen surface where drawing occurs.
 *
 * ViewportState stores scrollX/scrollY as positive NoteCoordinate values representing
 * how far the viewport has been scrolled from the origin:
 *   scrollX = 0 means the left edge of the note is at the left of the screen
 *   scrollY = 0 means the top of the note is at the top of the screen
 *
 * The transformation is: SurfaceCoords = (NoteCoords - scroll) * scale
 */
class ViewportManager {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
    }

    // Current viewport state
    private val _viewportState = MutableStateFlow(ViewportState())
    val viewportState: StateFlow<ViewportState> = _viewportState.asStateFlow()

    // Transformation matrix (updated whenever viewport changes)
    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    // View dimensions (set by the activity)
    var viewWidth = 0
    var viewHeight = 0

    // Pagination state (updated from EditorViewModel)
    var isPaginationEnabled: Boolean = false
    var pageWidth: Float = 0f
    var pageHeight: Float = 0f

    // When > 0, the scroll is bounded to exactly this many pages (PDF-backed notes).
    var pdfPageCount: Int = 0

    // Content bounds: the maximum Y coordinate across all shapes (updated externally)
    var contentMaxY: Float = 0f

    /**
     * Constrains scrollX based on pagination mode and scale.
     * - Pagination enabled + scale < 1.0: center the page horizontally.
     * - Otherwise: prevent scrolling left of the note's left edge (scrollX >= 0).
     */
    private fun constrainScrollX(scrollX: Float, scale: Float): Float {
        Log.d("ViewportManager", "constrainScrollX: scrollX=$scrollX, scale=$scale, pagination=$isPaginationEnabled, viewWidth=$viewWidth, pageWidth=$pageWidth")
        return if (isPaginationEnabled && scale < 1.0f && viewWidth > 0 && pageWidth > 0f) {
            // Center the page: scrollX such that the page center aligns with screen center.
            // SurfaceX = (NoteX - scrollX) * scale; for page center (pageWidth/2) to be at
            // screen center (viewWidth/2): viewWidth/2 = (pageWidth/2 - scrollX) * scale
            // scrollX = pageWidth/2 - viewWidth/(2*scale)
            pageWidth / 2f - viewWidth / (2f * scale)
        } else {
            max(0f, scrollX)
        }
    }

    /**
     * Constrains scrollY:
     * - Minimum: 0 (can't scroll above top of note)
     * - Maximum: depends on pagination mode and content bounds
     *   - Pagination ON: can scroll to the first completely empty page after content
     *   - Pagination OFF: can scroll until all content is above the viewport (screen is empty)
     */
    private fun constrainScrollY(scrollY: Float): Float {
        val scale = _viewportState.value.scale
        val maxScrollY = computeMaxScrollY(scale)
        return scrollY.coerceIn(0f, maxScrollY)
    }

    private fun computeMaxScrollY(scale: Float): Float {
        if (isPaginationEnabled && pageHeight > 0f) {
            if (pdfPageCount > 0) {
                // PDF note: scroll bounded to exactly pdfPageCount pages
                return pdfPageCount * pageHeight
            }
            if (contentMaxY <= 0f) return Float.MAX_VALUE
            // Regular paginated note: allow one empty page beyond content
            val contentPage = (contentMaxY / pageHeight).toInt()
            return (contentPage + 1) * pageHeight
        }
        if (contentMaxY <= 0f) return Float.MAX_VALUE
        return contentMaxY
    }

    /**
     * Updates the viewport scale (zoom).
     *
     * @param scaleFactor The scale factor to apply (multiplicative)
     * @param focusX The X coordinate in SurfaceViewCoordinates to zoom around
     * @param focusY The Y coordinate in SurfaceViewCoordinates to zoom around
     */
    fun updateScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        Log.d("ViewportManager", "updateScale: scaleFactor=$scaleFactor, focusX=$focusX, focusY=$focusY")
        val currentState = _viewportState.value
        val newScale = (currentState.scale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)

        if (newScale != currentState.scale) {
            // Convert focus point to NoteCoordinates
            val notePoint = surfaceToNoteCoordinates(focusX, focusY)

            // Keep the focus note-point at the same screen position after rescaling:
            // focusX = (notePoint.x - newScrollX) * newScale => newScrollX = notePoint.x - focusX/newScale
            val newScrollX = constrainScrollX(notePoint.x - focusX / newScale, newScale)
            val newScrollY = constrainScrollY(notePoint.y - focusY / newScale)

            Log.d("ViewportManager", "updateScale: newScale=$newScale, notePoint=(${notePoint.x}, ${notePoint.y}), newScrollX=$newScrollX, newScrollY=$newScrollY")
            _viewportState.value = currentState.copy(
                scale = newScale,
                scrollX = newScrollX,
                scrollY = newScrollY
            )

            updateMatrices()
        }
    }

    /**
     * Updates the viewport offset (pan/scroll).
     *
     * @param deltaX The horizontal pixel distance the finger moved (positive = finger moved right)
     * @param deltaY The vertical pixel distance the finger moved (positive = finger moved down)
     */
    fun updateOffset(deltaX: Float, deltaY: Float) {

        val currentState = _viewportState.value
        // Dragging finger right (deltaX > 0) moves content right → scrollX decreases.
        val newScrollX = constrainScrollX(currentState.scrollX - deltaX / currentState.scale, currentState.scale)
        val newScrollY = constrainScrollY(currentState.scrollY - deltaY / currentState.scale)
        Log.d("ViewportManager", "updateOffset: offset (${newScrollX}, ${newScrollY} deltaX=$deltaX, deltaY=$deltaY")
        _viewportState.value = currentState.copy(
            scrollX = newScrollX,
            scrollY = newScrollY
        )

        updateMatrices()
    }

    /**
     * Converts a point from SurfaceViewCoordinates to NoteCoordinates.
     * Used when creating new shapes from touch input.
     */
    fun surfaceToNoteCoordinates(surfaceX: Float, surfaceY: Float): PointF {
        val points = floatArrayOf(surfaceX, surfaceY)
        inverseMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    /**
     * Converts a point from NoteCoordinates to SurfaceViewCoordinates.
     * Used for hit testing and UI feedback.
     */
    fun noteToSurfaceCoordinates(noteX: Float, noteY: Float): PointF {
        val points = floatArrayOf(noteX, noteY)
        transformMatrix.mapPoints(points)
        return PointF(points[0], points[1])
    }

    /**
     * Gets the current transformation matrix for rendering.
     * This matrix transforms from NoteCoordinates to SurfaceViewCoordinates.
     */
    fun getTransformMatrix(): Matrix = Matrix(transformMatrix)

    /**
     * Resets the viewport to default state (no zoom, no scroll).
     */
    fun reset() {
        _viewportState.value = ViewportState()
        updateMatrices()
    }

    /**
     * Resets zoom to 100% while keeping the current vertical scroll position.
     * If pagination is enabled, also centers the page horizontally on screen.
     *
     * @param isPaginationEnabled Whether pagination mode is active
     * @param pageWidth The page width in NoteCoordinates (typically equals screenWidth)
     */
    fun resetZoomAndCenter(isPaginationEnabled: Boolean, pageWidth: Float = 0f) {
        val currentState = _viewportState.value

        val newScrollX = if (isPaginationEnabled && pageWidth > 0 && viewWidth > 0) {
            // Center the page at scale=1: scrollX = pageWidth/2 - viewWidth/2
            pageWidth / 2f - viewWidth / 2f
        } else {
            0f
        }

        // Preserve vertical scroll position, clamped to >= 0
        val newScrollY = constrainScrollY(currentState.scrollY)

        _viewportState.value = ViewportState(
            scale = 1.0f,
            scrollX = newScrollX,
            scrollY = newScrollY
        )
        updateMatrices()
        Log.d("ViewportManager", "resetZoomAndCenter: pagination=$isPaginationEnabled, pageWidth=$pageWidth, newScrollX=$newScrollX, newScrollY=$newScrollY")
    }

    /**
     * Directly sets the vertical scroll position in NoteCoordinates.
     * Used by the scroll bar drag gesture.
     */
    fun setScrollY(newScrollY: Float) {
        val currentState = _viewportState.value
        _viewportState.value = currentState.copy(scrollY = constrainScrollY(newScrollY))
        updateMatrices()
    }

    /**
     * Sets the viewport state (used for persistence/restoration).
     * scrollX and scrollY are positive NoteCoordinate values (distance scrolled from origin).
     */
    fun setState(scale: Float, scrollX: Float, scrollY: Float) {
        Log.d("ViewportManager", "setState called with: scale=$scale, scrollX=$scrollX, scrollY=$scrollY")
        val clampedScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _viewportState.value = ViewportState(
            scale = clampedScale,
            scrollX = constrainScrollX(scrollX, clampedScale),
            scrollY = constrainScrollY(scrollY)
        )
        updateMatrices()
    }

    /**
     * Updates the transformation matrices based on current state.
     * SurfaceCoords = (NoteCoords - scroll) * scale
     */
    private fun updateMatrices() {
        val state = _viewportState.value

        transformMatrix.reset()
        transformMatrix.postScale(state.scale, state.scale)
        transformMatrix.postTranslate(-state.scrollX * state.scale, -state.scrollY * state.scale)

        // Create inverse transformation matrix
        transformMatrix.invert(inverseMatrix)
    }

    /**
     * Gets the current scroll position in NoteCoordinates.
     * This represents the top-left corner of the viewport in note space.
     */
    fun getScrollPosition(): PointF {
        val state = _viewportState.value
        return PointF(state.scrollX, state.scrollY)
    }

    /**
     * Returns the visible bounds in note coordinates for the given canvas size.
     */
    fun getVisibleBounds(canvasWidth: Float, canvasHeight: Float): RectF {
        val topLeft = surfaceToNoteCoordinates(0f, 0f)
        val bottomRight = surfaceToNoteCoordinates(canvasWidth, canvasHeight)
        return RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    /**
     * Gets the current zoom percentage (100% = 1.0 scale).
     */
    fun getZoomPercentage(): Int = (_viewportState.value.scale * 100).toInt()

}

/**
 * Represents the current state of the viewport.
 *
 * scrollX and scrollY are the scroll position in NoteCoordinates (always >= 0 normally).
 * scrollX = 0 means the left edge of the note is at the left of the screen.
 * scrollY = 0 means the top of the note is at the top of the screen.
 * Increasing scrollX/scrollY means you have scrolled further right/down into the note.
 */
data class ViewportState(
    val scale: Float = 1.0f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f
)
