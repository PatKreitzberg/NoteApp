package com.wyldsoft.notes.ui.components

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel

/**
 * Canvas-positioned live text input overlay.
 *
 * Renders a bordered TextField directly on the canvas at the note coordinate position.
 * - Width expands with content (IntrinsicSize.Min), wraps at screen edge.
 * - Enter key creates a new line.
 * - Tapping outside commits the text. Keyboard is dismissed on exit.
 */
@Composable
fun LiveTextInput(
    notePosition: PointF,
    viewModel: EditorViewModel,
    onCommit: () -> Unit
) {
    val density = LocalDensity.current
    val viewportState by viewModel.viewportState.collectAsState()
    val textValue by viewModel.liveTextContent.collectAsState()
    val currentFontSize by viewModel.textFontSize.collectAsState()
    val currentFontFamily by viewModel.textFontFamily.collectAsState()
    val currentTextColor by viewModel.textColor.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    // Convert note coords to canvas-relative pixels
    val surfacePoint = remember(notePosition, viewportState) {
        viewModel.viewportManager.noteToSurfaceCoordinates(notePosition.x, notePosition.y)
    }
    val offsetXDp = with(density) { surfacePoint.x.toDp() }
    val offsetYDp = with(density) { surfacePoint.y.toDp() }
    val maxWidth = (screenWidthDp - offsetXDp).coerceAtLeast(120.dp)

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val composeFontFamily = when (currentFontFamily) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }

    val textStyle = TextStyle(
        fontSize = currentFontSize.sp,
        fontFamily = composeFontFamily,
        color = Color(currentTextColor)
    )

    // Full-screen transparent overlay — tapping outside commits text
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onCommit() }
            }
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { viewModel.updateLiveTextContent(it) },
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default  // Enter inserts newline
            ),
            modifier = Modifier
                .layout { measurable, constraints ->
                    // Place the composable at surface coordinates (pixel offset)
                    val placeable = measurable.measure(constraints)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = surfacePoint.x.toInt(),
                            y = surfacePoint.y.toInt()
                        )
                    }
                }
                .width(IntrinsicSize.Min)
                .widthIn(min = 120.dp, max = maxWidth)
                .background(Color.White.copy(alpha = 0.95f))
                .border(2.dp, Color.Black)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField -> Box { innerTextField() } }
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }
}
