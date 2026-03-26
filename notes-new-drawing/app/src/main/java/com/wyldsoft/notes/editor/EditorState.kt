package com.wyldsoft.notes.editor

import android.graphics.Rect
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import android.util.Log

class EditorState {
    companion object {
        val refreshUi = MutableSharedFlow<Unit>()
        val isStrokeOptionsOpen = MutableSharedFlow<Boolean>()
        val drawingStarted = MutableSharedFlow<Unit>()
        val drawingEnded = MutableSharedFlow<Unit>()
        val forceScreenRefresh = MutableSharedFlow<Unit>()

        private var mainActivity: BaseDrawingActivity? = null

        fun setMainActivity(activity: BaseDrawingActivity) {
            mainActivity = activity
        }

        fun notifyDrawingStarted() {
            kotlinx.coroutines.GlobalScope.launch {
                drawingStarted.emit(Unit)
                isStrokeOptionsOpen.emit(false)
            }
        }

        fun notifyDrawingEnded() {
            kotlinx.coroutines.GlobalScope.launch {
                drawingEnded.emit(Unit)
                forceScreenRefresh.emit(Unit)
            }
        }

        fun getCurrentExclusionRects(): List<Rect> {
            return mainActivity?.let { activity ->
                emptyList<Rect>()
            } ?: emptyList()
        }

        fun forceRefresh() {
            Log.d("EditorState:", "forceRefresh()");
            kotlinx.coroutines.GlobalScope.launch {
                forceScreenRefresh.emit(Unit)
            }
            mainActivity?.let { activity ->
                activity.runOnUiThread {
                    kotlinx.coroutines.GlobalScope.launch {
                        refreshUi.emit(Unit)
                    }
                }
            }
        }
    }
}