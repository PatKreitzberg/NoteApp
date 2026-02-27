package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.viewport.ViewportState
import kotlin.math.roundToInt

@Composable
fun ViewportInfo(
    viewportState: ViewportState,
    modifier: Modifier = Modifier,
    isPaginationEnabled: Boolean = false,
    currentPageNumber: Int = 1
) {
    // Calculate scroll position in NoteCoordinates
    val scrollX = (-viewportState.offsetX / viewportState.scale).roundToInt()
    val scrollY = (-viewportState.offsetY / viewportState.scale).roundToInt()
    val zoomPercent = (viewportState.scale * 100).roundToInt()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show page number when pagination is enabled
            if (isPaginationEnabled) {
                Text(
                    text = "Page: $currentPageNumber",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "|",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            Text(
                text = "Zoom: $zoomPercent%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "|",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            
            // Scroll position
            Text(
                text = "Scroll: ($scrollX, $scrollY)",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}