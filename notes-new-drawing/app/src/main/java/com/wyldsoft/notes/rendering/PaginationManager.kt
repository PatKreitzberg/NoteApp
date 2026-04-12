package com.wyldsoft.notes.rendering

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log

/**
 * Pure geometry calculator for pagination. Computes page boundaries, gap regions,
 * and exclusion rects for TouchHelper. All page/gap coordinates are in note-space.
 *
 * Page dimensions use Letter ratio (8.5 × 11): screenWidth = pageWidth,
 * pageHeight = pageWidth × (11 / 8.5).
 */
class PaginationManager(
    private val screenWidthPx: Int,
    private val screenHeightPx: Int,
    private val density: Float,
    pageAspectRatio: Float = DEFAULT_ASPECT_RATIO
) {
    private val TAG = "PaginationManager"

    companion object {
        private const val GAP_DP = 20f
        const val DEFAULT_ASPECT_RATIO = 11.0f / 8.5f
    }

    val pageWidth: Float = screenWidthPx.toFloat()
    val pageHeight: Float = pageWidth * pageAspectRatio
    val gapPx: Float = GAP_DP * density

    var pageCount: Int = 1
        private set

    fun addPages(n: Int) {
        pageCount += n
        Log.d(TAG, "addPages $n: now $pageCount pages")
    }

    fun removePages(n: Int) {
        pageCount = maxOf(1, pageCount - n)
        Log.d(TAG, "removePages $n: now $pageCount pages")
    }

    init {
        Log.d(TAG, "PaginationManager created: pageWidth=$pageWidth pageHeight=$pageHeight gapPx=$gapPx")
    }

    fun totalContentHeight(): Float =
        pageCount * pageHeight + (pageCount - 1) * gapPx

    fun pageTopY(pageIndex: Int): Float =
        pageIndex * (pageHeight + gapPx)

    fun pageBottomY(pageIndex: Int): Float =
        pageTopY(pageIndex) + pageHeight

    fun gapRect(pageIndex: Int): RectF? {
        if (pageIndex >= pageCount - 1) return null
        val top = pageBottomY(pageIndex)
        return RectF(0f, top, pageWidth, top + gapPx)
    }

    fun allGapRects(): List<RectF> =
        (0 until pageCount - 1).mapNotNull { gapRect(it) }

    /**
     * Adds a page if the user has scrolled past 80% of the last page.
     * Returns true if a page was added.
     */
    fun maybeAddPage(scrollY: Float, viewportHeightInNote: Float): Boolean {
        val viewportBottom = scrollY + viewportHeightInNote
        val threshold = pageTopY(pageCount - 1) + pageHeight * 0.8f
        if (viewportBottom >= threshold) {
            pageCount++
            Log.d(TAG, "maybeAddPage: added page, now $pageCount pages")
            return true
        }
        return false
    }

    /**
     * Converts visible out-of-page regions to viewport-space Rects for TouchHelper exclusion.
     * Covers: gaps between pages, area to the right of the page (when zoomed out),
     * and area below the last page (when visible).
     */
    fun computeExclusionRects(
        scrollX: Float,
        scrollY: Float,
        scale: Float,
        screenWidth: Int,
        screenHeight: Int
    ): List<Rect> {
        Log.d(TAG, "computeExclusionRects")
        val result = mutableListOf<Rect>()
        val viewportNoteTop = scrollY
        val viewportNoteBottom = scrollY + screenHeight / scale

        // Gap exclusions (between pages)
        for (gap in allGapRects()) {
            if (gap.bottom <= viewportNoteTop || gap.top >= viewportNoteBottom) continue
            val top = ((gap.top - scrollY) * scale).toInt().coerceAtLeast(0)
            val bottom = ((gap.bottom - scrollY) * scale).toInt().coerceAtMost(screenHeight)
            if (top < bottom) {
                result.add(Rect(0, top, screenWidth, bottom))
            }
        }

        // Right-side exclusion: area to the right of the page when zoomed out
        val pageRightViewport = ((pageWidth - scrollX) * scale).toInt()
        if (pageRightViewport < screenWidth) {
            result.add(Rect(pageRightViewport.coerceAtLeast(0), 0, screenWidth, screenHeight))
        }

        // Below-last-page exclusion: area below the last page when visible
        val lastPageBottomViewport = ((pageBottomY(pageCount - 1) - scrollY) * scale).toInt()
        if (lastPageBottomViewport < screenHeight) {
            result.add(Rect(0, lastPageBottomViewport.coerceAtLeast(0), screenWidth, screenHeight))
        }

        Log.d(TAG, "computeExclusionRects: ${result.size} rects")
        return result
    }
}
