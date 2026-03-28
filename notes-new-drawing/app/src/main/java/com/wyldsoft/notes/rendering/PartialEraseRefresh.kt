package com.wyldsoft.notes.refreshingscreen

import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.rendering.PartialRefreshRequest
import com.wyldsoft.notes.rendering.ViewportManager
import com.wyldsoft.notes.shapemanagement.shapes.Shape

/**
 * Handles efficient partial screen redraws after shapes are erased.
 * Instead of re-rendering the entire bitmap, it creates a temporary bitmap
 * covering only the erased region, re-renders the surviving shapes that
 * intersect that region, and blits the result to the SurfaceView.
 *
 * Called by OnyxDrawingActivity.handleErasing() after EraseManager identifies
 * which shapes were hit. The refresh request is enqueued through RxManager
 * to serialize it with other rendering operations.
 */
class PartialEraseRefresh {
    private val TAG = "PartialEraseRefresh"

    fun performPartialRefresh(
        surfaceView: SurfaceView,
        viewportRefreshRect: RectF,
        remainingShapes: List<Shape>,
        viewportManager: ViewportManager,
        rxManager: RxManager
    ) {
        Log.d(TAG, "performPartialRefresh")

        EpdController.enablePost(surfaceView, 1)
        val partialRefreshRequest = PartialRefreshRequest(
            surfaceView,
            viewportRefreshRect,
            remainingShapes,
            viewportManager
        )
        rxManager.enqueue(partialRefreshRequest, null)
    }
}
