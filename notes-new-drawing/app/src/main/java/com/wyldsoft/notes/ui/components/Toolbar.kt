package com.wyldsoft.notes.ui.components

import android.graphics.Rect
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

import com.wyldsoft.notes.pen.PenProfile
import com.wyldsoft.notes.presentation.viewmodel.EditorViewModel
import com.wyldsoft.notes.presentation.viewmodel.Tool


@Composable
fun Toolbar(
    viewModel: EditorViewModel,
    currentPenProfile: PenProfile,
    isStrokeOptionsOpen: Boolean,
    onSettingsClick: () -> Unit = {},
    onCollapsedChanged: (Boolean) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onNavigateForward: (() -> Unit)? = null,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var selectedProfileIndex by remember { mutableStateOf(0) }
    var isStrokeSelectionOpen by remember { mutableStateOf(isStrokeOptionsOpen) }
    var strokePanelRect by remember { mutableStateOf<Rect?>(null) }
    var isCollapsed by remember { mutableStateOf(false) }
    var toolbarHeightPx by remember { mutableStateOf(0) }

    // Store 5 profiles
    var profiles by remember {
        mutableStateOf(PenProfile.createDefaultProfiles())
    }

    // Sync with ViewModel state
    LaunchedEffect(isStrokeOptionsOpen) {
        isStrokeSelectionOpen = isStrokeOptionsOpen
    }
    
    fun forceUIRefresh() {
        Log.d("Toolbar:", "UI Refresh triggered WARNING: CURRENTLY DOES NOTHING")
        viewModel.forceRefresh()
    }

    fun addStrokeOptionPanelRect() {
        strokePanelRect?.let { rect ->
            val currentRects = viewModel.excludeRects.value.toMutableList()
            currentRects.add(rect)
            viewModel.updateExclusionZones(currentRects)
            forceUIRefresh()
        }
    }

    fun removeStrokeOptionPanelRect() {
        Log.d("Toolbar", "Removing stroke options panel rect from exclusion zones")
        strokePanelRect?.let { rect ->
            val currentRects = viewModel.excludeRects.value.toMutableList()
            currentRects.remove(rect)
            viewModel.updateExclusionZones(currentRects)
        }
    }

    fun openStrokeOptionsPanel() {
        Log.d("Toolbar", "Opening stroke options panel for profile $selectedProfileIndex")
        viewModel.toggleStrokeOptions()
    }

    fun closeStrokeOptionsPanel() {
        Log.d("Toolbar", "Closing stroke options panel")
        if (isStrokeSelectionOpen) {
            viewModel.toggleStrokeOptions()
        }
        removeStrokeOptionPanelRect()
        forceUIRefresh()
    }

    fun handleProfileClick(profileIndex: Int) {
        // If in selection mode, switch back to pen
        if (viewModel.uiState.value.selectedTool == Tool.SELECTOR) {
            viewModel.cancelSelection()
        }
        if (selectedProfileIndex == profileIndex && isStrokeSelectionOpen) {
            // Same profile clicked - close panel
            closeStrokeOptionsPanel()
        } else if (selectedProfileIndex == profileIndex && !isStrokeSelectionOpen) {
            // Same profile clicked - open panel
            openStrokeOptionsPanel()
        } else {
            // Different profile - switch profile and update
            if (isStrokeSelectionOpen) {
                closeStrokeOptionsPanel()
            }
            selectedProfileIndex = profileIndex
            val newProfile = profiles[profileIndex]
            viewModel.updatePenProfile(newProfile)
        }
    }

    fun updateProfile(newProfile: PenProfile) {
        val updatedProfiles = profiles.toMutableList()
        updatedProfiles[selectedProfileIndex] = newProfile
        profiles = updatedProfiles

        // Immediately apply the new profile
        viewModel.updatePenProfile(newProfile)

        Log.d("Toolbar", "Profile $selectedProfileIndex updated: $newProfile")
    }

    // Listen for drawing events to close panel
    val isDrawing by viewModel.isDrawing.collectAsState()
    LaunchedEffect(isDrawing) {
        if (isDrawing && isStrokeSelectionOpen) {
            Log.d("Toolbar", "Drawing started - closing stroke options panel")
            closeStrokeOptionsPanel()
        }
    }


    // Initialize with default profile
    LaunchedEffect(Unit) {
        viewModel.updatePenProfile(profiles[selectedProfileIndex])
    }

    Column {
        if (isCollapsed) {
            // Collapsed toolbar - a small tab at the right edge to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    isCollapsed = false
                    onCollapsedChanged(false)
                }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Show Toolbar",
                        tint = Color.Black
                    )
                }
            }
        } else {
            // Main toolbar - single row with 5 profile buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        toolbarHeightPx = bounds.bottom.toInt()
                    }
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //Text("Profiles:", color = Color.Black, fontSize = 12.sp)

                // 5 Profile buttons
                profiles.forEachIndexed { index, profile ->
                    ProfileButton(
                        profile = profile,
                        isSelected = selectedProfileIndex == index,
                        isActive = currentPenProfile.profileId == profile.profileId,
                        onClick = { handleProfileClick(index) }
                    )
                }

                // Selection tool button
                val uiState by viewModel.uiState.collectAsState()
                val isSelectionActive = uiState.selectedTool == Tool.SELECTOR

                IconButton(
                    onClick = {
                        if (isSelectionActive) {
                            viewModel.cancelSelection()
                        } else {
                            if (isStrokeSelectionOpen) {
                                closeStrokeOptionsPanel()
                            }
                            viewModel.selectTool(Tool.SELECTOR)
                        }
                    },
                    modifier = Modifier
                        .then(
                            if (isSelectionActive) Modifier.border(2.dp, Color.Black)
                            else Modifier
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Selection Tool",
                        tint = if (isSelectionActive) Color.Black else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Undo/Redo buttons
                val canUndo by viewModel.canUndo.collectAsState()
                val canRedo by viewModel.canRedo.collectAsState()

                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.Black else Color.LightGray
                    )
                }

                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.Black else Color.LightGray
                    )
                }

                // Settings icon
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Note Settings",
                        tint = Color.Black
                    )
                }

                // Note navigation buttons (only shown when in a notebook)
                if (onNavigateBack != null && onNavigateForward != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Note",
                            tint = if (canGoBack) Color.Black else Color.LightGray
                        )
                    }

                    IconButton(
                        onClick = onNavigateForward,
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Note",
                            tint = if (canGoForward) Color.Black else Color.LightGray
                        )
                    }
                }

                // Collapse toolbar button
                IconButton(onClick = {
                    if (isStrokeSelectionOpen) {
                        closeStrokeOptionsPanel()
                    }
                    isCollapsed = true
                    onCollapsedChanged(true)
                }) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Collapse Toolbar",
                        tint = Color.Black
                    )
                }

            }

            // Stroke options panel - rendered in a Popup so it appears above the SurfaceView canvas
            if (isStrokeSelectionOpen) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, toolbarHeightPx),
                    properties = PopupProperties(focusable = false)
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            Log.d("Toolbar", "StrokeOptionsPanel removed from composition")
                            removeStrokeOptionPanelRect()
                            forceUIRefresh()
                        }
                    }

                    StrokeOptionsPanel(
                        viewModel = viewModel,
                        currentProfile = currentPenProfile,
                        onProfileChanged = { newProfile ->
                            updateProfile(newProfile)
                        },
                        onPanelPositioned = { rect ->
                            if (rect != strokePanelRect) {
                                strokePanelRect = rect
                                if (isStrokeSelectionOpen) {
                                    scope.launch {
                                        addStrokeOptionPanelRect()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileButton(
    profile: PenProfile,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PenButton(
            penType = profile.penType,
            isSelected = isSelected,
            onClick = onClick,
            selectedColor = profile.strokeColor,
            size = 48.dp,
            iconSize = 24.dp
        )
        // Color dot: shows the profile's ink color at a glance.
        // Filled when this profile is the one currently drawing (isActive),
        // outlined otherwise so all 5 colors are always visible.
        Box(
            modifier = Modifier
                .size(8.dp)
                .border(
                    width = 1.dp,
                    color = profile.strokeColor,
                    shape = CircleShape
                )
                .then(
                    if (isActive) Modifier.background(profile.strokeColor, CircleShape)
                    else Modifier
                )
        )
    }
}