package com.wyldsoft.notes.shapemanagement

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.shapemanagement.shapes.BaseShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import com.wyldsoft.notes.utils.touchPointListToPoints
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
    private var lastDragPoint: PointF? = null
    private var accumulatedDragDx: Float = 0f
    private var accumulatedDragDy: Float = 0f

    // Transform state
    var transformMode = TransformMode.NONE
        private set
    private var scaleAnchorCornerIndex: Int = -1
    private var scaleStartDistance: Float = 0f
    private var lastScaleDistance: Float = 0f
    private var rotationStartAngle: Float = 0f
    private var lastRotationAngle: Float = 0f

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
        lassoPoints.addAll(touchPointListToPoints(touchPointList))
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
     * A shape is "inside" the lasso if ALL of its points are inside the polygon.
     */
    private fun isShapeInsideLasso(shape: BaseShape): Boolean {
        val points = shape.touchPointList ?: return false
        if (points.size() == 0) return false
        for (i in 0 until points.size()) {
            val tp = points.get(i)
            if (!pointInPolygon(tp.x, tp.y, lassoPoints)) {
                return false
            }
        }
        return true
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
        shapes.forEach { it.updateShapeRect() }
        return calculateShapesBoundingBox(shapes)
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
        lastDragPoint = startPoint
        accumulatedDragDx = 0f
        accumulatedDragDy = 0f
        Log.d(TAG, "Drag started at $startPoint")
    }

    /**
     * Incrementally move selected shapes during drag (called on each move event).
     */
    fun updateDrag(currentPoint: PointF, allShapes: List<BaseShape>) {
        val last = lastDragPoint ?: return
        val dx = currentPoint.x - last.x
        val dy = currentPoint.y - last.y
        if (Math.abs(dx) < 1f && Math.abs(dy) < 1f) return

        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                moveShapeTouchPoints(shape, dx, dy)
            }
        }
        selectionBoundingBox?.offset(dx, dy)

        lastDragPoint = currentPoint
        accumulatedDragDx += dx
        accumulatedDragDy += dy
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
        if (endPointList.size() == 0) {
            dragStartPoint = null
            lastDragPoint = null
            // Return accumulated delta if incremental updates were applied
            val totalDx = accumulatedDragDx
            val totalDy = accumulatedDragDy
            accumulatedDragDx = 0f
            accumulatedDragDy = 0f
            return if (Math.abs(totalDx) < 2f && Math.abs(totalDy) < 2f) null
                   else PointF(totalDx, totalDy)
        }

        // Use the last point as the end
        val lastPt = endPointList.get(endPointList.size() - 1)
        val totalDx = lastPt.x - start.x
        val totalDy = lastPt.y - start.y
        dragStartPoint = null
        lastDragPoint = null

        if (Math.abs(totalDx) < 2f && Math.abs(totalDy) < 2f) {
            accumulatedDragDx = 0f
            accumulatedDragDy = 0f
            return null
        }

        // Apply only the remaining delta not covered by incremental updates
        val remainingDx = totalDx - accumulatedDragDx
        val remainingDy = totalDy - accumulatedDragDy
        if (Math.abs(remainingDx) > 0.5f || Math.abs(remainingDy) > 0.5f) {
            for (shape in allShapes) {
                if (shape.id in selectedShapeIds) {
                    moveShapeTouchPoints(shape, remainingDx, remainingDy)
                }
            }
            selectionBoundingBox?.offset(remainingDx, remainingDy)
        }

        accumulatedDragDx = 0f
        accumulatedDragDy = 0f
        Log.d(TAG, "Drag finished: dx=$totalDx, dy=$totalDy")
        return PointF(totalDx, totalDy)
    }

    private fun transformShapeTouchPoints(shape: BaseShape, transform: (TouchPoint) -> TouchPoint) {
        val oldList = shape.touchPointList ?: return
        val newList = TouchPointList()
        for (i in 0 until oldList.size()) {
            newList.add(transform(oldList.get(i)))
        }
        shape.touchPointList = newList
        shape.updateShapeRect()
    }

    private fun moveShapeTouchPoints(shape: BaseShape, dx: Float, dy: Float) {
        transformShapeTouchPoints(shape) { tp ->
            TouchPoint(tp.x + dx, tp.y + dy, tp.pressure, tp.size, tp.timestamp)
        }
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
        lastScaleDistance = scaleStartDistance
        Log.d(TAG, "Scale started from corner $cornerIndex")
    }

    /**
     * Incrementally scale selected shapes during scale gesture (called on each move event).
     */
    fun updateScale(currentPoint: PointF, allShapes: List<BaseShape>) {
        val box = selectionBoundingBox ?: return
        if (lastScaleDistance < 1f) return
        val centerX = box.centerX()
        val centerY = box.centerY()
        val currentDistance = hypot(currentPoint.x - centerX, currentPoint.y - centerY)
        val incrementalFactor = currentDistance / lastScaleDistance
        if (Math.abs(incrementalFactor - 1f) < 0.005f) return

        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                scaleShapeTouchPoints(shape, incrementalFactor, centerX, centerY)
            }
        }
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })
        lastScaleDistance = currentDistance
    }

    /**
     * Apply proportional scale to all selected shapes around bounding box center.
     * Returns the scale factor applied, or null if no meaningful scale.
     */
    fun finishScale(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        if (scaleStartDistance < 1f) return null
        if (endPointList.size() == 0) {
            // Compute total factor from incremental updates
            val totalFactor = if (lastScaleDistance > 0f) lastScaleDistance / scaleStartDistance else 1f
            return if (Math.abs(totalFactor - 1f) < 0.01f) null else totalFactor
        }

        val box = selectionBoundingBox ?: return null
        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = box.centerX()
        val centerY = box.centerY()
        val endDistance = hypot(lastPt.x - centerX, lastPt.y - centerY)

        // Apply remaining incremental scale
        if (lastScaleDistance > 0f) {
            val remainingFactor = endDistance / lastScaleDistance
            if (Math.abs(remainingFactor - 1f) > 0.005f) {
                for (shape in allShapes) {
                    if (shape.id in selectedShapeIds) {
                        scaleShapeTouchPoints(shape, remainingFactor, centerX, centerY)
                    }
                }
            }
        }

        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })

        // Total scale factor for persistence/undo
        val totalFactor = endDistance / scaleStartDistance
        Log.d(TAG, "Scale finished: factor=$totalFactor")
        return if (Math.abs(totalFactor - 1f) < 0.01f) null else totalFactor
    }

    private fun scaleShapeTouchPoints(shape: BaseShape, scaleFactor: Float, centerX: Float, centerY: Float) {
        transformShapeTouchPoints(shape) { tp ->
            TouchPoint(
                centerX + (tp.x - centerX) * scaleFactor,
                centerY + (tp.y - centerY) * scaleFactor,
                tp.pressure, tp.size, tp.timestamp
            )
        }
    }

    // --- Rotate Phase ---

    fun beginRotate(startPoint: PointF) {
        val box = selectionBoundingBox ?: return
        transformMode = TransformMode.ROTATE
        val centerX = box.centerX()
        val centerY = box.centerY()
        rotationStartAngle = atan2(startPoint.y - centerY, startPoint.x - centerX)
        lastRotationAngle = rotationStartAngle
        Log.d(TAG, "Rotate started")
    }

    /**
     * Incrementally rotate selected shapes during rotation gesture (called on each move event).
     */
    fun updateRotate(currentPoint: PointF, allShapes: List<BaseShape>) {
        val box = selectionBoundingBox ?: return
        val centerX = box.centerX()
        val centerY = box.centerY()
        val currentAngle = atan2(currentPoint.y - centerY, currentPoint.x - centerX)
        val incrementalAngle = currentAngle - lastRotationAngle
        if (Math.abs(incrementalAngle) < 0.005f) return

        for (shape in allShapes) {
            if (shape.id in selectedShapeIds) {
                rotateShapeTouchPoints(shape, incrementalAngle, centerX, centerY)
            }
        }
        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })
        lastRotationAngle = currentAngle
    }

    /**
     * Apply rotation to all selected shapes around bounding box center.
     * Returns the angle in radians applied, or null if no meaningful rotation.
     */
    fun finishRotate(endPointList: TouchPointList, allShapes: List<BaseShape>): Float? {
        transformMode = TransformMode.NONE
        if (endPointList.size() == 0) {
            val totalAngle = lastRotationAngle - rotationStartAngle
            return if (Math.abs(totalAngle) < 0.01f) null else totalAngle
        }

        val box = selectionBoundingBox ?: return null
        val lastPt = endPointList.get(endPointList.size() - 1)
        val centerX = box.centerX()
        val centerY = box.centerY()
        val endAngle = atan2(lastPt.y - centerY, lastPt.x - centerX)

        // Apply remaining incremental rotation
        val remainingAngle = endAngle - lastRotationAngle
        if (Math.abs(remainingAngle) > 0.005f) {
            for (shape in allShapes) {
                if (shape.id in selectedShapeIds) {
                    rotateShapeTouchPoints(shape, remainingAngle, centerX, centerY)
                }
            }
        }

        selectionBoundingBox = calculateBoundingBox(allShapes.filter { it.id in selectedShapeIds })

        // Total angle for persistence/undo
        val totalAngle = endAngle - rotationStartAngle
        Log.d(TAG, "Rotate finished: angle=${Math.toDegrees(totalAngle.toDouble())} deg")
        return if (Math.abs(totalAngle) < 0.01f) null else totalAngle
    }

    private fun rotateShapeTouchPoints(shape: BaseShape, angleRad: Float, centerX: Float, centerY: Float) {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        transformShapeTouchPoints(shape) { tp ->
            val dx = tp.x - centerX
            val dy = tp.y - centerY
            TouchPoint(
                centerX + dx * cosA - dy * sinA,
                centerY + dx * sinA + dy * cosA,
                tp.pressure, tp.size, tp.timestamp
            )
        }
    }

    // --- Cancel / Clear ---

    fun clearSelection() {
        lassoPoints.clear()
        isLassoInProgress = false
        selectedShapeIds = emptySet()
        selectionBoundingBox = null
        isDragging = false
        dragStartPoint = null
        lastDragPoint = null
        accumulatedDragDx = 0f
        accumulatedDragDy = 0f
        transformMode = TransformMode.NONE
        scaleAnchorCornerIndex = -1
        scaleStartDistance = 0f
        lastScaleDistance = 0f
        rotationStartAngle = 0f
        lastRotationAngle = 0f
        Log.d(TAG, "Selection cleared")
    }

    /** Get current lasso points for rendering. */
    fun getLassoPoints(): List<PointF> = lassoPoints.toList()
}
