package com.wyldsoft.notes.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap

/**
 * Renders PDF pages to bitmaps using [PdfRenderer].
 * Caches rendered bitmaps per page index; invalidates cache when target dimensions change
 * (e.g., user zooms in/out).
 */
class PdfPageRenderer(private val context: Context, private val pdfUri: Uri) {
    private val TAG = "PdfPageRenderer"

    private val pageCache = mutableMapOf<Int, Bitmap>()
    private var lastRenderWidth = -1

    /**
     * Returns a bitmap of the given PDF [pageIndex] sized to [targetWidth] x [targetHeight].
     * Result is cached by page index; cache is cleared if [targetWidth] changes.
     * Returns null if the page cannot be rendered (e.g., URI permission revoked).
     */
    fun getPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        Log.d(TAG, "getPageBitmap pageIndex=$pageIndex targetWidth=$targetWidth")
        if (targetWidth <= 0 || targetHeight <= 0) return null

        val cached = pageCache[pageIndex]
        if (cached != null && !cached.isRecycled && lastRenderWidth == targetWidth) {
            return cached
        }

        if (lastRenderWidth != targetWidth) {
            clearCache()
            lastRenderWidth = targetWidth
        }

        return try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = createBitmap(targetWidth, targetHeight)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageCache[pageIndex] = bitmap
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render page $pageIndex from $pdfUri", e)
            null
        }
    }

    fun clearCache() {
        Log.d(TAG, "clearCache: ${pageCache.size} entries")
        pageCache.values.forEach { if (!it.isRecycled) it.recycle() }
        pageCache.clear()
    }

    fun close() {
        Log.d(TAG, "close")
        clearCache()
    }
}
