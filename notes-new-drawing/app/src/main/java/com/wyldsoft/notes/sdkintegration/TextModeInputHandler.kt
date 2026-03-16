package com.wyldsoft.notes.sdkintegration

import com.onyx.android.sdk.data.note.TouchPoint
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.shapemanagement.ShapesManager
import com.wyldsoft.notes.shapemanagement.shapes.TextShape

/**
 * Handles touch input routing for Text mode: hit-tests existing text shapes
 * to edit them, or creates new text at the tap location.
 * Parallel to [SelectionInputHandler] for selection mode.
 */
class TextModeInputHandler(
    private val viewModel: EditorViewModel,
    private val getShapesManager: () -> ShapesManager
) {
    private val shapesManager: ShapesManager get() = getShapesManager()

    /**
     * Handle a touch begin in text mode. Hit-tests existing text shapes
     * on the active layer; edits the hit shape or begins new text input.
     */
    fun handleBegin(touchPoint: TouchPoint) {
        val notePoint = viewModel.viewportManager.surfaceToNoteCoordinates(touchPoint.x, touchPoint.y)
        val hitShape = findTextShapeAtNotePoint(notePoint.x, notePoint.y)
        if (hitShape != null) {
            val anchor = hitShape.touchPointList?.points?.firstOrNull()
            val anchorX = anchor?.x ?: notePoint.x
            val anchorY = anchor?.y ?: notePoint.y
            viewModel.beginEditingTextShape(
                shapeId = hitShape.id,
                anchorNoteX = anchorX,
                anchorNoteY = anchorY,
                existingText = hitShape.text,
                existingFontSize = hitShape.fontSize,
                existingFontFamily = hitShape.fontFamily,
                existingColor = hitShape.strokeColor
            )
        } else {
            viewModel.beginTextInput(notePoint.x, notePoint.y)
        }
    }

    private fun findTextShapeAtNotePoint(noteX: Float, noteY: Float): TextShape? {
        val activeLayer = viewModel.activeLayer.value
        return shapesManager.shapes()
            .filterIsInstance<TextShape>()
            .filter { it.layer == activeLayer }
            .firstOrNull { shape ->
                shape.updateShapeRect()
                shape.boundingRect?.contains(noteX, noteY) == true
            }
    }
}
