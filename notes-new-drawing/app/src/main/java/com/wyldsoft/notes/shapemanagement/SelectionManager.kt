package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class TransformMode {
    NONE, MOVE, SCALE, ROTATE
}

data class HandlePositions(
    val corners: List<PointF>,       // 4 corners: TL, TR, BR, BL
    val rotationHandle: PointF       // circle above top-center
)

/**
 * Manages lasso selection and drag-to-move/scale/rotate of shapes.
 *
 * Phases:
 * 1. Lasso phase: user draws a freeform lasso; touch points accumulate.
 * 2. Selection phase: point-in-polygon test determines contained shapes; bounding box shown.
 * 3. Transform phase: stylus touch on handles scales/rotates, inside box moves.
 */
class SelectionManager {

    companion object {
        private const val TAG = "SelectionManager"
        private const val HANDLE_HIT_RADIUS = 30f  // px hit target for handles
        private const val ROTATION_HANDLE_OFFSET = 40f  // px above top-center
    }

    // Lasso state
    private val lassoPoints = mutableListOf<PointF>()
    var isLassoInProgress = false
        private set

    // Selection state
    var selectedShapeIds = setOf<String>()
        private set
    var selectionBoundingBox: RectF? = null
        private set

    // Move state
    var isDragging = false
        private set
    private var dragStartPoint: PointF? = null

    // Transform state
    var transformMode = TransformMode.NONE
        private set
    private var scaleAnchorCornerIndex: Int = -1
    private var scaleStartDistance: Float = 0f
    private var rotationStartAngle: Float = 0f

    /** True when shapes have been selected (after lasso completes). */
    val hasSelection: Boolean get() = selectedShapeIds.isNotEmpty()

    // --- Lasso Phase ---

    fun beginLasso() {
        lassoPoints.clear()
        isLassoInProgress = true
        Log.d(TAG, "Lasso started")
    }

    /**
     * Feed touch points from a drawing stroke into the lasso polygon.
     * Returns the lasso points for rendering the dashed line.
     */
    fun addLassoPoints(touchPointList: TouchPointList): List<PointF> {
        for (i in 0 until touchPointList.size()) {
            val tp = touchPointList.get(i)
            lassoPoints.add(PointF(tp.x, tp.y))
        }
        return lassoPoints.toList()
    }

