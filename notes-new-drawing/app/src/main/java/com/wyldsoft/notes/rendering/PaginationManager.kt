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
    private val density: Float
) {
    private val TAG = "PaginationManager"

    companion object {
        private const val GAP_DP = 40f
        private const val PAGE_ASPECT_RATIO = 11.0f / 8.5f
    }

    val pageWidth: Float = screenWidthPx.toFloat()
    val pageHeight: Float = pageWidth * PAGE_ASPECT_RATIO
    val gapPx: Float = GAP_DP * density

    var pageCount: Int = 1
        private set

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
     * Converts visible gap regions to viewport-space Rects for TouchHelper exclusion.
     * Only returns rects that intersect the current screen area.
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

        for (gap in allGapRects()) {
            if (gap.bottom <= viewportNoteTop || gap.top >= viewportNoteBottom) continue
            val top = ((gap.top - scrollY) * scale).toInt().coerceAtLeast(0)
            val bottom = ((gap.bottom - scrollY) * scale).toInt().coerceAtMost(screenHeight)
            if (top < bottom) {
                result.add(Rect(0, top, screenWidth, bottom))
            }
        }
        Log.d(TAG, "computeExclusionRects: ${result.size} rects")
        return result
    }
}
