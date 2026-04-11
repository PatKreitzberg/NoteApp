package com.wyldsoft.notes.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.rendering.PaginationManager
import com.wyldsoft.notes.rendering.RenderContext
import com.wyldsoft.notes.rendering.ViewportManager
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Exports a note to a PDF file using [PdfDocument].
 * For PDF-backed notes, each page's background is sourced from the original PDF.
 * For regular notes, pages have a white background.
 * All shapes (annotations) are rendered on top.
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
     * @return The exported PDF [File] in [Context.getCacheDir]/exports/.
     */
    suspend fun export(
        context: Context,
        noteId: String,
        pdfUri: Uri?,
        shapes: List<Shape>,
        paginationManager: PaginationManager
    ): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "export noteId=$noteId pdfUri=$pdfUri pages=${paginationManager.pageCount}")

        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()
        val outputFile = File(exportsDir, "$noteId.pdf")

        val pageWidth = paginationManager.pageWidth.toInt().coerceAtLeast(1)
        val pageHeight = paginationManager.pageHeight.toInt().coerceAtLeast(1)
        val document = PdfDocument()

        try {
            for (pageIndex in 0 until paginationManager.pageCount) {
                val pageTopY = paginationManager.pageTopY(pageIndex)

                // Render page content to a bitmap
                val bitmap = createBitmap(pageWidth, pageHeight)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)

                // Draw PDF background if available
                if (pdfUri != null) {
                    renderPdfBackground(context, pdfUri, pageIndex, bitmapCanvas, pageWidth, pageHeight)
                }

                // Render shapes using a viewport positioned at this page's top
                val renderContext = RenderContext.createForBitmap(bitmap, bitmapCanvas)
                val pageViewport = ViewportManager().apply {
                    restoreState(scale = 1f, scrollX = 0f, scrollY = pageTopY)
                }
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
