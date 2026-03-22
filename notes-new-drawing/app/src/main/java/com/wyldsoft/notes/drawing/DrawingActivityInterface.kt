package com.wyldsoft.notes.drawing

import android.graphics.PointF
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

interface DrawingActivityInterface {
    fun setViewModel(viewModel: EditorViewModel)
    fun onShapeCompleted(id: String, points: List<PointF>, pressures: List<Float>, timestamps: List<Long> = emptyList(), tiltX: List<Int> = emptyList(), tiltY: List<Int> = emptyList())
    fun onShapeRemoved(shapeId: String)
    fun forceScreenRefresh()
}