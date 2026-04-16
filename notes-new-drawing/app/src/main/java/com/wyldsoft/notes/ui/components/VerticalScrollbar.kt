package com.wyldsoft.notes.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val TAG = "VerticalScrollbar"

/**
 * A thin vertical scrollbar overlaid on the canvas right edge.
 * Works regardless of canvas lock state — it is the sole scroll mechanism when locked.
 *
 * [scrollY]            current note-space scroll Y
 * [scale]              current viewport scale
 * [totalContentHeight] note-space height of the full canvas content
 * [canvasHeightPx]     pixel height of the drawing canvas area
 * [onScrollRequested]  called with the desired note-space scrollY when the thumb is dragged
 */
@Composable
fun VerticalScrollbar(
    scrollY: Float,
    scale: Float,
    totalContentHeight: Float,
    canvasHeightPx: Float,
    onScrollRequested: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "VerticalScrollbar scrollY=$scrollY scale=$scale totalContentHeight=$totalContentHeight canvasHeightPx=$canvasHeightPx")
    val viewportHeightInNote = if (scale > 0f) canvasHeightPx / scale else canvasHeightPx
    val maxScrollable = (totalContentHeight - viewportHeightInNote).coerceAtLeast(0f)

    if (maxScrollable <= 0f) return

    val scrollFraction = (scrollY / maxScrollable).coerceIn(0f, 1f)
    val thumbRatio = (viewportHeightInNote / totalContentHeight).coerceIn(0.04f, 0.95f)

    BoxWithConstraints(modifier = modifier) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = (thumbRatio * trackHeightPx).coerceAtLeast(40f)
        val thumbTopPx = scrollFraction * (trackHeightPx - thumbHeightPx)

        val density = LocalDensity.current
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
        val thumbTopDp = with(density) { thumbTopPx.toDp() }

        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x18000000), RoundedCornerShape(4.dp))
        )

        // Thumb
        var isDragging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbTopDp)
                .width(14.dp)
                .height(thumbHeightDp)
                .background(
                    if (isDragging) Color(0xCC333333) else Color(0x99555555),
                    RoundedCornerShape(7.dp)
                )
                .pointerInput(maxScrollable, thumbHeightPx, trackHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            Log.d(TAG, "drag start")
                            isDragging = true
                        },
                        onDragEnd = {
                            Log.d(TAG, "drag end")
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val availableTrack = trackHeightPx - thumbHeightPx
                            if (availableTrack > 0f) {
                                val deltaFraction = dragAmount.y / availableTrack
                                val newScrollY = (scrollY + deltaFraction * maxScrollable)
                                    .coerceIn(0f, maxScrollable)
                                onScrollRequested(newScrollY)
                            }
                        }
                    )
                }
        )
    }
}
