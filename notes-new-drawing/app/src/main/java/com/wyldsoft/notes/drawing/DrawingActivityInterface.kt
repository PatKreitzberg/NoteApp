package com.wyldsoft.notes.drawing

import android.graphics.PointF
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

interface DrawingActivityInterface {
    fun setViewModel(viewModel: EditorViewModel)
    fun onShapeCompleted(id: String, points: List<PointF>, pressures: List<Float>)
    fun onShapeRemoved(shapeId: String)
    fun forceScreenRefresh()
}