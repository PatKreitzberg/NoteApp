package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalScrollBar(
    scrollY: Float,
    contentMaxY: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val viewHeightPx = constraints.maxHeight.toFloat()
        if (viewHeightPx <= 0f) return@BoxWithConstraints

        val viewHeightNoteCoords = viewHeightPx / scale
        val totalHeight = maxOf(contentMaxY, viewHeightNoteCoords)

        // Nothing to scroll: full thumb
        val thumbFraction = (viewHeightNoteCoords / totalHeight).coerceIn(0.05f, 1f)
        val scrollRange = totalHeight - viewHeightNoteCoords
        val scrollFraction = if (scrollRange > 0f) (scrollY / scrollRange).coerceIn(0f, 1f) else 0f

        val thumbHeight = viewHeightPx * thumbFraction
        val thumbTop = (viewHeightPx - thumbHeight) * scrollFraction

        val density = LocalDensity.current
        val trackWidth = with(density) { 8.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = size.width - trackWidth
            // Track
            drawRect(
                color = Color.Gray.copy(alpha = 0.15f),
                topLeft = Offset(left, 0f),
                size = Size(trackWidth, size.height)
            )
            // Thumb
            drawRect(
                color = Color.Gray.copy(alpha = 0.5f),
                topLeft = Offset(left, thumbTop),
                size = Size(trackWidth, thumbHeight)
            )
        }
    }
}
