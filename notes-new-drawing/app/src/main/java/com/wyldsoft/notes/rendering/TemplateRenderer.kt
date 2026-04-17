package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.models.PaperTemplate
import kotlin.math.ceil

/**
 * Draws paper templates (grid, ruled lines) onto a canvas in viewport space.
 *
 * Template lines are defined proportionally to the page width rather than using
 * absolute physical measurements. The canvas width represents the full paper width
 * (8.5" for Letter), so all spacing is expressed as a fraction of that width.
 * This ensures the template looks correct regardless of the physical screen size.
 *
 * For pagination mode, a list of page rects (in note-space) is supplied so that
 * template lines are only drawn within page boundaries, not in the gaps between pages.
 * For non-pagination mode, pageRects is null and the template is drawn across the
 * visible area using the canvas width as the page width reference.
 */
class TemplateRenderer {
    companion object {
        private const val TAG = "TemplateRenderer"

        // All spacings are expressed as a fraction of the page width (8.5" Letter baseline).
        // Letter paper is 8.5 inches wide, 11 inches tall.
        private const val LETTER_WIDTH_IN = 8.5f
        private const val LETTER_HEIGHT_IN = 11.0f

        // College ruled: 9/32" line spacing
        private const val COLLEGE_RULED_FRACTION = (9f / 32f) / LETTER_WIDTH_IN   // ≈ 0.0331
        // Wide ruled: 11/32" line spacing
        private const val WIDE_RULED_FRACTION = (11f / 32f) / LETTER_WIDTH_IN     // ≈ 0.0405
        // Grid: 5mm on 215.9mm (8.5") wide paper
        private const val GRID_FRACTION = 5f / 215.9f                              // ≈ 0.02316
        // Left margin: 1.25" from left edge
        private const val MARGIN_FRACTION = 1.25f / LETTER_WIDTH_IN               // ≈ 0.1471
    }

    private val linePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private val marginPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    /**
     * Draw the template onto [canvas] using [viewportManager] for coordinate transforms.
     *
     * @param canvasWidth  width of the bitmap/canvas in pixels
     * @param canvasHeight height of the bitmap/canvas in pixels
     * @param pageRects    page boundaries in NOTE space; null means no pagination (use canvas width as page width)
     */
    fun drawTemplate(
        canvas: Canvas,
        template: PaperTemplate,
        viewportManager: ViewportManager,
        canvasWidth: Int,
        canvasHeight: Int,
        pageRects: List<RectF>?
    ) {
        Log.d(TAG, "drawTemplate template=$template")
        if (template == PaperTemplate.BLANK) return

        if (pageRects == null) {
            // No pagination: use visible note area; page width = note-space width of canvas
            val noteLeft = viewportManager.viewportToNoteX(0f)
            val noteTop = viewportManager.viewportToNoteY(0f)
            val noteRight = viewportManager.viewportToNoteX(canvasWidth.toFloat())
            val noteBottom = viewportManager.viewportToNoteY(canvasHeight.toFloat())
            val pageWidth = canvasWidth.toFloat()  // fixed note-space reference so lines spread apart on zoom-in
            val infinitePage = RectF(noteLeft, noteTop, noteRight, noteBottom)
            drawTemplateInRect(canvas, template, viewportManager, canvasWidth, canvasHeight, infinitePage, pageWidth, clipToRect = false)
        } else {
            for (pageRect in pageRects) {
                val pageWidth = pageRect.width()
                drawTemplateInRect(canvas, template, viewportManager, canvasWidth, canvasHeight, pageRect, pageWidth, clipToRect = true)
            }
        }
    }

    private fun drawTemplateInRect(
        canvas: Canvas,
        template: PaperTemplate,
        vm: ViewportManager,
        canvasWidth: Int,
        canvasHeight: Int,
        pageNoteRect: RectF,
        pageWidth: Float,   // note-space width of the page (used as the proportional reference)
        clipToRect: Boolean
    ) {
        val vpRect = vm.noteToViewport(pageNoteRect)
        // Skip pages not visible on screen
        if (vpRect.bottom < 0 || vpRect.top > canvasHeight || vpRect.right < 0 || vpRect.left > canvasWidth) {
            return
        }

        if (clipToRect) {
            canvas.save()
            canvas.clipRect(vpRect)
        }

        when (template) {
            PaperTemplate.GRID -> drawGrid(canvas, vm, canvasWidth, canvasHeight, pageNoteRect, pageWidth, clipToRect)
            PaperTemplate.COLLEGE_RULED -> drawRuled(canvas, vm, canvasWidth, canvasHeight, pageNoteRect, pageWidth, COLLEGE_RULED_FRACTION, clipToRect)
            PaperTemplate.WIDE_RULED -> drawRuled(canvas, vm, canvasWidth, canvasHeight, pageNoteRect, pageWidth, WIDE_RULED_FRACTION, clipToRect)
            PaperTemplate.BLANK -> { /* nothing */ }
        }

        if (clipToRect) {
            canvas.restore()
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        vm: ViewportManager,
        canvasWidth: Int,
        canvasHeight: Int,
        pageNoteRect: RectF,
        pageWidth: Float,
        restartAtPageBoundary: Boolean = false
    ) {
        val spacingNote = pageWidth * GRID_FRACTION

        // Horizontal lines
        val firstY = if (restartAtPageBoundary) pageNoteRect.top else ceil(pageNoteRect.top / spacingNote) * spacingNote
        var y = firstY
        while (y <= pageNoteRect.bottom) {
            val vy = vm.noteToViewportY(y)
            if (vy in -1f..canvasHeight + 1f) {
                canvas.drawLine(0f, vy, canvasWidth.toFloat(), vy, linePaint)
            }
            y += spacingNote
        }

        // Vertical lines
        val firstX = if (restartAtPageBoundary) pageNoteRect.left else ceil(pageNoteRect.left / spacingNote) * spacingNote
        var x = firstX
        while (x <= pageNoteRect.right) {
            val vx = vm.noteToViewportX(x)
            if (vx in -1f..canvasWidth + 1f) {
                canvas.drawLine(vx, 0f, vx, canvasHeight.toFloat(), linePaint)
            }
            x += spacingNote
        }
    }

    private fun drawRuled(
        canvas: Canvas,
        vm: ViewportManager,
        canvasWidth: Int,
        canvasHeight: Int,
        pageNoteRect: RectF,
        pageWidth: Float,
        spacingFraction: Float,
        restartAtPageBoundary: Boolean = false
    ) {
        val spacingNote = pageWidth * spacingFraction

        // Horizontal ruled lines
        val firstY = if (restartAtPageBoundary) pageNoteRect.top else ceil(pageNoteRect.top / spacingNote) * spacingNote
        var y = firstY
        while (y <= pageNoteRect.bottom) {
            val vy = vm.noteToViewportY(y)
            if (vy in -1f..canvasHeight + 1f) {
                canvas.drawLine(0f, vy, canvasWidth.toFloat(), vy, linePaint)
            }
            y += spacingNote
        }

        // Left margin vertical line — proportional to page width (1.25" on 8.5" paper)
        val marginNoteX = pageNoteRect.left + pageWidth * MARGIN_FRACTION
        val marginVx = vm.noteToViewportX(marginNoteX)
        if (marginVx in -1f..canvasWidth + 1f) {
            val topVy = vm.noteToViewportY(pageNoteRect.top).coerceAtLeast(0f)
            val bottomVy = vm.noteToViewportY(pageNoteRect.bottom).coerceAtMost(canvasHeight.toFloat())
            canvas.drawLine(marginVx, topVy, marginVx, bottomVy, marginPaint)
        }
    }
}
