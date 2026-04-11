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

/**
 * Exports a note to a PDF file using [PdfDocument].
 * Renders everything the user sees: PDF background (if applicable), paper template,
 * and all drawn shapes/annotations.
 */
object PdfExporter {
    private const val TAG = "PdfExporter"

    /**
     * Exports the note to a PDF file in the app's cache dir.
     *
     * @param context       Application context.
     * @param noteId        Used as the output filename.
     * @param pdfUri        URI of the source PDF, or null for regular (non-PDF-backed) notes.
     * @param shapes        All shapes/annotations to render.
     * @param paginationManager  Provides page dimensions and count.
     * @param template      Paper template to render (BLANK, LINED, GRID, etc.).
     * @return The exported PDF [File] in [Context.getCacheDir]/exports/.
     */
    suspend fun export(
        context: Context,
        noteId: String,
        pdfUri: Uri?,
        shapes: List<Shape>,
        paginationManager: PaginationManager,
        template: PaperTemplate = PaperTemplate.BLANK
    ): File = withContext(Dispatchers.IO) {
        // For PDF-backed notes, the page count is fixed. For regular notes, derive it
        // from both the tracked page count and the actual content extent so all pages
        // with drawn content are included even if the user hasn't scrolled to them.
        val pageCount = effectivePageCount(paginationManager, shapes, pdfUri != null)
        Log.d(TAG, "export noteId=$noteId pdfUri=$pdfUri pages=$pageCount template=$template")

        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()
        val outputFile = File(exportsDir, "$noteId.pdf")

        val pageWidth = paginationManager.pageWidth.toInt().coerceAtLeast(1)
        val pageHeight = paginationManager.pageHeight.toInt().coerceAtLeast(1)
        val document = PdfDocument()
        val templateRenderer = TemplateRenderer()

        try {
            for (pageIndex in 0 until pageCount) {
                val pageTopY = paginationManager.pageTopY(pageIndex)

                // Synthetic viewport: scale=1, scrolled to this page's top-left
                val pageViewport = ViewportManager().apply {
                    restoreState(scale = 1f, scrollX = 0f, scrollY = pageTopY)
                }

                // Render page content to a bitmap
                val bitmap = createBitmap(pageWidth, pageHeight)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)

                // 1. Draw PDF background (PDF-backed notes)
                if (pdfUri != null) {
                    renderPdfBackground(context, pdfUri, pageIndex, bitmapCanvas, pageWidth, pageHeight)
                }

                // 2. Draw paper template on top (for non-PDF notes, or if template is not BLANK)
                //    Pass the single page rect so template is confined to the page area
                if (template != PaperTemplate.BLANK && pdfUri == null) {
                    val pageNoteRect = RectF(
                        0f,
                        pageTopY,
                        paginationManager.pageWidth,
                        paginationManager.pageBottomY(pageIndex)
                    )
                    templateRenderer.drawTemplate(
                        bitmapCanvas,
                        template,
                        pageViewport,
                        pageWidth,
                        pageHeight,
                        listOf(pageNoteRect)
                    )
                }

                // 3. Draw shapes/annotations
                val renderContext = RenderContext.createForBitmap(bitmap, bitmapCanvas)
                for (shape in shapes) {
                    shape.renderInViewport(renderContext, pageViewport)
                }

                // Add page to the PDF document
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val pdfPage = document.startPage(pageInfo)
                val scalePaint = Paint().apply { isFilterBitmap = true }
                pdfPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, pageWidth, pageHeight), scalePaint)
                document.finishPage(pdfPage)

                bitmap.recycle()
            }

            FileOutputStream(outputFile).use { stream -> document.writeTo(stream) }
            Log.d(TAG, "export complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } finally {
            document.close()
        }

        outputFile
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
