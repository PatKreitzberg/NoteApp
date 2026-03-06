package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.rx.RxRequest
import com.wyldsoft.notes.drawing.DrawingManager
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.sdkintegration.DeviceHelper

/**
 * Renders the bitmap to the SurfaceView, including page separators when pagination is enabled.
 * Used by both Onyx (via RxManager queue) and generic (via direct execute()) rendering paths.
 */
class PaginationRendererToScreenRequest(
    private val surfaceView: SurfaceView,
    private val bitmap: Bitmap,
    private val viewModel: EditorViewModel?
) : RxRequest() {

    override fun execute() {
        val viewRect = RenderingUtils.checkSurfaceView(surfaceView)
        if (DeviceHelper.isOnyxDevice) {
            EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE)
        }
        val canvas = surfaceView.holder.lockCanvas() ?: return

        try {
            RenderingUtils.renderBackground(canvas, viewRect)
            RenderingUtils.drawRendererContent(bitmap, canvas)

            // Draw page separators if pagination is enabled
            viewModel?.let { vm ->
                if (vm.isPaginationEnabled.value) {
                    DrawingManager.drawPageSeparators(
                        canvas = canvas,
                        screenWidth = vm.screenWidth.value,
                        pageHeight = vm.pageHeight.value,
                        isPaginationEnabled = true,
                        viewportManager = vm.viewportManager
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
            if (DeviceHelper.isOnyxDevice) {
                EpdController.resetViewUpdateMode(surfaceView)
            }
        }
    }
}