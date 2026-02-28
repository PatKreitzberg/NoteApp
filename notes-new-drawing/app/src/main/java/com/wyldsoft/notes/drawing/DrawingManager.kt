package com.wyldsoft.notes.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.utils.PaginationConstants
import com.wyldsoft.notes.utils.createStrokePaint
import com.wyldsoft.notes.utils.getPageBounds
import com.wyldsoft.notes.utils.getVisiblePageRange
import com.wyldsoft.notes.viewport.ViewportManager

class DrawingManager(
    private val viewportManager: ViewportManager? = null
) {
    private val paint = createStrokePaint()
    
    fun drawShape(canvas: Canvas, shape: Shape) {
        paint.color = shape.strokeColor
        paint.strokeWidth = shape.strokeWidth
        
        // Apply viewport transformation if available
        canvas.save()
        viewportManager?.let { vm ->
            canvas.concat(vm.getTransformMatrix())
        }
        
        when (shape.type) {
            ShapeType.STROKE -> drawStroke(canvas, shape)
            ShapeType.RECTANGLE -> drawRectangle(canvas, shape)
            ShapeType.CIRCLE -> drawCircle(canvas, shape)
            ShapeType.TRIANGLE -> drawTriangle(canvas, shape)
            ShapeType.LINE -> drawLine(canvas, shape)
        }
        
        canvas.restore()
    }
    
    private fun drawStroke(canvas: Canvas, shape: Shape) {
        if (shape.points.size < 2) return
        
        val path = Path()
        path.moveTo(shape.points[0].x, shape.points[0].y)
        
        for (i in 1 until shape.points.size) {
            val prevPoint = shape.points[i - 1]
            val currPoint = shape.points[i]
            
            if (shape.pressure.isNotEmpty() && i < shape.pressure.size) {
                paint.strokeWidth = shape.strokeWidth * shape.pressure[i]
            }
            
            path.quadTo(
                prevPoint.x, prevPoint.y,
                (prevPoint.x + currPoint.x) / 2,
                (prevPoint.y + currPoint.y) / 2
            )
        }
        
        if (shape.points.size > 1) {
            val lastPoint = shape.points.last()
            path.lineTo(lastPoint.x, lastPoint.y)
        }
        
        canvas.drawPath(path, paint)
    }
    
    private fun drawRectangle(canvas: Canvas, shape: Shape) {
        if (shape.points.size >= 2) {
            val startPoint = shape.points.first()
            val endPoint = shape.points.last()
            canvas.drawRect(
                startPoint.x, startPoint.y,
                endPoint.x, endPoint.y,
                paint
            )
        }
    }
    
    private fun drawCircle(canvas: Canvas, shape: Shape) {
        if (shape.points.size >= 2) {
            val center = shape.points.first()
            val radiusPoint = shape.points.last()
            val radius = kotlin.math.sqrt(
                (radiusPoint.x - center.x) * (radiusPoint.x - center.x) +
                (radiusPoint.y - center.y) * (radiusPoint.y - center.y)
            )
            canvas.drawCircle(center.x, center.y, radius.toFloat(), paint)
        }
    }
    
    private fun drawTriangle(canvas: Canvas, shape: Shape) {
        if (shape.points.size >= 3) {
            val path = Path()
            path.moveTo(shape.points[0].x, shape.points[0].y)
            path.lineTo(shape.points[1].x, shape.points[1].y)
            path.lineTo(shape.points[2].x, shape.points[2].y)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
    
    private fun drawLine(canvas: Canvas, shape: Shape) {
        if (shape.points.size >= 2) {
            val startPoint = shape.points.first()
            val endPoint = shape.points.last()
            canvas.drawLine(
                startPoint.x, startPoint.y,
                endPoint.x, endPoint.y,
                paint
            )
        }
    }
    
    fun drawPageSeparators(
        canvas: Canvas,
        screenWidth: Int,
        pageHeight: Float,
        isPaginationEnabled: Boolean
    ) {
        if (!isPaginationEnabled || pageHeight <= 0) return

        val separatorPaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            isAntiAlias = true
        }

        // Apply viewport transformation so separators scale with strokes
        canvas.save()
        viewportManager?.let { vm ->
            canvas.concat(vm.getTransformMatrix())
        }

        // Calculate visible range in note coordinates
        val visibleTop: Float
        val visibleBottom: Float
        if (viewportManager != null) {
            val bounds = viewportManager.getVisibleBounds(canvas.width.toFloat(), canvas.height.toFloat())
            visibleTop = bounds.top
            visibleBottom = bounds.bottom
        } else {
            visibleTop = 0f
            visibleBottom = canvas.height.toFloat()
        }

        val separatorHeight = PaginationConstants.SEPARATOR_HEIGHT
        val noteWidth = screenWidth.toFloat()

        // Draw separators for visible pages (all in note coordinates)
        var pageY = pageHeight
        var pageNum = 2

        while (pageY - separatorHeight < visibleBottom + pageHeight) {
            if (pageY + separatorHeight > visibleTop - pageHeight) {
                // Draw blue separator rectangle in note coordinates
                canvas.drawRect(
                    0f,
                    pageY,
                    noteWidth,
                    pageY + separatorHeight,
                    separatorPaint
                )

                // Draw page number in top right of the page above separator
                val pageNumberY = pageY - 20f
                val pageNumberX = noteWidth - 100f
                canvas.drawText("Page ${pageNum - 1}", pageNumberX, pageNumberY, textPaint)
            }
            pageY += pageHeight
            pageNum++
        }

        // Draw page number for the first page if visible
        if (visibleTop < pageHeight) {
            val pageNumberY = 40f
            val pageNumberX = noteWidth - 100f
            canvas.drawText("Page 1", pageNumberX, pageNumberY, textPaint)
        }

        // Draw left and right page borders
        val borderPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        for (page in getVisiblePageRange(visibleTop, visibleBottom, pageHeight)) {
            val (pageTop, pageBottom) = getPageBounds(page, pageHeight)

            // Only draw if this page overlaps the visible area
            if (pageBottom >= visibleTop && pageTop <= visibleBottom) {
                // Left border
                canvas.drawLine(0f, pageTop, 0f, pageBottom, borderPaint)
                // Right border
                canvas.drawLine(noteWidth, pageTop, noteWidth, pageBottom, borderPaint)
            }
        }

        canvas.restore()
    }
}