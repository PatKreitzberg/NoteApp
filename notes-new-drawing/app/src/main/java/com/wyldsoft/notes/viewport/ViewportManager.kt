package com.wyldsoft.notes.viewport

import android.graphics.Matrix
import android.graphics.PointF
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
        private const val MAX_SCALE = 5.0f
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
            
            // Update scale
            val actualScaleFactor = newScale / currentState.scale
            
            // Adjust offset to keep the focus point stationary
            val newOffsetX = focusX - (notePoint.x * newScale)
            val newOffsetY = focusY - (notePoint.y * newScale)
            
            // Apply top limit constraint
            val constrainedOffsetY = min(newOffsetY, TOP_LIMIT * newScale)
            
            _viewportState.value = currentState.copy(
                scale = newScale,
                offsetX = newOffsetX,
                offsetY = constrainedOffsetY
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
        var newOffsetX = currentState.offsetX + deltaX
        var newOffsetY = currentState.offsetY + deltaY
        
        // Apply top limit constraint (can't scroll above y=0 in NoteCoordinates)
        // When offsetY >= 0, we're at or above the top
        newOffsetY = min(newOffsetY, TOP_LIMIT * currentState.scale)
        
        // No bottom limit - infinite scroll down
        
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
     * Sets the viewport state (used for persistence/restoration).
     */
    fun setState(scale: Float, offsetX: Float, offsetY: Float) {
        Log.d("ViewportManager", "setState called with: scale=$scale, offsetX=$offsetX, offsetY=$offsetY")
        _viewportState.value = ViewportState(
            scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
            offsetX = offsetX,
            offsetY = min(offsetY, TOP_LIMIT * scale)
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