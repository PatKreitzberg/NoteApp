package com.wyldsoft.notes.home.components

import androidx.compose.runtime.Composable

@Composable
fun CreateItemDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) = TextInputDialog(title, "Create", "", onConfirm, onDismiss)
