package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val TRACK_COLOR = Color(0xFFDDDDDD)
private val THUMB_COLOR = Color(0xFF555555)
private val TRACK_WIDTH_DP = 12.dp
private val MIN_THUMB_FRACTION = 0.08f

@Composable
fun VerticalScrollBar(
    scrollY: Float,
    contentMaxY: Float,
    scale: Float,
    onScrollTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val viewHeightPx = constraints.maxHeight.toFloat()
        if (viewHeightPx <= 0f) return@BoxWithConstraints

        val viewHeightNoteCoords = viewHeightPx / scale
        val totalHeight = maxOf(contentMaxY, viewHeightNoteCoords)
        val scrollRange = totalHeight - viewHeightNoteCoords

        // If everything fits in view, show a full-size disabled thumb
        val thumbFraction = (viewHeightNoteCoords / totalHeight).coerceIn(MIN_THUMB_FRACTION, 1f)
        val thumbHeight = viewHeightPx * thumbFraction
        val trackHeight = viewHeightPx - thumbHeight

        val scrollFraction = if (scrollRange > 0f) (scrollY / scrollRange).coerceIn(0f, 1f) else 0f
        val externalThumbTop = trackHeight * scrollFraction

        // Local drag state: keeps thumb position smooth during a drag
        var thumbTop by remember { mutableFloatStateOf(externalThumbTop) }
        LaunchedEffect(externalThumbTop) { thumbTop = externalThumbTop }

        val density = LocalDensity.current
        val trackWidthPx = with(density) { TRACK_WIDTH_DP.toPx() }
        val cornerPx = trackWidthPx / 2f

        val canDrag = scrollRange > 0f

        val drawModifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val left = (size.width - trackWidthPx) / 2f
                // Track
                drawRoundRect(
                    color = TRACK_COLOR,
                    topLeft = Offset(left, 0f),
                    size = Size(trackWidthPx, size.height),
                    cornerRadius = CornerRadius(cornerPx)
                )
                // Thumb
                drawRoundRect(
                    color = THUMB_COLOR,
                    topLeft = Offset(left, thumbTop),
                    size = Size(trackWidthPx, thumbHeight),
                    cornerRadius = CornerRadius(cornerPx)
                )
            }

        val interactiveModifier = if (canDrag) {
            drawModifier.pointerInput(scrollRange, thumbHeight, trackHeight) {
                detectVerticalDragGestures { _, dragAmount ->
                    val newThumbTop = (thumbTop + dragAmount).coerceIn(0f, trackHeight)
                    thumbTop = newThumbTop
                    val newScrollFraction = if (trackHeight > 0f) newThumbTop / trackHeight else 0f
                    onScrollTo(newScrollFraction * scrollRange)
                }
            }
        } else {
            drawModifier
        }

        androidx.compose.foundation.layout.Box(modifier = interactiveModifier)
    }
}
