package com.wyldsoft.notes.viewport

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import android.util.Log

/**
 * Manages the viewport transformation between NoteCoordinates and SurfaceViewCoordinates.
 *
 * Coordinate Systems:
 * - NoteCoordinates: The absolute position in the note (stored with shapes)
 * - SurfaceViewCoordinates: The position on the screen surface where drawing occurs
 *
 * The transformation is: SurfaceViewCoords = (NoteCoords * scale) + offset
 */
class ViewportManager {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
        private const val TOP_LIMIT = 0f // Top limit in NoteCoordinates
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
    
    /**
     * Constrains offsetX based on pagination mode and scale.
     * - Pagination enabled + scale < 1.0: center the page horizontally.
     * - Otherwise: prevent scrolling left of the note's left edge (offsetX >= 0).
     */
    private fun constrainOffsetX(offsetX: Float, scale: Float): Float {
        return if (isPaginationEnabled && scale < 1.0f && viewWidth > 0 && pageWidth > 0f) {
            (viewWidth - pageWidth * scale) / 2f
        } else {
            max(offsetX, 0f)
        }
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

            // Adjust offset to keep the focus point stationary
            val newOffsetX = constrainOffsetX(focusX - (notePoint.x * newScale), newScale)
            val newOffsetY = min(focusY - (notePoint.y * newScale), TOP_LIMIT * newScale)

            _viewportState.value = currentState.copy(
                scale = newScale,
                offsetX = newOffsetX,
                offsetY = newOffsetY
            )

            updateMatrices()
        }
    }
    
    /**
     * Updates the viewport offset (pan/scroll).
     *
     * @param deltaX The horizontal distance to pan in SurfaceViewCoordinates
     * @param deltaY The vertical distance to pan in SurfaceViewCoordinates
     */
    fun updateOffset(deltaX: Float, deltaY: Float) {
        Log.d("ViewportManager", "updateOffset: deltaX=$deltaX, deltaY=$deltaY")
        val currentState = _viewportState.value
        val newOffsetX = constrainOffsetX(currentState.offsetX + deltaX, currentState.scale)
        val newOffsetY = min(currentState.offsetY + deltaY, TOP_LIMIT * currentState.scale)

        _viewportState.value = currentState.copy(
            offsetX = newOffsetX,
            offsetY = newOffsetY
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

        // Keep current vertical scroll position in NoteCoordinates, then recalculate for scale=1.0
        val currentScrollY = -currentState.offsetY / currentState.scale

        val newOffsetX = if (isPaginationEnabled && pageWidth > 0 && viewWidth > 0) {
            // Center the page horizontally: offset so that the page center aligns with screen center
            (viewWidth - pageWidth) / 2f
        } else {
            0f
        }

        val newOffsetY = min(-currentScrollY, TOP_LIMIT)

        _viewportState.value = ViewportState(
            scale = 1.0f,
            offsetX = newOffsetX,
            offsetY = newOffsetY
        )
        updateMatrices()
        Log.d("ViewportManager", "resetZoomAndCenter: pagination=$isPaginationEnabled, pageWidth=$pageWidth, newOffsetX=$newOffsetX, newOffsetY=$newOffsetY")
    }
    
    /**
     * Sets the viewport state (used for persistence/restoration).
     */
    fun setState(scale: Float, offsetX: Float, offsetY: Float) {
        Log.d("ViewportManager", "setState called with: scale=$scale, offsetX=$offsetX, offsetY=$offsetY")
        val clampedScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _viewportState.value = ViewportState(
            scale = clampedScale,
            offsetX = constrainOffsetX(offsetX, clampedScale),
            offsetY = min(offsetY, TOP_LIMIT * clampedScale)
        )
        updateMatrices()
    }
    
    /**
     * Updates the transformation matrices based on current state.
     */
    private fun updateMatrices() {
        val state = _viewportState.value
        
        // Create forward transformation matrix
        transformMatrix.reset()
        transformMatrix.postScale(state.scale, state.scale)
        transformMatrix.postTranslate(state.offsetX, state.offsetY)
        
        // Create inverse transformation matrix
        transformMatrix.invert(inverseMatrix)
    }
    
    /**
     * Gets the current scroll position in NoteCoordinates.
     * This represents the top-left corner of the viewport in note space.
     */
    fun getScrollPosition(): PointF {
        val state = _viewportState.value
        return PointF(
            -state.offsetX / state.scale,
            -state.offsetY / state.scale
        )
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
 */
data class ViewportState(
    val scale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)