package com.wyldsoft.notes.sdkintegration.onyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.sdkintegration.GlobalDeviceReceiver
import com.wyldsoft.notes.rendering.RendererToScreenRequest
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.touchhandling.TouchUtils
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver
import com.wyldsoft.notes.sdkintegration.BaseDrawingActivity
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import com.wyldsoft.notes.pen.PenType
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.shapemanagement.EraseManager
import com.wyldsoft.notes.refreshingscreen.PartialEraseRefresh


/**
 * Onyx SDK implementation of BaseDrawingActivity. This is the core drawing engine.
 *
 * Drawing flow:
 *   Onyx TouchHelper delivers pen strokes via RawInputCallback ->
 *   onRawDrawingTouchPointListReceived() -> drawScribbleToBitmap() ->
 *   ShapeFactory creates a typed Shape -> shape is rendered to the offscreen bitmap
 *   -> RendererToScreenRequest blits bitmap to SurfaceView via RxManager.
 *
 * Erasing flow:
 *   onRawErasingTouchPointListReceived() -> handleErasing() ->
 *   EraseManager.findIntersectingShapes() hit-tests erase points against stored shapes ->
 *   matching shapes removed from drawnShapes list ->
 *   PartialEraseRefresh redraws just the affected region ->
 *   recreateBitmapFromShapes() rebuilds the full offscreen bitmap.
 *
 * Manages the Onyx TouchHelper lifecycle (open/close raw drawing, stroke style),
 * finger-touch suppression during pen input, and the GlobalDeviceReceiver for
 * system UI events. Extended by MainActivity as the app entry point.
 */
open class OnyxDrawingActivity : BaseDrawingActivity() {
    override var TAG = "OnyxDrawingActivity"
    private var rxManager: RxManager? = null
    private var onyxTouchHelper: TouchHelper? = null
    private var onyxDeviceReceiver: GlobalDeviceReceiver? = null

    // Store all drawn shapes for re-rendering
    private val drawnShapes = mutableListOf<Shape>()

    // Erase management
    private val eraseManager = EraseManager()
    private val partialEraseRefresh = PartialEraseRefresh()

    override fun initializeSDK() {
        // Onyx-specific initialization
    }

    override fun createTouchHelper(surfaceView: SurfaceView) {
        val callback = createOnyxCallback()
        onyxTouchHelper = TouchHelper.create(surfaceView, callback)
    }

    override fun createDeviceReceiver(): BaseDeviceReceiver {
        onyxDeviceReceiver = GlobalDeviceReceiver()
        return OnyxDeviceReceiverWrapper(onyxDeviceReceiver!!)
    }

    override fun enableFingerTouch() {
        TouchUtils.enableFingerTouch(applicationContext)
    }

    override fun disableFingerTouch() {
        TouchUtils.disableFingerTouch(applicationContext)
    }

    override fun cleanSurfaceView(surfaceView: SurfaceView): Boolean {
        Log.d(TAG, "cleanSurfaceView")
        val holder = surfaceView.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun renderToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        Log.d(TAG, "renderToScreen")
        if (bitmap != null) {
            getRxManager().enqueue(
                RendererToScreenRequest(
                    surfaceView,
                    bitmap
                ), null)
        }
    }

