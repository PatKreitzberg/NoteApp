package com.wyldsoft.notes.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.models.PaperTemplate
import com.wyldsoft.notes.rendering.PaginationManager
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.rendering.TemplateRenderer
import com.wyldsoft.notes.rendering.ViewportManager
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class NoteExportData(
    val noteId: String,
    val pdfUri: Uri?,
    val shapes: List<Shape>,
    val paginationManager: PaginationManager,
    val template: PaperTemplate
)

/**
 * Exports notes to PDF files using [PdfDocument].
 * Renders everything the user sees: PDF background (if applicable), paper template,
 * and all drawn shapes/annotations.
 */
object PdfExporter {
    private const val TAG = "PdfExporter"

    /**
     * Exports the note to a PDF file in the app's cache dir.
     */
    suspend fun export(
        context: Context,
        noteId: String,
        pdfUri: Uri?,
        shapes: List<Shape>,
        paginationManager: PaginationManager,
        template: PaperTemplate = PaperTemplate.BLANK
    ): File = withContext(Dispatchers.IO) {
        val pageCount = effectivePageCount(paginationManager, shapes, pdfUri != null)
        Log.d(TAG, "export noteId=$noteId pdfUri=$pdfUri pages=$pageCount template=$template")

        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()
        val outputFile = File(exportsDir, "$noteId.pdf")

        val document = PdfDocument()
        val templateRenderer = TemplateRenderer()
        try {
            renderNotePages(
                context, document,
                NoteExportData(noteId, pdfUri, shapes, paginationManager, template),
                templateRenderer, startPageNumber = 1
            )
            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            Log.d(TAG, "export complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } finally {
            document.close()
        }
        outputFile
    }

    /**
     * Exports all notes in a notebook to a single combined PDF, in the order given.
     * Each note's own template and pagination settings are respected.
     */
    suspend fun exportNotebook(
        context: Context,
        notebookId: String,
        notesData: List<NoteExportData>
    ): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "exportNotebook notebookId=$notebookId notes=${notesData.size}")

        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()
        val outputFile = File(exportsDir, "notebook_$notebookId.pdf")

        val document = PdfDocument()
        val templateRenderer = TemplateRenderer()
        var globalPageNumber = 1
        try {
            for (noteData in notesData) {
                val pagesRendered = renderNotePages(context, document, noteData, templateRenderer, globalPageNumber)
                globalPageNumber += pagesRendered
            }
            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            Log.d(TAG, "exportNotebook complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } finally {
            document.close()
        }
        outputFile
    }

    /**
     * Renders all pages of a single note into [document], starting at [startPageNumber].
     * Returns the number of pages rendered.
     */
    private fun renderNotePages(
        context: Context,
        document: PdfDocument,
        noteData: NoteExportData,
        templateRenderer: TemplateRenderer,
        startPageNumber: Int
    ): Int {
        val pm = noteData.paginationManager
        val pageCount = effectivePageCount(pm, noteData.shapes, noteData.pdfUri != null)
        val pageWidth = pm.pageWidth.toInt().coerceAtLeast(1)
        val pageHeight = pm.pageHeight.toInt().coerceAtLeast(1)
        Log.d(TAG, "renderNotePages noteId=${noteData.noteId} pages=$pageCount")

        for (pageIndex in 0 until pageCount) {
            val pageTopY = pm.pageTopY(pageIndex)
            val pageViewport = ViewportManager().apply {
                restoreState(scale = 1f, scrollX = 0f, scrollY = pageTopY)
            }

            val bitmap = createBitmap(pageWidth, pageHeight)
            val bitmapCanvas = Canvas(bitmap)
            bitmapCanvas.drawColor(Color.WHITE)

            if (noteData.pdfUri != null) {
                renderPdfBackground(context, noteData.pdfUri, pageIndex, bitmapCanvas, pageWidth, pageHeight)
            }

            if (noteData.template != PaperTemplate.BLANK && noteData.pdfUri == null) {
                val pageNoteRect = RectF(0f, pageTopY, pm.pageWidth, pm.pageBottomY(pageIndex))
                templateRenderer.drawTemplate(
                    bitmapCanvas, noteData.template, pageViewport,
                    pageWidth, pageHeight, listOf(pageNoteRect)
                )
            }

            val renderContext = RenderContext.createForBitmap(bitmap, bitmapCanvas)
            for (shape in noteData.shapes) {
                shape.renderInViewport(renderContext, pageViewport)
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, startPageNumber + pageIndex).create()
            val pdfPage = document.startPage(pageInfo)
            val scalePaint = Paint().apply { isFilterBitmap = true }
            pdfPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, pageWidth, pageHeight), scalePaint)
            document.finishPage(pdfPage)
            bitmap.recycle()
        }

        return pageCount
    }

    /**
     * For PDF-backed notes the page count is fixed (from the PDF).
     * For regular notes the PaginationManager only tracks pages the user has scrolled to,
     * so we also check all shapes' bounding boxes to cover content on pages not yet scrolled.
     */
    private fun effectivePageCount(
        pm: PaginationManager,
        shapes: List<Shape>,
        isPdfNote: Boolean
    ): Int {
        if (isPdfNote) return pm.pageCount.coerceAtLeast(1)

        var maxPage = pm.pageCount
        val stride = pm.pageHeight + pm.gapPx
        if (stride > 0) {
            for (shape in shapes) {
                val bottomY = shape.boundingRect?.bottom ?: continue
                val page = (bottomY / stride).toInt() + 1
                if (page > maxPage) maxPage = page
            }
        }
        return maxPage.coerceAtLeast(1)
    }

    private fun renderPdfBackground(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int,
        canvas: Canvas,
        width: Int,
        height: Int
    ) {
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val bgBitmap = createBitmap(width, height)
                            val bgCanvas = Canvas(bgBitmap)
                            bgCanvas.drawColor(Color.WHITE)
                            page.render(bgBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            canvas.drawBitmap(bgBitmap, 0f, 0f, null)
                            bgBitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF background for page $pageIndex", e)
        }
    }
}
