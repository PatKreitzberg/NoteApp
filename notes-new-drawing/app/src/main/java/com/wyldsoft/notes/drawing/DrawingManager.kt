package com.wyldsoft.notes.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.wyldsoft.notes.utils.PaginationConstants
import com.wyldsoft.notes.utils.getPageBounds
import com.wyldsoft.notes.utils.getVisiblePageRange
import com.wyldsoft.notes.viewport.ViewportManager

/**
 * Utility for drawing page-level decorations (separators, borders, page numbers).
 */
object DrawingManager {

    fun drawPageSeparators(
        canvas: Canvas,
        screenWidth: Int,
        pageHeight: Float,
        isPaginationEnabled: Boolean,
        viewportManager: ViewportManager?
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
