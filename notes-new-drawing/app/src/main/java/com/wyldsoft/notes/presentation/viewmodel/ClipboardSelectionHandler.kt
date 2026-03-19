package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.RectF
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.ActionUtils
import com.wyldsoft.notes.actions.ConvertToTextAction
import com.wyldsoft.notes.actions.DrawAction
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.SelectionManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.viewport.ViewportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles clipboard (copy/paste) and selection-level operations (convert to text).
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class ClipboardSelectionHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val getActionManager: () -> ActionManager,
    private val getCurrentNote: () -> Note,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val selectionManager: SelectionManager,
    private val viewportManager: ViewportManager,
    private val onScreenRefreshNeeded: () -> Unit,
    private val onUpdateContentBounds: () -> Unit,
    private val onNotifySelectionChanged: () -> Unit,
    private val onSwitchToSelectMode: () -> Unit,
    private val htrRunManager: HTRRunManager? = null
) {
    private val _copiedShapes = MutableStateFlow<List<Shape>>(emptyList())
    val copiedShapes: StateFlow<List<Shape>> = _copiedShapes.asStateFlow()

    private val _isConvertingToText = MutableStateFlow(false)
    val isConvertingToText: StateFlow<Boolean> = _isConvertingToText.asStateFlow()

    fun copySelection() {
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return
        _copiedShapes.value = getCurrentNote().shapes.filter { it.id in selectedIds }
    }

    fun pasteSelection() {
        val shapes = _copiedShapes.value
        if (shapes.isEmpty()) return

        scope.launch {
            val sm = getShapesManager() ?: return@launch
            val bm = getBitmapManager() ?: return@launch
            val note = getCurrentNote()

            val allPoints = shapes.flatMap { it.points }
            if (allPoints.isEmpty()) return@launch

            val copyBox = RectF(allPoints[0].x, allPoints[0].y, allPoints[0].x, allPoints[0].y)
            allPoints.drop(1).forEach { copyBox.union(it.x, it.y) }
            val copyCenterX = copyBox.centerX()
            val copyCenterY = copyBox.centerY()

            val screenCenter = viewportManager.surfaceToNoteCoordinates(
                viewportManager.viewWidth / 2f, viewportManager.viewHeight / 2f
            )
            val dx = screenCenter.x - copyCenterX
            val dy = screenCenter.y - copyCenterY

            val newShapes = shapes.map { shape ->
                shape.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    points = shape.points.map { pt -> android.graphics.PointF(pt.x + dx, pt.y + dy) }
                )
            }

            for (newShape in newShapes) {
                ActionUtils.addShapeToNoteAndMemory(note.id, newShape, noteRepository, sm)
                getActionManager().recordAction(DrawAction(note.id, newShape, noteRepository, sm))
            }

            val newPoints = newShapes.flatMap { it.points }
            val newBox = RectF(newPoints[0].x, newPoints[0].y, newPoints[0].x, newPoints[0].y)
            newPoints.drop(1).forEach { newBox.union(it.x, it.y) }

            selectionManager.clearSelection()
            selectionManager.setSelection(newShapes.map { it.id }.toSet(), newBox)
            onNotifySelectionChanged()

            bm.recreateBitmapFromShapes(sm.shapes())
            onScreenRefreshNeeded()
            onUpdateContentBounds()
            onSwitchToSelectMode()
        }
    }

    fun convertSelectionToText() {
        val selectedIds = selectionManager.selectedShapeIds
        if (selectedIds.isEmpty()) return
        val boundingBox = selectionManager.selectionBoundingBox ?: return
        val note = getCurrentNote()
        val selectedShapes = note.shapes.filter { it.id in selectedIds }
        if (selectedShapes.isEmpty()) return
        val htr = htrRunManager ?: return

        _isConvertingToText.value = true
        scope.launch {
            try {
                val text = htr.recognizeShapesDirectly(note.id, selectedShapes) ?: return@launch
                val sm = getShapesManager() ?: return@launch
                val bm = getBitmapManager() ?: return@launch

                for (shape in selectedShapes) {
                    ActionUtils.removeShapeFromNoteAndMemory(note.id, shape, noteRepository, sm)
                }

                val textShape = Shape(
                    type = ShapeType.TEXT,
                    points = listOf(android.graphics.PointF(boundingBox.left, boundingBox.top)),
                    strokeWidth = 2f,
                    strokeColor = android.graphics.Color.BLACK,
                    text = text,
                    fontSize = 24f,
                    fontFamily = "sans-serif"
                )
                ActionUtils.addShapeToNoteAndMemory(note.id, textShape, noteRepository, sm)
                getActionManager().recordAction(ConvertToTextAction(note.id, selectedShapes, textShape, noteRepository, sm))
                bm.recreateBitmapFromShapes(sm.shapes())
                selectionManager.clearSelection()
                onNotifySelectionChanged()
                onScreenRefreshNeeded()
                onUpdateContentBounds()
            } finally {
                _isConvertingToText.value = false
            }
        }
    }
}
