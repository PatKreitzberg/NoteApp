package com.wyldsoft.notes.ui.toolbar

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val TAG = "LayerButton"

@Composable
fun LayerButton(
    layerPanelExpanded: Boolean,
    onTogglePanel: () -> Unit
) {
    Log.d(TAG, "LayerButton layerPanelExpanded=$layerPanelExpanded")
    IconButton(
        onClick = {
            Log.d(TAG, "LayerButton clicked")
            onTogglePanel()
        },
        modifier = Modifier
            .size(36.dp)
            .then(if (layerPanelExpanded) Modifier.border(2.dp, Color.Black) else Modifier)
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "Layers",
            tint = if (layerPanelExpanded) Color.Black else Color.Gray
        )
    }
}
