package com.wyldsoft.notes.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.DrawingCanvas
import com.wyldsoft.notes.touchhandling.GestureDisplay
import com.wyldsoft.notes.ui.components.VerticalScrollbar
import com.wyldsoft.notes.ui.toolbar.LayerPanel
import com.wyldsoft.notes.ui.toolbar.PenPropertiesPanel
import com.wyldsoft.notes.ui.toolbar.TextPropertiesPanel
import com.wyldsoft.notes.ui.toolbar.Toolbar

/**
 * Top-level Compose layout for the editor screen.
 * Hosts the Toolbar, DrawingCanvas, and optional settings panels
 * with a fullscreen scrim for dismissal.
 */
@Composable
fun EditorView(
    onSurfaceViewCreated: (android.view.SurfaceView) -> Unit = {},
    gestureLabel: MutableState<String> = remember { mutableStateOf("") },
    resetViewport: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {}
) {
    remember { EditorState() }

    var menuExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var geometryExpanded by remember { mutableStateOf(false) }
    var layerPanelExpanded by remember { mutableStateOf(false) }
    val currentProfile by EditorState.currentPenProfile.collectAsState()
    val textProfile by EditorState.textProfile.collectAsState()
    val currentMode by EditorState.currentMode.collectAsState()
    val layers by EditorState.layers.collectAsState()
    val activeLayer by EditorState.activeLayer.collectAsState()
    val viewportScrollY by EditorState.viewportScrollY.collectAsState()
    val viewportScale by EditorState.viewportScale.collectAsState()
    val totalContentHeight by EditorState.totalContentHeight.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Toolbar(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                settingsExpanded = settingsExpanded,
                onSettingsExpandedChange = { settingsExpanded = it },
                textExpanded = textExpanded,
                onTextExpandedChange = { textExpanded = it },
                geometryExpanded = geometryExpanded,
                onGeometryExpandedChange = { geometryExpanded = it },
                layerPanelExpanded = layerPanelExpanded,
                onLayerPanelExpandedChange = { layerPanelExpanded = it },
                resetViewport = { resetViewport() },
                onSearchQueryChanged = onSearchQueryChanged
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val canvasHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

                DrawingCanvas(
                    onSurfaceViewCreated = onSurfaceViewCreated,
                    modifier = Modifier.fillMaxSize()
                )

                VerticalScrollbar(
                    scrollY = viewportScrollY,
                    scale = viewportScale,
                    totalContentHeight = totalContentHeight,
                    canvasHeightPx = canvasHeightPx,
                    onScrollRequested = { EditorState.requestScrollbarScroll(it) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                )
            }
        }

        // Layer panel overlay
        if (layerPanelExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        layerPanelExpanded = false
                        EditorState.setMode(AppMode.DRAWING)
                    }
            )
            LayerPanel(
                layers = layers,
                activeLayer = activeLayer,
                onSelectLayer = { pos ->
                    EditorState.setActiveLayer(pos)
                    layerPanelExpanded = false
                    EditorState.setMode(AppMode.DRAWING)
                },
                onAddLayer = {
                    EditorState.requestAddLayer()
                },
                onDeleteLayer = { layer ->
                    EditorState.requestDeleteLayer(layer)
                },
                onRenameLayer = { layer, newName ->
                    EditorState.requestRenameLayer(layer, newName)
                },
                onToggleVisibility = { layer ->
                    EditorState.requestToggleLayerVisibility(layer)
                },
                onDismiss = {
                    layerPanelExpanded = false
                    EditorState.setMode(AppMode.DRAWING)
                },
                modifier = Modifier.padding(top = 48.dp)
            )
        }

        // Scrim + panels overlay when any panel is open
        if (menuExpanded || settingsExpanded) {
            // Fullscreen transparent scrim — catches finger taps outside the panel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        menuExpanded = false
                        settingsExpanded = false
                        EditorState.setMode(AppMode.DRAWING)
                    }
            )

            if (menuExpanded) {
                // Pen settings panel positioned below the toolbar
                PenPropertiesPanel(
                    currentProfile = currentProfile,
                    onProfileChanged = { newProfile ->
                        EditorState.setPenProfile(newProfile)
                    },
                    modifier = Modifier.padding(top = 48.dp)
                )
            }

            if (settingsExpanded) {
                // Editor settings panel positioned below the toolbar, right-aligned
                EditorSettingsPanel(
                    modifier = Modifier
                        .padding(top = 48.dp)
                        .wrapContentWidth()
                        .align(androidx.compose.ui.Alignment.TopEnd)
                )
            }
        }

        // Text properties panel overlay (separate from SETTINGS — stays in TEXT mode)
        if (textExpanded && currentMode == AppMode.TEXT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        textExpanded = false
                    }
            )
            TextPropertiesPanel(
                textProfile = textProfile,
                onProfileChanged = { EditorState.setTextProfile(it) },
                modifier = Modifier.padding(top = 48.dp)
            )
        }

        // Gesture notification overlay
        GestureDisplay(gestureLabel = gestureLabel)
    }
}
