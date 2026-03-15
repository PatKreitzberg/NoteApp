package com.wyldsoft.notes.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.PaperSize
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Generates PDF files from notes.
 *
 * - Pagination ON: each page is the selected paper size. Shapes spanning
 *   page boundaries are split across pages using canvas clipping.
 * - Pagination OFF: single page sized to fit all shape content with padding.
 * - Templates are rendered on every page at the same quality as strokes.
 * - Background is always white.
 */
class PdfExporter(private val context: Context) {

    companion object {
        private const val EXPORT_DIR = "exported_pdfs"
        private const val PADDING_UNPAGINATED = 30f   // PDF points

        // College-ruled spacing: 9/32 inch on 8.5" paper
        private const val RULED_SPACING_FRACTION = 9.0f / 32.0f / 8.5f
        private const val MARGIN_FRACTION = 1.25f / 8.5f
        private const val TOP_MARGIN_FRACTION = 0.5f / 8.5f

        private val LINE_COLOR = Color.rgb(173, 216, 230)
        private val MARGIN_COLOR = Color.rgb(220, 120, 120)

        /** PDF page dimensions in points (1 pt = 1/72 inch). */
        fun pdfPageWidth(paperSize: PaperSize): Float = when (paperSize) {
            PaperSize.LETTER -> 612f
            PaperSize.A4 -> 595f
            PaperSize.LEGAL -> 612f
        }

        fun pdfPageHeight(paperSize: PaperSize): Float = when (paperSize) {
            PaperSize.LETTER -> 792f
            PaperSize.A4 -> 842f
            PaperSize.LEGAL -> 1008f
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun exportNote(note: Note, noteWidthPx: Int): File {
        val doc = PdfDocument()
        try {
            if (note.isPaginationEnabled) {
                renderPaginatedNote(doc, note, noteWidthPx, startPage = 1)
            } else {
                renderUnpaginatedNote(doc, note, noteWidthPx, pageNumber = 1)
            }
            return writeToFile(doc, sanitizeFileName(note.title))
        } finally {
            doc.close()
        }
    }

    fun exportNotebook(notes: List<Note>, notebookName: String, noteWidthPx: Int): File {
        val doc = PdfDocument()
        try {
            var nextPage = 1
            for (note in notes) {
                nextPage = if (note.isPaginationEnabled) {
                    renderPaginatedNote(doc, note, noteWidthPx, startPage = nextPage)
                } else {
                    renderUnpaginatedNote(doc, note, noteWidthPx, pageNumber = nextPage)
                    nextPage + 1
                }
            }
            // If no notes, add a blank page
            if (notes.isEmpty()) {
                val paperSize = PaperSize.LETTER
                val pdfW = pdfPageWidth(paperSize).toInt()
                val pdfH = pdfPageHeight(paperSize).toInt()
                val pageInfo = PdfDocument.PageInfo.Builder(pdfW, pdfH, 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                doc.finishPage(page)
            }
            return writeToFile(doc, sanitizeFileName(notebookName))
        } finally {
            doc.close()
        }
    }

    // -------------------------------------------------------------------------
    // Paginated rendering
    // -------------------------------------------------------------------------

    /** Returns the next available page number after this note's pages. */
    private fun renderPaginatedNote(
        doc: PdfDocument,
        note: Note,
        noteWidthPx: Int,
        startPage: Int
    ): Int {
        val paperSize = PaperSize.fromString(note.paperSize)
        val pdfW = pdfPageWidth(paperSize)
        val pdfH = pdfPageHeight(paperSize)

        // Page height in note coordinates
        val notePageHeightPx = noteWidthPx * paperSize.aspectRatio

        // Scale factors
        val scaleX = pdfW / noteWidthPx
        val scaleY = pdfH / notePageHeightPx  // == scaleX (same ratio)

        // Determine total page count from content extent
        val maxY = note.shapes.flatMap { it.points }.maxOfOrNull { it.y } ?: 0f
        val numPages = max(1, ceil(maxY / notePageHeightPx).toInt())

        var pageNumber = startPage
        for (pageIdx in 0 until numPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pdfW.toInt(), pdfH.toInt(), pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            // Template
            drawTemplatePaginated(
                canvas, note.paperTemplate,
                pdfW, pdfH, noteWidthPx, scaleX, scaleY
            )

            // Clip to page bounds and draw shapes
            canvas.save()
            canvas.clipRect(0f, 0f, pdfW, pdfH)
            val pageOffsetY = pageIdx * notePageHeightPx
            for (shape in note.shapes) {
                renderShapeToPdfPage(canvas, shape, scaleX, scaleY, pageOffsetY)
            }
            canvas.restore()

            doc.finishPage(page)
            pageNumber++
        }
        return pageNumber
    }

    // -------------------------------------------------------------------------
    // Unpaginated rendering
    // -------------------------------------------------------------------------

    private fun renderUnpaginatedNote(
        doc: PdfDocument,
        note: Note,
        noteWidthPx: Int,
        pageNumber: Int
    ): Int {
        val allPoints = note.shapes.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: noteWidthPx.toFloat()
        val maxY = allPoints.maxOfOrNull { it.y } ?: (noteWidthPx * 1.0f)

        // Content dimensions in note coords
        val contentW = maxX - minX
        val contentH = maxY - minY

        // Target PDF width = Letter width; scale to fit
        val targetPdfW = pdfPageWidth(PaperSize.LETTER)
        val usableW = targetPdfW - 2 * PADDING_UNPAGINATED
        val scale = if (contentW > 0f) usableW / contentW else 1f

        val pdfW = targetPdfW
        val pdfH = contentH * scale + 2 * PADDING_UNPAGINATED

        val pageInfo = PdfDocument.PageInfo.Builder(pdfW.toInt(), pdfH.toInt(), pageNumber).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        // Draw template using full note width as reference
        drawTemplateUnpaginated(
            canvas, note.paperTemplate,
            pdfW, pdfH, noteWidthPx,
            scale, minX, minY
        )

        // Draw shapes offset so content starts at padding
        canvas.save()
        canvas.clipRect(0f, 0f, pdfW, pdfH)
        for (shape in note.shapes) {
            renderShapeUnpaginated(canvas, shape, scale, minX, minY)
        }
        canvas.restore()

        doc.finishPage(page)
        return pageNumber + 1
    }

    // -------------------------------------------------------------------------
    // Shape rendering - paginated
    // -------------------------------------------------------------------------

    private fun renderShapeToPdfPage(
        canvas: Canvas,
        shape: Shape,
        scaleX: Float,
        scaleY: Float,
        pageOffsetY: Float
    ) {
        when (shape.type) {
            ShapeType.TEXT -> renderTextPaginated(canvas, shape, scaleX, scaleY, pageOffsetY)
            else -> renderPathPaginated(canvas, shape, scaleX, scaleY, pageOffsetY)
        }
    }

    private fun renderPathPaginated(
        canvas: Canvas,
        shape: Shape,
        scaleX: Float,
        scaleY: Float,
        pageOffsetY: Float
    ) {
        if (shape.points.isEmpty()) return
        val paint = createPaint(shape, scaleX)
        val path = Path()
        shape.points.forEachIndexed { i, pt ->
            val px = pt.x * scaleX
            val py = (pt.y - pageOffsetY) * scaleY
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, paint)
    }

    private fun renderTextPaginated(
        canvas: Canvas,
        shape: Shape,
        scaleX: Float,
        scaleY: Float,
        pageOffsetY: Float
    ) {
        val text = shape.text ?: return
        val pt = shape.points.firstOrNull() ?: return
        val paint = createTextPaint(shape, scaleX)
        canvas.drawText(text, pt.x * scaleX, (pt.y - pageOffsetY) * scaleY, paint)
    }

    // -------------------------------------------------------------------------
    // Shape rendering - unpaginated
    // -------------------------------------------------------------------------

    private fun renderShapeUnpaginated(
        canvas: Canvas,
        shape: Shape,
        scale: Float,
        originX: Float,
        originY: Float
    ) {
        when (shape.type) {
            ShapeType.TEXT -> renderTextUnpaginated(canvas, shape, scale, originX, originY)
            else -> renderPathUnpaginated(canvas, shape, scale, originX, originY)
        }
    }

    private fun renderPathUnpaginated(
        canvas: Canvas,
        shape: Shape,
        scale: Float,
        originX: Float,
        originY: Float
    ) {
        if (shape.points.isEmpty()) return
        val paint = createPaint(shape, scale)
        val path = Path()
        shape.points.forEachIndexed { i, pt ->
            val px = (pt.x - originX) * scale + PADDING_UNPAGINATED
            val py = (pt.y - originY) * scale + PADDING_UNPAGINATED
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, paint)
    }

    private fun renderTextUnpaginated(
        canvas: Canvas,
        shape: Shape,
        scale: Float,
        originX: Float,
        originY: Float
    ) {
        val text = shape.text ?: return
        val pt = shape.points.firstOrNull() ?: return
        val paint = createTextPaint(shape, scale)
        val px = (pt.x - originX) * scale + PADDING_UNPAGINATED
        val py = (pt.y - originY) * scale + PADDING_UNPAGINATED
        canvas.drawText(text, px, py, paint)
    }

    // -------------------------------------------------------------------------
    // Template rendering - paginated
    // -------------------------------------------------------------------------

    private fun drawTemplatePaginated(
        canvas: Canvas,
        template: PaperTemplate,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scaleX: Float,
        scaleY: Float
    ) {
        when (template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.GRID -> drawGridPaginated(canvas, pdfW, pdfH, noteWidthPx, scaleX)
            PaperTemplate.RULED -> drawRuledPaginated(canvas, pdfW, pdfH, noteWidthPx, scaleX)
        }
    }

    private fun drawGridPaginated(
        canvas: Canvas,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scaleX: Float
    ) {
        val spacingNote = noteWidthPx * RULED_SPACING_FRACTION
        val spacingPdf = spacingNote * scaleX
        val linePaint = makeLinePaint()

        var y = spacingPdf
        while (y < pdfH) {
            canvas.drawLine(0f, y, pdfW, y, linePaint)
            y += spacingPdf
        }
        var x = spacingPdf
        while (x < pdfW) {
            canvas.drawLine(x, 0f, x, pdfH, linePaint)
            x += spacingPdf
        }
    }

    private fun drawRuledPaginated(
        canvas: Canvas,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scaleX: Float
    ) {
        val spacingNote = noteWidthPx * RULED_SPACING_FRACTION
        val spacingPdf = spacingNote * scaleX
        val topMarginPdf = noteWidthPx * TOP_MARGIN_FRACTION * scaleX
        val marginXPdf = noteWidthPx * MARGIN_FRACTION * scaleX
        val linePaint = makeLinePaint()
        val marginPaint = makeMarginPaint()

        var y = topMarginPdf
        while (y < pdfH) {
            canvas.drawLine(0f, y, pdfW, y, linePaint)
            y += spacingPdf
        }
        canvas.drawLine(marginXPdf, 0f, marginXPdf, pdfH, marginPaint)
    }

    // -------------------------------------------------------------------------
    // Template rendering - unpaginated
    // -------------------------------------------------------------------------

    private fun drawTemplateUnpaginated(
        canvas: Canvas,
        template: PaperTemplate,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scale: Float,
        originX: Float,
        originY: Float
    ) {
        when (template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.GRID -> drawGridUnpaginated(canvas, pdfW, pdfH, noteWidthPx, scale)
            PaperTemplate.RULED -> drawRuledUnpaginated(canvas, pdfW, pdfH, noteWidthPx, scale, originY)
        }
    }

    private fun drawGridUnpaginated(
        canvas: Canvas,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scale: Float
    ) {
        val spacingPdf = noteWidthPx * RULED_SPACING_FRACTION * scale
        val linePaint = makeLinePaint()

        var y = PADDING_UNPAGINATED % spacingPdf
        while (y < pdfH) {
            canvas.drawLine(0f, y, pdfW, y, linePaint)
            y += spacingPdf
        }
        var x = PADDING_UNPAGINATED % spacingPdf
        while (x < pdfW) {
            canvas.drawLine(x, 0f, x, pdfH, linePaint)
            x += spacingPdf
        }
    }

    private fun drawRuledUnpaginated(
        canvas: Canvas,
        pdfW: Float,
        pdfH: Float,
        noteWidthPx: Int,
        scale: Float,
        originY: Float
    ) {
        val spacingNote = noteWidthPx * RULED_SPACING_FRACTION
        val spacingPdf = spacingNote * scale
        val topMarginNote = noteWidthPx * TOP_MARGIN_FRACTION
        val marginXNote = noteWidthPx * MARGIN_FRACTION
        val linePaint = makeLinePaint()
        val marginPaint = makeMarginPaint()

        // First line position relative to PDF canvas
        val firstLineYPdf = (topMarginNote - originY) * scale + PADDING_UNPAGINATED
        var y = firstLineYPdf
        while (y < pdfH) {
            if (y >= 0f) canvas.drawLine(0f, y, pdfW, y, linePaint)
            y += spacingPdf
        }
        val marginXPdf = (marginXNote - originY) * scale + PADDING_UNPAGINATED
        if (marginXPdf in 0f..pdfW) {
            canvas.drawLine(marginXPdf, 0f, marginXPdf, pdfH, marginPaint)
        }
    }

    // -------------------------------------------------------------------------
    // Paint helpers
    // -------------------------------------------------------------------------

    private fun createPaint(shape: Shape, scale: Float): Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = shape.strokeColor
        strokeWidth = shape.strokeWidth * scale
    }

    private fun createTextPaint(shape: Shape, scale: Float): Paint = Paint().apply {
        isAntiAlias = true
        color = shape.strokeColor
        textSize = shape.fontSize * scale
        typeface = android.graphics.Typeface.create(shape.fontFamily, android.graphics.Typeface.NORMAL)
    }

    private fun makeLinePaint(): Paint = Paint().apply {
        color = LINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    private fun makeMarginPaint(): Paint = Paint().apply {
        color = MARGIN_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private fun writeToFile(doc: PdfDocument, baseName: String): File {
        val dir = File(context.cacheDir, EXPORT_DIR).also { it.mkdirs() }
        val file = File(dir, "$baseName.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        return file
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").trim().ifBlank { "export" }
}
