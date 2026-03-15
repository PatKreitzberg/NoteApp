package com.wyldsoft.notes.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PdfMetadata(
    val filePath: String,
    val pageCount: Int,
    val pageAspectRatio: Float  // height / width
)

object PdfImporter {

    /**
     * Copies the PDF at [uri] into app-private storage and returns its metadata.
     * Returns null if the file cannot be read or has no pages.
     */
    fun importPdf(context: Context, uri: Uri): PdfMetadata? {
        val pdfDir = File(context.filesDir, "imported_pdfs").also { it.mkdirs() }
        val destFile = File(pdfDir, "${UUID.randomUUID()}.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        } ?: return null

        val pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        if (pageCount == 0) {
            renderer.close()
            pfd.close()
            destFile.delete()
            return null
        }
        val aspectRatio = renderer.openPage(0).use { page ->
            page.height.toFloat() / page.width.toFloat()
        }
        renderer.close()
        pfd.close()

        return PdfMetadata(
            filePath = destFile.absolutePath,
            pageCount = pageCount,
            pageAspectRatio = aspectRatio
        )
    }

    /** Derives a human-readable notebook name from a URI (uses the filename without extension). */
    fun nameFromUri(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        val name = cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
        }
        return name?.substringBeforeLast('.') ?: "Imported PDF"
    }
}
