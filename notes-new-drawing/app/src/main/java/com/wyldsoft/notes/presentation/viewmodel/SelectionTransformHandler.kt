package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.PointF
import android.util.Log
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.MoveAction
import com.wyldsoft.notes.actions.TextFormattingAction
import com.wyldsoft.notes.actions.TransformAction
import com.wyldsoft.notes.actions.TransformType
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import com.wyldsoft.notes.utils.calculateShapesBoundingBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles selection transforms (move, scale, rotate) and pen profile application.
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class SelectionTransformHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val selectionManager: SelectionManager,
    private val getActionManager: () -> ActionManager,
    private val getCurrentNote: () -> Note,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onScreenRefreshNeeded: () -> Unit
) {
    fun applyPenProfileToSelection(profile: PenProfile) {
        val sm = getShapesManager() ?: return
        val bm = getBitmapManager() ?: return
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return

        val colorInt = profile.getColorAsInt()
        val shapeType = ShapesManager.penTypeToShapeType(profile.penType)

        for (shape in sm.shapes()) {
            if (shape.id in selectedIds) {
                shape.setStrokeColor(colorInt)
                    .setStrokeWidth(profile.strokeWidth)
                    .setShapeType(shapeType)
                ShapesManager.applyCharcoalTexture(shape, profile.penType)
            }
        }

        val bbox = selectionManager.selectionBoundingBox
        if (bbox != null) {
            Log.d("RefreshDebug", "SelectionTransformHandler.applyPenProfileToSelection → partialRefresh")
            bm.partialRefresh(bbox, sm.shapes(), selectionManager, "SelectionTransformHandler.applyPenProfile")
        } else {
            Log.d("RefreshDebug", "SelectionTransformHandler.applyPenProfileToSelection → recreateBitmapFromShapes")
            bm.recreateBitmapFromShapes(sm.shapes(), caller = "SelectionTransformHandler.applyPenProfile")
            onScreenRefreshNeeded()
        }

        scope.launch {
            val note = getCurrentNote()
            for (shape in note.shapes) {
                if (shape.id in selectedIds) {
                    noteRepository.updateShape(
                        note.id,
                        shape.copy(strokeColor = colorInt, strokeWidth = profile.strokeWidth, penType = profile.penType)
                    )
                }
            }
        }
    }

    fun applyTextFormattingToSelection(fontSize: Float, fontFamily: String, color: Int) {
        val sm = getShapesManager() ?: return
        val bm = getBitmapManager() ?: return
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return

        // Capture before state for undo
        val note = getCurrentNote()
        val beforeShapes = note.shapes.filter { it.id in selectedIds && it.type == ShapeType.TEXT }

        for (shape in sm.shapes()) {
            if (shape.id in selectedIds && shape is TextShape) {
                shape.setFontSize(fontSize)
                shape.setFontFamily(fontFamily)
                shape.setStrokeColor(color)
                shape.updateShapeRect()
            }
        }

        // Recalculate bounding box to reflect new text dimensions
        val selectedShapes = sm.shapes().filter { it.id in selectedIds }
        calculateShapesBoundingBox(selectedShapes)?.let { selectionManager.setSelection(selectedIds, it) }

        val bbox = selectionManager.selectionBoundingBox
        if (bbox != null) {
            Log.d("RefreshDebug", "SelectionTransformHandler.applyTextFormatting → partialRefresh")
            bm.partialRefresh(bbox, sm.shapes(), selectionManager, "SelectionTransformHandler.applyTextFormatting")
        } else {
            Log.d("RefreshDebug", "SelectionTransformHandler.applyTextFormatting → recreateBitmapFromShapes")
            bm.recreateBitmapFromShapes(sm.shapes(), caller = "SelectionTransformHandler.applyTextFormatting")
            onScreenRefreshNeeded()
        }

        if (beforeShapes.isNotEmpty()) {
            val afterShapes = beforeShapes.map { it.copy(fontSize = fontSize, fontFamily = fontFamily, strokeColor = color) }
            getActionManager().recordAction(TextFormattingAction(note.id, beforeShapes, afterShapes, noteRepository, sm))
        }

        scope.launch {
            for (shape in note.shapes) {
                if (shape.id in selectedIds && shape.type == ShapeType.TEXT) {
                    noteRepository.updateShape(
                        note.id,
                        shape.copy(fontSize = fontSize, fontFamily = fontFamily, strokeColor = color)
                    )
                }
            }
        }
    }

    fun recordMoveAction(originalShapes: List<Shape>, dx: Float, dy: Float) {
        val sm = getShapesManager() ?: return
        getActionManager().recordAction(
            MoveAction(getCurrentNote().id, originalShapes, dx, dy, noteRepository, sm)
        )
    }

    fun persistMovedShapes(shapeIds: Set<String>, dx: Float, dy: Float) {
        scope.launch {
            val note = getCurrentNote()
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val movedPoints = shape.points.map { PointF(it.x + dx, it.y + dy) }
                    noteRepository.updateShape(note.id, shape.copy(points = movedPoints))
                }
            }
        }
    }

    fun recordTransformAction(
        originalShapes: List<Shape>,
        transformType: TransformType,
        param: Float,
        centerX: Float,
        centerY: Float
    ) {
        val sm = getShapesManager() ?: return
        getActionManager().recordAction(
            TransformAction(getCurrentNote().id, originalShapes, transformType, param, centerX, centerY, noteRepository, sm)
        )
    }

    fun persistScaledShapes(shapeIds: Set<String>, scaleFactor: Float, centerX: Float, centerY: Float) {
        scope.launch {
            val note = getCurrentNote()
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val scaledPoints = shape.points.map { pt ->
                        PointF(centerX + (pt.x - centerX) * scaleFactor, centerY + (pt.y - centerY) * scaleFactor)
                    }
                    noteRepository.updateShape(note.id, shape.copy(points = scaledPoints))
                }
            }
        }
    }

    fun persistRotatedShapes(shapeIds: Set<String>, angleRad: Float, centerX: Float, centerY: Float) {
        scope.launch {
            val note = getCurrentNote()
            val cosA = kotlin.math.cos(angleRad)
            val sinA = kotlin.math.sin(angleRad)
            for (shape in note.shapes) {
                if (shape.id in shapeIds) {
                    val rotatedPoints = shape.points.map { pt ->
                        val dx = pt.x - centerX
                        val dy = pt.y - centerY
                        PointF(centerX + dx * cosA - dy * sinA, centerY + dx * sinA + dy * cosA)
                    }
                    noteRepository.updateShape(note.id, shape.copy(points = rotatedPoints))
                }
            }
        }
    }
}
