package com.wyldsoft.notes.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool

private val fontOptions = listOf(
    "Sans Serif" to "sans-serif",
    "Serif" to "serif",
    "Monospace" to "monospace"
)

private val sizeOptions = listOf(16f, 24f, 32f, 48f, 64f, 96f)

private val colorOptions = listOf(
    "Black" to android.graphics.Color.BLACK,
    "Dark Gray" to android.graphics.Color.DKGRAY,
    "Gray" to android.graphics.Color.GRAY,
    "Red" to android.graphics.Color.RED,
    "Blue" to android.graphics.Color.BLUE
)

@Composable
fun ToolbarTextButtons(
    viewModel: EditorViewModel,
    isStrokeSelectionOpen: Boolean,
    onCloseStrokePanel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isTextActive = uiState.selectedTool == Tool.TEXT
    val currentFontFamily by viewModel.textFontFamily.collectAsState()
    val currentFontSize by viewModel.textFontSize.collectAsState()
    val currentTextColor by viewModel.textColor.collectAsState()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Text tool toggle button
        IconButton(
            onClick = {
                if (isTextActive) {
                    viewModel.selectTool(Tool.PEN)
                } else {
                    if (isStrokeSelectionOpen) onCloseStrokePanel()
                    viewModel.selectTool(Tool.TEXT)
                }
            },
            modifier = Modifier.then(if (isTextActive) Modifier.border(2.dp, Color.Black) else Modifier)
        ) {
            Icon(
                imageVector = Icons.Default.TextFields,
                contentDescription = "Text Tool",
                tint = if (isTextActive) Color.Black else Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Font family dropdown
        TextDropdown(
            label = fontOptions.firstOrNull { it.second == currentFontFamily }?.first ?: "Sans Serif",
            items = fontOptions.map { it.first },
            viewModel = viewModel,
            onItemSelected = { index -> viewModel.setTextFontFamily(fontOptions[index].second) }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Font size dropdown
        TextDropdown(
            label = "${currentFontSize.toInt()}px",
            items = sizeOptions.map { "${it.toInt()}px" },
            viewModel = viewModel,
            onItemSelected = { index -> viewModel.setTextFontSize(sizeOptions[index]) }
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Text color dropdown
        TextDropdown(
            label = colorOptions.firstOrNull { it.second == currentTextColor }?.first ?: "Black",
            items = colorOptions.map { it.first },
            viewModel = viewModel,
            onItemSelected = { index -> viewModel.setTextColor(colorOptions[index].second) }
        )
    }
}

@Composable
private fun TextDropdown(
    label: String,
    items: List<String>,
    viewModel: EditorViewModel,
    onItemSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.closeAllDropdownsEvent.collect {
            expanded = false
        }
    }

    Box {
        OutlinedButton(
            onClick = {
                if (!expanded) viewModel.onDropdownOpened()
                expanded = true
            },
            modifier = Modifier.height(36.dp)
        ) {
            Text(label, fontSize = 11.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                viewModel.onDropdownClosed()
            }
        ) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 13.sp) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                        viewModel.onDropdownClosed()
                    }
                )
            }
        }
    }
}
