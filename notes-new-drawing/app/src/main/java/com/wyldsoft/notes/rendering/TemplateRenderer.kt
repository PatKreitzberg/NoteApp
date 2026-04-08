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
 * Template lines are defined in note-space (using mm-based measurements) and then
 * projected into viewport space via ViewportManager, so they scale correctly on zoom.
 *
 * For pagination mode, a list of page rects (in note-space) is supplied so that
 * template lines are only drawn within page boundaries, not in the gaps between pages.
 */
class TemplateRenderer(private val density: Float) {
    companion object {
        private const val TAG = "TemplateRenderer"

        // mm to note-pixels: 1mm ≈ density * 6.299 px (at 160 dpi baseline)
        private fun mmToNotePx(mm: Float, density: Float): Float = mm * density * 6.299f

        // Standard measurements
        private const val GRID_SPACING_MM = 5f          // 5mm graph paper
        private const val COLLEGE_RULED_MM = 7.127f     // 9/32 inch
        private const val WIDE_RULED_MM = 8.731f        // 11/32 inch
        private const val MARGIN_MM = 31.75f            // 1.25 inch left margin for ruled

        // Line colour: semi-transparent grey
        private val LINE_COLOR = Color.argb(80, 100, 100, 100)
        private val MARGIN_COLOR = Color.argb(70, 210, 80, 80)
    }

    private val linePaint = Paint().apply {
        color = LINE_COLOR
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private val marginPaint = Paint().apply {
        color = MARGIN_COLOR
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    /**
     * Draw the template onto [canvas] using [viewportManager] for coordinate transforms.
     *
     * @param canvasWidth  width of the bitmap/canvas in pixels
     * @param canvasHeight height of the bitmap/canvas in pixels
     * @param pageRects    page boundaries in NOTE space; null means no pagination (infinite canvas)
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
            // No pagination: draw over the visible note area
            val noteLeft = viewportManager.viewportToNoteX(0f)
            val noteTop = viewportManager.viewportToNoteY(0f)
            val noteRight = viewportManager.viewportToNoteX(canvasWidth.toFloat())
            val noteBottom = viewportManager.viewportToNoteY(canvasHeight.toFloat())
            val infinitePage = RectF(noteLeft, noteTop, noteRight, noteBottom)

            drawTemplateInRect(canvas, template, viewportManager, canvasWidth, canvasHeight, infinitePage, clipToRect = false)
        } else {
            for (pageRect in pageRects) {
                drawTemplateInRect(canvas, template, viewportManager, canvasWidth, canvasHeight, pageRect, clipToRect = true)
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
        clipToRect: Boolean
    ) {
        // Convert page rect to viewport space to check visibility
        val vpRect = vm.noteToViewport(pageNoteRect)
        if (vpRect.bottom < 0 || vpRect.top > canvasHeight || vpRect.right < 0 || vpRect.left > canvasWidth) {
            return  // page not visible
        }

        if (clipToRect) {
            canvas.save()
            canvas.clipRect(vpRect)
        }

        when (template) {
            PaperTemplate.GRID -> drawGrid(canvas, vm, canvasWidth, canvasHeight, pageNoteRect)
            PaperTemplate.COLLEGE_RULED -> drawRuled(canvas, vm, canvasWidth, canvasHeight, pageNoteRect, COLLEGE_RULED_MM)
            PaperTemplate.WIDE_RULED -> drawRuled(canvas, vm, canvasWidth, canvasHeight, pageNoteRect, WIDE_RULED_MM)
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
        pageNoteRect: RectF
    ) {
        val spacingNote = mmToNotePx(GRID_SPACING_MM, density)

        // Horizontal lines
        val firstY = ceil(pageNoteRect.top / spacingNote) * spacingNote
        var y = firstY
        while (y <= pageNoteRect.bottom) {
            val vy = vm.noteToViewportY(y)
            if (vy in -1f..canvasHeight + 1f) {
                canvas.drawLine(0f, vy, canvasWidth.toFloat(), vy, linePaint)
            }
            y += spacingNote
        }

        // Vertical lines
        val firstX = ceil(pageNoteRect.left / spacingNote) * spacingNote
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
        spacingMm: Float
    ) {
        val spacingNote = mmToNotePx(spacingMm, density)

        // Horizontal ruled lines
        val firstY = ceil(pageNoteRect.top / spacingNote) * spacingNote
        var y = firstY
        while (y <= pageNoteRect.bottom) {
            val vy = vm.noteToViewportY(y)
            if (vy in -1f..canvasHeight + 1f) {
                canvas.drawLine(0f, vy, canvasWidth.toFloat(), vy, linePaint)
            }
            y += spacingNote
        }

        // Left margin vertical line (1.25" from the left edge of the page)
        val marginNoteX = pageNoteRect.left + mmToNotePx(MARGIN_MM, density)
        val marginVx = vm.noteToViewportX(marginNoteX)
        if (marginVx in -1f..canvasWidth + 1f) {
            val topVy = vm.noteToViewportY(pageNoteRect.top).coerceAtLeast(0f)
            val bottomVy = vm.noteToViewportY(pageNoteRect.bottom).coerceAtMost(canvasHeight.toFloat())
            canvas.drawLine(marginVx, topVy, marginVx, bottomVy, marginPaint)
        }
    }
}
