package com.wyldsoft.notes.home.components

import androidx.compose.runtime.Composable

@Composable
fun RenameDialog(
    title: String,
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) = TextInputDialog(title, "Rename", currentName, onConfirm, onDismiss)
