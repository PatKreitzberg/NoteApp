package com.wyldsoft.notes.presentation.viewmodel

import android.graphics.Color
import android.graphics.PointF
import com.wyldsoft.notes.actions.ActionManager
import com.wyldsoft.notes.actions.EditTextAction
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.rendering.BitmapManager
import com.wyldsoft.notes.shapemanagement.ShapesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles text input state and operations: placing, editing, and committing text shapes.
 * Extracted from EditorViewModel to keep it under the 300-line guideline.
 */
class TextInputHandler(
    private val noteRepository: NoteRepository,
    private val scope: CoroutineScope,
    private val actionManager: ActionManager,
    private val getCurrentNote: () -> Note,
    private val getShapesManager: () -> ShapesManager?,
    private val getBitmapManager: () -> BitmapManager?,
    private val onScreenRefreshNeeded: () -> Unit,
    private val onUpdateContentBounds: () -> Unit,
    private val applyFormattingToSelection: (fontSize: Float, fontFamily: String, color: Int) -> Unit
) {
    private val _textInputPosition = MutableStateFlow<PointF?>(null)
    val textInputPosition: StateFlow<PointF?> = _textInputPosition.asStateFlow()

    private val _liveTextContent = MutableStateFlow("")
    val liveTextContent: StateFlow<String> = _liveTextContent.asStateFlow()

    private var editingShapeId: String? = null

    private val _textFontSize = MutableStateFlow(32f)
    val textFontSize: StateFlow<Float> = _textFontSize.asStateFlow()

    private val _textFontFamily = MutableStateFlow("sans-serif")
    val textFontFamily: StateFlow<String> = _textFontFamily.asStateFlow()

    private val _textColor = MutableStateFlow(Color.BLACK)
    val textColor: StateFlow<Int> = _textColor.asStateFlow()

    fun setTextFontSize(size: Float) {
        _textFontSize.value = size
        applyFormattingToSelection(size, _textFontFamily.value, _textColor.value)
    }

    fun setTextFontFamily(family: String) {
        _textFontFamily.value = family
        applyFormattingToSelection(_textFontSize.value, family, _textColor.value)
    }

    fun setTextColor(color: Int) {
        _textColor.value = color
        applyFormattingToSelection(_textFontSize.value, _textFontFamily.value, color)
    }

    fun beginTextInput(noteX: Float, noteY: Float) {
        if (_textInputPosition.value != null) return
        editingShapeId = null
        _liveTextContent.value = ""
        _textInputPosition.value = PointF(noteX, noteY)
    }

    fun beginEditingTextShape(
        shapeId: String,
        anchorNoteX: Float,
        anchorNoteY: Float,
        existingText: String,
        existingFontSize: Float,
        existingFontFamily: String,
        existingColor: Int
    ) {
        if (_textInputPosition.value != null) return
        editingShapeId = shapeId
        _textFontSize.value = existingFontSize
        _textFontFamily.value = existingFontFamily
        _textColor.value = existingColor
        _liveTextContent.value = existingText
        _textInputPosition.value = PointF(anchorNoteX, anchorNoteY)

        val sm = getShapesManager()
        val bm = getBitmapManager()
        if (sm != null && bm != null) {
            val sdkShape = sm.findShapeById(shapeId)
            if (sdkShape != null) {
                sm.removeShape(sdkShape)
                bm.recreateBitmapFromShapes(sm.shapes())
            }
        }
        onScreenRefreshNeeded()
    }

    fun updateLiveTextContent(text: String) {
        _liveTextContent.value = text
    }

    fun commitLiveTextInput() {
        commitTextInput(_liveTextContent.value)
        _liveTextContent.value = ""
    }

    fun commitTextInput(text: String) {
        val position = _textInputPosition.value ?: return
        val editId = editingShapeId
        _textInputPosition.value = null
        editingShapeId = null

        scope.launch {
            val noteId = getCurrentNote().id
            val oldShape = if (editId != null) getCurrentNote().shapes.find { it.id == editId } else null

            if (editId != null) {
                noteRepository.removeShape(noteId, editId)
            }

            if (text.isBlank()) {
                val bm = getBitmapManager()
                val sm = getShapesManager()
                if (bm != null && sm != null) {
                    bm.recreateBitmapFromShapes(sm.shapes())
                    onScreenRefreshNeeded()
                    if (oldShape != null) {
                        actionManager.recordAction(EditTextAction(noteId, oldShape, null, noteRepository, sm, bm))
                    }
                }
                return@launch
            }

            val shape = Shape(
                type = ShapeType.TEXT,
                points = listOf(position),
                strokeWidth = 2f,
                strokeColor = _textColor.value,
                text = text,
                fontSize = _textFontSize.value,
                fontFamily = _textFontFamily.value
            )
            noteRepository.addShape(noteId, shape)
            val sm = getShapesManager()
            val bm = getBitmapManager()
            if (sm != null && bm != null) {
                val sdkShape = sm.convertDomainShapeToSdkShape(shape)
                sm.addShape(sdkShape)
                bm.recreateBitmapFromShapes(sm.shapes())
                actionManager.recordAction(EditTextAction(noteId, oldShape, shape, noteRepository, sm, bm))
                onScreenRefreshNeeded()
            }
            onUpdateContentBounds()
        }
    }

    fun cancelTextInput() {
        val editId = editingShapeId
        _textInputPosition.value = null
        _liveTextContent.value = ""
        editingShapeId = null

        if (editId != null) {
            scope.launch {
                noteRepository.removeShape(getCurrentNote().id, editId)
            }
        }
    }
}