    override fun onResumeDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(true)
    }

    override fun onPauseDrawing() {
        onyxTouchHelper?.setRawDrawingEnabled(false)
    }

    override fun onCleanupSDK() {
        onyxTouchHelper?.closeRawDrawing()
        drawnShapes.clear()
    }

    override fun updateActiveSurface() {
        updateTouchHelperWithProfile()
    }

    override fun updateTouchHelperWithProfile() {
        Log.d(TAG, "updateTouchHelperWithProfile")
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            val excludeRects = EditorState.getCurrentExclusionRects()
            Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setStrokeColor(currentPenProfile.getColorAsInt())
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()

            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())
            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun updateTouchHelperExclusionZones(excludeRects: List<Rect>) {
        Log.d(TAG, "updateTouchHelperExclusionZones")
        onyxTouchHelper?.let { helper ->
            helper.setRawDrawingEnabled(false)
            helper.closeRawDrawing()

            val limit = Rect()
            surfaceView?.getLocalVisibleRect(limit)

            Log.d("ExclusionRects", "Current exclusion rects ${excludeRects.size}")
            helper.setStrokeWidth(currentPenProfile.strokeWidth)
                .setLimitRect(limit, ArrayList(excludeRects))
                .openRawDrawing()
            helper.setStrokeStyle(currentPenProfile.getOnyxStrokeStyleInternal())

            helper.setRawDrawingEnabled(true)
            helper.setRawDrawingRenderEnabled(true)
        }
    }

    override fun initializeDeviceReceiver() {
        Log.d(TAG, "initializeDeviceReceiver")
        val deviceReceiver = createDeviceReceiver() as OnyxDeviceReceiverWrapper
        deviceReceiver.enable(this, true)
        deviceReceiver.setSystemNotificationPanelChangeListener { open ->
            onyxTouchHelper?.setRawDrawingEnabled(!open)
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }.setSystemScreenOnListener {
            surfaceView?.let { sv ->
                renderToScreen(sv, bitmap)
            }
        }
    }

    override fun onCleanupDeviceReceiver() {
        onyxDeviceReceiver?.enable(this, false)
    }

    override fun forceScreenRefresh() {
        Log.d(TAG, "forceScreenRefresh() called")
        surfaceView?.let { sv ->
            cleanSurfaceView(sv)
            // Recreate bitmap from all stored shapes
            recreateBitmapFromShapes()
            bitmap?.let { renderToScreen(sv, it) }
        }
    }

    private fun getRxManager(): RxManager {
        Log.d(TAG, "getRxManager")
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager!!
    }

    private fun createOnyxCallback() = object : com.onyx.android.sdk.pen.RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawDrawing")
            isDrawingInProgress = true
            disableFingerTouch()
            EditorState.notifyDrawingStarted()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawDrawing")
            isDrawingInProgress = false
            enableFingerTouch()
            forceScreenRefresh()
            EditorState.notifyDrawingEnded()
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onRawDrawingTouchPointMoveReceived")
            // Handle move events if needed
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList?) {
            //Log.d(TAG, "createOnyxCallback.onRawDrawingTouchPointListReceived")
            touchPointList?.points?.let { points ->
                if (!isDrawingInProgress) {
                    isDrawingInProgress = true
                }
                drawScribbleToBitmap(points, touchPointList)
            }
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onBeginRawErasing")
            // Handle erasing start
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onEndRawErasing")
            // Handle erasing end
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint?) {
            Log.d(TAG, "createOnyxCallback.onRawErasingTouchPointMoveReceived")
            // Handle erase move
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList?) {
            Log.d(TAG, "createOnyxCallback.onRawErasingTouchPointListReceived")
            touchPointList?.let { erasePointList ->
                handleErasing(erasePointList)
            }

        }
    }

    private fun handleErasing(erasePointList: TouchPointList) {
        Log.d(TAG, "handleErasing called with ${erasePointList.size()} points")
        
        // Find shapes that intersect with the erase touch points
        val intersectingShapes = eraseManager.findIntersectingShapes(
            erasePointList, 
            drawnShapes
        )
        
        if (intersectingShapes.isNotEmpty()) {
            Log.d(TAG, "Found ${intersectingShapes.size} shapes to erase")
            
            // Calculate refresh area before removing shapes
            val refreshRect = eraseManager.calculateRefreshRect(intersectingShapes)
            
            // Remove intersecting shapes from our shape list
            drawnShapes.removeAll(intersectingShapes.toSet())
            
            // Perform partial refresh of the erased area
            refreshRect?.let { rect: RectF ->

                surfaceView?.let { sv ->
                    partialEraseRefresh.performPartialRefresh(
                        sv,
                        rect,
                        drawnShapes,
                        getRxManager()
                    )
                }
            }
            
            // Also update the main bitmap by recreating it from remaining shapes
            recreateBitmapFromShapes()
        }
    }

    private fun drawScribbleToBitmap(points: List<TouchPoint>, touchPointList: TouchPointList) {
        Log.d(TAG, "drawScribbleToBitmap called list size " + touchPointList.size())
        surfaceView?.let { sv ->
            createDrawingBitmap()

            // Create and store the shape based on current pen type
            val shape = createShapeFromPenType(touchPointList)
            drawnShapes.add(shape)

            // Render the new shape to the bitmap
            // fixme i dont think either of next to lines do anything necessary
            renderShapeToBitmap(shape)
            renderToScreen(sv, bitmap)
        }
    }

    private fun createShapeFromPenType(touchPointList: TouchPointList): Shape {
        Log.d(TAG, "createShapeFromPenType")
        // Map pen type to shape type
        val shapeType = when (currentPenProfile.penType) {
            PenType.BALLPEN, PenType.PENCIL -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE
            PenType.FOUNTAIN -> ShapeFactory.SHAPE_BRUSH_SCRIBBLE
            PenType.MARKER -> ShapeFactory.SHAPE_MARKER_SCRIBBLE
            PenType.CHARCOAL, PenType.CHARCOAL_V2 -> ShapeFactory.SHAPE_CHARCOAL_SCRIBBLE
            PenType.NEO_BRUSH -> ShapeFactory.SHAPE_NEO_BRUSH_SCRIBBLE
            PenType.DASH -> ShapeFactory.SHAPE_PENCIL_SCRIBBLE // Default to pencil for dash
        }

        // Create the shape
        val shape = ShapeFactory.createShape(shapeType)
        shape.touchPointList = touchPointList
        shape.strokeColor = currentPenProfile.getColorAsInt()
        shape.strokeWidth = currentPenProfile.strokeWidth
        shape.shapeType = shapeType
            
        // Update bounding rect for hit testing
        shape.updateShapeRect()

        // Set texture for charcoal if needed
        if (currentPenProfile.penType == PenType.CHARCOAL_V2) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V2
        } else if (currentPenProfile.penType == PenType.CHARCOAL) {
            shape.texture = com.onyx.android.sdk.data.note.PenTexture.CHARCOAL_SHAPE_V1
        }

        return shape
    }

    private fun renderShapeToBitmap(shape: Shape) {
        Log.d(TAG, "renderShapeToBitmap")
        bitmap?.let { bmp ->
            val renderContext = RenderContext().apply {
                bitmap = bmp
                canvas = Canvas(bmp)
                paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                viewPoint = android.graphics.Point(0, 0)
            }
            shape.render(renderContext)
        }
    }

    private fun recreateBitmapFromShapes() {
        Log.d(TAG, "recreateBitmapFromShape")
        surfaceView?.let { sv ->
            // Create a fresh bitmap
            bitmap?.recycle()
            bitmap = createBitmap(sv.width, sv.height)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.WHITE)

            val renderContext = RenderContext().apply {
                bitmap = this@OnyxDrawingActivity.bitmap
                canvas = bitmapCanvas!!
                paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                viewPoint = android.graphics.Point(0, 0)
            }

            for (shape in drawnShapes) {
                shape.render(renderContext)
            }
        }
    }
}