package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.wyldsoft.notes.domain.models.PaperTemplate
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
     */
    fun drawTemplate(canvas: Canvas, template: PaperTemplate, noteWidth: Int) {
        when (template) {
            PaperTemplate.BLANK -> { /* nothing to draw */ }
            PaperTemplate.GRID -> drawGrid(canvas, noteWidth)
            PaperTemplate.RULED -> drawRuled(canvas, noteWidth)
        }
    }

    private fun drawGrid(canvas: Canvas, noteWidth: Int) {
        val spacing = noteWidth * RULED_SPACING_FRACTION
        val screenWidth = canvas.width
        val screenHeight = canvas.height

        // Calculate visible range in note coordinates
        val topLeft = viewportManager.surfaceToNoteCoordinates(0f, 0f)
        val bottomRight = viewportManager.surfaceToNoteCoordinates(
            screenWidth.toFloat(), screenHeight.toFloat()
        )

        // Draw horizontal lines
        val firstLineY = (topLeft.y / spacing).toInt() * spacing
        var y = firstLineY
        while (y <= bottomRight.y) {
            if (y >= 0f) {
                val surfaceStart = viewportManager.noteToSurfaceCoordinates(0f, y)
                val surfaceEnd = viewportManager.noteToSurfaceCoordinates(noteWidth.toFloat(), y)
                canvas.drawLine(surfaceStart.x, surfaceStart.y, surfaceEnd.x, surfaceEnd.y, linePaint)
            }
            y += spacing
        }

        // Draw vertical lines
        val firstLineX = (topLeft.x / spacing).toInt() * spacing
        var x = firstLineX
        while (x <= bottomRight.x) {
            if (x >= 0f && x <= noteWidth.toFloat()) {
                val surfaceTop = viewportManager.noteToSurfaceCoordinates(x, maxOf(0f, topLeft.y))
                val surfaceBottom = viewportManager.noteToSurfaceCoordinates(x, bottomRight.y)
                canvas.drawLine(surfaceTop.x, surfaceTop.y, surfaceBottom.x, surfaceBottom.y, linePaint)
            }
            x += spacing
        }
    }

    private fun drawRuled(canvas: Canvas, noteWidth: Int) {
        val spacing = noteWidth * RULED_SPACING_FRACTION
        val topMargin = noteWidth * TOP_MARGIN_FRACTION
        val marginX = noteWidth * MARGIN_FRACTION
        val screenWidth = canvas.width
        val screenHeight = canvas.height

        // Calculate visible range in note coordinates
        val topLeft = viewportManager.surfaceToNoteCoordinates(0f, 0f)
        val bottomRight = viewportManager.surfaceToNoteCoordinates(
            screenWidth.toFloat(), screenHeight.toFloat()
        )

        // Draw horizontal ruled lines
        val firstLineIndex = maxOf(0, ((topLeft.y - topMargin) / spacing).toInt())
        var y = topMargin + firstLineIndex * spacing
        while (y <= bottomRight.y) {
            val surfaceStart = viewportManager.noteToSurfaceCoordinates(0f, y)
            val surfaceEnd = viewportManager.noteToSurfaceCoordinates(noteWidth.toFloat(), y)
            canvas.drawLine(surfaceStart.x, surfaceStart.y, surfaceEnd.x, surfaceEnd.y, linePaint)
            y += spacing
        }

        // Draw red margin line
        val marginTop = viewportManager.noteToSurfaceCoordinates(marginX, maxOf(0f, topLeft.y))
        val marginBottom = viewportManager.noteToSurfaceCoordinates(marginX, bottomRight.y)
        canvas.drawLine(marginTop.x, marginTop.y, marginBottom.x, marginBottom.y, marginPaint)
    }
}
