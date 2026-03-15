package com.wyldsoft.notes.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.wyldsoft.notes.viewport.ViewportManager
import java.io.File

/**
 * Renders PDF pages to bitmaps for display as a locked note background.
 *
 * - Pages are rendered at [screenWidthPx] × (screenWidthPx × aspectRatio) resolution.
 * - An LRU cache holds up to [CACHE_SIZE] pages to avoid re-rendering on every scroll frame.
 * - Close [close] when done (e.g. when the note is unloaded) to release the PdfRenderer.
 */
class PdfPageRenderer(
    private val pdfPath: String,
    private val screenWidthPx: Int,
    private val pageAspectRatio: Float
) : AutoCloseable {

    companion object {
        private const val CACHE_SIZE = 6
    }

    private val pageHeightPx: Int = (screenWidthPx * pageAspectRatio).toInt()
    private val cache = LruCache<Int, Bitmap>(CACHE_SIZE)

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    private fun ensureOpen() {
        if (renderer != null) return
        val file = File(pdfPath)
        if (!file.exists()) return
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd!!)
    }

    /**
     * Returns a cached bitmap for [pageIndex] (0-based), rendering it if needed.
     * Returns null if the page index is out of range or the file is unavailable.
     */
    fun getPage(pageIndex: Int): Bitmap? {
        cache.get(pageIndex)?.let { return it }

        ensureOpen()
        val r = renderer ?: return null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return null

        val bitmap = Bitmap.createBitmap(screenWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        r.openPage(pageIndex).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }

        cache.put(pageIndex, bitmap)
        return bitmap
    }

    /**
     * Draws all PDF pages that overlap the visible viewport onto [canvas].
     * Pages that are beyond [pdfPageCount] (user-added blank pages) are skipped.
     */
    fun drawVisiblePages(
        canvas: Canvas,
        viewportManager: ViewportManager,
        pageHeightNote: Float,
        pdfPageCount: Int
    ) {
        val screenWidth = screenWidthPx.toFloat()
        val screenHeight = canvas.height.toFloat()

        for (pageIdx in 0 until pdfPageCount) {
            val pageTopNote = pageIdx * pageHeightNote
            val pageBottomNote = (pageIdx + 1) * pageHeightNote

            val topSurf = viewportManager.noteToSurfaceCoordinates(0f, pageTopNote)
            val bottomSurf = viewportManager.noteToSurfaceCoordinates(0f, pageBottomNote)

            val pageTopSurf = topSurf.y
            val pageBottomSurf = bottomSurf.y

            // Skip pages that are off-screen
            if (pageBottomSurf < 0f || pageTopSurf > screenHeight) continue

            val bitmap = getPage(pageIdx) ?: continue

            val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val dst = RectF(0f, pageTopSurf, screenWidth, pageBottomSurf)
            canvas.drawBitmap(bitmap, src, dst, null)
        }
    }

    /** Evicts cached bitmaps if screen width has changed (e.g. after orientation change). */
    fun invalidateCache() {
        cache.evictAll()
    }

    override fun close() {
        cache.evictAll()
        renderer?.close()
        renderer = null
        pfd?.close()
        pfd = null
    }
}
