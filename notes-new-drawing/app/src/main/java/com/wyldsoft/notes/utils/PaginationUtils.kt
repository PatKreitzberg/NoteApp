package com.wyldsoft.notes.utils

object PaginationConstants {
    const val SEPARATOR_HEIGHT = 10f
}

fun getPageBounds(page: Int, pageHeight: Float): Pair<Float, Float> {
    val pageTop = page * pageHeight + if (page > 0) PaginationConstants.SEPARATOR_HEIGHT else 0f
    val pageBottom = (page + 1) * pageHeight
    return pageTop to pageBottom
}

fun getVisiblePageRange(visibleTop: Float, visibleBottom: Float, pageHeight: Float): IntRange {
    val firstVisiblePage = maxOf(0, (visibleTop / pageHeight).toInt())
    val lastVisiblePage = (visibleBottom / pageHeight).toInt() + 1
    return firstVisiblePage..lastVisiblePage
}
