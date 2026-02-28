package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.utils.getPageBounds
import com.wyldsoft.notes.utils.getVisiblePageRange
import com.wyldsoft.notes.viewport.ViewportManager

/**
 * Renders paper template backgrounds (grid, ruled lines) onto the bitmap canvas.
 * All template geometry is specified in note coordinates and transformed
 * to surface coordinates via the ViewportManager, so templates scale and
 * pan identically to user strokes.
 */
class PaperTemplateRenderer(
    private val viewportManager: ViewportManager
) {
    companion object {
        // College ruled: 9/32 inch spacing. For letter paper (8.5" wide),
        // spacing as a fraction of paper width = (9.0/32.0) / 8.5
        private const val RULED_SPACING_FRACTION = 9.0f / 32.0f / 8.5f

        // Left margin at 1.25 inches from left on 8.5" paper
        private const val MARGIN_FRACTION = 1.25f / 8.5f

        // Top margin: first line starts ~0.5 inches from top
        private const val TOP_MARGIN_FRACTION = 0.5f / 8.5f

        // Light blue for lines
        private val LINE_COLOR = Color.rgb(173, 216, 230)

        // Pink/red for margin line
        private val MARGIN_COLOR = Color.rgb(220, 120, 120)
    }

    private val linePaint = Paint().apply {
        color = LINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = false
    }

    private val marginPaint = Paint().apply {
        color = MARGIN_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = false
    }

    /**
     * Draws the paper template onto the bitmap canvas.
     * @param canvas The bitmap canvas to draw on (surface coordinates)
     * @param template The template type to draw
     * @param noteWidth The width of the note in note coordinates (typically screen width)
     * @param isPaginationEnabled Whether pagination is on
     * @param pageHeight The height of a page in note coordinates (only used when paginated)
     */
    fun drawTemplate(
        canvas: Canvas,
        template: PaperTemplate,
        noteWidth: Int,
        isPaginationEnabled: Boolean = false,
        pageHeight: Float = 0f
    ) {
        when (template) {
            PaperTemplate.BLANK -> { /* nothing to draw */ }
            PaperTemplate.GRID -> drawGrid(canvas, noteWidth, isPaginationEnabled, pageHeight)
            PaperTemplate.RULED -> drawRuled(canvas, noteWidth, isPaginationEnabled, pageHeight)
        }
    }

    private fun drawGrid(canvas: Canvas, noteWidth: Int, isPaginationEnabled: Boolean, pageHeight: Float) {
        val spacing = noteWidth * RULED_SPACING_FRACTION
        val screenWidth = canvas.width
        val screenHeight = canvas.height

        // Calculate visible range in note coordinates
        val bounds = viewportManager.getVisibleBounds(screenWidth.toFloat(), screenHeight.toFloat())

        // Draw horizontal lines
        val firstLineY = (bounds.top / spacing).toInt() * spacing
        var y = firstLineY
        while (y <= bounds.bottom) {
            if (y >= 0f) {
                val surfaceStart = viewportManager.noteToSurfaceCoordinates(0f, y)
                val surfaceEnd = viewportManager.noteToSurfaceCoordinates(noteWidth.toFloat(), y)
                canvas.drawLine(surfaceStart.x, surfaceStart.y, surfaceEnd.x, surfaceEnd.y, linePaint)
            }
            y += spacing
        }

        // Draw vertical lines
        val firstLineX = (bounds.left / spacing).toInt() * spacing
        var x = firstLineX
        while (x <= bounds.right) {
            if (x >= 0f && x <= noteWidth.toFloat()) {
                val surfaceTop = viewportManager.noteToSurfaceCoordinates(x, maxOf(0f, bounds.top))
                val surfaceBottom = viewportManager.noteToSurfaceCoordinates(x, bounds.bottom)
                canvas.drawLine(surfaceTop.x, surfaceTop.y, surfaceBottom.x, surfaceBottom.y, linePaint)
            }
            x += spacing
        }
    }

    private fun drawRuled(canvas: Canvas, noteWidth: Int, isPaginationEnabled: Boolean, pageHeight: Float) {
        val spacing = noteWidth * RULED_SPACING_FRACTION
        val topMargin = noteWidth * TOP_MARGIN_FRACTION
        val marginX = noteWidth * MARGIN_FRACTION
        val screenWidth = canvas.width
        val screenHeight = canvas.height
        // Calculate visible range in note coordinates
        val bounds = viewportManager.getVisibleBounds(screenWidth.toFloat(), screenHeight.toFloat())

        if (isPaginationEnabled && pageHeight > 0f) {
            for (page in getVisiblePageRange(bounds.top, bounds.bottom, pageHeight)) {
                val (pageTop, pageBottom) = getPageBounds(page, pageHeight)

                // Draw horizontal ruled lines for this page
                var y = pageTop + topMargin
                while (y <= pageBottom && y <= bounds.bottom) {
                    if (y >= bounds.top) {
                        val surfaceStart = viewportManager.noteToSurfaceCoordinates(0f, y)
                        val surfaceEnd = viewportManager.noteToSurfaceCoordinates(noteWidth.toFloat(), y)
                        canvas.drawLine(surfaceStart.x, surfaceStart.y, surfaceEnd.x, surfaceEnd.y, linePaint)
                    }
                    y += spacing
                }

                // Draw red margin line for this page
                val visibleTop = maxOf(pageTop, bounds.top)
                val visibleBottom = minOf(pageBottom, bounds.bottom)
                if (visibleTop < visibleBottom) {
                    val marginSurfaceTop = viewportManager.noteToSurfaceCoordinates(marginX, visibleTop)
                    val marginSurfaceBottom = viewportManager.noteToSurfaceCoordinates(marginX, visibleBottom)
                    canvas.drawLine(marginSurfaceTop.x, marginSurfaceTop.y, marginSurfaceBottom.x, marginSurfaceBottom.y, marginPaint)
                }
            }
        } else {
            // No pagination: continuous ruled lines from the top
            val firstLineIndex = maxOf(0, ((bounds.top - topMargin) / spacing).toInt())
            var y = topMargin + firstLineIndex * spacing
            while (y <= bounds.bottom) {
                val surfaceStart = viewportManager.noteToSurfaceCoordinates(0f, y)
                val surfaceEnd = viewportManager.noteToSurfaceCoordinates(noteWidth.toFloat(), y)
                canvas.drawLine(surfaceStart.x, surfaceStart.y, surfaceEnd.x, surfaceEnd.y, linePaint)
                y += spacing
            }

            // Draw red margin line
            val marginTop = viewportManager.noteToSurfaceCoordinates(marginX, maxOf(0f, bounds.top))
            val marginBottom = viewportManager.noteToSurfaceCoordinates(marginX, bounds.bottom)
            canvas.drawLine(marginTop.x, marginTop.y, marginBottom.x, marginBottom.y, marginPaint)
        }
    }
}