    /**
     * Finalize lasso and select shapes whose *any* point is inside the polygon.
     * Returns the set of selected shape IDs.
     */
    fun finishLasso(allShapes: List<BaseShape>): Set<String> {
        isLassoInProgress = false
        if (lassoPoints.size < 3) {
            Log.d(TAG, "Lasso too small (${lassoPoints.size} points), clearing")
            clearSelection()
            return emptySet()
        }

        val selected = mutableSetOf<String>()
        for (shape in allShapes) {
            if (isShapeInsideLasso(shape)) {
                selected.add(shape.id)
            }
        }

        selectedShapeIds = selected
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selected })
        lassoPoints.clear()

        Log.d(TAG, "Lasso finished: ${selected.size} shapes selected")
        return selected
    }

    /**
     * A shape is "inside" the lasso if at least one of its points is inside the polygon.
     */
    private fun isShapeInsideLasso(shape: BaseShape): Boolean {
        val points = shape.touchPointList ?: return false
        for (i in 0 until points.size()) {
            val tp = points.get(i)
            if (pointInPolygon(tp.x, tp.y, lassoPoints)) {
                return true
            }
        }
        return false
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    private fun pointInPolygon(x: Float, y: Float, polygon: List<PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun calculateBoundingBox(shapes: List<BaseShape>): RectF? {
        if (shapes.isEmpty()) return null
        var rect: RectF? = null
        for (shape in shapes) {
            shape.updateShapeRect()
            val br = shape.boundingRect ?: continue
            if (rect == null) {
                rect = RectF(br)
            } else {
                rect.union(br)
            }
        }
        // Add padding
        rect?.inset(-10f, -10f)
        return rect
    }

    // --- Move Phase ---

    /**
     * Check if a note-coordinate point is inside the current selection bounding box.
     */
    fun isInsideBoundingBox(noteX: Float, noteY: Float): Boolean {
        return selectionBoundingBox?.contains(noteX, noteY) == true
    }

    fun beginDrag(startPoint: PointF) {
        isDragging = true
        dragStartPoint = startPoint
        Log.d(TAG, "Drag started at $startPoint")
    }

    /**
     * Apply move delta to all selected shapes.
     * Returns the delta (dx, dy) applied.
     */
    fun finishDrag(
        endPointList: TouchPointList,
        allShapes: List<BaseShape>
    ): PointF? {
        isDragging = false
        val start = dragStartPoint ?: return null
        if (endPointList.size() == 0) return null

        // Use the last point as the end
        val lastPt = endPointList.get(endPointList.size() - 1)
        val dx = lastPt.x - start.x
        val dy = lastPt.y - start.y
        dragStartPoint = null

        if (Math.abs(dx) < 2f && Math.abs(dy) < 2f) return null

        // Move selected shapes
        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                moveShapeTouchPoints(shape, dx, dy)
            }
        }

        // Update bounding box
        selectionBoundingBox?.offset(dx, dy)

        Log.d(TAG, "Drag finished: dx=$dx, dy=$dy")
        return PointF(dx, dy)
    }

    private fun moveShapeTouchPoints(shape: BaseShape, dx: Float, dy: Float) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            newList.add(TouchPoint(tp.x + dx, tp.y + dy, tp.pressure, tp.size, tp.timestamp))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }

    // --- Handle Detection ---

    fun getHandlePositions(): HandlePositions? {
        val box = selectionBoundingBox ?: return null
        val corners = listOf(
            PointF(box.left, box.top),       // 0: TL
            PointF(box.right, box.top),      // 1: TR
            PointF(box.right, box.bottom),   // 2: BR
            PointF(box.left, box.bottom)     // 3: BL
        )
        val topCenterX = (box.left + box.right) / 2f
        val rotationHandle = PointF(topCenterX, box.top - ROTATION_HANDLE_OFFSET)
        return HandlePositions(corners, rotationHandle)
    }

    /** Returns corner index (0-3) if point is near a corner handle, null otherwise. */
    fun isOnScaleHandle(noteX: Float, noteY: Float): Int? {
        val handles = getHandlePositions() ?: return null
        for ((i, corner) in handles.corners.withIndex()) {
            if (hypot(noteX - corner.x, noteY - corner.y) <= HANDLE_HIT_RADIUS) {
                return i
            }
        }
        return null
    }

    /** Returns true if point is near the rotation handle circle. */
    fun isOnRotationHandle(noteX: Float, noteY: Float): Boolean {
        val handles = getHandlePositions() ?: return false
        return hypot(noteX - handles.rotationHandle.x, noteY - handles.rotationHandle.y) <= HANDLE_HIT_RADIUS
    }

    // --- Scale Phase ---

    fun beginScale(cornerIndex: Int, startPoint: PointF) {
        val box = selectionBoundingBox ?: return
        transformMode = TransformMode.SCALE
        scaleAnchorCornerIndex = cornerIndex
        val centerX = box.centerX()
        val centerY = box.centerY()
        scaleStartDistance = hypot(startPoint.x - centerX, startPoint.y - centerY)
        Log.d(TAG, "Scale started from corner $cornerIndex")
    }

    /**
     * Apply proportional scale to all selected shapes around bounding box center.
     * Returns the scale factor applied, or null if no meaningful scale.
     */
    fun finishScale(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        val box = selectionBoundingBox ?: return null
        if (endPointList.size() == 0) return null

        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = box.centerX()
        val centerY = box.centerY()
        val endDistance = hypot(lastPt.x - centerX, lastPt.y - centerY)

        if (scaleStartDistance < 1f) return null
        val scaleFactor = endDistance / scaleStartDistance
        if (Math.abs(scaleFactor - 1f) < 0.01f) return null

        // Apply scale to all selected shapes
        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                scaleShapeTouchPoints(shape, scaleFactor, centerX, centerY)
            }
        }

        // Update bounding box
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })

        Log.d(TAG, "Scale finished: factor=$scaleFactor")
        return scaleFactor
    }

    private fun scaleShapeTouchPoints(shape: BaseShape, scaleFactor: Float, centerX: Float, centerY: Float) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            val newX = centerX + (tp.x - centerX) * scaleFactor
            val newY = centerY + (tp.y - centerY) * scaleFactor
            newList.add(TouchPoint(newX, newY, tp.pressure, tp.size, tp.timestamp))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }

    // --- Rotate Phase ---

    fun beginRotate(startPoint: PointF) {
        val box = selectionBoundingBox ?: return
        transformMode = TransformMode.ROTATE
        val centerX = box.centerX()
        val centerY = box.centerY()
        rotationStartAngle = atan2(startPoint.y - centerY, startPoint.x - centerX)
        Log.d(TAG, "Rotate started")
    }

    /**
     * Apply rotation to all selected shapes around bounding box center.
     * Returns the angle in radians applied, or null if no meaningful rotation.
     */
    fun finishRotate(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        val box = selectionBoundingBox ?: return null
        if (endPointList.size() == 0) return null

        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = box.centerX()
        val centerY = box.centerY()
        val endAngle = atan2(lastPt.y - centerY, lastPt.x - centerX)
        val angleRad = endAngle - rotationStartAngle

        if (Math.abs(angleRad) < 0.01f) return null

        // Apply rotation to all selected shapes
        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                rotateShapeTouchPoints(shape, angleRad, centerX, centerY)
            }
        }

        // Update bounding box
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })

        Log.d(TAG, "Rotate finished: angle=${Math.toDegrees(angleRad.toDouble())} deg")
        return angleRad
    }

    private fun rotateShapeTouchPoints(shape: BaseShape, angleRad: Float, centerX: Float, centerY: Float) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        for (i in 0 until oldList.size()) {
            val tp = oldList.get(i)
            val dx = tp.x - centerX
            val dy = tp.y - centerY
            val newX = centerX + dx * cosA - dy * sinA
            val newY = centerY + dx * sinA + dy * cosA
            newList.add(TouchPoint(newX, newY, tp.pressure, tp.size, tp.timestamp))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }

    // --- Cancel / Clear ---

    fun clearSelection() {
        lassoPoints.clear()
        isLassoInProgress = false
        selectedShapeIds = emptySet()
        selectionBoundingBox = null
        isDragging = false
        dragStartPoint = null
        transformMode = TransformMode.NONE
        scaleAnchorCornerIndex = -1
        scaleStartDistance = 0f
        rotationStartAngle = 0f
        Log.d(TAG, "Selection cleared")
    }

    /** Get current lasso points for rendering. */
    fun getLassoPoints(): List<PointF> = lassoPoints.toList()
}
