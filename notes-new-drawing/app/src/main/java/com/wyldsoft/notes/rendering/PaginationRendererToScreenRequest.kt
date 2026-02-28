package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.rx.RxRequest
import com.wyldsoft.notes.drawing.DrawingManager
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

/**
 * Custom renderer that includes pagination support with page separators
 */
class PaginationRendererToScreenRequest(
    private val surfaceView: SurfaceView,
    private val bitmap: Bitmap,
    private val viewModel: EditorViewModel?
) : RxRequest() {
    
    private val drawingManager = DrawingManager(viewModel?.viewportManager)
    
    override fun execute() {
        renderToScreen()
    }
    
    private fun renderToScreen() {
        val viewRect = RenderingUtils.checkSurfaceView(surfaceView)
        EpdController.setViewDefaultUpdateMode(surfaceView, UpdateMode.HAND_WRITING_REPAINT_MODE)
        val canvas = surfaceView.holder.lockCanvas() ?: return
        
        try {
            // Render background
            RenderingUtils.renderBackground(canvas, viewRect)
            
            // Draw the main content bitmap
            RenderingUtils.drawRendererContent(bitmap, canvas)
            
            // Draw page separators if pagination is enabled
            viewModel?.let { vm ->
                if (vm.isPaginationEnabled.value) {
                    drawingManager.drawPageSeparators(
                        canvas = canvas,
                        screenWidth = vm.screenWidth.value,
                        pageHeight = vm.pageHeight.value,
                        isPaginationEnabled = true
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
            EpdController.resetViewUpdateMode(surfaceView)
        }
    }
}